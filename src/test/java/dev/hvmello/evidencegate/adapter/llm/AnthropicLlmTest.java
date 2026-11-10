package dev.hvmello.evidencegate.adapter.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import dev.hvmello.evidencegate.domain.Classification;
import dev.hvmello.evidencegate.domain.Classification.Intent;
import dev.hvmello.evidencegate.port.LlmPort.FailureCode;
import dev.hvmello.evidencegate.port.LlmPort.Result;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Runs against a local JDK HTTP stub. No real model is ever called. */
class AnthropicLlmTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String VALID =
            "{\"intent\":\"PAUSE\",\"citations\":[{\"messageId\":1,\"quote\":\"vou viajar\"}],"
                    + "\"insufficientEvidence\":false}";
    private static final List<String> SAMPLING = List.of("temperature", "top_p", "top_k", "stop_sequences");

    private HttpServer server;
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicInteger requests = new AtomicInteger();

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void stub(String stopReason, String text) throws IOException {
        stubRaw(200, Map.of(
                "id", "msg_1", "type", "message", "role", "assistant", "model", "claude-opus-5-5",
                "content", text == null ? List.of() : List.of(Map.of("type", "text", "text", text)),
                "stop_reason", stopReason,
                "usage", Map.of("input_tokens", 10, "output_tokens", 5)), 0);
    }

    private void stubRaw(int status, Object body, long delayMillis) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            var bytes = JSON.writeValueAsBytes(body);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            try {
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (IOException ignored) {
                // the client gave up (timeout case)
            }
            exchange.close();
        });
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    private AnthropicLlm llm() {
        return llm(baseUrl(), Duration.ofSeconds(5));
    }

    private static AnthropicLlm llm(String baseUrl, Duration timeout) {
        return new AnthropicLlm(new AnthropicLlm.Settings("test-key", baseUrl, "claude-opus-5-5", 256, timeout));
    }

    @Test
    void classifyAndDraftSendNoSamplingParameter() throws Exception {
        stub("end_turn", VALID);
        llm().classify("#1 patient: vou viajar");
        var classifyBody = JSON.readTree(requestBody.get());
        server.stop(0);
        stub("end_turn", "Olá, {NAME}.");
        llm().draft(Intent.PAUSE, "#1 patient: vou viajar");
        var draftBody = JSON.readTree(requestBody.get());
        for (var body : List.of(classifyBody, draftBody)) {
            for (var key : SAMPLING) {
                assertThat(body.has(key)).as(key).isFalse();
            }
        }
    }

    @Test
    void classifyRequestCarriesModelMaxTokensAndSchema() throws Exception {
        stub("end_turn", VALID);
        llm().classify("#1 patient: vou viajar");
        JsonNode sent = JSON.readTree(requestBody.get());
        assertThat(sent.get("model").asString()).isEqualTo("claude-opus-5-5");
        assertThat(sent.get("max_tokens").asInt()).isEqualTo(256);
        assertThat(sent.at("/output_config/format/type").asString()).isEqualTo("json_schema");
        Set<String> required = new HashSet<>();
        sent.at("/output_config/format/schema/required").forEach(n -> required.add(n.asString()));
        Set<String> components = Arrays.stream(Classification.class.getRecordComponents())
                .map(RecordComponent::getName).collect(Collectors.toSet());
        assertThat(required).isEqualTo(components);
        assertThat(sent.at("/messages/0/content").toString()).contains("vou viajar");
    }

    @Test
    void draftRequestHasNoOutputConfig() throws Exception {
        stub("end_turn", "Olá, {NAME}.");
        llm().draft(Intent.CONTINUE, "#1 patient: x");
        var sent = JSON.readTree(requestBody.get());
        assertThat(sent.has("output_config")).isFalse();
        assertThat(sent.get("model").asString()).isEqualTo("claude-opus-5-5");
        assertThat(sent.get("max_tokens").asInt()).isEqualTo(256);
    }

    @Test
    void validJsonGivesSuccess() throws Exception {
        stub("end_turn", VALID);
        assertThat(llm().classify("x")).isEqualTo(new Result.Success<>(new Classification(Intent.PAUSE,
                List.of(new Classification.Citation(1, "vou viajar")), false)));
    }

    @Test
    void draftKeepsPlaceholdersUnchanged() throws Exception {
        stub("end_turn", "Olá, {NAME}. Como prefere seguir?");
        assertThat(llm().draft(Intent.PAUSE, "x"))
                .isEqualTo(new Result.Success<>("Olá, {NAME}. Como prefere seguir?"));
    }

    @Test
    void refusalMapsToRefusalForBothCalls() throws Exception {
        stub("refusal", "");
        var llm = llm();
        assertThat(llm.classify("x")).isEqualTo(new Result.Failure<>(FailureCode.REFUSAL));
        assertThat(llm.draft(Intent.END, "x")).isEqualTo(new Result.Failure<>(FailureCode.REFUSAL));
    }

    @Test
    void maxTokensMapsToTruncatedForBothCalls() throws Exception {
        stub("max_tokens", "{\"intent\":\"PA");
        var llm = llm();
        assertThat(llm.classify("x")).isEqualTo(new Result.Failure<>(FailureCode.TRUNCATED));
        assertThat(llm.draft(Intent.END, "x")).isEqualTo(new Result.Failure<>(FailureCode.TRUNCATED));
    }

    @Test
    void contextWindowExceededMapsToTruncatedForBothCalls() throws Exception {
        stub("model_context_window_exceeded", "half a reply");
        var llm = llm();
        assertThat(llm.classify("x")).isEqualTo(new Result.Failure<>(FailureCode.TRUNCATED));
        assertThat(llm.draft(Intent.END, "x")).isEqualTo(new Result.Failure<>(FailureCode.TRUNCATED));
    }

    @ParameterizedTest
    @ValueSource(strings = {"pause_turn", "tool_use", "stop_sequence", "something_new"})
    void otherStopReasonsMapToInvalidOutputForBothCalls(String stopReason) throws Exception {
        stub(stopReason, VALID);
        var llm = llm();
        assertThat(llm.classify("x")).isEqualTo(new Result.Failure<>(FailureCode.INVALID_OUTPUT));
        assertThat(llm.draft(Intent.END, "x")).isEqualTo(new Result.Failure<>(FailureCode.INVALID_OUTPUT));
    }

    static Stream<Arguments> invalidClassifications() {
        return Stream.of(
                Arguments.of("not json", "this is not json"),
                Arguments.of("empty text", ""),
                Arguments.of("unknown intent",
                        "{\"intent\":\"MAYBE\",\"citations\":[],\"insufficientEvidence\":false}"),
                Arguments.of("missing intent", "{\"citations\":[],\"insufficientEvidence\":false}"),
                Arguments.of("missing insufficientEvidence", "{\"intent\":\"END\",\"citations\":[]}"),
                Arguments.of("null insufficientEvidence",
                        "{\"intent\":\"END\",\"citations\":[],\"insufficientEvidence\":null}"),
                Arguments.of("citations not an array",
                        "{\"intent\":\"END\",\"citations\":\"x\",\"insufficientEvidence\":false}"),
                Arguments.of("citation with a wrong type",
                        "{\"intent\":\"END\",\"citations\":[{\"messageId\":\"1\",\"quote\":\"x\"}],"
                                + "\"insufficientEvidence\":false}"),
                Arguments.of("not an object", "[1,2]"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidClassifications")
    void invalidClassificationMapsToInvalidOutput(String name, String text) throws Exception {
        stub("end_turn", text);
        assertThat(llm().classify("x")).isEqualTo(new Result.Failure<>(FailureCode.INVALID_OUTPUT));
    }

    @Test
    void emptyDraftMapsToInvalidOutput() throws Exception {
        stub("end_turn", "   ");
        assertThat(llm().draft(Intent.END, "x")).isEqualTo(new Result.Failure<>(FailureCode.INVALID_OUTPUT));
    }

    @Test
    void missingContentMapsToInvalidOutputNotSuccess() throws Exception {
        stub("end_turn", null);
        assertThat(llm().classify("x")).isEqualTo(new Result.Failure<>(FailureCode.INVALID_OUTPUT));
    }

    @ParameterizedTest(name = "{0}")
    @org.junit.jupiter.params.provider.ValueSource(strings = {"http500", "http401", "timeout", "refused"})
    void transportProblemsMapToTransportWithoutRetry(String scenario) throws Exception {
        AnthropicLlm llm;
        switch (scenario) {
            case "http500" -> {
                stubRaw(500, Map.of("error", Map.of("type", "api_error", "message", "boom")), 0);
                llm = llm();
            }
            case "http401" -> {
                stubRaw(401, Map.of("error", Map.of("type", "authentication_error", "message", "no")), 0);
                llm = llm();
            }
            case "timeout" -> {
                stubRaw(200, Map.of(), 3000);
                llm = llm(baseUrl(), Duration.ofMillis(300));
            }
            default -> llm = llm("http://127.0.0.1:" + freePort() + "/v1", Duration.ofSeconds(2));
        }
        assertThat(llm.classify("x")).isEqualTo(new Result.Failure<>(FailureCode.TRANSPORT));
        assertThat(llm.draft(Intent.END, "x")).isEqualTo(new Result.Failure<>(FailureCode.TRANSPORT));
        if (server != null) {
            assertThat(requests.get()).as("one request per call, no retry").isEqualTo(2);
        }
    }

    private static int freePort() throws IOException {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Test
    void reportsConfiguredModelAndPromptVersion() {
        var llm = llm("http://127.0.0.1:1/v1", Duration.ofSeconds(1));
        assertThat(llm.modelId()).isEqualTo("claude-opus-5-5");
        assertThat(llm.promptVersion()).isEqualTo(AnthropicLlm.PROMPT_VERSION);
    }

    @Test
    void settingsToStringHidesTheApiKey() {
        var settings = new AnthropicLlm.Settings("sk-secret", null, "m", 1, Duration.ofSeconds(1));
        assertThat(settings.toString()).doesNotContain("sk-secret");
    }
}
