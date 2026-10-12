package org.dsh.mobile;

import android.content.Context;
import android.system.Os;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.*;

/** Authenticated loopback RPC. The private endpoint is shared only with the local Harness. */
final class BrowserBridge implements Closeable {
    private final ServerSocket listener;
    private final String token = UUID.randomUUID().toString()+UUID.randomUUID().toString();
    private final File endpoint;
    private final Context context;
    private final ExecutorService clients = new ThreadPoolExecutor(1,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(8));
    private volatile boolean closed;
    BrowserBridge(Context context) throws Exception {
        this.context=context.getApplicationContext();
        File home=new File(context.getFilesDir(),"home"); if(!home.isDirectory()&&!home.mkdirs())throw new IOException("Не удалось создать домашнюю папку браузера");
        endpoint=new File(home,".harness-browser-bridge.json");
        listener=new ServerSocket(0,8,InetAddress.getByName("127.0.0.1"));
        JSONObject config=new JSONObject().put("url","http://127.0.0.1:"+listener.getLocalPort()+"/rpc").put("token",token);
        File pending=new File(home,".harness-browser-bridge.next");
        try(FileOutputStream out=new FileOutputStream(pending)){out.write(config.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
        Os.chmod(pending.getPath(),0600);Os.rename(pending.getPath(),endpoint.getPath());
        Thread accept=new Thread(()->{
            while(!closed)try{
                Socket socket=listener.accept();socket.setSoTimeout(10000);
                try{clients.execute(()->serve(socket));}catch(RejectedExecutionException busy){socket.close();}
            }catch(IOException ignored){if(!closed)close();}
        },"browser-rpc");accept.setDaemon(true);accept.start();
    }
    private static String line(InputStream input)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();int c;
        while((c=input.read())!=-1&&c!='\n'){if(out.size()>8192)throw new IOException("Header too long");if(c!='\r')out.write(c);}
        return out.toString("US-ASCII");
    }
    private void serve(Socket socket){
        try(Socket connection=socket){
            InputStream in=new BufferedInputStream(socket.getInputStream());OutputStream out=new BufferedOutputStream(socket.getOutputStream());
            String request=line(in),auth="",host="";int length=0;
            for(int n=0;;n++){
                if(n>64)throw new IOException("Too many headers");String header=line(in);if(header.isEmpty())break;
                int colon=header.indexOf(':');if(colon<0)continue;
                String key=header.substring(0,colon),value=header.substring(colon+1).trim();
                if(key.equalsIgnoreCase("Authorization"))auth=value;
                if(key.equalsIgnoreCase("Host"))host=value;
                if(key.equalsIgnoreCase("Content-Length"))length=Integer.parseInt(value);
            }
            if(!request.equals("POST /rpc HTTP/1.1")||!host.equals("127.0.0.1:"+listener.getLocalPort())||!auth.equals("Bearer "+token)){reply(out,403,"{\"error\":\"Unauthorized browser request\"}");return;}
            if(length<1||length>65536){reply(out,400,"{\"error\":\"Invalid request size\"}");return;}
            byte[] body=new byte[length];new DataInputStream(in).readFully(body);
            CompletableFuture<JSONObject> call=BrowserController.perform(context,new JSONObject(new String(body,StandardCharsets.UTF_8)));
            try{reply(out,200,call.get(40,TimeUnit.SECONDS).toString());}
            catch(TimeoutException timeout){call.cancel(false);reply(out,504,"{\"error\":\"Browser timed out; keep the app in the foreground and retry\"}");}
        }catch(Exception ignored){ }
    }
    private static void reply(OutputStream out,int code,String value)throws IOException{
        byte[] bytes=value.getBytes(StandardCharsets.UTF_8);
        out.write(("HTTP/1.1 "+code+" Response\r\nContent-Type: application/json\r\nContent-Length: "+bytes.length+"\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(bytes);out.flush();
    }
    @Override public void close(){closed=true;try{listener.close();}catch(IOException ignored){}clients.shutdownNow();endpoint.delete();BrowserController.shutdown();}
}
