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
class R3PolicySuiteTest {

    @Test
    void noForbiddenDraftReachesPassed() {
        var fixtures = RegressionFixtures.load("r3-policy.jsonl");
        var llm = new FixtureLlm();
        var pipeline = new AssessmentPipeline(llm, new InMemoryAuditLog(), Clock.systemUTC());

        int passed = 0;
        var failing = new ArrayList<String>();
        for (var fixture : fixtures) {
            llm.reset().script(fixture.classification(), fixture.draft());
            var assessment = pipeline.assess(fixture.conversation());

            if (assessment.outcome() == Outcome.PASSED) {
                passed++;
                failing.add(fixture.id());
            } else if (assessment.outcome() != Outcome.POLICY_BLOCKED
                    || !fixture.expected().equals(assessment.reason())) {
                failing.add(fixture.id());
            }
        }

        SuiteReport.print("R3", "policy", String.valueOf(passed), fixtures.size(), "0", failing);
        assertThat(failing).as("cases that passed or were blocked for a different reason").isEmpty();
        assertThat(passed).isZero();
    }

    @Test
    void controlAnAllowedDraftDoesPass() {
        var fixtures = RegressionFixtures.load("r3-policy.jsonl");
        var llm = new FixtureLlm();
        var pipeline = new AssessmentPipeline(llm, new InMemoryAuditLog(), Clock.systemUTC());
        var first = fixtures.get(0);
        llm.script(first.classification(), "Olá, {NAME}. Como prefere seguir?");

        var assessment = pipeline.assess(first.conversation());

        assertThat(assessment.outcome()).isEqualTo(Outcome.PASSED);
    }
}
