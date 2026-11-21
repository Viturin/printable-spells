package io.github.viturin.spellcards.worker.pdf;

import io.github.viturin.spellcards.application.model.SpellCardFace;
import io.github.viturin.spellcards.application.model.SpellCardFace.ActionBadge;
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

@Component
public class SpellCardPdfRenderer {
    private static final float POINTS_PER_INCH = 72;
    private static final float CARD_WIDTH = 2.5f * POINTS_PER_INCH;
    private static final float CARD_HEIGHT = 3.5f * POINTS_PER_INCH;
    private static final int SHEET_COLUMNS = 3;
    private static final int SHEET_ROWS = 3;
    private static final float FRAME = 3;
    private static final float PADDING = 8;
    private static final float HEADING_TOP = CARD_HEIGHT - 7;
    private static final float LINE_WIDTH = CARD_WIDTH - 2 * PADDING;
    private static final float BADGE_WIDTH = 43;
    private static final float BADGE_HEIGHT = 21;
    private static final float CONTINUATION_SPACE = 11;
    private static final Color FRAME_COLOR = new Color(0x262c30);
    private static final Color PAPER = new Color(0xfffbf3);
    private static final Color TYPE_STRIP = new Color(0xe9ddc5);
    private static final Color INK = new Color(0x262120);
    private static final Color MUTED = new Color(0x534843);
    private static final Color CUT_GUIDE = new Color(0x817770);
    private static final Color SPELL_ACCENT = new Color(0x29484a);
    private static final Color FOCUS_ACCENT = new Color(0x4c3b60);
    private static final Color RITUAL_ACCENT = new Color(0x563a29);

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
        Color accent = switch (spell.kind()) {
            case SPELL -> SPELL_ACCENT;
            case FOCUS -> FOCUS_ACCENT;
            case RITUAL -> RITUAL_ACCENT;
        };
        var face = SpellCardFace.from(spell);
        List<String> rules = face.rules().stream().map(rule -> rule.label() + ": " + rule.value()).toList();
        List<Line> body = bodyLines(rules, spell.description(), regular, 8.4f);

