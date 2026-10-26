package ru.spanish.learning;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Guardrails for additions made through the embedded ChatGPT, not manual cards or stories. */
final class CardAdditionPolicy {
    static final int HARD_LIMIT = 10;
    private static final Pattern COUNT = Pattern.compile(
            "(?iu)(?<![\\p{L}\\d])(\\d{1,4})\\s+(?:[\\p{L}]+(?:-[\\p{L}]+)?\\s+){0,6}"
                    + "(?:слов\\p{L}*|фраз\\p{L}*|выражен\\p{L}*|глагол\\p{L}*|существительн\\p{L}*|прилагательн\\p{L}*)");
    private CardAdditionPolicy() {}

    static final class Batch {
        final int limit;
        int attempted;
        int added;
        int duplicates;
        int rejected;

        Batch(String userText) {
            Matcher matcher = COUNT.matcher(userText == null ? "" : userText);
            int requested = HARD_LIMIT;
            if (matcher.find()) {
                try { requested = Integer.parseInt(matcher.group(1)); }
                catch (NumberFormatException ignored) { requested = HARD_LIMIT; }
            }
            limit = Math.max(1, Math.min(HARD_LIMIT, requested));
        }

        boolean reached() { return attempted >= limit; }
        boolean hasAttempts() { return attempted > 0; }
        void record(String result) {
            attempted++;
            try {
                JSONObject response = new JSONObject(result);
                if (!response.optBoolean("ok", false)) rejected++;
                else if ("added".equals(response.optString("status"))) added++;
                else if ("already_exists".equals(response.optString("status"))) duplicates++;
                else rejected++;
            } catch (Exception ignored) { rejected++; }
        }
        String report() {
            return "Карточки: добавлено " + added + ", дубликатов " + duplicates
                    + ", отклонено " + rejected + ". Обработано " + attempted
                    + " из " + limit + " за запрос."
                    + (reached() ? " Лимит достигнут; следующая партия — отдельным запросом." : "");
        }
    }

    static void validate(String word, String translation, boolean phrase) {
        String v = word == null ? "" : word.trim();
        String t = translation == null ? "" : translation.trim();
        if (v.isEmpty() || t.isEmpty() || v.length() > (phrase ? 160 : 55)
                || t.length() > 180 || v.contains("\n") || t.contains("\n"))
            throw new IllegalArgumentException("Некорректная длина или пустое поле карточки");
        if (!phrase && !v.matches("[\\p{L}]+(?:[-'’][\\p{L}]+)?"))
            throw new IllegalArgumentException("Для слова нужна одна словоформа; фразы добавляются отдельно");
        if (!t.matches("(?s).*[А-Яа-яЁё].*") || t.toLowerCase(Locale.ROOT).contains("перевод уточня")
                || t.toLowerCase(Locale.ROOT).contains("неизвестно")
                || t.toLowerCase(Locale.ROOT).contains("зависит от контекста")
                || t.contains("???"))
            throw new IllegalArgumentException("Для карточки необходим конкретный русский перевод");
        if (v.equalsIgnoreCase(t)) throw new IllegalArgumentException("Не переведён текст карточки");
    }

