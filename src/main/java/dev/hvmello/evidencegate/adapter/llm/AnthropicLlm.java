package dev.hvmello.evidencegate.adapter.llm;

import dev.hvmello.evidencegate.domain.Classification;
import dev.hvmello.evidencegate.domain.Classification.Citation;
import dev.hvmello.evidencegate.domain.Classification.Intent;
import dev.hvmello.evidencegate.port.LlmPort;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicChatResponseMetadata;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The real model behind {@link LlmPort}, through LangChain4j and the Anthropic Messages API.
 * Plain Java: no Spring. It receives redacted text only and never logs a payload or a response.
 */
public final class AnthropicLlm implements LlmPort {

    private static final Logger LOG = LoggerFactory.getLogger(AnthropicLlm.class);

    /** Raised whenever a prompt file under {@code prompts/} changes. */
    static final String PROMPT_VERSION = "v1";

    /** The JSON schema of {@link Classification}, sent as {@code output_config.format.schema}. */
    static final Map<String, Object> CLASSIFICATION_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "intent", Map.of("type", "string", "enum", List.of("CONTINUE", "UNDECIDED", "PAUSE", "END")),
                    "citations", Map.of("type", "array", "items", Map.of(
                            "type", "object",
                            "properties", Map.of("messageId", Map.of("type", "integer"),
                                    "quote", Map.of("type", "string")),
                            "required", List.of("messageId", "quote"),
                            "additionalProperties", false)),
                    "insufficientEvidence", Map.of("type", "boolean")),
            "required", List.of("intent", "citations", "insufficientEvidence"),
            "additionalProperties", false);

    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    /** The API key is left out of toString on purpose. */
    public record Settings(String apiKey, String baseUrl, String modelId, int maxTokens, Duration timeout) {
        @Override
        public String toString() {
            return "Settings[modelId=" + modelId + ", maxTokens=" + maxTokens + ", timeout=" + timeout + "]";
        }
    }

    private final AnthropicChatModel classifyModel;
    private final AnthropicChatModel draftModel;
    private final String modelId;
    private final String classifyPrompt;
    private final String draftPrompt;

    public AnthropicLlm(Settings settings) {
        this.modelId = settings.modelId();
        this.classifyPrompt = resource("prompts/classify-v1.txt");
        this.draftPrompt = resource("prompts/draft-v1.txt");
        this.classifyModel = chatModel(settings, Map.of("output_config",
                Map.of("format", Map.of("type", "json_schema", "schema", CLASSIFICATION_SCHEMA))));
        // The draft call must not carry output_config, so it uses its own model instance.
        this.draftModel = chatModel(settings, Map.of());
    }

    private static AnthropicChatModel chatModel(Settings settings, Map<String, Object> customParameters) {
        // Sampling is left to the model default on purpose: temperature, topP, topK and stopSequences are never
        // set, because recent models reject or ignore them. maxRetries(0) keeps one model call per pipeline step.
        var builder = AnthropicChatModel.builder()
                .apiKey(settings.apiKey())
                .modelName(settings.modelId())
                .maxTokens(settings.maxTokens())
                .timeout(settings.timeout())
                .maxRetries(0)
                .logRequests(false)
                .logResponses(false);
        if (!customParameters.isEmpty()) {
            builder.customParameters(customParameters);
        }
        if (settings.baseUrl() != null && !settings.baseUrl().isBlank()) {
            builder.baseUrl(settings.baseUrl());
        }
        return builder.build();
    }

    @Override
    public Result<Classification> classify(String redactedPayload) {
        var outcome = call(classifyModel, classifyPrompt, redactedPayload);
        if (outcome instanceof Result.Failure<String> failure) {
            return new Result.Failure<>(failure.code());
        }
        var parsed = parseClassification(((Result.Success<String>) outcome).value());
        return parsed == null ? new Result.Failure<>(FailureCode.INVALID_OUTPUT) : new Result.Success<>(parsed);
    }

    @Override
    public Result<String> draft(Intent intent, String redactedPayload) {
        return call(draftModel, draftPrompt, "Intent: " + intent + "\n\n" + redactedPayload);
    }

    /** One model call; returns the non-blank text or the failure code, checked in a fixed order. */
    private Result<String> call(AnthropicChatModel chatModel, String systemPrompt, String userText) {
        ChatResponse response;
        try {
            response = chatModel.chat(ChatRequest.builder()
                    .messages(SystemMessage.from(systemPrompt), UserMessage.from(userText))
                    .build());
        } catch (RuntimeException e) {
            // Only the class is logged: the message may echo request content.
            LOG.warn("Model call failed: {}", e.getClass().getName());
            return new Result.Failure<>(FailureCode.TRANSPORT);
        }
        if ("refusal".equals(rawStopReason(response))) {
            return new Result.Failure<>(FailureCode.REFUSAL);
        }
        if (response.finishReason() == FinishReason.LENGTH) {
            return new Result.Failure<>(FailureCode.TRUNCATED);
        }
        var text = response.aiMessage() == null ? null : response.aiMessage().text();
        if (text == null || text.isBlank()) {
            return new Result.Failure<>(FailureCode.INVALID_OUTPUT);
        }
        return new Result.Success<>(text);
    }

    /**
     * {@code finishReason()} collapses {@code refusal} into {@code OTHER}, so the raw body is the lossless source.
     * A missing or unreadable body means the stop reason is unknown (null), and the other checks still run.
     */
    private static String rawStopReason(ChatResponse response) {
        if (response.metadata() instanceof AnthropicChatResponseMetadata anthropic
                && anthropic.rawHttpResponse() != null
                && anthropic.rawHttpResponse().body() != null) {
            try {
                var node = JSON.readTree(anthropic.rawHttpResponse().body()).path("stop_reason");
                return node.isString() ? node.asString() : null;
            } catch (JacksonException e) {
                return null;
            }
        }
        return null;
    }

    /** Returns null unless every required field is present with the right type. */
    private static Classification parseClassification(String text) {
        JsonNode root;
        try {
            root = JSON.readTree(text);
        } catch (JacksonException e) {
            return null;
        }
        if (!root.isObject()) {
            return null;
        }
        var intentNode = root.get("intent");
        var evidenceNode = root.get("insufficientEvidence");
        var citationsNode = root.get("citations");
        if (intentNode == null || !intentNode.isString() || evidenceNode == null || !evidenceNode.isBoolean()
                || citationsNode == null || !citationsNode.isArray()) {
            return null;
        }
        Intent intent;
        try {
            intent = Intent.valueOf(intentNode.asString());
        } catch (IllegalArgumentException e) {
            return null;
        }
        List<Citation> citations = new ArrayList<>();
        for (var item : citationsNode) {
            var id = item.isObject() ? item.get("messageId") : null;
            var quote = item.isObject() ? item.get("quote") : null;
            if (id == null || !id.isInt() || quote == null || !quote.isString()) {
                return null;
            }
            citations.add(new Citation(id.asInt(), quote.asString()));
        }
        return new Classification(intent, citations, evidenceNode.asBoolean());
    }

    private static String resource(String path) {
        try (var in = AnthropicLlm.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing prompt resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String modelId() {
        return modelId;
    }

    @Override
    public String promptVersion() {
        return PROMPT_VERSION;
    }
}
