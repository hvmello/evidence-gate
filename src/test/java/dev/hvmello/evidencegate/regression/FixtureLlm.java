package dev.hvmello.evidencegate.regression;

import dev.hvmello.evidencegate.domain.Classification;
import dev.hvmello.evidencegate.domain.Classification.Intent;
import dev.hvmello.evidencegate.port.LlmPort;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The only stand-in in the regression suites: it returns what a fixture scripts, counts calls and records
 * every payload it receives. It never calls a model.
 */
final class FixtureLlm implements LlmPort {

    private final AtomicInteger classifyCalls = new AtomicInteger();
    private final AtomicInteger draftCalls = new AtomicInteger();
    private final List<String> payloads = new CopyOnWriteArrayList<>();
    private final List<RuntimeException> thrown = new CopyOnWriteArrayList<>();

    private volatile Classification classification;
    private volatile String draft;
    private volatile boolean throwing;

    FixtureLlm() {
        reset();
    }

    /** Clears counters and records and goes back to a valid classification and an allowed draft. */
    FixtureLlm reset() {
        classifyCalls.set(0);
        draftCalls.set(0);
        payloads.clear();
        thrown.clear();
        classification = new Classification(
                Intent.CONTINUE, List.of(new Classification.Citation(1, "Quero continuar o tratamento")), false);
        draft = "Olá, {NAME}. Como prefere seguir?";
        throwing = false;
        return this;
    }

    FixtureLlm script(Classification scriptedClassification, String scriptedDraft) {
        if (scriptedClassification != null) {
            classification = scriptedClassification;
        }
        if (scriptedDraft != null) {
            draft = scriptedDraft;
        }
        return this;
    }

    /** Makes every call throw an exception whose message and cause carry the payload it received. */
    FixtureLlm throwing() {
        throwing = true;
        return this;
    }

    int classifyCalls() {
        return classifyCalls.get();
    }

    int draftCalls() {
        return draftCalls.get();
    }

    int calls() {
        return classifyCalls.get() + draftCalls.get();
    }

    List<String> payloads() {
        return List.copyOf(payloads);
    }

    List<RuntimeException> thrown() {
        return List.copyOf(thrown);
    }

    @Override
    public Result<Classification> classify(String redactedPayload) {
        classifyCalls.incrementAndGet();
        payloads.add(redactedPayload);
        failIfThrowing(redactedPayload);
        return new Result.Success<>(classification);
    }

    @Override
    public Result<String> draft(Intent intent, String redactedPayload) {
        draftCalls.incrementAndGet();
        payloads.add(redactedPayload);
        failIfThrowing(redactedPayload);
        return new Result.Success<>(draft);
    }

    private void failIfThrowing(String payload) {
        if (throwing) {
            var e = new IllegalStateException(
                    "scripted failure for payload: " + payload, new RuntimeException("cause saw: " + payload));
            thrown.add(e);
            throw e;
        }
    }

    @Override
    public String modelId() {
        return "fixture-llm";
    }

    @Override
    public String promptVersion() {
        return "fixture-v1";
    }
}
