package io.github.viturin.spellcards.worker.pdf;

import io.github.viturin.spellcards.application.model.SpellCardPdfLayout;
import io.github.viturin.spellcards.domain.model.Spell;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class SpellCardPdfRenderer {
    private static final float POINTS_PER_INCH = 72;
    private static final float CARD_WIDTH = 2.5f * POINTS_PER_INCH;
    private static final float CARD_HEIGHT = 3.5f * POINTS_PER_INCH;
    private static final int SHEET_COLUMNS = 3;
    private static final int SHEET_ROWS = 3;
    private static final float PADDING = 11;
    private static final float HEADING_TOP = CARD_HEIGHT - 17;
    private static final float LINE_WIDTH = CARD_WIDTH - 2 * PADDING;
    private static final float CONTINUATION_SPACE = 12;
    private static final Color ACCENT = new Color(0x852b43);
    private static final Color INK = new Color(0x262120);
    private static final Color MUTED = new Color(0x534843);
    private static final Color DIVIDER = new Color(0xddd2cb);
    private static final Color CUT_GUIDE = new Color(0x817770);

    public void render(List<Spell> spells, SpellCardPdfLayout layout, OutputStream output) throws IOException {
        try (var document = new PDDocument();
             var regularData = getClass().getResourceAsStream("/fonts/DejaVuSans.ttf");
             var boldData = getClass().getResourceAsStream("/fonts/DejaVuSans-Bold.ttf")) {
            var regular = PDType0Font.load(document, regularData);
            var bold = PDType0Font.load(document, boldData);
            List<Card> cards = new ArrayList<>();
            for (Spell spell : spells) {
                addSpellCards(cards, spell, regular, bold);
            }
            addPages(document, cards, layout);
            document.save(output);
        }
    }

    private void addSpellCards(List<Card> cards, Spell spell, PDType0Font regular, PDType0Font bold)
            throws IOException {
        List<Line> body = new ArrayList<>();
        appendField(body, "Actions", spell.actionCost(), regular);
        appendField(body, "Range", spell.range(), regular);
        appendField(body, "Target", spell.target(), regular);
        String duration = spell.duration();
        if (spell.sustained()) {
            duration = duration.isBlank() ? "Sustained" : duration + " (sustained)";
        }
        appendField(body, "Duration", duration, regular);
        appendField(body, "Traditions", String.join(", ", spell.traditions()), regular);
        appendField(body, "Traits", String.join(", ", spell.traits()), regular);
        if (!spell.description().isBlank()) {
            if (!body.isEmpty()) {
                body.add(new Line("", regular, 4, INK));
            }
            append(body, spell.description(), regular, 8.5f, INK);
        }

        int bodyIndex = 0;
        for (int part = 1; part == 1 || bodyIndex < body.size(); part++) {
            List<Line> heading = new ArrayList<>();
            String label = part == 1
                    ? spell.kind().name() + "  ·  LEVEL " + spell.level() + "  ·  " + spell.rarity().toUpperCase(Locale.ROOT)
                    : "CONTINUED  ·  PART " + part;
            append(heading, label, bold, 7.5f, ACCENT);
            append(heading, spell.name(), bold, 15, INK);
            float bodyStart = HEADING_TOP - 9;
            for (Line line : heading) {
                bodyStart -= line.height();
            }

            int start = bodyIndex;
            float remaining = bodyStart - PADDING;
            while (bodyIndex < body.size() && remaining >= body.get(bodyIndex).height()) {
                remaining -= body.get(bodyIndex++).height();
            }
            boolean hasContinuation = bodyIndex < body.size();
            while (hasContinuation && bodyIndex > start && remaining < CONTINUATION_SPACE) {
                remaining += body.get(--bodyIndex).height();
            }
            if (start == bodyIndex && bodyIndex < body.size()) {
                throw new IOException("Spell heading leaves no space for card content.");
            }
            Line footer = hasContinuation ? new Line("Continued on card " + (part + 1), bold, 7, ACCENT) : null;
            cards.add(new Card(heading, body.subList(start, bodyIndex), footer));
        }
    }

    private void addPages(PDDocument document, List<Card> cards, SpellCardPdfLayout layout) throws IOException {
        boolean sheet = layout == SpellCardPdfLayout.A4_SHEET;
        float gridLeft = sheet ? (PDRectangle.A4.getWidth() - SHEET_COLUMNS * CARD_WIDTH) / 2 : 0;
        float gridTop = sheet ? (PDRectangle.A4.getHeight() + SHEET_ROWS * CARD_HEIGHT) / 2 : CARD_HEIGHT;
        int cardsPerPage = sheet ? SHEET_COLUMNS * SHEET_ROWS : 1;
        for (int start = 0; start < cards.size(); start += cardsPerPage) {
            var page = new PDPage(sheet ? PDRectangle.A4 : new PDRectangle(CARD_WIDTH, CARD_HEIGHT));
            document.addPage(page);
            try (var content = new PDPageContentStream(document, page)) {
                int count = Math.min(cardsPerPage, cards.size() - start);
                for (int index = 0; index < count; index++) {
                    float left = gridLeft + index % SHEET_COLUMNS * CARD_WIDTH;
                    float bottom = gridTop - (index / SHEET_COLUMNS + 1) * CARD_HEIGHT;
                    drawCard(content, cards.get(start + index), left, bottom);
                }
                if (sheet) {
                    drawCutGuides(content, gridLeft, gridTop, count);
                }
            }
        }
    }

    private void drawCard(PDPageContentStream content, Card card, float left, float bottom) throws IOException {
        float x = left + PADDING;
        content.setNonStrokingColor(ACCENT);
        content.addRect(x, bottom + CARD_HEIGHT - 12, 22, 2);
        content.fill();

        float y = bottom + HEADING_TOP;
        for (Line line : card.heading()) {
            y = drawLine(content, line, x, y);
        }
        content.setStrokingColor(DIVIDER);
        content.setLineWidth(0.5f);
        content.moveTo(x, y - 3);
        content.lineTo(left + CARD_WIDTH - PADDING, y - 3);
        content.stroke();
        y -= 9;
        for (Line line : card.body()) {
            y = drawLine(content, line, x, y);
        }
        if (card.footer() != null) {
            drawLine(content, card.footer(), x, bottom + PADDING + card.footer().height());
        }
    }

    private void drawCutGuides(PDPageContentStream content, float left, float top, int count) throws IOException {
        content.setStrokingColor(CUT_GUIDE);
        content.setLineWidth(0.4f);
        content.setLineDashPattern(new float[]{2, 2}, 0);
        int firstRowCount = Math.min(SHEET_COLUMNS, count);
        content.moveTo(left, top);
        content.lineTo(left + firstRowCount * CARD_WIDTH, top);
        for (int start = 0; start < count; start += SHEET_COLUMNS) {
            int columns = Math.min(SHEET_COLUMNS, count - start);
            float rowTop = top - start / SHEET_COLUMNS * CARD_HEIGHT;
            float rowBottom = rowTop - CARD_HEIGHT;
            content.moveTo(left, rowBottom);
            content.lineTo(left + columns * CARD_WIDTH, rowBottom);
            for (int column = 0; column <= columns; column++) {
                float x = left + column * CARD_WIDTH;
                content.moveTo(x, rowTop);
                content.lineTo(x, rowBottom);
            }
        }
        content.stroke();
    }

    private void appendField(List<Line> lines, String name, String value, PDType0Font font) throws IOException {
        if (!value.isBlank()) {
            append(lines, name + ": " + value, font, 8, MUTED);
        }
    }

    private void append(List<Line> lines, String text, PDType0Font font, float fontSize, Color color) throws IOException {
        for (String paragraph : text.split("\\R", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.strip().split("(?U)\\s+")) {
                String printable = printableText(word, font);
                if (!line.isEmpty() && width(font, fontSize, line + " " + printable) > LINE_WIDTH) {
                    lines.add(new Line(line.toString(), font, fontSize, color));
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
                        lines.add(new Line(line.toString(), font, fontSize, color));
                        line.setLength(0);
                    }
                    line.append(glyph);
                }
            }
            lines.add(new Line(line.toString(), font, fontSize, color));
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

    private float width(PDType0Font font, float fontSize, String text) throws IOException {
        return font.getStringWidth(text) * fontSize / 1000;
    }

    private float drawLine(PDPageContentStream content, Line line, float x, float top) throws IOException {
        if (!line.text().isEmpty()) {
            content.beginText();
            content.setFont(line.font(), line.fontSize());
            content.setNonStrokingColor(line.color());
            content.newLineAtOffset(x, top - line.fontSize());
            content.showText(line.text());
            content.endText();
        }
        return top - line.height();
    }

    private record Card(List<Line> heading, List<Line> body, Line footer) {
    }

    private record Line(String text, PDType0Font font, float fontSize, Color color) {
        float height() {
            return fontSize + 3;
        }
    }
}
