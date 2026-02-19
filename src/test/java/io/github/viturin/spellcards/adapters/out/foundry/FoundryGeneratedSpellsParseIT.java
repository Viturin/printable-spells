package io.github.viturin.spellcards.adapters.out.foundry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("generated-data")
class FoundryGeneratedSpellsParseIT {
    private static final Path INDEX_PATH = Path.of("src/generated/resources/foundry/spells/index.txt");

    @Test
    void generatedSpellIndexExistsAndIsNotEmpty() throws IOException {
        assertTrue(Files.exists(INDEX_PATH),
                "Missing generated spell index at " + INDEX_PATH + ". Run fetch first.");

        assertFalse(loadIndexedResourceNames().isEmpty(),
                "Generated spell index is empty: " + INDEX_PATH);
    }

    @ParameterizedTest(name = "parse generated spell: {0}")
    @MethodSource("indexedSpellResourceNames")
    void parsesEachGeneratedSpellJson(String resourceName) throws IOException {
        Path spellPath = INDEX_PATH.getParent().resolve(resourceName);
        assertTrue(Files.exists(spellPath), "Missing file listed in index: " + resourceName);

        String json = Files.readString(spellPath, StandardCharsets.UTF_8);
        FoundrySpellJsonParser parser = new FoundrySpellJsonParser();

        assertTrue(parser.parseSpell(json, "foundry/spells/" + resourceName).isPresent(),
                "Failed to parse generated spell JSON: " + resourceName);
    }

    private static Stream<String> indexedSpellResourceNames() throws IOException {
        return loadIndexedResourceNames().stream();
    }

    private static List<String> loadIndexedResourceNames() throws IOException {
        return Files.readAllLines(INDEX_PATH, StandardCharsets.UTF_8).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .filter(line -> !line.startsWith("#"))
                .toList();
    }
}
