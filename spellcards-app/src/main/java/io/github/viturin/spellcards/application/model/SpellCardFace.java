package io.github.viturin.spellcards.application.model;

import io.github.viturin.spellcards.domain.model.Spell;
import io.github.viturin.spellcards.domain.model.SpellKind;
import io.github.viturin.spellcards.domain.model.SpellDice;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

public record SpellCardFace(String classification, Optional<ActionBadge> badge, List<Rule> rules) {
    public static final String DURATION_SYMBOL = "◷";
    public static final String DICE_HEADER = "Dice needed";
    private static final Pattern DIE = Pattern.compile("(?i)d(\\d{1,3})\\b");
    private static final Pattern VARIABLE_ACTIONS = Pattern.compile("([1-3]) (to|or) ([1-3])");

    public static SpellCardFace from(Spell spell) {
        var badge = actionBadge(spell.actionCost());
        List<Rule> rules = new ArrayList<>();
        var casting = spell.castingDetails();
        if (badge.isEmpty()) {
            addRule(rules, "Cast", spell.actionCost());
        }
        addRule(rules, "Trigger", casting.trigger());
        addRule(rules, "Requirements", casting.requirements());
        addRule(rules, "Cost", casting.cost());
        addRule(rules, "Range", spell.range());
        addRule(rules, "Area", casting.area());
        addRule(rules, "Targets", spell.target());
        addRule(rules, "Defense", casting.defense());
        String duration = spell.duration();
        if (spell.sustained()) {
            duration = duration.isBlank() ? "Sustained" : duration + " (sustained)";
        }
        addRule(rules, "Duration", duration);
        addRule(rules, "Primary Check", casting.primaryCheck());
        addRule(rules, "Secondary Checks", casting.secondaryChecks());
        addRule(rules, "Secondary Casters", casting.secondaryCasters());
        addRule(rules, "Traditions", String.join(", ", spell.traditions()));
        addRule(rules, "Traits", String.join(", ", spell.traits().stream().filter(trait -> !trait.equals("cantrip")).toList()));

        var dice = spell.dice();
        addRule(rules, DICE_HEADER, diceHeader(dice));
        addRule(rules, "Caster attack", dice.attack());
        addRule(rules, "Target save", dice.save());
        addRule(rules, "Effect dice", dice.effect());
        addRule(rules, "Heightening", dice.heightening());

        String kind = spell.kind().name();
        if (spell.traits().contains("cantrip")) {
            kind = spell.kind() == SpellKind.FOCUS ? "FOCUS CANTRIP" : "CANTRIP";
        }
        String classification = kind + "  ·  RANK " + spell.level() + "  ·  " + spell.rarity().toUpperCase(Locale.ROOT);
        return new SpellCardFace(classification, badge, List.copyOf(rules));
    }

    private static String diceHeader(SpellDice dice) {
        var sides = new TreeSet<Integer>();
        for (String roll : List.of(dice.attack(), dice.save(), dice.effect(), dice.heightening())) {
            var matcher = DIE.matcher(roll);
            while (matcher.find()) {
                sides.add(Integer.parseInt(matcher.group(1)));
            }
        }
        return sides.stream().map(count -> dieSymbol(count) + " d" + count).collect(Collectors.joining("   "));
    }

    private static String dieSymbol(int sides) {
        return switch (sides) {
            case 4 -> "△";
            case 6 -> "□";
            case 8 -> "◇";
            case 10 -> "⬟";
            default -> "⬡";
        };
    }

    private static void addRule(List<Rule> rules, String label, String value) {
        if (!value.isBlank()) {
            rules.add(new Rule(label, value, ruleSymbol(label)));
        }
    }

    private static String ruleSymbol(String label) {
        return switch (label) {
            case "Range" -> "↔";
            case "Area" -> "▦";
            case "Targets" -> "◎";
            case "Duration" -> DURATION_SYMBOL;
            case "Defense" -> "⬟";
            case "Traditions" -> "✦";
            default -> "";
        };
    }

    private static Optional<ActionBadge> actionBadge(String actionCost) {
        String action = actionCost.strip().toLowerCase(Locale.ROOT);
        return switch (action) {
            case "1", "2", "3" -> Optional.of(new ActionBadge("◆".repeat(Integer.parseInt(action)), "CAST"));
            case "free", "free action" -> Optional.of(new ActionBadge("◇", "FREE"));
            case "reaction" -> Optional.of(new ActionBadge("↶", "REACTION"));
            default -> {
                var range = VARIABLE_ACTIONS.matcher(action);
                if (range.matches()) {
                    String joiner = range.group(2).equals("or") ? "/" : "–";
                    yield Optional.of(new ActionBadge("◆".repeat(Integer.parseInt(range.group(1))) + joiner
                            + "◆".repeat(Integer.parseInt(range.group(3))), "CAST"));
                }
                yield Optional.empty();
            }
        };
    }

    public record Rule(String label, String value, String symbol) {
    }

    public record ActionBadge(String symbols, String caption) {
    }
}
