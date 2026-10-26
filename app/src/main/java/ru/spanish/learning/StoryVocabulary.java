package ru.spanish.learning;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/** Adds the exact printed word-form to study without guessing an unreliable translation. */
final class StoryVocabulary {
    private StoryVocabulary() {}


    /**
     * An already learned word needs no new translation. New words are never
     * saved here: unlike the former story long-press path, a sentence-level
     * translation or a lemma is NOT a translation of the selected word-form.
     */
    static Store.StoryWordSave existing(Store store, String printed) {
        String word = printed == null ? "" : printed.trim();
        if (word.isEmpty() || word.length() > 55)
            throw new IllegalArgumentException("Некорректная словоформа");
        for (Store.Card card : store.vocabulary()) {
            if (card.kind() != 0 || !card.spanish().equalsIgnoreCase(word)) continue;
            if (card.mastered()) {
                store.setMastered(card.id(), false);
                return Store.StoryWordSave.RESTORED;
            }
            return Store.StoryWordSave.ALREADY_STUDYING;
        }
        return null;
    }

    /**
     * The only write path for a newly selected story word. The caller supplies
     * a concrete, literal-first Russian meaning AFTER three model reviews.
     */
    static Store.StoryWordSave saveVerified(Context context, Store store,
                                           String printed, String russian, int category)
            throws IOException {
        requireVerified(printed, russian);
        if (category < 0 || category > 3)
            throw new IllegalArgumentException("Неверная категория слова");
        Store.StoryWordSave already = existing(store, printed);
        if (already != null) return already;
        String word = printed.trim();
        String lemma = store.isEnglish() ? "" : irregularLemma(word);
        ensureIllustration(context, store, word, lemma);
        return store.addStoryWord(word, russian.trim(), category);
    }

    /** Fail closed at the database boundary even if another caller skips the AI. */
    static void requireVerified(String foreign, String russian) {
        CardAdditionPolicy.validate(foreign, russian, false);
        String meaning = russian.trim().toLowerCase(Locale.ROOT);
        if (meaning.startsWith("форма глагола ")
                || meaning.startsWith("словоформа из ")
                || meaning.startsWith("слово из ")
                || meaning.startsWith("контекст:")
                || meaning.startsWith("перевод предложения:")
                || meaning.contains("точный перевод")
                || meaning.contains("перевод уточняется"))
            throw new IllegalArgumentException("Нужен перевод самой словоформы, не описание");
    }

    record Proposal(String russian, String grammar, int category) {}

    /** Parse a strictly structured translation hypothesis, not proof of quality. */
    static Proposal proposal(String foreign, String raw) throws Exception {
        org.json.JSONObject answer = new org.json.JSONObject(raw == null ? "" : raw.trim());
        if (!answer.optBoolean("ok", false))
            throw new IllegalArgumentException("Невозможно уверенно перевести слово в этом контексте");
        String literal = answer.optString("literal", "").trim();
        String idiomatic = answer.optString("idiomatic", "").trim();
        String russian = CardTranslationAudit.literalFirst(literal, idiomatic);
        requireVerified(foreign, russian);
        String grammar = answer.optString("category", "").trim();
        int category = switch (grammar) {
            case "verb" -> 1;
            case "noun" -> 2;
            case "adjective" -> 3;
            case "other" -> 0;
            default -> throw new IllegalArgumentException("Не определена часть речи слова");
        };
        return new Proposal(russian, grammar, category);
    }

