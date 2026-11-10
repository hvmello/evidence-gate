package dev.hvmello.evidencegate.regression;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hvmello.evidencegate.adapter.audit.InMemoryAuditLog;
import dev.hvmello.evidencegate.application.AssessmentPipeline;
import dev.hvmello.evidencegate.domain.Assessment.Outcome;
import java.time.Clock;
import java.util.ArrayList;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("regression")
class R2FabricationSuiteTest {

    @Test
    void noFabricatedCitationReachesPassed() {
        var fixtures = RegressionFixtures.load("r2-fabrication.jsonl");
        var llm = new FixtureLlm();
        var pipeline = new AssessmentPipeline(llm, new InMemoryAuditLog(), Clock.systemUTC());

        int passed = 0;
        var failing = new ArrayList<String>();
        for (var fixture : fixtures) {
            llm.reset().script(fixture.classification(), null);
            var assessment = pipeline.assess(fixture.conversation());

            if (assessment.outcome() == Outcome.PASSED) {
                passed++;
                failing.add(fixture.id());
            } else if (assessment.outcome() != Outcome.EVIDENCE_MISMATCH
                    || !fixture.expected().equals(assessment.reason())
                    || llm.draftCalls() != 0) {
                failing.add(fixture.id());
            }
        }

        SuiteReport.print("R2", "fabrication", String.valueOf(passed), fixtures.size(), "0", failing);
        assertThat(failing).as("cases that passed or ended in a different outcome or reason").isEmpty();
        assertThat(passed).isZero();
    }

    @Test
    void controlAValidCitationDoesPass() {
        var fixtures = RegressionFixtures.load("r2-fabrication.jsonl");
        var llm = new FixtureLlm();
        var pipeline = new AssessmentPipeline(llm, new InMemoryAuditLog(), Clock.systemUTC());

        var assessment = pipeline.assess(fixtures.get(0).conversation());

        assertThat(assessment.outcome()).isEqualTo(Outcome.PASSED);
    }
}
