package io.github.viturin.spellcards.application.port.in;

import io.github.viturin.spellcards.domain.model.Spell;

public record SearchSpellsResultItem(Spell spell, double score) {
}
