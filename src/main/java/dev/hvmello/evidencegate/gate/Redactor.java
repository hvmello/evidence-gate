package dev.hvmello.evidencegate.gate;

import dev.hvmello.evidencegate.domain.Conversation;
import dev.hvmello.evidencegate.domain.Conversation.Speaker;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Removes known identifier formats and the names supplied with the conversation.
 *
 * <p>This is harm reduction, not anonymisation: a name the caller did not supply, an address or a
 * nickname is not recognised, and pseudonymised health text remains sensitive data.
 */
public final class Redactor {

    public static final String PATIENT_NAME = "[PATIENT_NAME]";
    public static final String STAFF_NAME = "[STAFF_NAME]";

    private static final String SEPARATOR = "[ .-]{0,4}";

    // Order matters. Links and e-mails go before names so that a name inside them cannot split the match;
    // CPF goes before phone because an unformatted CPF would otherwise match as a phone plus a stray digit.
    // The lookbehinds and possessive quantifiers keep matching linear on long input.
    private static final List<Rule> RULES = List.of(
            new Rule(Pattern.compile("(?i)(?<![\\p{L}\\p{N}])(?:https?://|www\\.)\\S++"), "[LINK]"),
            new Rule(Pattern.compile(
                    "(?<![\\p{L}\\p{N}._%+-])[\\p{L}\\p{N}._%+-]++@[\\p{L}\\p{N}-]++(?:\\.[\\p{L}\\p{N}-]++)++"), "[EMAIL]"),
            new Rule(Pattern.compile("(?i)R\\$ ?[\\d.,]*\\d"), "[AMOUNT]"),
            new Rule(Pattern.compile("(?<!\\d)\\d{3}[. -]?\\d{3}[. -]?\\d{3}[. -]?\\d{2}(?!\\d)"), "[CPF]"),
            new Rule(Pattern.compile("(?<!\\d)(?:\\+?55" + SEPARATOR + ")?(?:\\(?0?\\d{2}\\)?" + SEPARATOR + ")?"
                    + "(?:(?:9" + SEPARATOR + ")?\\d{4}" + SEPARATOR + "\\d{4}|9\\d{2}[ .-]\\d{3}[ .-]\\d{3})(?!\\d)"), "[PHONE]"));

    private static final Set<String> NAME_PARTICLES = Set.of("de", "da", "do", "das", "dos", "e");

    private static final Map<Character, String> ACCENT_CLASSES = Map.of(
            'a', "[aáàâãä]", 'e', "[eéèêë]", 'i', "[iíìîï]", 'o', "[oóòôõö]",
            'u', "[uúùûü]", 'c', "[cç]", 'n', "[nñ]");

    private record Rule(Pattern pattern, String placeholder) { }

    public record RedactedMessage(int id, Speaker speaker, String text) { }

    public record Redacted(List<RedactedMessage> messages, String payload, String payloadSha256) {

        public Optional<RedactedMessage> message(int id) {
            return messages.stream().filter(m -> m.id() == id).findFirst();
        }
    }

    public Redacted redact(Conversation conversation) {
        var patient = namePatterns(conversation.patientName());
        var staff = namePatterns(conversation.staffName());

        var messages = conversation.messages().stream()
                .map(m -> new RedactedMessage(m.id(), m.speaker(), redactBody(m.body(), patient, staff)))
                .toList();

        // Line breaks are already gone from each body, so message text cannot forge another "#id speaker:" line.
        var payload = String.join("\n", messages.stream()
                .map(m -> "#" + m.id() + " " + m.speaker().name().toLowerCase() + ": " + m.text())
                .toList());

        return new Redacted(messages, payload, sha256(payload));
    }

    private static String redactBody(String body, List<Pattern> patient, List<Pattern> staff) {
        // Normalising first means invisible characters and exotic spaces cannot split an identifier.
        var text = clean(body);
        for (var rule : RULES) {
            text = rule.pattern().matcher(text).replaceAll(rule.placeholder());
        }
        for (var name : patient) {
            text = name.matcher(text).replaceAll(java.util.regex.Matcher.quoteReplacement(PATIENT_NAME));
        }
        for (var name : staff) {
            text = name.matcher(text).replaceAll(java.util.regex.Matcher.quoteReplacement(STAFF_NAME));
        }
        return text.trim();
    }

    private static String clean(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFC)
                .replaceAll("\\p{Cf}", "")
                .replaceAll("[\\s\\p{Z}\\p{Cc}]+", " ");
    }

    private static List<Pattern> namePatterns(String fullName) {
        return List.of(clean(fullName).split("[ '’\u00B4`-]+")).stream()
                .map(Redactor::stripAccents)
                .filter(token -> token.length() >= 2 && !NAME_PARTICLES.contains(token))
                .map(Redactor::tokenPattern)
                .toList();
    }

    private static String stripAccents(String token) {
        return Normalizer.normalize(token, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private static Pattern tokenPattern(String token) {
        var body = new StringBuilder();
        for (char ch : token.toCharArray()) {
            body.append(ACCENT_CLASSES.getOrDefault(ch, Pattern.quote(String.valueOf(ch))));
        }
        return Pattern.compile(
                "(?<!\\p{L})" + body + "(?!\\p{L})", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    static String sha256(String text) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
