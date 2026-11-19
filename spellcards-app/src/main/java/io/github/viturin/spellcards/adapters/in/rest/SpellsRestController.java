package io.github.viturin.spellcards.adapters.in.rest;

import io.github.viturin.spellcards.application.port.in.SearchSpellsResult;
import io.github.viturin.spellcards.application.port.in.SpellSearchService;
import io.github.viturin.spellcards.domain.model.Spell;
import io.github.viturin.spellcards.generated.api.SpellsApi;
import io.github.viturin.spellcards.generated.model.ResolveSpellNamesRequest;
import io.github.viturin.spellcards.generated.model.ResolveSpellNamesResponse;
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
                result.items().stream().map(item -> toSummary(item.spell(), item.score())).toList()
        );
        result.nextCursor().ifPresent(response::nextCursor);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<ResolveSpellNamesResponse> resolveSpellNames(ResolveSpellNamesRequest request) {
        var result = searchSpellsService.resolveNames(request.getNames());
        return ResponseEntity.ok(new ResolveSpellNamesResponse(
                result.matches().stream().map(spell -> toSummary(spell, 1)).toList(),
                result.unmatchedNames()
        ));
    }

    private SpellSummary toSummary(Spell spell, double score) {
        SpellSummary summary = new SpellSummary(
                spell.id(),
                spell.name(),
                spell.level(),
                SpellKind.fromValue(spell.kind().name()),
                mapRarity(spell.rarity()),
                spell.traditions(),
                spell.traits(),
                score
        );
        summary.setActionCost(spell.actionCost());
        summary.setRange(spell.range());
        summary.setTarget(spell.target());
        summary.setDescription(spell.description());
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
