import autotrade.*;
import GameScreen.GameScreen;
import Main.GameCanvas;
import Model.Point;
import Thread_More.LoadMap;

/** Reproduces actual map arrivals, including portal lists that contradict seeded routes. */
public final class MapLoopRegressionTest {
    private static int checks;
    private static void check(boolean ok,String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static void enter(Navigator nav,int id,String name,String[] portals,long time) {
        GameCanvas.loadmap.idMap=id; GameCanvas.loadmap.nameMap=name;
        GameCanvas.loadmap.mapW=20; GameCanvas.loadmap.mapH=20; GameCanvas.loadmap.mapType=new int[400];
        GameScreen.player.x=240; GameScreen.player.y=240; GameScreen.player.hp=100;
        LoadMap.vecPointChange.removeAllElements();
        for (int i=0;i<portals.length;i++) {
            Point p=new Point(); p.name=portals[i]; p.x=48+96*i; p.y=48;
            LoadMap.vecPointChange.addElement(p);
        }
        nav.observe(time);
    }
    public static void main(String[] args) throws Exception {
        LiveTrade arrival=new LiveTrade(new TradeConfig(),new MapGraph());
        GameCanvas.loadmap.idMap=19; GameCanvas.loadmap.nameMap="Cua Bien (khu 2)";
        GameScreen.player.posTransRoad=new short[]{123};
        check(arrival.travel("Cửa Biển",0),"Market arrival uses map ID even when displayed name has suffix");
        check(GameScreen.player.posTransRoad==null,"Stop stale path on arrival instead of walking into Doi Xac Chet");
        String a="Map A",b="Map B",target="Cổng Địa Ngục";
        MapGraph graph=new MapGraph(); Navigator nav=new Navigator(graph);
        graph.add(a,b,48,48); graph.add(b,target,48,48);
        GameScreen.player.posTransRoad=new short[]{123};
        GameScreen.player.toX=999; GameScreen.player.toY=999;
        enter(nav,19,a,new String[]{b},1000);
        check(GameScreen.player.posTransRoad==null && GameScreen.player.toX==240,"Clear old-map movement immediately on arrival");
        nav.go(target,-1,null,3000);
        check(GameScreen.player.posTransRoad!=null,"Initially try the route toward Doi Xac Chet");
        enter(nav,47,b,new String[]{a},4000);
        nav.go(target,-1,null,6000);
        check(graph.distance(b,target)==Integer.MAX_VALUE,"Actual portals remove nonexistent seeded shortcut to target");
        check(GameScreen.player.posTransRoad!=null,"Allow one legitimate retreat from a dead end");
        enter(nav,19,a,new String[]{b},7000);
        for (int i=0;i<12;i++) {
            nav.observe(9000+i*1000); nav.go(target,-1,null,9000+i*1000);
            check(GameScreen.player.posTransRoad==null,"Never oscillate Cua Bien -> Doi Xac Chet again for same goal");
        }
        check(nav.status.indexOf("tránh lặp map")>=0,"Explain why navigation stopped");
        check(!graph.canUse(a,b),"Repeated observations cannot clear traversal guard");
        nav.go(b,-1,null,22000);
        check(GameScreen.player.posTransRoad!=null,"New explicit destination can reuse a previously traversed exit");

        // The entire observed hell corridor wins over even shorter stale cache links.
        graph=new MapGraph(); nav=new Navigator(graph);
        graph.add("Đồi Xác Chết", "Cổng vào Hạ Giới", 24,24);
        graph.seedDefaults();
        check(!graph.hasDirect("Đồi Xác Chết","Cổng vào Hạ Giới"),"Remove legacy fictitious shortcut");
        String[] hell=TradeConfig.HELL_ROUTE;
        for (int i=0;i<hell.length-1;i++) {
            graph.add("Đồi Xác Chết",target,24,24);
            long at=24000+i*3000;
            enter(nav,200+i,hell[i],new String[]{"Đồi Xác Chết",hell[i+1],i==0?"Thành Phố Cảng":hell[i-1]},at);
            nav.go(target,-1,null,at+2000);
            check(nav.status.equals("Đi tới "+hell[i+1]) && GameScreen.player.xStopMove==144,
                "Follow hell corridor immediately from "+hell[i]);
        }
        enter(nav,52,target,new String[]{hell[hell.length-2]},46000);
        check(nav.go(target,-1,null,48000),"Arrive at hell without detour");
        nav=new Navigator(new MapGraph());
        enter(nav,24,"Nghĩa Địa Cát",new String[]{"Cửa Biển","Đồi Cát"},49000);
        nav.go(target,-1,null,51000);
        check(GameScreen.player.posTransRoad==null && nav.status.indexOf("Vực Lún")>=0,
            "Missing Vuc Lun portal never falls back to sea detour");

        // Verified absence of a portal survives save/load and seed installation.
        MapGraph known=new MapGraph(); known.seedDefaults();
        known.replaceExits(b,new String[]{a},new int[]{48},new int[]{48});
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
        known.write(new java.io.DataOutputStream(bytes));
        MapGraph restored=new MapGraph(); restored.read(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())));
        restored.seedDefaults();
        check(restored.distance(b,target,a)==Integer.MAX_VALUE,"Reloading cannot recreate disproved outgoing shortcuts");
        // Empty map topology also invalidates cached routes.
        restored.replaceExits(a,new String[0],new int[0],new int[0]);
        check(restored.next(a,target)==null,"Handle a genuine map with no exits");
        // Persisted shortcuts must not override the user-confirmed selling corridor.
        graph=new MapGraph(); nav=new Navigator(graph);
        graph.add("Thành Phố Cảng",TradeConfig.SELLER_MAP,24,24);
        graph.add("Đồi Cát",TradeConfig.SELLER_MAP,24,24);
        GameObjects.MainObject animal=new GameObjects.MainObject(); animal.x=240; animal.y=240;
        enter(nav,19,"Cửa Biển",new String[]{"Thành Phố Cảng","Nghĩa Địa Cát"},50000);
        nav.go(TradeConfig.SELLER_MAP,-1,animal,52000);
        check(nav.status.equals("Đi tới Nghĩa Địa Cát") && GameScreen.player.xStopMove==144,"Seller route ignores shorter cached port shortcut");
        enter(nav,24,"Nghĩa Địa Cát",new String[]{"Đồi Cát","Hố Tử Thần","Cửa Biển"},53000);
        nav.go(TradeConfig.SELLER_MAP,-1,animal,55000);
        check(nav.status.equals("Đi tới Hố Tử Thần") && GameScreen.player.xStopMove==144,"Seller route passes through Ho Tu Than instead of cached Doi Cat shortcut");
        // A missing required exit must not cause a detour to another map.
        nav=new Navigator(new MapGraph());
        enter(nav,24,"Nghĩa Địa Cát",new String[]{"Đồi Cát","Cửa Biển"},56000);
        nav.go(TradeConfig.SELLER_MAP,-1,animal,58000);
        check(GameScreen.player.posTransRoad==null && nav.status.indexOf("Hố Tử Thần")>=0,"Missing required portal stops escort instead of selecting wrong route");
        // Market-bound navigation still uses its own route.
        nav.go("Cửa Biển",-1,animal,59000);
        check(nav.status.equals("Đi tới Cửa Biển"),"Selling corridor does not affect the return trip to market");
        // Follow every user-confirmed hop despite shorter/incorrect cached routes.
        String[] corridor={"Cửa Biển","Nghĩa Địa Cát","Hố Tử Thần","Sa Mạc","Khu phía Tây",
            "Thành Phố Kho Báu","Rừng Chết","Hang Sói Quỷ","Cánh Đồng Sói","Thung Lũng Kỳ Bí"};
        graph=new MapGraph(); nav=new Navigator(graph);
        for (int i=0;i<corridor.length-1;i++) {
            graph.add(corridor[i],"Hồ Ký Ức",24,24);
            graph.add("Hồ Ký Ức",TradeConfig.SELLER_MAP,24,24);
            long at=60000+i*4000;
            String back=i==0?"Thành Phố Cảng":corridor[i-1];
            enter(nav,100+i,corridor[i],new String[]{"Hồ Ký Ức",corridor[i+1],back},at);
            nav.go(TradeConfig.SELLER_MAP,-1,animal,at+2000);
            check(nav.status.equals("Đi tới "+corridor[i+1]) && GameScreen.player.xStopMove==144,
                "Follow confirmed selling hop from "+corridor[i]+" to "+corridor[i+1]);
        }
        enter(nav,109,corridor[9],new String[]{corridor[8]},100000);
        check(nav.go(TradeConfig.SELLER_MAP,-1,animal,102000),"Arrive at Mystic Valley by name after full corridor");
        check(GameScreen.player.posTransRoad==null,"Stop at selling destination instead of walking back");
        nav=new Navigator(new MapGraph());
        enter(nav,25,"Rừng Chết",new String[]{"Hang Sói Quỷ"},110000);
        GameScreen.player.x=48; GameScreen.player.y=48; animal.x=48; animal.y=48;
        nav.go(TradeConfig.SELLER_MAP,-1,animal,112000);
        nav.go(TradeConfig.SELLER_MAP,-1,animal,116000);
        check(GameScreen.player.posTransRoad!=null && (GameScreen.player.xStopMove!=48 || GameScreen.player.yStopMove!=48),
            "Stalled portal starts a reachable retreat instead of remaining at entrance");
        GameScreen.player.x=GameScreen.player.xStopMove; GameScreen.player.y=GameScreen.player.yStopMove;
        animal.x=GameScreen.player.x; animal.y=GameScreen.player.y;
        nav.go(TradeConfig.SELLER_MAP,-1,animal,117000);
        check(GameScreen.player.xStopMove==48 && GameScreen.player.yStopMove==48,"After retreat re-enter same real portal");
        enter(nav,18,"Hang Sói Quỷ",new String[]{"Cánh Đồng Sói"},118000);
        animal.x=240; animal.y=240;
        nav.go(TradeConfig.SELLER_MAP,-1,animal,120000);
        check(nav.status.equals("Đi tới Cánh Đồng Sói"),"Map arrival resets portal retry state");
        // Adjacent tiles can still be 47 pixels apart: radius-one BFS used to
        // return an empty path while reunion required <=35 pixels forever.
        nav=new Navigator(new MapGraph());
        enter(nav,25,"Rừng Chết",new String[]{"Hang Sói Quỷ"},130000);
        GameScreen.player.x=48; GameScreen.player.y=60; animal.x=95; animal.y=60;
        check(GameObjects.MainObject.getDistance(48,60,95,60)==47,"Reproduce screenshot reunion distance");
        nav.approachAnimal(animal,132000);
        check(GameScreen.player.posTransRoad!=null && GameScreen.player.xStopMove==95,
            "47px reunion crosses adjacent tile instead of declaring arrival");
        short[] reunionRoad=GameScreen.player.posTransRoad;
        nav.approachAnimal(animal,132100);
        check(GameScreen.player.posTransRoad==reunionRoad,"Do not reset a healthy path every tick");
        nav.approachAnimal(animal,136000);
        check(GameScreen.player.posTransRoad!=null &&
            GameObjects.MainObject.getDistance(GameScreen.player.xStopMove,GameScreen.player.yStopMove,animal.x,animal.y)<=35,
            "Stationary player tries a reachable alternate approach within leash distance");
        GameScreen.player.x=GameScreen.player.xStopMove; GameScreen.player.y=GameScreen.player.yStopMove;
        check(nav.approachAnimal(animal,136100),"Reunion completes by actual distance after detour");
        nav.resetJourney(); GameScreen.player.x=48; GameScreen.player.y=60;
        GameCanvas.loadmap.mapType[2*20+3]=1;
        nav.approachAnimal(animal,137000);
        check(GameScreen.player.posTransRoad!=null,"Blocked animal tile uses an accessible nearby position");
        int destTile=GameScreen.player.yStopMove/24*20+GameScreen.player.xStopMove/24;
        check(GameCanvas.loadmap.mapType[destTile]!=1 &&
            GameObjects.MainObject.getDistance(GameScreen.player.xStopMove,GameScreen.player.yStopMove,animal.x,animal.y)<=35,
            "Alternate reunion destination avoids collision and stays close enough to animal");
        // Vuc Lun: both actors remain close, and the client repeatedly clears its road.
        nav=new Navigator(new MapGraph());
        enter(nav,22,"Vực Lún",new String[]{"Nghĩa Địa Cát","Đèo Hoang Sơ"},140000);
        animal.x=240; animal.y=240;
        nav.go(TradeConfig.MARKETS[0],52,animal,142000);
        short[] healthyRoad=GameScreen.player.posTransRoad;
        nav.go(TradeConfig.MARKETS[0],52,animal,142100);
        check(GameScreen.player.posTransRoad==healthyRoad,"Healthy escort road is not interrupted");
        for (int i=1;i<=4;i++) {
            GameScreen.player.posTransRoad=null;
            nav.go(TradeConfig.MARKETS[0],52,animal,142000+i*1000);
        }
        check(GameScreen.player.posTransRoad!=null && nav.status.indexOf("Kẹt di chuyển")>=0,
            "Coordinate watchdog fires despite repeatedly discarded roads and nearby stationary cow: "+nav.status+" at "+GameScreen.player.xStopMove+","+GameScreen.player.yStopMove);
        int detourX=GameScreen.player.xStopMove,detourY=GameScreen.player.yStopMove;
        check(GameObjects.MainObject.getDistance(detourX,detourY,animal.x,animal.y)<=35,
            "Unstick destination remains close enough to reacquire cow");
        check(detourX!=144 || detourY!=48,"Unstick changes direction instead of repeating failed portal path");
        nav.go(TradeConfig.MARKETS[0],52,animal,146100);
        check(GameScreen.player.xStopMove==detourX && GameScreen.player.yStopMove==detourY,
            "Next tick preserves active recovery step");
        GameScreen.player.x=detourX; GameScreen.player.y=detourY;
        animal.x=detourX; animal.y=detourY;
        nav.go(TradeConfig.MARKETS[0],52,animal,146200);
        check(GameScreen.player.xStopMove==144 && GameScreen.player.yStopMove==48,
            "Recovery resumes original Deo Hoang So portal");
        // If that detour also fails, use another candidate after a bounded retry.
        GameScreen.player.x=240; GameScreen.player.y=240; animal.x=240; animal.y=240;
        nav.resetJourney();
        nav.go(TradeConfig.MARKETS[0],52,animal,150000);
        nav.go(TradeConfig.MARKETS[0],52,animal,154100);
        detourX=GameScreen.player.xStopMove; detourY=GameScreen.player.yStopMove;
        nav.go(TradeConfig.MARKETS[0],52,animal,157000);
        nav.go(TradeConfig.MARKETS[0],52,animal,161100);
        check(GameScreen.player.xStopMove!=detourX || GameScreen.player.yStopMove!=detourY,
            "Failed recovery rotates to a different reachable tile");
        nav.resetJourney();
        nav.go(TradeConfig.MARKETS[0],52,animal,162000);
        check(GameScreen.player.xStopMove==144 && GameScreen.player.yStopMove==48,
            "Stopping and restarting clears stale recovery state");
        enter(nav,42,"Đèo Hoang Sơ",new String[]{"Vực Lún","Đường Xuống Lòng Đất"},163000);
        nav.go(TradeConfig.MARKETS[0],52,animal,165000);
        check(nav.status.equals("Đi tới Đường Xuống Lòng Đất"),"Map arrival clears watchdog and continues route");
        // No safe sidestep: do not walk through walls or accidentally change maps.
        nav=new Navigator(new MapGraph());
        enter(nav,22,"Vực Lún",new String[]{"Đèo Hoang Sơ"},170000);
        animal.x=240; animal.y=240;
        for (int i=0;i<400;i++) GameCanvas.loadmap.mapType[i]=1;
        GameCanvas.loadmap.mapType[10*20+10]=0;
        nav.go(TradeConfig.MARKETS[0],52,animal,172000);
        nav.go(TradeConfig.MARKETS[0],52,animal,176100);
        check(GameScreen.player.posTransRoad==null && nav.status.indexOf("ô trống an toàn")>=0,
            "Fully enclosed actor waits instead of forcing a blocked recovery path");
        // Opening a safe cell lets the next bounded retry recover.
        GameCanvas.loadmap.mapType[10*20+9]=0;
        nav.go(TradeConfig.MARKETS[0],52,animal,180200);
        check(GameScreen.player.posTransRoad!=null && GameScreen.player.xStopMove==216,
            "Retry observes current collision data and uses newly reachable safe tile");
        nav.resetJourney();
        Point nearbyPortal=new Point(); nearbyPortal.name="Cửa Biển"; nearbyPortal.x=216; nearbyPortal.y=240;
        LoadMap.vecPointChange.addElement(nearbyPortal);
        nav.go(TradeConfig.MARKETS[0],52,animal,181000);
        nav.go(TradeConfig.MARKETS[0],52,animal,185100);
        check(GameScreen.player.posTransRoad==null,"Recovery cannot step into a nearby unrelated portal");
        // Match Player.updatePlayer: the last road entry uses xStopMove/yStopMove.
        // A three-entry escort road used to jump across this wall on entry three.
        nav=new Navigator(new MapGraph());
        enter(nav,22,"Vực Lún",new String[0],190000);
        GameScreen.player.x=252; GameScreen.player.y=252;
        animal.x=252; animal.y=252;
        for (int y=0;y<14;y++) GameCanvas.loadmap.mapType[y*20+11]=1;
        GameScreen.ObjFocus=animal;
        nav.walk(348,252,0,animal,192000);
        check(GameScreen.ObjFocus==null,"Clear focus on owned animal during escort");
        short[] escortRoad=GameScreen.player.posTransRoad;
        check(escortRoad!=null && escortRoad.length>3,"Escort keeps full winding road around wall");
        int prevX=10,prevY=10;
        for (int i=0;i<escortRoad.length;i++) {
            int tx=i==escortRoad.length-1 ? GameScreen.player.xStopMove/24 : (escortRoad[i]>>8)&255;
            int ty=i==escortRoad.length-1 ? GameScreen.player.yStopMove/24 : escortRoad[i]&255;
            check(Math.abs(tx-prevX)+Math.abs(ty-prevY)==1 && GameCanvas.loadmap.mapType[ty*20+tx]!=1,
                "Original client waypoint semantics cannot shortcut through wall at step "+i);
            prevX=tx; prevY=ty;
        }
        check(prevX==14 && prevY==10,"Full escort road reaches intended destination");
        GameObjects.MainObject npc=new GameObjects.MainObject();
        GameScreen.ObjFocus=npc;
        nav.walk(348,252,0,animal,192100);
        check(GameScreen.ObjFocus==npc,"Do not clear unrelated NPC focus");
        System.out.println("PASS map-loop regression: "+checks+" assertions");
    }
}

