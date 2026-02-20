package io.github.viturin.spellcards.adapters.in.rest;

import io.github.viturin.spellcards.application.port.in.SearchSpellsResult;
import io.github.viturin.spellcards.application.port.in.SearchSpellsResultItem;
import io.github.viturin.spellcards.application.port.in.SpellSearchService;
import io.github.viturin.spellcards.domain.model.ImmutableSpell;
import io.github.viturin.spellcards.domain.model.Spell;
import io.github.viturin.spellcards.generated.model.SpellKind;
import io.github.viturin.spellcards.generated.model.SpellSearchResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpellsRestControllerTest {

    @Test
    void mapsSearchResultToApiResponse() {
        Spell spell = ImmutableSpell.builder()
                .id("spell-1")
                .name("Daze")
                .level(1)
                .description("mental")
                .rarity("common")
                .build();

        SpellSearchService searchSpellsService = mock(SpellSearchService.class);
        when(searchSpellsService.search(anyString(), any(), anyInt(), any()))
                .thenReturn(new SearchSpellsResult(
                        List.of(new SearchSpellsResultItem(spell, 0.99d)),
                        Optional.of("Mg")
                ));

        SpellsRestController controller = new SpellsRestController(searchSpellsService);

        ResponseEntity<SpellSearchResponse> response = controller.searchSpells("daze", SpellKind.SPELL, 20, null);

        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getItems().size());
        assertEquals("Daze", response.getBody().getItems().getFirst().getName());
        assertTrue(response.getBody().getNextCursor().isPresent());
        assertEquals("Mg", response.getBody().getNextCursor().get());
    }
}
