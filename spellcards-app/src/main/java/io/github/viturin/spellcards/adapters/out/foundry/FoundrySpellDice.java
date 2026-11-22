package io.github.viturin.spellcards.adapters.out.foundry;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.viturin.spellcards.domain.model.SpellDice;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class FoundrySpellDice {
    // The attack trait alone also covers spells that call for a Strike or skill check.
    private static final Pattern SPELL_ATTACK = Pattern.compile(
            "(?i)\\b(?:make|attempt|roll) (?:an? |(?:your|another) )?(?:(?:melee|ranged) )?spell attack\\b");
    private static final Pattern ALTERNATE_DEFENSE = Pattern.compile(
            "(?i)spell attack(?: rolls?)? against [^.]{0,45}?(Fortitude|Reflex|Will) DC");

    private FoundrySpellDice() {
    }

    static SpellDice read(JsonNode system, String description, String name) {
        String attack = attackRoll(description);

        // These spell variants are defined in prose and cannot be inferred from the damage entries.
        return switch (name) {
            case "Ignition" -> new SpellDice(attack,
                    "Fire on hit: ranged 2d4 / melee 2d6. Crit: double damage + 1d4 / 1d6 persistent fire.",
                    "+1 rank: +1 die to initial and persistent damage");
            case "Force Barrage" -> new SpellDice("",
                    "Auto-hit: 1d4+1 force per shard, 1 shard per action",
                    "+2 ranks: +1 shard per action");
            case "Heal" -> new SpellDice("",
                    "1d8 HP (living) or vitality damage (undead). 2 actions: +8 HP to living target.",
                    "+1 rank: +1d8 and +8 HP to the 2-action bonus");
            default -> new SpellDice(attack,
                    baseEffects(system.path("damage")), intervalEffects(system));
        };
    }

    private static String attackRoll(String description) {
        if (!SPELL_ATTACK.matcher(description).find()) {
            return "";
        }
        var alternate = ALTERNATE_DEFENSE.matcher(description);
        String defense = alternate.find() ? alternate.group(1) + " DC" : "AC";
        return "d20 + spell attack modifier vs " + defense;
    }

    private static String baseEffects(JsonNode damage) {
        List<String> formulas = new ArrayList<>();
        for (var entry : damage) {
            String formula = entry.path("formula").asText("");
            if (formula.isBlank()) {
                continue;
            }
            String type = entry.path("type").asText("");
            String category = entry.path("category").asText("");
            boolean healing = entry.path("kinds").valueStream().anyMatch(kind -> kind.asText().equals("healing"));
            boolean damageKind = entry.path("kinds").valueStream().anyMatch(kind -> kind.asText().equals("damage"));
            String label = healing && !damageKind ? "healing" : type + (healing ? " damage / healing" : "");
            formulas.add(FoundrySpellJsonParser.formulaText(formula)
                    + (entry.path("applyMod").asBoolean() ? " + spellcasting modifier" : "")
                    + (category.isBlank() ? "" : " " + category)
                    + (label.isBlank() ? "" : " " + label));
        }
        return String.join("; ", formulas);
    }

    private static String intervalEffects(JsonNode system) {
        var heightening = system.path("heightening");
        int interval = heightening.path("interval").asInt();
        if (!heightening.path("type").asText().equals("interval") || interval <= 0) {
            return "";
        }
        var damage = system.path("damage");
        List<String> increases = new ArrayList<>();
        heightening.path("damage").properties().forEach(increase -> {
            var entry = damage.path(increase.getKey());
            String formula = increase.getValue().asText();
            if (!entry.isMissingNode() && !formula.isBlank() && !formula.equals("0")) {
                increases.add("+" + FoundrySpellJsonParser.formulaText(formula)
                        + " " + entry.path("type").asText());
            }
        });
        return increases.isEmpty() ? "" : "+" + interval + (interval == 1 ? " rank: " : " ranks: ")
                + String.join("; ", increases);
    }
}
