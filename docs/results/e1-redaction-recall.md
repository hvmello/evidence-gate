# E1 redaction recall

- Set: v1 (seed 20, 650 records)
- Redactor SHA-256 (LF): `52d97879f6e2e65311970e8bea1b6502b8ba1e33b78b70f52f4434dbddc32079`
- Model: none (deterministic)
- Date: 2026-11-10
- Command: `./mvnw -B test -Dgroups=evaluation -De1.write=true`

A record is caught only when no letter or digit of the identifier survives. Partial matches count as leaks. Classes marked "no rule" are expected to leak: the redactor has no rule for them, and they are measured so the known limit is a number.

| Class | Rule | n | Caught | Leaked (full) | Leaked (partial) | Filler damaged | Recall | 95 % interval (Wilson) | Label accuracy |
|---|---|--:|--:|--:|--:|--:|--:|--:|--:|
| CPF | yes | 50 | 44 | 5 | 1 | 0 | 88.0 % | 76.2 % to 94.4 % | 97.7 % |
| PHONE | yes | 50 | 40 | 5 | 5 | 0 | 80.0 % | 67.0 % to 88.8 % | 100.0 % |
| EMAIL | yes | 50 | 42 | 8 | 0 | 0 | 84.0 % | 71.5 % to 91.7 % | 100.0 % |
| LINK | yes | 50 | 29 | 14 | 7 | 0 | 58.0 % | 44.2 % to 70.6 % | 100.0 % |
| AMOUNT | yes | 50 | 30 | 20 | 0 | 0 | 60.0 % | 46.2 % to 72.4 % | 100.0 % |
| PATIENT_NAME | yes | 50 | 38 | 4 | 8 | 0 | 76.0 % | 62.6 % to 85.7 % | 100.0 % |
| STAFF_NAME | yes | 50 | 38 | 4 | 8 | 0 | 76.0 % | 62.6 % to 85.7 % | 100.0 % |
| RG | no rule | 50 | 0 | 50 | 0 | 0 | 0.0 % | 0.0 % to 7.1 % | n/a |
| CEP | no rule | 50 | 12 | 38 | 0 | 0 | 24.0 % | 14.3 % to 37.4 % | n/a |
| CNS | no rule | 50 | 0 | 17 | 33 | 0 | 0.0 % | 0.0 % to 7.1 % | n/a |
| BIRTH_DATE | no rule | 50 | 0 | 50 | 0 | 0 | 0.0 % | 0.0 % to 7.1 % | n/a |
| ADDRESS | no rule | 50 | 0 | 50 | 0 | 0 | 0.0 % | 0.0 % to 7.1 % | n/a |
| OTHER_NAME | no rule | 50 | 0 | 49 | 1 | 0 | 0.0 % | 0.0 % to 7.1 % | n/a |

## Misses by variant

### CPF

- `slash-before-check` (6 of 6): cpf-06, cpf-14, cpf-22, cpf-30, cpf-38, cpf-46

### PHONE

- `carrier-prefix` (5 of 5): phone-06, phone-15, phone-24, phone-33, phone-42
- `slash-separator` (5 of 5): phone-07, phone-16, phone-25, phone-34, phone-43

### EMAIL

- `spelled-out` (8 of 8): email-06, email-12, email-18, email-24, email-30, email-36, email-42, email-48

### LINK

- `bare-domain-with-path` (7 of 7): link-04, link-11, link-18, link-25, link-32, link-39, link-46
- `whatsapp-short-link` (7 of 7): link-05, link-12, link-19, link-26, link-33, link-40, link-47
- `shortener` (7 of 7): link-07, link-14, link-21, link-28, link-35, link-42, link-49

### AMOUNT

- `thousands-reais-word` (10 of 10): amount-03, amount-08, amount-13, amount-18, amount-23, amount-28, amount-33, amount-38, amount-43, amount-48
- `brl-code` (10 of 10): amount-04, amount-09, amount-14, amount-19, amount-24, amount-29, amount-34, amount-39, amount-44, amount-49

### PATIENT_NAME

- `particle` (4 of 4): patient-name-08, patient-name-19, patient-name-30, patient-name-41
- `possessive` (4 of 4): patient-name-09, patient-name-20, patient-name-31, patient-name-42
- `diminutive-or-nickname` (4 of 4): patient-name-11, patient-name-22, patient-name-33, patient-name-44

### STAFF_NAME

- `particle` (4 of 4): staff-name-08, staff-name-19, staff-name-30, staff-name-41
- `possessive` (4 of 4): staff-name-09, staff-name-20, staff-name-31, staff-name-42
- `diminutive-or-nickname` (4 of 4): staff-name-11, staff-name-22, staff-name-33, staff-name-44

