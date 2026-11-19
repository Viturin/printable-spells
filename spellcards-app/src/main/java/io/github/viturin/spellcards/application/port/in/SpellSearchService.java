package io.github.viturin.spellcards.application.port.in;

import io.github.viturin.spellcards.domain.model.SpellKind;

import java.util.List;

public interface SpellSearchService {
    SearchSpellsResult search(String query, SpellKind kind, int limit, String cursor);

    SpellNameResolution resolveNames(List<String> names);
}
