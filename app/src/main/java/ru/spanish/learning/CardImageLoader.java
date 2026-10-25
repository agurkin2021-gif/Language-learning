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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class CardImageLoader {
    private static final String BASE = "https://raw.githubusercontent.com/agurkin2021-gif/Language-learning/phone-builds/card-images/";

    private CardImageLoader() {}

    static String key(String spanish) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(spanish.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < 12; i++) out.append(String.format("%02x", hash[i]));
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
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

        target.loadUrl(BASE + key(spanish) + ".svg");
    }
}
