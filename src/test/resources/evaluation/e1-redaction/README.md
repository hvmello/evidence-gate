# E1 redaction-recall set

Set version: **v1** (see `manifest.json` for the seed, the date and the hashes). Synthetic data in Brazilian
Portuguese. It measures `Redactor` alone: no model, no network.

## How it is made

`scripts/generate_redaction_set.py` (Python standard library only, seeded) writes `v1.jsonl` and `manifest.json`.
It builds identifiers from public format specifications, not from `Redactor.java`, and never parses or imports the
redactor. It only hashes the file, so the manifest records which redactor was frozen.

Each record is `prefix + identifier + suffix`: one identifier inside a short pt-BR sentence. The filler has no
digit, no `@`, no `R$`, no `http` or `www`, and none of the supplied name tokens, so anything that survives
redaction can only come from the identifier. The loader fails if that is not true.

| Field | Meaning |
|---|---|
| `id` | Unique, `<class>-<nn>` |
| `class` | One of the classes below |
| `variant` | The format being tried, used to group misses |
| `expectedPlaceholder` | The placeholder the redactor should use, or `null` when it has no rule for the class |
| `speaker`, `patientName`, `staffName` | Sent with the conversation. Names are the supplied names |
| `prefix`, `identifier`, `suffix` | The sentence parts |

## Classes and format sources

| Class | Formats tried |
|---|---|
| `CPF` | Receita Federal layout `000.000.000-00`, digits only, spaces, mixed separators, `/` before the check digits, `CPF:` label, valid and invalid check digits |
| `PHONE` | ITU E.164 with the national format, ANATEL numbering plan (DDD, ninth digit, landlines), `0 DDD` carrier prefix, other separators |
| `EMAIL` | RFC 5322 shapes: plus addressing, subdomains, upper case, accented local part, spelled out in words |
| `LINK` | RFC 3986 URLs: `https://`, `http://`, `www.`, bare domain with a path, `wa.me`, shortener, trailing punctuation |
| `AMOUNT` | `R$ 1.234,56`, `R$1234`, `1.200 reais`, `BRL 300`, lower case `r$` |
| `PATIENT_NAME`, `STAFF_NAME` | Full or partial supplied name, changed case, accents added or dropped, particles, possessive, hashtag, nickname |
| `RG`, `CEP`, `CNS`, `BIRTH_DATE`, `ADDRESS`, `OTHER_NAME` | No rule today. Expected to leak, measured anyway |

## The leak rule

Redact the record, remove every `[A-Z_]+` placeholder, then reduce the result and the filler to letters and digits.
Strip the prefix from the start and the suffix from the end.

- Anything left over is a **leak**, partial or full. Leftover particles (`de`, `da`) and possessive endings (`s` from
  `Murilo's`) count too, because the rule does not guess what is identifying.
- If the prefix or suffix is no longer intact the record is a leak flagged `FILLER_DAMAGED`. When in doubt, the record
  counts against the redactor.
- Otherwise the identifier was **caught**. Label accuracy is reported separately.

## Rules for changing the set

- Never edit or drop a record. `manifest.json` holds the hash of the set, and the evaluation test fails if it changes.
- A changed `Redactor.java` fails the test until a new set version exists. Tuning the redactor on a set and
  measuring on the same set would inflate recall. Run the generator with a new `--version` and a new `--seed`, then
  rewrite the report.
- Identifiers must not appear in `RedactorTest.java` or in the regression fixtures. A test checks it.
- Independence is procedural: the generator was written by someone who has seen the redactor. It is a separate
  language, never reads the redactor, and takes its variants from the specifications above.
