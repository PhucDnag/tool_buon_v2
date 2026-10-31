package autotrade;

import java.io.*;

/** Local diagnostic metadata only: never record packet payloads or credentials. */
public final class ConnectionTrace {
    private static PrintStream output;
    private static boolean opened;
    private static final String[] recent=new String[80];
    private static int cursor;
    private static int events;
    public static synchronized void packet(int command) {
        recent[cursor++%recent.length]=System.currentTimeMillis()+" SEND command="+command;
    }
    public static synchronized void event(String event) {
        try {
            if (!opened) {
                opened=true;
                File folder=new File("logs"); folder.mkdirs();
                output=new PrintStream(new FileOutputStream(new File(folder,"connection-"+System.currentTimeMillis()+".log")));
            }
            if (output==null || events++>=500) return;
            output.println(System.currentTimeMillis()+" "+event);
            if ("DISCONNECTED".equals(event)) {
                for (int i=Math.max(0,cursor-recent.length);i<cursor;i++) output.println(recent[i%recent.length]);
            }
            output.flush();
        } catch (Exception ignored) {}
    }
    public static void failure(Throwable error) {
        event("NETWORK_ERROR "+error.getClass().getName());
    }
}
