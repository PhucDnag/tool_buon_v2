package autotrade;
import java.io.*;

/** Separate mailbox keeps performance settings independent of auto commands. */
public final class RuntimeMaintenance {
    private static long unansweredSince;
    private long nextHealth;
    public static synchronized void sent() {
        if (unansweredSince==0) unansweredSince=System.currentTimeMillis();
    }
    public static synchronized void received() { unansweredSince=0; }
    private static synchronized long pendingSince(boolean armed) {
        if (!armed) unansweredSince=0;
        return unansweredSince;
    }
    public void health(long now,String control,boolean armed) {
        long pending=pendingSince(armed);
        if (control==null || now<nextHealth) return;
        nextHealth=now+1000;
        try {
            Writer out=new FileWriter(control+".health");
            try { out.write(now+":"+pending+":"+(armed ? "1" : "0")+"\n"); }
            finally { out.close(); }
        } catch (IOException ignored) {}
    }
    private long nextPoll, nextGc, nextPaint, lastGold=-1, lastUnknown=-1;
    private int gcMinutes;
    private boolean background;
    private long nextMemoryCheck, nextLowMemGc;
    public boolean paint(long now) {
        if (!background) return true;
        if (now<nextPaint) return false;
        nextPaint=now+1000; return true;
    }
    public void checkMemory(long now) {
        checkMemory(now,true);
    }
    public static boolean memoryPressure(long free,long total,long max) {
        return free+Math.max(0,max-total)<16*1024*1024L;
    }
    public void checkMemory(long now,boolean safeToCollect) {
        if (!safeToCollect || now<nextMemoryCheck) return;
        nextMemoryCheck=now+1000;
        Runtime rt=Runtime.getRuntime();
        // A small committed heap can still grow. Do not force GC every few
        // seconds merely because its currently unused portion is small.
        if (memoryPressure(rt.freeMemory(),rt.totalMemory(),rt.maxMemory()) && now>=nextLowMemGc) {
            nextLowMemGc=now+60000L;
            System.gc();
        }
    }
    public void onMapChange(long now) {
        // Let map loading finish before any optional collection.
        nextMemoryCheck=now+5000;
    }
    public void update(long now, String control, TradeEarnings earnings, boolean safeToCollect) {
        if (control==null || now<nextPoll) return;
        nextPoll=now+1000;
        try {
            File config=new File(control+".runtime");
            if (config.isFile()) {
                BufferedReader in=new BufferedReader(new FileReader(config));
                String line; try { line=in.readLine(); } finally { in.close(); }
                if (line!=null) {
                    int split=line.indexOf(':');
                    if (split>0) {
                        int minutes=Integer.parseInt(line.substring(split+1));
                        minutes=Math.max(0,Math.min(120,minutes));
                        background=line.charAt(0)=='1';
                        if (minutes!=gcMinutes) { gcMinutes=minutes; nextGc=now+minutes*60000L; }
                    }
                }
            }
        } catch (Exception ignored) {}
        if (gcMinutes>0 && safeToCollect && now>=nextGc) {
            nextGc=now+gcMinutes*60000L;
            System.gc();
        }
        if (earnings.gold==lastGold && earnings.unmeasured==lastUnknown) return;
        try {
            File dest=new File(control+".stats");
            Writer out=new FileWriter(dest,false);
            try { out.write(earnings.gold+":"+earnings.unmeasured+"\n"); out.flush(); } finally { out.close(); }
            lastGold=earnings.gold; lastUnknown=earnings.unmeasured;
        } catch (IOException ignored) {}
    }
}
