package org.dsh.mobile;
import android.content.Context;
import android.system.Os;
import java.io.*;
import org.json.*;
/** Small app-bundled UI update; leaves the runtime version and user data intact. */
final class RuntimePatches {
    private static String read(InputStream input) throws IOException {
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[65536]; int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return out.toString("UTF-8");
        }
    }
    private static void applyAccount(Context context, File root) throws Exception {
        File target = new File(root, "opt/dsh/node_modules/@deepseek-ai/dsh-client-ui-settings-account/lib/client.js");
        if (!target.isFile()) throw new IOException("Модуль аккаунта DeepSeek не найден в установленном ядре");
        String source = read(new FileInputStream(target));
        if (source.contains("// Android uses the upstream account service and loopback callback.")) return;
        JSONArray changes = new JSONObject(read(context.getAssets().open("account-ui-patch.json"))).getJSONArray("replacements");
        for (int i = 0; i < changes.length(); i++) {
            JSONObject change = changes.getJSONObject(i);
            String before = change.getString("old");
            int at = source.indexOf(before);
            if (at < 0 || source.indexOf(before, at + before.length()) >= 0) throw new IOException("Версия интерфейса не подходит для обновления аккаунта");
            source = source.substring(0, at) + change.getString("new") + source.substring(at + before.length());
        }
        File pending = new File(target.getParentFile(), "client.js.mobile-update");
        try (FileOutputStream out = new FileOutputStream(pending)) {
            out.write(source.getBytes("UTF-8")); out.getFD().sync();
        }
        Os.chmod(pending.getPath(), 0644);
        Os.rename(pending.getPath(), target.getPath());
        RuntimeService.log("Вход через аккаунт DeepSeek включён. Установленное ядро сохранено.");
    }
    private static void patch(Context context, File root, String module, String asset) throws Exception {
        File target = new File(root, "opt/dsh/node_modules/@deepseek-ai/" + module + "/lib/client.js");
        String source = read(new FileInputStream(target));
        JSONObject spec = new JSONObject(read(context.getAssets().open(asset)));
        if (source.contains(spec.getString("marker"))) return;
        JSONArray changes = spec.getJSONArray("replacements");
        for (int i = 0; i < changes.length(); i++) {
            JSONObject change = changes.getJSONObject(i); String before = change.getString("old");
            int at = source.indexOf(before);
            if (at < 0 || source.indexOf(before, at + before.length()) >= 0) throw new IOException("Не удалось обновить " + module);
            source = source.substring(0, at) + change.getString("new") + source.substring(at + before.length());
        }
        File pending = new File(target.getParentFile(), "client.js.mobile-update");
        try (FileOutputStream out = new FileOutputStream(pending)) { out.write(source.getBytes("UTF-8")); out.getFD().sync(); }
        Os.chmod(pending.getPath(), 0644); Os.rename(pending.getPath(), target.getPath());
    }
    static void apply(Context context, File root) throws Exception {
        SshTools.install(context, root);
        for (String asset : new String[]{"android-bootstrap.mjs", "android-capabilities.mjs", "android-browser.mjs", "android.patch.yml"}) {
            File target = new File(root, "opt/dsh/" + asset);
            File pending = new File(target.getParentFile(), asset + ".next");
            try (InputStream in = context.getAssets().open(asset); FileOutputStream out = new FileOutputStream(pending)) {
                byte[] buffer = new byte[8192]; int n; while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                out.getFD().sync();
            }
            Os.chmod(pending.getPath(), 0644); Os.rename(pending.getPath(), target.getPath());
        }
        applyAccount(context, root);
        patch(context, root, "dsh-client-ui-settings-models", "models-ui-patch.json");
        patch(context, root, "dsh-client-ui-settings-general", "settings-ui-patch.json");
    }

}
