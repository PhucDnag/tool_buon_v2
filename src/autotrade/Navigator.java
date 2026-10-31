package autotrade;

import GameObjects.MainObject;
import GameObjects.Player;
import GameScreen.GameScreen;
import Main.GameCanvas;
import Thread_More.LoadMap;
import Model.Point;
import Model.CRes;
import java.io.*;

public final class Navigator {
    public final MapGraph graph;
    public String status = "", previousMap = "";
    private String current = "", destination;
    private String routeGoal, portalSnapshot;
    private int mapId = -1, cliffIndex = -1, cliffDirection;
    private long nextStep, transitionUntil;
    private long progressAt;
    private int progressX, progressY;
    private int destX = -1, destY = -1;
    private int portalX=-1, portalY=-1, retryX, retryY, portalAttempt;
    private long portalNearAt, retreatUntil;
    private long walkStartAt, lastMovedAt;
    private int lastPlayerX = -1, lastPlayerY = -1;
    private long stalledAt=-1, beastProgressAt=-1, unstickUntil;
    private int watchedX, watchedY, watchedBeastX, watchedBeastY, watchedBeast=-1;
    private int unstickX, unstickY, unstickAttempt;

    private void resetStallWatch() {
        stalledAt=-1; beastProgressAt=-1; watchedBeast=-1; unstickUntil=0; unstickAttempt=0;
    }
    private int lastBeastX = -1, lastBeastY = -1;
    private long lastBeastMovedAt = 0;
    private boolean returningToBeast = false;
    private long reunionAt, reunionDetourUntil;
    private int reunionX, reunionY, reunionBeast=-1, reunionDetourX, reunionDetourY, reunionAttempt;
    private static final int[][] CLIFF = { { 696, 1104 }, { 624, 792 }, { 120, 528 }, { 504, 192 } };

    public Navigator(MapGraph g) {
        graph = g;
        try {
            byte[] b = CRes.loadRMS("phuc_routes_v3");
            if (b != null)
                graph.read(new DataInputStream(new ByteArrayInputStream(b)));
        } catch (Exception e) {
            status = "Đọc lại dữ liệu đường từ map";
        }
        graph.seedDefaults();
    }

    public void observe(long now) {
        if (GameCanvas.loadmap == null)
            return;
        boolean changed = (GameCanvas.loadmap.idMap != mapId);
        if (changed) {
            resetStallWatch();
            stop();
            previousMap = current;
            current = GameCanvas.loadmap.nameMap;
            mapId = GameCanvas.loadmap.idMap;
            if (routeGoal != null && previousMap.length() > 0 && !same(previousMap, current))
                graph.block(previousMap, current);
            cliffIndex = -1;
            destination = null;
            destX = -1;
            destY = -1;
            returningToBeast = false;
            reunionAt=0; reunionDetourUntil=0; reunionBeast=-1;
            lastBeastX = -1;
            lastBeastY = -1;
            lastBeastMovedAt = 0;
            portalX=-1; portalNearAt=0; retreatUntil=0; portalAttempt=0;
            transitionUntil = now + 1500;
            progressAt = now;
            portalSnapshot = null;
            graph.observed(current, mapId);
        }
        // Map packets install names, collision data and portals in separate steps.
        if (now < transitionUntil || LoadMap.vecPointChange == null)
            return;
        int count = LoadMap.vecPointChange.size();
        String[] names = new String[count];
        int[] xs = new int[count], ys = new int[count];
        StringBuffer signature = new StringBuffer();
        for (int i = 0; i < count; i++) {
            Point p = (Point) LoadMap.vecPointChange.elementAt(i);
            if (p != null && p.name != null) {
                names[i] = p.name;
                xs[i] = p.x;
                ys[i] = p.y;
                signature.append(p.name).append(':').append(p.x).append(':').append(p.y).append('\n');
            }
        }
        String snapshot = signature.toString();
        if (!snapshot.equals(portalSnapshot)) {
            graph.replaceExits(current, names, xs, ys);
            portalSnapshot = snapshot;
            try {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                graph.write(new DataOutputStream(b));
                CRes.saveRMS("phuc_routes_v3", b.toByteArray());
            } catch (Exception e) {
                status = "Không lưu được đường; tiếp tục dùng dữ liệu trong RAM";
            }
        }
    }

