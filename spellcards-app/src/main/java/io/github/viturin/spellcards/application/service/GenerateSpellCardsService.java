package io.github.viturin.spellcards.application.service;

import io.github.viturin.spellcards.application.port.in.SpellCardGenerationService;
import io.github.viturin.spellcards.domain.model.Spell;
import io.github.viturin.spellcards.domain.port.SpellRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class GenerateSpellCardsService implements SpellCardGenerationService {
    private static final Logger LOG = LoggerFactory.getLogger(GenerateSpellCardsService.class);

    private final SpellRepository spellCatalog;

    public GenerateSpellCardsService(SpellRepository spellCatalog) {
        this.spellCatalog = spellCatalog;
    }

    @Override
    public void generateForSpells(List<String> spellNames) {
        for (String spellName : spellNames) {
            Optional<Spell> spell = spellCatalog.findByName(spellName);
            if (spell.isEmpty()) {
                LOG.warn("Spell not found: {}", spellName);
                continue;
            }

            Spell value = spell.get();
            LOG.info("Spell card data -> name: {} | level: {} | description: {}",
                    value.name(), value.level(), value.description());
        }
    }
}
