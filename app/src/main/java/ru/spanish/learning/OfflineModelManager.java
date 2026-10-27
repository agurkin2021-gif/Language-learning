package ru.spanish.learning;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import java.io.File;

/**
 * A model is downloaded once into the app's own files folder, never the APK.
 * Downloads are run by Android DownloadManager and survive activity restarts.
 */
public final class OfflineModelManager {
    private static final String PREFS = "offline_story_model";
    private static final String ID = "download_id";
    private static final String NAME = "qwen2_5_0_5b_q8_1280.task";
    // Public model, Apache-2.0; chosen CPU-compatible MediaPipe .task asset.
    public static final String SOURCE =
            "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/" +
            "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task?download=true";
    public static final long MIN_BYTES = 400L * 1024 * 1024;
    private static final long MAX_BYTES = 800L * 1024 * 1024;

    public record State(boolean ready, boolean downloading, int percent, String message) {}
    private OfflineModelManager() {}

    public static File file(Context context) {
        File dir = context.getExternalFilesDir("models");
        if (dir == null) throw new IllegalStateException("Нет доступного хранилища для модели");
        return new File(dir, NAME);
    }

    public static boolean ready(Context context) {
        File f = file(context);
        return f.isFile() && f.length() >= MIN_BYTES && f.length() <= MAX_BYTES;
    }

    private static long pendingId(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(ID, -1);
    }

    public static State status(Context context) {
        if (ready(context)) return new State(true, false, 100, "Локальная модель готова • работает без интернета");
        long id = pendingId(context);
        if (id == -1) return new State(false, false, 0, "Нужно один раз скачать модель (~550 МБ)");
        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager == null) return new State(false, false, 0, "Системная загрузка Android недоступна");
        try (Cursor c = manager.query(new DownloadManager.Query().setFilterById(id))) {
            if (c == null || !c.moveToFirst())
                return new State(false, false, 0, "Загрузка отсутствует • можно начать заново");
            int status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            long bytes = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            long total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            int percent = total > 0 ? (int) Math.min(99, 100 * bytes / total) : 0;
            if (status == DownloadManager.STATUS_SUCCESSFUL)
                return new State(false, false, 0, "Файл модели получен, но его размер неверный");
            if (status == DownloadManager.STATUS_FAILED)
                return new State(false, false, percent, "Загрузка не удалась. Проверьте интернет и повторите");
            return new State(false, true, percent,
                    status == DownloadManager.STATUS_PAUSED
                            ? "Загрузка ожидает сети • " + percent + "%"
                            : "Скачиваю модель • " + percent + "%");
        } catch (Exception e) {
            return new State(false, false, 0, "Ошибка проверки загрузки: " + e.getClass().getSimpleName());
        }
    }

    /** Call only after the owner explicitly confirms the model size and source. */
    public static void download(Context context) {
        if (ready(context)) return;
        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager == null) throw new IllegalStateException("Загрузчик Android недоступен");
        long previousId = pendingId(context);
        if (previousId != -1) manager.remove(previousId);
        File f = file(context);
        if (f.exists() && !f.delete()) throw new IllegalStateException("Не удалось заменить старый файл");
        // DownloadManager persists the download even if the app is closed.
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(SOURCE))
                .setTitle("Локальная модель для рассказов")
                .setDescription("Qwen2.5 0.5B • после загрузки работает офлайн")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverRoaming(false)
                .setAllowedOverMetered(true)
                .setDestinationInExternalFilesDir(context, "models", NAME);
        long id = manager.enqueue(request);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(ID, id).apply();
    }
}
