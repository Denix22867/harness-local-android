package org.dsh.mobile;

import java.io.*;
import java.nio.file.*;
import java.util.Locale;

/** Maps the two PRoot bind mounts without exposing the Android filesystem. */
final class ProjectFiles {
    private final File home, workspace, backing;
    ProjectFiles(File files) {
        home = new File(files, "home"); workspace = new File(files, "workspace");
        backing = new File(files, "runtime/.proot-hardlinks");
    }
    static boolean within(File path, File root) throws IOException {
        String p = path.getCanonicalPath(), r = root.getCanonicalPath();
        return p.equals(r) || p.startsWith(r + File.separator);
    }
    static String normalize(String path) throws IOException {
        if (path == null || path.indexOf('\0') >= 0 || !path.startsWith("/")) throw new IOException("Неверный путь");
        String value = Paths.get(path).normalize().toString();
        if (!(value.equals("/root") || value.startsWith("/root/") || value.equals("/workspace") || value.startsWith("/workspace/")))
            throw new IOException("Выберите файл из /root или /workspace");
        return value;
    }
    private File mapped(String guest) throws IOException {
        String path = normalize(guest);
        return path.equals("/root") || path.startsWith("/root/")
            ? new File(home, path.length() == 5 ? "" : path.substring(6))
            : new File(workspace, path.length() == 10 ? "" : path.substring(11));
    }
    File resolve(String guest) throws IOException {
        File nominal = mapped(guest);
        // PRoot stores emulated hardlinks as absolute host symlinks. They are
        // regular guest files, even though their canonical host path is outside
        // the bind mount. Never allow directory browsing through that exception.
        File file = nominal.getCanonicalFile();
        if (within(file, home) || within(file, workspace)) return file;
        if (within(file, backing) && file.isFile() && file.getName().startsWith(".l2s.")) return file;
        throw new IOException("Ссылка ведёт за пределы файлов проекта");
    }
    File previewFile(String directory, String relative) throws IOException {
        if (relative.indexOf('\0') >= 0 || relative.startsWith("/") || relative.indexOf('\\') >= 0) throw new IOException("Неверный путь");
        // Home-directory HTML must never serve private bridge/config/SSH files.
        String[] parts=relative.split("/");
        for (int i=0;i<parts.length;i++) if (parts[i].startsWith(".") && !parts[i].equals(".") && !parts[i].equals("..")) {
            if(i==parts.length-1&&isHtml(parts[i]))continue; // explicitly browsable HTML backups
            throw new IOException("Служебные файлы недоступны через HTTP-предпросмотр");
        }
        String root = normalize(directory), guest = normalize(root + "/" + relative);
        if (!guest.startsWith(root + "/")) throw new IOException("Файл вне папки HTML");
        File file = resolve(guest), dir = resolve(root);
        if (!within(file, dir) && !within(file, backing)) throw new IOException("Файл вне папки HTML");
        if (!file.isFile()) throw new FileNotFoundException(guest);
        return file;
    }
    static boolean isHtml(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".html") || lower.endsWith(".htm");
    }
    static String mime(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (isHtml(n)) return "text/html; charset=utf-8";
        if (n.endsWith(".css")) return "text/css; charset=utf-8";
        if (n.endsWith(".js") || n.endsWith(".mjs")) return "text/javascript; charset=utf-8";
        if (n.endsWith(".json")) return "application/json; charset=utf-8";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".ico")) return "image/x-icon";
        if (n.endsWith(".woff2")) return "font/woff2";
        if (n.endsWith(".woff")) return "font/woff";
        if (n.endsWith(".ttf")) return "font/ttf";
        if (n.endsWith(".wasm")) return "application/wasm";
        if (n.endsWith(".txt") || n.endsWith(".md")) return "text/plain; charset=utf-8";
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".zip")) return "application/zip";
        return "application/octet-stream";
    }
}