    private static String compact(String text, int max) {
        if (text == null) return "";
        String value = text.replace('\r', ' ').replace('\n', ' ').trim().replaceAll("\\s+", " ");
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** Only unambiguous established irregular forms; the surface form is always retained. */
    static String irregularLemma(String surface) {
        return switch (surface.toLowerCase(Locale.ROOT)) {
            case "fui", "fuiste", "fue", "fuimos", "fueron" -> "ser/ir";
            case "soy", "sos", "es", "somos", "son", "era", "eras", "éramos", "eran", "sido" -> "ser";
            case "voy", "vas", "va", "vamos", "van", "iba", "ibas", "íbamos", "iban", "iré" -> "ir";
            case "estoy", "estás", "está", "estamos", "están", "estaba", "estuvo", "estuve" -> "estar";
            case "tengo", "tenés", "tiene", "tenemos", "tienen", "tenía", "tuve", "tuvo", "tuvieron" -> "tener";
            case "hago", "hacés", "hace", "hacen", "hizo", "hicieron", "hecho", "hicimos" -> "hacer";
            case "dije", "dijo", "dijeron", "digo", "decís", "dice", "dicen" -> "decir";
            case "quiero", "querés", "quiere", "quería", "quiso", "quisieron", "quieren" -> "querer";
            case "podés", "puedo", "puede", "podía", "pudo", "pudieron", "pueden" -> "poder";
            case "sé", "sabés", "sabe", "sabía", "supo", "supieron" -> "saber";
            case "vengo", "venís", "viene", "vino", "vinieron" -> "venir";
            case "puse", "puso", "ponen", "pongo", "pusieron" -> "poner";
            case "vi", "vio", "vieron", "veo", "ves" -> "ver";
            case "doy", "das", "da", "dan", "dio", "dieron" -> "dar";
            case "salgo", "salís", "sale", "salió", "salieron" -> "salir";
            case "hay", "hubo", "había", "habrá", "han", "hemos" -> "haber";
            default -> "";
        };
    }

    private static void ensureIllustration(Context context, Store store, String word, String lemma) throws IOException {
        File target = CardImageLoader.imageFile(context, word, store.isEnglish());
        File directory = target.getParentFile();
        if (!directory.exists() && !directory.mkdirs()) throw new IOException("Cannot create card-images");
        String key = CardImageLoader.key(word);
        if (target.isFile()) return;
        // The bundled card illustrations only belong to Spanish lessons.
        if (!store.isEnglish()) {
            try (InputStream present = context.getAssets().open("card-images/" + key + ".svg")) {
                if (present.read() != -1) return;
            } catch (IOException ignored) { }
        }

        byte[] svg = null;
        for (String base : lemma.split("/")) {
            if (store.isEnglish() || base.isEmpty()) continue;
            try (InputStream asset = context.getAssets().open("card-images/" + CardImageLoader.key(base) + ".svg");
                 ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                for (int n; (n = asset.read(buffer)) != -1; ) bytes.write(buffer, 0, n);
                svg = bytes.toByteArray();
                break;
            } catch (IOException ignored) { }
        }
        if (svg == null) {
            // Honest offline fallback when the exact semantic illustration is unavailable.
            svg = ("""
                <svg xmlns="http://www.w3.org/2000/svg" width="256" height="256" viewBox="0 0 256 256">
                  <rect x="4" y="4" width="248" height="248" rx="34" fill="#E9F4FF"/>
                  <path d="M56 62 Q90 44 128 64 Q166 44 200 62 V193 Q162 176 128 196 Q94 176 56 193 Z" fill="#FFFFFF" stroke="#3978A8" stroke-width="10" stroke-linejoin="round"/>
                  <path d="M128 65 V196 M75 92 Q96 85 111 93 M75 121 Q96 114 111 122 M145 93 Q166 85 184 93 M145 122 Q166 114 184 122" fill="none" stroke="#91B9DC" stroke-width="7" stroke-linecap="round"/>
                  <path d="M165 53 V111 L178 100 L191 111 V53" fill="#EFAD61"/>
                </svg>
                """).getBytes(StandardCharsets.UTF_8);
        }
        File pending = new File(directory, key + ".tmp");
        try (FileOutputStream out = new FileOutputStream(pending)) {
            out.write(svg);
            out.getFD().sync();
        }
        if (!pending.renameTo(target)) {
            pending.delete();
            throw new IOException("Cannot install card illustration");
        }
    }
}
