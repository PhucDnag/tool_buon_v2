import java.io.*;
import java.util.Properties;

/** Receive per-account settings over a private pipe, never in process arguments. */
public final class ManagerLauncher {
    public static Properties read(InputStream input) throws IOException {
        Properties values=new Properties();
        BufferedReader reader=new BufferedReader(new InputStreamReader(input,"UTF-8"));
        String line;
        while ((line=reader.readLine())!=null) {
            int eq=line.indexOf('=');
            if (eq<1 || (line.length()-eq-1)%2!=0) throw new IOException("Invalid launch settings");
            String key=line.substring(0,eq);
            if (!key.startsWith("phuc.") && !key.equals("user.home")) throw new IOException("Invalid launch key");
            byte[] bytes=new byte[(line.length()-eq-1)/2];
            for (int i=0;i<bytes.length;i++) {
                int hi=Character.digit(line.charAt(eq+1+i*2),16),lo=Character.digit(line.charAt(eq+2+i*2),16);
                if (hi<0 || lo<0) throw new IOException("Invalid launch encoding");
                bytes[i]=(byte)(hi*16+lo);
            }
            values.setProperty(key,new String(bytes,"UTF-8"));
        }
        return values;
    }
    public static void initializeProfile(String[] args) throws Exception {
        String id=null;
        for (int i=0;i<args.length;i++) {
            if ("--id".equals(args[i])) {
                if (id!=null || ++i>=args.length) throw new IOException("Invalid profile ID");
                id=args[i];
            }
        }
        if (id==null || !id.matches("[A-Za-z0-9_-]+")) throw new IOException("Missing or invalid profile ID");
        // Main's constructor loads Config before Common processes --id. Set it
        // first, otherwise Config permanently caches the shared RMS directory.
        Class.forName("org.microemu.app.Config").getMethod("setEmulatorID",String.class).invoke(null,id);
    }
    public static void main(String[] args) throws Exception {
        Properties values=read(System.in);
        for (Object key:values.keySet()) System.setProperty((String)key,values.getProperty((String)key));
        initializeProfile(args);
        Class.forName("org.microemu.app.Main").getMethod("main",String[].class).invoke(null,(Object)args);
    }
}
