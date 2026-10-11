package org.dsh.mobile;
import java.io.*;
import java.util.zip.*;
public class ArchiveCheck {
 public static void main(String[] args)throws Exception{
  InputStream input=new BufferedInputStream(new FileInputStream(args[0]));
  for(String path:new String[]{"Harness-Local-arm64-preview.apk","assets/runtime.zip"}){
   ZipInputStream layer=new ZipInputStream(input);input=layer;ZipEntry entry;boolean found=false;
   while((entry=layer.getNextEntry())!=null)if(path.equals(entry.getName())){found=true;break;}
   if(!found)throw new AssertionError(path);
  }
  int count=0;long bytes=0;byte[] buffer=new byte[65536];
  try(ZipInputStream zip=new ZipInputStream(input)){ZipEntry entry;while((entry=zip.getNextEntry())!=null){int n;while((n=zip.read(buffer))!=-1)bytes+=n;count++;}}
  if(count<40000 || bytes<1200000000L)throw new AssertionError();
  System.out.println("PASS streamed nested extraction and CRC: "+count+" entries, "+bytes+" bytes");
 }
}
