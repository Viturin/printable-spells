package io.github.viturin.spellcards.domain.port;

import io.github.viturin.spellcards.domain.model.Spell;

import java.util.List;
import java.util.Optional;

public interface SpellRepository {
    Optional<Spell> findByName(String name);

    List<Spell> findAll();
}
