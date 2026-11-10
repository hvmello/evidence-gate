#!/usr/bin/env python3
"""Generates the E1 redaction-recall set (seeded, standard library only).

The identifiers come from public format specifications (CPF check digits, the Brazilian numbering plan, RFC 5322
addresses, RFC 3986 URLs, the usual ways to write reais), not from Redactor.java. This script never parses or
imports the redactor. It only hashes the file, so the manifest records which version was frozen.

Usage:  python scripts/generate_redaction_set.py [--seed N] [--version v1] [--per-class 50]

Never edit a generated set. A changed redactor needs a new version and a new seed (see the set README).
"""
import argparse
import datetime
import hashlib
import json
import pathlib
import random
import unicodedata

ROOT = pathlib.Path(__file__).resolve().parent.parent
REDACTOR = ROOT / "src/main/java/dev/hvmello/evidencegate/gate/Redactor.java"
OUT_DIR = ROOT / "src/test/resources/evaluation/e1-redaction"

# (prefix, suffix) pairs. No digits, no '@', no 'R$', no 'http' or 'www', and no name token from the pools below.
FILLERS = [
    ("Bom dia, o dado que deixei no cadastro é ", " e agradeço a atenção de vocês."),
    ("Para confirmar a consulta, pode usar ", ", por favor confirmem o recebimento."),
    ("Sobre o atendimento de ontem, anotem ", " mesmo, e qualquer dúvida me avisem."),
    ("Se precisarem falar comigo, é ", " conforme combinado na última conversa."),
    ("Segue a informação que a recepção pediu: ", ". Obrigado pela paciência."),
    ("Peço que registrem na ficha ", " e fico no aguardo do retorno."),
]

# (full name, nickname or None). Tokens are rare on purpose so the filler never contains one.
PATIENT_NAMES = [
    ("Aurélio Brandão Tavares", None), ("Tereza Cavalcanti Nogueira", None), ("Murilo Pimentel Rocha", None),
    ("José Ferraz Medeiros", "Zé"), ("Francisca Albuquerque Dantas", "Chica"), ("Antônio Menezes Veloso", "Toninho"),
    ("Roberto Camargo Lacerda", "Beto"), ("Heloísa Quintanilha Borges", None),
]
PARTICLE_NAMES = [
    "Cleonice de Vasconcelos Dornelas", "Rafael Pinheiro da Fonseca", "Joana dos Reis Carneiro",
    "Wellington Mota de Alencar", "Isabela Furtado e Sampaio",
]
STAFF_NAMES = [
    ("Débora Linhares Cordeiro", None), ("Ícaro Mourão Bittencourt", None), ("Lúcia Peixoto Salgado", None),
    ("Sebastião Arruda Villela", "Tião"), ("Cristiane Valadares Coutinho", "Cris"),
    ("Valdemar Gouveia Pacheco", None), ("Ângela Siqueira Barcellos", None),
]
STAFF_PARTICLE_NAMES = ["Leonor de Magalhães Prado", "Fabrício dos Anjos Sobral", "Tatiana da Cunha Ramalho"]
OTHER_NAMES = [
    "Beatriz Cardoso", "dona Lourdes", "Doutor Ricardo Almeida", "Fulano Tavares", "Gabriela Montenegro",
    "senhor Osvaldo", "Priscila Andrade", "Bruno Albuquerque", "Dra. Simone Falcão", "Marcos Vinícius",
]
DDDS = ["11", "21", "31", "41", "51", "61", "62", "63", "64", "71", "81", "85", "91"]
WORDS = ["clinica", "agenda", "contato", "paciente", "recepcao", "consulta", "marcos", "luiza", "paulo", "carla"]
DOMAINS = ["exemplo.com.br", "correio.org", "mail.exemplo.net", "provedor.com"]
STREETS = ["Rua das Acácias", "Avenida Goiás", "Alameda dos Ipês", "Rua dos Girassóis", "Travessa da Paz"]
DISTRICTS = ["Setor Central", "Jardim Europa", "Vila Nova", "Setor Bueno", "Parque Amazônia"]
MONTHS = ["janeiro", "março", "maio", "agosto", "outubro", "dezembro"]


def digits(rng, n):
    return "".join(str(rng.randrange(10)) for _ in range(n))


