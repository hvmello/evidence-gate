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

Work is tracked in [issues](https://github.com/hvmello/evidence-gate/issues) and follows [this workflow](docs/WORKFLOW.md).
