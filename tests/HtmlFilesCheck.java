package org.dsh.mobile;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** Regression fixture for /root export mapping, PRoot write files, and preview boundaries. */
public final class HtmlFilesCheck {
    private static void write(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent()); Files.write(path,text.getBytes(StandardCharsets.UTF_8));
    }
    private static void denied(ProjectFiles files, String path) throws Exception {
        try { files.resolve(path); throw new AssertionError("Allowed: " + path); } catch (IOException expected) { }
    }
    private static void check(boolean condition) { if (!condition) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        Path base = Files.createTempDirectory("html-files-check");
        Path project = base.resolve("home/my project");
        write(project.resolve("index.html"),"<!doctype html><meta name=viewport content='width=device-width,initial-scale=1'><link rel=stylesheet href='style.css'><h1>HTML preview</h1><button id=click>Run JS</button><p id=result>waiting</p><script src='app.js'></script>");
        write(project.resolve("style.css"),"h1{color:rgb(12,34,56)} body{font-family:sans-serif;padding:16px}");
        write(project.resolve("app.js"),"document.querySelector('#click').onclick=async()=>{document.querySelector('#result').textContent=await(await fetch('data.json')).text()};");
        write(project.resolve("data.json"),"JS and fetch work");
        write(project.resolve("page #+ тест.html"),"<h1>Unicode filename</h1>");
        write(project.resolve(".backup.html"),"<h1>HTML backup</h1>");
        write(base.resolve("workspace/test.txt"),"workspace export");
        write(base.resolve("home/private.txt"),"outside project");
        write(base.resolve("home/.harness-browser-bridge.json"),"private endpoint");
        Path backing = base.resolve("runtime/.proot-hardlinks/.l2s.write.html0001.0001");
        write(backing,"<h1>Written via PRoot</h1>");
        Files.createSymbolicLink(project.resolve("write.html"),backing);
        Files.createSymbolicLink(project.resolve("escape.html"),base.resolve("home/private.txt"));
        Files.createSymbolicLink(project.resolve("escape-dir"),base.resolve("home"));
        ProjectFiles files = new ProjectFiles(base.toFile());
        check(files.resolve("/root/my project/index.html").isFile());
        check(files.resolve("/workspace/test.txt").isFile());
        check(files.previewFile("/root/my project",".backup.html").isFile());
        check(new String(Files.readAllBytes(files.resolve("/root/my project/write.html").toPath()),StandardCharsets.UTF_8).contains("PRoot"));
        denied(files,"/root/../../etc/passwd"); denied(files,"/etc/passwd");
        try { files.previewFile("/root",".harness-browser-bridge.json"); throw new AssertionError(); } catch (IOException expected) { }
        try { files.previewFile("/root/my project","escape.html"); throw new AssertionError(); } catch (IOException expected) { }
        try { files.previewFile("/root/my project","escape-dir/private.txt"); throw new AssertionError(); } catch (IOException expected) { }
        try { files.previewFile("/root/my project","../private.txt"); throw new AssertionError(); } catch (IOException expected) { }
        try (HtmlPreviewServer server = new HtmlPreviewServer(files,"/root/my project")) {
            HttpURLConnection html = (HttpURLConnection)new URL(server.url("write.html")).openConnection();
            check(html.getResponseCode()==200 && html.getContentType().startsWith("text/html")); html.disconnect();
            HttpURLConnection unicode = (HttpURLConnection)new URL(server.url("page #+ тест.html")).openConnection();
            check(unicode.getResponseCode()==200); unicode.disconnect();
            HttpURLConnection missingToken = (HttpURLConnection)new URL(server.origin()+"/index.html").openConnection();
            check(missingToken.getResponseCode()==403); missingToken.disconnect();
            System.out.println("PASS /root and /workspace exports, PRoot write files, Unicode names, preview boundaries");
            if (args.length>0 && args[0].equals("--browser")) {
                System.out.println("PREVIEW_URL="+server.url("index.html")); System.out.flush(); System.in.read();
            }
        }
    }
}