def cpf_check(base9):
    nums = [int(c) for c in base9]
    for n in (9, 10):
        total = sum(d * (n + 1 - i) for i, d in enumerate(nums[:n]))
        nums.append(total * 10 % 11 % 10)
    return "".join(str(d) for d in nums)


def cpf_valid(rng):
    while True:
        base = digits(rng, 9)
        if len(set(base)) > 1:
            return cpf_check(base)


def cpf_invalid(rng):
    while True:
        value = digits(rng, 11)
        if value != cpf_check(value[:9]):
            return value


def strip_accents(text):
    return "".join(c for c in unicodedata.normalize("NFD", text) if not unicodedata.combining(c))


def mobile(rng):
    return "9" + digits(rng, 8)


def money(rng):
    return f"{rng.randrange(1, 9)}.{digits(rng, 3)},{digits(rng, 2)}"


def local(rng):
    return rng.choice(WORDS) + "." + rng.choice(WORDS) + digits(rng, rng.randrange(0, 3))


def cep(rng):
    return digits(rng, 5), digits(rng, 3)


def simple(identifier):
    """A builder result: identifier, prefix override, suffix override, patient name, staff name."""
    return identifier, None, None, None, None


def cpf_variants():
    def fmt(rng, sep1, sep2, valid=True):
        v = cpf_valid(rng) if valid else cpf_invalid(rng)
        return simple(f"{v[:3]}{sep1}{v[3:6]}{sep1}{v[6:9]}{sep2}{v[9:]}")

    def mixed(rng):
        v = cpf_valid(rng)
        return simple(f"{v[:3]}.{v[3:6]} {v[6:9]}-{v[9:]}")

    def labelled(rng):
        v = cpf_valid(rng)
        return f"{v[:3]}.{v[3:6]}.{v[6:9]}-{v[9:]}", "Para o cadastro, meu CPF: ", None, None, None

    return [
        ("dotted", lambda r: fmt(r, ".", "-")),
        ("digits-only", lambda r: simple(cpf_valid(r))),
        ("digits-only-invalid-check", lambda r: simple(cpf_invalid(r))),
        ("spaces", lambda r: fmt(r, " ", " ")),
        ("mixed-separators", mixed),
        ("slash-before-check", lambda r: fmt(r, ".", "/")),
        ("labelled", labelled),
        ("dotted-invalid-check", lambda r: fmt(r, ".", "-", valid=False)),
    ]


def phone_variants():
    def formatted(r, template):
        n = mobile(r)
        return simple(template.format(ddd=r.choice(DDDS), a=n[:5], b=n[5:], c=n[1:5]))

    return [
        ("e164-formatted", lambda r: formatted(r, "+55 ({ddd}) {a}-{b}")),
        ("parenthesised", lambda r: formatted(r, "({ddd}) {a}-{b}")),
        ("raw-digits", lambda r: formatted(r, "{ddd}{a}{b}")),
        ("nine-without-ddd", lambda r: formatted(r, "9 {c}-{b}")),
        ("landline", lambda r: simple(f"3{digits(r, 3)}-{digits(r, 4)}")),
        ("carrier-prefix", lambda r: formatted(r, "0 {ddd} {a}-{b}")),
        ("slash-separator", lambda r: formatted(r, "({ddd}) {a}/{b}")),
        ("dot-separators", lambda r: formatted(r, "{ddd}.{a}.{b}")),
        ("landline-with-ddd", lambda r: simple(f"({r.choice(DDDS)}) 3{digits(r, 3)}-{digits(r, 4)}")),
    ]


def email_variants():
    def build(local_part, domain):
        return simple(f"{local_part}@{domain}")

    return [
        ("plain", lambda r: build(local(r), r.choice(DOMAINS))),
        ("plus-addressing", lambda r: build(local(r) + "+" + r.choice(WORDS), r.choice(DOMAINS))),
        ("subdomain", lambda r: build(local(r), "mail." + r.choice(WORDS) + ".exemplo.org")),
        ("uppercase", lambda r: build(local(r).upper(), r.choice(DOMAINS).upper())),
        ("accented-local-part", lambda r: build("josé." + r.choice(WORDS), r.choice(DOMAINS))),
        ("spelled-out", lambda r: simple(f"{r.choice(WORDS)} arroba {r.choice(WORDS)} ponto com")),
    ]


