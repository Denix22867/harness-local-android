package org.dsh.mobile;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.*;

/** A separate loopback origin for HTML and its adjacent assets; no Harness cookies or bridge. */
final class HtmlPreviewServer implements Closeable {
    private static final String HOST = "127.0.0.2";
    private final ProjectFiles files;
    private final String directory, prefix;
    private final ServerSocket server;
    private final ExecutorService workers = new ThreadPoolExecutor(2, 4, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(16));
    private volatile boolean closed;
    HtmlPreviewServer(ProjectFiles files, String directory) throws IOException {
        this.files = files; this.directory = ProjectFiles.normalize(directory);
        prefix = "/" + UUID.randomUUID().toString() + "/";
        server = new ServerSocket(0, 16, InetAddress.getByName(HOST));
        Thread accept = new Thread(() -> {
            while (!closed) {
                try {
                    Socket client = server.accept(); client.setSoTimeout(5000);
                    try { workers.execute(() -> serve(client)); }
                    catch (RejectedExecutionException busy) { client.close(); }
                } catch (IOException ignored) { if (!closed) close(); }
            }
        }, "html-preview");
        accept.setDaemon(true); accept.start();
    }
    String url(String name) throws IOException {
        try { return origin() + prefix + new URI(null, null, name, null).toASCIIString(); }
        catch (URISyntaxException error) { throw new IOException(error); }
    }
    String origin() { return "http://" + HOST + ":" + server.getLocalPort(); }
    private static String line(InputStream input) throws IOException {
        ByteArrayOutputStream value = new ByteArrayOutputStream();
        int c;
        while ((c = input.read()) != -1 && c != '\n') {
            if (value.size() > 8192) throw new IOException("Header too long");
            if (c != '\r') value.write(c);
        }
        return value.toString("US-ASCII");
    }
    private void serve(Socket client) {
        try (Socket connection = client) {
            InputStream input = new BufferedInputStream(client.getInputStream());
            OutputStream output = new BufferedOutputStream(client.getOutputStream());
            String[] request = line(input).split(" ");
            if (request.length != 3) { response(output, 400, "Bad request"); return; }
            String host = "";
            for (int count = 0; ; count++) {
                if (count > 64) { response(output, 400, "Bad request"); return; }
                String header = line(input); if (header.isEmpty()) break;
                if (header.regionMatches(true, 0, "Host:", 0, 5)) host = header.substring(5).trim();
            }
            if (!host.equals(HOST + ":" + server.getLocalPort())) { response(output, 403, "Forbidden"); return; }
            if (!(request[0].equals("GET") || request[0].equals("HEAD"))) { response(output, 405, "Method not allowed"); return; }
            try {
                String path = URI.create(request[1]).getPath();
                if (path == null || !path.startsWith(prefix)) { response(output, 403, "Forbidden"); return; }
                File file = files.previewFile(directory, path.substring(prefix.length()));
                headers(output, 200, ProjectFiles.mime(path), file.length());
                if (!request[0].equals("HEAD")) {
                    try (InputStream content = new BufferedInputStream(new FileInputStream(file))) {
                        byte[] buffer = new byte[65536]; int n;
                        while ((n = content.read(buffer)) != -1) output.write(buffer, 0, n);
                    }
                }
                output.flush();
            } catch (IllegalArgumentException | IOException denied) { response(output, 404, "Not found"); }
        } catch (IOException ignored) { }
    }
    private static void headers(OutputStream output, int code, String type, long size) throws IOException {
        String text = "HTTP/1.1 " + code + " Response\r\nContent-Type: " + type + "\r\nContent-Length: " + size
            + "\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\nConnection: close\r\n\r\n";
        output.write(text.getBytes(StandardCharsets.US_ASCII));
    }
    private static void response(OutputStream output, int code, String message) throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        headers(output, code, "text/plain; charset=utf-8", bytes.length); output.write(bytes); output.flush();
    }
    @Override public void close() {
        closed = true; try { server.close(); } catch (IOException ignored) { }
        workers.shutdownNow();
    }
}
