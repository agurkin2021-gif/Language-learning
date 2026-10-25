package ru.spanish.learning;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.widget.ImageView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class CardImageLoader {
    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final int MAX_BYTES = 256 * 1024;
    private static final String BASE = "https://raw.githubusercontent.com/agurkin2021-gif/Language-learning/phone-builds/card-images/";

    private CardImageLoader() {}

    static String key(String spanish) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(spanish.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < 12; i++) out.append(String.format("%02x", hash[i]));
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static void load(Activity activity, ImageView target, String spanish) {
        target.setVisibility(View.GONE);
        String key = key(spanish);
        File dir = new File(activity.getCacheDir(), "card-images");
        File file = new File(dir, key + ".webp");
        IO.execute(() -> {
            Bitmap bitmap = decode(file);
            if (bitmap == null) {
                bitmap = download(file, key);
            }
            Bitmap ready = bitmap;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (ready == null) {
                    target.setVisibility(View.GONE);
                } else {
                    target.setImageBitmap(ready);
                    target.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    private static Bitmap decode(File file) {
        if (!file.isFile() || file.length() <= 0 || file.length() > MAX_BYTES) return null;
        return BitmapFactory.decodeFile(file.getAbsolutePath());
    }

    private static Bitmap download(File file, String key) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(BASE + key + ".webp").openConnection();
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "SpanishLearning/1");
            if (connection.getResponseCode() != 200) return null;
            int length = connection.getContentLength();
            if (length > MAX_BYTES) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = connection.getInputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                int total = 0;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_BYTES) return null;
                    out.write(buffer, 0, read);
                }
            }
            byte[] bytes = out.toByteArray();
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap == null) return null;
            file.getParentFile().mkdirs();
            try (FileOutputStream fos = new FileOutputStream(file)) { fos.write(bytes); }
            return bitmap;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
