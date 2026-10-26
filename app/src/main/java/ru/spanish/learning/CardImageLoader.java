package ru.spanish.learning;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.util.Base64;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.io.FileInputStream;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

final class CardImageLoader {
    private CardImageLoader() {}

    private static volatile Set<String> bundledSpanishImages;
    private static volatile Set<String> bundledEmojiPlaceholders;

    /**
     * A colored background with a Unicode emoji does not count as a real
     * illustration: many devices cannot render the emoji in an SVG WebView.
     * The shipped 950-card index identifies those existing placeholders.
     */
    static boolean hasIllustration(Context context, String word, boolean english) {
        File dynamic = imageFile(context, word, english);
        if (dynamic.isFile() && dynamic.length() > 100L) return true;
        if (english) return false;
        String asset = key(word) + ".svg";
        try {
            if (!bundledSpanishImages(context).contains(asset)) return false;
            try {
                return !bundledEmojiPlaceholders(context).contains(asset);
            } catch (IOException indexMissing) {
                // Do not regenerate the entire bundled dictionary if its
                // metadata index cannot be read on a particular phone.
                return true;
            }
        } catch (IOException unavailable) {
            return false;
        }
    }

    static boolean isBundledEmojiPlaceholder(Context context, String word) {
        try {
            return bundledEmojiPlaceholders(context).contains(key(word) + ".svg");
        } catch (IOException missing) {
            return false;
        }
    }

    private static Set<String> bundledEmojiPlaceholders(Context context) throws IOException {
        Set<String> cached = bundledEmojiPlaceholders;
        if (cached != null) return cached;
        synchronized (CardImageLoader.class) {
            if (bundledEmojiPlaceholders == null) {
                try (InputStream input = context.getAssets().open("card-images/index.json");
                     ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] bytes = new byte[4096];
                    for (int n; (n = input.read(bytes)) != -1; )
                        output.write(bytes, 0, n);
                    JSONObject index = new JSONObject(
                            new String(output.toByteArray(), StandardCharsets.UTF_8));
                    JSONArray entries = index.getJSONArray("cards");
                    Set<String> placeholders = new HashSet<>();
                    for (int i = 0; i < entries.length(); i++) {
                        JSONObject item = entries.getJSONObject(i);
                        String key = item.optString("key");
                        if (!key.isEmpty() && !item.optString("visual").isEmpty())
                            placeholders.add(key + ".svg");
                    }
                    bundledEmojiPlaceholders = Collections.unmodifiableSet(placeholders);
                } catch (JSONException invalid) {
                    throw new IOException("Invalid card image index", invalid);
                }
            }
            return bundledEmojiPlaceholders;
        }
    }

    private static Set<String> bundledSpanishImages(Context context) throws IOException {
        Set<String> cached = bundledSpanishImages;
        if (cached != null) return cached;
        synchronized (CardImageLoader.class) {
            if (bundledSpanishImages == null) {
                String[] names = context.getAssets().list("card-images");
                if (names == null) throw new IOException("Illustration index unavailable");
                bundledSpanishImages = Collections.unmodifiableSet(
                        new HashSet<>(Arrays.asList(names)));
            }
            return bundledSpanishImages;
        }
    }

    static String key(String spanish) {
        String normalized = Normalizer.normalize(spanish.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (normalized.isEmpty()) normalized = "card";
        return normalized + "-" + Integer.toUnsignedString(spanish.trim().toLowerCase(Locale.ROOT).hashCode(), 36);
    }

    /** English illustrations never share a directory with Spanish cards. */
    static File imageFile(Context context, String word, boolean english) {
        return new File(new File(context.getFilesDir(), english ? "card-images-en" : "card-images"),
                key(word) + ".svg");
    }

    static void load(Activity activity, WebView target, View fallback, String spanish, boolean english) {
        target.setVisibility(View.GONE);
        if (fallback != null) fallback.setVisibility(View.VISIBLE);

        byte[] svg;
        String imageKey = key(spanish);
        File dynamic = imageFile(activity, spanish, english);
        // The bundled SVGs are exclusively Spanish: never show one in English mode.
        if (english && !dynamic.isFile()) return;
        try (InputStream input = dynamic.isFile()
                    ? new FileInputStream(dynamic)
                    : activity.getAssets().open("card-images/" + imageKey + ".svg");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            svg = output.toByteArray();
        } catch (IOException missing) {
            return;
        }

        WebSettings settings = target.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        target.setBackgroundColor(Color.TRANSPARENT);
        target.setVerticalScrollBarEnabled(false);
        target.setHorizontalScrollBarEnabled(false);

        target.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                target.setVisibility(View.VISIBLE);
                if (fallback != null) fallback.setVisibility(View.GONE);
            }
        });

        String data = Base64.encodeToString(svg, Base64.NO_WRAP);
        String html = "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<style>html,body{margin:0;width:100%;height:100%;overflow:hidden;background:transparent}"
                + "body{display:flex;align-items:center;justify-content:center}"
                + "img{display:block;width:100%;height:100%;object-fit:contain}</style></head>"
                + "<body><img src=\"data:image/svg+xml;base64," + data + "\" alt=\"\"></body></html>";
        target.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
    }
}
