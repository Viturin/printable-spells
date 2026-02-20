package io.github.viturin.spellcards.application.port.in;

import java.util.List;

public interface SpellCardGenerationService {
    void generateForSpells(List<String> spellNames);
}
