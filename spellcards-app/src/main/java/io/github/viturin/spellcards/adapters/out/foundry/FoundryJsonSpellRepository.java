package io.github.viturin.spellcards.adapters.out.foundry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.viturin.spellcards.domain.model.Spell;
import io.github.viturin.spellcards.domain.port.SpellRepository;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class FoundryJsonSpellRepository implements SpellRepository {
    private static final String SPELLS_PATH = "foundry/spells/spells.json";

    private final Map<String, Spell> spellsByLowerName = new HashMap<>();
    private final FoundrySpellJsonParser parser = new FoundrySpellJsonParser();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FoundryJsonSpellRepository() {
        loadSpellsFromResources();
    }

    @Override
    public Optional<Spell> findByName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(spellsByLowerName.get(name.toLowerCase()));
    }

    @Override
    public List<Spell> findAll() {
        return List.copyOf(spellsByLowerName.values());
    }

    private void loadSpellsFromResources() {
        try (InputStream input = getRequiredResource()) {
            String content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            JsonNode root = objectMapper.readTree(content);

            for (JsonNode document : extractDocuments(root)) {
                parser.parseSpell(document.toString(), SPELLS_PATH)
                        .ifPresent(spell -> spellsByLowerName.putIfAbsent(spell.name().toLowerCase(), spell));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load spells from: " + SPELLS_PATH, e);
        }
    }

    private InputStream getRequiredResource() {
        InputStream resource = Thread.currentThread().getContextClassLoader().getResourceAsStream(FoundryJsonSpellRepository.SPELLS_PATH);
        if (resource == null) {
            throw new IllegalStateException("Missing resource on classpath: " + FoundryJsonSpellRepository.SPELLS_PATH);
        }
        return resource;
    }

    private List<JsonNode> extractDocuments(JsonNode root) {
        List<JsonNode> documents = new ArrayList<>();
        if (root == null) {
            return documents;
        }
        if (root.isArray()) {
            root.forEach(documents::add);
            return documents;
        }
        if (root.path("documents").isArray()) {
            root.path("documents").forEach(documents::add);
            return documents;
        }
        if (root.path("items").isArray()) {
            root.path("items").forEach(documents::add);
            return documents;
        }
        if (root.path("entries").isArray()) {
            root.path("entries").forEach(documents::add);
        }
        return documents;
    }
}
