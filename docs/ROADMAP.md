# evidence-gate roadmap

## Objective

Use a language model on sensitive text without trusting its output. `evidence-gate` is a Java 21 service
that places a model between deterministic checks:

- known identifier formats are removed before the model call;
- every citation is verified verbatim against the message it names;
- drafts must pass deterministic rules;
- a human reviews everything, and the service never sends a message.

**Who it is for:** an engineering manager hiring a backend engineer for an AI team. In five minutes they
should see a running demo, real model behaviour with numbers, and code they would accept in review.

**Use case (synthetic data only, in Brazilian Portuguese):** a clinic receives patient messages. The service
classifies whether the patient intends to continue care, cites the sentences behind that, and drafts a reply.

## Pipeline

```text
request -> consent check -> Redactor -> LlmPort.classify -> EvidenceGate -> LlmPort.draft -> PolicyFilter -> result
                                  AuditLog: one row per assessment, hashes and codes only
```

The model's own self-assessment never decides anything. Citations are checked against the exact redacted
text the model received. The reviewer sees the redacted quote and a message id that leads to the original.

| Outcome | Meaning |
|---|---|
| `PASSED` | Every check passed |
| `NO_CONSENT` | Stopped before any model call |
| `INSUFFICIENT_EVIDENCE` | The model abstained, which is a valid answer |
| `EVIDENCE_MISMATCH` | A citation is missing, too short, or not in the message it names |
| `POLICY_BLOCKED` | The draft broke a deterministic rule |
| `MODEL_FAILED` | Refusal, truncation, invalid output or transport error |

**What the checks do not do.** `EvidenceGate` shows that a sentence exists, not that it supports the label.
Drafts are not grounded in sources until M5. `Redactor` misses identifiers it has no pattern for, and
pseudonymised health text stays sensitive. These limits are measured, and the README must state them.

## Decisions

| Decision | Choice | Why |
|---|---|---|
| Runtime | Java 21, Spring Boot 4.1.2 | Current stable release |
| LLM access | LangChain4j 1.23.0 behind `LlmPort`, confirmed by the M2 spike | Provider-neutral and framework-independent; Spring AI would tie the adapter to the Spring release cycle |
| Default model | `claude-opus-5-5`, set by configuration | Current default; changed without code edits |
| Offline modes | `ScriptedLlm` for the demo, `RecordedLlm` for CI | No API key needed to run or to verify |
| Control flow | No agent loop in the core path | The steps are known in advance, so code owns them |

## Metrics

Each number comes from a committed command. A number enters the README only after that command produced it.

### Regression suites (every push, no API key)

These prove the checks still work. They are not evidence of real-world safety, and a check that rejects
everything would pass R2 to R4, which is why R6 exists.

| ID | Suite | Target |
|---|---|---|
| R1 | Consent: model calls made without consent | 0 |
| R2 | Fabrication: invented, wrong-message, spanning and empty citations reaching `PASSED` | 0 |
| R3 | Policy: forbidden drafts reaching `PASSED`, including zero-width and accent tricks | 0 |
| R4 | Canary leak: seeded identifiers found in payloads, logs, audit rows, spans and exceptions | 0 |
| R5 | Test strength: PIT mutation score on the `gate` package | At least 85 % |
| R6 | Legitimate drafts blocked by `PolicyFilter` | At most 5 % |

### Evaluation (on demand, real model, published with dataset version, model and date)

| ID | Metric | Definition | Target |
|---|---|---|---|
| E1 | Redaction recall | Per identifier class, partial matches counted as leaks, on a set generated independently after the redactor is frozen | Reported |
| E2 | Classification | Macro-F1 on held-out data (at least 150 conversations) with bootstrap CI, against a majority-class baseline | Baseline first, then at least 0.80 |
| E3 | Label reliability | Cohen's kappa with a second annotator on at least 40 items | Reported |
| E4 | False rejection | Citations a human judged near-verbatim that `EvidenceGate` rejected | At most 5 % |
| E5 | Caught in the wild | Fabrications and policy violations produced by the real model and stopped | Reported |
| E6 | Injection | Attack (at least 50 cases, repeated) against the same conversation without the attack | Reported with CI |
| E7 | Comparison | Prompt v1 against v2, and Opus against a cheaper model, on quality and cost | Reported |
| O1 | Cost and latency | US$ per 1,000 conversations; p95 end to end; check overhead | Reported; overhead p99 under 5 ms |
| D1 | Time to first run | From clone to working request, no API key | Under 5 minutes |

**Known limit:** labels are synthetic and written by the author, so E2 measures consistency against that set.
It does not predict performance on real patients.

## Milestones

Estimates are focused hours and are not commitments.

| | Milestone | Hours | Done when | Status |
|---|---|--:|---|---|
| M0 | Skeleton: build, CI, architecture rules | 2 | CI green on a clean clone | Local build green; CI not run |
| M1 | Deterministic pipeline, `ScriptedLlm`, API | 14 | R1 to R6 measured on fixtures | Fixtures done; PIT pending (#12) |
| M2a | Spike, go or no-go | 1 | Structured output through `customParameters`, refusal detection and Jackson fit verified; fallback chosen | |
| M2b | Real adapter | 11 | Contract suite asserts shape and failure mapping for scripted and real adapters | |
| M3 | Evaluation and `RecordedLlm` | 20 | E1 to E7 published; prompts tuned on train split only; CI replays committed real responses | |
| v0.1 | **Public release** | 4 | Diagram, demo, results, limits; D1 timed by someone else; repository pinned | |
| M4 | Production concerns | 10 | Postgres audit with Testcontainers, metrics, GenAI spans, cost limit; R4 covers all sinks | |
| M5 | Retrieval | 12 | Replies grounded with pgvector; citations verified against chunks; recall@5 published | |

Audit rows gain prompt version and the failing check in M1 and need no content to explain a failure.

## Out of scope

Real patient data, web UI, multi-tenancy, fine-tuning, autonomous sending of messages.
