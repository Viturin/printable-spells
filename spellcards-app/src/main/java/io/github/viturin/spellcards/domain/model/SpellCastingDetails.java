package io.github.viturin.spellcards.domain.model;

public record SpellCastingDetails(
        String area,
        String defense,
        String requirements,
        String cost,
        String trigger,
        String primaryCheck,
        String secondaryChecks,
        String secondaryCasters
) {
    public static final SpellCastingDetails EMPTY = new SpellCastingDetails("", "", "", "", "", "", "", "");
}
