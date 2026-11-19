package io.github.viturin.spellcards.application.port.in;

import io.github.viturin.spellcards.domain.model.Spell;

import java.util.List;

public record SpellNameResolution(List<Spell> matches, List<String> unmatchedNames) {
    public SpellNameResolution {
        matches = List.copyOf(matches);
        unmatchedNames = List.copyOf(unmatchedNames);
    }
}