        int bodyIndex = 0;
        for (int part = 1; part == 1 || bodyIndex < body.size(); part++) {
            ActionBadge badge = part == 1 ? face.badge().orElse(null) : null;
            List<Line> title = new ArrayList<>();
            List<Line> type = new ArrayList<>();
            String label = part == 1 ? face.classification() : "CONTINUED  ·  PART " + part;
            append(title, spell.name(), bold, 13, PAPER, badge == null ? LINE_WIDTH : LINE_WIDTH - BADGE_WIDTH - 5);
            append(type, label, bold, 7, accent);
            float titleHeight = (float) title.stream().mapToDouble(Line::height).sum();
            float titleBottom = HEADING_TOP - Math.max(titleHeight, badge == null ? 0 : BADGE_HEIGHT) - 3;
            float typeBottom = titleBottom - (float) type.stream().mapToDouble(Line::height).sum() - 5;
            float bodyStart = typeBottom - 5;

            int start = bodyIndex;
            float remaining = bodyStart - PADDING;
            if (part == 1) {
                for (float largerSize : new float[]{10, 9.2f}) {
                    var largerBody = bodyLines(rules, spell.description(), regular, largerSize);
                    if (largerBody.stream().mapToDouble(Line::height).sum() <= remaining) {
                        body = largerBody;
                        break;
                    }
                }
            }
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
            Line footer = hasContinuation ? new Line("Continued on card " + (part + 1), bold, 7, accent) : null;
            cards.add(new Card(title, type, body.subList(start, bodyIndex), footer, badge, accent, titleBottom, typeBottom));
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
        fill(content, FRAME_COLOR, left + 0.75f, bottom + 0.75f, CARD_WIDTH - 1.5f, CARD_HEIGHT - 1.5f);
        fill(content, PAPER, left + FRAME, bottom + FRAME, CARD_WIDTH - 2 * FRAME, CARD_HEIGHT - 2 * FRAME);
        fill(content, card.accent(), left + FRAME, bottom + card.titleBottom(),
                CARD_WIDTH - 2 * FRAME, CARD_HEIGHT - FRAME - card.titleBottom());
        fill(content, TYPE_STRIP, left + FRAME, bottom + card.typeBottom(),
                CARD_WIDTH - 2 * FRAME, card.titleBottom() - card.typeBottom());

        float y = bottom + HEADING_TOP;
        for (Line line : card.title()) {
            y = drawLine(content, line, x, y);
        }
        if (card.badge() != null) {
            drawBadge(content, card, left + CARD_WIDTH - PADDING - BADGE_WIDTH, bottom + HEADING_TOP);
        }
        y = bottom + card.titleBottom() - 2;
        for (Line line : card.type()) {
            y = drawLine(content, line, x, y);
        }
        y = bottom + card.typeBottom() - 5;
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

    private List<Line> bodyLines(List<String> rules, String description, PDType0Font regular, float size)
            throws IOException {
        List<Line> lines = new ArrayList<>();
        float ruleSize = size - 0.7f;
        for (int index = 0; index < rules.size(); index++) {
            String row = rules.get(index);
            if (index + 1 < rules.size()) {
                String pair = row + "   ·   " + rules.get(index + 1);
                if (!pair.contains("\n") && width(regular, ruleSize, printableText(pair, regular)) <= LINE_WIDTH) {
                    row = pair;
                    index++;
                }
            }
            append(lines, row, regular, ruleSize, MUTED);
        }
        if (!description.isBlank()) {
            if (!lines.isEmpty()) {
                lines.add(new Line("", regular, 2, INK));
            }
            for (String paragraph : description.split("\\R", -1)) {
                if (paragraph.isBlank()) {
                    lines.add(new Line("", regular, 2, INK));
                } else {
                    append(lines, paragraph, regular, size, INK);
                }
            }
        }
        return lines;
    }

    private void drawBadge(PDPageContentStream content, Card card, float left, float top) throws IOException {
        fill(content, TYPE_STRIP, left, top - BADGE_HEIGHT, BADGE_WIDTH, BADGE_HEIGHT);
        var font = card.title().getFirst().font();
        String symbols = card.badge().symbols();
        float size = Math.min(9, 9 * (BADGE_WIDTH - 4) / width(font, 9, symbols));
        drawCentered(content, new Line(symbols, font, size, card.accent()), left, top - 2);
        drawCentered(content, new Line(card.badge().caption(), font, 5.8f, card.accent()), left, top - 12.5f);
    }

    private void drawCentered(PDPageContentStream content, Line line, float left, float top) throws IOException {
        float offset = (BADGE_WIDTH - width(line.font(), line.fontSize(), line.text())) / 2;
        drawLine(content, line, left + offset, top);
    }

    private void fill(PDPageContentStream content, Color color, float x, float y, float width, float height)
            throws IOException {
        content.setNonStrokingColor(color);
        content.addRect(x, y, width, height);
        content.fill();
    }

    private void append(List<Line> lines, String text, PDType0Font font, float fontSize, Color color) throws IOException {
        append(lines, text, font, fontSize, color, LINE_WIDTH);
    }

    private void append(List<Line> lines, String text, PDType0Font font, float fontSize, Color color, float lineWidth)
            throws IOException {
        for (String paragraph : text.split("\\R", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.strip().split("(?U)\\s+")) {
                String printable = printableText(word, font);
                if (!line.isEmpty() && width(font, fontSize, line + " " + printable) > lineWidth) {
                    lines.add(new Line(line.toString(), font, fontSize, color));
                    line.setLength(0);
                }
                if (!line.isEmpty()) {
                    line.append(' ');
                }
                if (width(font, fontSize, printable) <= lineWidth) {
                    line.append(printable);
                    continue;
                }
                for (int codePoint : printable.codePoints().toArray()) {
                    String glyph = Character.toString(codePoint);
                    if (!line.isEmpty() && width(font, fontSize, line + glyph) > lineWidth) {
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

    private record Card(List<Line> title, List<Line> type, List<Line> body, Line footer,
                        ActionBadge badge, Color accent, float titleBottom, float typeBottom) {
    }

    private record Line(String text, PDType0Font font, float fontSize, Color color) {
        float height() {
            return fontSize + 2.3f;
        }
    }
}