    public void resetJourney() {
        resetStallWatch();
        reunionAt=0; reunionDetourUntil=0; reunionBeast=-1;
        portalX=-1; portalNearAt=0; retreatUntil=0; portalAttempt=0;
        routeGoal = null;
        graph.clearBlocks();
        destination = null;
        returningToBeast = false;
        lastBeastX = -1;
        lastBeastY = -1;
        lastBeastMovedAt = 0;
        stop();
    }

    public void resetConnection() {
        resetJourney();
        mapId = -1;
        destination = null;
        cliffIndex = -1;
        destX = -1;
        destY = -1;
        returningToBeast = false;
        lastBeastX = -1;
        lastBeastY = -1;
        lastBeastMovedAt = 0;
        stop();
    }

    public static void stop() {
        Player p = GameScreen.player;
        if (p == null)
            return;
        p.posTransRoad = null;
        p.countAutoMove = 0;
        p.toX = p.x;
        p.toY = p.y;
        p.xStopMove = 0;
        p.yStopMove = 0;
        p.vx = 0;
        p.vy = 0;
    }

    public Point findPortal(String targetName) {
        if (targetName == null || LoadMap.vecPointChange == null)
            return null;
        for (int i = 0; i < LoadMap.vecPointChange.size(); i++) {
            Point p = (Point) LoadMap.vecPointChange.elementAt(i);
            if (p != null && p.name != null && same(p.name, targetName))
                return p;
        }
        return null;
    }

    public boolean go(String target, int targetId, MainObject beast, long now) {
        observe(now);
        if (now < transitionUntil)
            return false;
        if (targetId >= 0 && mapId == targetId || target != null && same(current, target)) {
            stop();
            return true;
        }
        if (target == null)
            target = graph.nameFor(targetId);
        if (target == null) {
            status = "Chưa xác định điểm đến";
            stop();
            return false;
        }
        String goal = TradeStock.normalize(target).trim();
        if (!goal.equals(routeGoal)) {
            resetJourney();
            routeGoal = goal;
        }

        // The selling journey must use the confirmed desert corridor. Do not let
        // seeded shortcuts or a route cache send the animal through another exit.
        String sellerExit = null;
        String[] requiredRoute = same(target, TradeConfig.MARKETS[0]) ? TradeConfig.HELL_ROUTE
                : same(target, TradeConfig.SELLER_MAP) ? TradeConfig.SELLER_ROUTE : null;
        if (requiredRoute != null) {
            for (int i=0; i<requiredRoute.length-1; i++) {
                if (same(current, requiredRoute[i])) {
                    sellerExit=requiredRoute[i+1];
                    break;
                }
            }
        }
        if (sellerExit != null) {
            Point portal = findPortal(sellerExit);
            if (portal != null && graph.canUse(current, portal.name)) {
                status = "Đi tới " + portal.name;
                walkPortal(portal.x, portal.y, beast, now);
            } else {
                status = "Chưa có cổng tới " + sellerExit + "; dừng để tránh đi sai tuyến";
                stop();
            }
            return false;
        }

        // 1. Direct portal check: Does current map have a portal directly to target?
        Point directPortal = findPortal(target);
        if (directPortal != null && graph.canUse(current, directPortal.name)) {
            status = "Đi tới " + directPortal.name;
            walkPortal(directPortal.x, directPortal.y, beast, now);
            return false;
        }

        // 2. Among physical portals on this map, find the one with shortest route to
        // target
        Point chosenPortal = null;
        int bestDistance = Integer.MAX_VALUE;

        if (LoadMap.vecPointChange != null && LoadMap.vecPointChange.size() > 0) {
            for (int i = 0; i < LoadMap.vecPointChange.size(); i++) {
                Point p = (Point) LoadMap.vecPointChange.elementAt(i);
                if (p == null || p.name == null || !graph.canUse(current, p.name))
                    continue;
                if (same(p.name, target)) {
                    chosenPortal = p;
                    bestDistance = 0;
                    break;
                }
                int d = graph.distance(p.name, target, current);
                if (d < bestDistance) {
                    bestDistance = d;
                    chosenPortal = p;
                }
            }
        }

        if (chosenPortal != null && bestDistance < Integer.MAX_VALUE) {
            status = "Đi tới " + chosenPortal.name;
            walkPortal(chosenPortal.x, chosenPortal.y, beast, now);
            return false;
        }

        // 3. Fallback to graph.next(current, target)
        MapGraph.Exit exit = graph.next(current, target);
        if (exit != null) {
            Point realExit = findPortal(exit.to);
            if (realExit != null && graph.canUse(current, realExit.name)) {
                status = "Đi tới " + realExit.name;
                walkPortal(realExit.x, realExit.y, beast, now);
                return false;
            }
        }

        // 4. If no known route to target, but beast == null, explore forward (never
        // backtrack to previousMap)
        if (beast == null && LoadMap.vecPointChange != null && LoadMap.vecPointChange.size() > 0) {
            Point forwardPortal = null;
            for (int i = 0; i < LoadMap.vecPointChange.size(); i++) {
                Point p = (Point) LoadMap.vecPointChange.elementAt(i);
                if (p != null && p.name != null && graph.canUse(current, p.name) && !same(p.name, previousMap)) {
                    forwardPortal = p;
                    break;
                }
            }
            if (forwardPortal == null) {
                for (int i = 0; i < LoadMap.vecPointChange.size(); i++) {
                    Point p = (Point) LoadMap.vecPointChange.elementAt(i);
                    if (p != null && p.name != null && graph.canUse(current, p.name)) {
                        forwardPortal = p;
                        break;
                    }
                }
            }
            if (forwardPortal != null) {
                status = "Khảo sát đường: " + forwardPortal.name;
                walkPortal(forwardPortal.x, forwardPortal.y, beast, now);
                return false;
            }
        }

        status = "Chưa có tuyến đến " + target + "; dừng để tránh lặp map";
        stop();
        return false;
    }

