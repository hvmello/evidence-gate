package dev.hvmello.evidencegate.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AssessmentApiTest {

    @LocalServerPort
    private int port;

    private HttpResponse<String> post(String json) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/assessments"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void assessesAConversationAndReturnsOnlyRedactedText() throws Exception {
        var response = post("""
                {"subjectId":"s-1","consent":true,"patientName":"Beatriz Lima","staffName":"Marina",
                 "messages":[{"id":1,"speaker":"PATIENT","body":"Beatriz aqui, quero continuar o tratamento."}]}
                """);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"outcome\":\"PASSED\"", "[PATIENT_NAME]").doesNotContain("Beatriz");
    }

    @Test
    void aPhoneNumberNeverComesBackInACitation() throws Exception {
        var response = post("""
                {"subjectId":"s-1","consent":true,
                 "messages":[{"id":1,"speaker":"PATIENT","body":"quero continuar, meu fone 98123-4567 fica igual"}]}
                """);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).doesNotContain("98123").contains("[PHONE]");
    }

    @Test
    void rejectsAnOversizedBody() throws Exception {
        var body = "a".repeat(4_001);

        var response = post("""
                {"subjectId":"s-1","consent":true,"messages":[{"id":1,"speaker":"PATIENT","body":"%s"}]}
                """.formatted(body));

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void rejectsABlankSubject() throws Exception {
        var response = post("""
                {"subjectId":" ","consent":true,"messages":[]}
                """);

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void rejectsDuplicateMessageIds() throws Exception {
        var response = post("""
                {"subjectId":"s-1","consent":true,"messages":[
                 {"id":1,"speaker":"PATIENT","body":"a"},{"id":1,"speaker":"PATIENT","body":"b"}]}
                """);

        assertThat(response.statusCode()).isEqualTo(400);
    }
}
