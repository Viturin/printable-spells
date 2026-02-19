package io.github.viturin.spellcards.adapters.out.foundry;

import io.github.viturin.spellcards.domain.model.Spell;
import io.github.viturin.spellcards.domain.model.SpellKind;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FoundrySpellJsonParserTest {
    private final FoundrySpellJsonParser parser = new FoundrySpellJsonParser();

    @Test
    void parsesSpellAndNormalizesDescription() {
        String json = """
                {
                  "_id": "abc123",
                  "name": "Daze",
                  "type": "spell",
                  "system": {
                    "level": { "value": 1 },
                    "description": { "value": "<p>Cloud the target's mind.</p>" },
                    "time": { "value": "2" },
                    "range": { "value": "60 feet" },
                    "target": { "value": "1 creature" },
                    "duration": { "value": "", "sustained": false },
                    "traits": {
                      "rarity": "common",
                      "traditions": ["arcane", "occult"],
                      "value": ["cantrip", "mental"]
                    }
                  }
                }
                """;

        Optional<Spell> parsed = parser.parseSpell(json, "foundry/spells/spells__daze.json");

        assertTrue(parsed.isPresent());
        Spell spell = parsed.get();
        assertEquals("abc123", spell.id());
        assertEquals("Daze", spell.name());
        assertEquals(1, spell.level());
        assertEquals("Cloud the target's mind.", spell.description());
        assertEquals("2", spell.actionCost());
        assertEquals("60 feet", spell.range());
        assertEquals("1 creature", spell.target());
        assertEquals(SpellKind.SPELL, spell.kind());
        assertEquals("common", spell.rarity());
        assertEquals(2, spell.traditions().size());
        assertEquals(2, spell.traits().size());
    }

    @Test
    void infersFocusAndRitualKindsFromSourcePath() {
        String base = """
                {
                  "_id": "id",
                  "name": "Any",
                  "type": "spell",
                  "system": {
                    "level": { "value": 3 },
                    "description": { "value": "<p>Any</p>" },
                    "time": { "value": "1" },
                    "range": { "value": "" },
                    "target": { "value": "" },
                    "duration": { "value": "", "sustained": false },
                    "traits": { "rarity": "common", "traditions": [], "value": [] }
                  }
                }
                """;

        Spell focus = parser.parseSpell(base, "foundry/spells/focus__x.json").orElseThrow();
        Spell ritual = parser.parseSpell(base, "foundry/spells/rituals__x.json").orElseThrow();

        assertEquals(SpellKind.FOCUS, focus.kind());
        assertEquals(SpellKind.RITUAL, ritual.kind());
    }

    @Test
    void returnsEmptyForInvalidOrNonSpellJson() {
        String nonSpell = """
                {
                  "_id": "abc",
                  "name": "Not a spell",
                  "type": "equipment",
                  "system": {}
                }
                """;

        String missingRequired = """
                {
                  "name": "Broken",
                  "type": "spell",
                  "system": {
                    "level": { "value": -1 },
                    "description": { "value": "" }
                  }
                }
                """;

        assertTrue(parser.parseSpell(nonSpell).isEmpty());
        assertTrue(parser.parseSpell(missingRequired).isEmpty());
        assertTrue(parser.parseSpell("not-json").isEmpty());
    }
}
