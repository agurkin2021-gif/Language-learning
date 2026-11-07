package ru.spanish.learning;

import java.util.List;

/**
 * Visual highlighting for the separate "citas en rojo" copy of Gerizim y Ebal.
 * No markup is inserted into the story: word selection, speech synthesis,
 * original biblical wording and Spanish/Russian chunk alignment stay intact.
 */
final class BibleStoryHighlight {
    private static final String TITLE_SUFFIX = "— citas en rojo";

    private BibleStoryHighlight() {}

    static boolean appliesTo(String title) {
        return title != null && title.trim().endsWith(TITLE_SUFFIX);
    }

    static boolean[] mark(String text) {
        List<String> chunks = Sentences.split(text);
        boolean[] marked = new boolean[chunks.size()];
        boolean expectingBibleQuotation = false;
        boolean insideBibleQuotation = false;
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i).trim();
            if (isBibleReference(chunk)) {
                marked[i] = true;
                expectingBibleQuotation = true;
                insideBibleQuotation = false;
                continue;
            }
            if (expectingBibleQuotation && chunk.indexOf('«') >= 0) {
                expectingBibleQuotation = false;
                insideBibleQuotation = true;
            }
            if (insideBibleQuotation) {
                marked[i] = true;
                if (chunk.indexOf('»') >= 0) insideBibleQuotation = false;
            }
            // This quotation appears without a chapter/verse heading in the
            // original historical narrative and is attributed there to Daniel.
            if (chunk.startsWith("«Hemos pecado contra Ti»")) marked[i] = true;
        }
        return marked;
    }

    private static boolean isBibleReference(String chunk) {
        return chunk.matches("^(Deuteronomio|Jeremías|Sofonías|Segunda de Crónicas), capítulo\\b.*");
    }
}
