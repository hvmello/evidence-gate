package dev.hvmello.evidencegate.port;

import dev.hvmello.evidencegate.domain.Assessment.Outcome;
import java.time.Instant;

/** One row per assessment. Hashes and codes only: no field may hold conversation or model text. */
public interface AuditLog {

    void record(Entry entry);

    record Entry(
            Instant at,
            String subjectSha256,
            Outcome outcome,
            String reasonCode,
            String payloadSha256,
            String modelId,
            String promptVersion,
            int modelCalls) { }
}
