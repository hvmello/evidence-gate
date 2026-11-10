package dev.hvmello.evidencegate.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.hvmello.evidencegate.domain.Conversation.Speaker;
import dev.hvmello.evidencegate.evaluation.RedactionRecallSet.Entry;
import dev.hvmello.evidencegate.evaluation.RedactionRecallSet.IdentifierClass;
import dev.hvmello.evidencegate.evaluation.RedactionRecallSet.Manifest;
import dev.hvmello.evidencegate.evaluation.RedactionScorer.Outcome;
import java.util.List;
import org.junit.jupiter.api.Test;

class RedactionRecallReportTest {

    private static final Manifest MANIFEST = new Manifest("v1", 7, "2026-01-01", 3, "g", "r", "s");

    @Test
    void wilsonIntervalMatchesHandComputedValues() {
        assertThat(RedactionRecallReport.wilson(50, 50)).satisfies(ci -> {
            assertThat(ci[0]).isCloseTo(0.9287, within(0.0005));
            assertThat(ci[1]).isEqualTo(1.0);
        });
        assertThat(RedactionRecallReport.wilson(45, 50)).satisfies(ci -> {
            assertThat(ci[0]).isCloseTo(0.7864, within(0.0005));
            assertThat(ci[1]).isCloseTo(0.9565, within(0.0005));
        });
        assertThat(RedactionRecallReport.wilson(0, 50)).satisfies(ci -> {
            assertThat(ci[0]).isEqualTo(0.0);
            assertThat(ci[1]).isCloseTo(0.0713, within(0.0005));
        });
    }

    private static RedactionRecallReport.Scored scored(String id, IdentifierClass cls, String variant,
            String expected, Outcome outcome, RedactionScorer.Label label) {
        var entry = new Entry(id, cls, variant, expected, Speaker.PATIENT, "A", "B", "p ", "x", " s");
        return new RedactionRecallReport.Scored(entry, new RedactionScorer.Score(outcome, label, ""));
    }

    private static List<RedactionRecallReport.Scored> sample() {
        return List.of(
                scored("cpf-01", IdentifierClass.CPF, "dotted", "[CPF]", Outcome.CAUGHT, RedactionScorer.Label.CORRECT),
                scored("cpf-02", IdentifierClass.CPF, "digits-only", "[CPF]", Outcome.CAUGHT,
                        RedactionScorer.Label.WRONG),
                scored("rg-01", IdentifierClass.RG, "dotted", null, Outcome.LEAK_FULL,
                        RedactionScorer.Label.NOT_APPLICABLE));
    }

    @Test
    void aggregatesCountsPerClass() {
        var stats = RedactionRecallReport.aggregate(sample());

        assertThat(stats).hasSize(2);
        var cpf = stats.getFirst();
        assertThat(cpf.identifierClass()).isEqualTo(IdentifierClass.CPF);
        assertThat(cpf.n()).isEqualTo(2);
        assertThat(cpf.caught()).isEqualTo(2);
        assertThat(cpf.labelCorrect()).isEqualTo(1);
        assertThat(cpf.hasRule()).isTrue();
        var rg = stats.get(1);
        assertThat(rg.leakedFull()).isEqualTo(1);
        assertThat(rg.hasRule()).isFalse();
    }

    @Test
    void markdownRenderingIsStableAndListsMissesAndMislabels() {
        var stats = RedactionRecallReport.aggregate(sample());

        var first = RedactionRecallReport.render(stats, MANIFEST, "2026-01-02");
        var second = RedactionRecallReport.render(stats, MANIFEST, "2026-01-02");

        assertThat(first).isEqualTo(second).doesNotContain("\r");
        assertThat(first)
                .contains("- Set: v1 (seed 7, 3 records)")
                .contains("- Model: none (deterministic)")
                .contains("| CPF | yes | 2 | 2 | 0 | 0 | 0 | 100.0 % | 34.2 % to 100.0 % | 50.0 % |")
                .contains("| RG | no rule | 1 | 0 | 1 | 0 | 0 | 0.0 % | 0.0 % to 79.3 % | n/a |")
                .contains("- `dotted` (1 of 1): rg-01")
                .contains("- `digits-only` (1 of 1): cpf-02");
    }

    @Test
    void ignoringTheDateLineMakesTwoReportsEqual() {
        var stats = RedactionRecallReport.aggregate(sample());

        var a = RedactionRecallReport.render(stats, MANIFEST, "2026-01-02");
        var b = RedactionRecallReport.render(stats, MANIFEST, "2030-12-31");

        assertThat(a).isNotEqualTo(b);
        assertThat(RedactionRecallReport.withoutDate(a)).isEqualTo(RedactionRecallReport.withoutDate(b));
    }

    @Test
    void evaluationLineCarriesTheNumbers() {
        var line = RedactionRecallReport.evaluationLine(RedactionRecallReport.aggregate(sample()).get(1), "v1");

        assertThat(line).startsWith("EVALUATION E1 RG set=v1 n=1 caught=0 leaked=1 recall=0.0 %");
    }
}
