package dev.hvmello.evidencegate;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Model settings. The API key is deliberately not here: it comes only from the environment. */
@ConfigurationProperties("evidence-gate.llm")
public record LlmProperties(
        @DefaultValue("scripted") Provider provider,
        @DefaultValue("claude-opus-5-5") String model,
        @DefaultValue("1024") int maxTokens,
        @DefaultValue("30s") Duration timeout,
        String baseUrl) {

    public enum Provider { SCRIPTED, ANTHROPIC }
}
