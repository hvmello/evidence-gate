package dev.hvmello.evidencegate.regression;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hvmello.evidencegate.adapter.audit.InMemoryAuditLog;
import dev.hvmello.evidencegate.application.AssessmentPipeline;
import dev.hvmello.evidencegate.domain.Assessment;
import dev.hvmello.evidencegate.port.AuditLog;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * Seeds identifiers in conversations and searches every sink that exists today. Spans, Postgres and
 * metrics do not exist yet; issue #26 extends this suite to them.
 */
@Tag("regression")
@ExtendWith(OutputCaptureExtension.class)
class R4CanarySuiteTest {

    private static final int SINKS_PER_FIXTURE = 5;

    @Test
    void noCanaryReachesAnySink(CapturedOutput output) {
        var fixtures = RegressionFixtures.load("r4-canary.jsonl");

        int checks = 0;
        var failing = new ArrayList<String>();
        var allCanaries = new ArrayList<String>();
        for (var fixture : fixtures) {
            allCanaries.addAll(fixture.canaries());

            var llm = new FixtureLlm().script(fixture.classification(), null);
            var audit = new InMemoryAuditLog();
            var assessment = new AssessmentPipeline(llm, audit, Clock.systemUTC()).assess(fixture.conversation());

            var failingLlm = new FixtureLlm().throwing();
            var failingAudit = new InMemoryAuditLog();
            new AssessmentPipeline(failingLlm, failingAudit, Clock.systemUTC()).assess(fixture.conversation());

            var sinks = List.of(
                    new CanarySearch.Sink("model-payload", String.join("\n", llm.payloads())),
                    new CanarySearch.Sink("assessment", assessmentText(assessment)),
                    new CanarySearch.Sink("audit-row", auditText(audit, true), auditText(audit, false)),
                    new CanarySearch.Sink("exception", exceptionText(failingLlm)),
                    new CanarySearch.Sink(
                            "failed-audit-row", auditText(failingAudit, true), auditText(failingAudit, false)));
            assertThat(sinks).hasSize(SINKS_PER_FIXTURE);

            checks += fixture.canaries().size() * SINKS_PER_FIXTURE;
            CanarySearch.hits(fixture.canaries(), sinks).forEach(hit -> failing.add(fixture.id() + ":" + hit));
        }

        // Anything the runs wrote to the console or a logger. The report line is printed afterwards.
        checks += allCanaries.size();
        CanarySearch.hits(allCanaries, List.of(new CanarySearch.Sink("log", output.getAll())))
                .forEach(hit -> failing.add("all:" + hit));

        SuiteReport.print("R4", "canary-leak", String.valueOf(failing.size()), checks, "0", failing);
        assertThat(failing).isEmpty();
    }

    @Test
    void controlALeakInTheAssessmentIsFound() {
        var leaky = new Assessment(Assessment.Outcome.PASSED, null, List.of(), "ligue 12345678909", null);

        var hits = CanarySearch.hits(
                List.of("123.456.789-09"), List.of(new CanarySearch.Sink("assessment", assessmentText(leaky))));

        assertThat(hits).containsExactly("canary0@assessment");
    }

    private static String assessmentText(Assessment assessment) {
        var quotes = assessment.citations().stream()
                .map(c -> c.speaker() + " " + c.messageId() + " " + c.quote())
                .collect(Collectors.joining("\n"));
        return String.join("\n", String.valueOf(assessment.outcome()), String.valueOf(assessment.intent()), quotes,
                String.valueOf(assessment.draft()), String.valueOf(assessment.reason()));
    }

    private static String auditText(InMemoryAuditLog audit, boolean withDigests) {
        return audit.entries().stream().map(e -> auditRow(e, withDigests)).collect(Collectors.joining("\n"));
    }

    private static String auditRow(AuditLog.Entry e, boolean withDigests) {
        var fields = new ArrayList<>(List.of(String.valueOf(e.at()), String.valueOf(e.outcome()),
                String.valueOf(e.reasonCode()), String.valueOf(e.modelId()), String.valueOf(e.promptVersion()),
                String.valueOf(e.modelCalls())));
        if (withDigests) {
            fields.add(String.valueOf(e.subjectSha256()));
            fields.add(String.valueOf(e.payloadSha256()));
        }
        return String.join("\n", fields);
    }

    private static String exceptionText(FixtureLlm llm) {
        var text = new StringBuilder();
        for (Throwable thrown : llm.thrown()) {
            for (Throwable t = thrown; t != null; t = t.getCause()) {
                text.append(t.getClass().getName()).append(": ").append(t.getMessage()).append('\n');
            }
        }
        return text.toString();
    }
}
