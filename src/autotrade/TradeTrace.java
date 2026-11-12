package autotrade;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;

/** Bounded local state diagnostics; no credentials, chat or packet payloads. */
public final class TradeTrace {
    private PrintStream out;
    private boolean opened;
    private int count;
    private String previous="";
    private long next;
    public void record(long now,int state,int map,int animal,int cargo,int hp,int x,int y,int bx,int by,int intent,boolean drops) {
        if ("false".equals(System.getProperty("phuc.tradeTrace"))) return;
        String key=state+":"+map+":"+animal+":"+cargo+":"+intent+":"+drops;
        if (key.equals(previous) && now<next || count>=20000) return;
        previous=key; next=now+15000;
        write(now+" state="+state+" map="+map+" animal="+animal+" cargo="+cargo
                +" hp="+hp+" player="+x+","+y+" beast="+bx+","+by+" intent="+intent+" drops="+drops);
    }
    public void response(long now,int state,int intent,int type,int signal) {
        write(now+" response="+type+" signal="+signal+" state="+state+" intent="+intent);
    }
    public void action(long now,String action,int hp,int maxHp,int cargo) {
        write(now+" action="+action+" hp="+hp+"/"+maxHp+" cargo="+cargo);
    }
    public void disappeared(long now,int animal,boolean stopped,boolean removed,int hp) {
        write(now+" event=ANIMAL_NOT_VISIBLE animal="+animal+" stopped="+stopped+" removed="+removed+" hp="+hp);
    }
    private void write(String line) {
        if (count>=20000 || "false".equals(System.getProperty("phuc.tradeTrace"))) return;
        try {
            if (!opened) {
                opened=true;
                File folder=new File("logs"); folder.mkdirs();
                File file=File.createTempFile("trade-",".log",folder);
                out=new PrintStream(new FileOutputStream(file));
                out.println("trade-flow=dialog-command-identity-v1");
            }
            if (out==null) return;
            out.println(line);
            out.flush();
            if (++count>=20000) { out.close(); out=null; }
        } catch (Exception ignored) {}
    }
}
