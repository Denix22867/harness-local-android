package org.dsh.mobile;

import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import java.util.regex.*;

/** Resumable, cookie-aware transfer; only verified bytes become an installable runtime. */
final class CoreDownloader {
    interface Listener {
        void progress(long received, long total);
        void message(String text);
    }
    private volatile boolean cancelled;
    private volatile HttpURLConnection active;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);

    void cancel() {
        cancelled = true;
        HttpURLConnection connection = active;
        if (connection != null) connection.disconnect();
    }

    private void checkCancelled() throws InterruptedIOException {
        if (cancelled || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Загрузка остановлена");
    }

    private static void validateUrl(URL url) throws IOException {
        if ("https".equals(url.getProtocol())) return;
        if ("http".equals(url.getProtocol()) && ("127.0.0.1".equals(url.getHost()) || "localhost".equals(url.getHost()))) return;
        throw new IOException("Источник ядра должен использовать HTTPS");
    }

    File download(String address, long expectedSize, String expectedHash, File directory, Listener listener) throws IOException {
        if (expectedSize <= 0 || !expectedHash.matches("[0-9a-f]{64}")) throw new IOException("Неверные параметры ядра");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Не удалось создать каталог загрузки");
        File complete = new File(directory, "runtime.zip");
        File partial = new File(directory, "runtime.zip.part");
        if (complete.exists()) {
            listener.message("Проверка скачанного ядра…");
            if (verify(complete, expectedSize, expectedHash)) return complete;
            if (!complete.delete()) throw new IOException("Не удалось удалить повреждённый архив");
        }
        URL original = new URL(address);
        validateUrl(original);
        for (int attempt = 0; attempt < 5; attempt++) {
            checkCancelled();
            if (partial.length() > expectedSize && !partial.delete()) throw new IOException("Некорректная частичная загрузка");
            try {
                if (partial.length() < expectedSize) transfer(original, partial, expectedSize, listener);
                listener.message("Проверка SHA-256 ядра…");
                if (!verify(partial, expectedSize, expectedHash)) {
                    partial.delete();
                    throw new IOException("Скачанное ядро не прошло проверку SHA-256");
                }
                if (!partial.renameTo(complete)) throw new IOException("Не удалось сохранить проверенное ядро");
                return complete;
            } catch (IOException error) {
                checkCancelled();
                if (attempt == 4) throw error;
                listener.message("Связь прервана. Докачка, попытка " + (attempt + 2) + "/5…");
                try { Thread.sleep(Math.min(4000, 1000L * (attempt + 1))); }
                catch (InterruptedException stopped) { Thread.currentThread().interrupt(); checkCancelled(); }
            }
        }
        throw new IOException("Загрузка не завершена");
    }

    private void transfer(URL original, File partial, long expectedSize, Listener listener) throws IOException {
        URL url = original;
        for (int hop = 0; hop < 12; hop++) {
            checkCancelled();
            validateUrl(url);
            long offset = partial.length();
            HttpURLConnection connection = (HttpURLConnection)url.openConnection();
            active = connection;
            try {
                connection.setConnectTimeout(20000); connection.setReadTimeout(30000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("Accept", "application/octet-stream");
                connection.setRequestProperty("Accept-Encoding", "identity");
                if (offset > 0) connection.setRequestProperty("Range", "bytes=" + offset + "-");
                URI uri;
                try { uri = url.toURI(); }
                catch (URISyntaxException e) { throw new IOException("Неверный адрес ядра"); }
                for (Map.Entry<String,List<String>> header : cookies.get(uri, Collections.emptyMap()).entrySet()) {
                    connection.setRequestProperty(header.getKey(), String.join("; ", header.getValue()));
                }
                int code = connection.getResponseCode();
                cookies.put(uri, connection.getHeaderFields());
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IOException("Сервер не указал адрес скачивания");
                    URL next = new URL(url, location);
                    if ("https".equals(url.getProtocol()) && !"https".equals(next.getProtocol())) throw new IOException("Небезопасное перенаправление");
                    url = next;
                    continue;
                }
                String type = connection.getContentType();
                if (code == 200 && type != null && type.toLowerCase(Locale.ROOT).startsWith("text/html")) {
                    // Filebin sets a verification cookie on its first HTML response.
                    continue;
                }
                if (code == 404 || code == 410) throw new IOException("Архив ядра недоступен: срок хранения ссылки мог истечь");
                if (code != 200 && code != 206) throw new IOException("Сервер ядра ответил HTTP " + code);
                boolean append = code == 206;
                long responseBytes;
                if (append) {
                    String range = connection.getHeaderField("Content-Range");
                    Matcher match = Pattern.compile("bytes (\\d+)-(\\d+)/(\\d+)").matcher(range == null ? "" : range);
                    if (!match.matches() || Long.parseLong(match.group(1)) != offset || Long.parseLong(match.group(3)) != expectedSize)
                        throw new IOException("Сервер вернул неправильный диапазон докачки");
                    long end = Long.parseLong(match.group(2));
                    if (end < offset || end >= expectedSize) throw new IOException("Неверный конец диапазона");
                    responseBytes = end - offset + 1;
                } else {
                    offset = 0;
                    responseBytes = expectedSize;
                }
                long length = connection.getContentLengthLong();
                if (length >= 0 && length != responseBytes) throw new IOException("Размер ответа не совпадает с размером ядра");
                listener.progress(offset, expectedSize);
                long received = offset;
                long lastUpdate = 0;
                try (InputStream input = new BufferedInputStream(connection.getInputStream()); OutputStream output = new BufferedOutputStream(new FileOutputStream(partial, append))) {
                    byte[] buffer = new byte[65536]; int n;
                    while ((n = input.read(buffer)) != -1) {
                        checkCancelled();
                        if (received + n > offset + responseBytes) throw new IOException("Ответ превышает ожидаемый размер");
                        output.write(buffer, 0, n); received += n;
                        if (System.currentTimeMillis() - lastUpdate > 250) {
                            listener.progress(received, expectedSize); lastUpdate = System.currentTimeMillis();
                        }
                    }
                }
                listener.progress(received, expectedSize);
                if (received != expectedSize) throw new EOFException("Загрузка прервана; получено " + received + " из " + expectedSize + " байт");
                return;
            } finally {
                connection.disconnect();
                if (active == connection) active = null;
            }
        }
        throw new IOException("Источник не предоставил архив после перенаправлений");
    }

    private boolean verify(File file, long size, String expectedHash) throws IOException {
        if (file.length() != size) return false;
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IOException(e); }
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[65536]; int n;
            while ((n = input.read(buffer)) != -1) { checkCancelled(); digest.update(buffer, 0, n); }
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) hex.append(String.format(Locale.ROOT, "%02x", b & 255));
        return hex.toString().equals(expectedHash);
    }
}
