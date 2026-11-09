package dev.hvmello.evidencegate.domain;

import java.util.List;

/** What the model claims. Nothing here is trusted until the gates have checked it. */
public record Classification(Intent intent, List<Citation> citations, boolean insufficientEvidence) {

    public enum Intent { CONTINUE, UNDECIDED, PAUSE, END }

    public record Citation(int messageId, String quote) { }

    public Classification {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
