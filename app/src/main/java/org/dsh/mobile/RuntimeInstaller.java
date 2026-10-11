package org.dsh.mobile;

import android.content.Context;
import android.system.Os;
import java.io.*;
import java.util.zip.*;
import org.json.*;

/** Installs packaged ARM64 files into private app storage; user data lives separately. */
final class RuntimeInstaller {
    static final String VERSION = "dsh-0.2.0-rc.2-node-24.9.0-v1";

    private static File safeChild(File root, String path) throws IOException {
        if (path.startsWith("/") || path.contains("\\")) throw new IOException("Invalid archive path");
        File child = new File(root, path);
        if (!child.getCanonicalPath().startsWith(root.getCanonicalPath() + "/"))
            throw new IOException("Archive path escapes runtime");
        return child;
    }

    private static void deleteTree(File file) {
        // Never follow links: the failed staging tree may contain guest absolute links.
        try {
            if (android.system.OsConstants.S_ISLNK(Os.lstat(file.getPath()).st_mode)) {
                file.delete();
                return;
            }
        } catch (Exception ignored) { return; }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        file.delete();
    }

    private static ZipInputStream runtimeStream(File archive, JSONObject spec) throws Exception {
        InputStream input = new BufferedInputStream(new FileInputStream(archive));
        try {
            JSONArray nested = spec.optJSONArray("nested");
            if (nested != null) for (int i = 0; i < nested.length(); i++) {
                ZipInputStream layer = new ZipInputStream(input);
                input = layer;
                String wanted = nested.getString(i);
                ZipEntry entry;
                boolean found = false;
                while ((entry = layer.getNextEntry()) != null) {
                    if (wanted.equals(entry.getName())) { found = true; break; }
                }
                if (!found) throw new IOException("В архиве отсутствует " + wanted);
            }
            return new ZipInputStream(input);
        } catch (Exception error) { input.close(); throw error; }
    }

    static File install(Context context, CoreDownloader downloader) throws Exception {
        File root = new File(context.getFilesDir(), "runtime");
        File stamp = new File(root, ".mobile-version");
        if (stamp.exists()) {
            try (BufferedReader r = new BufferedReader(new FileReader(stamp))) {
                if (VERSION.equals(r.readLine())) return root;
            }
        }
        File pending = new File(context.getFilesDir(), "runtime.pending");
        deleteTree(pending);
        if (!pending.mkdirs()) throw new IOException("Не удалось создать каталог ядра");
        byte[] buffer = new byte[65536];
        JSONObject spec;
        try (InputStream input = context.getAssets().open("runtime-download.json"); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            int n; while ((n = input.read(buffer)) != -1) out.write(buffer, 0, n);
            spec = new JSONObject(out.toString("UTF-8"));
        }
        File incoming = new File(context.getFilesDir(), "incoming");
        long saved = Math.min(spec.getLong("bytes"), new File(incoming, "runtime.zip.part").length());
        if (new File(incoming, "runtime.zip").exists()) saved = spec.getLong("bytes");
        long required = spec.getLong("expanded_bytes") + spec.getLong("bytes") - saved + 128L * 1024 * 1024;
        long available = new android.os.StatFs(context.getFilesDir().getPath()).getAvailableBytes();
        if (available < required) throw new IOException("Для загрузки и установки нужно около 2 ГБ свободного места");
        RuntimeService.log("Первый запуск: загрузка ядра, затем распаковка. Прерванную загрузку можно продолжить.");
        File archive = downloader.download(spec.getString("url"), spec.getLong("bytes"), spec.getString("sha256"), incoming, new CoreDownloader.Listener() {
            @Override public void progress(long received, long total) {
                RuntimeService.progress = (int)(received * 1000 / total);
                RuntimeService.status = "Скачивание ядра: " + (received * 100 / total) + "% (" + (received / 1048576) + "/" + (total / 1048576) + " МБ)";
            }
            @Override public void message(String text) { RuntimeService.status = text; RuntimeService.log(text); }
        });
        RuntimeService.progress = -1;
        RuntimeService.status = "Распаковка ядра…";
        RuntimeService.log("SHA-256 подтверждён. Распаковка ядра…");
        int count = 0;
        try (ZipInputStream zip = runtimeStream(archive, spec)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                File dest = safeChild(pending, entry.getName());
                if (entry.isDirectory()) {
                    if (!dest.isDirectory() && !dest.mkdirs()) throw new IOException("mkdir " + dest);
                } else {
                    File parent = dest.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("mkdir " + parent);
                    try (OutputStream out = new BufferedOutputStream(new FileOutputStream(dest))) {
                        int n;
                        while ((n = zip.read(buffer)) != -1) out.write(buffer, 0, n);
                    }
                }
                if (++count % 3000 == 0) { RuntimeService.status = "Распаковка: " + count + " файлов"; RuntimeService.log(RuntimeService.status); }
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            }
        }
        String json;
        try (InputStream input = context.getAssets().open("runtime-manifest.json"); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            int n;
            while ((n = input.read(buffer)) != -1) out.write(buffer, 0, n);
            json = out.toString("UTF-8");
        }
        JSONArray entries = new JSONArray(json);
        // Permissions first, links last, so archive writes cannot traverse a symlink.
        for (int i = 0; i < entries.length(); i++) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            JSONObject entry = entries.getJSONObject(i);
            if (entry.has("link")) continue;
            File dest = safeChild(pending, entry.getString("path"));
            Os.chmod(dest.getPath(), entry.getInt("mode"));
        }
        for (int i = 0; i < entries.length(); i++) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            JSONObject entry = entries.getJSONObject(i);
            if (!entry.has("link")) continue;
            File dest = new File(pending, entry.getString("path"));
            File parent = dest.getParentFile();
            // Manifest is build-owned; parents must already be real directories.
            if (!parent.isDirectory()) throw new IOException("Missing link parent");
            Os.symlink(entry.getString("link"), dest.getPath());
        }
        try (Writer out = new FileWriter(new File(pending, ".mobile-version"))) { out.write(VERSION + "\n"); }
        deleteTree(root);
        if (!pending.renameTo(root)) throw new IOException("Не удалось завершить установку ядра");
        archive.delete();
        RuntimeService.log("Ядро установлено.");
        return root;
    }
}
