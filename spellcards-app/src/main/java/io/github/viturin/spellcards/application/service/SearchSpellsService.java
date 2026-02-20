package io.github.viturin.spellcards.application.service;

import io.github.viturin.spellcards.application.port.in.SearchSpellsResult;
import io.github.viturin.spellcards.application.port.in.SearchSpellsResultItem;
import io.github.viturin.spellcards.application.port.in.SpellSearchService;
import io.github.viturin.spellcards.domain.model.Spell;
import io.github.viturin.spellcards.domain.model.SpellKind;
import io.github.viturin.spellcards.domain.port.SpellRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class SearchSpellsService implements SpellSearchService {
    private static final double MIN_SCORE = 0.35d;

    private final SpellRepository spellRepository;

    public SearchSpellsService(SpellRepository spellRepository) {
        this.spellRepository = spellRepository;
    }

    @Override
    public SearchSpellsResult search(String query, SpellKind kind, int limit, String cursor) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        int safeLimit = Math.max(1, Math.min(100, limit));
        int offset = decodeOffset(cursor);

        List<SearchSpellsResultItem> ranked = spellRepository.findAll().stream()
                .filter(spell -> kind == null || spell.kind() == kind)
                .map(spell -> new SearchSpellsResultItem(spell, computeScore(normalizedQuery, spell)))
                .filter(item -> item.score() >= MIN_SCORE)
                .sorted(Comparator
                        .comparingDouble(SearchSpellsResultItem::score).reversed()
                        .thenComparing(item -> item.spell().name()))
                .toList();

        if (offset >= ranked.size()) {
            return new SearchSpellsResult(List.of(), Optional.empty());
        }

        int toIndex = Math.min(offset + safeLimit, ranked.size());
        List<SearchSpellsResultItem> page = ranked.subList(offset, toIndex);
        Optional<String> nextCursor = toIndex < ranked.size()
                ? Optional.of(encodeOffset(toIndex))
                : Optional.empty();
        return new SearchSpellsResult(page, nextCursor);
    }

    private double computeScore(String query, Spell spell) {
        if (query.isBlank()) {
            return 0.0d;
        }

        String name = spell.name().toLowerCase(Locale.ROOT);
        if (name.equals(query)) {
            return 1.0d;
        }
        if (name.startsWith(query)) {
            return 0.95d;
        }
        if (name.contains(query)) {
            return 0.80d;
        }
        return diceCoefficient(query, name);
    }

    private double diceCoefficient(String left, String right) {
        if (left.length() < 2 || right.length() < 2) {
            return 0.0d;
        }
        int matches = 0;
        for (int i = 0; i < left.length() - 1; i++) {
            String biGram = left.substring(i, i + 2);
            if (right.contains(biGram)) {
                matches++;
            }
        }
        return (2.0d * matches) / ((left.length() - 1) + (right.length() - 1));
    }

    private int decodeOffset(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            return Math.max(Integer.parseInt(decoded), 0);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String encodeOffset(int offset) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(String.valueOf(offset).getBytes(StandardCharsets.UTF_8));
    }
}
