package ru.spanish.learning;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Sherpa-onnx v1.13.8 needs an on-disk espeak-ng-data directory.
 * AssetManager paths are not valid filesystem paths for the native phonemizer.
 * Extract once on a worker thread; an incomplete copy has no ready marker.
 */
final class DanielaAssets {
    private static final String VERSION = "piper-daniela-espeak-v1";
    private DanielaAssets() {}

    static String ensurePhonemeDirectory(Context context) throws IOException {
        File root = new File(context.getFilesDir(), VERSION);
        File data = new File(root, "espeak-ng-data");
        File marker = new File(root, ".complete");
        File phontab = new File(data, "phontab");
        if (marker.isFile() && phontab.isFile() && phontab.length() > 0) {
            return data.getAbsolutePath();
        }
        if (!data.isDirectory() && !data.mkdirs()) {
            throw new IOException("Не удалось создать каталог фонетических данных");
        }
        copyAssetTree(context.getAssets(), DanielaVoiceOption.DATA_DIRECTORY, data);
        if (!phontab.isFile() || phontab.length() == 0) {
            throw new IOException("Фонетическая таблица Piper Daniela отсутствует");
        }
        try (FileOutputStream output = new FileOutputStream(marker)) {
            output.write(VERSION.getBytes(StandardCharsets.US_ASCII));
            output.getFD().sync();
        }
        return data.getAbsolutePath();
    }

    private static void copyAssetTree(AssetManager manager, String assetPath, File destination)
            throws IOException {
        String[] children = manager.list(assetPath);
        if (children == null) throw new IOException("Не удалось прочитать " + assetPath);
        if (children.length != 0) {
            if (!destination.isDirectory() && !destination.mkdirs()) {
                throw new IOException("Не удалось создать каталог " + destination.getName());
            }
            for (String child : children) {
                if (child.isEmpty() || child.equals(".") || child.equals("..")
                        || child.contains("/") || child.contains("\\")) {
                    throw new IOException("Недопустимое имя ресурса");
                }
                copyAssetTree(manager, assetPath + "/" + child, new File(destination, child));
            }
            return;
        }
        try (InputStream in = manager.open(assetPath);
             FileOutputStream out = new FileOutputStream(destination, false)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
            out.getFD().sync();
        }
    }
}