    /**
     * Only exact, unambiguous, illustrated beginner nouns are rendered as Android
     * color emoji. The Russian root protects against mismatched translations.
     * All other words require a meaningful model-drawn SVG.
     */
    static String exactEmoji(String word, String russian, boolean english, int category) {
        if (category != 2) return null;
        String key = word.trim().toLowerCase(Locale.ROOT);
        String pair = english ? switch (key) {
            case "cat" -> "🐈|кот,кошк";
            case "dog" -> "🐕|собак";
            case "house" -> "🏠|дом";
            case "water" -> "💧|вод";
            case "book" -> "📖|книг";
            case "car" -> "🚗|машин";
            case "sun" -> "☀️|солн";
            case "tree" -> "🌳|дерев";
            case "apple" -> "🍎|яблок";
            case "coffee" -> "☕|коф";
            default -> null;
        } : switch (key) {
            case "gato" -> "🐈|кот,кошк";
            case "perro" -> "🐕|собак";
            case "casa" -> "🏠|дом";
            case "agua" -> "💧|вод";
            case "libro" -> "📖|книг";
            case "auto", "coche" -> "🚗|машин,автомобил";
            case "colectivo", "autobús" -> "🚌|автобус";
            case "tren" -> "🚆|поезд";
            case "subte", "metro" -> "🚇|метр";
            case "bicicleta" -> "🚲|велосипед";
            case "avión" -> "✈️|самол";
            case "barco" -> "🚢|кораб";
            case "taxi" -> "🚕|такс";
            case "sol" -> "☀️|солн";
            case "luna" -> "🌙|лун";
            case "estrella" -> "⭐|звезд";
            case "árbol" -> "🌳|дерев";
            case "flor" -> "🌷|цвет";
            case "lluvia" -> "🌧️|дожд";
            case "nieve" -> "❄️|снег";
            case "mar" -> "🌊|мор";
            case "montaña" -> "⛰️|гор";
            case "río" -> "🏞️|рек";
            case "playa" -> "🏖️|пляж";
            case "fuego" -> "🔥|огон";
            case "café" -> "☕|коф";
            case "té" -> "🍵|чай";
            case "leche" -> "🥛|молок";
            case "pan" -> "🍞|хлеб";
            case "queso" -> "🧀|сыр";
            case "huevo" -> "🥚|яйц";
            case "arroz" -> "🍚|рис";
            case "sopa" -> "🍲|суп";
            case "ensalada" -> "🥗|салат";
            case "pollo" -> "🍗|куриц";
            case "tomate" -> "🍅|помидор";
            case "papa", "patata" -> "🥔|картоф";
            case "cebolla" -> "🧅|лук";
            case "ajo" -> "🧄|чеснок";
            case "manzana" -> "🍎|яблок";
            case "banana", "plátano" -> "🍌|банан";
            case "naranja" -> "🍊|апельс";
            case "limón" -> "🍋|лимон";
            case "pizza" -> "🍕|пицц";
            case "chocolate" -> "🍫|шоколад";
            case "escuela" -> "🏫|школ";
            case "hospital" -> "🏥|больниц";
            case "farmacia" -> "💊|аптек";
            case "banco" -> "🏦|банк";
            case "aeropuerto" -> "✈️|аэропорт";
            case "teléfono" -> "📱|телефон";
            case "computadora", "ordenador" -> "💻|компьютер";
            case "llave" -> "🔑|ключ";
            case "reloj" -> "⌚|час";
            case "dinero" -> "💰|деньг";
            case "pelota" -> "⚽|мяч";
            case "zapato" -> "👞|ботин";
            case "camisa" -> "👕|рубаш";
            case "sombrero" -> "👒|шляп";
            case "paraguas" -> "☂️|зонт";
            case "cama" -> "🛏️|кроват";
            case "silla" -> "🪑|стул";
            default -> null;
        };
        if (pair == null) return null;
        String[] parts = pair.split("\\|", 2);
        String meaning = russian.toLowerCase(Locale.ROOT);
        for (String root : parts[1].split(",")) {
            if (meaning.contains(root)) return parts[0];
        }
        // A synonym may be valid; do not reject it on a heuristic match.
        // Instead require a model-drawn SVG that conveys the supplied meaning.
        return null;
    }

    static void validateSvg(String svg) {
        String s = svg == null ? "" : svg.trim();
        String lower = s.toLowerCase(Locale.ROOT);
        String withoutSvgNamespace = lower.replace("xmlns=\"http://www.w3.org/2000/svg\"", "");
        if (!s.contains("<svg") || !s.contains("</svg>")
                || !s.contains("width=\"256\"") || !s.contains("height=\"256\"")
                || !s.contains("viewBox=\"0 0 256 256\""))
            throw new IllegalArgumentException("Для слова без подходящего эмодзи нужен SVG 256x256");
        if (!lower.matches("(?s).*<(?:path|circle|rect|ellipse|polygon|polyline|line)\\b.*"))
            throw new IllegalArgumentException("SVG не содержит нарисованных объектов");
        if (lower.contains("<script") || lower.contains("<text") || lower.contains("<image")
                || lower.contains("<style") || lower.contains("<use") || lower.contains("<!entity")
                || lower.contains("<!doctype") || lower.contains("foreignobject")
                || lower.contains("javascript:") || withoutSvgNamespace.contains("http://")
                || withoutSvgNamespace.contains("https://")
                || lower.matches("(?s).*\\son[a-z]+\\s*=.*"))
            throw new IllegalArgumentException("SVG содержит запрещённые элементы");
        if (s.length() > 40_000) throw new IllegalArgumentException("SVG слишком большой");
    }

    static File emojiFile(Context context, String word, boolean english) {
        File svg = CardImageLoader.imageFile(context, word, english);
        return new File(svg.getParentFile(), CardImageLoader.key(word) + ".emoji");
    }

    static String savedEmoji(Context context, String word, boolean english) {
        File file = emojiFile(context, word, english);
        if (!file.isFile() || file.length() > 32 || file.length() == 0) return "";
        try (FileInputStream stream = new FileInputStream(file)) {
            byte[] b = new byte[(int) file.length()];
            int size = stream.read(b);
            return size > 0 ? new String(b, 0, size, StandardCharsets.UTF_8) : "";
        } catch (Exception ignored) { return ""; }
    }

    static String savePicture(Context context, String word, boolean english,
                              String russian, int category, String svg) throws Exception {
        String emoji = exactEmoji(word, russian, english, category);
        File target = emoji == null
                ? CardImageLoader.imageFile(context, word, english)
                : emojiFile(context, word, english);
        if (emoji == null) validateSvg(svg);
        File parent = target.getParentFile();
        if (!parent.exists() && !parent.mkdirs())
            throw new IllegalStateException("Не удалось создать папку картинок");
        try (FileOutputStream out = new FileOutputStream(target, false)) {
            out.write((emoji == null ? svg.trim() : emoji).getBytes(StandardCharsets.UTF_8));
        }
        return emoji == null ? "svg" : "emoji";
    }
}