def link_variants():
    def path(r):
        return "/" + r.choice(WORDS) + "/" + digits(r, 3)

    def trailing(r):
        return "https://" + r.choice(DOMAINS) + path(r), None, ". Obrigado pela paciência.", None, None

    return [
        ("https", lambda r: simple("https://" + r.choice(DOMAINS) + path(r))),
        ("http", lambda r: simple("http://" + r.choice(DOMAINS) + path(r))),
        ("www", lambda r: simple("www." + r.choice(DOMAINS) + path(r))),
        ("bare-domain-with-path", lambda r: simple(r.choice(DOMAINS) + path(r))),
        ("whatsapp-short-link", lambda r: simple("wa.me/55" + r.choice(DDDS) + mobile(r))),
        ("trailing-punctuation", trailing),
        ("shortener", lambda r: simple("bit.ly/" + digits(r, 3) + "Xy")),
    ]


def amount_variants():
    return [
        ("reais-symbol-spaced", lambda r: simple("R$ " + money(r))),
        ("reais-symbol-tight", lambda r: simple("R$" + digits(r, 4))),
        ("thousands-reais-word", lambda r: simple(f"{r.randrange(1, 9)}.{digits(r, 3)} reais")),
        ("brl-code", lambda r: simple("BRL " + digits(r, 3))),
        ("lowercase-symbol", lambda r: simple("r$ " + str(r.randrange(2, 900)) + "," + digits(r, 2))),
    ]


def name_variants(pool, particle_pool, patient):
    def names(full):
        return (full, None) if patient else (None, full)

    def derived(r, fn, supplied=lambda full: full):
        full, nick = r.choice(pool)
        return (fn(full),) + (None, None) + names(supplied(full))

    def particle(r):
        full = r.choice(particle_pool)
        return (full, None, None) + names(full)

    def nickname(r):
        full, nick = r.choice([entry for entry in pool if entry[1]])
        return (nick, None, None) + names(full)

    return [
        ("full-name", lambda r: derived(r, lambda f: f)),
        ("first-name", lambda r: derived(r, lambda f: f.split()[0])),
        ("last-name", lambda r: derived(r, lambda f: f.split()[-1])),
        ("upper-case", lambda r: derived(r, lambda f: f.upper())),
        ("lower-case", lambda r: derived(r, lambda f: f.lower())),
        ("accents-dropped", lambda r: derived(r, strip_accents)),
        ("accents-added", lambda r: derived(r, lambda f: f, strip_accents)),
        ("particle", particle),
        ("possessive", lambda r: derived(r, lambda f: f.split()[0] + "'s")),
        ("hashtag", lambda r: derived(r, lambda f: "#" + strip_accents(f.split()[0]).lower())),
        ("diminutive-or-nickname", nickname),
    ]


