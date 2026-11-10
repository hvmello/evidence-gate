# evidence-gate

A Java 21 service that uses a language model on sensitive text without trusting its output.

> **Status: in progress.** The deterministic pipeline runs against a scripted stand-in for the model.
> Real model integration and evaluation results are next. See the [roadmap](docs/ROADMAP.md).

```text
request -> consent check -> Redactor -> model: classify -> EvidenceGate -> model: draft -> PolicyFilter -> result
```

- Known identifier formats and supplied names are removed before any model call.
- Every citation must exist verbatim in the message it names.
- Drafts must pass deterministic rules, and a human reviews everything. The service never sends a message.
- The audit log holds hashes and codes only.

These checks have limits, which the roadmap states and measures: a citation that exists does not prove the
label is right, and the redactor misses identifiers it has no pattern for.

## Run the tests

Requires JDK 21. No API key is needed.

```bash
./mvnw verify
```

## Mutation testing

PIT measures how many deliberate code changes (mutants) the tests catch. Scope: `dev.hvmello.evidencegate.gate`,
tested only by the `gate.*Test` classes. Mutators: PIT `DEFAULTS`. The build fails below a mutation score of 85 %.

```bash
./mvnw test-compile org.pitest:pitest-maven:mutationCoverage
```

The report is written to `target/pit-reports/index.html`. It is not part of `verify`; CI runs it as a separate step
and uploads the report when it fails.

## Regression suites

Five suites run the real pipeline against versioned fixtures in `src/test/resources/regression/`. Only the
model is replaced by a stub, so no API key is needed. Each prints its number:

```bash
./mvnw -B test -Dgroups=regression
```

```text
REGRESSION R1 consent fixtures=v1 value=0 total=9 target=0
REGRESSION R2 fabrication fixtures=v1 value=0 total=19 target=0
REGRESSION R3 policy fixtures=v1 value=0 total=47 target=0
REGRESSION R4 canary-leak fixtures=v1 value=0 total=162 target=0
REGRESSION R6 legitimate fixtures=v1 value=2.2% total=46 target=<=5.0%
REGRESSION R6 failing: r6-17 (TOO_MANY_QUESTIONS)
```

`./mvnw verify` runs them with every other test. They show the checks still work on these cases, not that
the service is safe on real patients. See the [fixture notes](src/test/resources/regression/README.md).

## Evaluation

### E1: redaction recall

`Redactor` is measured alone, with no model and no API key, on 650 synthetic records (50 per identifier class) generated after the redactor was frozen. A record counts as caught only when no letter or digit of the identifier survives, so partial matches are leaks.

```bash
./mvnw -B test -Dgroups=evaluation -De1.write=true
```

| Class | Rule | n | Recall | 95 % interval (Wilson) |
|---|---|--:|--:|--:|
| CPF | yes | 50 | 88.0 % | 76.2 % to 94.4 % |
| PHONE | yes | 50 | 80.0 % | 67.0 % to 88.8 % |
| EMAIL | yes | 50 | 84.0 % | 71.5 % to 91.7 % |
| LINK | yes | 50 | 58.0 % | 44.2 % to 70.6 % |
| AMOUNT | yes | 50 | 60.0 % | 46.2 % to 72.4 % |
| PATIENT_NAME | yes | 50 | 76.0 % | 62.6 % to 85.7 % |
| STAFF_NAME | yes | 50 | 76.0 % | 62.6 % to 85.7 % |
| RG | no rule | 50 | 0.0 % | 0.0 % to 7.1 % |
| CEP | no rule | 50 | 24.0 % | 14.3 % to 37.4 % |
| CNS | no rule | 50 | 0.0 % | 0.0 % to 7.1 % |
| BIRTH_DATE | no rule | 50 | 0.0 % | 0.0 % to 7.1 % |
| ADDRESS | no rule | 50 | 0.0 % | 0.0 % to 7.1 % |
| OTHER_NAME | no rule | 50 | 0.0 % | 0.0 % to 7.1 % |

The full report, with misses grouped by variant, is in [docs/results/e1-redaction-recall.md](docs/results/e1-redaction-recall.md). Limits: the set is synthetic and written by the same author as the redactor, so independence is procedural (see the [set notes](src/test/resources/evaluation/e1-redaction/README.md)). Classes marked "no rule" (RG, CEP, CNS, birth date, address, names that were not supplied) are expected to leak and are measured so that limit is a number. `./mvnw verify` fails if the redactor, the generator or the set change without a new set version, or if the committed report is stale.

Work is tracked in [issues](https://github.com/hvmello/evidence-gate/issues) and follows [this workflow](docs/WORKFLOW.md).
