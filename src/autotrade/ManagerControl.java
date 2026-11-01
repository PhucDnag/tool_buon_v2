package autotrade;

import java.io.*;

/** Per-launch mailbox; sequence tokens make commands idempotent. */
public final class ManagerControl {
    private String last="";
    private long nextPoll;
    public int poll(String path,long now) {
        if (path==null || now<nextPoll) return -1;
        nextPoll=now+250;
        try {
            if (!new File(path).isFile()) return -1;
            BufferedReader reader=new BufferedReader(new FileReader(path));
            String line;
            try { line=reader.readLine(); } finally { reader.close(); }
            if (line==null || line.equals(last) || line.length()<3 || line.charAt(1)!=':') return -1;
            if (line.charAt(0)!='0' && line.charAt(0)!='1') return -1;
            last=line;
            return line.charAt(0)-'0';
        } catch (IOException ignored) { return -1; }
    }
}
