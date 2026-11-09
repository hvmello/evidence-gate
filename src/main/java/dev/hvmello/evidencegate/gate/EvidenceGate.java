package dev.hvmello.evidencegate.gate;

import dev.hvmello.evidencegate.domain.Assessment.VerifiedCitation;
import dev.hvmello.evidencegate.domain.Classification;
import dev.hvmello.evidencegate.domain.Conversation.Speaker;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Checks that every citation exists verbatim in the message it names, in the redacted text the model saw.
 *
 * <p>It proves a sentence exists. It does not prove the sentence supports the label; that is measured
 * separately and left to the human reviewer.
 */
public final class EvidenceGate {

    static final int MIN_WORDS = 3;
    static final int MAX_CITATIONS = 3;

    public sealed interface Verdict {
        record Verified(List<VerifiedCitation> citations) implements Verdict { }

        record Insufficient() implements Verdict { }

        record Mismatch(String reasonCode) implements Verdict { }
    }

    public Verdict verify(Classification classification, Redactor.Redacted redacted) {
        if (classification.insufficientEvidence()) {
            return new Verdict.Insufficient();
        }
        if (classification.intent() == null) {
            return new Verdict.Mismatch("NO_INTENT");
        }
        if (classification.citations().isEmpty()) {
            return new Verdict.Mismatch("NO_CITATIONS");
        }

        // Every citation is checked before any cap is applied, so a fabricated one cannot hide past the limit.
        var verified = new ArrayList<VerifiedCitation>();
        for (var citation : classification.citations()) {
            var message = redacted.message(citation.messageId());
            if (message.isEmpty()) {
                return new Verdict.Mismatch("UNKNOWN_MESSAGE");
            }
            var quote = normalize(citation.quote() == null ? "" : citation.quote());
            if (substantiveWords(quote) < MIN_WORDS) {
                return new Verdict.Mismatch("QUOTE_TOO_SHORT");
            }
            if (!containsWholeWords(normalize(message.get().text()), quote)) {
                return new Verdict.Mismatch("QUOTE_NOT_IN_MESSAGE");
            }
            verified.add(new VerifiedCitation(citation.messageId(), message.get().speaker(), quote));
        }

        if (verified.stream().noneMatch(c -> c.speaker() == Speaker.PATIENT)) {
            return new Verdict.Mismatch("NO_PATIENT_CITATION");
        }
        // Patient citations go first, so the cap cannot leave only staff words supporting the patient's intent.
        return new Verdict.Verified(verified.stream()
                .sorted(Comparator.comparing(c -> c.speaker() != Speaker.PATIENT))
                .limit(MAX_CITATIONS)
                .toList());
    }

    private static boolean containsWholeWords(String text, String quote) {
        for (int at = text.indexOf(quote); at >= 0; at = text.indexOf(quote, at + 1)) {
            var end = at + quote.length();
            var startsClean = at == 0 || !Character.isLetterOrDigit(text.codePointBefore(at));
            var endsClean = end == text.length() || !Character.isLetterOrDigit(text.codePointAt(end));
            if (startsClean && endsClean) {
                return true;
            }
        }
        return false;
    }

    private static int substantiveWords(String quote) {
        var withoutPlaceholders = quote.replaceAll("\\[[A-Z_]+]", " ");
        var count = 0;
        for (var token : withoutPlaceholders.split("\\s+")) {
            var cutPlaceholder = token.contains("[") || token.contains("]");
            if (!cutPlaceholder && token.codePoints().anyMatch(Character::isLetterOrDigit)) {
                count++;
            }
        }
        return count;
    }

    static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFC)
                .replaceAll("\\p{Cf}", "")
                .replaceAll("[‘’]", "'")
                .replaceAll("[“”]", "\"")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
