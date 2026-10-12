package org.dsh.mobile;

import android.app.*;
import android.content.Context;
import android.hardware.display.*;
import android.media.*;
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

/** Hardware-rendered browser on a private virtual display; never covers the chat. */
final class BrowserSession extends Presentation {
    private WebView web;
    private VirtualDisplay display;
    private ImageReader frames;
    private Image latestFrame;
    private long frameNumber;
    private HtmlPreviewServer files;
    private CompletableFuture<JSONObject> navigation;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ArrayList<String> console=new ArrayList<>();
    private float density;
    private int width=360,height=640;
    private boolean destroyed;
    static BrowserSession create(Context context,int width,int height) throws IOException {
        ImageReader reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,3);
        VirtualDisplay display=null;
        try {
            display=((DisplayManager)context.getSystemService(Context.DISPLAY_SERVICE)).createVirtualDisplay(
                "Harness private browser "+UUID.randomUUID(),width,height,160,reader.getSurface(),DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY|DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION);
            if(display==null)throw new IOException("Не удалось создать виртуальный экран браузера");
            BrowserSession session=new BrowserSession(context,display,reader,width,height);session.show();return session;
        } catch(Exception error){if(display!=null)display.release();reader.close();throw new IOException("Не удалось запустить скрытый браузер",error);}
    }
    private BrowserSession(Context context,VirtualDisplay display,ImageReader reader,int width,int height){
        super(context,display.getDisplay());this.display=display;this.frames=reader;this.width=width;this.height=height;
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);density=1;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED|WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        FrameLayout frame=new FrameLayout(getContext());
        web=new WebView(getContext());frame.addView(web,new FrameLayout.LayoutParams(-1,-1));
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);settings.setAllowUniversalAccessFromFileURLs(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onConsoleMessage(ConsoleMessage message){addConsole(message.messageLevel()+": "+message.message()+" (line "+message.lineNumber()+")");return true;}
        });
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return !allowed(request.getUrl().toString());}
            @Override public void onPageFinished(WebView view,String url){if(navigation!=null){CompletableFuture<JSONObject> result=navigation;navigation=null;ui.postDelayed(()->snapshot(result,false),200);}}
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){addConsole("Load error: "+error.getDescription());if(request.isForMainFrame()&&navigation!=null){BrowserController.fail(navigation,new IOException(error.getDescription().toString()));navigation=null;}}
            @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse response){addConsole("HTTP "+response.getStatusCode()+": "+request.getUrl());}
        });
        setContentView(frame);
        getWindow().setLayout(-1,-1);
        listen(frames);
    }
    private void listen(ImageReader reader){
        reader.setOnImageAvailableListener(source->{
            Image image=null;
            try {
                image=source.acquireLatestImage();
                if(image==null)return;
                if(destroyed||source!=frames){image.close();return;}
                if(latestFrame!=null)latestFrame.close();latestFrame=image;image=null;frameNumber++;
            }catch(Exception error){if(image!=null)image.close();addConsole("Capture error: "+error.getMessage());}
        },ui);
    }
    private Bitmap frameBitmap(){
        Image.Plane plane=latestFrame.getPlanes()[0];
        // Plane buffers need not include padding after the final row.
        java.nio.ByteBuffer source=plane.getBuffer().duplicate();
        java.nio.ByteBuffer pixels=java.nio.ByteBuffer.allocate(width*height*4);
        for(int y=0;y<height;y++){
            source.limit(source.capacity());source.position(y*plane.getRowStride());source.limit(source.position()+width*4);pixels.put(source);
        }
        pixels.flip();Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);bitmap.copyPixelsFromBuffer(pixels);return bitmap;
    }
    boolean hasViewport(int w,int h){return width==w&&height==h;}
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
                int w=args.optInt("width",360),h=args.optInt("height",640);
                if(w<240||w>1280||h<240||h>1600)throw new IllegalArgumentException("Viewport: width 240–1280, height 240–1600");
                if(!hasViewport(w,h))throw new IllegalStateException("Размер виртуального экрана не совпадает");console.clear();
                if(files!=null){files.close();files=null;}
                String target;
                if(args.has("path")){
                    String path=ProjectFiles.normalize(args.getString("path"));
                    if(!ProjectFiles.isHtml(path))throw new IOException("Укажите HTML-файл");
                    if(!new ProjectFiles(getContext().getFilesDir()).resolve(path).isFile())throw new FileNotFoundException("HTML-файл не найден");
                    int slash=path.lastIndexOf('/');files=new HtmlPreviewServer(new ProjectFiles(getContext().getFilesDir()),path.substring(0,slash));target=files.url(path.substring(slash+1));
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
            JSONObject state=(JSONObject)value;state.put("hardwareAccelerated",web.isHardwareAccelerated());state.put("console",new JSONArray(console.subList(Math.max(0,console.size()-20),console.size())));
            if(image){
                String readback="(()=>{let budget=16000000;const images=[],warnings=[];for(const c of Array.from(document.querySelectorAll('canvas')).slice(0,8)){const r=c.getBoundingClientRect();const style=getComputedStyle(c);if(!c.width||!c.height||r.width<=0||r.height<=0||r.bottom<=0||r.right<=0||r.top>=innerHeight||r.left>=innerWidth||style.visibility==='hidden'||style.display==='none'||Number(style.opacity)===0)continue;if(c.width*c.height>budget){warnings.push('Canvas readback exceeds pixel budget');continue}budget-=c.width*c.height;try{const png=c.toDataURL('image/png');if(png.indexOf('data:image/png;base64,')!==0)throw Error('No PNG returned');images.push({selector:'[data-harness-browser-id=\"'+c.getAttribute('data-harness-browser-id')+'\"]',x:r.x,y:r.y,cssWidth:r.width,cssHeight:r.height,width:c.width,height:c.height,image:png.slice(png.indexOf(',')+1)})}catch(e){warnings.push('Canvas readback unavailable: '+String(e))}}return {images,warnings}})()";
                evaluate(readback,result,pixels->{
                    JSONObject layers=(JSONObject)pixels;state.put("canvasImages",layers.getJSONArray("images"));state.put("captureWarnings",layers.getJSONArray("warnings"));
                    state.put("captureMethod","virtual-display-frame-plus-canvas-readbacks");
                    state.put("captureNote","The viewport is a rendered frame. Separate canvas images are actual pixel readbacks, supplied because Android WebView may omit accelerated canvas layers from the viewport capture. They are not a claim that those pixels were composited correctly into the viewport. Animation may advance between captures; WebGL with a discarded drawing buffer may read back blank.");
                    capture(state,result);
                });
            }else result.complete(state);
        });
    }
    private void capture(JSONObject state,CompletableFuture<JSONObject> result){
        final long start=SystemClock.uptimeMillis();
        final boolean[] ready={false};
        web.postVisualStateCallback(start,new WebView.VisualStateCallback(){
            @Override public void onComplete(long requestId){ready[0]=true;web.invalidate();getWindow().getDecorView().invalidate();}
        });
        web.invalidate();
        Runnable wait=new Runnable(){public void run(){
            if(result.isCancelled())return;
            if(destroyed){BrowserController.fail(result,new IOException("Браузер закрыт"));return;}
            if(ready[0]&&SystemClock.uptimeMillis()-start>=150&&latestFrame!=null&&latestFrame.getWidth()==width&&latestFrame.getHeight()==height){
                try{
                    Bitmap bitmap=frameBitmap();ByteArrayOutputStream bytes=new ByteArrayOutputStream();try{bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes);}finally{bitmap.recycle();}
                    state.put("image",android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP));
                    state.put("imageWidth",width);state.put("imageHeight",height);result.complete(state);
                }catch(Exception error){BrowserController.fail(result,error);}
            }else if(SystemClock.uptimeMillis()-start>5000)BrowserController.fail(result,new IOException("Нет свежего аппаратного кадра браузера; повторите снимок (ready="+ready[0]+", frames="+frameNumber+")"));
            else ui.postDelayed(this,25);
        }};
        ui.post(wait);
    }
    void closeBrowser(){
        if(destroyed)return;destroyed=true;
        BrowserController.detached(this);
        if(navigation!=null)BrowserController.fail(navigation,new IOException("Браузер закрыт"));navigation=null;
        if(web!=null){web.stopLoading();web.destroy();}
        if(files!=null)files.close();
        dismiss();display.setSurface(null);display.release();
        if(latestFrame!=null){latestFrame.close();latestFrame=null;}frames.close();
    }
    @Override public void onDisplayRemoved(){closeBrowser();}
}
