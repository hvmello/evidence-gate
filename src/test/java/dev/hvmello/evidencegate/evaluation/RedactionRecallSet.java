package dev.hvmello.evidencegate.evaluation;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.hvmello.evidencegate.domain.Conversation;
import dev.hvmello.evidencegate.domain.Conversation.Message;
import dev.hvmello.evidencegate.domain.Conversation.Speaker;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Loads and validates the E1 set. A malformed line, an unknown field, a duplicate id, an unknown class or filler
 * that could itself contain an identifier fails the load, so a broken record cannot silently raise recall.
 */
final class RedactionRecallSet {

    static final String RESOURCE_DIR = "/evaluation/e1-redaction/";

    private static final JsonMapper MAPPER =
            JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    private static final Pattern FILLER_FORBIDDEN =
            Pattern.compile("\\d|@|R\\$|http|www", Pattern.CASE_INSENSITIVE);
    private static final Set<String> NAME_PARTICLES = Set.of("de", "da", "do", "das", "dos", "e");

    enum IdentifierClass {
        CPF, PHONE, EMAIL, LINK, AMOUNT, PATIENT_NAME, STAFF_NAME, RG, CEP, CNS, BIRTH_DATE, ADDRESS, OTHER_NAME
    }

    /** One record: a sentence with exactly one identifier between inert filler. */
    record Entry(
            String id,
            @JsonProperty("class") IdentifierClass identifierClass,
            String variant,
            String expectedPlaceholder,
            Speaker speaker,
            String patientName,
            String staffName,
            String prefix,
            String identifier,
            String suffix) {

        Conversation conversation() {
            return conversationWith(prefix + identifier + suffix);
        }

        Conversation conversationWith(String body) {
            return new Conversation("e1-" + id, true, patientName, staffName, List.of(new Message(1, speaker, body)));
        }
    }

    record Manifest(
            String setVersion,
            int seed,
            String date,
            int records,
            String generatorSha256,
            String redactorSha256,
            String setSha256) { }

    private RedactionRecallSet() { }

    static Manifest loadManifest() {
        return MAPPER.readValue(resource("manifest.json"), Manifest.class);
    }

    static String setText(Manifest manifest) {
        return resource(manifest.setVersion() + ".jsonl");
    }

    static List<Entry> load(Manifest manifest) {
        return parse(manifest.setVersion() + ".jsonl", setText(manifest).lines().toList());
    }

    static List<Entry> parse(String source, List<String> lines) {
        var entries = new java.util.ArrayList<Entry>();
        var ids = new HashSet<String>();
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            var where = source + " line " + (i + 1);
            Entry entry;
            try {
                entry = MAPPER.readValue(line, Entry.class);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(where + ": " + e.getMessage(), e);
            }
            requireFields(where, entry);
            if (!ids.add(entry.id())) {
                throw new IllegalArgumentException(where + ": duplicate id " + entry.id());
            }
            checkFiller(where, entry);
            entries.add(entry);
        }
        if (entries.isEmpty()) {
            throw new IllegalArgumentException(source + ": no records");
        }
        return List.copyOf(entries);
    }

    private static void requireFields(String where, Entry e) {
        if (e.id() == null || e.id().isBlank() || e.identifierClass() == null || e.variant() == null
                || e.speaker() == null || e.patientName() == null || e.staffName() == null || e.prefix() == null
                || e.identifier() == null || e.identifier().isBlank() || e.suffix() == null) {
            throw new IllegalArgumentException(where + ": a required field is missing");
        }
    }

    /** The filler may hold no digit, no mail, money or link marker and no supplied name token. */
    private static void checkFiller(String where, Entry e) {
        for (var filler : List.of(e.prefix(), e.suffix())) {
            if (FILLER_FORBIDDEN.matcher(filler).find()) {
                throw new IllegalArgumentException(where + ": filler contains a digit or an identifier marker");
            }
            var plain = strip(filler);
            for (var token : nameTokens(e.patientName() + " " + e.staffName())) {
                if (Pattern.compile("(?<!\\p{L})" + Pattern.quote(token) + "(?!\\p{L})").matcher(plain).find()) {
                    throw new IllegalArgumentException(where + ": filler contains the name token '" + token + "'");
                }
            }
        }
    }

    private static List<String> nameTokens(String names) {
        return List.of(Normalizer.normalize(names, Normalizer.Form.NFC).split("[ '’´`-]+")).stream()
                .map(RedactionRecallSet::strip)
                .filter(token -> token.length() >= 2 && !NAME_PARTICLES.contains(token))
                .toList();
    }

    private static String strip(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    /** SHA-256 of the text with line endings normalised to LF, so a Windows checkout hashes the same. */
    static String sha256Lf(String text) {
        try {
            var bytes = text.replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8);
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String resource(String name) {
        var path = RESOURCE_DIR + name;
        try (var stream = RedactionRecallSet.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalArgumentException("resource not found: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
