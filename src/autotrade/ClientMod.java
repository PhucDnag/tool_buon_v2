package autotrade;

import CLib.mFont;
import CLib.mGraphics;
import CLib.mVector;
import CLib.Session_ME;
import GameObjects.MainObject;
import GameObjects.MainItem;
import GameObjects.Item;
import GameObjects.Player;
import GameScreen.GameScreen;
import GameScreen.LoginScreen;
import GameScreen.MainScreen;
import GameScreen.SelectCharScreen;
import GameScreen.SelectionBridge;
import GameScreen.TabScreenNew;
import GameScreen.PaintInfoGameScreen;
import InterfaceComponents.iCommand;
import InterfaceComponents.IAction;
import InterfaceComponents.ChatTextField;
import Main.GameCanvas;
import Model.CRes;
import Model.Point;
import Thread_More.LoadMap;
import netcommand.global.GlobalService;
import netcommand.global.GlobalLogicHandler;
import java.io.*;

/** Small hooks into original bytecode; original game classes are not recompiled. */
public final class ClientMod {
    public static final TradeConfig config=new TradeConfig();
    public static final MapGraph graph=new MapGraph();
    public static LiveTrade live;
    private static boolean loaded, diagnostics;
    private static String status="Auto buôn: sẵn sàng", route;
    private static String latestStock="Chưa có phản hồi tồn kho NPC";
    private static int lastMap=-1, selectedNpc=-1, charId=-1, reconnectAttempts;
    private static long nextTick, nextMount, nextDismount, reconnectAt;
    private static boolean reconnecting, selectionSent;
    private static boolean resumePending;
    private static final ManagerControl managerControl=new ManagerControl();
    private static final RuntimeMaintenance maintenance=new RuntimeMaintenance();
    private static long lastNetworkActivity;
    public static boolean shouldPaint() { return maintenance.paint(System.currentTimeMillis()); }
    private static void pollManager(long now) {
        int command=managerControl.poll(System.getProperty("phuc.control"),now);
        if (command<0) return;
        route=null;
        live.navigation.resetJourney();
        if (command==0) {
            if (live.active()) live.toggle(now);
            resumePending=false;
            config.resumeTrade=false;
            status="Đã dừng auto từ quản lí tài khoản";
        } else {
            resumePending=!live.active();
            config.resumeTrade=true;
            status="Đã bật auto từ quản lí tài khoản";
        }
        save();
    }
    private static boolean slotAttempted;
    private static final EntryGate entryGate=new EntryGate();
    private static boolean entryObserved, supportApplied;
    private static long selectCharScreenEnteredTime;
    private static MainObject animal;
    private static String log="";
    private static final StartupLogin startupLogin=new StartupLogin();

    public static void prepareManagerLogin() {
        // Native auto-login requires old RMS and can run before server selection.
        if (System.getProperty("phuc.user")!=null) LoginScreen.isAutoLogin=false;
    }

    public static boolean applyManagerLogin() {
        String user=System.getProperty("phuc.user");
        String pass=System.getProperty("phuc.pass");
        if (user==null || user.length()==0 || pass==null
                || LoginScreen.tfusername==null || LoginScreen.tfpassword==null) return false;
        LoginScreen.tfusername.setText(user);
        LoginScreen.tfpassword.setText(pass);
        // The original client prefers its cached guest account on reconnect.
        LoginScreen.username="";
        LoginScreen.password="";
        return true;
    }

