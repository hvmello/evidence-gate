package dev.hvmello.evidencegate.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hvmello.evidencegate.gate.Redactor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class RedactionRecallSetTest {

    private static String line(String id, String cls, String prefix, String suffix, String extra) {
        return "{\"id\":\"" + id + "\",\"class\":\"" + cls + "\",\"variant\":\"v\",\"expectedPlaceholder\":\"[CPF]\","
                + "\"speaker\":\"PATIENT\",\"patientName\":\"Aurélio Brandão\",\"staffName\":\"Débora Linhares\","
                + "\"prefix\":\"" + prefix + "\",\"identifier\":\"123\",\"suffix\":\"" + suffix + "\"" + extra + "}";
    }

    private static String valid(String id) {
        return line(id, "CPF", "Meu cpf é ", " ok.", "");
    }

    @Test
    void loadsAValidLine() {
        assertThat(RedactionRecallSet.parse("t", List.of(valid("a")))).hasSize(1);
    }

    @Test
    void aMalformedLineFailsTheLoad() {
        assertThatThrownBy(() -> RedactionRecallSet.parse("t", List.of(valid("a"), "{not json")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("line 2");
    }

    @Test
    void anUnknownFieldFailsTheLoad() {
        assertThatThrownBy(() -> RedactionRecallSet.parse("t", List.of(line("a", "CPF", "x ", " y", ",\"extra\":1"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aDuplicateIdFailsTheLoad() {
        assertThatThrownBy(() -> RedactionRecallSet.parse("t", List.of(valid("a"), valid("a"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate id a");
    }

    @Test
    void anUnknownClassFailsTheLoad() {
        assertThatThrownBy(() -> RedactionRecallSet.parse("t", List.of(line("a", "PASSPORT", "x ", " y", ""))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aMissingFieldFailsTheLoad() {
        assertThatThrownBy(() -> RedactionRecallSet.parse("t", List.of("{\"id\":\"a\",\"class\":\"CPF\"}")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anEmptySetFailsTheLoad() {
        assertThatThrownBy(() -> RedactionRecallSet.parse("t", List.of("", " ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no records");
    }

    @Test
    void fillerWithADigitFailsTheLoad() {
        assertThatThrownBy(() -> RedactionRecallSet.parse("t", List.of(line("a", "CPF", "Ligue 2 ", " ok.", ""))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("filler");
    }

    @Test
    void fillerWithAnIdentifierMarkerFailsTheLoad() {
        for (var marker : List.of("a@b", "R$", "http", "www")) {
            assertThatThrownBy(() -> RedactionRecallSet.parse("t",
                    List.of(line("a", "CPF", "veja " + marker + " ", " ok.", ""))))
                    .as(marker)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void fillerWithASuppliedNameTokenFailsTheLoadEvenWithoutAccents() {
        assertThatThrownBy(() -> RedactionRecallSet.parse("t",
                List.of(line("a", "CPF", "Fale com a Debora ", " ok.", ""))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("debora");
    }

    @Test
    void theCommittedSetLoadsAndMatchesTheManifest() {
        var manifest = RedactionRecallSet.loadManifest();
        var entries = RedactionRecallSet.load(manifest);

        assertThat(entries).hasSize(manifest.records()).hasSizeGreaterThanOrEqualTo(600);
        assertThat(entries.stream().map(e -> e.identifierClass()).distinct()).hasSize(
                RedactionRecallSet.IdentifierClass.values().length);
    }

    @Test
    void theFillerIsInertForEveryRecord() {
        var entries = RedactionRecallSet.load(RedactionRecallSet.loadManifest());
        var redactor = new Redactor();

        var damaged = new ArrayList<String>();
        for (var entry : entries) {
            var conversation = entry.conversationWith(entry.prefix() + entry.suffix());
            var text = redactor.redact(conversation).messages().getFirst().text();
            if (!RedactionScorer.skeleton(text).equals(RedactionScorer.skeleton(entry.prefix() + entry.suffix()))) {
                damaged.add(entry.id());
            }
        }

        assertThat(damaged).as("records whose filler the redactor changes").isEmpty();
    }

    @Test
    void noIdentifierAppearsInTheRedactorTestsOrTheRegressionFixtures() throws IOException {
        var corpus = new ArrayList<String>();
        corpus.add(Files.readString(
                Path.of("src/test/java/dev/hvmello/evidencegate/gate/RedactorTest.java"), StandardCharsets.UTF_8));
        try (Stream<Path> files = Files.list(Path.of("src/test/resources/regression"))) {
            for (var file : files.filter(f -> f.toString().endsWith(".jsonl")).toList()) {
                corpus.add(Files.readString(file, StandardCharsets.UTF_8));
            }
        }
        var entries = RedactionRecallSet.load(RedactionRecallSet.loadManifest());

        var overlapping = entries.stream()
                .filter(e -> corpus.stream().anyMatch(text -> text.contains(e.identifier())))
                .map(RedactionRecallSet.Entry::id)
                .collect(Collectors.toList());

        assertThat(overlapping).as("identifiers already used by the redactor tests or regression fixtures").isEmpty();
    }

    @Test
    void sha256IgnoresLineEndings() {
        assertThat(RedactionRecallSet.sha256Lf("a\r\nb\n")).isEqualTo(RedactionRecallSet.sha256Lf("a\nb\n"));
    }
}