    private void walkPortal(int x,int y,MainObject beast,long now) {
        Player p=GameScreen.player;
        if (portalX!=x || portalY!=y) { portalX=x; portalY=y; portalNearAt=0; retreatUntil=0; portalAttempt=0; }
        if (retreatUntil>0) {
            if (now<retreatUntil && MainObject.getDistance(p.x,p.y,retryX,retryY)>12) {
                status="Lùi khỏi cổng để thử qua lại"; walk(retryX,retryY,0,beast,now); return;
            }
            retreatUntil=0; portalNearAt=now; stop();
        }
        if (MainObject.getDistance(p.x,p.y,x,y)<=72) {
            if (portalNearAt==0) portalNearAt=now;
            if (now-portalNearAt>=3500) {
                int tile=LoadMap.wTile, w=GameCanvas.loadmap.mapW, h=GameCanvas.loadmap.mapH;
                int[] dx={-2,2,0,0,-2,2,-2,2}, dy={0,0,-2,2,-2,-2,2,2};
                for (int k=0;k<8;k++) {
                    int d=(portalAttempt+k)%8, tx=x/tile+dx[d], ty=y/tile+dy[d];
                    if(tx<0 || ty<0 || tx>=w || ty>=h) continue;
                    int cell=GameCanvas.loadmap.mapType[ty*w+tx];
                    if(cell==1 || cell==-1) continue;
                    int[] path=GridPath.find(GameCanvas.loadmap.mapType,w,h,p.x/tile,p.y/tile,tx,ty,0);
                    if(path==null || path.length==0) continue;
                    retryX=tx*tile+tile/2; retryY=ty*tile+tile/2;
                    portalAttempt=d+1; retreatUntil=now+4000; stop();
                    status="Kẹt cổng; lùi rồi đi vào lại"; walk(retryX,retryY,0,beast,now); return;
                }
                portalNearAt=now;
            }
        } else portalNearAt=0;
        walk(x,y,0,beast,now);
    }

