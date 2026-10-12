package org.dsh.mobile;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.webkit.*;
import android.widget.*;
import java.io.*;

/** Executes project HTML in a WebView isolated from the authenticated Harness UI. */
public final class HtmlPreviewActivity extends Activity {
    private WebView web;
    private HtmlPreviewServer server;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        String path = getIntent().getStringExtra("path");
        try {
            path = ProjectFiles.normalize(path);
            ProjectFiles files = new ProjectFiles(getFilesDir());
            if (!ProjectFiles.isHtml(path) || !files.resolve(path).isFile()) throw new IOException("HTML-файл не найден");
            int slash = path.lastIndexOf('/');
            server = new HtmlPreviewServer(files, path.substring(0, slash));
            LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setBackgroundColor(Color.rgb(20,20,20));
            LinearLayout bar = new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL);
            Button back = new Button(this); back.setText("←"); back.setContentDescription("Вернуться в Harness"); back.setOnClickListener(v -> finish()); bar.addView(back);
            TextView title = new TextView(this); title.setText(path); title.setTextColor(Color.WHITE); title.setTextSize(12); title.setMaxLines(2);
            bar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
            Button reload = new Button(this); reload.setText("↻"); reload.setContentDescription("Обновить HTML"); reload.setOnClickListener(v -> web.reload()); bar.addView(reload);
            layout.addView(bar);
            web = new WebView(this);
            WebSettings settings = web.getSettings(); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
            settings.setAllowFileAccess(false); settings.setAllowContentAccess(false);
            settings.setAllowFileAccessFromFileURLs(false); settings.setAllowUniversalAccessFromFileURLs(false);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            web.setWebChromeClient(new WebChromeClient());
            web.setWebViewClient(new WebViewClient() {
                @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    Uri uri = request.getUrl();
                    if (uri.toString().startsWith(server.origin() + "/")) return false;
                    if (request.isForMainFrame() && ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))) {
                        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (ActivityNotFoundException ignored) { }
                    }
                    return true;
                }
            });
            layout.addView(web,new LinearLayout.LayoutParams(-1,0,1)); setContentView(layout);
            web.loadUrl(server.url(path.substring(slash + 1)));
        } catch (IOException | IllegalArgumentException error) {
            Toast.makeText(this,"Не удалось открыть HTML: " + error.getMessage(),Toast.LENGTH_LONG).show(); finish();
        }
    }
    @Override public void onBackPressed() { if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed(); }
    @Override protected void onDestroy() { if (web != null) web.destroy(); if (server != null) server.close(); super.onDestroy(); }
}
