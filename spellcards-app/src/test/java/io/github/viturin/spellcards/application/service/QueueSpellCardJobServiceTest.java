package io.github.viturin.spellcards.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.viturin.spellcards.application.model.SpellCardPdfLayout;
import io.github.viturin.spellcards.adapters.out.filesystem.FileSpellCardJobStore;
import io.github.viturin.spellcards.application.model.SpellCardJobStatus;
import io.github.viturin.spellcards.application.port.out.SpellCardJobPublicationException;
import io.github.viturin.spellcards.application.port.out.SpellCardJobPublisher;
import io.github.viturin.spellcards.queue.model.SpellCardJobMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class QueueSpellCardJobServiceTest {
    @TempDir
    Path artifactDirectory;

    @Test
    void persistsNormalizedJobBeforePublishing() {
        FileSpellCardJobStore jobStore = new FileSpellCardJobStore(artifactDirectory, 24);
        SpellCardJobPublisher publisher = mock(SpellCardJobPublisher.class);
        QueueSpellCardJobService service = new QueueSpellCardJobService(publisher, jobStore);
        doAnswer(invocation -> {
            SpellCardJobMessage message = invocation.getArgument(0);
            var stored = new FileSpellCardJobStore(artifactDirectory, 24).find(message.jobId()).orElseThrow();
            assertEquals(SpellCardJobStatus.QUEUED, stored.status());
            assertEquals(message.spellNames(), stored.spellNames());
            assertEquals(SpellCardPdfLayout.PLAYING_CARD, stored.layout());
            return null;
        }).when(publisher).publish(any());

        UUID jobId = service.submit(List.of(" Daze ", "Magic Missile", "Daze", "  "), SpellCardPdfLayout.PLAYING_CARD);

        ArgumentCaptor<SpellCardJobMessage> messageCaptor = ArgumentCaptor.forClass(SpellCardJobMessage.class);
        verify(publisher).publish(messageCaptor.capture());

        SpellCardJobMessage message = messageCaptor.getValue();
        assertEquals(jobId, message.jobId());
        assertEquals(List.of("Daze", "Magic Missile"), message.spellNames());
    }

    @Test
    void readsOlderManifestsThatDoNotContainALayout() throws Exception {
        var store = new FileSpellCardJobStore(artifactDirectory, 24);
        UUID id = UUID.randomUUID();
        store.createQueued(id, List.of("Daze"), SpellCardPdfLayout.A4_SHEET);
        Path manifestFile = artifactDirectory.resolve(id.toString()).resolve("job.json");
        var mapper = new ObjectMapper();
        var manifest = (ObjectNode) mapper.readTree(Files.readString(manifestFile));
        manifest.remove("layout");
        Files.writeString(manifestFile, mapper.writeValueAsString(manifest));

        assertEquals(SpellCardPdfLayout.A4_SHEET, store.find(id).orElseThrow().layout());
    }

    @Test
    void throwsForEmptyPayloadAndDoesNotPublish() {
        SpellCardJobPublisher publisher = mock(SpellCardJobPublisher.class);
        FileSpellCardJobStore jobStore = new FileSpellCardJobStore(artifactDirectory, 24);
        QueueSpellCardJobService service = new QueueSpellCardJobService(publisher, jobStore);

        assertThrows(IllegalArgumentException.class, () -> service.submit(List.of("  ", ""), SpellCardPdfLayout.A4_SHEET));
        verify(publisher, never()).publish(any());
    }

    @Test
    void definiteRejectionMarksThePersistedJobFailed() {
        verifyFailedPublication(true, SpellCardJobStatus.FAILED);
    }

    @Test
    void uncertainPublicationLeavesJobAvailableToWorker() {
        verifyFailedPublication(false, SpellCardJobStatus.QUEUED);
    }

    private void verifyFailedPublication(boolean rejected, SpellCardJobStatus expectedStatus) {
        FileSpellCardJobStore jobStore = new FileSpellCardJobStore(artifactDirectory, 24);
        SpellCardJobPublisher publisher = mock(SpellCardJobPublisher.class);
        doThrow(new SpellCardJobPublicationException(rejected, null)).when(publisher).publish(any());
        QueueSpellCardJobService service = new QueueSpellCardJobService(publisher, jobStore);
        assertThrows(SpellCardJobPublicationException.class, () -> service.submit(List.of("Daze"), SpellCardPdfLayout.A4_SHEET));

        ArgumentCaptor<SpellCardJobMessage> captor = ArgumentCaptor.forClass(SpellCardJobMessage.class);
        verify(publisher).publish(captor.capture());
        assertEquals(expectedStatus, jobStore.find(captor.getValue().jobId()).orElseThrow().status());
    }
}
