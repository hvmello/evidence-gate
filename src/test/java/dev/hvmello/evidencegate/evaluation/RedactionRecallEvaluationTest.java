package dev.hvmello.evidencegate.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hvmello.evidencegate.evaluation.RedactionRecallSet.Manifest;
import dev.hvmello.evidencegate.gate.Redactor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * E1: redaction recall per identifier class. Deterministic, no model, no network. The test fails when the
 * redactor, the generator or the set no longer match the manifest, and when the committed report is stale.
 */
@Tag("evaluation")
class RedactionRecallEvaluationTest {

    static final Path REDACTOR = Path.of("src/main/java/dev/hvmello/evidencegate/gate/Redactor.java");
    static final Path GENERATOR = Path.of("scripts/generate_redaction_set.py");
    static final Path REPORT = Path.of("docs/results/e1-redaction-recall.md");

    @Test
    void publishedRecallMatchesTheFrozenRedactorAndSet() throws IOException {
        var manifest = RedactionRecallSet.loadManifest();
        var problems = freezeProblems(manifest, hashOf(REDACTOR), hashOf(GENERATOR),
                RedactionRecallSet.sha256Lf(RedactionRecallSet.setText(manifest)));
        assertThat(problems).as("freeze").isEmpty();

        var entries = RedactionRecallSet.load(manifest);
        assertThat(entries).hasSize(manifest.records());

        var redactor = new Redactor();
        var scored = new ArrayList<RedactionRecallReport.Scored>();
        for (var entry : entries) {
            var text = redactor.redact(entry.conversation()).messages().getFirst().text();
            scored.add(new RedactionRecallReport.Scored(entry, RedactionScorer.score(
                    entry.prefix(), entry.identifier(), entry.suffix(), text, entry.expectedPlaceholder())));
        }
        var stats = RedactionRecallReport.aggregate(scored);
        stats.forEach(s -> System.out.println(RedactionRecallReport.evaluationLine(s, manifest.setVersion())));
        System.out.flush();

        if (Boolean.getBoolean("e1.write")) {
            Files.createDirectories(REPORT.getParent());
            Files.writeString(REPORT, RedactionRecallReport.render(stats, manifest, LocalDate.now().toString()),
                    StandardCharsets.UTF_8);
            return;
        }
        var computed = RedactionRecallReport.render(stats, manifest, "ignored");
        assertThat(REPORT).as("committed report, write it with: " + RedactionRecallReport.COMMAND).exists();
        var committed = Files.readString(REPORT, StandardCharsets.UTF_8);
        assertThat(RedactionRecallReport.withoutDate(committed))
                .as("committed report is stale, rewrite it with: " + RedactionRecallReport.COMMAND)
                .isEqualTo(RedactionRecallReport.withoutDate(computed));
    }

    /** The seam: the checks take the actual hashes as input, so a changed hash can be simulated. */
    static List<String> freezeProblems(Manifest manifest, String redactorSha, String generatorSha, String setSha) {
        var problems = new ArrayList<String>();
        if (!manifest.redactorSha256().equals(redactorSha)) {
            problems.add("Redactor.java changed after set " + manifest.setVersion() + " was generated. Do not "
                    + "re-measure on this set: tuning the redactor on a set and measuring on the same set inflates "
                    + "recall. Generate a new version with a new seed (python scripts/generate_redaction_set.py "
                    + "--version <next> --seed <new>), then rewrite the report with: "
                    + RedactionRecallReport.COMMAND);
        }
        if (!manifest.generatorSha256().equals(generatorSha)) {
            problems.add("scripts/generate_redaction_set.py no longer matches the hash recorded in the manifest. "
                    + "Restore it, or generate a new set version and report.");
        }
        if (!manifest.setSha256().equals(setSha)) {
            problems.add("Set " + manifest.setVersion() + " was edited after it was generated. A set is never "
                    + "edited: generate a new version instead.");
        }
        return problems;
    }

    private static String hashOf(Path path) throws IOException {
        return RedactionRecallSet.sha256Lf(Files.readString(path, StandardCharsets.UTF_8));
    }

    @Test
    void aMatchingManifestHasNoProblems() {
        assertThat(freezeProblems(manifest(), "r", "g", "s")).isEmpty();
    }

    @Test
    void aChangedRedactorFailsWithAnActionableMessage() {
        assertThat(freezeProblems(manifest(), "other", "g", "s")).singleElement().asString()
                .contains("Redactor.java changed")
                .contains("inflates recall")
                .contains("--version");
    }

    @Test
    void aChangedGeneratorFails() {
        assertThat(freezeProblems(manifest(), "r", "other", "s")).singleElement().asString()
                .contains("generate_redaction_set.py");
    }

    @Test
    void anEditedSetFails() {
        assertThat(freezeProblems(manifest(), "r", "g", "other")).singleElement().asString()
                .contains("never edited");
    }

    private static Manifest manifest() {
        return new Manifest("v1", 1, "2026-01-01", 1, "g", "r", "s");
    }
}
