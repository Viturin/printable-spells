package io.github.viturin.spellcards.application.port.in;

import java.util.List;
import java.util.Optional;

public record SearchSpellsResult(List<SearchSpellsResultItem> items, Optional<String> nextCursor) {
}