    /** Reunion uses pixel distance, not a one-tile BFS radius (which can stop 47px away). */
    public boolean approachAnimal(MainObject beast,long now) {
        Player p=GameScreen.player;
        if (p==null || beast==null || now<transitionUntil) return false;
        if (MainObject.getDistance(p.x,p.y,beast.x,beast.y)<=35) {
            reunionAt=0; reunionDetourUntil=0; stop(); return true;
        }
        if (reunionBeast!=beast.ID || reunionAt==0 || MainObject.getDistance(p.x,p.y,reunionX,reunionY)>4) {
            reunionBeast=beast.ID; reunionAt=now; reunionX=p.x; reunionY=p.y;
        }
        if (reunionDetourUntil>now) {
            if (MainObject.getDistance(p.x,p.y,reunionDetourX,reunionDetourY)>12) {
                walkInternal(reunionDetourX,reunionDetourY,0,null,now); return false;
            }
            reunionDetourUntil=0;
        }
        int tile=LoadMap.wTile,w=GameCanvas.loadmap.mapW,h=GameCanvas.loadmap.mapH;
        int tx=beast.x/tile,ty=beast.y/tile;
        boolean blocked=tx<0 || ty<0 || tx>=w || ty>=h || GameCanvas.loadmap.mapType==null
                || GameCanvas.loadmap.mapType[ty*w+tx]==1 || GameCanvas.loadmap.mapType[ty*w+tx]==-1;
        if (blocked || now-reunionAt>=3500) {
            for (int k=0;k<25;k++) {
                int slot=(reunionAttempt+k)%25, nx=tx+slot%5-2,ny=ty+slot/5-2;
                if(nx<0 || ny<0 || nx>=w || ny>=h || GameCanvas.loadmap.mapType==null) continue;
                int cell=GameCanvas.loadmap.mapType[ny*w+nx], px=nx*tile+tile/2,py=ny*tile+tile/2;
                if(cell==1 || cell==-1 || MainObject.getDistance(px,py,beast.x,beast.y)>35) continue;
                int[] path=GridPath.find(GameCanvas.loadmap.mapType,w,h,p.x/tile,p.y/tile,nx,ny,0);
                if(path==null || path.length==0) continue;
                reunionAttempt=slot+1; reunionDetourX=px; reunionDetourY=py; reunionDetourUntil=now+3500;
                reunionAt=now; stop(); status="Kẹt khi nhận bò; thử đường tiếp cận khác";
                walkInternal(px,py,0,null,now); return false;
            }
            if(blocked) { stop(); status="Chưa có đường tiếp cận thú; đang thử lại"; return false; }
            reunionAt=now; stop();
        }
        walkInternal(beast.x,beast.y,0,null,now);
        return false;
    }

