package org.dsh.mobile;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.system.Os;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.UUID;

/** Private native provider editor; secrets never pass through the WebView. */
final class MobileProviders {
    private static final int BG=Color.rgb(20,20,20), CARD=Color.rgb(36,38,43), TEXT=Color.rgb(235,238,246), MUTED=Color.rgb(153,163,184), ACCENT=Color.rgb(161,184,255);
    private static int dp(Context c,int n){return (int)(n*c.getResources().getDisplayMetrics().density+.5f);}
    private static File location(Context c){return new File(c.getFilesDir(),"home/.harness-mobile-providers.json");}
    private static JSONArray load(Context c)throws Exception{
        File f=location(c);if(!f.exists())return new JSONArray();
        try(InputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
            return new JSONObject(out.toString("UTF-8")).getJSONArray("providers");
        }
    }
    private static void store(Context c,JSONArray providers)throws Exception{
        File file=location(c);if(!file.getParentFile().isDirectory()&&!file.getParentFile().mkdirs())throw new IOException("Не удалось открыть хранилище");
        File next=new File(file.getParentFile(),file.getName()+".next");
        try(FileOutputStream out=new FileOutputStream(next)){out.write(new JSONObject().put("providers",providers).toString().getBytes("UTF-8"));out.getFD().sync();}
        Os.chmod(next.getPath(),0600);Os.rename(next.getPath(),file.getPath());
    }
    private static TextView text(Context c,String value,int size,int color){TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
    private static GradientDrawable background(Context c,int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,14));d.setStroke(dp(c,1),Color.rgb(57,62,74));return d;}
    private static TextView button(Context c,String title,Runnable action){TextView b=text(c,title,14,ACCENT);b.setGravity(Gravity.CENTER);b.setPadding(dp(c,12),dp(c,10),dp(c,12),dp(c,10));b.setMinHeight(dp(c,44));b.setBackground(background(c,CARD));b.setOnClickListener(v->action.run());b.setContentDescription(title);return b;}
    private static Dialog dialog(Activity a,String title,LinearLayout body){
        Dialog d=new Dialog(a,android.R.style.Theme_Material_NoActionBar);
        LinearLayout root=new LinearLayout(a);root.setOrientation(1);root.setBackgroundColor(BG);
        LinearLayout header=new LinearLayout(a);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(a,10),0,dp(a,16),0);
        TextView back=button(a,"‹",d::dismiss);back.setTextSize(28);header.addView(back,new LinearLayout.LayoutParams(dp(a,44),dp(a,48)));
        TextView label=text(a,title,20,TEXT);label.setTypeface(null,Typeface.BOLD);label.setPadding(dp(a,12),0,0,0);header.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(header,new LinearLayout.LayoutParams(-1,dp(a,60)));
        ScrollView scroll=new ScrollView(a);scroll.setFillViewport(true);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        d.setContentView(root);Window w=d.getWindow();if(w!=null){w.setStatusBarColor(BG);w.setNavigationBarColor(BG);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);w.setLayout(-1,-1);}return d;
    }
    private static LinearLayout body(Activity a){LinearLayout b=new LinearLayout(a);b.setOrientation(1);b.setPadding(dp(a,20),dp(a,12),dp(a,20),dp(a,24));return b;}
    private static void add(LinearLayout parent,View child,int gap){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(parent.getContext(),gap);parent.addView(child,p);}
    static void show(Activity a,Runnable apply){
        try{
            JSONArray providers=load(a);LinearLayout body=body(a);Dialog page=dialog(a,"Модели",body);
            add(body,text(a,"OPENAI-СОВМЕСТИМЫЕ API",11,MUTED),10);
            add(body,text(a,"Gemini, прокси или локальный сервер — через Base URL, токен и имя модели.",15,TEXT),20);
            for(int i=0;i<providers.length();i++){
                JSONObject provider=providers.getJSONObject(i);
                LinearLayout card=new LinearLayout(a);card.setOrientation(1);card.setPadding(dp(a,16),dp(a,14),dp(a,16),dp(a,14));card.setBackground(background(a,CARD));
                TextView name=text(a,provider.optString("name"),17,TEXT);name.setTypeface(null,Typeface.BOLD);add(card,name,6);
                add(card,text(a,provider.optString("model"),13,ACCENT),6);
                TextView url=text(a,provider.optString("baseURL"),12,MUTED);url.setSingleLine(true);url.setEllipsize(android.text.TextUtils.TruncateAt.END);add(card,url,10);
                add(card,button(a,"Изменить",()->{page.dismiss();editor(a,provider,apply);}),0);add(body,card,14);
            }
            add(body,button(a,"+ Добавить API",()->{page.dismiss();editor(a,null,apply);}),20);
            add(body,text(a,"Инструменты подключаются автоматически через tool calling. Сервер и модель должны поддерживать вызовы функций.",13,MUTED),14);
            add(body,text(a,"После сохранения ядро перезапустится. Затем выберите добавленную модель в поле сообщения. Вход DeepSeek остаётся в меню аккаунта Harness.",13,MUTED),0);
            page.show();page.getWindow().setLayout(-1,-1);
        }catch(Exception error){Toast.makeText(a,"Не удалось прочитать настройки моделей",Toast.LENGTH_LONG).show();RuntimeService.log("Ошибка настроек моделей: "+error.getClass().getSimpleName());}
    }
    private static EditText field(Activity a,LinearLayout body,String label,String value,String hint,int type){
        add(body,text(a,label,12,MUTED),6);
        EditText input=new EditText(a);input.setSingleLine(true);input.setTextSize(15);input.setTextColor(TEXT);input.setHintTextColor(MUTED);input.setInputType(type);input.setText(value);input.setHint(hint);input.setContentDescription(label);input.setPadding(dp(a,12),0,dp(a,12),0);input.setBackground(background(a,CARD));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(a,50));p.bottomMargin=dp(a,18);body.addView(input,p);return input;
    }
    private static void editor(Activity a,JSONObject existing,Runnable apply){
        LinearLayout body=body(a);Dialog page=dialog(a,existing==null?"Добавить API":"Изменить API",body);
        LinearLayout presets=new LinearLayout(a);body.addView(presets);
        EditText name=field(a,body,"Название",existing==null?"":existing.optString("name"),"Например, Gemini",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        EditText url=field(a,body,"Base URL",existing==null?"":existing.optString("baseURL"),"https://…/v1",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        EditText token=field(a,body,"Токен / API key",existing==null?"":existing.optString("token"),"Ключ провайдера",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText model=field(a,body,"Имя модели (Model ID)",existing==null?"":existing.optString("model"),"gemini-2.5-flash",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        EditText maxTokens=field(a,body,"Лимит ответа (токены)",String.valueOf(existing==null?8192:existing.optInt("maxTokens",8192)),"Например, 32768",InputType.TYPE_CLASS_NUMBER);
        add(body,text(a,"Для длинного кода попробуйте 32768, если модель поддерживает такой лимит. В бюджет ответа могут входить рассуждения. Оплачиваются использованные токены; больший лимит позволяет модели потратить больше.",13,MUTED),18);
        presets.setPadding(0,0,0,dp(a,20));
        TextView gemini=button(a,"Gemini",()->{name.setText("Gemini");url.setText("https://generativelanguage.googleapis.com/v1beta/openai/");model.setText("gemini-2.5-flash");});
        presets.addView(gemini,new LinearLayout.LayoutParams(0,dp(a,46),1));
        TextView custom=button(a,"Свой API",()->{name.setText("");url.setText("");model.setText("");});LinearLayout.LayoutParams presetLayout=new LinearLayout.LayoutParams(0,dp(a,46),1);presetLayout.leftMargin=dp(a,10);presets.addView(custom,presetLayout);
        add(body,text(a,"Протокол: OpenAI Chat Completions\nИнструменты: tool calling\nТокен сохраняется только в закрытом хранилище приложения.",13,MUTED),20);
        TextView error=text(a,"",13,Color.rgb(255,140,150));add(body,error,8);
        add(body,button(a,"Сохранить и перезапустить",()->{
            try{
                String address=url.getText().toString().trim();URI uri=new URI(address);
                if(!("https".equals(uri.getScheme())||"http".equals(uri.getScheme()))||uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null||uri.getQuery()!=null)throw new IllegalArgumentException("Введите Base URL http(s) без параметров и пароля");
                if(name.getText().toString().trim().isEmpty()||model.getText().toString().trim().isEmpty())throw new IllegalArgumentException("Укажите название и точный Model ID");
                int outputLimit;
                try{outputLimit=Integer.parseInt(maxTokens.getText().toString().trim());}catch(NumberFormatException ex){throw new IllegalArgumentException("Лимит ответа должен быть целым числом");}
                int context=existing==null?262144:existing.optInt("contextWindow",262144);
                if(outputLimit<1||outputLimit>context)throw new IllegalArgumentException("Лимит ответа должен быть от 1 до "+context);
                String id=existing==null?"mobile-"+UUID.randomUUID().toString().substring(0,8):existing.getString("id");
                JSONObject row=new JSONObject().put("id",id).put("name",name.getText().toString().trim()).put("baseURL",address).put("token",token.getText().toString().trim()).put("model",model.getText().toString().trim()).put("contextWindow",context).put("maxTokens",outputLimit);
                JSONArray current=load(a),next=new JSONArray();boolean updated=false;
                for(int i=0;i<current.length();i++){JSONObject value=current.getJSONObject(i);if(id.equals(value.getString("id"))){next.put(row);updated=true;}else next.put(value);}
                if(!updated)next.put(row);store(a,next);page.dismiss();apply.run();
                Toast.makeText(a,"Настройки сохранены. Ядро перезапускается.",Toast.LENGTH_LONG).show();
            }catch(IllegalArgumentException ex){error.setText(ex.getMessage());}catch(Exception ex){error.setText("Не удалось сохранить настройки");RuntimeService.log("Ошибка сохранения моделей: "+ex.getClass().getSimpleName());}
        }),16);
        if(existing!=null)add(body,button(a,"Удалить подключение",()->{
            try{JSONArray current=load(a),next=new JSONArray();for(int i=0;i<current.length();i++){JSONObject row=current.getJSONObject(i);if(!existing.optString("id").equals(row.optString("id")))next.put(row);}store(a,next);page.dismiss();apply.run();}catch(Exception ex){error.setText("Не удалось удалить подключение");}
        }),12);
        page.show();page.getWindow().setLayout(-1,-1);page.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
}
