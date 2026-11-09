package dev.hvmello.evidencegate.application;

import dev.hvmello.evidencegate.domain.Assessment;
import dev.hvmello.evidencegate.domain.Assessment.Outcome;
import dev.hvmello.evidencegate.domain.Classification;
import dev.hvmello.evidencegate.domain.Conversation;
import dev.hvmello.evidencegate.gate.EvidenceGate;
import dev.hvmello.evidencegate.gate.PolicyFilter;
import dev.hvmello.evidencegate.gate.Redactor;
import dev.hvmello.evidencegate.port.AuditLog;
import dev.hvmello.evidencegate.port.LlmPort;
import dev.hvmello.evidencegate.port.LlmPort.Result;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.function.Supplier;

/** Runs the steps in a fixed order. Code owns the control flow; the model only fills in two steps. */
public final class AssessmentPipeline {

    private final LlmPort llm;
    private final AuditLog audit;
    private final Clock clock;
    private final Redactor redactor = new Redactor();
    private final EvidenceGate evidenceGate = new EvidenceGate();
    private final PolicyFilter policyFilter = new PolicyFilter();

    public AssessmentPipeline(LlmPort llm, AuditLog audit, Clock clock) {
        this.llm = llm;
        this.audit = audit;
        this.clock = clock;
    }

    public Assessment assess(Conversation conversation) {
        if (!conversation.consent()) {
            var stopped = Assessment.stopped(Outcome.NO_CONSENT, "NO_CONSENT");
            record(conversation, stopped, null, 0);
            return stopped;
        }

        var redacted = redactor.redact(conversation);
        var result = run(redacted);
        record(conversation, result.assessment(), redacted.payloadSha256(), result.modelCalls());
        return result.assessment();
    }

    private Run run(Redactor.Redacted redacted) {
        var classified = safely(() -> llm.classify(redacted.payload()));
        if (classified instanceof Result.Failure<?> failure) {
            return new Run(modelFailed(failure), 1);
        }
        var classification = ((Result.Success<Classification>) classified).value();

        return switch (evidenceGate.verify(classification, redacted)) {
            case EvidenceGate.Verdict.Insufficient ignored ->
                    new Run(Assessment.stopped(Outcome.INSUFFICIENT_EVIDENCE, "MODEL_ABSTAINED"), 1);
            case EvidenceGate.Verdict.Mismatch mismatch ->
                    new Run(Assessment.stopped(Outcome.EVIDENCE_MISMATCH, mismatch.reasonCode()), 1);
            case EvidenceGate.Verdict.Verified verified -> {
                var drafted = safely(() -> llm.draft(classification.intent(), redacted.payload()));
                if (drafted instanceof Result.Failure<?> failure) {
                    yield new Run(modelFailed(failure), 2);
                }
                var draft = ((Result.Success<String>) drafted).value();
                var violation = policyFilter.violation(draft);
                if (violation.isPresent()) {
                    yield new Run(Assessment.stopped(Outcome.POLICY_BLOCKED, violation.get()), 2);
                }
                yield new Run(
                        new Assessment(Outcome.PASSED, classification.intent(), verified.citations(), draft, null), 2);
            }
        };
    }

    /** An adapter that throws or returns nothing is a failed call, so it still ends in an audited outcome. */
    private static <T> Result<T> safely(Supplier<Result<T>> call) {
        try {
            var result = call.get();
            if (result == null || (result instanceof Result.Success<T> success && success.value() == null)) {
                return new Result.Failure<>(LlmPort.FailureCode.INVALID_OUTPUT);
            }
            return result;
        } catch (RuntimeException e) {
            return new Result.Failure<>(LlmPort.FailureCode.TRANSPORT);
        }
    }

    private static Assessment modelFailed(Result.Failure<?> failure) {
        return Assessment.stopped(Outcome.MODEL_FAILED, failure.code().name());
    }

    private void record(Conversation conversation, Assessment assessment, String payloadSha256, int modelCalls) {
        audit.record(new AuditLog.Entry(
                clock.instant(),
                sha256(conversation.subjectId()),
                assessment.outcome(),
                assessment.reason(),
                payloadSha256,
                llm.modelId(),
                llm.promptVersion(),
                modelCalls));
    }

    private static String sha256(String text) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record Run(Assessment assessment, int modelCalls) { }
}
