package org.dsh.mobile;

import android.app.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import android.net.Uri;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Real, visible WebView controlled by Harness tools, without a JavaScript bridge. */
public final class BrowserActivity extends Activity {
    private WebView web;
    private TextView address;
    private HtmlPreviewServer files;
    private CompletableFuture<JSONObject> navigation;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ArrayList<String> console=new ArrayList<>();
    private float density;
    private int width=360,height=640;
    private boolean destroyed;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);density=getResources().getDisplayMetrics().density;
        getWindow().setStatusBarColor(Color.rgb(20,20,20));getWindow().setNavigationBarColor(Color.rgb(20,20,20));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(Color.rgb(20,20,20));
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=control("←","Вернуться в Harness");back.setOnClickListener(v->closeBrowser());bar.addView(back,new LinearLayout.LayoutParams(dp(48),dp(52)));
        address=new TextView(this);address.setText("Браузер агента");address.setTextColor(Color.WHITE);address.setTextSize(12);address.setMaxLines(2);bar.addView(address,new LinearLayout.LayoutParams(0,-2,1));
        TextView reload=control("↻","Обновить страницу");reload.setOnClickListener(v->web.reload());bar.addView(reload,new LinearLayout.LayoutParams(dp(48),dp(52)));root.addView(bar);
        FrameLayout frame=new FrameLayout(this);root.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
        WebView.enableSlowWholeDocumentDraw();web=new WebView(this);frame.addView(web,new FrameLayout.LayoutParams(dp(width),dp(height)));
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);settings.setAllowUniversalAccessFromFileURLs(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onConsoleMessage(ConsoleMessage message){addConsole(message.messageLevel()+": "+message.message()+" (line "+message.lineNumber()+")");return true;}
        });
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return !allowed(request.getUrl().toString());}
            @Override public void onPageFinished(WebView view,String url){address.setText(url);if(navigation!=null){CompletableFuture<JSONObject> result=navigation;navigation=null;ui.postDelayed(()->snapshot(result,false),200);}}
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){addConsole("Load error: "+error.getDescription());if(request.isForMainFrame()&&navigation!=null){BrowserController.fail(navigation,new IOException(error.getDescription().toString()));navigation=null;}}
            @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse response){addConsole("HTTP "+response.getStatusCode()+": "+request.getUrl());}
        });
        setContentView(root);
        try{BrowserController.attached(this,new JSONObject(getIntent().getStringExtra("request")),getIntent().getStringExtra("openingKey"));}catch(Exception error){finish();}
    }
    private int dp(int value){return Math.round(value*density);}
    private TextView control(String text,String label){TextView v=new TextView(this);v.setText(text);v.setTextSize(24);v.setTextColor(Color.rgb(180,198,255));v.setGravity(Gravity.CENTER);v.setContentDescription(label);return v;}
    private void addConsole(String value){console.add(value.length()>500?value.substring(0,500):value);if(console.size()>40)console.remove(0);}
    private static boolean allowed(String value){
        Uri uri=Uri.parse(value);String scheme=uri.getScheme();
        if(!("http".equals(scheme)||"https".equals(scheme))||uri.getHost()==null||uri.getUserInfo()!=null)return false;
        // Project browsing never navigates into Harness's authenticated control UI.
        return !(Arrays.asList("127.0.0.1","localhost","::1","[::1]").contains(uri.getHost())&&uri.getPort()==3080);
    }
    void execute(JSONObject args,CompletableFuture<JSONObject> result){
        if(result.isCancelled())return;
        try{
            if(navigation!=null&&navigation.isCancelled()){navigation=null;web.stopLoading();}
            String action=args.getString("action");
            if(action.equals("open")){
                if(navigation!=null)throw new IllegalStateException("Дождитесь завершения открытия страницы");
                width=args.optInt("width",360);height=args.optInt("height",640);
                if(width<240||width>1280||height<240||height>1600)throw new IllegalArgumentException("Viewport: width 240–1280, height 240–1600");
                web.setLayoutParams(new FrameLayout.LayoutParams(dp(width),dp(height)));console.clear();
                if(files!=null){files.close();files=null;}
                String target;
                if(args.has("path")){
                    String path=ProjectFiles.normalize(args.getString("path"));
                    if(!ProjectFiles.isHtml(path))throw new IOException("Укажите HTML-файл");
                    if(!new ProjectFiles(getFilesDir()).resolve(path).isFile())throw new FileNotFoundException("HTML-файл не найден");
                    int slash=path.lastIndexOf('/');files=new HtmlPreviewServer(new ProjectFiles(getFilesDir()),path.substring(0,slash));target=files.url(path.substring(slash+1));
                }else target=args.getString("url");
                if(!allowed(target))throw new IOException("Разрешены HTTP(S) страницы и HTML проекта");
                navigation=result;web.loadUrl(target);
            }else if(action.equals("snapshot"))snapshot(result,false);
            else if(action.equals("screenshot"))snapshot(result,true);
            else if(action.equals("wait")){
                int ms=args.optInt("ms",500);if(ms<0||ms>5000)throw new IllegalArgumentException("Wait must be 0–5000 ms");ui.postDelayed(()->snapshot(result,false),ms);
            }else if(action.equals("click")){
                if(args.has("selector")){
                    String script="(()=>{const e=document.querySelector("+JSONObject.quote(args.getString("selector"))+");if(!e)throw Error('Element not found');e.scrollIntoView({block:'center'});const r=e.getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2}})()";
                    evaluate(script,result,value->{JSONObject point=(JSONObject)value;tap(point.getDouble("x"),point.getDouble("y"),result);});
                }else tap(args.getDouble("x"),args.getDouble("y"),result);
            }else if(action.equals("type")){
                String selector=JSONObject.quote(args.getString("selector")),text=JSONObject.quote(args.getString("text"));
                evaluate("(()=>{const e=document.querySelector("+selector+");if(!e)throw Error('Element not found');e.focus();const p=e.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;const s=Object.getOwnPropertyDescriptor(p,'value').set;s.call(e,"+text+");e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));return true})()",result,value->snapshot(result,false));
            }else if(action.equals("key")){
                String key=args.getString("key");int code;
                switch(key){case "ArrowLeft":code=KeyEvent.KEYCODE_DPAD_LEFT;break;case "ArrowRight":code=KeyEvent.KEYCODE_DPAD_RIGHT;break;case "ArrowUp":code=KeyEvent.KEYCODE_DPAD_UP;break;case "ArrowDown":code=KeyEvent.KEYCODE_DPAD_DOWN;break;case "Enter":code=KeyEvent.KEYCODE_ENTER;break;case "Escape":code=KeyEvent.KEYCODE_ESCAPE;break;case "Space":case " ":code=KeyEvent.KEYCODE_SPACE;break;default:if(!key.matches("[a-zA-Z0-9]"))throw new IllegalArgumentException("Unsupported key");code=KeyEvent.keyCodeFromString("KEYCODE_"+key.toUpperCase(Locale.ROOT));}
                web.requestFocus();web.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,code));web.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,code));ui.postDelayed(()->snapshot(result,false),100);
            }else if(action.equals("evaluate")){
                String code=args.getString("javascript");if(code.length()>16000)throw new IllegalArgumentException("JavaScript is too long");
                evaluate("(()=>{"+code+"})()",result,value->{JSONObject out=new JSONObject();out.put("value",value);out.put("console",new JSONArray(console.subList(Math.max(0,console.size()-20),console.size())));result.complete(out);});
            }else if(action.equals("close")){closeBrowser();result.complete(new JSONObject().put("closed",true));}
            else throw new IllegalArgumentException("Unknown browser action");
        }catch(Exception error){BrowserController.fail(result,error);}
    }
    private interface ValueHandler{void run(Object value)throws Exception;}
    private void evaluate(String expression,CompletableFuture<JSONObject> result,ValueHandler next){
        if(destroyed||result.isCancelled()){BrowserController.fail(result,new IOException("Браузер закрыт"));return;}
        String code="(()=>{try{return JSON.stringify({ok:true,value:(function(){var v=("+expression+");return v===undefined?null:v})()})}catch(e){return JSON.stringify({ok:false,error:String(e)})}})()";
        web.evaluateJavascript(code,encoded->{
            if(result.isCancelled())return;
            try{Object decoded=new JSONTokener(encoded).nextValue();if(!(decoded instanceof String))throw new IOException("JavaScript did not return a result");JSONObject envelope=new JSONObject((String)decoded);if(!envelope.getBoolean("ok"))throw new IOException(envelope.getString("error"));next.run(envelope.opt("value"));}catch(Exception error){BrowserController.fail(result,error);}
        });
    }
    private void tap(double x,double y,CompletableFuture<JSONObject> result)throws Exception{
        if(x<0||y<0||x>=width||y>=height)throw new IllegalArgumentException("Click is outside viewport");
        long t=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,(float)x*density,(float)y*density,0),up=MotionEvent.obtain(t,t+40,MotionEvent.ACTION_UP,(float)x*density,(float)y*density,0);
        web.dispatchTouchEvent(down);web.dispatchTouchEvent(up);down.recycle();up.recycle();ui.postDelayed(()->snapshot(result,false),250);
    }
    private void snapshot(CompletableFuture<JSONObject> result,boolean image){
        String script="(()=>{let n=0;const elements=[...document.querySelectorAll('button,a,input,select,textarea,[role=button],canvas')].filter(e=>e.getBoundingClientRect().width>0&&e.getBoundingClientRect().height>0).slice(0,100).map(e=>{const id=String(++n);e.setAttribute('data-harness-browser-id',id);const r=e.getBoundingClientRect();return {selector:'[data-harness-browser-id=\"'+id+'\"]',tag:e.tagName,text:(e.innerText||e.getAttribute('aria-label')||e.getAttribute('placeholder')||'').slice(0,160),x:r.x,y:r.y,width:r.width,height:r.height,disabled:!!e.disabled}});return {url:location.href,title:document.title,readyState:document.readyState,viewport:{width:innerWidth,height:innerHeight},text:(document.body?document.body.innerText||'':'').slice(0,10000),elements}})()";
        evaluate(script,result,value->{
            JSONObject state=(JSONObject)value;state.put("console",new JSONArray(console.subList(Math.max(0,console.size()-20),console.size())));
            if(image){
                if(web.getWidth()==0||web.getHeight()==0)throw new IOException("Страница ещё не отрисована");
                Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.scale(1/density,1/density);web.draw(canvas);
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes);bitmap.recycle();
                state.put("image",android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP));state.put("imageWidth",width);state.put("imageHeight",height);
            }
            result.complete(state);
        });
    }
    private void closeBrowser(){BrowserController.detached(this);finish();}
    @Override public void onBackPressed(){closeBrowser();}
    @Override protected void onDestroy(){destroyed=true;if(navigation!=null)BrowserController.fail(navigation,new IOException("Браузер закрыт"));if(web!=null)web.destroy();if(files!=null)files.close();BrowserController.detached(this);super.onDestroy();}
}
