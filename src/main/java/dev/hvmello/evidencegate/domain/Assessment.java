package dev.hvmello.evidencegate.domain;

import dev.hvmello.evidencegate.domain.Classification.Intent;
import dev.hvmello.evidencegate.domain.Conversation.Speaker;
import java.util.List;

/** The result a human reviewer sees. Quotes stay redacted; the message id leads back to the original. */
public record Assessment(
        Outcome outcome, Intent intent, List<VerifiedCitation> citations, String draft, String reason) {

    public enum Outcome {
        PASSED, NO_CONSENT, INSUFFICIENT_EVIDENCE, EVIDENCE_MISMATCH, POLICY_BLOCKED, MODEL_FAILED
    }

    /** The speaker comes from the message record, never from the model. */
    public record VerifiedCitation(int messageId, Speaker speaker, String quote) { }

    public static Assessment stopped(Outcome outcome, String reason) {
        return new Assessment(outcome, null, List.of(), null, reason);
    }
}
