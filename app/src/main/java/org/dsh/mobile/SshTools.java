package org.dsh.mobile;
import android.content.Context;
import android.system.Os;
import java.io.*;
import java.util.zip.*;
/** A small app-bundled addition to an existing core; never reinstalls it. */
final class SshTools {
    static void install(Context context, File root) throws Exception {
        File stamp=new File(root,"opt/dsh/.mobile-ssh-version");
        if(stamp.isFile() && new File(root,"usr/bin/ssh").isFile() && new File(root,"usr/bin/sshpass").isFile()) return;
        String base=root.getCanonicalPath()+File.separator;
        try(ZipInputStream zip=new ZipInputStream(context.getAssets().open("ssh-tools-v1.zip"))){
            ZipEntry entry;
            byte[] buffer=new byte[65536];
            while((entry=zip.getNextEntry())!=null){
                if(entry.isDirectory()) continue;
                String name=entry.getName();
                if(!(name.startsWith("usr/") || name.equals("etc/ssh/ssh_config")))throw new IOException("Invalid SSH payload path");
                File dest=new File(root,name);
                if(!dest.getCanonicalPath().startsWith(base))throw new IOException("SSH payload outside runtime");
                // Leave user-installed client upgrades and configuration untouched.
                if(dest.exists())continue;
                File parent=dest.getParentFile();
                if(!parent.isDirectory() && !parent.mkdirs())throw new IOException("mkdir SSH tools");
                File pending=new File(parent,dest.getName()+".mobile-ssh-next");
                try(FileOutputStream out=new FileOutputStream(pending)){
                    int n;while((n=zip.read(buffer))!=-1)out.write(buffer,0,n);out.getFD().sync();
                }
                Os.chmod(pending.getPath(),name.startsWith("usr/bin/")?0755:0644);
                Os.rename(pending.getPath(),dest.getPath());
            }
        }
        try(FileOutputStream out=new FileOutputStream(stamp)){out.write("ssh-tools-v1\n".getBytes("UTF-8"));out.getFD().sync();}
        RuntimeService.log("SSH-клиенты установлены: ssh, scp, sftp. Ядро и данные сохранены.");
    }
}
