package org.dsh.mobile;

import android.content.*;
import android.os.*;
import org.json.*;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;

/** Serial UI-thread owner of the model's visible browser session. */
final class BrowserController {
    private static final Handler ui = new Handler(Looper.getMainLooper());
    private static BrowserActivity activity;
    private static String owner;
    private static CompletableFuture<JSONObject> opening;
    private static String openingKey;
    static CompletableFuture<JSONObject> perform(Context context, JSONObject args) {
        CompletableFuture<JSONObject> result = new CompletableFuture<>();
        ui.post(() -> {
            if (result.isCancelled()) return;
            try {
                if(opening!=null&&opening.isCancelled()){opening=null;openingKey=null;owner=null;}
                String requested = args.getString("owner"), action = args.getString("action");
                if (owner != null && !owner.equals(requested)) throw new IllegalStateException("Браузер занят другой сессией. Закройте его через панель браузера.");
                if (action.equals("open") && activity == null) {
                    if (opening != null) throw new IllegalStateException("Браузер уже открывается");
                    owner = requested; opening = result; openingKey=UUID.randomUUID().toString();
                    context.startActivity(new Intent(context, BrowserActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("request",args.toString()).putExtra("openingKey",openingKey));
                } else {
                    if (activity == null) throw new IllegalStateException("Сначала вызовите browser с action=open");
                    activity.execute(args,result);
                }
            } catch (Exception error) { fail(result,error); if (activity == null) { owner=null; opening=null; openingKey=null; } }
        });
        return result;
    }
    static void attached(BrowserActivity view, JSONObject request, String key) {
        if(opening==null||opening.isCancelled()||key==null||!key.equals(openingKey)){view.finish();return;}
        activity = view;
        CompletableFuture<JSONObject> result = opening; opening = null; openingKey=null;
        if (result == null) { view.finish(); return; }
        view.execute(request,result);
    }
    static void detached(BrowserActivity view) {
        if (activity == view) { activity=null; owner=null; }
    }
    static void shutdown() { ui.post(() -> { BrowserActivity old=activity;activity=null;if(old!=null)old.finish();if(opening!=null)fail(opening,new IllegalStateException("Ядро остановлено")); opening=null; openingKey=null; owner=null; }); }
    static void fail(CompletableFuture<JSONObject> result, Throwable error) {
        JSONObject value = new JSONObject();
        try { value.put("error",error.getMessage()==null?error.getClass().getSimpleName():error.getMessage()); } catch(JSONException ignored) { }
        result.complete(value);
    }
}
