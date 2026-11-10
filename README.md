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

Work is tracked in [issues](https://github.com/hvmello/evidence-gate/issues) and follows [this workflow](docs/WORKFLOW.md).
