package io.github.viturin.spellcards.adapters.out.foundry;

import io.github.viturin.spellcards.domain.model.Spell;
import io.github.viturin.spellcards.domain.port.SpellRepository;
import org.springframework.stereotype.Repository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class FoundryJsonSpellRepository implements SpellRepository {
    private static final String INDEX_PATH = "foundry/spells/index.txt";

    private final Map<String, Spell> spellsByLowerName = new HashMap<>();
    private final FoundrySpellJsonParser parser = new FoundrySpellJsonParser();

    public FoundryJsonSpellRepository() {
        loadIndexFromResources();
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

    private void loadIndexFromResources() {
        try (InputStream input = getRequiredResource(INDEX_PATH);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String resourceName;
            while ((resourceName = reader.readLine()) != null) {
                String trimmed = resourceName.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                loadSpellResource("foundry/spells/" + trimmed);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load spell index: " + INDEX_PATH, e);
        }
    }

    private void loadSpellResource(String path) throws IOException {
        try (InputStream input = getRequiredResource(path)) {
            String content = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            // Some sources are one object per file, others are NDJSON lines.
            String[] candidates = content.lines().anyMatch(line -> line.trim().startsWith("{"))
                    ? content.split("\\R")
                    : new String[]{content};

            for (String candidate : candidates) {
                parser.parseSpell(candidate, path)
                        .ifPresent(spell -> spellsByLowerName.putIfAbsent(spell.name().toLowerCase(), spell));
            }

            parser.parseSpell(content, path)
                    .ifPresent(spell -> spellsByLowerName.putIfAbsent(spell.name().toLowerCase(), spell));
        }
    }

    private InputStream getRequiredResource(String path) {
        InputStream resource = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        if (resource == null) {
            throw new IllegalStateException("Missing resource on classpath: " + path);
        }
        return resource;
    }
}
