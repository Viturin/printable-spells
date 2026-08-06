package io.github.viturin.spellcards.application.service;

import io.github.viturin.spellcards.application.port.out.SpellCardJobPublisher;
import io.github.viturin.spellcards.application.port.out.SpellCardJobStore;
import io.github.viturin.spellcards.queue.model.SpellCardJobMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class QueueSpellCardJobServiceTest {

    @Test
    void returnsUuidAndPublishesNormalizedMessage() {
        SpellCardJobPublisher publisher = mock(SpellCardJobPublisher.class);
        SpellCardJobStore jobStore = mock(SpellCardJobStore.class);
        QueueSpellCardJobService service = new QueueSpellCardJobService(publisher, jobStore);

        UUID jobId = service.submit(List.of(" Daze ", "Magic Missile", "Daze", "  "));

        verify(jobStore).createQueued(jobId, List.of("Daze", "Magic Missile"));
        ArgumentCaptor<SpellCardJobMessage> messageCaptor = ArgumentCaptor.forClass(SpellCardJobMessage.class);
        verify(publisher).publish(messageCaptor.capture());

        SpellCardJobMessage message = messageCaptor.getValue();
        assertEquals(jobId, message.jobId());
        assertEquals(List.of("Daze", "Magic Missile"), message.spellNames());
    }

    @Test
    void throwsForEmptyPayloadAndDoesNotPublish() {
        SpellCardJobPublisher publisher = mock(SpellCardJobPublisher.class);
        SpellCardJobStore jobStore = mock(SpellCardJobStore.class);
        QueueSpellCardJobService service = new QueueSpellCardJobService(publisher, jobStore);

        assertThrows(IllegalArgumentException.class, () -> service.submit(List.of("  ", "")));
        verify(jobStore, never()).createQueued(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(publisher, never()).publish(org.mockito.ArgumentMatchers.any());
    }
}
