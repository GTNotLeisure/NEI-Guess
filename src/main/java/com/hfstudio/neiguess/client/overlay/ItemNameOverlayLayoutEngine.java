package com.hfstudio.neiguess.client.overlay;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import net.minecraft.client.gui.FontRenderer;

public class ItemNameOverlayLayoutEngine {

    private String languageCode = "en_US";
    private BreakIterator lineBreaker = BreakIterator.getLineInstance(Locale.ENGLISH);

    public boolean setLanguage(String languageCode) {
        if (languageCode == null || languageCode.isEmpty()) languageCode = "en_US";
        if (this.languageCode.equals(languageCode)) return false;
        Locale locale = Locale.forLanguageTag(languageCode.replace('_', '-'));
        this.languageCode = languageCode;
        lineBreaker = BreakIterator.getLineInstance(
            locale.getLanguage()
                .isEmpty() ? Locale.ENGLISH : locale);
        return true;
    }

    public void clear() {
        lineBreaker.setText("");
    }

    public LabelLayout createLayout(LayoutRequest request) {
        final ItemNameOverlaySettings settings = request.settings();
        final FontRenderer font = request.font();
        final String text = sanitizeText(request.text());
        final float minimumScale = settings.minimumScale;
        if (text.isEmpty()) {
            return createLabelLayout(font, request.width(), Collections.emptyList(), minimumScale);
        }

        final boolean unicode = request.unicode() || hasUnicodeCharacters(text);
        final float preferredScale = Math.max(minimumScale, unicode ? settings.unicodeFontScale : settings.fontScale);
        final float lineHeight = ItemNameOverlayFont.lineHeight(font);
        final float singleLineScale = Math.min(
            preferredScale,
            Math.min(
                request.width() / (float) Math.max(1, getTextWidth(font, text)),
                request.height() / (lineHeight + 1)));
        if (singleLineScale >= minimumScale && fitsCharacterLimit(text, settings.maxCharacters)) {
            return createLabelLayout(font, request.width(), Collections.singletonList(text), singleLineScale);
        }

        final int maximumWidth = (int) Math.nextUp(request.width() / minimumScale);
        final LineBreaks breaks = createLineBreaks(font, text, maximumWidth, settings.maxCharacters);
        int availableLines = 1;
        for (int lineCount = 2; lineCount <= settings.maxLines; lineCount++) {
            final float wrappedScale = Math.min(preferredScale, request.height() / (lineHeight * lineCount + 1));
            if (wrappedScale < minimumScale) {
                break;
            }
            availableLines = lineCount;
            List<String> completeLines = wrapText(
                font,
                text,
                maximumWidth,
                lineCount,
                settings.maxCharacters,
                breaks,
                false);
            if (fitsWidth(font, completeLines, maximumWidth, settings.maxCharacters)) {
                // Find the narrowest complete split before reducing the configured multi-line scale.
                int lowerWidth = 1;
                int upperWidth = maximumWidth;
                while (lowerWidth < upperWidth) {
                    final int candidateWidth = lowerWidth + (upperWidth - lowerWidth) / 2;
                    final List<String> candidateLines = wrapText(
                        font,
                        text,
                        candidateWidth,
                        lineCount,
                        settings.maxCharacters,
                        breaks,
                        false);
                    if (fitsWidth(font, candidateLines, candidateWidth, settings.maxCharacters)) {
                        upperWidth = candidateWidth;
                        completeLines = candidateLines;
                    } else {
                        lowerWidth = candidateWidth + 1;
                    }
                }

                int widestLine = 1;
                for (String line : completeLines) {
                    widestLine = Math.max(widestLine, getTextWidth(font, line));
                }
                final float completeScale = Math.min(wrappedScale, request.width() / (float) widestLine);
                return createLabelLayout(font, request.width(), completeLines, completeScale);
            }

        }

        final float croppedScale = Math.min(preferredScale, request.height() / (lineHeight * availableLines + 1));
        if (croppedScale < minimumScale) {
            return createLabelLayout(font, request.width(), Collections.emptyList(), croppedScale);
        }

        final int textWidth = (int) (request.width() / croppedScale);
        final List<String> shortenedLines = new ArrayList<>(
            wrapText(font, text, textWidth, availableLines, settings.maxCharacters, breaks, true));
        if (shortenedLines.isEmpty()) {
            final String line = ellipsize(font, text, textWidth, settings.maxCharacters);
            if (!line.isEmpty()) {
                shortenedLines.add(line);
            }
        } else {
            final int lastLine = shortenedLines.size() - 1;
            shortenedLines
                .set(lastLine, ellipsize(font, shortenedLines.get(lastLine), textWidth, settings.maxCharacters));
        }
        return createLabelLayout(font, request.width(), shortenedLines, croppedScale);
    }

    private LineBreaks createLineBreaks(FontRenderer font, String text, int maximumWidth, int maxCharacters) {
        lineBreaker.setText(text);
        final int[] boundaries = new int[text.length() + 1];
        int count = 0;
        for (int boundary = lineBreaker.first(); boundary != BreakIterator.DONE; boundary = lineBreaker.next()) {
            boundaries[count++] = boundary;
        }
        final boolean[] splittable = new boolean[count - 1];
        for (int index = 0; index < splittable.length; index++) {
            final String token = text.substring(boundaries[index], boundaries[index + 1])
                .trim();
            splittable[index] = getTextWidth(font, token) > maximumWidth || !fitsCharacterLimit(token, maxCharacters);
        }
        return new LineBreaks(boundaries, count, splittable);
    }

