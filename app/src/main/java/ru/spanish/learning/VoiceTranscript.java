package ru.spanish.learning;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative speech transcript handling: never silently change meaningful commands. */
final class VoiceTranscript {
    private VoiceTranscript() {}

    static List<String> candidates(List<String> variants) {
        List<String> result = new ArrayList<>();
        if (variants == null) return result;
        for (String variant : variants) {
            if (variant == null) continue;
            String clean = variant.replace('\r',' ').replace('\n',' ')
                    .trim().replaceAll("\\s+", " ");
            if (clean.isEmpty() || clean.length() > 600) continue;
            boolean seen = false;
            for (String previous : result) {
                if (previous.equalsIgnoreCase(clean)) { seen = true; break; }
            }
            if (!seen) result.add(clean);
            if (result.size() == 5) break;
        }
        return result;
    }

    static String modelReply(String response) {
        if (response == null) return "";
        String text = response.trim();
        if (text.isEmpty() || text.length() > 600 || text.contains("\n")
                || text.startsWith("Исправленный текст:")) return "";
        if (text.length() > 2 && text.startsWith("\"") && text.endsWith("\""))
            text = text.substring(1, text.length() - 1);
        return text.trim();
    }

    static boolean safeToAutoSend(String original, String corrected, float confidence) {
        if (original == null || corrected == null || original.isBlank() || corrected.isBlank()) return false;
        if (confidence >= 0 && confidence < 0.56f) return false;
        if (corrected.length() > original.length() * 1.27f + 10) return false;
        String a = normalize(original), b = normalize(corrected);
        if (a.equals(b)) return true;
        if (!extracted("\\d+", original).equals(extracted("\\d+", corrected))) return false;
        for (String negative : new String[]{"не", "нет", "ни", "никогда", "no", "nunca", "sin"}) {
            if (hasWord(a, negative) != hasWord(b, negative)) return false;
        }
        if (!extracted("[A-Za-zÁÉÍÓÚÜÑáéíóúüñ]+", original)
                .equals(extracted("[A-Za-zÁÉÍÓÚÜÑáéíóúüñ]+", corrected))) return false;
        if (a.length() < 10 || b.length() < 10) return false;
        return editDistance(a,b) <= Math.max(1, Math.round(a.length() * 0.14f));
    }

    private static String normalize(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFC).toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{P}\\p{S}]", " ").replaceAll("\\s+", " ").trim();
    }
    private static boolean hasWord(String s, String word) {
        return (" " + s + " ").contains(" " + word + " ");
    }
    private static List<String> extracted(String regex, String s) {
        Matcher matcher=Pattern.compile(regex).matcher(s);
        List<String> words=new ArrayList<>();
        while(matcher.find()) words.add(matcher.group().toLowerCase(Locale.ROOT));
        return words;
    }
    private static int editDistance(String a, String b) {
        int[] previous=new int[b.length()+1], next=new int[b.length()+1];
        for(int j=0;j<=b.length();j++) previous[j]=j;
        for(int i=1;i<=a.length();i++){
            next[0]=i;
            for(int j=1;j<=b.length();j++) {
                int change=a.charAt(i-1)==b.charAt(j-1)?0:1;
                next[j]=Math.min(Math.min(previous[j]+1,next[j-1]+1),previous[j-1]+change);
            }
            int[] tmp=previous;previous=next;next=tmp;
        }
        return previous[b.length()];
    }
}
