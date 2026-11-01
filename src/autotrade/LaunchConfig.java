package autotrade;

/** Manager settings override RMS once, independently for each field. */
public final class LaunchConfig {
    private static int number(String key,int fallback,int min,int max) {
        try { String value=System.getProperty("phuc."+key); return value==null ? fallback : Math.max(min,Math.min(max,Integer.parseInt(value.trim()))); }
        catch (Exception ignored) { return fallback; }
    }
    private static boolean flag(String key,boolean fallback) {
        String value=System.getProperty("phuc."+key);
        return value==null ? fallback : "1".equals(value) || "true".equalsIgnoreCase(value);
    }
    public static void apply(TradeConfig c) {
        c.charSlot=number("slot",c.charSlot,-1,2); c.market=number("market",c.market,0,2);
        c.gem=number("gem",c.gem,0,2); c.animal=number("animal",c.animal,0,2);
        c.hpIndex=number("hp",c.hpIndex,0,3);
        c.resumeTrade=flag("autoon",c.resumeTrade); c.reconnect=flag("reconnect",c.reconnect);
        c.autoMount=flag("automount",c.autoMount); c.rotateMarkets=flag("rotate",c.rotateMarkets);
        c.teleport=flag("teleport",c.teleport); c.hud=flag("hud",c.hud);
        c.visual=flag("visual",c.visual); c.fakeName=flag("fakename",c.fakeName);
        c.silentBuy=flag("silentbuy",c.silentBuy);
        String mount=System.getProperty("phuc.mountname");
        if (mount!=null) { c.mountName=mount.trim(); c.mountItem=-1; }
    }
    public static int serverIndex(String requested,String[][] servers) {
        if (requested==null || servers==null) return -1;
        for (int i=0;i<servers.length;i++) {
            String[] entry=servers[i];
            if (entry!=null && entry.length>0 && entry[0]!=null
                    && TradeStock.normalize(requested).trim().equals(TradeStock.normalize(entry[0]).trim())) return i;
        }
        return -1;
    }
}
