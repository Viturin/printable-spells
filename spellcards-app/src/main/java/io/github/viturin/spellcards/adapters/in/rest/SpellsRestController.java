package io.github.viturin.spellcards.adapters.in.rest;

import io.github.viturin.spellcards.application.port.in.SearchSpellsResult;
import io.github.viturin.spellcards.application.port.in.SearchSpellsResultItem;
import io.github.viturin.spellcards.application.port.in.SpellSearchService;
import io.github.viturin.spellcards.generated.api.SpellsApi;
import io.github.viturin.spellcards.generated.model.SpellKind;
import io.github.viturin.spellcards.generated.model.SpellRarity;
import io.github.viturin.spellcards.generated.model.SpellSearchResponse;
import io.github.viturin.spellcards.generated.model.SpellSummary;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SpellsRestController implements SpellsApi {
    private final SpellSearchService searchSpellsService;

    public SpellsRestController(SpellSearchService searchSpellsService) {
        this.searchSpellsService = searchSpellsService;
    }

    @Override
    public ResponseEntity<SpellSearchResponse> searchSpells(String q, SpellKind kind, Integer limit, String cursor) {
        SearchSpellsResult result = searchSpellsService.search(
                q,
                mapDomainKind(kind),
                limit == null ? 20 : limit,
                cursor
        );

        SpellSearchResponse response = new SpellSearchResponse(
                result.items().stream().map(this::toSummary).toList()
        );
        result.nextCursor().ifPresent(response::nextCursor);
        return ResponseEntity.ok(response);
    }

    private SpellSummary toSummary(SearchSpellsResultItem item) {
        SpellSummary summary = new SpellSummary(
                item.spell().id(),
                item.spell().name(),
                item.spell().level(),
                SpellKind.fromValue(item.spell().kind().name()),
                mapRarity(item.spell().rarity()),
                item.spell().traditions(),
                item.spell().traits(),
                item.score()
        );
        summary.setActionCost(item.spell().actionCost());
        summary.setRange(item.spell().range());
        summary.setTarget(item.spell().target());
        summary.setDescription(item.spell().description());
        return summary;
    }

    private io.github.viturin.spellcards.domain.model.SpellKind mapDomainKind(SpellKind kind) {
        if (kind == null) {
            return null;
        }
        return io.github.viturin.spellcards.domain.model.SpellKind.valueOf(kind.getValue());
    }

    private SpellRarity mapRarity(String rarity) {
        try {
            return SpellRarity.fromValue(rarity == null ? "common" : rarity);
        } catch (IllegalArgumentException ignored) {
            return SpellRarity.COMMON;
        }
    }
}
