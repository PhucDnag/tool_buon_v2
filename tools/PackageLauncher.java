import java.io.*;
import java.util.jar.*;
public final class PackageLauncher {
    public static void main(String[] args) throws Exception {
        JarOutputStream out=new JarOutputStream(new FileOutputStream(args[1]));
        try {
            out.putNextEntry(new JarEntry("ManagerLauncher.class"));
            FileInputStream in=new FileInputStream(args[0]);
            try { byte[] bytes=new byte[8192]; int n; while((n=in.read(bytes))!=-1) out.write(bytes,0,n); }
            finally { in.close(); }
            out.closeEntry();
        } finally { out.close(); }
    }
}
