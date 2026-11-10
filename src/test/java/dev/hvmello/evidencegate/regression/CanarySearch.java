package dev.hvmello.evidencegate.regression;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Looks for seeded canary strings in sinks, as typed (any case) and as digits only. */
final class CanarySearch {

    /** Digit-only forms shorter than this match hex digests by chance, so they are not searched. */
    static final int MIN_DIGITS = 9;

    /**
     * @param text what a sink holds
     * @param digitText the part of it searched for digit-only forms; digests are left out because hex
     *     strings contain long digit runs by chance
     */
    record Sink(String name, String text, String digitText) {
        Sink(String name, String text) {
            this(name, text, text);
        }
    }

    private CanarySearch() { }

    /** Returns "canary-index@sink" for every canary found, so the report never prints the canary itself. */
    static List<String> hits(List<String> canaries, List<Sink> sinks) {
        var hits = new ArrayList<String>();
        for (int i = 0; i < canaries.size(); i++) {
            var raw = canaries.get(i).toLowerCase(Locale.ROOT);
            var digits = canaries.get(i).replaceAll("\\D", "");
            for (var sink : sinks) {
                var found = sink.text().toLowerCase(Locale.ROOT).contains(raw)
                        || (digits.length() >= MIN_DIGITS && sink.digitText().replaceAll("\\D", "").contains(digits));
                if (found) {
                    hits.add("canary" + i + "@" + sink.name());
                }
            }
        }
        return hits;
    }
}
