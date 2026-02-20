package io.github.viturin.spellcards.adapters.out.foundry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("generated-data")
class FoundryGeneratedSpellsParseIT {
    private static final Path SPELLS_PATH = Path.of("src/generated/resources/foundry/spells/spells.json");

    @Test
    void generatedSpellsFileExistsAndIsNotEmpty() throws IOException {
        assertTrue(Files.exists(SPELLS_PATH),
                "Missing generated spells file at " + SPELLS_PATH + ". Run fetch first.");

        assertFalse(loadSpellDocuments().isEmpty(),
                "Generated spells file has no spell documents: " + SPELLS_PATH);
    }

    @ParameterizedTest(name = "parse generated spell: {0}")
    @MethodSource("spellDocuments")
    void parsesEachGeneratedSpellJson(String spellName, String json) {
        FoundrySpellJsonParser parser = new FoundrySpellJsonParser();

        assertTrue(parser.parseSpell(json, "foundry/spells/spells.json").isPresent(),
                "Failed to parse generated spell JSON: " + spellName);
    }

    private static Stream<Arguments> spellDocuments() throws IOException {
        return loadSpellDocuments().stream()
                .map(document -> Arguments.of(document.path("name").asText("unknown"), document.toString()));
    }

    private static java.util.List<JsonNode> loadSpellDocuments() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode root = objectMapper.readTree(Files.readString(SPELLS_PATH, StandardCharsets.UTF_8));
        java.util.List<JsonNode> documents = new ArrayList<>();

        if (root != null && root.isArray()) {
            root.forEach(node -> {
                if ("spell".equalsIgnoreCase(node.path("type").asText())) {
                    documents.add(node);
                }
            });
            return documents;
        }

        JsonNode candidates = root == null ? null : root.path("documents");
        if (candidates != null && candidates.isArray()) {
            candidates.forEach(node -> {
                if ("spell".equalsIgnoreCase(node.path("type").asText())) {
                    documents.add(node);
                }
            });
        }

        return documents;
    }
}
