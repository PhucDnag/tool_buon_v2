package autotrade;

/** Approved map topology only. Never populated from RMS or live exploration. */
public final class KnownRoutes {
    private static final MapGraph routes=new MapGraph();
    static {
        WorldTopology.addTo(routes);
        corridor(TradeConfig.SELLER_ROUTE);
        corridor(TradeConfig.HELL_ROUTE);
        corridor(new String[]{"Nghĩa Địa Cát","Đồi Cát","Rừng Cao Nguyên","Con Đường Hiểm Trở","Vách Đá Cheo Leo"});
        corridor(new String[]{"Khu Mua Bán","Làng Sói Trắng","Cánh Đồng Sói","Bờ Biển"});
        corridor(new String[]{"Thung Lũng Kỳ Bí","Hồ Ký Ức","Bãi Đất Trống"});
        corridor(new String[]{"Cửa Biển","Đồi Xác Chết"});
    }
    private static void corridor(String[] maps) {
        for (int i=0;i<maps.length-1;i++) {
            routes.add(maps[i],maps[i+1],0,0);
            routes.add(maps[i+1],maps[i],0,0);
        }
    }
    public static String next(String from,String target) {
        MapGraph.Exit exit=routes.next(from,target);
        return exit==null ? null : exit.to;
    }
    public static int remaining(String from,String target,String avoid) {
        return routes.distance(from,target,avoid);
    }
}
