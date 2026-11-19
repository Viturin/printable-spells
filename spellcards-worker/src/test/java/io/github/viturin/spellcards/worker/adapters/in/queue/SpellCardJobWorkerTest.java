package io.github.viturin.spellcards.worker.adapters.in.queue;

import io.github.viturin.spellcards.adapters.out.filesystem.FileSpellCardJobStore;
import io.github.viturin.spellcards.application.model.SpellCardJobStatus;
import io.github.viturin.spellcards.domain.model.ImmutableSpell;
import io.github.viturin.spellcards.domain.port.SpellRepository;
import io.github.viturin.spellcards.queue.model.SpellCardJobMessage;
import io.github.viturin.spellcards.worker.pdf.SpellCardPdfRenderer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpellCardJobWorkerTest {
    @TempDir
    Path artifactDirectory;

    @Test
    void resumesProcessingAndPublishesOneReadablePdfInSelectionOrder() throws Exception {
        var store = new FileSpellCardJobStore(artifactDirectory, 24);
        var repository = mock(SpellRepository.class);
        var worker = new SpellCardJobWorker(store, repository, new SpellCardPdfRenderer(), 7);
        var job = new SpellCardJobMessage(UUID.randomUUID(), List.of("Daze", "Éclat"));
        store.createQueued(job.jobId(), job.spellNames());
        store.markProcessing(job.jobId());
        when(repository.findByName("Daze")).thenReturn(Optional.of(spell("Daze", "Cloud the target’s mind.")));
        when(repository.findByName("Éclat")).thenReturn(Optional.of(spell("Éclat", "A bright flash.")));

        worker.handle(job);
        worker.handle(job);

        var completed = store.find(job.jobId()).orElseThrow();
        assertEquals(SpellCardJobStatus.COMPLETED, completed.status());
        assertEquals(Duration.ofHours(24), Duration.between(completed.updatedAt(), completed.expiresAt()));
        verify(repository, times(1)).findByName("Daze");
        try (var document = Loader.loadPDF(pdfPath(job.jobId()).toFile())) {
            assertEquals(2, document.getNumberOfPages());
            var text = new PDFTextStripper();
            text.setEndPage(1);
            assertTrue(text.getText(document).contains("Daze"));
            assertTrue(text.getText(document).contains("target’s mind"));
            text.setStartPage(2);
            text.setEndPage(2);
            assertTrue(text.getText(document).contains("Éclat"));
        }
    }

    @Test
    void wrapsLongDescriptionsOntoContinuationPages() throws Exception {
        var store = new FileSpellCardJobStore(artifactDirectory, 24);
        var repository = mock(SpellRepository.class);
        var worker = new SpellCardJobWorker(store, repository, new SpellCardPdfRenderer(), 7);
        var job = new SpellCardJobMessage(UUID.randomUUID(), List.of("Long spell"));
        store.createQueued(job.jobId(), job.spellNames());
        when(repository.findByName("Long spell")).thenReturn(Optional.of(spell(
                "Long spell", "A description that wraps onto a continuation page. ".repeat(150) + "Final words."
        )));

        worker.handle(job);

        try (var document = Loader.loadPDF(pdfPath(job.jobId()).toFile())) {
            assertTrue(document.getNumberOfPages() > 1);
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Long spell (continued)"));
            assertTrue(text.replaceAll("\\s+", " ").contains("Final words."));
        }
    }

    @Test
    void missingSpellFailsTheWholeJobWithoutCreatingAnArtifact() {
        var store = new FileSpellCardJobStore(artifactDirectory, 24);
        var repository = mock(SpellRepository.class);
        var worker = new SpellCardJobWorker(store, repository, new SpellCardPdfRenderer(), 7);
        var job = new SpellCardJobMessage(UUID.randomUUID(), List.of("Missing"));
        store.createQueued(job.jobId(), job.spellNames());
        when(repository.findByName("Missing")).thenReturn(Optional.empty());

        worker.handle(job);

        var failed = store.find(job.jobId()).orElseThrow();
        assertEquals(SpellCardJobStatus.FAILED, failed.status());
        assertEquals("Spell not found: Missing", failed.errorMessage());
        assertFalse(Files.exists(pdfPath(job.jobId())));
    }

    @Test
    void removesExpiredPdfBeforeDiscardingItsManifest() throws Exception {
        var store = new FileSpellCardJobStore(artifactDirectory, 0);
        var id = UUID.randomUUID();
        store.createQueued(id, List.of("Daze"));
        store.complete(id, output -> output.write(1));

        store.cleanupExpired(Duration.ofDays(7));

        assertFalse(Files.exists(pdfPath(id)));
        assertEquals(SpellCardJobStatus.EXPIRED, store.find(id).orElseThrow().status());
        store.cleanupExpired(Duration.ZERO);
        assertTrue(store.find(id).isEmpty());
    }

    private ImmutableSpell spell(String name, String description) {
        return ImmutableSpell.builder().id(name).name(name).level(1).description(description).build();
    }

    private Path pdfPath(UUID jobId) {
        return artifactDirectory.resolve(jobId.toString()).resolve("spellcards.pdf");
    }
}
