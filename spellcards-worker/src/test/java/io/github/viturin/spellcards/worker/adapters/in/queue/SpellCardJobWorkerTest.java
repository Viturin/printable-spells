package io.github.viturin.spellcards.worker.adapters.in.queue;

import io.github.viturin.spellcards.application.port.in.SpellCardGenerationService;
import io.github.viturin.spellcards.queue.model.SpellCardJobMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SpellCardJobWorkerTest {

    @Test
    void delegatesMessageToGenerationService() {
        SpellCardGenerationService generationService = mock(SpellCardGenerationService.class);
        SpellCardJobWorker worker = new SpellCardJobWorker(generationService);
        SpellCardJobMessage message = new SpellCardJobMessage(
                UUID.fromString("4f76e5c5-42ea-47ef-b2d1-5ce3dd7f2dbf"),
                List.of("Daze", "Magic Missile")
        );

        worker.handle(message);

        verify(generationService).generateForSpells(List.of("Daze", "Magic Missile"));
    }
}