### RG

- `dotted` (17 of 17): rg-01, rg-04, rg-07, rg-10, rg-13, rg-16, rg-19, rg-22, rg-25, rg-28, rg-31, rg-34, rg-37, rg-40, rg-43, rg-46, rg-49
- `with-issuer` (17 of 17): rg-02, rg-05, rg-08, rg-11, rg-14, rg-17, rg-20, rg-23, rg-26, rg-29, rg-32, rg-35, rg-38, rg-41, rg-44, rg-47, rg-50
- `prefixed-state` (16 of 16): rg-03, rg-06, rg-09, rg-12, rg-15, rg-18, rg-21, rg-24, rg-27, rg-30, rg-33, rg-36, rg-39, rg-42, rg-45, rg-48

### CEP

- `hyphen` (13 of 13): cep-01, cep-05, cep-09, cep-13, cep-17, cep-21, cep-25, cep-29, cep-33, cep-37, cep-41, cep-45, cep-49
- `dotted` (13 of 13): cep-02, cep-06, cep-10, cep-14, cep-18, cep-22, cep-26, cep-30, cep-34, cep-38, cep-42, cep-46, cep-50
- `labelled` (12 of 12): cep-04, cep-08, cep-12, cep-16, cep-20, cep-24, cep-28, cep-32, cep-36, cep-40, cep-44, cep-48

### CNS

- `grouped` (17 of 17): cns-01, cns-04, cns-07, cns-10, cns-13, cns-16, cns-19, cns-22, cns-25, cns-28, cns-31, cns-34, cns-37, cns-40, cns-43, cns-46, cns-49
- `digits-only` (17 of 17): cns-02, cns-05, cns-08, cns-11, cns-14, cns-17, cns-20, cns-23, cns-26, cns-29, cns-32, cns-35, cns-38, cns-41, cns-44, cns-47, cns-50
- `dotted` (16 of 16): cns-03, cns-06, cns-09, cns-12, cns-15, cns-18, cns-21, cns-24, cns-27, cns-30, cns-33, cns-36, cns-39, cns-42, cns-45, cns-48

### BIRTH_DATE

- `slashes` (13 of 13): birth-date-01, birth-date-05, birth-date-09, birth-date-13, birth-date-17, birth-date-21, birth-date-25, birth-date-29, birth-date-33, birth-date-37, birth-date-41, birth-date-45, birth-date-49
- `written-out` (13 of 13): birth-date-02, birth-date-06, birth-date-10, birth-date-14, birth-date-18, birth-date-22, birth-date-26, birth-date-30, birth-date-34, birth-date-38, birth-date-42, birth-date-46, birth-date-50
- `short-year` (12 of 12): birth-date-03, birth-date-07, birth-date-11, birth-date-15, birth-date-19, birth-date-23, birth-date-27, birth-date-31, birth-date-35, birth-date-39, birth-date-43, birth-date-47
- `year-only` (12 of 12): birth-date-04, birth-date-08, birth-date-12, birth-date-16, birth-date-20, birth-date-24, birth-date-28, birth-date-32, birth-date-36, birth-date-40, birth-date-44, birth-date-48

### ADDRESS

- `street-block-lot` (17 of 17): address-01, address-04, address-07, address-10, address-13, address-16, address-19, address-22, address-25, address-28, address-31, address-34, address-37, address-40, address-43, address-46, address-49
- `street-number-district` (17 of 17): address-02, address-05, address-08, address-11, address-14, address-17, address-20, address-23, address-26, address-29, address-32, address-35, address-38, address-41, address-44, address-47, address-50
- `house-number` (16 of 16): address-03, address-06, address-09, address-12, address-15, address-18, address-21, address-24, address-27, address-30, address-33, address-36, address-39, address-42, address-45, address-48

### OTHER_NAME

- `unsupplied-name` (50 of 50): other-name-01, other-name-02, other-name-03, other-name-04, other-name-05, other-name-06, other-name-07, other-name-08, other-name-09, other-name-10, other-name-11, other-name-12, other-name-13, other-name-14, other-name-15, other-name-16, other-name-17, other-name-18, other-name-19, other-name-20, other-name-21, other-name-22, other-name-23, other-name-24, other-name-25, other-name-26, other-name-27, other-name-28, other-name-29, other-name-30, other-name-31, other-name-32, other-name-33, other-name-34, other-name-35, other-name-36, other-name-37, other-name-38, other-name-39, other-name-40, other-name-41, other-name-42, other-name-43, other-name-44, other-name-45, other-name-46, other-name-47, other-name-48, other-name-49, other-name-50

## Caught under the wrong label

### CPF

- `digits-only-invalid-check` (1 of 6): cpf-03
