package dev.hvmello.evidencegate.adapter.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import dev.hvmello.evidencegate.domain.Classification;
import dev.hvmello.evidencegate.port.LlmPort.FailureCode;
import dev.hvmello.evidencegate.port.LlmPort.Result;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AnthropicSpikeLlmTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String VALID =
            "{\"intent\":\"PAUSE\",\"citations\":[{\"messageId\":1,\"quote\":\"x\"}],\"insufficientEvidence\":false}";

    private HttpServer server;
    private final AtomicReference<String> requestBody = new AtomicReference<>();

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    private void stub(String stopReason, String text) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var body = JSON.writeValueAsBytes(Map.of(
                    "id", "msg_1", "type", "message", "role", "assistant", "model", "claude-opus-5-5",
                    "content", List.of(Map.of("type", "text", "text", text)),
                    "stop_reason", stopReason,
                    "usage", Map.of("input_tokens", 10, "output_tokens", 5)));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    private AnthropicSpikeLlm llm() {
        return new AnthropicSpikeLlm("test-key", baseUrl(), "claude-opus-5-5", 256);
    }

    @Test
    void requestCarriesOutputConfigFormatAtTopLevel() throws Exception {
        stub("end_turn", VALID);
        var result = llm().classify("#1 patient: x");
        System.out.println("REQUEST BODY: " + requestBody.get());
        JsonNode sent = JSON.readTree(requestBody.get());
        assertThat(sent.at("/output_config/format/type").asText()).isEqualTo("json_schema");
        assertThat(sent.at("/output_config/format/schema/required").size()).isEqualTo(3);
        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(((Result.Success<Classification>) result).value().intent())
                .isEqualTo(Classification.Intent.PAUSE);
    }

    @Test
    void refusalMapsToRefusal() throws Exception {
        stub("refusal", "");
        assertThat(llm().classify("x")).isEqualTo(new Result.Failure<>(FailureCode.REFUSAL));
    }

    @Test
    void maxTokensMapsToTruncated() throws Exception {
        stub("max_tokens", "{\"intent\":\"PA");
        assertThat(llm().classify("x")).isEqualTo(new Result.Failure<>(FailureCode.TRUNCATED));
    }

    @Test
    void malformedJsonMapsToInvalidOutput() throws Exception {
        stub("end_turn", "not json");
        assertThat(llm().classify("x")).isEqualTo(new Result.Failure<>(FailureCode.INVALID_OUTPUT));
    }

    @Test
    void finishReasonAloneIsRecordedForRefusal() throws Exception {
        stub("refusal", "");
        var model = AnthropicChatModel.builder().apiKey("k").modelName("claude-opus-5-5").maxTokens(10)
                .baseUrl(baseUrl()).build();
        var response = model.chat(ChatRequest.builder().messages(UserMessage.from("x")).build());
        System.out.println("FINISH REASON for stop_reason=refusal: " + response.finishReason());
        assertThat(response.finishReason()).isNotNull();
    }
}
