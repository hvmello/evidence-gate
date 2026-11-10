package dev.hvmello.evidencegate.regression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Self-checks, so a broken loader or search cannot make a suite report a comforting zero. */
class RegressionHarnessTest {

    private static final String VALID = """
            {"id":"a","category":"c","conversation":{"subjectId":"s","consent":true,"messages":[]}}""";

    @Test
    void loadsAValidLine() {
        assertThat(RegressionFixtures.parse("t", List.of(VALID))).hasSize(1);
    }

    @Test
    void aMalformedLineFailsTheLoad() {
        assertThatThrownBy(() -> RegressionFixtures.parse("t", List.of(VALID, "{not json")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("line 2");
    }

    @Test
    void aDuplicateIdFailsTheLoad() {
        assertThatThrownBy(() -> RegressionFixtures.parse("t", List.of(VALID, VALID)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate id a");
    }

    @Test
    void anUnknownFieldFailsTheLoad() {
        assertThatThrownBy(() -> RegressionFixtures.parse("t", List.of(VALID.replace("\"id\"", "\"idd\":1,\"id\""))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aFileWithNoFixturesFailsTheLoad() {
        assertThatThrownBy(() -> RegressionFixtures.parse("t", List.of("", "  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no fixtures");
    }

    @Test
    void theSearchFindsACanaryPlantedInAFakeSink() {
        var sinks = List.of(
                new CanarySearch.Sink("clean", "nada aqui"),
                new CanarySearch.Sink("typed", "meu cpf 123.456.789-09 vazou"),
                new CanarySearch.Sink("digits", "cpf=12345678909"),
                new CanarySearch.Sink("upper", "ANA.CANARIO@EXEMPLO.COM"));

        var hits = CanarySearch.hits(List.of("123.456.789-09", "ana.canario@exemplo.com"), sinks);

        assertThat(hits).containsExactlyInAnyOrder("canary0@typed", "canary0@digits", "canary1@upper");
    }

    @Test
    void digitsFromDigestsAreNotSearched() {
        var sink = new CanarySearch.Sink("audit", "abc12345678909def", "outcome PASSED");

        assertThat(CanarySearch.hits(List.of("123.456.789-09"), List.of(sink))).isEmpty();
    }
}
