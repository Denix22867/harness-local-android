package org.dsh.mobile;

import android.content.Context;
import android.os.*;
import org.json.*;
import java.util.concurrent.CompletableFuture;

/** UI-thread owner of the private virtual-display browser session. */
final class BrowserController {
    private static final Handler ui=new Handler(Looper.getMainLooper());
    private static BrowserSession browser;
    private static String owner;
    static CompletableFuture<JSONObject> perform(Context context,JSONObject args){
        CompletableFuture<JSONObject> result=new CompletableFuture<>();
        ui.post(()->{
            if(result.isCancelled())return;
            try{
                String requested=args.getString("owner"),action=args.getString("action");
                if(owner!=null&&!owner.equals(requested))throw new IllegalStateException("Браузер занят другой сессией. Владеющая сессия должна вызвать browser close.");
                if(action.equals("open")){
                    int width=args.optInt("width",360),height=args.optInt("height",640);
                    if(width<240||width>1280||height<240||height>1600)throw new IllegalArgumentException("Viewport: width 240–1280, height 240–1600");
                    if(browser!=null&&!browser.hasViewport(width,height))browser.closeBrowser();
                    if(browser==null){browser=BrowserSession.create(context,width,height);owner=requested;}
                }
                if(browser==null)throw new IllegalStateException("Сначала вызовите browser с action=open");
                browser.execute(args,result);
            }catch(Exception error){fail(result,error);}
        });
        return result;
    }
    static void detached(BrowserSession view){if(browser==view){browser=null;owner=null;}}
    static void shutdown(){ui.post(()->{if(browser!=null)browser.closeBrowser();owner=null;});}
    static void fail(CompletableFuture<JSONObject> result,Throwable error){
        JSONObject value=new JSONObject();
        try{value.put("error",error.getMessage()==null?error.getClass().getSimpleName():error.getMessage());}catch(JSONException ignored){}
        result.complete(value);
    }
}
