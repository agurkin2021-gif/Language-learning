package ru.spanish.learning;

import android.app.Activity;
import android.graphics.Color;
import android.util.Base64;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.Locale;

final class CardImageLoader {
    private CardImageLoader() {}

    static String key(String spanish) {
        String normalized = Normalizer.normalize(spanish.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (normalized.isEmpty()) normalized = "card";
        return normalized + "-" + Integer.toUnsignedString(spanish.trim().toLowerCase(Locale.ROOT).hashCode(), 36);
    }

    static void load(Activity activity, WebView target, View fallback, String spanish) {
        target.setVisibility(View.GONE);
        if (fallback != null) fallback.setVisibility(View.VISIBLE);

        byte[] svg;
        String asset = "card-images/" + key(spanish) + ".svg";
        try (InputStream input = activity.getAssets().open(asset);
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
