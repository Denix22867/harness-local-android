package org.dsh.mobile;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Owns the local PRoot process and a user-visible foreground notification. */
public final class RuntimeService extends Service {
    static final String URL = "http://127.0.0.1:3080/";
    static volatile String status = "Готов к запуску";
    static volatile boolean ready = false;
    static volatile boolean running = false;
    static volatile String launchUrl = URL;
    static volatile int progress = -1;
    private static final StringBuilder LOG = new StringBuilder();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile java.lang.Process process;
    static volatile java.lang.Process activeProcess;
    static boolean hasLiveProcess() { java.lang.Process p = activeProcess; return p != null && p.isAlive(); }
    private volatile boolean stopping;
    private PowerManager.WakeLock wakeLock;
    private final CoreDownloader downloader = new CoreDownloader();

    static synchronized void log(String line) {
        if (LOG.length() > 160000) LOG.delete(0, LOG.length() - 120000);
        LOG.append(line).append('\n');
    }
    static synchronized String logs() { return LOG.toString(); }

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("runtime", "Локальное ядро Harness", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, RuntimeService.class).setAction("STOP"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        startForeground(1, new Notification.Builder(this, "runtime").setSmallIcon(org.dsh.mobile.R.drawable.icon)
            .setContentTitle("Harness Local").setContentText("Загрузка и работа локального ядра")
            .setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null, "Остановить", stop).build()).build());
        wakeLock = ((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HarnessLocal:runtime");
        wakeLock.acquire();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) { status = "Ядро остановлено"; stopSelf(); return START_NOT_STICKY; }
        if (!running) {
            running = true;
            ready = false;
            launchUrl = URL;
            stopping = false;
            worker.submit(this::boot);
        }
        return START_NOT_STICKY;
    }

    private void boot() {
        try {
            status = "Подготовка ядра…";
            if (!Arrays.asList(android.os.Build.SUPPORTED_ABIS).contains("arm64-v8a")) throw new IOException("Для локального ядра нужен телефон ARM64. ABI: " + Arrays.toString(android.os.Build.SUPPORTED_ABIS));
            log("Android " + android.os.Build.VERSION.RELEASE + "; " + android.os.Build.MODEL + "; ABI " + Arrays.toString(android.os.Build.SUPPORTED_ABIS));
            File root = RuntimeInstaller.install(this, downloader);
            RuntimePatches.apply(this, root);
            if (stopping) return;
            File home = new File(getFilesDir(), "home");
            File workspace = new File(getFilesDir(), "workspace");
            File temp = new File(getFilesDir(), "tmp");
            File libs = new File(getFilesDir(), "native");
            // PRoot hardlink backing files must outlive temporary staging folders.
            File linkStore = new File(root, ".proot-hardlinks");
            for (File dir : new File[]{home, workspace, temp, libs, linkStore}) if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("mkdir " + dir);
            File talloc = new File(libs, "libtalloc.so.2");
            // Android changes nativeLibraryDir on APK updates. File.exists() follows
            // the old dangling link and reports false, although its name still exists.
            // Atomically replace the link with this installation's library path.
            File pendingLink = new File(libs, ".libtalloc.so.2.next");
            pendingLink.delete(); // delete the link itself, including a dangling one
            android.system.Os.symlink(getApplicationInfo().nativeLibraryDir + "/libtalloc.so", pendingLink.getPath());
            android.system.Os.rename(pendingLink.getPath(), talloc.getPath());
            try (Writer out = new FileWriter(new File(root, "etc/resolv.conf"))) {
                android.net.ConnectivityManager network = (android.net.ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
                android.net.LinkProperties link = network.getLinkProperties(network.getActiveNetwork());
                List<InetAddress> dns = link == null ? Collections.emptyList() : link.getDnsServers();
                if (dns.isEmpty()) out.write("nameserver 1.1.1.1\nnameserver 8.8.8.8\n");
                else for (InetAddress server : dns) out.write("nameserver " + server.getHostAddress() + "\n");
            }
            String nativeDir = getApplicationInfo().nativeLibraryDir;
            List<String> args = Arrays.asList(nativeDir + "/libproot.so", "--kill-on-exit", "--link2symlink", "-0", "-r", root.getPath(),
                "-b", "/dev", "-b", "/proc", "-b", "/sys", "-b", home.getPath() + ":/root", "-b", workspace.getPath() + ":/workspace", "-w", "/workspace",
                "/usr/bin/env", "-i", "HOME=/root", "PATH=/usr/local/bin:/usr/bin:/bin", "LANG=C.UTF-8", "TERM=xterm-256color", "TMPDIR=/tmp", "DSH_HOME=/root/.dsh", "UV_USE_IO_URING=0",
                "/usr/local/bin/node", "/opt/dsh/android-bootstrap.mjs", "web", "--patch", "/opt/dsh/android.patch.yml", "--no-open", "--host", "127.0.0.1", "--port", "3080");
            // PRoot ignores SIGTERM. The shell supervisor translates stop into
            // SIGQUIT, which makes PRoot terminate all traced child processes.
            List<String> supervised = new ArrayList<>();
            supervised.add("/system/bin/sh"); supervised.add("-c");
            supervised.add("trap 'kill -QUIT \"$child\" 2>/dev/null; wait \"$child\" 2>/dev/null; exit 0' TERM INT; \"$@\" & child=$!; wait \"$child\"; result=$?; exit \"$result\"");
            supervised.add("harness-local"); supervised.addAll(args);
            ProcessBuilder builder = new ProcessBuilder(supervised).redirectErrorStream(true);
            Map<String,String> env = builder.environment();
            env.put("LD_LIBRARY_PATH", libs.getPath() + ":" + nativeDir);
            env.put("PROOT_LOADER", nativeDir + "/libproot-loader.so");
            env.put("PROOT_TMP_DIR", temp.getPath());
            env.put("PROOT_L2S_DIR", linkStore.getPath());
            env.put("PROOT_NO_SECCOMP", "1");
            env.remove("LD_PRELOAD");
            status = "Запуск Harness…";
            log("Запуск локального Harness " + RuntimeInstaller.VERSION);
            process = builder.start();
            activeProcess = process;
            if (stopping) { process.destroy(); return; }
            Thread reader = new Thread(() -> {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = in.readLine()) != null) {
                        int start = line.indexOf(URL + "?token=");
                        if (start >= 0) launchUrl = line.substring(start).trim().split("\\s+")[0];
                        log(line.replaceAll("([?&]token=)[^\\s&]+", "$1[скрыт]"));
                    }
                } catch (IOException e) { if (!stopping) log(e.toString()); }
            }, "harness-output");
            reader.start();
            long deadline = System.currentTimeMillis() + 120000;
            boolean slow = false;
            while (!stopping && process.isAlive()) {
                try {
                    HttpURLConnection conn = (HttpURLConnection)new URL(launchUrl).openConnection();
                    conn.setConnectTimeout(1000); conn.setReadTimeout(1000);
                    conn.setInstanceFollowRedirects(false);
                    try {
                        int response = conn.getResponseCode();
                        if (response == 200 || ((response == 302 || response == 303) && launchUrl.contains("?token=") && "./".equals(conn.getHeaderField("Location")))) {
                            ready = true; progress = -1; status = "Работает на телефоне";
                            log("Harness готов. Локальный интерфейс доступен.");
                            break;
                        }
                    } finally { conn.disconnect(); }
                } catch (IOException ignored) { }
                if (!slow && System.currentTimeMillis() > deadline) {
                    slow = true;
                    status = "Запуск занимает больше времени — см. журнал";
                    log("HTTP-интерфейс ещё не готов; ожидание продолжается.");
                }
                Thread.sleep(500);
            }
            int exit = process.waitFor();
            ready = false;
            if (!stopping) { status = "Ядро завершилось: " + exit; log(status); stopSelf(); }
        } catch (Exception e) {
            if (!stopping) { status = "Ошибка запуска — см. журнал"; log(e.toString()); stopSelf(); }
        } finally { running = false; }
    }

    @Override public void onDestroy() {
        stopping = true;
        ready = false;
        progress = -1;
        downloader.cancel();
        if (!status.startsWith("Ошибка") && !status.startsWith("Ядро завершилось")) status = "Ядро остановлено";
        java.lang.Process child = process;
        if (child != null) child.destroy();
        worker.shutdownNow();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        running = false;
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
