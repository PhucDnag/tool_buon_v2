package autotrade;
import java.io.*;

/** Separate mailbox keeps performance settings independent of auto commands. */
public final class RuntimeMaintenance {
    private long nextPoll, nextGc, nextPaint, lastGold=-1, lastUnknown=-1;
    private int gcMinutes;
    private boolean background;
    public boolean paint(long now) {
        if (!background) return true;
        if (now<nextPaint) return false;
        nextPaint=now+1000; return true;
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
            File dest=new File(control+".stats"), tmp=new File(control+".stats.tmp");
            Writer out=new FileWriter(tmp);
            try { out.write(earnings.gold+":"+earnings.unmeasured+"\n"); } finally { out.close(); }
            if (dest.exists() && !dest.delete()) return;
            if (!tmp.renameTo(dest)) return;
            lastGold=earnings.gold; lastUnknown=earnings.unmeasured;
        } catch (IOException ignored) {}
    }
}
