package dev.hvmello.evidencegate.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hvmello.evidencegate.adapter.audit.InMemoryAuditLog;
import dev.hvmello.evidencegate.domain.Assessment.Outcome;
import dev.hvmello.evidencegate.domain.Classification;
import dev.hvmello.evidencegate.domain.Classification.Citation;
import dev.hvmello.evidencegate.domain.Classification.Intent;
import dev.hvmello.evidencegate.domain.Conversation;
import dev.hvmello.evidencegate.domain.Conversation.Message;
import dev.hvmello.evidencegate.domain.Conversation.Speaker;
import dev.hvmello.evidencegate.port.LlmPort;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssessmentPipelineTest {

    private static final String CANARY_PHONE = "(62) 9 8123-4567";

    private static class StubLlm implements LlmPort {
        Result<Classification> classification = new Result.Success<>(new Classification(
                Intent.CONTINUE, List.of(new Citation(1, "quero continuar o tratamento")), false));
        Result<String> draft = new Result.Success<>("Olá, {NAME}. Como prefere seguir?");
        final List<String> payloads = new ArrayList<>();

        @Override
        public Result<Classification> classify(String payload) {
            payloads.add(payload);
            return classification;
        }

        @Override
        public Result<String> draft(Intent intent, String payload) {
            payloads.add(payload);
            return draft;
        }

        @Override
        public String modelId() {
            return "stub";
        }

        @Override
        public String promptVersion() {
            return "v0";
        }
    }

    private final StubLlm llm = new StubLlm();
    private final InMemoryAuditLog audit = new InMemoryAuditLog();
    private final AssessmentPipeline pipeline = new AssessmentPipeline(llm, audit, Clock.systemUTC());

    private static Conversation conversation(boolean consent) {
        return new Conversation("subject-42", consent, "Beatriz Lima", "Marina", List.of(new Message(
                1, Speaker.PATIENT, "Beatriz aqui, quero continuar o tratamento. Meu fone é " + CANARY_PHONE)));
    }

    @Test
    void passesWhenEveryGatePasses() {
        var result = pipeline.assess(conversation(true));

        assertThat(result.outcome()).isEqualTo(Outcome.PASSED);
        assertThat(result.intent()).isEqualTo(Intent.CONTINUE);
        assertThat(result.draft()).contains("{NAME}");
    }

    @Test
    void withoutConsentTheModelIsNeverCalled() {
        var result = pipeline.assess(conversation(false));

        assertThat(result.outcome()).isEqualTo(Outcome.NO_CONSENT);
        assertThat(llm.payloads).isEmpty();
        assertThat(audit.entries().getFirst().modelCalls()).isZero();
    }

    @Test
    void identifiersNeverReachTheModel() {
        pipeline.assess(conversation(true));

        assertThat(llm.payloads).hasSize(2).allSatisfy(payload -> assertThat(payload)
                .doesNotContain("Beatriz", "Lima", CANARY_PHONE, "8123")
                .contains("[PATIENT_NAME]", "[PHONE]"));
    }

    @Test
    void aFabricatedCitationStopsBeforeTheDraftIsRequested() {
        llm.classification = new LlmPort.Result.Success<>(new Classification(
                Intent.END, List.of(new Citation(1, "nunca mais voltarei aqui")), false));

        var result = pipeline.assess(conversation(true));

        assertThat(result.outcome()).isEqualTo(Outcome.EVIDENCE_MISMATCH);
        assertThat(result.draft()).isNull();
        assertThat(llm.payloads).hasSize(1);
    }

    @Test
    void anAbstentionStopsWithoutCountingAsAFailure() {
        llm.classification = new LlmPort.Result.Success<>(new Classification(Intent.UNDECIDED, List.of(), true));

        assertThat(pipeline.assess(conversation(true)).outcome()).isEqualTo(Outcome.INSUFFICIENT_EVIDENCE);
    }

    @Test
    void aBlockedDraftIsNotReturned() {
        llm.draft = new LlmPort.Result.Success<>("{NAME}, sua ansiedade melhorou?");

        var result = pipeline.assess(conversation(true));

        assertThat(result.outcome()).isEqualTo(Outcome.POLICY_BLOCKED);
        assertThat(result.draft()).isNull();
    }

    @Test
    void aModelFailureDuringClassificationIsReportedByCode() {
        llm.classification = new LlmPort.Result.Failure<>(LlmPort.FailureCode.REFUSAL);

        var result = pipeline.assess(conversation(true));

        assertThat(result.outcome()).isEqualTo(Outcome.MODEL_FAILED);
        assertThat(result.reason()).isEqualTo("REFUSAL");
    }

    @Test
    void aModelFailureDuringDraftingIsReportedByCode() {
        llm.draft = new LlmPort.Result.Failure<>(LlmPort.FailureCode.TRUNCATED);

        var result = pipeline.assess(conversation(true));

        assertThat(result.outcome()).isEqualTo(Outcome.MODEL_FAILED);
        assertThat(result.reason()).isEqualTo("TRUNCATED");
    }

    @Test
    void anAdapterThatThrowsIsAuditedAsAFailedCall() {
        var throwing = new AssessmentPipeline(new StubLlm() {
            @Override
            public Result<Classification> classify(String payload) {
                throw new IllegalStateException("connection reset");
            }
        }, audit, Clock.systemUTC());

        var result = throwing.assess(conversation(true));

        assertThat(result.outcome()).isEqualTo(Outcome.MODEL_FAILED);
        assertThat(result.reason()).isEqualTo("TRANSPORT");
        assertThat(audit.entries()).hasSize(1);
    }

    @Test
    void anEmptyClassificationIsAFailedCall() {
        llm.classification = new LlmPort.Result.Success<>(null);

        var result = pipeline.assess(conversation(true));

        assertThat(result.reason()).isEqualTo("INVALID_OUTPUT");
        assertThat(audit.entries()).hasSize(1);
    }

    @Test
    void anEmptyDraftIsAFailedCall() {
        llm.draft = new LlmPort.Result.Success<>(null);

        var result = pipeline.assess(conversation(true));

        assertThat(result.outcome()).isEqualTo(Outcome.MODEL_FAILED);
        assertThat(result.reason()).isEqualTo("INVALID_OUTPUT");
        assertThat(audit.entries()).hasSize(1);
    }

    @Test
    void theAuditRowHoldsHashesAndCodesButNoContent() {
        llm.draft = new LlmPort.Result.Success<>("{NAME}, sua ansiedade melhorou?");

        pipeline.assess(conversation(true));

        var entry = audit.entries().getFirst();
        assertThat(entry.toString()).doesNotContain("Beatriz", "subject-42", "8123", "ansiedade", "tratamento");
        assertThat(entry.reasonCode()).isEqualTo("BLOCKED_TERM");
        assertThat(entry.payloadSha256()).hasSize(64);
        assertThat(entry.modelCalls()).isEqualTo(2);
    }
}
