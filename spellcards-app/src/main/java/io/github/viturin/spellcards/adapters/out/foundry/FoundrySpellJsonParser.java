package io.github.viturin.spellcards.adapters.out.foundry;

import org.jsoup.Jsoup;
import org.jsoup.nodes.TextNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.viturin.spellcards.domain.model.ImmutableSpell;
import io.github.viturin.spellcards.domain.model.Spell;
import io.github.viturin.spellcards.domain.model.SpellKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FoundrySpellJsonParser {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Optional<Spell> parseSpell(String jsonText) {
        return parseSpell(jsonText, "");
    }

    public Optional<Spell> parseSpell(String jsonText, String sourcePath) {
        try {
            JsonNode root = objectMapper.readTree(jsonText);
            if (root == null || !"spell".equalsIgnoreCase(root.path("type").asText())) {
                return Optional.empty();
            }

            JsonNode system = root.path("system");
            String id = root.path("_id").asText("");
            String name = root.path("name").asText("");
            int level = system.path("level").path("value").asInt(-1);
            JsonNode descriptionNode = system.path("description");
            String descriptionValue = descriptionNode.path("value").asText("");
            String descriptionGm = descriptionNode.path("gm").asText("");
            String descriptionSource = descriptionValue.isBlank() ? descriptionGm : descriptionValue;
            String description = htmlToPlain(descriptionSource);

            if (id.isBlank() || name.isBlank() || level < 0 || description.isBlank()) {
                return Optional.empty();
            }

            String actionCost = system.path("time").path("value").asText("");
            String range = system.path("range").path("value").asText("");
            String target = system.path("target").path("value").asText("");
            String duration = system.path("duration").path("value").asText("");
            boolean sustained = system.path("duration").path("sustained").asBoolean(false);
            String rarity = system.path("traits").path("rarity").asText("common");
            List<String> traditions = readStringList(system.path("traits").path("traditions"));
            List<String> traits = readStringList(system.path("traits").path("value"));

            return Optional.of(ImmutableSpell.builder()
                    .id(id)
                    .name(name)
                    .level(level)
                    .description(description)
                    .kind(inferKind(system, sourcePath))
                    .actionCost(actionCost)
                    .range(range)
                    .target(target)
                    .duration(duration)
                    .sustained(sustained)
                    .rarity(rarity)
                    .addAllTraditions(traditions)
                    .addAllTraits(traits)
                    .build());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private SpellKind inferKind(JsonNode system, String sourcePath) {
        String category = system.path("category").path("value").asText(system.path("category").asText(""));
        if ("focus".equalsIgnoreCase(category)) {
            return SpellKind.FOCUS;
        }
        if ("ritual".equalsIgnoreCase(category)) {
            return SpellKind.RITUAL;
        }

        List<String> traits = readStringList(system.path("traits").path("value"));
        if (traits.stream().anyMatch("focus"::equalsIgnoreCase)) {
            return SpellKind.FOCUS;
        }
        if (traits.stream().anyMatch("ritual"::equalsIgnoreCase)) {
            return SpellKind.RITUAL;
        }

        if (sourcePath == null) {
            return SpellKind.SPELL;
        }
        if (sourcePath.contains("/focus__")) {
            return SpellKind.FOCUS;
        }
        if (sourcePath.contains("/rituals__")) {
            return SpellKind.RITUAL;
        }
        return SpellKind.SPELL;
    }

    private List<String> readStringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return values;
        }
        for (JsonNode item : node) {
            String value = item.asText("");
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    private String htmlToPlain(String html) {
        var body = Jsoup.parseBodyFragment(html.replaceAll("\\R", " ")).body();
        body.select("li").forEach(item -> item.prependText("• "));
        body.select("br, hr, p, li, h1, h2, h3, h4, h5, h6")
                .forEach(element -> element.after(new TextNode("\n")));
        return body.wholeText()
                .replaceAll("@\\w+\\[[^\\]\\n]*]\\{([^}\\n]+)}", "$1")
                .replaceAll("@Template\\[(?:type:)?(burst|cone|emanation|line)\\|distance:(\\d+)]",
                        "$2-foot $1")
                .replaceAll("(?U)[^\\S\\n]+", " ").replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n").strip();
    }
}
