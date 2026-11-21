package io.github.viturin.spellcards.worker.pdf;

import io.github.viturin.spellcards.application.model.SpellCardPdfLayout;
import io.github.viturin.spellcards.domain.model.ImmutableSpell;
import io.github.viturin.spellcards.domain.model.SpellCastingDetails;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpellCardPdfTypographyTest {
    @ParameterizedTest
    @EnumSource(SpellCardPdfLayout.class)
    void justifiesWrappedProseWithinTheCardButLeavesTheLastLineRagged(SpellCardPdfLayout layout) throws Exception {
        var spell = ImmutableSpell.builder().id("example").name("Example Spell").level(1)
                .description("glow ".repeat(14) + "\n\nFinal line.")
                .build();
        var output = new ByteArrayOutputStream();
        new SpellCardPdfRenderer().render(List.of(spell), layout, output);

        try (var document = Loader.loadPDF(output.toByteArray())) {
            var page = document.getPage(0);
            assertTrue(new PDFStreamParser(page).parse().stream()
                    .anyMatch(token -> token instanceof Operator operator && operator.getName().equals("TJ")));

            float gridLeft = layout == SpellCardPdfLayout.A4_SHEET ? (page.getMediaBox().getWidth() - 540) / 2 : 0;
            float left = gridLeft + 7;
            float right = gridLeft + 180 - 7;
            var text = new PositionedText();
            text.getText(document);

            assertTrue(text.lines.stream().anyMatch(line -> Math.abs(line.right - right) < 1));
            assertTrue(text.lines.stream().allMatch(line -> line.left >= left - 1 && line.right <= right + 1));
            var finalLine = text.lines.stream().filter(line -> line.text.equals("Final line.")).findFirst().orElseThrow();
            assertEquals(left, finalLine.left, 0.1);
            assertTrue(finalLine.right < left + 75);
        }
    }

    @ParameterizedTest
    @EnumSource(SpellCardPdfLayout.class)
    void printsRuleSymbolsAndIndividualTraitChipsInGrayscaleLayouts(SpellCardPdfLayout layout) throws Exception {
        var spell = ImmutableSpell.builder().id("example").name("Example Spell").level(1)
                .range("60 feet").target("1 creature").traditions(List.of("arcane", "occult"))
                .traits(List.of("fire", "concentrate", "manipulate"))
                .castingDetails(new SpellCastingDetails("20-foot burst", "Basic Reflex", "", "", "", "", "", ""))
                .description("A concise effect.").build();
        var output = new ByteArrayOutputStream();
        new SpellCardPdfRenderer().render(List.of(spell), layout, output);

        try (var document = Loader.loadPDF(output.toByteArray())) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("↔ Range: 60 feet"), text);
            assertTrue(text.contains("▦ Area: 20-foot burst"), text);
            assertTrue(text.contains("⬟ Defense: Basic Reflex"), text);
            assertTrue(text.contains("✦ Traditions: arcane, occult"), text);
            assertTrue(text.contains("fire"), text);
            assertTrue(text.contains("concentrate"), text);
            assertTrue(text.contains("manipulate"), text);
            assertFalse(text.contains("Traits:"), text);
        }
    }

    private static class PositionedText extends PDFTextStripper {
        private final List<PositionedLine> lines = new ArrayList<>();

        @Override
        protected void writeString(String text, List<TextPosition> positions) throws IOException {
            var first = positions.getFirst();
            var last = positions.getLast();
            lines.add(new PositionedLine(text, first.getXDirAdj(), last.getXDirAdj() + last.getWidthDirAdj()));
            super.writeString(text, positions);
        }
    }

    private record PositionedLine(String text, float left, float right) {
    }
}
