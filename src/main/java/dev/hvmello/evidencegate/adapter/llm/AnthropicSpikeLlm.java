package dev.hvmello.evidencegate.adapter.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hvmello.evidencegate.domain.Classification;
import dev.hvmello.evidencegate.port.LlmPort;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicChatResponseMetadata;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import java.util.List;
import java.util.Map;

/** Throwaway spike for issue 13. Implements classify only; never wired into the application. */
public final class AnthropicSpikeLlm implements LlmPort {

    static final Map<String, Object> SCHEMA = Map.of(
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

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AnthropicChatModel model;
    private final String modelId;

    public AnthropicSpikeLlm(String apiKey, String baseUrl, String modelId, int maxTokens) {
        this.modelId = modelId;
        var builder = AnthropicChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelId)
                .maxTokens(maxTokens)
                .maxRetries(0)
                .customParameters(Map.of("output_config",
                        Map.of("format", Map.of("type", "json_schema", "schema", SCHEMA))));
        if (baseUrl != null) {
            builder.baseUrl(baseUrl);
        }
        this.model = builder.build();
    }

    @Override
    public Result<Classification> classify(String redactedPayload) {
        try {
            var response = model.chat(ChatRequest.builder().messages(UserMessage.from(redactedPayload)).build());
            if ("refusal".equals(rawStopReason(response.metadata()))) {
                return new Result.Failure<>(FailureCode.REFUSAL);
            }
            if (response.finishReason() == FinishReason.LENGTH) {
                return new Result.Failure<>(FailureCode.TRUNCATED);
            }
            return new Result.Success<>(JSON.readValue(response.aiMessage().text(), Classification.class));
        } catch (JsonProcessingException | NullPointerException e) {
            return new Result.Failure<>(FailureCode.INVALID_OUTPUT);
        } catch (RuntimeException e) {
            return new Result.Failure<>(FailureCode.TRANSPORT);
        }
    }

    /** The finish reason may collapse unknown stop reasons, so the raw body is the lossless source. */
    static String rawStopReason(ChatResponseMetadata metadata) throws JsonProcessingException {
        if (metadata instanceof AnthropicChatResponseMetadata anthropic && anthropic.rawHttpResponse() != null) {
            return JSON.readTree(anthropic.rawHttpResponse().body()).path("stop_reason").asText(null);
        }
        return null;
    }

    @Override
    public Result<String> draft(Classification.Intent intent, String redactedPayload) {
        throw new UnsupportedOperationException("spike");
    }

    @Override
    public String modelId() {
        return modelId;
    }

    @Override
    public String promptVersion() {
        return "spike";
    }
}
