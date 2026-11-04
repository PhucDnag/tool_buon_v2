package autotrade;
import java.io.*;

/** Separate mailbox keeps performance settings independent of auto commands. */
public final class RuntimeMaintenance {
    private long nextPoll, nextGc, nextPaint, lastGold=-1, lastUnknown=-1;
    private int gcMinutes;
    private boolean background;
    private long nextPeriodicGc, nextLowMemGc;
    public boolean paint(long now) {
        if (!background) return true;
        if (now<nextPaint) return false;
        nextPaint=now+1000; return true;
    }
    public void checkMemory(long now) {
        if (nextPeriodicGc==0) nextPeriodicGc=now+180000L;
        Runtime rt=Runtime.getRuntime();
        long free=rt.freeMemory();
        if (free<16*1024*1024L && now>=nextLowMemGc) {
            nextLowMemGc=now+15000L;
            nextPeriodicGc=now+180000L;
            System.gc();
        } else if (now>=nextPeriodicGc) {
            nextPeriodicGc=now+180000L;
            System.gc();
        }
    }
    public void onMapChange(long now) {
        nextPeriodicGc=now+180000L;
        System.gc();
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
