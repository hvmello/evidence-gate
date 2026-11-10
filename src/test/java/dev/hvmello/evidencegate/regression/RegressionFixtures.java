package dev.hvmello.evidencegate.regression;

import dev.hvmello.evidencegate.domain.Classification;
import dev.hvmello.evidencegate.domain.Conversation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Loads and validates the versioned fixture files. A malformed line, an unknown field or a duplicate id
 * fails the load, so a broken fixture cannot silently lower the number a suite reports.
 */
final class RegressionFixtures {

    static final String VERSION = "v2";

    private static final JsonMapper MAPPER =
            JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    /** One fixture line. Which fields are present depends on the suite. */
    record Fixture(
            String id,
            String category,
            Conversation conversation,
            Classification classification,
            String draft,
            String intent,
            List<String> canaries,
            String expected,
            JsonNode apiBody) {

        Fixture {
            canaries = canaries == null ? List.of() : List.copyOf(canaries);
        }
    }

    private RegressionFixtures() { }

    static List<Fixture> load(String resourceName) {
        var path = "/regression/" + resourceName;
        try (var stream = RegressionFixtures.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalArgumentException("fixture file not found: " + path);
            }
            var text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return parse(resourceName, text.lines().toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<Fixture> parse(String source, List<String> lines) {
        var fixtures = new java.util.ArrayList<Fixture>();
        var ids = new HashSet<String>();
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            Fixture fixture;
            try {
                fixture = MAPPER.readValue(line, Fixture.class);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(source + " line " + (i + 1) + ": " + e.getMessage(), e);
            }
            if (fixture.id() == null || fixture.id().isBlank() || fixture.category() == null) {
                throw new IllegalArgumentException(source + " line " + (i + 1) + ": id and category are required");
            }
            if (!ids.add(fixture.id())) {
                throw new IllegalArgumentException(source + " line " + (i + 1) + ": duplicate id " + fixture.id());
            }
            fixtures.add(fixture);
        }
        if (fixtures.isEmpty()) {
            throw new IllegalArgumentException(source + ": no fixtures");
        }
        return List.copyOf(fixtures);
    }
}
