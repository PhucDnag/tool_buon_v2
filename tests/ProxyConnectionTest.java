import autotrade.ProxyConnection;
import java.net.*;
import java.io.*;

public class ProxyConnectionTest {
    static Class socketType=CLib.mSocket.class;
    static Class proxyType=ProxyConnection.class;
    static String hud() throws Exception { return (String)proxyType.getMethod("hud").invoke(null); }
    static Object open() throws Exception {
        try { return socketType.getConstructor(String.class,Integer.TYPE).newInstance("game.invalid",19129); }
        catch (java.lang.reflect.InvocationTargetException e) {
            if(e.getCause() instanceof IOException) throw (IOException)e.getCause();
            throw e;
        }
    }
    static void check(boolean ok,String why) { if (!ok) throw new AssertionError(why); }
    static void scenario(final int mode) throws Exception {
        final ServerSocket server=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"));
        final Throwable[] failure=new Throwable[1];
        System.setProperty("phuc.proxy.enabled","1");
        System.setProperty("phuc.proxy.host","127.0.0.1");
        System.setProperty("phuc.proxy.port",""+server.getLocalPort());
        System.setProperty("phuc.proxy.user",mode==1 ? "" : "test-user");
        System.setProperty("phuc.proxy.pass","test-secret");
        Thread worker=new Thread(new Runnable() { public void run() {
            try {
                Socket socket=server.accept(); socket.setSoTimeout(3000);
                try {
                    DataInputStream in=new DataInputStream(socket.getInputStream());
                    DataOutputStream out=new DataOutputStream(socket.getOutputStream());
                    check(in.readUnsignedByte()==5 && in.readUnsignedByte()==1,"SOCKS5 greeting");
                    int method=in.readUnsignedByte(); check(method==(mode==1 ? 0 : 2),"Authentication selected explicitly");
                    out.write(new byte[]{5,(byte)method}); out.flush();
                    if (method==2) {
                        check(in.readUnsignedByte()==1,"RFC1929 version");
                        byte[] user=new byte[in.readUnsignedByte()]; in.readFully(user);
                        byte[] pass=new byte[in.readUnsignedByte()]; in.readFully(pass);
                        check(new String(user,"UTF-8").equals("test-user") && new String(pass,"UTF-8").equals("test-secret"),"Credentials transmitted correctly");
                        out.write(new byte[]{1,(byte)(mode==2 ? 1 : 0)}); out.flush();
                        if (mode==2) { check(in.read()==-1,"Auth failure closes connection"); return; }
                    }
                    check(in.readUnsignedByte()==5 && in.readUnsignedByte()==1 && in.readUnsignedByte()==0 && in.readUnsignedByte()==3,"CONNECT uses proxy-side target lookup");
                    byte[] host=new byte[in.readUnsignedByte()]; in.readFully(host);
                    check(new String(host,"UTF-8").equals("game.invalid") && in.readUnsignedShort()==19129,"Correct game destination");
                    out.write(new byte[]{5,(byte)(mode==3 ? 5 : 0),0,1,127,0,0,1,0,1}); out.flush();
                    if (mode==3) { check(in.read()==-1,"CONNECT failure closes connection"); return; }
                    out.writeByte(42); out.flush(); check(in.readUnsignedByte()==43,"Game data carried inside tunnel");
                } finally { socket.close(); }
            } catch (Throwable ex) { failure[0]=ex; }
        }});
        worker.start();
        try {
            try {
                Object socket=open();
                check(mode<2,"Failure must not return a socket or fall back to direct");
                check(((DataInputStream)socketType.getMethod("getInputStream").invoke(socket)).readUnsignedByte()==42,"Tunnel payload received");
                ((DataOutputStream)socketType.getMethod("getOutputStream").invoke(socket)).writeByte(43);
                check(hud().indexOf("SOCKS5")>=0,"HUD confirms tunnel");
                socketType.getMethod("close").invoke(socket);
            } catch (IOException ex) { if (mode<2) throw new AssertionError("Unexpected connection failure: "+ex+"; mock="+failure[0],ex); }
            worker.join(5000); check(!worker.isAlive(),"Mock proxy completed");
            if (failure[0]!=null) throw new AssertionError(failure[0]);
            check(hud().indexOf("test-secret")<0,"HUD never exposes credentials");
        } finally { server.close(); }
    }
    public static void main(String[] args) throws Exception {
        for (int i=0;i<4;i++) scenario(i);
        org.microemu.app.classloader.MIDletClassLoader loader=new org.microemu.app.classloader.MIDletClassLoader(ProxyConnectionTest.class.getClassLoader());
        loader.configure(new org.microemu.app.classloader.MIDletClassLoaderConfig(),false);
        loader.disableClassPreporcessing(org.microemu.Injected.class);
        loader.disableClassPreporcessing(org.microemu.app.util.MIDletThread.class);
        loader.disableClassPreporcessing(org.microemu.app.util.MIDletTimer.class);
        loader.disableClassPreporcessing(org.microemu.app.util.MIDletTimerTask.class);
        loader.addURL(new File(args.length>0 ? args[0] : "dist/KnightOnline_PhucHso_v2.jar").toURI().toURL());
        socketType=loader.loadClass("CLib.mSocket"); proxyType=loader.loadClass("autotrade.ProxyConnection");
        for (int i=0;i<4;i++) scenario(i);
        System.out.println("PASS actual MicroEmulator classloader SOCKS5 transport");
        System.setProperty("phuc.proxy.enabled","0");
        check(!ProxyConnection.enabled(),"Direct mode remains explicit");
        System.out.println("PASS SOCKS5 authenticated/unauthenticated tunnel, auth rejection, CONNECT rejection, no fallback, HUD privacy");
    }
}
