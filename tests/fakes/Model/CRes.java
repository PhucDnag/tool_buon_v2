package Model;
import java.util.Hashtable;

public class CRes {
    public static int saveCalls;
    private static final Hashtable store = new Hashtable();
    public static byte[] loadRMS(String key) {
        return (byte[]) store.get(key);
    }
    public static void saveRMS(String key, byte[] value) {
        if ("phuc_recovery_v1".equals(key)) saveCalls++;
        if (value == null) store.remove(key);
        else store.put(key, value);
    }
    public static void clearAllRMS() {
        store.clear();
    }
}
