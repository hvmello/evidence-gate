package dev.hvmello.evidencegate.regression;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hvmello.evidencegate.adapter.audit.InMemoryAuditLog;
import dev.hvmello.evidencegate.application.AssessmentPipeline;
import dev.hvmello.evidencegate.domain.Assessment.Outcome;
import dev.hvmello.evidencegate.domain.Conversation;
import dev.hvmello.evidencegate.port.AuditLog;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.util.ArrayList;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Without consent no model is called. The API case proves a request with no consent field never reaches a model. */
@Tag("regression")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class R1ConsentSuiteTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class StubModel {
        @Bean
        @Primary
        FixtureLlm fixtureLlm() {
            return new FixtureLlm();
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private FixtureLlm apiLlm;

    @Autowired
    private AuditLog apiAudit;

    @Test
    void noModelCallIsMadeWithoutConsent() throws Exception {
        var fixtures = RegressionFixtures.load("r1-consent.jsonl");

        int modelCalls = 0;
        var failing = new ArrayList<String>();
        for (var fixture : fixtures) {
            int calls;
            int audited;
            Outcome outcome;
            if (fixture.apiBody() != null) {
                apiLlm.reset();
                var before = ((InMemoryAuditLog) apiAudit).entries().size();
                var response = post(fixture.apiBody().toString());
                var entries = ((InMemoryAuditLog) apiAudit).entries();
                calls = apiLlm.calls();
                audited = entries.subList(before, entries.size()).stream().mapToInt(AuditLog.Entry::modelCalls).sum();
                // A request without the field is either rejected (400) or stopped at consent; both fail closed.
                outcome = response.statusCode() == 400
                                || (response.statusCode() == 200
                                        && response.body().contains("\"outcome\":\"NO_CONSENT\""))
                        ? Outcome.NO_CONSENT
                        : null;
            } else {
                var llm = new FixtureLlm();
                var audit = new InMemoryAuditLog();
                var assessment = new AssessmentPipeline(llm, audit, Clock.systemUTC()).assess(fixture.conversation());
                calls = llm.calls();
                audited = audit.entries().stream().mapToInt(AuditLog.Entry::modelCalls).sum();
                outcome = assessment.outcome();
            }
            modelCalls += calls;
            if (calls != 0 || audited != 0 || outcome != Outcome.NO_CONSENT) {
                failing.add(fixture.id());
            }
        }

        SuiteReport.print("R1", "consent", String.valueOf(modelCalls), fixtures.size(), "0", failing);
        assertThat(failing).as("cases that called a model, audited a call or did not stop at consent").isEmpty();
        assertThat(modelCalls).isZero();
    }

    @Test
    void controlWithConsentTheModelIsCalled() {
        var fixture = RegressionFixtures.load("r1-consent.jsonl").get(0);
        var withConsent = new Conversation(
                fixture.conversation().subjectId(), true, fixture.conversation().patientName(),
                fixture.conversation().staffName(), fixture.conversation().messages());
        var llm = new FixtureLlm();

        new AssessmentPipeline(llm, new InMemoryAuditLog(), Clock.systemUTC()).assess(withConsent);

        assertThat(llm.calls()).isEqualTo(2);
    }

    private HttpResponse<String> post(String json) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/assessments"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
