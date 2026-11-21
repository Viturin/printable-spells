package io.github.viturin.spellcards.adapters.in.rest;

import io.github.viturin.spellcards.application.port.in.SearchSpellsResult;
import io.github.viturin.spellcards.application.port.in.SearchSpellsResultItem;
import io.github.viturin.spellcards.application.port.in.SpellNameResolution;
import io.github.viturin.spellcards.application.port.in.SpellSearchService;
import io.github.viturin.spellcards.domain.model.ImmutableSpell;
import io.github.viturin.spellcards.domain.model.Spell;
import io.github.viturin.spellcards.domain.model.SpellCastingDetails;
import io.github.viturin.spellcards.generated.model.SpellKind;
import io.github.viturin.spellcards.generated.model.SpellSearchResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SpellsRestControllerTest {

    @Test
    void returnsSpellSummariesAndUnmatchedNamesForBulkImport() throws Exception {
        Spell spell = ImmutableSpell.builder().id("spell-1").name("Daze").level(1).description("Mental damage.")
                .actionCost("2").castingDetails(new SpellCastingDetails("", "Basic Will", "", "", "", "", "", ""))
                .build();
        SpellSearchService service = mock(SpellSearchService.class);
        when(service.resolveNames(List.of("daze", "Unknown"))).thenReturn(new SpellNameResolution(
                List.of(spell), List.of("Unknown")
        ));
        var mvc = MockMvcBuilders.standaloneSetup(new SpellsRestController(service))
                .setControllerAdvice(new RestExceptionHandler()).build();

        mvc.perform(post("/api/v1/spells/resolve").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"names":["daze","Unknown"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("spell-1"))
                .andExpect(jsonPath("$.items[0].name").value("Daze"))
                .andExpect(jsonPath("$.items[0].description").value("Mental damage."))
                .andExpect(jsonPath("$.items[0].cardPreview.classification").value("SPELL  ·  RANK 1  ·  COMMON"))
                .andExpect(jsonPath("$.items[0].cardPreview.action.symbols").value("◆◆"))
                .andExpect(jsonPath("$.items[0].cardPreview.rules[0].label").value("Defense"))
                .andExpect(jsonPath("$.items[0].cardPreview.rules[0].value").value("Basic Will"))
                .andExpect(jsonPath("$.unmatchedNames[0]").value("Unknown"));
    }

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
