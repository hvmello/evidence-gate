package dev.hvmello.evidencegate.adapter.llm;

import dev.hvmello.evidencegate.domain.Classification;
import dev.hvmello.evidencegate.domain.Classification.Citation;
import dev.hvmello.evidencegate.domain.Classification.Intent;
import dev.hvmello.evidencegate.port.LlmPort;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Keyword rules standing in for a model, so the service runs with no API key and no cost.
 * It is not a language model and says nothing about model quality.
 */
public final class ScriptedLlm implements LlmPort {

    private static final Pattern PATIENT_LINE = Pattern.compile("^#(\\d+) patient: (.+)$", Pattern.MULTILINE);

    @Override
    public Result<Classification> classify(String redactedPayload) {
        var matcher = PATIENT_LINE.matcher(redactedPayload);
        Citation citation = null;
        Intent intent = null;
        while (matcher.find()) {
            var text = matcher.group(2);
            var found = intentOf(text.toLowerCase(Locale.ROOT));
            if (found != null) {
                intent = found;
                citation = new Citation(Integer.parseInt(matcher.group(1)), text);
            }
        }
        if (citation == null) {
            return new Result.Success<>(new Classification(Intent.UNDECIDED, List.of(), true));
        }
        return new Result.Success<>(new Classification(intent, List.of(citation), false));
    }

    private static Intent intentOf(String text) {
        if (text.contains("encerrar") || text.contains("desistir")) {
            return Intent.END;
        }
        if (text.contains("pausa") || text.contains("viajar")) {
            return Intent.PAUSE;
        }
        if (text.contains("continuar") || text.contains("remarcar")) {
            return Intent.CONTINUE;
        }
        return null;
    }

    @Override
    public Result<String> draft(Intent intent, String redactedPayload) {
        return new Result.Success<>("Olá, {NAME}. Obrigada por avisar. Como prefere seguir daqui em diante?");
    }

    @Override
    public String modelId() {
        return "scripted";
    }

    @Override
    public String promptVersion() {
        return "none";
    }
}
