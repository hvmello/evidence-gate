package dev.hvmello.evidencegate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hvmello.evidencegate.adapter.llm.AnthropicLlm;
import dev.hvmello.evidencegate.adapter.llm.ScriptedLlm;
import dev.hvmello.evidencegate.port.LlmPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LlmWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PipelineConfiguration.class);

    @Test
    void defaultIsTheScriptedModel() {
        runner.run(context -> assertThat(context.getBean(LlmPort.class)).isInstanceOf(ScriptedLlm.class));
    }

    @Test
    void anthropicWithAKeyBuildsTheRealAdapter() {
        runner.withPropertyValues("evidence-gate.llm.provider=anthropic", "evidence-gate.llm.model=claude-test",
                        "ANTHROPIC_API_KEY=test-key")
                .run(context -> {
                    var llm = context.getBean(LlmPort.class);
                    assertThat(llm).isInstanceOf(AnthropicLlm.class);
                    assertThat(llm.modelId()).isEqualTo("claude-test");
                });
    }

    @Test
    void anthropicWithoutAKeyFailsAtStartup() {
        runner.withPropertyValues("evidence-gate.llm.provider=anthropic")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("ANTHROPIC_API_KEY");
                });
    }

    @Test
    void unknownProviderFailsAtStartup() {
        runner.withPropertyValues("evidence-gate.llm.provider=openai")
                .run(context -> assertThat(context).hasFailed());
    }
}
