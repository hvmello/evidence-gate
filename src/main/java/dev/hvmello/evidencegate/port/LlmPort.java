package dev.hvmello.evidencegate.port;

import dev.hvmello.evidencegate.domain.Classification;

/** The only way the application reaches a model. It receives redacted text only. */
public interface LlmPort {

    Result<Classification> classify(String redactedPayload);

    Result<String> draft(Classification.Intent intent, String redactedPayload);

    String modelId();

    String promptVersion();

    enum FailureCode { REFUSAL, TRUNCATED, INVALID_OUTPUT, TRANSPORT }

    sealed interface Result<T> {
        record Success<T>(T value) implements Result<T> { }

        record Failure<T>(FailureCode code) implements Result<T> { }
    }
}