def classes():
    """class -> (expected placeholder or None, [(variant, builder)])."""
    return {
        "CPF": ("[CPF]", cpf_variants()),
        "PHONE": ("[PHONE]", phone_variants()),
        "EMAIL": ("[EMAIL]", email_variants()),
        "LINK": ("[LINK]", link_variants()),
        "AMOUNT": ("[AMOUNT]", amount_variants()),
        "PATIENT_NAME": ("[PATIENT_NAME]", name_variants(PATIENT_NAMES, PARTICLE_NAMES, True)),
        "STAFF_NAME": ("[STAFF_NAME]", name_variants(STAFF_NAMES, STAFF_PARTICLE_NAMES, False)),
        "RG": (None, [
            ("dotted", lambda r: (lambda n: simple(f"{n[:2]}.{n[2:5]}.{n[5:]}-{digits(r, 1)}"))(digits(r, 7))),
            ("with-issuer", lambda r: simple(f"{digits(r, 7)} SSP/GO")),
            ("prefixed-state", lambda r: simple(f"MG-{digits(r, 2)}.{digits(r, 3)}.{digits(r, 3)}")),
        ]),
        "CEP": (None, [
            ("hyphen", lambda r: (lambda a, b: simple(f"{a}-{b}"))(*cep(r))),
            ("dotted", lambda r: (lambda a, b: simple(f"{a[:2]}.{a[2:]}-{b}"))(*cep(r))),
            ("digits-only", lambda r: (lambda a, b: simple(a + b))(*cep(r))),
            ("labelled", lambda r: (lambda a, b: (f"{a}-{b}", "Moro na cidade, o CEP é ", None, None, None))(*cep(r))),
        ]),
        "CNS": (None, [
            ("grouped", lambda r: simple(f"7{digits(r, 2)} {digits(r, 4)} {digits(r, 4)} {digits(r, 4)}")),
            ("digits-only", lambda r: simple("7" + digits(r, 14))),
            ("dotted", lambda r: simple(f"1{digits(r, 2)}.{digits(r, 4)}.{digits(r, 4)}.{digits(r, 4)}")),
        ]),
        "BIRTH_DATE": (None, [
            ("slashes", lambda r: simple(f"{r.randrange(10, 28)}/{r.randrange(10, 12)}/{r.randrange(1950, 2010)}")),
            ("written-out", lambda r: simple(f"{r.randrange(10, 28)} de {r.choice(MONTHS)} de {r.randrange(1950, 2010)}")),
            ("short-year", lambda r: simple(f"{r.randrange(10, 28)}-{r.randrange(10, 12)}-{r.randrange(50, 99)}")),
            ("year-only", lambda r: simple(str(r.randrange(1925, 1950)))),
        ]),
        "ADDRESS": (None, [
            ("street-block-lot", lambda r: simple(f"{r.choice(STREETS)}, quadra {r.randrange(10, 90)}, lote {r.randrange(1, 9)}")),
            ("street-number-district", lambda r: simple(f"{r.choice(STREETS)}, {r.randrange(100, 2000)}, {r.choice(DISTRICTS)}")),
            ("house-number", lambda r: simple(f"{r.choice(STREETS)}, número {r.randrange(10, 99)}")),
        ]),
        "OTHER_NAME": (None, [
            ("unsupplied-name", lambda r: simple(r.choice(OTHER_NAMES))),
        ]),
    }


def sha256_lf(path):
    return hashlib.sha256(path.read_bytes().replace(b"\r\n", b"\n")).hexdigest()


def nfc(text):
    return unicodedata.normalize("NFC", text)


def generate(seed, per_class):
    rng = random.Random(seed)
    records = []
    for cls, (placeholder, variants) in classes().items():
        for i in range(per_class):
            variant, build = variants[i % len(variants)]
            identifier, prefix, suffix, patient, staff = build(rng)
            default_prefix, default_suffix = rng.choice(FILLERS)
            speaker = rng.choice(["PATIENT", "STAFF"])
            default_patient = rng.choice(PATIENT_NAMES)[0]
            default_staff = rng.choice(STAFF_NAMES)[0]
            records.append({
                "id": f"{cls.lower().replace('_', '-')}-{i + 1:02d}",
                "class": cls,
                "variant": variant,
                "expectedPlaceholder": placeholder,
                "speaker": speaker,
                "patientName": nfc(patient or default_patient),
                "staffName": nfc(staff or default_staff),
                "prefix": nfc(prefix or default_prefix),
                "identifier": nfc(identifier),
                "suffix": nfc(suffix or default_suffix),
            })
    return records


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--seed", type=int, default=19)
    parser.add_argument("--version", default="v1")
    parser.add_argument("--per-class", type=int, default=50)
    args = parser.parse_args()

    records = generate(args.seed, args.per_class)
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    set_path = OUT_DIR / f"{args.version}.jsonl"
    with open(set_path, "w", encoding="utf-8", newline="\n") as handle:
        for record in records:
            handle.write(json.dumps(record, ensure_ascii=False) + "\n")
    manifest = {
        "setVersion": args.version,
        "seed": args.seed,
        "date": datetime.date.today().isoformat(),
        "records": len(records),
        "generatorSha256": sha256_lf(pathlib.Path(__file__).resolve()),
        "redactorSha256": sha256_lf(REDACTOR),
        "setSha256": sha256_lf(set_path),
    }
    with open(OUT_DIR / "manifest.json", "w", encoding="utf-8", newline="\n") as handle:
        handle.write(json.dumps(manifest, indent=2) + "\n")
    print(f"wrote {len(records)} records to {set_path.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
