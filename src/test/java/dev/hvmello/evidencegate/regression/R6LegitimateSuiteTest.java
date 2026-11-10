package dev.hvmello.evidencegate.regression;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hvmello.evidencegate.gate.PolicyFilter;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Legitimate replies a clinic would send. The set is written from realistic replies, not tuned to a number. */
@Tag("regression")
class R6LegitimateSuiteTest {

    static final double MAX_BLOCKED_PERCENT = 5.0;

    @Test
    void legitimateDraftsAreMostlyAllowed() {
        var fixtures = RegressionFixtures.load("r6-legitimate.jsonl");
        var filter = new PolicyFilter();

        var blocked = new ArrayList<String>();
        for (var fixture : fixtures) {
            filter.violation(fixture.draft()).ifPresent(code -> blocked.add(fixture.id() + " (" + code + ")"));
        }
        var percent = 100.0 * blocked.size() / fixtures.size();

        SuiteReport.print("R6", "legitimate", String.format(Locale.ROOT, "%.1f%%", percent), fixtures.size(),
                "<=" + MAX_BLOCKED_PERCENT + "%", blocked);
        assertThat(percent).as("blocked legitimate drafts: %s", blocked).isLessThanOrEqualTo(MAX_BLOCKED_PERCENT);
    }

    @Test
    void coversAllFourIntentsWithAtLeastFortyReplies() {
        var fixtures = RegressionFixtures.load("r6-legitimate.jsonl");

        Set<String> intents = new TreeSet<>();
        fixtures.forEach(f -> intents.add(f.intent()));

        assertThat(fixtures.size()).isGreaterThanOrEqualTo(40);
        assertThat(intents).containsExactly("CONTINUE", "END", "PAUSE", "UNDECIDED");
    }
}
