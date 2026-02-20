package io.github.viturin.spellcards.domain.model;

import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
public interface Spell {
    String id();

    String name();

    int level();

    String description();

    @Value.Default
    default SpellKind kind() {
        return SpellKind.SPELL;
    }

    @Value.Default
    default String actionCost() {
        return "";
    }

    @Value.Default
    default String range() {
        return "";
    }

    @Value.Default
    default String target() {
        return "";
    }

    @Value.Default
    default String duration() {
        return "";
    }

    @Value.Default
    default boolean sustained() {
        return false;
    }

    @Value.Default
    default String rarity() {
        return "common";
    }

    @Value.Default
    default List<String> traditions() {
        return List.of();
    }

    @Value.Default
    default List<String> traits() {
        return List.of();
    }
}
