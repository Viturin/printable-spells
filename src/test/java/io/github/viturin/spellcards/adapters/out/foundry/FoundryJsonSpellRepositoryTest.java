package io.github.viturin.spellcards.adapters.out.foundry;

import io.github.viturin.spellcards.domain.model.Spell;
import io.github.viturin.spellcards.domain.model.SpellKind;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FoundryJsonSpellRepositoryTest {

    @Test
    void findsSpellByNameCaseInsensitive() {
        FoundryJsonSpellRepository catalog = new FoundryJsonSpellRepository();

        Optional<Spell> daze = catalog.findByName("dAzE");

        assertTrue(daze.isPresent());
        assertEquals("Daze", daze.get().name());
        assertEquals(1, daze.get().level());
        assertEquals(SpellKind.SPELL, daze.get().kind());
    }

    @Test
    void loadsFocusAndRitualKinds() {
        FoundryJsonSpellRepository catalog = new FoundryJsonSpellRepository();

        Spell focus = catalog.findByName("Extend Spell").orElseThrow();
        Spell ritual = catalog.findByName("Sample Ritual").orElseThrow();

        assertEquals(SpellKind.FOCUS, focus.kind());
        assertEquals(SpellKind.RITUAL, ritual.kind());
    }

    @Test
    void returnsEmptyWhenSpellDoesNotExist() {
        FoundryJsonSpellRepository catalog = new FoundryJsonSpellRepository();

        assertTrue(catalog.findByName("Does Not Exist").isEmpty());
        assertTrue(catalog.findByName(null).isEmpty());
    }
}
