package dev.hvmello.evidencegate;

import dev.hvmello.evidencegate.adapter.audit.InMemoryAuditLog;
import dev.hvmello.evidencegate.adapter.llm.ScriptedLlm;
import dev.hvmello.evidencegate.application.AssessmentPipeline;
import dev.hvmello.evidencegate.port.AuditLog;
import dev.hvmello.evidencegate.port.LlmPort;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class PipelineConfiguration {

    @Bean
    LlmPort llmPort() {
        return new ScriptedLlm();
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