    private List<String> wrapText(FontRenderer font, String text, int width, int maxLines, int maxCharacters,
        LineBreaks breaks, boolean forceFit) {
        final List<String> lines = new ArrayList<>(maxLines);
        int start = 0;
        while (start < text.length()) {
            final String remainder = text.substring(start);
            if (lines.size() == maxLines - 1) {
                lines.add(remainder);
                break;
            }
            String prefix = trimToWidth(font, remainder, width);
            if (maxCharacters > 0) {
                prefix = trimCharacters(prefix, maxCharacters);
            }
            if (prefix.isEmpty()) {
                return Collections.emptyList();
            }
            int fittedEnd = start + prefix.length();
            // Trailing spaces do not consume visible line width or the character limit.
            while (fittedEnd < text.length() && text.charAt(fittedEnd) <= ' ') {
                fittedEnd++;
            }
            final int split = breaks.select(start, fittedEnd, forceFit);
            lines.add(
                text.substring(start, split)
                    .trim());
            start = split;
            while (start < text.length() && text.charAt(start) <= ' ') {
                start++;
            }
        }
        return lines;
    }

    private boolean fitsWidth(FontRenderer font, List<String> lines, int width, int maxCharacters) {
        if (lines.isEmpty()) {
            return false;
        }
        for (String line : lines) {
            if (getTextWidth(font, line) > width || !fitsCharacterLimit(line, maxCharacters)) {
                return false;
            }
        }
        return true;
    }

    private String sanitizeText(String text) {
        text = ItemNameOverlayFont.preprocess(text);
        if (text.indexOf('\u00a7') >= 0) {
            final StringBuilder plain = new StringBuilder(text.length());
            for (int index = 0; index < text.length(); index++) {
                if (text.charAt(index) == '\u00a7' && index + 1 < text.length()) {
                    index++;
                } else {
                    plain.append(text.charAt(index));
                }
            }
            text = plain.toString();
        }
        return text.replace('\n', ' ')
            .replace('\r', ' ')
            .replace('\t', ' ')
            .trim();
    }

    private String ellipsize(FontRenderer font, String text, int width, int maxCharacters) {
        if (getTextWidth(font, text) <= width && fitsCharacterLimit(text, maxCharacters)) {
            return text;
        }

        String suffix = trimToWidth(font, "...", width);
        if (maxCharacters > 0) {
            suffix = trimCharacters(suffix, maxCharacters);
        }
        String prefix = trimToWidth(font, text, width - getTextWidth(font, suffix)).trim();
        if (maxCharacters > 0) {
            prefix = trimCharacters(prefix, maxCharacters - suffix.codePointCount(0, suffix.length()));
        }
        String shortened = prefix + suffix;
        // Custom glyph spacing at the join must be measured as part of the complete string.
        while (!prefix.isEmpty() && getTextWidth(font, shortened) > width) {
            prefix = prefix.substring(0, prefix.offsetByCodePoints(prefix.length(), -1))
                .trim();
            shortened = prefix + suffix;
        }
        return shortened;
    }

    private String trimToWidth(FontRenderer font, String text, int width) {
        if (width <= 0) {
            return "";
        }

        final String trimmed = ItemNameOverlayFont.trimToWidth(font, text, width);
        final int length = trimmed.length();
        return length > 0 && Character.isHighSurrogate(trimmed.charAt(length - 1)) ? trimmed.substring(0, length - 1)
            : trimmed;
    }

    private static int getTextWidth(FontRenderer font, String text) {
        return ItemNameOverlayFont.width(font, text);
    }

    private static boolean fitsCharacterLimit(String text, int limit) {
        return limit == 0 || text.codePointCount(0, text.length()) <= limit;
    }

    private static String trimCharacters(String text, int limit) {
        final int count = Math.min(limit, text.codePointCount(0, text.length()));
        return text.substring(0, text.offsetByCodePoints(0, count));
    }

    private boolean hasUnicodeCharacters(String text) {
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) > 255) {
                return true;
            }
        }
        return false;
    }

    private record LineBreaks(int[] boundaries, int count, boolean[] splittable) {

        public int select(int start, int fittedEnd, boolean forceFit) {
            final int boundary = boundaries[atOrBefore(fittedEnd)];
            if (boundary > start) {
                return boundary;
            }
            final int token = atOrBefore(start);
            // Keep normal tokens whole during scale selection; split only when constraints require it.
            return forceFit || splittable[token] ? fittedEnd : boundaries[token + 1];
        }

        private int atOrBefore(int offset) {
            final int index = Arrays.binarySearch(boundaries, 0, count, offset);
            return index >= 0 ? index : -index - 2;
        }
    }

    private LabelLayout createLabelLayout(FontRenderer font, int width, List<String> texts, float scale) {
        List<LabelLine> lines = new ArrayList<>(texts.size());
        float lineHeight = ItemNameOverlayFont.lineHeight(font);
        float anchorOffset = (lineHeight - font.FONT_HEIGHT) / 2f;
        for (int index = 0; index < texts.size(); index++) {
            String text = texts.get(index);
            lines.add(
                new LabelLine(
                    text,
                    (width - getTextWidth(font, text) * scale) / 2f,
                    (1 + anchorOffset + index * lineHeight) * scale));
        }
        return new LabelLayout(lines, scale);
    }

    public record LabelLayout(List<LabelLine> lines, float scale) {

        public LabelLayout {
            lines = List.copyOf(lines);
        }
    }

    public record LabelLine(String text, float x, float y) {}

    public record LayoutRequest(FontRenderer font, String text, int width, int height, boolean unicode,
        ItemNameOverlaySettings settings) {

        public LayoutRequest {
            Objects.requireNonNull(font);
            Objects.requireNonNull(text);
            Objects.requireNonNull(settings);
            if (width <= 0 || height <= 0) throw new IllegalArgumentException("Label dimensions must be positive");
        }
    }
}
