package io.github.viturin.spellcards.worker.pdf;

import io.github.viturin.spellcards.application.model.SpellCardPdfLayout;
import io.github.viturin.spellcards.domain.model.ImmutableSpell;
import io.github.viturin.spellcards.domain.model.Spell;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpellCardDuplexTest {
    private final List<Spell> spells = List.of(
            spell("Short spell", 1), spell("Two-part spell", 22), spell("Three-part spell", 42));

    @Test
    void pairsContinuationPagesAndAddsMatchingBacksWhenNeeded() throws IOException {
        try (var pdf = render(SpellCardPdfLayout.PLAYING_CARD_DUPLEX)) {
            assertEquals(8, pdf.getNumberOfPages());
            assertTrue(text(pdf, 1).contains("Rule line 1."));
            assertTrue(text(pdf, 2).contains("Short spell"));
            assertTrue(text(pdf, 2).contains("Pathfinder Player Core"));
            assertFalse(text(pdf, 2).contains("Rule line"));
            assertTrue(text(pdf, 3).contains("Two-part spell"));
            assertTrue(text(pdf, 4).contains("PART 2"));
            assertTrue(text(pdf, 5).contains("Three-part spell"));
            assertTrue(text(pdf, 6).contains("Next: card 3"));
            assertTrue(text(pdf, 7).contains("PART 3"));
            assertTrue(text(pdf, 8).contains("PART 3"));
            assertFalse(text(pdf, 8).contains("Rule line"));
        }
    }

    @Test
    void mirrorsPartialA4RowsForLongEdgePrinting() throws IOException {
        try (var pdf = render(SpellCardPdfLayout.A4_DUPLEX)) {
            assertEquals(2, pdf.getNumberOfPages());
            assertEquals("DuplexFlipLongEdge", pdf.getDocumentCatalog().getViewerPreferences().getDuplex());
            var renderer = new PDFRenderer(pdf);
            var front = renderer.renderImage(0);
            var back = renderer.renderImage(1);
            // With four physical cards, only the first slot of row two is occupied on the front.
            int firstColumn = front.getWidth() / 2 - 180;
            int lastColumn = front.getWidth() / 2 + 180;
            int secondRow = front.getHeight() / 2;
            assertEquals(0xfffbf3, front.getRGB(firstColumn, secondRow + 90) & 0xffffff);
            assertEquals(0xffffff, front.getRGB(lastColumn, secondRow + 90) & 0xffffff);
            assertEquals(0xffffff, back.getRGB(firstColumn, secondRow + 90) & 0xffffff);
            assertEquals(0xfffbf3, back.getRGB(lastColumn, secondRow + 90) & 0xffffff);
            int guideY = front.getHeight() / 2 + 126;
            assertTrue(hasGuide(front, firstColumn, guideY));
            assertTrue(hasGuide(back, lastColumn, guideY));
            assertFalse(hasGuide(front, lastColumn, guideY));
            assertFalse(hasGuide(back, firstColumn, guideY));
        }
    }

    private static boolean hasGuide(java.awt.image.BufferedImage page, int center, int expectedY) {
        for (int x = center - 10; x < center + 10; x++) {
            for (int y = expectedY - 2; y <= expectedY + 2; y++) {
                if ((page.getRGB(x, y) & 0xffffff) != 0xffffff) return true;
            }
        }
        return false;
    }

    private PDDocument render(SpellCardPdfLayout layout) throws IOException {
        var output = new ByteArrayOutputStream();
        new SpellCardPdfRenderer().render(spells, layout, output);
        return Loader.loadPDF(output.toByteArray());
    }

    private static String text(PDDocument pdf, int page) throws IOException {
        var stripper = new PDFTextStripper();
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        return stripper.getText(pdf);
    }

    private static Spell spell(String name, int lines) {
        String description = java.util.stream.IntStream.rangeClosed(1, lines)
                .mapToObj(index -> "Rule line " + index + ".").collect(java.util.stream.Collectors.joining("\n"));
        return ImmutableSpell.builder().id(name).name(name).level(1).actionCost("2")
                .publication("Pathfinder Player Core").description(description).build();
    }
}
