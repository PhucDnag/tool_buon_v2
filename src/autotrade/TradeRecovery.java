package autotrade;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import Model.CRes;

/**
 * Persists active trade beast location and state so recovery trips survive
 * application restarts and disconnects ("out game vô lại").
 */
public final class TradeRecovery {
    private static final String STORE = "phuc_recovery_v1";

    public static final class Record {
        public int charId;
        public String map;
        public int x;
        public int y;
        public int cargo;
        public int market;
        public int gem;
        public int animal;
        public long timestamp;

        public boolean valid(int expectedCharId, long now) {
            return (expectedCharId < 0 || this.charId == expectedCharId)
                && map != null && map.length() > 0
                && x >= 0 && y >= 0
                && cargo>=-1 && market>=0 && market<TradeConfig.MARKETS.length
                && gem>=0 && gem<TradeConfig.GEMS.length && animal>=0 && animal<TradeConfig.ANIMALS.length
                && (now <= 0 || timestamp <= 0 || (now - timestamp >= 0 && now - timestamp <= 15 * 60 * 1000L));
        }
    }

    public static synchronized void save(int charId, String map, int x, int y, int cargo, int market, int gem, int animal, long now) {
        if (map == null || map.length() == 0 || x < 0 || y < 0) return;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(1);
            out.writeInt(charId);
            out.writeUTF(map);
            out.writeInt(x);
            out.writeInt(y);
            out.writeInt(cargo);
            out.writeInt(market);
            out.writeInt(gem);
            out.writeInt(animal);
            out.writeLong(now > 0 ? now : System.currentTimeMillis());
            out.flush();
            CRes.saveRMS(STORE, bytes.toByteArray());
        } catch (Exception e) {}
    }

    public static synchronized Record load(int expectedCharId, long now) {
        try {
            byte[] data = CRes.loadRMS(STORE);
            if (data == null || data.length == 0) return null;
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
            if (in.readInt() != 1) return null;
            Record r = new Record();
            r.charId = in.readInt();
            r.map = in.readUTF();
            r.x = in.readInt();
            r.y = in.readInt();
            r.cargo = in.readInt();
            r.market = in.readInt();
            r.gem = in.readInt();
            r.animal = in.readInt();
            r.timestamp = in.readLong();
            if (r.valid(expectedCharId, now)) return r;
        } catch (Exception e) {}
        return null;
    }

    public static synchronized void clear() {
        try {
            CRes.saveRMS(STORE, new byte[0]);
        } catch (Exception e) {}
    }
}

