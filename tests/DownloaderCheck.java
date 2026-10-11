package org.dsh.mobile;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import com.sun.net.httpserver.*;
import java.util.*;
public class DownloaderCheck {
 static byte[] data = new byte[524288];
 static String hash(byte[] bytes) throws Exception { StringBuilder out=new StringBuilder(); for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))out.append(String.format("%02x",b&255));return out.toString(); }
 static CoreDownloader.Listener listener=new CoreDownloader.Listener(){public void progress(long a,long b){}public void message(String s){System.out.println(s);}};
 static void send(HttpExchange e,int code,byte[] b)throws IOException{e.sendResponseHeaders(code,b.length);try(OutputStream out=e.getResponseBody()){out.write(b);}}
 public static void main(String[]args)throws Exception{
  if(args.length>0){File f=new CoreDownloader().download(args[0],Long.parseLong(args[1]),args[2],new File(args[3]),listener);System.out.println("REMOTE VERIFIED "+f.length());return;}
  new Random(17).nextBytes(data);String sha=hash(data);
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/file",e->{String range=e.getRequestHeaders().getFirst("Range");int start=range==null?0:Integer.parseInt(range.substring(6,range.length()-1));if(start>0){e.getResponseHeaders().set("Content-Range","bytes "+start+"-"+(data.length-1)+"/"+data.length);}send(e,start>0?206:200,Arrays.copyOfRange(data,start,data.length));});
  server.createContext("/cookie",e->{if(e.getRequestHeaders().getFirst("Cookie")==null){e.getResponseHeaders().set("Set-Cookie","verified=yes; Path=/");e.getResponseHeaders().set("Content-Type","text/html");send(e,200,"verify".getBytes());}else{e.getResponseHeaders().set("Location","/file");e.sendResponseHeaders(302,-1);e.close();}});
  server.createContext("/ignore",e->send(e,200,data));
  server.createContext("/bad",e->{byte[] bad=data.clone();bad[0]^=1;send(e,200,bad);});
  server.start();String base="http://127.0.0.1:"+server.getAddress().getPort();
  try{
   for(String route:new String[]{"/cookie","/ignore"}){File dir=Files.createTempDirectory("dsh-check").toFile();Files.write(new File(dir,"runtime.zip.part").toPath(),Arrays.copyOf(data,9217));File result=new CoreDownloader().download(base+route,data.length,sha,dir,listener);if(!Arrays.equals(data,Files.readAllBytes(result.toPath())))throw new AssertionError();System.out.println("PASS resume/cookie/redirect "+route);}
   File dir=Files.createTempDirectory("dsh-bad").toFile();try{new CoreDownloader().download(base+"/bad",data.length,sha,dir,listener);throw new AssertionError("Bad hash accepted");}catch(IOException expected){if(new File(dir,"runtime.zip").exists())throw new AssertionError();System.out.println("PASS bad hash rejected");}
   CoreDownloader cancelled=new CoreDownloader();cancelled.cancel();try{cancelled.download(base+"/file",data.length,sha,Files.createTempDirectory("dsh-cancel").toFile(),listener);throw new AssertionError();}catch(InterruptedIOException expected){System.out.println("PASS cancel");}
  }finally{server.stop(0);}
 }
}
