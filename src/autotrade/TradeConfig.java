package autotrade;

import java.util.Random;

public final class TradeConfig {
    public static final String[] ANIMALS = {"Lừa vàng", "Lạc đà vàng", "Bò vàng"};
    public static final String[] GEMS = {"Kim Cương", "Rubi", "Saphia"};
    public static final String[] MARKETS = {"Cổng Địa Ngục", "Cửa Biển", "Vách Đá Cheo Leo"};
    public static final int[] HP = {30, 50, 70, 90};
    public static final int[] MAX_CARGO = {6, 12, 24};
    public static final String SELLER_MAP = "Thung Lũng Kỳ Bí";
    public static final String[] HELL_ROUTE = {
        "Cửa Biển", "Nghĩa Địa Cát", "Vực Lún", "Đèo Hoang Sơ",
        "Đường Xuống Lòng Đất", "Cây Cầu Ma Ám", "Cổng vào Hạ Giới", "Cổng Địa Ngục"
    };
    public static final String[] SELLER_ROUTE = {
        "Cửa Biển", "Nghĩa Địa Cát", "Hố Tử Thần", "Sa Mạc", "Khu phía Tây",
        "Thành Phố Kho Báu", "Rừng Chết", "Hang Sói Quỷ", "Cánh Đồng Sói", SELLER_MAP
    };
    public static final int BUY_INTERVAL_MS = 1000;
    public static final int MAP_CONG_DIA_NGUC = 52;
    public static final int MAP_CUA_BIEN = 19;
    public static final int MAP_VACH_DA_CHEO_LEO = 39;
    public static final int[] MARKET_MAP_IDS = {52, 19, 39};

    public int animal, gem, market, hpIndex = 1;
    public int reviveMinMs = 9000, reviveMaxMs = 11000;
    public boolean hud = true, visual = true, fakeName = true, reconnect, autoMount;
    public boolean teleport;
    public boolean rotateMarkets=true;
    public boolean resumeTrade;
    public boolean silentBuy = true;
    public boolean lowDetail = true;
    public int savedCharacter = -1;
    public int charSlot = -1;
    public int mountItem = -1;
    public String mountName = "Chưa chọn";
    public int maxSaleAttempts = 3;
    public int saleTimeoutMs = 20000;

    public int reviveDelay(Random random) {
        int min = Math.max(0, Math.min(15000, reviveMinMs));
        int max = Math.max(min, Math.min(15000, reviveMaxMs));
        return min + (random.nextInt() & 0x7fffffff) % (max - min + 1);
    }

    // Use server-region time (UTC+7), independent of the emulator host timezone.
    public static boolean tradingClosed(long utcMillis) {
        long day = (utcMillis + 7L * 3600000L) % 86400000L;
        if (day < 0) day += 86400000L;
        return day >= 10L * 3600000L && day < 16L * 3600000L;
    }

    public static boolean isMount(int id, int category, String name) {
        if (name == null || name.length() == 0) return false;
        if (category == 3 || category == 5 || category == 6 || category == 7 || category >= 8) return false;
        String s = TradeStock.normalize(name).toLowerCase().trim();
        if (s.indexOf("me day") >= 0 || s.indexOf("xuong rong") >= 0 || s.indexOf("canh ") >= 0
                || s.indexOf("cay ") >= 0 || s.indexOf("hoa ") >= 0 || s.indexOf("qua ") >= 0 || s.indexOf("la ") >= 0
                || s.indexOf("thit") >= 0 || s.indexOf("thuc an") >= 0 || s.indexOf("long ") >= 0
                || s.indexOf("da ") >= 0 || s.indexOf("mong ") >= 0 || s.indexOf("trung ") >= 0
                || s.indexOf("sach ") >= 0 || s.indexOf("bi kip") >= 0 || s.indexOf("ngoc ") >= 0
                || s.indexOf("bua ") >= 0 || s.indexOf("ruong ") >= 0 || s.indexOf("hop ") >= 0
                || s.indexOf("ve ") >= 0 || s.indexOf("chia khoa") >= 0 || s.indexOf("tinh the") >= 0
                || s.indexOf("manh ") >= 0 || s.indexOf("sung ") >= 0 || s.startsWith("sung")
                || s.indexOf("rang ") >= 0 || s.startsWith("rang")) return false;

        return s.startsWith("ngua ") || s.equals("ngua")
                || s.startsWith("heo ") || s.equals("heo")
                || s.startsWith("lan ") || s.equals("lan")
                || s.startsWith("ky lan");
    }
}
