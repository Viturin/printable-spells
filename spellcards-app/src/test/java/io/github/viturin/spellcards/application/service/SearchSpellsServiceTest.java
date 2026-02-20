package io.github.viturin.spellcards.application.service;

import io.github.viturin.spellcards.application.port.in.SearchSpellsResult;
import io.github.viturin.spellcards.domain.model.ImmutableSpell;
import io.github.viturin.spellcards.domain.model.Spell;
import io.github.viturin.spellcards.domain.model.SpellKind;
import io.github.viturin.spellcards.domain.port.SpellRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchSpellsServiceTest {

    @Test
    void returnsExactMatchFirst() {
        SearchSpellsService service = new SearchSpellsService(new InMemoryRepo(sampleSpells()));

        SearchSpellsResult result = service.search("daze", null, 10, null);

        assertFalse(result.items().isEmpty());
        assertEquals("Daze", result.items().getFirst().spell().name());
    }

    @Test
    void filtersByKind() {
        SearchSpellsService service = new SearchSpellsService(new InMemoryRepo(sampleSpells()));

        SearchSpellsResult result = service.search("extend", SpellKind.FOCUS, 10, null);

        assertEquals(1, result.items().size());
        assertEquals(SpellKind.FOCUS, result.items().getFirst().spell().kind());
    }

    @Test
    void supportsCursorPagination() {
        SearchSpellsService service = new SearchSpellsService(new InMemoryRepo(sampleSpells()));

        SearchSpellsResult page1 = service.search("a", null, 1, null);
        assertEquals(1, page1.items().size());
        assertTrue(page1.nextCursor().isPresent());

        SearchSpellsResult page2 = service.search("a", null, 10, page1.nextCursor().orElseThrow());
        assertFalse(page2.items().isEmpty());
    }

    private static List<Spell> sampleSpells() {
        return List.of(
                ImmutableSpell.builder()
                        .id("1")
                        .name("Daze")
                        .level(1)
                        .description("Mental damage")
                        .kind(SpellKind.SPELL)
                        .build(),
                ImmutableSpell.builder()
                        .id("2")
                        .name("Dancing Lights")
                        .level(1)
                        .description("Create lights")
                        .kind(SpellKind.SPELL)
                        .build(),
                ImmutableSpell.builder()
                        .id("3")
                        .name("Extend Spell")
                        .level(1)
                        .description("Metamagic focus")
                        .kind(SpellKind.FOCUS)
                        .build()
        );
    }

    private record InMemoryRepo(List<Spell> spells) implements SpellRepository {
        @Override
        public Optional<Spell> findByName(String name) {
            if (name == null) {
                return Optional.empty();
            }
            return spells.stream()
                    .filter(spell -> spell.name().equalsIgnoreCase(name))
                    .findFirst();
        }

        @Override
        public List<Spell> findAll() {
            return spells;
        }
    }
}
