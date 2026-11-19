package io.github.viturin.spellcards.worker.adapters.in.queue;

import io.github.viturin.spellcards.application.model.SpellCardPdfLayout;
import io.github.viturin.spellcards.adapters.out.filesystem.FileSpellCardJobStore;
import io.github.viturin.spellcards.application.model.SpellCardJobStatus;
import io.github.viturin.spellcards.domain.model.ImmutableSpell;
import io.github.viturin.spellcards.domain.port.SpellRepository;
import io.github.viturin.spellcards.queue.model.SpellCardJobMessage;
import io.github.viturin.spellcards.worker.pdf.SpellCardPdfRenderer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

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
        store.createQueued(job.jobId(), job.spellNames(), SpellCardPdfLayout.PLAYING_CARD);
        store.markProcessing(job.jobId());
        when(repository.findByName("Daze")).thenReturn(Optional.of(spell("Daze", "Cloud the target’s mind.")));
        when(repository.findByName("Éclat")).thenReturn(Optional.of(spell("Éclat", "A bright flash.")));

        worker.handle(job);
        worker.handle(job);

        var completed = store.find(job.jobId()).orElseThrow();
        assertEquals(SpellCardJobStatus.COMPLETED, completed.status());
        assertEquals(SpellCardPdfLayout.PLAYING_CARD, completed.layout());
        assertEquals(Duration.ofHours(24), Duration.between(completed.updatedAt(), completed.expiresAt()));
        verify(repository, times(1)).findByName("Daze");
        try (var document = Loader.loadPDF(pdfPath(job.jobId()).toFile())) {
            assertEquals(2, document.getNumberOfPages());
            var card = document.getPage(0).getMediaBox();
            assertEquals(180, card.getWidth());
            assertEquals(252, card.getHeight());
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
    void fitsNinePlayingCardsOnEachA4SheetWithCutGuides() throws Exception {
        var store = new FileSpellCardJobStore(artifactDirectory, 24);
        var repository = mock(SpellRepository.class);
        var worker = new SpellCardJobWorker(store, repository, new SpellCardPdfRenderer(), 7);
        var names = IntStream.rangeClosed(1, 10).mapToObj(number -> "Example " + number).toList();
        var job = new SpellCardJobMessage(UUID.randomUUID(), names);
        store.createQueued(job.jobId(), names, SpellCardPdfLayout.A4_SHEET);
        for (String name : names) {
            when(repository.findByName(name)).thenReturn(Optional.of(spell(name, "A short spell description.")));
        }

        worker.handle(job);

        try (var document = Loader.loadPDF(pdfPath(job.jobId()).toFile())) {
            assertEquals(2, document.getNumberOfPages());
            var firstPage = document.getPage(0);
            assertEquals(PDRectangle.A4.getWidth(), firstPage.getMediaBox().getWidth());
            assertEquals(PDRectangle.A4.getHeight(), firstPage.getMediaBox().getHeight());
            assertTrue(new PDFStreamParser(firstPage).parse().stream()
                    .anyMatch(token -> token instanceof Operator operator && operator.getName().equals("d")));
            var text = new PDFTextStripper();
            text.setEndPage(1);
            String firstSheet = text.getText(document);
            assertTrue(firstSheet.indexOf("Example 1") < firstSheet.indexOf("Example 9"));
            assertFalse(firstSheet.contains("Example 10"));
            text.setStartPage(2);
            text.setEndPage(2);
            assertTrue(text.getText(document).contains("Example 10"));
        }
    }

    @Test
    void wrapsLongDescriptionsOntoContinuationPages() throws Exception {
        var store = new FileSpellCardJobStore(artifactDirectory, 24);
        var repository = mock(SpellRepository.class);
        var worker = new SpellCardJobWorker(store, repository, new SpellCardPdfRenderer(), 7);
        var job = new SpellCardJobMessage(UUID.randomUUID(), List.of("Long spell"));
        store.createQueued(job.jobId(), job.spellNames(), SpellCardPdfLayout.PLAYING_CARD);
        when(repository.findByName("Long spell")).thenReturn(Optional.of(spell(
                "Long spell", "A description that wraps onto a continuation page. ".repeat(150) + "Final words."
        )));

        worker.handle(job);

        try (var document = Loader.loadPDF(pdfPath(job.jobId()).toFile())) {
            assertTrue(document.getNumberOfPages() > 1);
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("CONTINUED"));
            assertTrue(text.contains("Continued on card 2"));
            assertTrue(text.replaceAll("\\s+", " ").contains("Final words."));
        }
    }

    @Test
    void missingSpellFailsTheWholeJobWithoutCreatingAnArtifact() {
        var store = new FileSpellCardJobStore(artifactDirectory, 24);
        var repository = mock(SpellRepository.class);
        var worker = new SpellCardJobWorker(store, repository, new SpellCardPdfRenderer(), 7);
        var job = new SpellCardJobMessage(UUID.randomUUID(), List.of("Missing"));
        store.createQueued(job.jobId(), job.spellNames(), SpellCardPdfLayout.PLAYING_CARD);
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
        store.createQueued(id, List.of("Daze"), SpellCardPdfLayout.A4_SHEET);
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
