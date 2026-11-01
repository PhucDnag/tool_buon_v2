import autotrade.*;
import Main.GameCanvas;
import GameScreen.GameScreen;
import GameObjects.*;
import Model.Point;
import Thread_More.LoadMap;
import CLib.mVector;
import netcommand.global.GlobalService;

public final class MovementVerificationTest {
    static void assertCondition(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
    }

    public static void main(String[] args) {
        System.out.println("Running Movement & Auto Buôn Verification Tests...");

        // 1. Verify GridPath finding with target on blocked edge tile
        int[] tiles = new int[400]; // 20x20
        // Set row 0 (top border) as wall (1)
        for (int x = 0; x < 20; x++) tiles[x] = 1;
        // Player at (10, 5), Portal at (10, 0) which is on the blocked border
        int[] path = GridPath.find(tiles, 20, 20, 10, 5, 10, 0, 0);
        assertCondition(path != null, "Path to border portal must not be null");
        assertCondition(path.length > 0, "Path to border portal must have waypoints");
        // The last waypoint should be adjacent to (10, 0), i.e. (10, 1)
        int lastTile = path[path.length - 1];
        int lastX = lastTile % 20, lastY = lastTile / 20;
        assertCondition(lastX == 10 && lastY == 1, "Last tile must be adjacent walkable tile (10, 1), got (" + lastX + ", " + lastY + ")");
        System.out.println("  [+] GridPath handles blocked border portals correctly");

        // 2. Verify Navigator multi-step road feeding
        MapGraph graph = new MapGraph();
        graph.seedDefaults();
        Navigator nav = new Navigator(graph);

        GameCanvas.loadmap.mapW = 20;
        GameCanvas.loadmap.mapH = 20;
        GameCanvas.loadmap.mapType = tiles;
        GameCanvas.loadmap.idMap = 1;
        GameCanvas.loadmap.nameMap = "Làng Sói Trắng";

        GameScreen.player.x = 240; // tile (10, 5) -> 10*24 = 240, 5*24 = 120
        GameScreen.player.y = 120;
        GameScreen.player.hp = 1000;
        GameScreen.player.posTransRoad = null;

        // Walk to (240, 24) -> tile (10, 1)
        boolean arrived = nav.walk(240, 24, 0, null, 1000);
        assertCondition(!arrived, "Should not be arrived yet");
        assertCondition(GameScreen.player.posTransRoad != null, "posTransRoad must be populated");
        assertCondition(GameScreen.player.posTransRoad.length == path.length, "posTransRoad must contain all waypoints");
        assertCondition(GameScreen.player.xStopMove == 240 && GameScreen.player.yStopMove == 24, "xStopMove/yStopMove must match destination");
        System.out.println("  [+] Navigator feeds full waypoint array to Player.posTransRoad");

        // 3. Verify real portal detection from LoadMap.vecPointChange
        LoadMap.vecPointChange.removeAllElements();
        Point pPortal = new Point();
        pPortal.name = "Cánh Đồng Sói";
        pPortal.x = 240;
        pPortal.y = 48;
        LoadMap.vecPointChange.addElement(pPortal);

        nav.observe(2000);
        Point found = nav.findPortal("Cánh Đồng Sói");
        assertCondition(found != null, "Must find live portal in vecPointChange");
        assertCondition(found.x == 240 && found.y == 48, "Must use live coordinates from vecPointChange");
        System.out.println("  [+] Navigator captures real portals dynamically from LoadMap.vecPointChange");

        // 4. Verify inter-map pathfinding from Làng Sói Trắng
        String[] destinations = {"Cổng Địa Ngục", "Cửa Biển", "Vách Đá Cheo Leo", "Thung Lũng Kỳ Bí", "Thành Phố Kho Báu", "Thành Phố Cảng"};
        for (int i = 0; i < destinations.length; i++) {
            MapGraph.Exit exit = graph.next("Làng Sói Trắng", destinations[i]);
            assertCondition(exit != null, "Route must exist from Làng Sói Trắng to " + destinations[i]);
            assertCondition(exit.to.equalsIgnoreCase("canh dong soi"), "Next step must be Cánh Đồng Sói");
        }
        System.out.println("  [+] All 6 inter-map routes from Làng Sói Trắng are valid");

        // 5. Verify Auto Buôn does NOT start trading while in village (map 1)
        TradeConfig config = new TradeConfig();
        config.market = 0; // Cổng Địa Ngục
        config.gem = 0;
        config.animal = 0;
        LiveTrade live = new LiveTrade(config, graph);
        GlobalService net = GlobalService.gI();
        net.queryCalls = 0;
        net.potionCalls = 0;
        net.shopCalls = 0;

        GameCanvas.loadmap.idMap = 1;
        GameCanvas.loadmap.nameMap = "Làng Sói Trắng";

        live.toggle(3000);
        assertCondition(live.active(), "Auto buôn must be active");
        assertCondition(live.engine.state == TradeEngine.TO_MARKET, "Initial state must be TO_MARKET");

        // Tick in village map 1
        live.tick(null, 3200);
        assertCondition(net.queryCalls == 0, "Must NOT query NPC in village");
        assertCondition(net.potionCalls == 0, "Must NOT summon animal in village");
        assertCondition(net.shopCalls == 0, "Must NOT buy goods in village");
        assertCondition(live.engine.state == TradeEngine.TO_MARKET, "State must remain TO_MARKET while traveling");
        System.out.println("  [+] Auto buôn travels to market first and does NOT trade in village");

        // Now simulate arriving at market map 52 (Cổng Địa Ngục)
        GameCanvas.loadmap.idMap = 52;
        GameCanvas.loadmap.nameMap = "Cổng Địa Ngục";
        LoadMap.vecPointChange.removeAllElements();
        Point pDoiXacFromCong = new Point();
        pDoiXacFromCong.name = "Đồi Xác Chết";
        pDoiXacFromCong.x = 240;
        pDoiXacFromCong.y = 400;
        LoadMap.vecPointChange.addElement(pDoiXacFromCong);
        MainObject buyer = new MainObject();
        buyer.ID = 5;
        buyer.typeObject = 2;
        buyer.name = "Thương nhân";
        buyer.x = 240;
        buyer.y = 120;
        GameScreen.Vecplayers.removeAllElements();
        GameScreen.Vecplayers.addElement(buyer);

        live.tick(null, 4500);
        assertCondition(live.engine.state == TradeEngine.STOCK, "State must switch to STOCK upon entering Cổng Địa Ngục");
        assertCondition(net.queryCalls == 1, "Must query NPC once inside market map");
        System.out.println("  [+] Auto buôn begins trading only upon physically entering market map");

        // 6. Verify Cửa Biển & Đồi Xác Chết routing
        // A. From Cửa Biển with portals: [Đồi Xác Chết, Nghĩa Địa Cát]
        GameCanvas.loadmap.idMap = 19;
        GameCanvas.loadmap.nameMap = "Cửa Biển";
        LoadMap.vecPointChange.removeAllElements();
        Point pDoiXac = new Point(); pDoiXac.name = "Đồi Xác Chết"; pDoiXac.x = 240; pDoiXac.y = 24;
        Point pNghiaDia = new Point(); pNghiaDia.name = "Nghĩa Địa Cát"; pNghiaDia.x = 240; pNghiaDia.y = 400;
        LoadMap.vecPointChange.addElement(pDoiXac);
        LoadMap.vecPointChange.addElement(pNghiaDia);
        nav.observe(5000);

        // Hell-bound travel must head directly into the desert corridor.
        GameScreen.player.posTransRoad = null;
        nav.go("Cổng Địa Ngục", -1, null, 7000);
        assertCondition(nav.status.indexOf("Nghĩa Địa Cát") >= 0, "Hell route must choose Nghia Dia Cat: " + nav.status);
        assertCondition(GameScreen.player.xStopMove == pNghiaDia.x && GameScreen.player.yStopMove == pNghiaDia.y, "Use live Nghia Dia Cat portal coordinates");

        // Target: Graham (Thung Lũng Kỳ Bí) -> must pick Nghĩa Địa Cát (to head towards town)
        GameScreen.player.posTransRoad = null;
        nav.go("Thung Lũng Kỳ Bí", -1, null, 7100);
        assertCondition(nav.status.indexOf("Nghĩa Địa Cát") >= 0, "From Cửa Biển to Thung Lũng Kỳ Bí, must choose Nghĩa Địa Cát, got: " + nav.status);
        assertCondition(GameScreen.player.xStopMove == pNghiaDia.x && GameScreen.player.yStopMove == pNghiaDia.y, "Must target real coordinates of Nghĩa Địa Cát portal");
        System.out.println("  [+] Cửa Biển correctly routes through Nghĩa Địa Cát");

        // B. From Đồi Xác Chết with portals: [Cổng Địa Ngục, Cửa Biển]
        GameCanvas.loadmap.idMap = 47;
        GameCanvas.loadmap.nameMap = "Đồi Xác Chết";
        LoadMap.vecPointChange.removeAllElements();
        Point pCongDiaNguc = new Point(); pCongDiaNguc.name = "Cổng Địa Ngục"; pCongDiaNguc.x = 240; pCongDiaNguc.y = 24;
        Point pCuaBien = new Point(); pCuaBien.name = "Cửa Biển"; pCuaBien.x = 240; pCuaBien.y = 400;
        LoadMap.vecPointChange.addElement(pCongDiaNguc);
        LoadMap.vecPointChange.addElement(pCuaBien);
        nav.observe(8000);

        // Target: Cổng Địa Ngục -> direct portal on map!
        GameScreen.player.posTransRoad = null;
        nav.go("Cổng Địa Ngục", -1, null, 10000);
        assertCondition(nav.status.indexOf("Cửa Biển") >= 0, "From dead-end hill return via approved Cua Bien route: " + nav.status);
        assertCondition(GameScreen.player.xStopMove == pCuaBien.x && GameScreen.player.yStopMove == pCuaBien.y, "Ignore unapproved direct shortcut");

        // Target: Graham (Thung Lũng Kỳ Bí) -> must return via Cửa Biển
        GameScreen.player.posTransRoad = null;
        nav.go("Thung Lũng Kỳ Bí", -1, null, 10100);
        assertCondition(nav.status.indexOf("Cửa Biển") >= 0, "From Đồi Xác Chết to Graham, must choose Cửa Biển portal, got: " + nav.status);
        assertCondition(GameScreen.player.xStopMove == pCuaBien.x && GameScreen.player.yStopMove == pCuaBien.y, "Must target real coordinates of Cửa Biển portal");
        System.out.println("  [+] Đồi Xác Chết correctly routes to Cổng Địa Ngục and back to Cửa Biển without looping");

        // 7. Verify cargo full immediately transitions to TO_SELL and leads beast toward Graham (Map 8)
        GameCanvas.loadmap.idMap = 52;
        GameCanvas.loadmap.nameMap = "Cổng Địa Ngục";
        LoadMap.vecPointChange.removeAllElements();
        Point pCongVao = new Point(); pCongVao.name = "Cổng vào Hạ Giới"; pCongVao.x = 240; pCongVao.y = 400;
        LoadMap.vecPointChange.addElement(pCongVao);

        TradeConfig cfg2 = new TradeConfig();
        cfg2.market = 0; cfg2.gem = 0; cfg2.animal = 0; // max cargo 6
        LiveTrade live2 = new LiveTrade(cfg2, graph);
        live2.toggle(20000);
        // Let map transition stabilize past transitionUntil (1500ms)
        live2.navigation.observe(20000);
        live2.navigation.observe(21600);

        MainObject testBeast = new MainObject();
        testBeast.ID = 88; testBeast.typeObject = 1; testBeast.x = 240; testBeast.y = 170;
        GameScreen.player.x = 240; GameScreen.player.y = 120; // dist = 50px (> 35px)
        live2.view.cargo = 6; live2.view.full = true;
        live2.engine.state = TradeEngine.TO_SELL;
        live2.tick(testBeast, 21700);
        assertCondition(live2.engine.state == TradeEngine.TO_SELL, "State must remain TO_SELL while traveling to Graham");
        assertCondition(live2.detail.indexOf("nhận bò") >= 0, "Must approach beast first when initial distance > 35px");
        assertCondition(GameScreen.player.xStopMove == testBeast.x && GameScreen.player.yStopMove == testBeast.y, "Must move toward beast coordinates");

        // Player reaches beast (<= 35px), now begins moving toward exit portal
        GameScreen.player.y = 160; // dist = 10px (<= 35px)
        live2.tick(testBeast, 21900);
        assertCondition(GameScreen.player.posTransRoad != null, "Player must begin moving toward exit portal with full cargo");
        assertCondition(GameScreen.player.xStopMove == pCongVao.x && GameScreen.player.yStopMove == pCongVao.y, "Target portal must be Cổng vào Hạ Giới");

        // Verify trailing beast pauses movement (55-85px)
        testBeast.y = 235; // distance = 75 (> 55 and <= 85)
        live2.tick(testBeast, 22100);
        assertCondition(GameScreen.player.posTransRoad == null, "Player must pause when beast trails behind > 55px");
        assertCondition(live2.navigation.status.indexOf("Chờ bò") >= 0, "Status must show waiting for beast");

        // Verify distance too far (> 85px) triggers turning back to beast
        testBeast.y = 280; // distance = 120 (> 85)
        live2.tick(testBeast, 22300);
        assertCondition(GameScreen.player.posTransRoad != null, "Player must walk back when distance > 85px");
        assertCondition(GameScreen.player.xStopMove == testBeast.x && GameScreen.player.yStopMove == testBeast.y, "Must walk toward beast coordinates");
        assertCondition(live2.navigation.status.indexOf("quay lại") >= 0, "Status must indicate turning back");

        // Verify beast re-secured (<= 35px) resumes path toward portal
        GameScreen.player.y = 270; // distance = 10 (<= 35)
        live2.tick(testBeast, 22500);
        live2.tick(testBeast, 22700);
        assertCondition(GameScreen.player.posTransRoad != null, "Player must resume path to portal once beast catches up");
        assertCondition(GameScreen.player.xStopMove == pCongVao.x && GameScreen.player.yStopMove == pCongVao.y, "Resume toward portal");

        // Verify stationary beast (> 45px and no move for > 1000ms) triggers turning back
        testBeast.y = 220;
        GameScreen.player.y = 160; // distance = 60 (> 45)
        live2.tick(testBeast, 22900); // register beast position at 22900
        live2.tick(testBeast, 24300); // 1400ms later with no beast movement
        assertCondition(GameScreen.player.posTransRoad != null, "Player must turn back if beast does not move for > 1000ms");
        assertCondition(GameScreen.player.xStopMove == testBeast.x && GameScreen.player.yStopMove == testBeast.y, "Must walk back to stationary beast");
        assertCondition(live2.navigation.status.indexOf("Bò không") >= 0, "Status must indicate beast did not follow");
        System.out.println("  [+] Full cargo correctly leads beast, turns back on distance loss or stationary cow, and paces smoothly");

        System.out.println("\nALL MOVEMENT & AUTO BUON VERIFICATION TESTS PASSED!");
    }
}
