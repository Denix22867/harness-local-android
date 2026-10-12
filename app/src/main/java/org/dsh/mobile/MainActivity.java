package org.dsh.mobile;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.graphics.Color;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.io.*;

/** Touch shell for the upstream local Web UI, with startup diagnostics and file import. */
public final class MainActivity extends Activity {
    private WebView web;
    private TextView status, journal;
    private ScrollView logs;
    private ProgressBar progress;
    private boolean loaded;
    private String mobileJavascript;
    private ValueCallback<Uri[]> chooser;
    private String exportingPath;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!android.text.TextUtils.equals(status.getText(), RuntimeService.status)) status.setText(RuntimeService.status);
            status.setVisibility(RuntimeService.ready && logs.getVisibility() != View.VISIBLE ? View.GONE : View.VISIBLE);
            progress.setIndeterminate(RuntimeService.progress < 0);
            if (RuntimeService.progress >= 0) progress.setProgress(RuntimeService.progress);
            progress.setVisibility(RuntimeService.ready ? View.GONE : View.VISIBLE);
            if (logs.getVisibility() == View.VISIBLE) {
                String text = RuntimeService.logs();
                if (!android.text.TextUtils.equals(journal.getText(), text)) journal.setText(text);
            }
            if (RuntimeService.ready && !loaded) {
                loaded = true;
                web.loadUrl(RuntimeService.launchUrl);
                showLogs(false);
            }
            handler.postDelayed(this, 750);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) exportingPath = state.getString("exportingPath");
        getWindow().setStatusBarColor(Color.rgb(20, 20, 20));
        getWindow().setNavigationBarColor(Color.rgb(20, 20, 20));
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(20, 20, 20));
        LinearLayout bar = new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL); bar.setPadding(dp(4), 0, dp(6), 0);
        toolbarButton(bar, "☰", () -> {
            if (RuntimeService.ready) { showLogs(false); web.evaluateJavascript("document.dispatchEvent(new Event('dsh-mobile-toggle-sidebar'))", null); }
        });
        TextView title = new TextView(this); title.setText("Harness"); title.setTextSize(18); title.setTextColor(Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD); bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        toolbarButton(bar, "Модели", () -> MobileProviders.show(this, this::restartRuntime));
        toolbarButton(bar, "⋮", () -> appMenu(bar));
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(52)));
        status = new TextView(this); status.setTextColor(Color.rgb(158, 166, 184)); status.setTextSize(12); status.setPadding(dp(16), 0, dp(16), dp(6)); root.addView(status);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); progress.setMax(1000); root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));
        FrameLayout content = new FrameLayout(this);
        web = new WebView(this);
        if (getPreferences(MODE_PRIVATE).getInt("web-ui-version", 0) < 7) {
            web.clearCache(true);
            getPreferences(MODE_PRIVATE).edit().putInt("web-ui-version", 7).apply();
        }
        web.setBackgroundColor(Color.rgb(20, 20, 20));
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(true);
        settings.setSupportMultipleWindows(true); settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        try (InputStream input = getAssets().open("mobile-ui.js"); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int n; while ((n = input.read(buffer)) != -1) out.write(buffer, 0, n);
            mobileJavascript = out.toString("UTF-8");
        } catch (IOException error) { RuntimeService.log(error.toString()); mobileJavascript = ""; }
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                Uri uri = Uri.parse(url);
                if ("127.0.0.1".equals(uri.getHost()) && uri.getPort() == 3080) view.evaluateJavascript(mobileJavascript, null);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("harness-file".equals(uri.getScheme())) {
                    Uri current = Uri.parse(view.getUrl() == null ? "" : view.getUrl());
                    if (request.isForMainFrame() && "127.0.0.1".equals(current.getHost()) && current.getPort() == 3080
                        && "preview".equals(uri.getHost())) openHtml(uri.getQueryParameter("path"));
                    return true;
                }
                if ("127.0.0.1".equals(uri.getHost()) && uri.getPort() == 3080 && "http".equals(uri.getScheme())) return false;
                if ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (ActivityNotFoundException ignored) { }
                }
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onCreateWindow(WebView view, boolean dialog, boolean userGesture, Message message) {
                WebView popup = new WebView(MainActivity.this);
                popup.setWebViewClient(new WebViewClient() {
                    private boolean open(Uri uri) {
                        if ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) {
                            try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                            catch (ActivityNotFoundException ignored) { Toast.makeText(MainActivity.this, "Установите браузер для входа", Toast.LENGTH_LONG).show(); }
                        }
                        popup.post(popup::destroy);
                        return true;
                    }
                    @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) { return open(request.getUrl()); }
                    @Override public boolean shouldOverrideUrlLoading(WebView v, String url) { return open(Uri.parse(url)); }
                });
                ((WebView.WebViewTransport)message.obj).setWebView(popup);
                message.sendToTarget();
                return true;
            }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (chooser != null) chooser.onReceiveValue(null);
                chooser = callback;
                try { startActivityForResult(params.createIntent().addCategory(Intent.CATEGORY_OPENABLE), 10); }
                catch (ActivityNotFoundException e) { chooser.onReceiveValue(null); chooser = null; }
                return true;
            }
        });
        content.addView(web);
        logs = new ScrollView(this); logs.setBackgroundColor(Color.rgb(20, 20, 20));
        journal = new TextView(this); journal.setTextColor(Color.rgb(205, 220, 240)); journal.setTextSize(12); journal.setTypeface(android.graphics.Typeface.MONOSPACE); journal.setPadding(16, 16, 16, 16); journal.setTextIsSelectable(true);
        logs.addView(journal); content.addView(logs);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        startForegroundService(new Intent(this, RuntimeService.class));
    }

    private void restartRuntime() {
        stopService(new Intent(this, RuntimeService.class));
        RuntimeService.status = "Применение настроек моделей…";
        long deadline = System.currentTimeMillis() + 20000;
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (RuntimeService.hasLiveProcess() || RuntimeService.running) {
                    if (System.currentTimeMillis() < deadline) { handler.postDelayed(this, 300); return; }
                    Toast.makeText(MainActivity.this, "Ядро ещё завершается. Запустите его через меню приложения.", Toast.LENGTH_LONG).show(); return;
                }
                loaded = false;
                startForegroundService(new Intent(MainActivity.this, RuntimeService.class));
            }
        }, 300);
    }
    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }
    private void toolbarButton(LinearLayout bar, String label, Runnable action) {
        TextView button = new TextView(this); button.setText(label); button.setTextColor(Color.rgb(180, 198, 255)); button.setTextSize(label.equals("⋮") ? 26 : 14);
        button.setGravity(Gravity.CENTER); button.setPadding(dp(12), 0, dp(12), 0); button.setContentDescription(label.equals("⋮") ? "Меню приложения" : label);
        button.setOnClickListener(v -> action.run()); bar.addView(button, new LinearLayout.LayoutParams(label.equals("⋮") ? dp(44) : -2, dp(48)));
    }
    private void appMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        for (String name : new String[]{"Интерфейс", "Настройки Harness", "Журнал", "Файлы", "Импортировать файлы", "Экспортировать файл", "Запустить ядро", "Остановить ядро", "Сбросить браузер агента", "О приложении"}) menu.getMenu().add(name);
        menu.setOnMenuItemClickListener(item -> {
            String name = item.getTitle().toString();
            if (name.equals("Интерфейс")) { web.loadUrl(RuntimeService.launchUrl); loaded = true; showLogs(false); }
            else if (name.equals("Настройки Harness")) { showLogs(false); web.evaluateJavascript("document.dispatchEvent(new Event('dsh-mobile-open-models'))", null); }
            else if (name.equals("Журнал")) showLogs(true);
            else if (name.equals("Импортировать файлы")) startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true), 20);
            else if (name.equals("Файлы")) fileRoots(false);
            else if (name.equals("Экспортировать файл")) fileRoots(true);
            else if (name.equals("Запустить ядро")) { loaded = false; startForegroundService(new Intent(this, RuntimeService.class)); }
            else if (name.equals("Остановить ядро")) stopService(new Intent(this, RuntimeService.class));
            else if (name.equals("Сбросить браузер агента")) { BrowserController.shutdown(); Toast.makeText(this,"Браузер агента закрыт",Toast.LENGTH_SHORT).show(); }
            else new AlertDialog.Builder(this).setTitle("Harness Local").setMessage("Локальное ядро DeepSeek Harness в PRoot. Root и Termux не нужны.\n\nВход DeepSeek: меню аккаунта → Sign in. Другие модели: Модели → Add provider → Custom model API. Для инструментов сервер должен поддерживать tool calling.\n\nФайлы импортируются в /workspace/inbox. Лицензии и исходники сторонних компонентов входят в APK. Это не официальный релиз DeepSeek.").setPositiveButton("Закрыть", null).show();
            return true;
        }); menu.show();
    }
    private void showLogs(boolean visible) { logs.setVisibility(visible ? View.VISIBLE : View.GONE); web.setVisibility(visible ? View.GONE : View.VISIBLE); }
    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
    @Override protected void onDestroy() { if (chooser != null) chooser.onReceiveValue(null); web.destroy(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle state) { state.putString("exportingPath", exportingPath); super.onSaveInstanceState(state); }
    @Override public void onBackPressed() { if (logs.getVisibility() == View.VISIBLE && loaded) showLogs(false); else if (web.canGoBack()) web.goBack(); else super.onBackPressed(); }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 10 && chooser != null) {
            chooser.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data)); chooser = null;
        }
        if (request == 20 && result == RESULT_OK && data != null) {
            new Thread(() -> {
                try {
                    int count = 0;
                    if (data.getClipData() != null) {
                        for (int i = 0; i < data.getClipData().getItemCount(); i++) { importFile(data.getClipData().getItemAt(i).getUri()); count++; }
                    } else if (data.getData() != null) { importFile(data.getData()); count++; }
                    final int imported = count;
                    runOnUiThread(() -> Toast.makeText(this, "Импортировано: " + imported + ". Путь: /workspace/inbox", Toast.LENGTH_LONG).show());
                } catch (Exception e) { RuntimeService.log("Ошибка импорта: " + e); }
            }, "file-import").start();
        }
        if (request == 30) {
            String path = exportingPath;
            exportingPath = null;
            if (result != RESULT_OK || data == null || data.getData() == null || path == null) return;
            new Thread(() -> {
                try {
                    File source = new ProjectFiles(getFilesDir()).resolve(path);
                    try (InputStream in = new FileInputStream(source); OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                        if (out == null) throw new IOException("Не удалось открыть файл для записи");
                        byte[] buffer = new byte[65536]; int n;
                        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                    }
                    runOnUiThread(() -> Toast.makeText(this, "Файл экспортирован", Toast.LENGTH_SHORT).show());
                } catch (IOException e) { RuntimeService.log("Ошибка экспорта: " + e); runOnUiThread(() -> Toast.makeText(this,"Ошибка экспорта: " + e.getMessage(),Toast.LENGTH_LONG).show()); }
            }, "file-export").start();
        }
    }
    private void fileRoots(boolean exportOnly) {
        new AlertDialog.Builder(this).setTitle(exportOnly ? "Откуда экспортировать?" : "Файлы проекта")
            .setItems(new String[]{"/workspace", "/root"}, (dialog, item) -> filePicker(item == 0 ? "/workspace" : "/root", exportOnly))
            .setNegativeButton("Закрыть", null).show();
    }
    private void filePicker(String path, boolean exportOnly) {
        try {
            String guest = ProjectFiles.normalize(path);
            ProjectFiles access = new ProjectFiles(getFilesDir());
            File dir = access.resolve(guest);
            File[] files = dir.listFiles();
            if (files == null) { Toast.makeText(this, "Папка пока не создана", Toast.LENGTH_SHORT).show(); return; }
            java.util.Arrays.sort(files, (a, b) -> a.isDirectory() != b.isDirectory() ? (a.isDirectory() ? -1 : 1) : a.getName().compareToIgnoreCase(b.getName()));
            boolean parent = !(guest.equals("/root") || guest.equals("/workspace"));
            String[] labels = new String[files.length + 1];
            labels[0] = parent ? "../" : "← /root и /workspace";
            for (int i = 0; i < files.length; i++) labels[i + 1] = files[i].getName() + (files[i].isDirectory() ? "/" : "");
            new AlertDialog.Builder(this).setTitle(guest).setItems(labels, (dialog, item) -> {
                if (item == 0) { if (parent) filePicker(guest.substring(0, guest.lastIndexOf('/')), exportOnly); else fileRoots(exportOnly); return; }
                String chosenPath = guest + "/" + files[item - 1].getName();
                try {
                    File chosen = access.resolve(chosenPath);
                    if (chosen.isDirectory()) filePicker(chosenPath, exportOnly);
                    else if (!chosen.isFile()) throw new IOException("Файл недоступен");
                    else if (!exportOnly && ProjectFiles.isHtml(chosenPath)) openHtml(chosenPath);
                    else beginExport(chosenPath);
                } catch (IOException e) { Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show(); }
            }).setNegativeButton("Закрыть", null).show();
        } catch (IOException e) { Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show(); }
    }
    private void beginExport(String path) throws IOException {
        if (!new ProjectFiles(getFilesDir()).resolve(path).isFile()) throw new IOException("Файл недоступен");
        exportingPath = path;
        String type = ProjectFiles.mime(path).split(";")[0];
        startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(type)
            .putExtra(Intent.EXTRA_TITLE, path.substring(path.lastIndexOf('/') + 1)), 30);
    }
    private void openHtml(String path) {
        try {
            path = ProjectFiles.normalize(path);
            if (!ProjectFiles.isHtml(path) || !new ProjectFiles(getFilesDir()).resolve(path).isFile()) throw new IOException("HTML-файл не найден");
            startActivity(new Intent(this,HtmlPreviewActivity.class).putExtra("path",path));
        } catch (IOException | IllegalArgumentException error) { Toast.makeText(this,error.getMessage(),Toast.LENGTH_LONG).show(); }
    }
    private void importFile(Uri uri) throws IOException {
        String name = "import-" + System.currentTimeMillis();
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
        }
        name = name.replaceAll("[^\\p{L}\\p{N}._ -]", "_");
        if (name.equals(".") || name.equals("..") || name.isEmpty()) name = "import";
        File inbox = new File(getFilesDir(), "workspace/inbox");
        if (!inbox.isDirectory() && !inbox.mkdirs()) throw new IOException("Не удалось создать inbox");
        File dest = new File(inbox, name);
        int suffix = 1;
        while (dest.exists()) dest = new File(inbox, suffix++ + "-" + name);
        try (InputStream in = getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(dest)) {
            if (in == null) throw new IOException("Не удалось открыть документ");
            byte[] buffer = new byte[65536]; int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        }
    }
}