    private static synchronized void init() {
        if (loaded) return;
        loaded=true;
        live=new LiveTrade(config,graph);
        loadSavedConfig();
        LaunchConfig.apply(config);
        resumePending=config.resumeTrade;
    }
    private static boolean launchBool(String key,boolean fallback) {
        String value=System.getProperty(key);
        return value==null ? fallback : "1".equals(value) || "true".equalsIgnoreCase(value);
    }
    private static int launchPercent(String key,int fallback) {
        try { String value=System.getProperty(key); return value==null ? fallback : Math.max(1,Math.min(100,Integer.parseInt(value))); }
        catch (Exception ignored) { return fallback; }
    }
    private static void applySupportSettings() {
        if (System.getProperty("phuc.autoattack")!=null) {
            boolean enabled=launchBool("phuc.autoattack",false);
            Player.isAutoFire=enabled?(byte)0:(byte)-1; Player.isCurAutoFire=enabled;
        }
        if (System.getProperty("phuc.autoheal")!=null) {
            InterfaceComponents.MsgDialog.mHPMP[0]=launchPercent("phuc.healhp",50);
            InterfaceComponents.MsgDialog.mHPMP[1]=launchPercent("phuc.healmp",50);
            Player.isAutoHPMP=launchBool("phuc.autoheal",false);
        }
        if (System.getProperty("phuc.autoloot")!=null) {
            Player.autoItem=launchBool("phuc.autoloot",true) ? new GameObjects.AutoGetItem((byte)0,(byte)0,(byte)0) : null;
        }
    }
    private static void loadSavedConfig() {
        try {
            byte[] data=CRes.loadRMS("phuc_trade_v1");
            if (data==null) return;
            DataInputStream in=new DataInputStream(new ByteArrayInputStream(data));
            if (in.readInt()!=1) return;
            config.animal=in.readUnsignedByte()%3; config.gem=in.readUnsignedByte()%3;
            config.market=in.readUnsignedByte()%3; config.hpIndex=in.readUnsignedByte()%4;
            config.hud=in.readBoolean(); config.visual=in.readBoolean(); config.fakeName=in.readBoolean();
            config.reconnect=in.readBoolean(); config.autoMount=in.readBoolean(); config.teleport=in.readBoolean();
            config.mountItem=in.readInt(); config.mountName=in.readUTF();
            if (in.available()>=8) {
                in.readInt(); in.readInt();
            }
            config.reviveMinMs=9000; config.reviveMaxMs=11000;
            if (in.available()>=5) { config.resumeTrade=in.readBoolean(); config.savedCharacter=in.readInt(); }
            if (in.available()>=4) { config.charSlot=in.readInt(); }
            if (in.available()>=1) { config.silentBuy=in.readBoolean(); }
            if (in.available()>=1) { config.lowDetail=in.readBoolean(); }
            charId=config.savedCharacter; resumePending=config.resumeTrade;
        } catch (Exception e) { status="Không đọc được cấu hình; dùng mặc định"; }
    }
    private static void save() {
        try {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream(); DataOutputStream out=new DataOutputStream(bytes);
            out.writeInt(1); out.writeByte(config.animal); out.writeByte(config.gem); out.writeByte(config.market); out.writeByte(config.hpIndex);
            out.writeBoolean(config.hud); out.writeBoolean(config.visual); out.writeBoolean(config.fakeName);
            out.writeBoolean(config.reconnect); out.writeBoolean(config.autoMount); out.writeBoolean(config.teleport);
            out.writeInt(config.mountItem); out.writeUTF(config.mountName);
            out.writeInt(config.reviveMinMs); out.writeInt(config.reviveMaxMs);
            out.writeBoolean(config.resumeTrade); out.writeInt(config.savedCharacter);
            out.writeInt(config.charSlot); out.writeBoolean(config.silentBuy); out.writeBoolean(config.lowDetail); out.flush(); CRes.saveRMS("phuc_trade_v1",bytes.toByteArray());
        } catch (Exception e) { status="Không lưu được cấu hình"; }
    }
    private static String on(boolean value) { return value ? "Bật" : "Tắt"; }
    private static void add(mVector menu, String label, final int command) {
        menu.addElement(new iCommand(label,new IAction() { public void perform() { action(command); } }));
    }
    public static void appendMenu() {
        init();
        if (GameCanvas.menu2!=null && GameCanvas.menu2.menuItems!=null) {
            mVector items=GameCanvas.menu2.menuItems;
            for (int i=0;i<items.size();i++) {
                iCommand cmd=(iCommand)items.elementAt(i);
                if (cmd!=null && cmd.caption!=null && cmd.caption.indexOf("Tool PhucHso")>=0) return;
            }
            items.insertElementAt(new iCommand(">> Tool PhucHso <<",new IAction() { public void perform() { menu(); } }),0);
            GameCanvas.menu2.startAt(items,2,"Menu",false,null);
        }
    }
    public static void menu() {
        init(); mVector items=new mVector("PhucHso");
        add(items,autoCaption(),1);
        add(items,"Cài đặt Đi Buôn >",20);
        add(items,"Di chuyển nhanh >",21);
        add(items,"Hỗ trợ nhân vật >",22);
        add(items,"Giao diện & Tiện ích >",23);
        add(items,"Dừng mọi di chuyển",13);
        GameCanvas.menu2.startAt(items,2,"Tool PhucHso",false,null);
    }
    private static String autoCaption() {
        return "Auto buôn: "+(live.active() ? "[BẬT]" : resumePending ? "[CHỜ VÀO MAP]" : "[TẮT]");
    }
    private static void refreshAutoCaption() {
        if (GameCanvas.menu2==null || !GameCanvas.menu2.isShowMenu || GameCanvas.menu2.menuItems==null) return;
        for (int i=0;i<GameCanvas.menu2.menuItems.size();i++) {
            iCommand c=(iCommand)GameCanvas.menu2.menuItems.elementAt(i);
            if (c!=null && c.caption!=null && c.caption.startsWith("Auto buôn: ")) {
                String caption=autoCaption(); if (!caption.equals(c.caption)) c.caption=caption;
            }
        }
    }
    public static void menuTradeConfig() {
        init(); mVector items=new mVector("Cài đặt buôn");
        add(items,"Thú buôn: "+TradeConfig.ANIMALS[config.animal],2);
        add(items,"Loại đá: "+TradeConfig.GEMS[config.gem],3);
        add(items,"Bãi mua: "+TradeConfig.MARKETS[config.market],4);
        add(items,"Đổi thú dưới: "+TradeConfig.HP[config.hpIndex]+"% HP",5);
        add(items,"Mua ẩn: "+on(config.silentBuy),16);
        add(items,"< Quay lại",0);
        GameCanvas.menu2.startAt(items,2,"Cài đặt Đi Buôn",false,null);
    }
    public static void menuMovement() {
        init(); mVector items=new mVector("Di chuyển");
        add(items,"Đến Cổng Địa Ngục",30);
        add(items,"Đến Cửa Biển",31);
        add(items,"Đến Vách Đá Cheo Leo",32);
        add(items,"Đến Graham (Thung Lũng)",33);
        add(items,"Đến Làng Sói Trắng",34);
        add(items,"Đến TP Kho Báu",35);
        add(items,"Đến TP Cảng",36);
        add(items,"Di chuyển: "+(config.teleport ? "Ưu tiên dịch chuyển" : "Chỉ chạy bộ"),19);
        add(items,"Dừng di chuyển",13);
        add(items,"< Quay lại",0);
        GameCanvas.menu2.startAt(items,2,"Di chuyển nhanh",false,null);
    }
    public static void menuSupport() {
        init(); mVector items=new mVector("Hỗ trợ");
        add(items,"Chọn NV: "+(config.charSlot < 0 ? "Tự chọn" : "NV " + (config.charSlot + 1)),12);
        add(items,"Tự kết nối lại: "+on(config.reconnect),9);
        add(items,"Tự cưỡi thú: "+on(config.autoMount),10);
        add(items,"Chọn thú cưỡi: "+config.mountName,11);
        add(items,"Tự đánh: "+on(Player.isAutoFire==1),40);
        add(items,"Tự hồi HP/MP: "+on(Player.isAutoHPMP),41);
        add(items,"Tự nhặt đồ: "+on(Player.autoItem!=null),42);
        add(items,"< Quay lại",0);
        GameCanvas.menu2.startAt(items,2,"Hỗ trợ nhân vật",false,null);
    }
    public static void menuUiUtils() {
        init(); mVector items=new mVector("Tiện ích");
        add(items,"HUD thông tin: "+on(config.hud),6);
        add(items,"Đường chỉ thú buôn: "+on(config.visual),7);
        add(items,"Tên giả PhucHso: "+on(config.fakeName),8);
        add(items,"Giảm lag (ẩn người/hiệu ứng, map ô): "+on(config.lowDetail),43);
        add(items,"Ghi log dữ liệu: "+on(diagnostics),14);
        add(items,"Xem log NPC gần nhất",15);
        add(items,"< Quay lại",0);
        GameCanvas.menu2.startAt(items,2,"Giao diện & Tiện ích",false,null);
    }
    private static void travelTo(String target) {
        if (live.active()) {
            GameCanvas.start_Ok_Dialog("Tắt Auto buôn trước khi chọn di chuyển thủ công");
            return;
        }
        live.navigation.resetJourney(); route=target;
        status="Đang đi đến: "+target;
        GameCanvas.menu2.isShowMenu=false;
    }
    private static void action(int id) {
        switch(id) {
            case 0: menu(); return;
            case 1: live.toggle(System.currentTimeMillis()); config.resumeTrade=live.active(); resumePending=false; save(); route=null; GameCanvas.menu2.isShowMenu=false; return;
            case 2: config.animal=(config.animal+1)%3; save(); menuTradeConfig(); return;
            case 3: config.gem=(config.gem+1)%3; save(); menuTradeConfig(); return;
            case 4: config.market=(config.market+1)%3; save(); menuTradeConfig(); return;
            case 5: config.hpIndex=(config.hpIndex+1)%4; save(); menuTradeConfig(); return;
            case 6: config.hud=!config.hud; save(); menuUiUtils(); return;
            case 7: config.visual=!config.visual; save(); menuUiUtils(); return;
            case 8: config.fakeName=!config.fakeName; save(); menuUiUtils(); return;
            case 9: config.reconnect=!config.reconnect; reconnecting=false; save(); menuSupport(); return;
            case 10: config.autoMount=!config.autoMount; save(); menuSupport(); return;
            case 11: mounts(); return;
            case 12: config.charSlot=(config.charSlot+2)%4-1; save(); menuSupport(); return;
            case 13: route=null; stopWalking(); status="Đã dừng di chuyển"; GameCanvas.menu2.isShowMenu=false; return;
            case 14: diagnostics=!diagnostics; if (diagnostics) lastMap=-1; menuUiUtils(); return;
            case 15: GameCanvas.start_Ok_Dialog(log.length()==0 ? "Chưa ghi dữ liệu NPC" : log); return;
            case 16: config.silentBuy=!config.silentBuy; save(); menuTradeConfig(); return;
            case 19: config.teleport=!config.teleport; save(); menuMovement(); return;
            case 20: menuTradeConfig(); return;
            case 21: menuMovement(); return;
            case 22: menuSupport(); return;
            case 23: menuUiUtils(); return;
            case 30: travelTo(TradeConfig.MARKETS[0]); return;
            case 31: travelTo(TradeConfig.MARKETS[1]); return;
            case 32: travelTo(TradeConfig.MARKETS[2]); return;
            case 33: travelTo("Thung Lũng Kỳ Bí"); return;
            case 34: travelTo("Làng Sói Trắng"); return;
            case 35: travelTo("Thành Phố Kho Báu"); return;
            case 36: travelTo("Thành Phố Cảng"); return;
            case 40: if (GameCanvas.game!=null) GameCanvas.game.commandPointer(16,0); menuSupport(); return;
            case 41: if (GameCanvas.game!=null) GameCanvas.game.commandPointer(19,0); menuSupport(); return;
            case 42: if (GameCanvas.game!=null) GameCanvas.game.commandPointer(18,0); menuSupport(); return;
            case 43: config.lowDetail=!config.lowDetail; save(); menuUiUtils(); return;
        }
        menu();
    }
    public static boolean isMountItem(Item item) {
        if (item==null) return false;
        return TradeConfig.isMount(item.Id, item.ItemCatagory, item.itemName);
    }
    private static void mounts() {
        mVector items=new mVector("Chon thu");
        if (Item.VecInvetoryPlayer!=null) {
            for (int i=0;i<Item.VecInvetoryPlayer.size();i++) {
                final Item item=(Item)Item.VecInvetoryPlayer.elementAt(i);
                if (item!=null && isMountItem(item)) {
                    final int id=item.Id; final String name=item.itemName;
                    boolean exists=false;
                    for (int j=0;j<items.size();j++) if (((iCommand)items.elementAt(j)).caption.equals(name)) { exists=true; break; }
                    if (!exists) {
                        items.addElement(new iCommand(name,new IAction() { public void perform() {
                            config.mountItem=id; config.mountName=name; save(); menuSupport();
                        }}));
                    }
                }
            }
        }
        if (GameCanvas.game!=null && GameCanvas.game.menuNgua!=null) {
            for (int i=0;i<GameCanvas.game.menuNgua.size();i++) {
                final MainItem item=(MainItem)GameCanvas.game.menuNgua.elementAt(i);
                if (item!=null) {
                    final int id=item.Id; final String name=item.itemName;
                    boolean exists=false;
                    for (int j=0;j<items.size();j++) if (((iCommand)items.elementAt(j)).caption.equals(name)) { exists=true; break; }
                    if (!exists) {
                        items.addElement(new iCommand(name,new IAction() { public void perform() {
                            config.mountItem=id; config.mountName=name; save(); menuSupport();
                        }}));
                    }
                }
            }
        }
        if (items.size()==0) {
            GameCanvas.start_Ok_Dialog("Không có thú cưỡi trong hành trang.");
            return;
        }
        add(items,"< Quay lại",22);
        GameCanvas.menu2.startAt(items,2,"Chọn thú cưỡi",false,null);
    }
    public static String displayName(MainObject object) {
        init(); return config.fakeName && object==GameScreen.player ? "PhucHso" : object.name;
    }
    private static boolean openMenuPending;
    private static boolean toolInputAvailable() {
        return GameScreen.player!=null && GameCanvas.menu2!=null
                && ToolInput.available(GameCanvas.currentScreen==GameCanvas.game,
                    GameCanvas.menu2.isShowMenu,GameCanvas.currentDialog!=null,ChatTextField.isShow);
    }
    public static void pointerClick(int x, int y) {
        init();
        live.manualInput();
        if (!toolInputAvailable() || !ToolInput.button(x,y,mGraphics.zoomLevel)) return;
        GameCanvas.isPointerMove=true; GameCanvas.isPointerSelect=false;
        GameCanvas.isPointerDown=false; GameCanvas.isPointerRelease=false;
        openMenuPending=true;
    }
    public static void keyPress(int keyCode) {
        init();
        live.manualInput();
        if (!toolInputAvailable() || !ToolInput.shortcut(keyCode)) return;
        openMenuPending=true;
    }
    private static void checkInput() {
        if (!toolInputAvailable()) return;
        if (ToolInput.shortcut(GameCanvas.keyAsciiPress)) {
            GameCanvas.keyAsciiPress=0;
            menu();
        }
    }
    public static void tick() {
        init();
        refreshAutoCaption();
        if (openMenuPending) {
            openMenuPending=false;
            if (!toolInputAvailable()) return;
            GameCanvas.keyAsciiPress=0;
            GameCanvas.isPointerSelect=false;
            GameCanvas.isPointerClick=false;
            GameCanvas.clearKeyPressed();
            GameCanvas.clearKeyHold();
            menu();
            return;
        }
        checkInput();
        long now=System.currentTimeMillis(); if (now<nextTick) return; nextTick=now+50;
        maintenance.update(now,System.getProperty("phuc.control"),live.earnings,
                !Session_ME.gI().connecting && GameCanvas.currentDialog==null && !reconnecting
                && (!live.active() || live.engine.state==TradeEngine.PAUSED || live.engine.state==TradeEngine.STOCK));
        maintenance.checkMemory(now);
        if (live.active() && Session_ME.gI().isConnected() && GameCanvas.currentScreen==GameCanvas.game && !reconnecting) {
            if (lastNetworkActivity==0) lastNetworkActivity=now;
            if (now-lastNetworkActivity>90000L) {
                ConnectionTrace.event("STALL_TIMEOUT");
                lastNetworkActivity=now;
                try { Session_ME.gI().close(); } catch (Throwable ignored) {}
                disconnected();
            }
        } else {
            lastNetworkActivity=now;
        }
        try {
            pollManager(now);
            if (GameCanvas.currentScreen==GameCanvas.selectChar) {
                if (selectCharScreenEnteredTime==0) selectCharScreenEnteredTime=now;
                if (!reconnecting && Session_ME.gI().isConnected() && !slotAttempted && config.charSlot>=0 && config.charSlot<3) {
                    int size=SelectCharScreen.VecSelectChar.size();
                    if (size>config.charSlot) {
                        slotAttempted=true;
                        selectCharacter(GameCanvas.selectChar,config.charSlot);
                    } else if (size>0 || (now-selectCharScreenEnteredTime>2500)) {
                        slotAttempted=true;
                        GameCanvas.start_Ok_Dialog("Tài khoản không có nhân vật "+(config.charSlot+1));
                    }
                }
            } else {
                selectCharScreenEnteredTime=0;
                if (GameCanvas.currentScreen==GameCanvas.game) {
                    slotAttempted=false;
                }
            }
            if (GameCanvas.currentScreen==GameCanvas.login) {
                try {
                    String server=System.getProperty("phuc.server");
                    if (!Session_ME.gI().isConnected() && server!=null && CLib.mSystem.listServer!=null) {
                        int index=LaunchConfig.serverIndex(server,CLib.mSystem.listServer);
                        if (index>=0 && GameCanvas.IndexServer!=(byte)index) {
                            GameCanvas.IndexServer=(byte)index;
                            LoginScreen.saveIndexServer();
                        }
                    }
                    String user=System.getProperty("phuc.user"), pass=System.getProperty("phuc.pass");
                    int requested=LaunchConfig.serverIndex(server,CLib.mSystem.listServer);
                    boolean ready=user!=null && user.length()>0 && pass!=null && pass.length()>0
                            && requested>=0 && GameCanvas.IndexServer==(byte)requested
                            && GameCanvas.login.section==0 && GameCanvas.currentDialog==null
                            && !GameCanvas.menu2.isShowMenu && LoginScreen.tfusername!=null && LoginScreen.tfpassword!=null;
                    if (startupLogin.claim(ready,Session_ME.gI().isConnected(),Session_ME.gI().connecting)) {
                        applyManagerLogin();
                        status="Đang đăng nhập tài khoản từ quản lí";
                        GameCanvas.login.commandTab(0,0);
                    }
                } catch (Throwable ignored) {}
            }
            if (System.getProperty("phuc.user")==null && resumePending && config.reconnect && charId>=0 && GameCanvas.currentScreen==GameCanvas.login && !reconnecting) {
                reconnecting=true; selectionSent=false; reconnectAt=now+1000;
            }
            reconnect(now);
            boolean inGame=EntryGate.inWorld(Session_ME.gI().isConnected(),GameCanvas.currentScreen==GameCanvas.game,
                    GameScreen.player!=null,GameCanvas.loadmap!=null && GameCanvas.loadmap.mapW>0 && GameCanvas.loadmap.mapH>0,reconnecting);
            if (inGame && !entryObserved) ConnectionTrace.event("ENTER_GAME map="+GameCanvas.loadmap.idMap);
            entryObserved=inGame;
            if (!entryGate.ready(inGame,now)) { animal=null; return; }
            if (!supportApplied) {
                applySupportSettings(); supportApplied=true;
            }
            Player p=GameScreen.player; if (!reconnecting) {
                charId=p.ID; if (config.savedCharacter!=charId) { config.savedCharacter=charId; save(); }
            }
            animal=OwnedAnimal.find(p,GameScreen.Vecplayers);
            live.navigation.observe(now);
            if (resumePending && GameCanvas.currentScreen==GameCanvas.game) {
                if (!live.active()) live.toggle(now);
                if (live.active()) { resumePending=false; config.resumeTrade=true; save(); }
            }
            boolean wasActive=live.active();
            live.tick(animal,now);
            if (wasActive && !live.active()) { config.resumeTrade=false; save(); status=live.engine.status; }
            if (live.active()) status=live.engine.status+" | "+live.detail;
            PaintInfoGameScreen.isShowInGame = true;
            PaintInfoGameScreen.hShowInGame = 0;
            PaintInfoGameScreen.timeDoNotClick = GameCanvas.timeNow;
            if (animal != null) {
                p.vMax = 3;
            } else if (p.vMax < 6) {
                p.vMax = 6;
            }
            if (GameCanvas.currentScreen!=GameCanvas.game) return;
            if (GameCanvas.loadmap.idMap!=lastMap) {
                lastMap=GameCanvas.loadmap.idMap;
                maintenance.onMapChange(now);
                lastNetworkActivity=now;
                for (int i=0;i<LoadMap.vecPointChange.size();i++) {
                    Point point=(Point)LoadMap.vecPointChange.elementAt(i);
                    graph.add(GameCanvas.loadmap.nameMap,point.name,point.x,point.y);
                    record("EXIT map="+lastMap+" from="+GameCanvas.loadmap.nameMap+" to="+point.name+" x="+point.x+" y="+point.y);
                }
            }
            if (!live.active() && route!=null && !GameCanvas.menu2.isShowMenu && GameCanvas.currentDialog==null) {
                if (live.travel(route,now)) { route=null; status="Đã đến map"; }
                else status=live.navigation.status;
            }
            if (config.autoMount && config.mountItem<0 && Item.VecInvetoryPlayer!=null) {
                for (int i=0;i<Item.VecInvetoryPlayer.size();i++) {
                    Item candidate=(Item)Item.VecInvetoryPlayer.elementAt(i);
                    if (isMountItem(candidate) && (config.mountName.length()==0 || config.mountName.equalsIgnoreCase(candidate.itemName))) {
                        config.mountItem=candidate.Id; break;
                    }
                }
            }
            boolean suppressMount = live.active() && (
                    live.engine.state == TradeEngine.REPLACE
                    || live.engine.state == TradeEngine.WAIT_REPLACE
                    || live.engine.state == TradeEngine.WAIT_NEW
                    || live.engine.state == TradeEngine.SUMMON
                    || live.engine.state == TradeEngine.BUY
                    || live.engine.state == TradeEngine.WAIT_BUY
                    || live.engine.state == TradeEngine.STOCK
                    || live.view.gemDrops
                    || live.intent == LiveTrade.SUMMON
            );
            if (config.autoMount && config.mountItem>=0 && p.typeMount==-1 && p.hp>0 && animal==null && !suppressMount && now>=nextMount
                    && !GameCanvas.menu2.isShowMenu && GameCanvas.currentDialog==null) {
                nextMount=now+15000; GlobalService.gI().Use_Potion((short)config.mountItem);
            }
            boolean mustDismount = p.typeMount != -1 && (animal != null || suppressMount || (live.active() && live.engine.state == TradeEngine.TO_SELL));
            if (mustDismount && now >= nextDismount && !GameCanvas.menu2.isShowMenu && GameCanvas.currentDialog == null) {
                nextDismount = now + 1500;
                GlobalService.gI().useMount((byte)-1);
            }
        } catch (Throwable e) {
            String name = e.getClass().getName();
            int dot = name.lastIndexOf('.');
            if (dot >= 0) name = name.substring(dot + 1);
            status = "Lỗi " + name + (e.getMessage() != null ? (": " + e.getMessage()) : "");
            e.printStackTrace();
        }
    }
    private static void walk(int x,int y) {
        Player p=GameScreen.player;
        if (p.hp<=0 || p.posTransRoad!=null) return;
        short[] path=GameCanvas.game.updateFindRoad(x/LoadMap.wTile,y/LoadMap.wTile,p.x/LoadMap.wTile,p.y/LoadMap.wTile,120);
        if (path==null || path.length==0 || path.length>120) { status="Không tìm thấy lối đi an toàn"; route=null; return; }
        p.resetMove(); p.toX=p.x; p.toY=p.y; p.countAutoMove=0; p.posTransRoad=path;
        status="Đi bộ đến "+route;
    }
    private static void stopWalking() {
        Player p=GameScreen.player; if (p==null) return;
        p.posTransRoad=null; p.countAutoMove=0; p.toX=p.x; p.toY=p.y;
        p.xStopMove=0; p.yStopMove=0; p.vx=0; p.vy=0;
    }
    public static void disconnected() {
        ConnectionTrace.event("DISCONNECTED");
        entryGate.ready(false,0); entryObserved=false; supportApplied=false;
        init();
        live.disconnected();
        if (!config.reconnect || charId<0) return;
        if (!reconnecting) { reconnectAt=System.currentTimeMillis()+30000; reconnectAttempts=0; }
        reconnecting=true; selectionSent=false;
        GlobalLogicHandler.isDisConect=false; GlobalLogicHandler.timeReconnect=0;
    }
    private static void reconnect(long now) {
        if (!config.reconnect || !reconnecting) return;
        // Original/manual selection can complete without our selectionSent flag.
        if (ReconnectPolicy.enteredGame(Session_ME.gI().isConnected(),
                GameCanvas.currentScreen==GameCanvas.game, GameScreen.player!=null)) {
            reconnecting=false; selectionSent=false; reconnectAttempts=0; reconnectAt=0;
            charId=GameScreen.player.ID;
            GlobalLogicHandler.isDisConect=false; GlobalLogicHandler.timeReconnect=0;
            status="Đã kết nối lại";
            return;
        }
        if (GameCanvas.currentScreen==GameCanvas.selectChar && Session_ME.gI().isConnected()) {
            if (!selectionSent) {
                if (config.charSlot>=0 && config.charSlot<3 && config.charSlot<SelectCharScreen.VecSelectChar.size()) {
                    selectCharacter(GameCanvas.selectChar,config.charSlot); selectionSent=true; return;
                }
                for (int i=0;i<SelectCharScreen.VecSelectChar.size();i++) {
                    MainObject object=(MainObject)SelectCharScreen.VecSelectChar.elementAt(i);
                    if (object.ID==charId) {
                        selectCharacter(GameCanvas.selectChar,i); selectionSent=true; return;
                    }
                }
                status="Không tìm thấy nhân vật cũ; chờ chọn thủ công";
            }
            return;
        }
        // Never resend login on an established socket, including map loading.
        if (!ReconnectPolicy.shouldLogin(Session_ME.gI().isConnected(),
                Session_ME.gI().connecting, now, reconnectAt)) return;
        reconnectAt=now+Math.min(300000L,30000L*(1L<<Math.min(reconnectAttempts++,3)));
        status="Đang kết nối lại";
        selectionSent=false; SelectCharScreen.IDCHAR=charId; SelectCharScreen.reSelect=false;
        GameCanvas.login.checkLoginAgain(GameCanvas.IndexServer);
    }
    private static void selectCharacter(SelectCharScreen screen,int index) {
        SelectionBridge.select(screen,index);
    }
    public static void npcChat(MainObject npc) {
        lastNetworkActivity=System.currentTimeMillis();
        if (!LiveTrade.isNpc(npc)) return;
        init(); live.chat(npc);
        record("NPC map="+GameCanvas.loadmap.idMap+" id="+npc.ID+" text="+npc.strChatPopup);
        if (npc.getIDnpc()!=selectedNpc) return;
        TradeStock stock=TradeStock.parse(npc.strChatPopup);
        latestStock="KC="+stock.count[0]+" Rubi="+stock.count[1]+" Saphia="+stock.count[2]+" (-1: chưa rõ)";
    }
    public static void stockRequested(int id) { selectedNpc=id; latestStock="Đang chờ NPC "+id; }
    public static void serverMenu(mVector items,int menuId,int npcId,String text) {
        lastNetworkActivity=System.currentTimeMillis();
        init(); live.menu(items,menuId,npcId,text);
        if (live.hideMenu(npcId)) GameCanvas.menu2.isShowMenu=false;
        record("MENU map="+GameCanvas.loadmap.idMap+" npc="+npcId+" menu="+menuId+" text="+text);
        if (items!=null) for (int i=0;i<items.size();i++) record("OPTION "+i+" "+((iCommand)items.elementAt(i)).caption);
    }
    public static void serverDialog(Object source,String text,mVector commands) {
        lastNetworkActivity=System.currentTimeMillis();
        init(); live.dialog(source,text,commands);
    }
    public static void serverDialogOne(Object source,String text,iCommand command) {
        mVector commands=new mVector(); if (command!=null) commands.addElement(command); serverDialog(source,text,commands);
    }
    private static TabScreenNew previousShop;
    public static void serverNamedShop(mVector items,int id,int type,String title) {
        lastNetworkActivity=System.currentTimeMillis();
        init();
        if (live.cargoShop(items,id,type,title)) { previousShop=GameCanvas.shopNpc; return; }
        serverShop(items,id,type);
    }
    public static void serverShop(mVector items,int id,int type) {
        init(); live.shop(items,id,type);
        if (live.hasHiddenShop()) previousShop=GameCanvas.shopNpc;
    }
    public static void finishNpcDialog() {
        if (live==null || !live.hasHiddenShop()) GameCanvas.end_Dialog();
    }
    public static boolean hideTradeShop() {
        return live!=null && live.active() && live.hasHiddenShop();
    }
    public static void paintTradeFallback(mGraphics g) {
        if (GameCanvas.game!=null) GameCanvas.game.paint(g);
    }
    public static boolean hideTradeMenu() {
        if (live!=null && live.active() && live.isBuying() && GameScreen.ObjFocus!=live.getBeast()) { if (GameCanvas.menu2!=null && LiveTrade.isBeastMenu(GameCanvas.menu2.menuItems)) { GameCanvas.menu2.isShowMenu=false; return true; } }
        return false;
    }
    public static boolean hideTradeDialog(Object dialog) {
        return live!=null && live.hideDialog(dialog);
    }
    public static void finishTradeShop() {
        lastNetworkActivity=System.currentTimeMillis();
        if (live!=null && live.takeHiddenShop()) { GameCanvas.shopNpc=previousShop; previousShop=null; }
    }
    public static void serverText(String text) {
        lastNetworkActivity=System.currentTimeMillis(); init(); live.serverText(text); record("SERVER "+text); }
    private static void record(String line) {
        if (!diagnostics) return;
        System.out.println("[PhucTrade] "+line);
        log+=line+"\n"; if (log.length()>6000) log=log.substring(log.length()-6000);
    }
    public static void paint(mGraphics g) {
        init(); if (GameScreen.player==null || GameCanvas.currentScreen!=GameCanvas.game) return;
        int tx=g.getTranslateX(), ty=g.getTranslateY();
        g.translate(-tx,-ty);
        try {
            MainObject visibleAnimal=Session_ME.gI().isConnected() && !reconnecting
                    ? OwnedAnimal.find(GameScreen.player,GameScreen.Vecplayers) : null;
            if (config.visual && visibleAnimal!=null && MainScreen.cameraMain!=null) {
                int cx=MainScreen.cameraMain.xCam,cy=MainScreen.cameraMain.yCam;
                g.setColor(0xffff00); g.drawLine(GameScreen.player.x-cx,GameScreen.player.y-cy,visibleAnimal.x-cx,visibleAnimal.y-cy,false);
                g.setColor(0xff9900); g.drawArc(visibleAnimal.x-cx-10,visibleAnimal.y-cy-10,20,20,0,360,false);
            }
            if (GameCanvas.menu2!=null && GameCanvas.menu2.isShowMenu || GameCanvas.currentDialog!=null) return;
            int bx=ToolInput.X, by=ToolInput.Y, bw=ToolInput.WIDTH, bh=ToolInput.HEIGHT;
            g.setColor(0x181818); g.fillRect(bx,by,bw,bh,false);
            g.setColor(0xff9900); g.drawRect(bx,by,bw,bh,false);
            g.setColor(0x444444); g.drawRect(bx+1,by+1,bw-2,bh-2,false);
            mFont.tahoma_7b_yellow.drawString(g,"MENU TOOL",bx+(bw>>1),by+4,2,false);

            if (!config.hud) return;
            int y=42;
            line(g,"PhucHso | "+GameCanvas.loadmap.nameMap+" #"+GameCanvas.loadmap.idMap+" [Phím M]",y); y+=11;
            line(g,"Đánh: "+on(Player.isAutoFire==1)+" HP/MP: "+on(Player.isAutoHPMP)+" Nhặt: "+on(Player.autoItem!=null),y); y+=11;
            line(g,autotrade.ProxyConnection.hud(),y); y+=11;
            if (autotrade.ProxyConnection.enabled()) { line(g,"SOCKS5: "+System.getProperty("phuc.proxy.host")+":"+System.getProperty("phuc.proxy.port"),y); y+=11; }
            line(g,status,y); y+=11;
            line(g,TradeConfig.ANIMALS[config.animal]+" | "+TradeConfig.GEMS[config.gem]+" | Bãi: "+TradeConfig.MARKETS[config.market],y); y+=11;
            line(g,"HP thú: "+(animal==null ? "--" : animal.hp+"/"+animal.maxHp)+" | Hàng: "+(live.view.cargo<0 ? "--" : ""+live.view.cargo)+"/"+TradeConfig.MAX_CARGO[config.animal],y); y+=11;
            if (GameScreen.pet!=null) { line(g,"Pet: "+GameScreen.pet.name+" ("+GameScreen.pet.hp+"/"+GameScreen.pet.maxHp+")",y); y+=11; }
            line(g,"HS: "+(config.reviveMinMs==0?"Ngay":config.reviveMinMs/1000+"–"+config.reviveMaxMs/1000+"s")+" | Đổi HP<"+TradeConfig.HP[config.hpIndex]+"%",y); y+=11;
            line(g,latestStock,y); y+=11;
            MainObject focus=GameScreen.ObjFocus;
            if (focus!=null) line(g,displayName(focus)+" ID="+focus.ID+" Type="+focus.typeObject,y);
        } finally { g.translate(tx,ty); }
    }
    private static void line(mGraphics g,String text,int y) { mFont.tahoma_7_yellow.drawString(g,text,3,y,0,false); }
    public static boolean hideEffects() { return config.lowDetail && GameCanvas.currentScreen==GameCanvas.game; }
    public static boolean paintLowDetail(mGraphics g) {
        init();
        if (!hideEffects() || GameCanvas.loadmap==null || MainScreen.cameraMain==null || GameScreen.player==null) return false;
        LowDetailRenderer.paint(g); paint(g); return true;
    }
}
