package io.github.viturin.spellcards.application.port.in;

import java.util.List;

public interface GenerateSpellCardsUseCase {
    void generateForSpells(List<String> spellNames);
}
