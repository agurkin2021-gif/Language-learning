package ru.spanish.learning;

import java.util.List;

/**
 * Colors the Bible references and quotations in the separate red-text story.
 * Works only on rendered text: the original, unmarked text is sent to TTS
 * and retains its existing segmentation and Russian translation alignment.
 */
final class BibleStoryHighlight {
    private static final String TITLE_SUFFIX = "— citas en rojo";

    private BibleStoryHighlight() {}

    static boolean appliesTo(String title) {
        return title != null && title.trim().endsWith(TITLE_SUFFIX);
    }

    static boolean[] mark(String text) {
        List<String> chunks = Sentences.split(text);
        boolean[] red = new boolean[chunks.size()];
        boolean expectingQuote = false;
        boolean insideQuote = false;
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i).trim();
            if (isBibleReference(chunk)) {
                red[i] = true;
                expectingQuote = true;
                insideQuote = false;
                continue;
            }
            if (expectingQuote && chunk.indexOf('«') >= 0) {
                expectingQuote = false;
                insideQuote = true;
            }
            if (insideQuote) {
                red[i] = true;
                if (chunk.indexOf('»') >= 0) insideQuote = false;
            }
            // The short biblical confession at the end has no verse number
            // in the source manuscript and is attributed there to Daniel.
            if (chunk.startsWith("«Hemos pecado contra Ti»")) red[i] = true;
        }
        return red;
    }

    private static boolean isBibleReference(String chunk) {
        return chunk.matches("^(Deuteronomio|Jeremías|Sofonías|Segunda de Crónicas), capítulo\\b.*");
    }
}
