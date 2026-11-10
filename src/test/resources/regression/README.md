# Regression fixtures

Fixture version: **v3**. Synthetic data in Brazilian Portuguese only. Each suite prints this version.

One JSON object per line (JSON Lines). Unknown fields, malformed lines and duplicate ids fail the load.

| Field | Used by | Meaning |
|---|---|---|
| `id` | all | Unique inside the file. Printed when the case fails |
| `category` | all | What the case tries |
| `conversation` | R1 to R4 | A `Conversation` as the API takes it |
| `classification` | R2 to R4 | What the model stub returns from `classify` |
| `draft` | R3, R6 | What the model stub returns from `draft` (R3), or the reply checked directly (R6) |
| `intent` | R6 | Intent the reply is for |
| `canaries` | R4 | Strings seeded in the conversation that must appear nowhere |
| `expected` | R2, R3 | The reason code the case must end with |
| `apiBody` | R1 | A raw request body sent to `POST /v1/assessments` |

| File | Suite | Number | Target |
|---|---|---|---|
| `r1-consent.jsonl` | R1 | Model calls without consent | 0 |
| `r2-fabrication.jsonl` | R2 | Fabricated citations reaching `PASSED` | 0 |
| `r3-policy.jsonl` | R3 | Forbidden drafts reaching `PASSED` | 0 |
| `r4-canary.jsonl` | R4 | (Canary, sink) hits | 0 |
| `r6-legitimate.jsonl` | R6 | Legitimate drafts blocked | at most 5 % |

Rules for changing a fixture:

- A fix for a known bug adds its case to the matching file and bumps the version. Cases for open bugs
  (#9, #38) are not in v1 because they would fail today.
- Never edit or drop a case to make a number pass. R6 is written from realistic replies, not tuned.
- Invisible and unusual characters are written as `\uXXXX` escapes so a reviewer can see them.
