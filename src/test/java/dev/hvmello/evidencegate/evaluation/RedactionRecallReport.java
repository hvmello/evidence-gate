package dev.hvmello.evidencegate.evaluation;

import dev.hvmello.evidencegate.evaluation.RedactionRecallSet.Entry;
import dev.hvmello.evidencegate.evaluation.RedactionRecallSet.IdentifierClass;
import dev.hvmello.evidencegate.evaluation.RedactionScorer.Label;
import dev.hvmello.evidencegate.evaluation.RedactionScorer.Outcome;
import dev.hvmello.evidencegate.evaluation.RedactionScorer.Score;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Aggregates scored records per class, with a Wilson interval, and renders the published Markdown. */
final class RedactionRecallReport {

    static final String COMMAND = "./mvnw -B test -Dgroups=evaluation -De1.write=true";
    static final String DATE_PREFIX = "- Date: ";

    private static final double Z = 1.96;

    record Scored(Entry entry, Score score) { }

    record ClassStats(
            IdentifierClass identifierClass, boolean hasRule, int n, int caught, int leakedFull, int leakedPartial,
            int fillerDamaged, int labelCorrect, List<Scored> records) {

        double recall() {
            return (double) caught / n;
        }

        double[] interval() {
            return wilson(caught, n);
        }
    }

    private RedactionRecallReport() { }

    static List<ClassStats> aggregate(List<Scored> scored) {
        Map<IdentifierClass, List<Scored>> byClass = new EnumMap<>(IdentifierClass.class);
        scored.forEach(s -> byClass.computeIfAbsent(s.entry().identifierClass(), k -> new ArrayList<>()).add(s));
        var stats = new ArrayList<ClassStats>();
        byClass.forEach((cls, records) -> stats.add(new ClassStats(
                cls,
                records.stream().anyMatch(r -> r.entry().expectedPlaceholder() != null),
                records.size(),
                count(records, Outcome.CAUGHT),
                count(records, Outcome.LEAK_FULL),
                count(records, Outcome.LEAK_PARTIAL),
                count(records, Outcome.FILLER_DAMAGED),
                (int) records.stream().filter(r -> r.score().label() == Label.CORRECT).count(),
                List.copyOf(records))));
        return stats;
    }

    private static int count(List<Scored> records, Outcome outcome) {
        return (int) records.stream().filter(r -> r.score().outcome() == outcome).count();
    }

    /** Wilson score interval for k successes in n trials at 95 %. Returns {lower, upper}. */
    static double[] wilson(int k, int n) {
        double p = (double) k / n;
        double z2 = Z * Z;
        double denominator = 1 + z2 / n;
        double centre = (p + z2 / (2 * n)) / denominator;
        double half = Z * Math.sqrt(p * (1 - p) / n + z2 / (4.0 * n * n)) / denominator;
        return new double[] {Math.max(0, centre - half), Math.min(1, centre + half)};
    }

    static String evaluationLine(ClassStats s, String setVersion) {
        var ci = s.interval();
        return String.format(Locale.ROOT, "EVALUATION E1 %s set=%s n=%d caught=%d leaked=%d recall=%s ci95=%s..%s",
                s.identifierClass(), setVersion, s.n(), s.caught(), s.n() - s.caught(), percent(s.recall()),
                percent(ci[0]), percent(ci[1]));
    }

    static String render(List<ClassStats> stats, RedactionRecallSet.Manifest manifest, String date) {
        var out = new StringBuilder();
        out.append("# E1 redaction recall\n\n");
        out.append("- Set: ").append(manifest.setVersion()).append(" (seed ").append(manifest.seed()).append(", ")
                .append(manifest.records()).append(" records)\n");
        out.append("- Redactor SHA-256 (LF): `").append(manifest.redactorSha256()).append("`\n");
        out.append("- Model: none (deterministic)\n");
        out.append(DATE_PREFIX).append(date).append("\n");
        out.append("- Command: `").append(COMMAND).append("`\n\n");
        out.append("A record is caught only when no letter or digit of the identifier survives. Partial matches "
                + "count as leaks. Classes marked \"no rule\" are expected to leak: the redactor has no rule for "
                + "them, and they are measured so the known limit is a number.\n\n");
        out.append("| Class | Rule | n | Caught | Leaked (full) | Leaked (partial) | Filler damaged | Recall | "
                + "95 % interval (Wilson) | Label accuracy |\n");
        out.append("|---|---|--:|--:|--:|--:|--:|--:|--:|--:|\n");
        for (var s : stats) {
            var ci = s.interval();
            out.append(String.format(Locale.ROOT, "| %s | %s | %d | %d | %d | %d | %d | %s | %s to %s | %s |\n",
                    s.identifierClass(), s.hasRule() ? "yes" : "no rule", s.n(), s.caught(), s.leakedFull(),
                    s.leakedPartial(), s.fillerDamaged(), percent(s.recall()), percent(ci[0]), percent(ci[1]),
                    s.hasRule() && s.caught() > 0 ? percent((double) s.labelCorrect() / s.caught()) : "n/a"));
        }
        out.append("\n## Misses by variant\n");
        appendGroups(out, stats, true);
        out.append("\n## Caught under the wrong label\n");
        appendGroups(out, stats, false);
        return out.toString();
    }

    private static void appendGroups(StringBuilder out, List<ClassStats> stats, boolean leaks) {
        boolean any = false;
        for (var s : stats) {
            Map<String, List<Scored>> groups = new LinkedHashMap<>();
            s.records().stream()
                    .filter(r -> leaks ? r.score().leaked() : r.score().label() == Label.WRONG)
                    .forEach(r -> groups.computeIfAbsent(r.entry().variant(), k -> new ArrayList<>()).add(r));
            if (groups.isEmpty()) {
                continue;
            }
            any = true;
            out.append("\n### ").append(s.identifierClass()).append("\n\n");
            groups.forEach((variant, records) -> {
                long total = s.records().stream().filter(r -> r.entry().variant().equals(variant)).count();
                out.append("- `").append(variant).append("` (").append(records.size()).append(" of ").append(total)
                        .append("): ")
                        .append(records.stream().map(r -> r.entry().id()).collect(Collectors.joining(", ")))
                        .append("\n");
            });
        }
        if (!any) {
            out.append("\nNone.\n");
        }
    }

    /** Drops the date line, so a report is compared on its numbers and not on the day it was written. */
    static String withoutDate(String report) {
        return report.lines().filter(line -> !line.startsWith(DATE_PREFIX)).collect(Collectors.joining("\n"));
    }

    static String percent(double value) {
        return String.format(Locale.ROOT, "%.1f %%", 100 * value);
    }
}