    public boolean walk(int x, int y, int radius, MainObject beast, long now) {
        Player p=GameScreen.player;
        if (p==null || p.hp<=0 || now<transitionUntil) return false;
        // Selecting the owned animal is not required to lead it. Keep its
        // interaction target from competing with an active escort movement.
        if (beast!=null && GameScreen.ObjFocus==beast) GameScreen.ObjFocus=null;
        if (MainObject.getDistance(p.x,p.y,x,y)<=Math.max(16,radius*LoadMap.wTile)) {
            resetStallWatch();
            return walkInternal(x,y,radius,beast,now);
        }
        // Observe actual coordinates, even when the client discards posTransRoad.
        // Issuing a new path is not evidence that either character has moved.
        if (stalledAt<0 || MainObject.getDistance(p.x,p.y,watchedX,watchedY)>4) {
            watchedX=p.x; watchedY=p.y; stalledAt=now;
        }
        if (beast!=null && (watchedBeast!=beast.ID || beastProgressAt<0
                || MainObject.getDistance(beast.x,beast.y,watchedBeastX,watchedBeastY)>4)) {
            watchedBeast=beast.ID; watchedBeastX=beast.x; watchedBeastY=beast.y; beastProgressAt=now;
        }
        if (unstickUntil>0) {
            if (now<unstickUntil && MainObject.getDistance(p.x,p.y,unstickX,unstickY)>16
                    && (beast==null || MainObject.getDistance(p.x,p.y,beast.x,beast.y)<=55)) {
                walkInternal(unstickX,unstickY,0,null,now);
                status="Đang gỡ kẹt: bước lệch rồi tiếp tục dắt bò";
                return false;
            }
            unstickUntil=0; stalledAt=now; beastProgressAt=now;
            returningToBeast=false; stop();
        }
        if ((now-stalledAt>=4000 || beast!=null && now-beastProgressAt>=6000)
                && (beast==null || MainObject.getDistance(p.x,p.y,beast.x,beast.y)<=55)) {
            if (startUnstick(beast,now)) return false;
            stalledAt=now; beastProgressAt=now;
            stop();
            status="Kẹt di chuyển; chưa có ô trống an toàn, sẽ thử lại";
            return false;
        }
        return walkInternal(x,y,radius,beast,now);
    }

    private boolean startUnstick(MainObject beast,long now) {
        Player p=GameScreen.player;
        int tile=LoadMap.wTile,w=GameCanvas.loadmap.mapW,h=GameCanvas.loadmap.mapH;
        int[] tiles=GameCanvas.loadmap.mapType;
        if (tiles==null || tile<=0 || (long)w*h>tiles.length) return false;
        for (int k=0;k<25;k++) {
            int slot=(unstickAttempt+k)%25, tx=p.x/tile+slot%5-2,ty=p.y/tile+slot/5-2;
            if (tx<0 || ty<0 || tx>=w || ty>=h || tiles[ty*w+tx]==1 || tiles[ty*w+tx]==-1) continue;
            int px=p.x+(slot%5-2)*tile,py=p.y+(slot/5-2)*tile;
            if (MainObject.getDistance(p.x,p.y,px,py)<=16
                    || beast!=null && MainObject.getDistance(px,py,beast.x,beast.y)>35) continue;
            int[] path=GridPath.find(tiles,w,h,p.x/tile,p.y/tile,tx,ty,0);
            if (path==null || path.length==0 || path.length>6) continue;
            boolean safe=true;
            for (int j=0;j<path.length && safe;j++) {
                int cx=path[j]%w*tile+tile/2,cy=path[j]/w*tile+tile/2;
                if (beast!=null && MainObject.getDistance(cx,cy,beast.x,beast.y)>55) safe=false;
                if (LoadMap.vecPointChange!=null) for (int n=0;n<LoadMap.vecPointChange.size();n++) {
                    Point portal=(Point)LoadMap.vecPointChange.elementAt(n);
                    if (portal!=null && MainObject.getDistance(cx,cy,portal.x,portal.y)<=24) safe=false;
                }
            }
            if (!safe) continue;
            unstickAttempt=slot+1; unstickX=px; unstickY=py; unstickUntil=now+2500;
            stop(); walkInternal(px,py,0,null,now);
            status="Kẹt di chuyển; đổi hướng gần bò để dắt lại";
            return true;
        }
        return false;
    }

