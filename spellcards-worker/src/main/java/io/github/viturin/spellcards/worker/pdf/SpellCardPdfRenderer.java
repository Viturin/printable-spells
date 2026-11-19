package io.github.viturin.spellcards.worker.pdf;

import io.github.viturin.spellcards.domain.model.Spell;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class SpellCardPdfRenderer {
    private static final float MARGIN = 48;
    private static final float LINE_WIDTH = PDRectangle.A4.getWidth() - 2 * MARGIN;

    public void render(List<Spell> spells, OutputStream output) throws IOException {
        try (var document = new PDDocument();
             var regularData = getClass().getResourceAsStream("/fonts/DejaVuSans.ttf");
             var boldData = getClass().getResourceAsStream("/fonts/DejaVuSans-Bold.ttf")) {
            var regular = PDType0Font.load(document, regularData);
            var bold = PDType0Font.load(document, boldData);
            for (Spell spell : spells) {
                addSpellPages(document, spell, regular, bold);
            }
            document.save(output);
        }
    }

    private void addSpellPages(PDDocument document, Spell spell, PDType0Font regular, PDType0Font bold)
            throws IOException {
        List<Line> lines = new ArrayList<>();
        append(lines, spell.name(), bold, 20);
        append(lines, spell.kind().name().toLowerCase(Locale.ROOT) + " · Level " + spell.level() + " · " + spell.rarity(),
                regular, 11);
        appendField(lines, "Traditions", String.join(", ", spell.traditions()), regular);
        appendField(lines, "Traits", String.join(", ", spell.traits()), regular);
        appendField(lines, "Actions", spell.actionCost(), regular);
        appendField(lines, "Range", spell.range(), regular);
        appendField(lines, "Target", spell.target(), regular);
        String duration = spell.duration();
        if (spell.sustained()) {
            duration = duration.isBlank() ? "Sustained" : duration + " (sustained)";
        }
        appendField(lines, "Duration", duration, regular);
        lines.add(new Line("", regular, 11));
        append(lines, spell.description(), regular, 11);

        List<Line> continuation = new ArrayList<>();
        append(continuation, spell.name() + " (continued)", bold, 14);
        int lineIndex = 0;
        do {
            var page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            float y = page.getMediaBox().getHeight() - MARGIN;
            try (var content = new PDPageContentStream(document, page)) {
                if (lineIndex > 0) {
                    for (Line title : continuation) {
                        y = drawLine(content, title, y);
                    }
                }
                while (lineIndex < lines.size() && y - lines.get(lineIndex).height() >= MARGIN) {
                    y = drawLine(content, lines.get(lineIndex++), y);
                }
            }
        } while (lineIndex < lines.size());
    }

    private void appendField(List<Line> lines, String name, String value, PDType0Font font) throws IOException {
        if (!value.isBlank()) {
            append(lines, name + ": " + value, font, 11);
        }
    }

    private void append(List<Line> lines, String text, PDType0Font font, int fontSize) throws IOException {
        for (String paragraph : text.split("\\R", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.strip().split("(?U)\\s+")) {
                String printable = printableText(word, font);
                if (!line.isEmpty() && width(font, fontSize, line + " " + printable) > LINE_WIDTH) {
                    lines.add(new Line(line.toString(), font, fontSize));
                    line.setLength(0);
                }
                if (!line.isEmpty()) {
                    line.append(' ');
                }
                if (width(font, fontSize, printable) <= LINE_WIDTH) {
                    line.append(printable);
                    continue;
                }
                for (int codePoint : printable.codePoints().toArray()) {
                    String glyph = Character.toString(codePoint);
                    if (!line.isEmpty() && width(font, fontSize, line + glyph) > LINE_WIDTH) {
                        lines.add(new Line(line.toString(), font, fontSize));
                        line.setLength(0);
                    }
                    line.append(glyph);
                }
            }
            lines.add(new Line(line.toString(), font, fontSize));
        }
    }

    private String printableText(String text, PDType0Font font) throws IOException {
        try {
            font.encode(text);
            return text;
        } catch (IllegalArgumentException unsupportedGlyph) {
            StringBuilder printable = new StringBuilder();
            for (int codePoint : text.codePoints().toArray()) {
                String glyph = Character.toString(codePoint);
                try {
                    font.encode(glyph);
                    printable.append(glyph);
                } catch (IllegalArgumentException ignored) {
                    printable.append('�');
                }
            }
            return printable.toString();
        }
    }

    private float width(PDType0Font font, int fontSize, String text) throws IOException {
        return font.getStringWidth(text) * fontSize / 1000;
    }

    private float drawLine(PDPageContentStream content, Line line, float y) throws IOException {
        if (!line.text().isEmpty()) {
            content.beginText();
            content.setFont(line.font(), line.fontSize());
            content.newLineAtOffset(MARGIN, y);
            content.showText(line.text());
            content.endText();
        }
        return y - line.height();
    }

    private record Line(String text, PDType0Font font, int fontSize) {
        int height() {
            return fontSize + 5;
        }
    }
}
