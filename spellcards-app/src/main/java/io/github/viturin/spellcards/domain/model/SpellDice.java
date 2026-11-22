package io.github.viturin.spellcards.domain.model;

public record SpellDice(String attack, String effect, String heightening) {
    public static final SpellDice EMPTY = new SpellDice("", "", "");
}
