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
                    "description": { "value": "<p>Cloud the target's mind in a @Template[type:cone|distance:30].</p><hr><p>@UUID[example]{Stunned &amp; dazed}.</p>" },
                    "time": { "value": "2" },
                    "range": { "value": "60 feet" },
                    "area": { "value": 20, "type": "burst" },
                    "defense": { "save": { "statistic": "will", "basic": true } },
                    "target": { "value": "1 creature" },
                    "requirements": "A free hand.",
                    "cost": { "value": "1 gp" },
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
        assertEquals("Cloud the target's mind in a 30-foot cone.\n\nStunned & dazed.", spell.description());
        assertEquals("2", spell.actionCost());
        assertEquals("60 feet", spell.range());
        assertEquals("20-foot burst", spell.castingDetails().area());
        assertEquals("Basic Will", spell.castingDetails().defense());
        assertEquals("A free hand.", spell.castingDetails().requirements());
        assertEquals("1 gp", spell.castingDetails().cost());
        assertEquals("1 creature", spell.target());
        assertEquals(SpellKind.SPELL, spell.kind());
        assertEquals("common", spell.rarity());
        assertEquals(2, spell.traditions().size());
        assertEquals(2, spell.traits().size());
    }

    @Test
    void readsPassiveDefenseAndRitualChecks() {
        var spell = parser.parseSpell("""
                {
                  "_id": "r123", "name": "Example Ritual", "type": "spell",
                  "system": {
                    "level": {"value": 3}, "description": {"value": "An effect."},
                    "defense": {"passive": {"statistic": "fortitude-dc"}, "save": null},
                    "area": {"value": 20, "type": "cylinder", "details": "20-foot radius, 40 feet high"},
                    "ritual": {"primary": {"check": "Arcana (expert)"},
                               "secondary": {"casters": 2, "checks": "Nature"}}
                  }
                }
                """).orElseThrow();

        assertEquals(SpellKind.RITUAL, spell.kind());
        assertEquals("Fortitude DC", spell.castingDetails().defense());
        assertEquals("20-foot radius, 40 feet high", spell.castingDetails().area());
        assertEquals("Arcana (expert)", spell.castingDetails().primaryCheck());
        assertEquals("Nature", spell.castingDetails().secondaryChecks());
        assertEquals("2", spell.castingDetails().secondaryCasters());
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
