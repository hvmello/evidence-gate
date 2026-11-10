package dev.hvmello.evidencegate.evaluation;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The leak rule. Placeholders are removed, then the result and the filler are reduced to their letters and digits.
 * What is left between the prefix and the suffix is what the redactor failed to remove. Any amount counts as a
 * leak, and a filler that no longer matches counts against the redactor too.
 */
final class RedactionScorer {

    enum Outcome { CAUGHT, LEAK_FULL, LEAK_PARTIAL, FILLER_DAMAGED }

    enum Label { CORRECT, WRONG, NOT_APPLICABLE }

    record Score(Outcome outcome, Label label, String leftover) {

        boolean leaked() {
            return outcome != Outcome.CAUGHT;
        }
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("\\[[A-Z_]+]");

    private RedactionScorer() { }

    static Score score(String prefix, String identifier, String suffix, String redacted, String expectedPlaceholder) {
        var skeleton = skeleton(PLACEHOLDER.matcher(redacted).replaceAll(""));
        var head = skeleton(prefix);
        var tail = skeleton(suffix);
        if (skeleton.length() < head.length() + tail.length() || !skeleton.startsWith(head)
                || !skeleton.endsWith(tail)) {
            return new Score(Outcome.FILLER_DAMAGED, Label.NOT_APPLICABLE, skeleton);
        }
        var leftover = skeleton.substring(head.length(), skeleton.length() - tail.length());
        if (!leftover.isEmpty()) {
            var outcome = leftover.equals(skeleton(identifier)) ? Outcome.LEAK_FULL : Outcome.LEAK_PARTIAL;
            return new Score(outcome, Label.NOT_APPLICABLE, leftover);
        }
        return new Score(Outcome.CAUGHT, label(redacted, expectedPlaceholder), "");
    }

    /** Correct when every placeholder in the text is the expected one. Classes without a rule have no label. */
    private static Label label(String redacted, String expectedPlaceholder) {
        if (expectedPlaceholder == null) {
            return Label.NOT_APPLICABLE;
        }
        List<String> found = new ArrayList<>();
        var matcher = PLACEHOLDER.matcher(redacted);
        while (matcher.find()) {
            found.add(matcher.group());
        }
        return !found.isEmpty() && found.stream().allMatch(expectedPlaceholder::equals) ? Label.CORRECT : Label.WRONG;
    }

    static String skeleton(String text) {
        var builder = new StringBuilder();
        Normalizer.normalize(text, Normalizer.Form.NFC).codePoints()
                .filter(Character::isLetterOrDigit)
                .forEach(builder::appendCodePoint);
        return builder.toString();
    }
}
