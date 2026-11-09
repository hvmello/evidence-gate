#!/usr/bin/env python3
"""Validate a backlog file and open its milestones, labels and issues on GitHub.

Usage: open_issues.py BACKLOG.json [--apply]

Without --apply nothing leaves this machine. With it, the GitHub CLI (gh) must be authenticated.
Existing issues are matched by title and skipped, so the script can be run again.
"""
import argparse
import json
import subprocess
import sys

TYPES = {
    "feature": ("feature", "feature", "develop"),
    "bugfix": ("bugfix", "bug", "develop"),
    "chore": ("chore", "chore", "develop"),
    "spike": ("spike", "spike", "develop"),
    "hotfix": ("hotfix", "hotfix", None),  # None means the production branch
}
LABEL_COLORS = {"feature": "1d76db", "bug": "d73a4a", "chore": "cfd3d7", "spike": "5319e7", "hotfix": "b60205"}


def validate(backlog):
    errors = []
    milestones = {m["title"] for m in backlog["milestones"]}
    seen, titles = set(), set()
    for issue in backlog["issues"]:
        key = issue["key"]
        if key in seen:
            errors.append(f"{key}: duplicate key")
        if issue["title"] in titles:
            errors.append(f"{key}: duplicate title")
        if issue["type"] not in TYPES:
            errors.append(f"{key}: unknown type {issue['type']}")
        if issue["milestone"] not in milestones:
            errors.append(f"{key}: unknown milestone {issue['milestone']}")
        if not issue["done"]:
            errors.append(f"{key}: no done criteria")
        for dep in issue.get("deps", []):
            if dep not in seen:
                errors.append(f"{key}: dependency {dep} is unknown or appears later")
        seen.add(key)
        titles.add(issue["title"])
    return errors


def gh(*args):
    done = subprocess.run(["gh", *args], capture_output=True, text=True, encoding="utf8")
    if done.returncode != 0:
        sys.exit(f"gh {' '.join(args[:3])} failed: {done.stderr.strip()}")
    return done.stdout.strip()


def body(issue, numbers, branch):
    lines = ["## Goal", "", issue["goal"], "", "## Done when", ""]
    lines += [f"- [ ] {item}" for item in issue["done"]]
    if issue.get("deps"):
        lines += ["", "## Depends on", ""] + [f"- #{numbers[d]}" for d in issue["deps"]]
    if issue.get("note"):
        lines += ["", "## Note", "", issue["note"]]
    lines += ["", "## Branch", "", f"`{branch}`"]
    return "\n".join(lines)


def branch_name(issue, number):
    return f"{TYPES[issue['type']][0]}/{number}-{issue['slug']}"


def apply(backlog):
    repo = backlog["repo"]
    for label, color in LABEL_COLORS.items():
        gh("label", "create", label, "--repo", repo, "--color", color, "--force")

    existing = json.loads(gh("api", f"repos/{repo}/milestones?state=all&per_page=100"))
    known = {m["title"] for m in existing}
    for milestone in backlog["milestones"]:
        if milestone["title"] not in known:
            gh("api", f"repos/{repo}/milestones", "-f", f"title={milestone['title']}",
               "-f", f"description={milestone['description']}")

    current = json.loads(gh("issue", "list", "--repo", repo, "--state", "all", "--limit", "500",
                            "--json", "number,title"))
    by_title = {i["title"]: i["number"] for i in current}

    # GitHub assigns numbers in creation order, but pull requests share that sequence, so each
    # number is read back from the created issue.
    numbers = {}
    for issue in backlog["issues"]:
        number = by_title.get(issue["title"])
        if number is None:
            url = gh("issue", "create", "--repo", repo, "--title", issue["title"], "--body", "pending",
                     "--label", TYPES[issue["type"]][1], "--milestone", issue["milestone"])
            number = int(url.rstrip("/").rsplit("/", 1)[1])
        numbers[issue["key"]] = number
        branch = branch_name(issue, number)
        gh("issue", "edit", str(number), "--repo", repo, "--body", body(issue, numbers, branch))
        print(f"#{number}\t{branch}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("backlog")
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    sys.stdout.reconfigure(encoding="utf8")

    with open(args.backlog, encoding="utf8") as handle:
        backlog = json.load(handle)

    errors = validate(backlog)
    if errors:
        sys.exit("\n".join(errors))

    if args.apply:
        apply(backlog)
        return

    production = backlog["production_branch"]
    print(f"{backlog['repo']}: {len(backlog['milestones'])} milestones, {len(backlog['issues'])} issues (dry run)")
    for issue in backlog["issues"]:
        prefix, _, base = TYPES[issue["type"]]
        print(f"{issue['key']:<7}{issue['milestone']:<7}{prefix + '/N-' + issue['slug']:<46}<- {base or production}")


if __name__ == "__main__":
    main()
