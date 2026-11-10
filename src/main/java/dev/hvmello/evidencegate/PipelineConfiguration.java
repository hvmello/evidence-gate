package dev.hvmello.evidencegate;

import dev.hvmello.evidencegate.adapter.audit.InMemoryAuditLog;
import dev.hvmello.evidencegate.adapter.llm.AnthropicLlm;
import dev.hvmello.evidencegate.adapter.llm.ScriptedLlm;
import dev.hvmello.evidencegate.application.AssessmentPipeline;
import dev.hvmello.evidencegate.port.AuditLog;
import dev.hvmello.evidencegate.port.LlmPort;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LlmProperties.class)
class PipelineConfiguration {

    /**
     * Never falls back to {@link ScriptedLlm} when the real provider is requested but unusable: a silent
     * fallback would be mistaken for model output.
     */
    @Bean
    LlmPort llmPort(LlmProperties properties, @Value("${ANTHROPIC_API_KEY:}") String apiKey) {
        return switch (properties.provider()) {
            case SCRIPTED -> new ScriptedLlm();
            case ANTHROPIC -> {
                if (apiKey == null || apiKey.isBlank()) {
                    throw new IllegalStateException(
                            "evidence-gate.llm.provider=anthropic requires the ANTHROPIC_API_KEY environment variable");
                }
                yield new AnthropicLlm(new AnthropicLlm.Settings(apiKey, properties.baseUrl(), properties.model(),
                        properties.maxTokens(), properties.timeout()));
            }
        };
    }

    @Bean
    AuditLog auditLog() {
        return new InMemoryAuditLog();
    }

    @Bean
    AssessmentPipeline assessmentPipeline(LlmPort llm, AuditLog audit) {
        return new AssessmentPipeline(llm, audit, Clock.systemUTC());
    }
}
