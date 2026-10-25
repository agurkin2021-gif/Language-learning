package ru.spanish.learning;

import android.app.Activity;
import android.graphics.Color;
import android.net.http.SslError;
import android.view.View;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.text.Normalizer;
import java.util.Locale;

final class CardImageLoader {
    private static final String BASE = "https://raw.githubusercontent.com/agurkin2021-gif/Language-learning/card-assets/card-images/";

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
        if (BuildConfig.DEBUG) return;

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
            private boolean failed;

            private void fail() {
                failed = true;
                target.setVisibility(View.GONE);
                if (fallback != null) fallback.setVisibility(View.VISIBLE);
            }

            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request.isForMainFrame() && response.getStatusCode() >= 400) fail();
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) fail();
            }

            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                fail();
            }

            @Override public void onPageFinished(WebView view, String url) {
                if (failed || activity.isFinishing() || activity.isDestroyed()) return;
                target.setVisibility(View.VISIBLE);
                if (fallback != null) fallback.setVisibility(View.GONE);
            }
        });

        String imageUrl = BASE + key(spanish) + ".svg";
        String html = "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<style>html,body{margin:0;width:100%;height:100%;overflow:hidden;background:transparent}"
                + "body{display:flex;align-items:center;justify-content:center}"
                + "img{display:block;width:100%;height:100%;object-fit:contain}</style></head>"
                + "<body><img src=\"" + imageUrl + "\" alt=\"\"></body></html>";
        target.loadDataWithBaseURL(BASE, html, "text/html", "UTF-8", null);
    }
}
