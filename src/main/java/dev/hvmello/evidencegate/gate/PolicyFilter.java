package dev.hvmello.evidencegate.gate;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Deterministic rules for a draft reply. The model's own opinion of its draft is never consulted.
 *
 * <p>A term list catches what it lists and nothing else. Letters separated by spaces or punctuation
 * ({@code d e p r e s s a o}, {@code p-i-x}) are joined before matching. Arbitrary splits inside a word
 * ({@code depre ssao}), paraphrases and look-alike letters from other alphabets still pass, and are measured
 * by the evaluation suite. A human reviews every draft that passes.
 */
public final class PolicyFilter {

    static final int MAX_LENGTH = 320;
    static final String NAME_MARKER = "{NAME}";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\[[A-Z_]+]");
    private static final Pattern CONTACT_DATA = Pattern.compile("https?://|www\\.|\\d{5,}|\\d{4}[ .-]\\d{4}");

    private static final String START = "(?<![\\p{L}\\p{N}])";
    private static final String END = "(?![\\p{L}\\p{N}])";

    // Clinical stems match anywhere, so that "antidepressivo" is caught. Short or ambiguous words need boundaries.
    private static final List<Pattern> BLOCKED_TERMS = List.of(
            Pattern.compile("diagnost"),
            Pattern.compile("ansiedad"),
            Pattern.compile("depress(?!a" + END + ")"),
            Pattern.compile("transtorno"),
            Pattern.compile("sintoma"),
            Pattern.compile(START + "crises?" + END),
            Pattern.compile("r\\$"),
            Pattern.compile(START + "pix" + END),
            Pattern.compile("desconto"),
            Pattern.compile("promoc"),
            Pattern.compile(START + "urgente"),
            Pattern.compile(START + "ultima ?chance" + END));

    // Isolated single letters separated by non-alphanumerics, three or more in a row: "d e p r e s s a", "p-i-x".
    private static final Pattern SPACED_LETTERS = Pattern.compile(
            START + "\\p{L}(?:[^\\p{L}\\p{N}]+\\p{L}){2,}" + END);
    // Accents are already folded away: a, e, o, and "é" and "à" become e and a.
    private static final String ONE_LETTER_WORDS = "aeo";
    private static final Pattern INNOCENT_WORDS = Pattern.compile(START + "depressa" + END);
    private static final Pattern SEPARATORS = Pattern.compile("[^\\p{L}\\p{N}]+");

    /** Returns the violated rule as a code, or empty when the draft is allowed. */
    public Optional<String> violation(String draft) {
        if (draft.length() > MAX_LENGTH) {
            return Optional.of("TOO_LONG");
        }
        // Folding first means every later rule sees the same text, whatever tricks the draft used.
        var plain = Normalizer.normalize(draft, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}|\\p{Cf}", "")
                .replaceAll("[\\s\\p{Z}\\p{Cc}]+", " ");

        if (plain.chars().filter(c -> c == '?').count() > 1) {
            return Optional.of("TOO_MANY_QUESTIONS");
        }
        // Checked on the raw draft, because that is the text returned and later filled in.
        if (!draft.contains(NAME_MARKER)) {
            return Optional.of("NAME_MARKER_MISSING");
        }
        if (PLACEHOLDER.matcher(plain).find()) {
            return Optional.of("PLACEHOLDER_LEAK");
        }
        var lower = plain.toLowerCase(Locale.ROOT);
        if (CONTACT_DATA.matcher(lower).find()) {
            return Optional.of("CONTACT_DATA");
        }
        return blocked(lower) || SPACED_LETTERS.matcher(lower).results()
                .anyMatch(run -> blockedRun(SEPARATORS.matcher(run.group()).replaceAll("")))
                ? Optional.of("BLOCKED_TERM")
                : Optional.empty();
    }

    /**
     * A run can swallow a real one-letter word next to the spaced term ("faca o p i x" joins to "opix"). The whole
     * run is checked, then the run with such words trimmed off either end. Trimmed forms are not checked inside an
     * innocent word, so "d e p r e s s a" stays allowed.
     */
    private static boolean blockedRun(String joined) {
        if (blocked(joined)) {
            return true;
        }
        var innocent = INNOCENT_WORDS.matcher(joined).results().toList();
        var lead = 0;
        while (lead < joined.length() && ONE_LETTER_WORDS.indexOf(joined.charAt(lead)) >= 0) {
            lead++;
        }
        var tail = joined.length();
        while (tail > 0 && ONE_LETTER_WORDS.indexOf(joined.charAt(tail - 1)) >= 0) {
            tail--;
        }
        for (var from = 0; from <= lead; from++) {
            for (var to = joined.length(); to >= Math.max(tail, from + 1); to--) {
                if (from == 0 && to == joined.length()) {
                    continue;
                }
                var start = from;
                var end = to;
                if (innocent.stream().noneMatch(w -> w.start() <= start && end <= w.end())
                        && blocked(joined.substring(from, to))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean blocked(String text) {
        return BLOCKED_TERMS.stream().anyMatch(term -> term.matcher(text).find());
    }
}
