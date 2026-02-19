package io.github.viturin.spellcards.domain.port;

import io.github.viturin.spellcards.domain.model.Spell;

import java.util.Optional;

public interface SpellRepository {
    Optional<Spell> findByName(String name);
}