    private boolean walkInternal(int x, int y, int radius, MainObject beast, long now) {
        Player p = GameScreen.player;
        if (p == null || p.hp <= 0 || now < transitionUntil)
            return false;
        int tile = LoadMap.wTile;
        if (MainObject.getDistance(p.x, p.y, x, y) <= Math.max(16, radius * tile)) {
            stop();
            return true;
        }
        if (beast != null) {
            int bDist = MainObject.getDistance(p.x, p.y, beast.x, beast.y);
            if (lastBeastX != -1 && MainObject.getDistance(lastBeastX, lastBeastY, beast.x, beast.y) > 1) {
                lastBeastX = beast.x;
                lastBeastY = beast.y;
                lastBeastMovedAt = now;
            } else if (lastBeastX == -1) {
                lastBeastX = beast.x;
                lastBeastY = beast.y;
                lastBeastMovedAt = now;
            }

            if (returningToBeast) {
                if (bDist <= 35) {
                    returningToBeast = false;
                    lastBeastMovedAt = now;
                    stop();
                    return false;
                }
                status = "Quay lại nhận bò (" + bDist + "px)";
                approachAnimal(beast,now);
                return false;
            }

            if (bDist > 85) {
                returningToBeast = true;
                status = "Khoảng cách quá xa (" + bDist + "px); quay lại dắt bò";
                approachAnimal(beast,now);
                return false;
            }

            if (bDist > 35 && now - lastBeastMovedAt > 1000) {
                returningToBeast = true;
                status = "Bò không đi; quay lại dắt bò";
                approachAnimal(beast,now);
                return false;
            }

            if (bDist > 55) {
                stop();
                status = "Chờ bò theo kịp (" + bDist + "px)";
                return false;
            }

            if (radius == 0 && MainObject.getDistance(p.x, p.y, x, y) <= 48 && bDist > 40) {
                stop();
                status = "Chờ bò vào cổng (" + bDist + "px)";
                return false;
            }
        }

        if (p.posTransRoad != null && Math.abs(destX - x) <= 12 && Math.abs(destY - y) <= 12) {
            if (MainObject.getDistance(lastPlayerX, lastPlayerY, p.x, p.y) > 4) {
                lastPlayerX = p.x;
                lastPlayerY = p.y;
                lastMovedAt = now;
            } else if (now - lastMovedAt > 3500) {
                stop();
            } else {
                return false;
            }
        }

        int w = GameCanvas.loadmap.mapW, h = GameCanvas.loadmap.mapH;
        int[] path = GridPath.find(GameCanvas.loadmap.mapType, w, h, p.x / tile, p.y / tile, x / tile, y / tile,
                radius);
        if (path == null) {
            status = "Không có đường đi an toàn tại map " + mapId;
            stop();
            return false;
        }
        if (path.length == 0) {
            if (radius > 0 || MainObject.getDistance(p.x, p.y, x, y) <= 24) {
                stop();
                return true;
            }
            short[] roads = new short[] { (short) (((x / tile & 0xFF) << 8) | (y / tile & 0xFF)) };
            p.posTransRoad = roads;
            p.countAutoMove = 0;
            p.xStopMove = x;
            p.yStopMove = y;
            p.toX = p.x;
            p.toY = p.y;
            destX = x;
            destY = y;
            walkStartAt = now;
            lastMovedAt = now;
            lastPlayerX = p.x;
            lastPlayerY = p.y;
            return false;
        }

        // Player.updatePlayer uses xStopMove/yStopMove for the final waypoint.
        // Truncating to three steps makes that third step jump to the distant
        // portal, cutting through walls. Leash checks above pace the full path.
        int len = path.length;
        short[] roads = new short[len];
        for (int i = 0; i < len; i++) {
            int tx = path[i] % w, ty = path[i] / w;
            roads[i] = (short) (((tx & 0xFF) << 8) | (ty & 0xFF));
        }

        p.posTransRoad = null;
        p.countAutoMove = 0;
        p.xStopMove = x;
        p.yStopMove = y;
        p.toX = p.x;
        p.toY = p.y;
        p.posTransRoad = roads;

        destX = x;
        destY = y;
        walkStartAt = now;
        lastMovedAt = now;
        lastPlayerX = p.x;
        lastPlayerY = p.y;
        nextStep = now + (beast == null ? 100 : Math.max(150, 24 * 50 / Math.max(1, beast.vMax)));
        return false;
    }

    public static boolean same(String a, String b) {
        if (a == null || b == null)
            return false;
        String na = TradeStock.normalize(a).trim();
        String nb = TradeStock.normalize(b).trim();
        if (na.equals(nb))
            return true;
        if (na.length() >= 4 && nb.length() >= 4) {
            return na.indexOf(nb) >= 0 || nb.indexOf(na) >= 0;
        }
        return false;
    }
}
