package autotrade;

import CLib.Session_ME;
import CLib.mVector;
import GameObjects.Item;
import GameObjects.AutoGetItem;
import GameObjects.MainObject;
import GameObjects.Player;
import GameScreen.GameScreen;
import InterfaceComponents.iCommand;
import Main.GameCanvas;
import netcommand.global.GlobalService;
import java.util.Vector;

/** Drives the existing game protocol using IDs and captions observed in the current session. */
public final class LiveTrade implements TradeEngine.Port {
    public static final int QUERY=1, SUMMON=2, BUY=3, SELL=4, RECALL=5;
    public final TradeEngine engine;
    public final Navigator navigation;
    public final TradeEngine.View view=new TradeEngine.View();
    public final TradeEarnings earnings=new TradeEarnings();
    public long saleAmount=-1;
    // The original client displays coin as vàng; gold is the other currency.
    private long coinsBeforeSale=-1;
    private long receiptWaitUntil;
    private final TradeConfig config;
    private final Vector events=new Vector();
    private MainObject beast,buyer,seller;
    public int intent;
    private int request, sentRequest, queriedNpc=-10000, requestMarket, scanIndex, lastMap=-1, oldBeast=-1;
    private int menuNpc,menuId,shopId,argument;
    private mVector menu,shop;
    private long menuAt, nextAction, sentAt, absentAt, lastInfo, lastNow, sellStart;
    private boolean summoned, purchaseSent, saleSent, saleAcknowledged, hadBeast, recalled, reconnecting;
    private boolean itemPurchaseSent, leashSecured;
    private boolean itemRevivePending;
    public Object activeDialog;
    public String activeDialogText;
    public mVector activeDialogChoices;
    private long saleConfirmedAt;
    private mVector cachedShop;
    private int cachedShopId, cachedMap, cachedNpc;
    private long lastPurchase;
    private Object hiddenDialog;
    private boolean hiddenShop;
    public boolean hideMenu(int npc) { return active() && ((intent==BUY && buyer!=null && buyer.getIDnpc()==npc) || (isBuying() && npc==-56 && GameScreen.ObjFocus!=beast)); }
    public MainObject getBeast() { return beast; }
    public boolean hideDialog(Object dialog) { return active() && dialog!=null && dialog==hiddenDialog; }
    public static boolean isBeastMenu(mVector items) { if (items==null) return false; for (int i=0;i<items.size();i++) { Object obj=items.elementAt(i); if (obj instanceof iCommand) { String caption=TradeStock.normalize(((iCommand)obj).caption); if (caption.indexOf("hanh trang")>=0 || caption.indexOf("tang toc")>=0 || caption.indexOf("bao ho")>=0 || caption.indexOf("hoi mau")>=0) return true; } } return false; }
    public static boolean isReviveMenu(mVector items) { if (items==null) return false; for (int i=0;i<items.size();i++) { Object obj=items.elementAt(i); if (obj instanceof iCommand) { String caption=TradeStock.normalize(((iCommand)obj).caption); if (caption.indexOf("vat pham hoi sinh")>=0 || caption.indexOf("thanh gia")>=0) return true; } } return false; }
    public boolean hasHiddenShop() { return hiddenShop; }
    public boolean takeHiddenShop() { boolean result=hiddenShop; hiddenShop=false; return result; }
    public boolean isBuying() { return intent==BUY || engine.state==TradeEngine.BUY || engine.state==TradeEngine.WAIT_BUY; }
    private String lastConfirmedText;
    private String recoveryMap="";
    public String lastBeastMap="";
    public int lastBeastX=-1, lastBeastY=-1;
    private long nextPickup;
    private String teleportTarget,teleportAttempt="";
    private long teleportUntil;
    private int dialogSequence;
    private int infoAttempts;
    private int cargoRequestAnimal=-1, cargoRequestMap=-1;
    private long cargoRequestAt;
    private long nextRecoverySave;
    /** A real user input takes ownership of an outstanding animal dialog. */
    public void manualInput() { cargoRequestAnimal=-1; }
    private boolean stoppedBuying, resumeBuying, stoppedEscorting, resumeEscorting;
    private long resumeEscortUntil;
    private int stoppedGem=-1, stoppedAnimal=-1, stoppedMap=-1, stoppedMarket=-1, stoppedCargo=-1;
    private long stoppedPurchase;
    private boolean stoppedFull;
    public String detail="";
    private static final class Event {
        int type,id,menu,map,sequence; String text; mVector choices; Object dialog;
    }
    public LiveTrade(TradeConfig c,MapGraph graph) {
        config=c;
        config.maxSaleAttempts=5;
        config.saleTimeoutMs=4000;
        navigation=new Navigator(graph);
        engine=new TradeEngine(c,this);
    }
    public boolean active() { return engine.state!=TradeEngine.STOP; }
    public void toggle(long now) {
        navigation.resetJourney();
        if (active()) {
            stoppedEscorting=engine.state==TradeEngine.TO_SELL;
            stoppedGem=config.gem;
            stoppedBuying=isBuying() || engine.state==TradeEngine.STOCK;
            stoppedAnimal=beast==null?-1:beast.ID;
            stoppedMap=GameCanvas.loadmap.idMap; stoppedMarket=engine.market;
            stoppedCargo=view.cargo; stoppedFull=view.full; stoppedPurchase=lastPurchase;
            engine.stop(); clearIntent(); return;
        }
        resumeEscorting=stoppedEscorting && stoppedGem==config.gem && stoppedMarket==config.market;
        stoppedEscorting=false; resumeEscortUntil=now+20000; leashSecured=false;
        resumeBuying=stoppedBuying || stoppedAnimal>=0 && stoppedGem!=config.gem; stoppedBuying=false;
        clearIntent(); detail=""; view.cargo=-1; view.full=false; view.cargoRevision=0;
        hadBeast=false; oldBeast=-1; absentAt=0; summoned=false; recalled=false; reconnecting=false;
        infoAttempts=0; queriedNpc=-10000;
        Player.autoItem=new AutoGetItem((byte)0,(byte)0,(byte)0);
        if (!resumeBuying && !resumeEscorting) {
            int charId=GameScreen.player!=null ? GameScreen.player.ID : -1;
            TradeRecovery.Record rec=TradeRecovery.load(charId,now);
            if (rec!=null && rec.map!=null && rec.map.length()>0) {
                lastBeastMap=rec.map;
                lastBeastX=rec.x;
                lastBeastY=rec.y;
                recoveryMap=rec.map;
                hadBeast=true;
                view.cargo=rec.cargo;
                config.market=rec.market;
                config.gem=rec.gem;
                config.animal=rec.animal;
                engine.start(now);
                if (beast!=null) {
                    engine.resumeEscort(now);
                } else {
                    engine.resumeRecovery(now);
                    detail="V\u1ec1 map "+lastBeastMap+" t\u00ecm th\u00fa";
                }
                return;
            }
        }
        engine.start(now);
    }
    private void clearIntent() { receiptWaitUntil=0; sellStart=0; coinsBeforeSale=-1; cargoRequestAnimal=-1; intent=0; menu=null; shop=null; cachedShop=null; hiddenShop=false; hiddenDialog=null; lastPurchase=0; purchaseSent=false; itemPurchaseSent=false; saleSent=false; saleAcknowledged=false; saleConfirmedAt=0; lastConfirmedText=null; recalled=false; summoned=false; }
    public boolean hasLostTrip() { return (recoveryMap!=null && recoveryMap.length()>0) || (lastBeastMap!=null && lastBeastMap.length()>0) || hadBeast; }
    public boolean ready() { return GameScreen.player!=null; }
    public void notifyUser(String text) { detail=text; GameCanvas.addInfoChar(text); }
    public void stopMovement() { Navigator.stop(); }
    public void goMarket(int index) {
        int idx = (index>=0 && index<TradeConfig.MARKETS.length) ? index : 0;
        travel(TradeConfig.MARKETS[idx],lastNow);
    }
    public boolean travel(String target,long now) {
        int targetMap=-1;
        for (int i=0;i<TradeConfig.MARKETS.length;i++) {
            if (Navigator.same(target,TradeConfig.MARKETS[i])) targetMap=TradeConfig.MARKET_MAP_IDS[i];
        }
        if (targetMap>=0 && GameCanvas.loadmap.idMap==targetMap || Navigator.same(GameCanvas.loadmap.nameMap,target)) {
            teleportTarget=null; Navigator.stop(); return true;
        }
        String key=GameCanvas.loadmap.idMap+":"+target;
        if (config.teleport && beast==null && !key.equals(teleportAttempt)) {
            teleportAttempt=key;
            for (int i=0;i<Item.VecInvetoryPlayer.size();i++) {
                Item item=(Item)Item.VecInvetoryPlayer.elementAt(i);
                if (item!=null && item.ItemCatagory==4 && item.numPotion>0 && TradeStock.normalize(item.itemName).equals("da dich chuyen")) {
                    teleportTarget=target; teleportUntil=now+8000; GlobalService.gI().Use_Potion((short)item.Id); break;
                }
            }
        }
        if (teleportTarget!=null && now<teleportUntil) { detail="Chờ lựa chọn đá dịch chuyển"; return false; }
        teleportTarget=null;
        boolean arrived=navigation.go(target,targetMap,beast,now); detail=navigation.status; return arrived;
    }
    public synchronized boolean animalCargo(String name,int count) {
        if (!active() || beast==null || count<0) return false;
        String normName=TradeStock.normalize(name);
        String normBeast=TradeStock.normalize(beast.name);
        boolean isTradeAnimalName=normName.equals(normBeast)
                || normName.equals("hanh trang") || normName.equals("hanh trang thu")
                || normName.equals("hang tren thu") || normName.equals("thong tin thu")
                || normName.equals("thong tin thu buon");
        if (!isTradeAnimalName) return false;
        if (beast!=GameScreen.ObjFocus && (beast.ID!=cargoRequestAnimal || GameCanvas.loadmap.idMap!=cargoRequestMap)) return false;
        Event e=new Event(); e.type=6; e.id=beast.ID; e.menu=count; e.map=GameCanvas.loadmap.idMap; enqueue(e);
        return true;
    }
    public void goSeller() {
        if (beast==null) { Navigator.stop(); detail="Chờ thú đi buôn xuất hiện để dắt tiếp"; return; }
        if (view.cargo<0 && !view.full) {
            Navigator.stop(); detail="Kiểm tra túi hàng trên thú trước khi dắt về bán";
            if (cargoRequestAnimal<0 && GameCanvas.menu2!=null && GameCanvas.menu2.isShowMenu) {
                detail="Chờ bạn đóng menu để tiếp tục kiểm tra hàng"; return;
            }
            if (cargoRequestAnimal!=beast.ID || cargoRequestMap!=GameCanvas.loadmap.idMap || lastNow-cargoRequestAt>=3000) {
                cargoRequestAnimal=beast.ID; cargoRequestMap=GameCanvas.loadmap.idMap; cargoRequestAt=lastNow;
                info(lastNow);
            }
            return;
        }
        if (distance(beast)>85) leashSecured=false;
        if (!leashSecured) {
            int d = MainObject.getDistance(GameScreen.player.x, GameScreen.player.y, beast.x, beast.y);
            if (d > 35) {
                navigation.approachAnimal(beast,lastNow);
                detail = "Đang quay lại nhận bò (" + d + "px)";
                navigation.status=detail;
                return;
            }
            leashSecured = true;
        }
        if (navigation.go(TradeConfig.SELLER_MAP,-1,beast,lastNow)) {
            seller=findNamed("graham");
            if (seller!=null) {
                // Reaching Graham with the player alone is not enough. walk()
                // returns immediately at its destination, before leash checks.
                if (distance(seller)<=64 && distance(beast)>40) {
                    navigation.approachAnimal(beast,lastNow);
                    detail="Đến Graham; quay lại đón thú để bán hàng";
                } else {
                    navigation.walk(seller.x,seller.y,1,beast,lastNow);
                    detail=navigation.status;
                }
            }
            else detail="Chờ NPC Graham xuất hiện";
        } else detail=navigation.status;
    }
    public void queryStock(int market,int sequence) {
        if (view.market!=market) return;
        requestMarket=market; request=sequence;
        // Every new stock request needs a fresh NPC response, including a gem
        // preference change immediately after a successful purchase.
        queriedNpc=-10000; intent=QUERY; nextAction=lastNow; menu=null; shop=null;
    }
    private void begin(int action,int value) {
        intent=action; argument=value; nextAction=lastNow; sentAt=0; menu=null; shop=null; lastConfirmedText=null; itemPurchaseSent=false;
    }
    public void summon(int animal) {
        if (view.market!=engine.market && engine.state!=TradeEngine.WAIT_REPLACE && engine.state!=TradeEngine.WAIT_NEW) return;
        if (engine.state==TradeEngine.WAIT_REPLACE || engine.state==TradeEngine.WAIT_NEW) { view.cargo=-1; view.full=false; infoAttempts=0; lastInfo=0; }
        begin(SUMMON,animal); summoned=false; recalled=false;
    }
    public void buy(int gem) {
        if (view.market!=engine.market) return;
        begin(BUY,gem); purchaseSent=false;
        if (buyer==null) buyer=findNamed("dylan");
        if (buyer==null) buyer=findNamed("thuong nhan");
        if (buyer!=null && GameScreen.ObjFocus==null) GameScreen.ObjFocus=buyer;
        if (cachedShop!=null && cachedMap==GameCanvas.loadmap.idMap) {
            shop=cachedShop; shopId=cachedShopId;
        }
    }
    public void sell() { begin(SELL,0); saleSent=false; saleAcknowledged=false; saleAmount=-1; sentAt=0; menu=null; sellStart=lastNow; saleConfirmedAt=0; lastConfirmedText=null; coinsBeforeSale=(GameScreen.player!=null ? GameScreen.player.coin : -1); }
    public void recall() {
        purchaseSent=false; itemPurchaseSent=false; cachedShop=null;
        begin(RECALL,config.animal); recalled=false;
        if (beast!=null) {
            for (int i=0;i<TradeConfig.ANIMALS.length;i++)
                if (TradeSignals.animal(beast.name,i)) { argument=i; config.animal=i; break; }
            Player.autoItem=new AutoGetItem((byte)0,(byte)0,(byte)0);
        }
    }
    public void recoverPreviousMap() {
        if (recoveryMap==null || recoveryMap.length()==0)
            recoveryMap=(lastBeastMap!=null && lastBeastMap.length()>0) ? lastBeastMap : navigation.previousMap;
        detail="Đang tìm lại thú; chờ xác minh map cũ";
        if (recoveryMap!=null && recoveryMap.length()>0) {
            if (GameCanvas.loadmap!=null && Navigator.same(GameCanvas.loadmap.nameMap,recoveryMap)) {
                if (lastBeastX>=0 && lastBeastY>=0) navigation.walk(lastBeastX,lastBeastY,2,null,lastNow);
                detail="Tìm thú trong "+recoveryMap;
            } else {
                navigation.go(recoveryMap,-1,null,lastNow);
                detail="Về map "+recoveryMap+" tìm thú";
            }
        }
    }
    public void followAnimal() { if (beast!=null) navigation.walk(beast.x,beast.y,2,null,lastNow); }
    // Fail closed: a generic revive caption is not proof that revival is item-only.
    public static boolean isItemOnlyRevive(String text) {
        String s=TradeStock.normalize(text);
        return s.indexOf("ngoc")<0 && s.indexOf("gem")<0 && s.indexOf("ruby")<0
                && s.indexOf("kim cuong")<0 && s.indexOf("vang")<0
                && (s.indexOf("vat pham hoi sinh")>=0 || s.indexOf("thanh gia")>=0
                    || s.indexOf("bua hoi sinh")>=0 || s.indexOf("thuoc hoi sinh")>=0
                    || s.indexOf("binh hoi sinh")>=0);
    }
    private boolean canConfirmRevive(String text) {
        String s=TradeStock.normalize(text);
        // The server states the gem charge in the confirmation. Never approve it.
        return itemRevivePending && hasReviveItem() && s.indexOf("ngoc")<0
                && s.indexOf("gem")<0 && s.indexOf("ruby")<0 && s.indexOf("kim cuong")<0
                && (s.indexOf("hoi sinh")>=0 || s.indexOf("thanh gia")>=0 || s.indexOf("revive")>=0);
    }
    private boolean canUseReviveItem(String text) {
        return itemRevivePending && hasReviveItem() && isItemOnlyRevive(text);
    }
    public boolean hasReviveItem() { return findReviveItem() != null; }
    public static Item findReviveItem() {
        if (Item.VecInvetoryPlayer == null) return null;
        for (int i = 0; i < Item.VecInvetoryPlayer.size(); i++) {
            Item item = (Item) Item.VecInvetoryPlayer.elementAt(i);
            if (item != null && isReviveItem(item)) return item;
        }
        return null;
    }
    public static boolean isReviveItem(Item item) {
        if (item == null || item.itemName == null) return false;
        if (item.numPotion <= 0) return false;
        if (item.ItemCatagory != 0 && item.ItemCatagory != 4) return false;
        String name = TradeStock.normalize(item.itemName);
        return name.indexOf("thanh gia") >= 0
                || name.indexOf("bua hoi sinh") >= 0
                || name.indexOf("thuoc hoi sinh") >= 0
                || name.indexOf("ngoc hoi sinh") >= 0
                || name.indexOf("binh hoi sinh") >= 0
                || name.equals("hoi sinh")
                || name.indexOf("vat pham hoi sinh") >= 0;
    }
    public void resurrect() {
        if (GameScreen.player!=null && (GameScreen.player.hp<=0 || GameScreen.player.Action==4)) {
            if (!hasReviveItem()) { resurrectVillage(); return; }
            itemRevivePending=true;
            GlobalService.gI().gohome((byte)1);
        }
    }
    public void resurrectVillage() {
        itemRevivePending=false;
        if (GameScreen.player!=null && (GameScreen.player.hp<=0 || GameScreen.player.Action==4)) {
            GlobalService.gI().gohome((byte)0);
            if (GameCanvas.currentDialog!=null) GameCanvas.end_Dialog();
            if (GameCanvas.menu2!=null) GameCanvas.menu2.isShowMenu=false;
            recalled=false;
            clearIntent();
            if (hadBeast) {
                absentAt = lastNow > 2000 ? lastNow - 2000 : 1;
                if (recoveryMap==null || recoveryMap.length()==0) {
                    recoveryMap = (lastBeastMap!=null && lastBeastMap.length()>0) ? lastBeastMap : navigation.previousMap;
                }
            }
        }
    }
    public void resetLostTrip() {
        earnings.abandon();
        detail="";
        clearIntent(); hadBeast=false; absentAt=0; view.cargo=0; view.full=false; view.animalMissing=false; leashSecured=false;
        reconnecting=false; resumeEscorting=false; resumeBuying=false; cargoRequestAnimal=-1;
        oldBeast=-1; navigation.resetJourney();
        lastBeastMap=""; lastBeastX=-1; lastBeastY=-1; recoveryMap="";
        TradeRecovery.clear();
    }
    public void disconnected() {
        if (!active()) return;
        view.connected=false; engine.tick(view,System.currentTimeMillis());
        clearIntent();
        synchronized (this) { events.removeAllElements(); }
        reconnecting=true; view.cargo=-1; view.full=false; buyer=null; seller=null; queriedNpc=-10000; leashSecured=false;
        navigation.resetConnection();
    }
    public synchronized void chat(MainObject npc) {
        Event e=new Event(); e.type=1; e.id=npc.getIDnpc(); e.text=npc.strChatPopup; e.map=GameCanvas.loadmap.idMap; enqueue(e);
    }
    public synchronized void menu(mVector options,int id,int npc,String text) {
        Event e=new Event(); e.type=2; e.id=npc; e.menu=id; e.text=text; e.map=GameCanvas.loadmap.idMap;
        e.choices=options; enqueue(e);
    }
    public synchronized void dialog(Object source,String text,mVector choices) {
        activeDialog=source; activeDialogText=text; activeDialogChoices=choices;
        hiddenDialog=null;
        if (active() && (TradeSignals.startTradeConfirmation(text)
                || (intent==BUY || engine.state==TradeEngine.BUY || engine.state==TradeEngine.WAIT_BUY)
                    && (TradeSignals.gem(text,argument) || TradeSignals.bought(text) || TradeSignals.full(text) || TradeSignals.depleted(text) || TradeSignals.tooFast(text)))) {
            hiddenDialog=source;
        }
        Event e=new Event(); e.type=3; e.dialog=source; e.text=text; e.choices=choices; e.map=GameCanvas.loadmap.idMap; e.sequence=++dialogSequence; enqueue(e);
    }
    public synchronized boolean cargoShop(mVector items,int id,int type,String title) {
        if (id!=13 || type!=11 || items==null) return false;
        if (!animalCargo(title,items.size())) return false;
        // Hide only background cargo checks. A selected animal belongs to the
        // user's interaction and its inventory must remain visible.
        hiddenShop=beast!=null && beast.ID==cargoRequestAnimal && GameCanvas.loadmap.idMap==cargoRequestMap;
        return true;
    }
    public synchronized void shop(mVector items,int id,int type) {
        // Trade gems may arrive in either the trade shop or the ordinary potion/material shop.
        // Inventory/chest tabs use a negative shop ID and must never issue buy_item.
        if (id<0 || items==null) return;
        boolean gems=false;
        for (int i=0;i<items.size();i++) {
            Item item=(Item)items.elementAt(i);
            if (item!=null && (TradeSignals.gem(item.itemName,0) || TradeSignals.gem(item.itemName,1) || TradeSignals.gem(item.itemName,2))) { gems=true; break; }
        }
        if (!gems) return;
        if (active() && (intent==BUY || engine.state==TradeEngine.BUY || engine.state==TradeEngine.WAIT_BUY )) {
            hiddenShop=true;
        }
        Event e=new Event(); e.type=4; e.id=id; e.choices=items; e.map=GameCanvas.loadmap.idMap; enqueue(e);
    }
    public synchronized void serverText(String text) {
        Event e=new Event(); e.type=5; e.text=text; e.map=GameCanvas.loadmap.idMap; enqueue(e);
    }
    private void enqueue(Event event) { if (events.size()>=128) events.removeElementAt(0); events.addElement(event); }
    private synchronized Event poll() {
        if (events.size()==0) return null; Event e=(Event)events.elementAt(0); events.removeElementAt(0); return e;
    }
    public void tick(MainObject owned,long now) {
        lastNow=now; beast=owned;
        if (!active()) { Event idle; for (int n=0;n<128 && (idle=poll())!=null;n++) teleportEvent(idle,now); return; }
        view.connected=Session_ME.gI().isConnected();
        if (!view.connected || GameScreen.player==null) { engine.tick(view,now); return; }
        navigation.observe(now);
        if (engine.state!=TradeEngine.TO_SELL) leashSecured=false;
        if (GameCanvas.loadmap.idMap!=lastMap) {
            lastMap=GameCanvas.loadmap.idMap; buyer=null; seller=null; menu=null; shop=null; cachedShop=null; scanIndex=0; queriedNpc=-10000; leashSecured=false;
        }
        view.gemDrops=false;
        boolean beastHasCargo=beast!=null && (view.cargo>0 || view.full) && engine.state!=TradeEngine.WAIT_NEW;
        if (!beastHasCargo) {
            for (int i=0;i<GameScreen.Vecplayers.size();i++) {
                MainObject drop=(MainObject)GameScreen.Vecplayers.elementAt(i);
                if (drop!=null && !drop.isRemove && (drop.typeObject==3 || drop.typeObject==4 || drop.typeObject==5 || drop.typeObject==7)
                        && (TradeSignals.gem(drop.name,0) || TradeSignals.gem(drop.name,1) || TradeSignals.gem(drop.name,2))) view.gemDrops=true;
            }
        }
        boolean wasDead=view.dead;
        view.dead=GameScreen.player!=null && (GameScreen.player.hp<=0 || GameScreen.player.Action==4);
        if (view.dead && !wasDead) clearIntent();
        view.hasReviveItem=view.dead && hasReviveItem();
        view.animal=beast!=null;
        if (view.dead && !wasDead) {
            navigation.teleported=true;
            navigation.resetJourney();
        }
        if (beast!=null) {
            if (oldBeast>=0 && beast.ID!=oldBeast && engine.state!=TradeEngine.SUMMON
                    && engine.state!=TradeEngine.WAIT_NEW && engine.state!=TradeEngine.WAIT_REPLACE) {
                // Cargo belongs to one animal. A different visible animal is
                // not proof that the previous purchase/full-load still exists.
                clearIntent(); view.cargo=-1; view.full=false; lastInfo=0;
                engine.onAnimalNotFound(view,now);
            }
            hadBeast=true; absentAt=0; view.hp=beast.hp; view.maxHp=beast.maxHp;
            view.distance=MainObject.getDistance(GameScreen.player.x,GameScreen.player.y,beast.x,beast.y);
            if (GameCanvas.loadmap!=null) {
                boolean changedMap=!Navigator.same(lastBeastMap,GameCanvas.loadmap.nameMap);
                lastBeastMap=GameCanvas.loadmap.nameMap;
                lastBeastX=beast.x;
                lastBeastY=beast.y;
                if ((changedMap || now>=nextRecoverySave) && (engine.state==TradeEngine.TO_SELL || engine.state==TradeEngine.RECOVER || view.cargo>0)) {
                    nextRecoverySave=now+5000;
                    TradeRecovery.save(GameScreen.player!=null ? GameScreen.player.ID : -1,
                        lastBeastMap,lastBeastX,lastBeastY,view.cargo,engine.market,config.gem,config.animal,now);
                }
            }
            if (summoned && beast.ID!=oldBeast && engine.state==TradeEngine.SUMMON) {
                view.cargo=0; view.full=false; view.cargoRevision++; summoned=false; intent=0;
            }
            if (beast.ID!=oldBeast && engine.state==TradeEngine.WAIT_NEW) {
                view.cargo=-1; lastInfo=0; summoned=false; intent=0;
            }
            oldBeast=beast.ID;
        } else {
            view.distance=0;
            if (hadBeast && absentAt==0) {
                absentAt=now;
                recoveryMap=(lastBeastMap!=null && lastBeastMap.length()>0) ? lastBeastMap : navigation.previousMap;
                TradeRecovery.save(GameScreen.player!=null ? GameScreen.player.ID : -1,
                    recoveryMap,lastBeastX,lastBeastY,view.cargo,engine.market,config.gem,config.animal,now);
            }
            // No visible animal is not evidence of an empty inventory. A
            // delayed existing animal must be queried before recall or buying.
        }
        view.animalMissing=hadBeast && beast==null && !recalled && !saleSent && absentAt>0 && now-absentAt>=2000;
        view.market=-1;
        int currentMapId=GameCanvas.loadmap.idMap;
        for (int i=0;i<3;i++) {
            if (currentMapId==TradeConfig.MARKET_MAP_IDS[i] || Navigator.same(GameCanvas.loadmap.nameMap,TradeConfig.MARKETS[i])) {
                view.market=i; break;
            }
        }
        view.inRecoveryMap=recoveryMap!=null && recoveryMap.length()>0 && GameCanvas.loadmap!=null && Navigator.same(GameCanvas.loadmap.nameMap,recoveryMap);
        if (resumeEscorting) {
            if (beast==null) {
                Navigator.stop(); detail="Chờ tìm lại thú trước khi dắt tiếp";
                if (now>=resumeEscortUntil) {
                    resetLostTrip(); engine.start(now);
                    detail="Không còn thú cũ; về bãi mua chuyến mới";
                }
                return;
            }
            resumeEscorting=false;
            if (beast.ID==stoppedAnimal) {
                resumeBuying=false; view.cargo=stoppedCargo; view.full=stoppedFull;
                leashSecured=false; engine.resumeEscort(now);
            }
        }
        if (resumeBuying) {
            resumeBuying=false;
            if (beast!=null && beast.ID==stoppedAnimal && currentMapId==stoppedMap
                    && view.market==stoppedMarket && config.market==stoppedMarket) {
                view.cargo=stoppedCargo; view.full=stoppedFull; lastPurchase=stoppedPurchase;
                if (!view.full)
                    engine.resumePurchases(view,now);
            }
        }
        seller=findNamed("graham");
        view.atSeller=Navigator.same(GameCanvas.loadmap.nameMap,TradeConfig.SELLER_MAP) && seller!=null && distance(seller)<=64 && beast!=null && view.distance<=40 && MainObject.getDistance(beast.x,beast.y,seller.x,seller.y)<=64 && (view.cargo>0 || view.full);
        if (view.atSeller && engine.state==TradeEngine.TO_SELL) {
            Navigator.stop();
        }
        Event event; for (int n=0;n<128 && (event=poll())!=null;n++) process(event,now);
        if (active() && engine.state!=TradeEngine.WAIT_RECALL && engine.state!=TradeEngine.RECALL)
            earnings.observeCargo(view.cargo>0 || view.full,
                engine.state==TradeEngine.TO_SELL || engine.state==TradeEngine.SELL || engine.state==TradeEngine.WAIT_SALE);
        boolean coinsReceived=coinsBeforeSale>=0 && GameScreen.player!=null && GameScreen.player.coin>coinsBeforeSale;
        boolean emptyAnimal=beast!=null && view.cargo==0 && !view.full;
        boolean saleFinished=saleAcknowledged || emptyAnimal || coinsReceived && beast==null;
        if (engine.state==TradeEngine.WAIT_SALE && saleSent && saleFinished && !view.dead && seller!=null && distance(seller)<=64 && view.connected && Navigator.same(GameCanvas.loadmap.nameMap,TradeConfig.SELLER_MAP)) {
            if (engine.state==TradeEngine.WAIT_SALE || engine.state==TradeEngine.SELL) {
                if (saleAmount<0 && coinsBeforeSale>=0 && GameScreen.player!=null && GameScreen.player.coin>coinsBeforeSale) {
                    saleAmount=GameScreen.player.coin-coinsBeforeSale;
                }
                // A success packet can precede the wallet packet. Keep the sale
                // context briefly so the next trip cannot erase its baseline.
                if (saleAmount<0 && coinsBeforeSale>=0) {
                    saleAcknowledged=true;
                    if (receiptWaitUntil==0) receiptWaitUntil=now+2000;
                    if (now<receiptWaitUntil) return;
                }
                earnings.complete(saleAmount);
            }
            engine.onSaleConfirmed(true,now); clearIntent(); hadBeast=false; view.cargo=0; view.full=false; view.animalMissing=false;
            lastBeastMap=""; lastBeastX=-1; lastBeastY=-1; recoveryMap="";
            TradeRecovery.clear();
        }
        if (recalled && beast==null && engine.state==TradeEngine.WAIT_RECALL) {
            intent=0; hadBeast=false; view.animalMissing=false; TradeRecovery.clear();
        }
        if (reconnecting && view.cargo>=0) { reconnecting=false; intent=0; }
        int stateBeforeTick=engine.state;
        engine.tick(view,now);
        if ((engine.state==TradeEngine.RECOVER || engine.state==TradeEngine.RESYNC) && stateBeforeTick!=engine.state) clearIntent();
        if ((engine.state==TradeEngine.TO_SELL || engine.state==TradeEngine.TO_MARKET)
                && (intent==BUY || intent==QUERY || intent==SUMMON || intent==RECALL)) clearIntent();
        if (engine.state==TradeEngine.TO_MARKET && intent==SELL) clearIntent();
        if (isBuying() && GameScreen.ObjFocus!=beast) {
            if (GameCanvas.menu2!=null && GameCanvas.menu2.isShowMenu && isBeastMenu(GameCanvas.menu2.menuItems)) GameCanvas.menu2.isShowMenu=false;
        }
        if (GameCanvas.currentDialog==null) {
            activeDialog=null; activeDialogText=null; activeDialogChoices=null;
        } else if (!view.dead && activeDialog==GameCanvas.currentDialog && activeDialogChoices!=null) {
            checkAndConfirmSaleDialog(activeDialog, activeDialogText, activeDialogChoices, now);
        }
        if (!view.dead) {
            itemRevivePending=false;
            if (GameCanvas.menu2!=null && GameCanvas.menu2.isShowMenu && isReviveMenu(GameCanvas.menu2.menuItems)) {
                GameCanvas.menu2.isShowMenu=false;
            }
        } else if (GameCanvas.menu2!=null && GameCanvas.menu2.isShowMenu && GameCanvas.menu2.menuItems!=null) {
            mVector items=GameCanvas.menu2.menuItems;
            for (int i=0;i<items.size();i++) {
                Object obj=items.elementAt(i);
                if (obj instanceof iCommand) {
                    iCommand cmd=(iCommand)obj;
                    String cap=TradeStock.normalize(cmd.caption!=null ? cmd.caption : "");
                    if (canUseReviveItem(cap)) {
                        GameCanvas.menu2.menuSelectedItem=i;
                        try { GameCanvas.menu2.commandPointer(2,0); } catch (Throwable ignored) {}
                        GameCanvas.menu2.isShowMenu=false;
                        if (GameCanvas.currentDialog!=null) GameCanvas.end_Dialog();
                        break;
                    }
                }
            }
        }
        if (view.dead && GameCanvas.menu2!=null && GameCanvas.menu2.isShowMenu) resurrectVillage();
        if (!active()) { clearIntent(); return; }
        if (engine.state==TradeEngine.PAUSED || view.dead) return;
        if ((engine.state==TradeEngine.WAIT_NEW || engine.state==TradeEngine.RESYNC) && beast!=null && now-cargoRequestAt>=3000) {
            cargoRequestAnimal=beast.ID; cargoRequestMap=GameCanvas.loadmap.idMap; cargoRequestAt=now;
            info(now);
        }
        if (engine.state==TradeEngine.RESYNC) return;
        beastHasCargo=beast!=null && (view.cargo>0 || view.full) && engine.state!=TradeEngine.WAIT_NEW;
        if (beastHasCargo) view.gemDrops=false;
        if (view.animal && !beastHasCargo && engine.state!=TradeEngine.RECALL
                && engine.state!=TradeEngine.WAIT_RECALL && (view.gemDrops || engine.state==TradeEngine.WAIT_NEW)) {
            MainObject nearestGem=findNearestGem();
            if (nearestGem!=null) {
                if (Player.autoItem==null) Player.autoItem=new AutoGetItem((byte)0,(byte)0,(byte)0);
                int d=MainObject.getDistance(GameScreen.player.x,GameScreen.player.y,nearestGem.x,nearestGem.y);
                if (d<=48 && now>=nextPickup) {
                    nextPickup=now+250;
                    GlobalService.gI().Get_Item_Map((short)nearestGem.ID,(byte)nearestGem.typeObject);
                }
                if (d>20) navigation.walk(nearestGem.x,nearestGem.y,1,null,now);
                detail="Nhặt "+nearestGem.name;
                return;
            } else if (view.gemDrops) {
                view.gemDrops=false;
            }
        }
        pump(now);
    }
    private MainObject findNearestGem() {
        if (GameScreen.Vecplayers==null || GameScreen.player==null) return null;
        MainObject nearest=null; int best=Integer.MAX_VALUE;
        for (int i=0;i<GameScreen.Vecplayers.size();i++) {
            MainObject drop=(MainObject)GameScreen.Vecplayers.elementAt(i);
            if (drop!=null && !drop.isRemove && (drop.typeObject==3 || drop.typeObject==4 || drop.typeObject==5 || drop.typeObject==7)
                    && (TradeSignals.gem(drop.name,0) || TradeSignals.gem(drop.name,1) || TradeSignals.gem(drop.name,2))) {
                int d=MainObject.getDistance(GameScreen.player.x,GameScreen.player.y,drop.x,drop.y);
                if (d<best) { best=d; nearest=drop; }
            }
        }
        return nearest;
    }
    private MainObject findNamed(String name) {
        for (int i=0;i<GameScreen.Vecplayers.size();i++) {
            MainObject object=(MainObject)GameScreen.Vecplayers.elementAt(i);
            if (isNpc(object) && !object.isRemove && TradeStock.normalize(object.name).indexOf(name)>=0) return object;
        }
        return null;
    }
    private int distance(MainObject npc) { return MainObject.getDistance(GameScreen.player.x,GameScreen.player.y,npc.x,npc.y); }
    public static boolean isNpc(MainObject object) {
        return object!=null && (object.typeObject==2 || object.isNPC_server() || object.isNPC());
    }
    private MainObject scanNpc() {
        int size=GameScreen.Vecplayers.size();
        for (int i=0;i<size;i++) {
            MainObject candidate=(MainObject)GameScreen.Vecplayers.elementAt((scanIndex+i)%size);
            if (isNpc(candidate) && !candidate.isRemove) {
                scanIndex=(scanIndex+i)%size; return candidate;
            }
        }
        return null;
    }
    private void info(long now) {
        lastInfo=now; GlobalService.gI().Dynamic_Menu((short)-56,(byte)0,(byte)0);
    }
    private void process(Event e,long now) {
        if (e.map!=GameCanvas.loadmap.idMap) return;
        if (e.type==6) {
            if (beast!=null && beast.ID==e.id) {
                view.cargo=e.menu; view.cargoRevision++;
                // Only authoritative inventory counts, never local purchase estimates.
                int kind=config.animal;
                for (int i=0;i<TradeConfig.ANIMALS.length;i++)
                    if (TradeSignals.animal(beast.name,i)) { kind=i; break; }
                view.full=e.menu>=TradeConfig.MAX_CARGO[kind];
                if (e.menu==0) view.full=false;
                cargoRequestAnimal=-1;
            }
            return;
        }
        if (e.text==null && e.type!=4 && e.type!=2) return;
        if (teleportEvent(e,now)) return;
        if (e.type==3 && e.sequence!=dialogSequence) return;
        if (e.type==4) {
            if (intent==BUY && !itemPurchaseSent && view.market==engine.market) {
                shop=e.choices; shopId=e.id; menu=null;
                if (buyer==null) buyer=findNamed("dylan");
                if (buyer==null) buyer=findNamed("thuong nhan");
                cachedShop=shop; cachedShopId=shopId; cachedMap=e.map;
                if (buyer!=null) cachedNpc=buyer.getIDnpc();
                // Selecting the gem opened its shop; the actual buy request has not been sent yet.
                purchaseSent=false; nextAction=now; engine.onActionProgress(now);
            }
            return;
        }
        String s=TradeStock.normalize(e.text);
        if (TradeSignals.animalNotFound(e.text)) {
            boolean expected=intent==SELL || intent==BUY || cargoRequestAnimal>=0
                    || engine.state==TradeEngine.WAIT_SALE;
            boolean response=e.type==3 || e.type==5
                    || (e.type==1 || e.type==2) && (seller!=null && e.id==seller.getIDnpc()
                        || buyer!=null && e.id==buyer.getIDnpc() || e.id==-56);
            if (active() && response) {
                // This rejection never proves a sale. Close its own dialog,
                // including a late reply after navigation has resumed.
                dismiss(e);
                if (expected && !view.dead && engine.state!=TradeEngine.PAUSED) {
                    clearIntent(); leashSecured=false;
                    engine.onAnimalNotFound(view,now);
                }
                return;
            }
        }
        boolean stockSource=(e.type==1 || e.type==2) && e.id==queriedNpc && intent==QUERY;
        if (stockSource) {
            TradeStock stock=TradeStock.parse(e.text);
            if (stock.choose(config.gem)>=0 || stock.exhausted(config.gem)) {
                buyer=MainObject.get_Object(e.id,(byte)2);
                engine.onStock(requestMarket,sentRequest,e.text,now);
                if (sentRequest==request) intent=0;
            }
        }
        boolean tradeResponse=(e.type==3 || e.type==5 || (e.type==1 || e.type==2) && (buyer!=null && e.id==buyer.getIDnpc() || seller!=null && e.id==seller.getIDnpc()))
                && (sentAt>0 && now-sentAt<=15000 || isBuying() && lastPurchase>0 && now-lastPurchase<=15000);
        TradeStock reportedStock=TradeStock.parse(e.text);
        if (tradeResponse && isBuying()) {
            if (TradeSignals.tooFast(e.text)) {
                purchaseSent=false; dismiss(e); return;
            }
            if (TradeSignals.full(e.text)) {
                view.full=true; intent=0; purchaseSent=false; menu=null; shop=null; dismiss(e); return;
            }
            if (reportedStock.exhausted(config.gem)) {
                intent=0; purchaseSent=false; menu=null; shop=null;
                engine.onAllGemsDepleted(view,now); dismiss(e); return;
            }
            if (TradeSignals.depleted(e.text)) {
                purchaseSent=false; intent=0; menu=null; shop=null; cachedShop=null;
                queriedNpc=-10000;
                engine.onOutOfStock(view,now); dismiss(e); return;
            }
        }
        // NPC stock quantities must never be counted as cargo on the animal.
        boolean requestedCargo=beast!=null && cargoRequestAnimal==beast.ID
                && cargoRequestMap==e.map && now-cargoRequestAt<=15000;
        boolean explicitCargo=s.indexOf("hang tren thu")>=0 || s.indexOf("so hang")>=0
                || s.indexOf("so luong hang")>=0 || s.indexOf("dang cho")>=0
                || beast!=null && s.indexOf(TradeStock.normalize(beast.name))>=0;
        // Global dialogs/server text are not animal inventory. In particular,
        // unrelated "So luong: 0" must never trigger cancellation of a loaded animal.
        boolean infoSource=(e.type==2 && e.id==-56 && beast!=null
                || (e.type==3 || e.type==5) && (requestedCargo && explicitCargo
                    || tradeResponse && isBuying() && TradeSignals.bought(s)))
                && reportedStock.choose(0)<0 && !reportedStock.empty();
        boolean cargoReported=false;
        // An inventory refresh is not another purchase after an interrupted escort.
        if (tradeResponse && isBuying() && TradeSignals.bought(s)) earnings.buyingAgain();
        if (infoSource && e.text!=null) {
            int cargo=TradeSignals.cargo(e.text);
            if (cargo>=0) {
                view.cargo=cargo;
                cargoReported=true;
                if (cargo==0) view.full=false;
                view.cargoRevision++;
                if (purchaseSent) { purchaseSent=false; intent=0; dismiss(e); }
            }
        }
        if (!cargoReported && tradeResponse && (purchaseSent || isBuying()) && TradeSignals.bought(s)) {
            view.cargo=Math.max(0,view.cargo)+1; view.cargoRevision++; for (int i=0;i<TradeConfig.ANIMALS.length;i++) if (beast!=null && TradeSignals.animal(beast.name,i)) view.full=view.cargo>=TradeConfig.MAX_CARGO[i]; if (view.full) { purchaseSent=false; intent=0; menu=null; shop=null; } dismiss(e);
        }
        if (tradeResponse && saleSent && TradeSignals.sold(s)) {
            long amount=TradeEarnings.amount(e.text);
            if (amount>=0) saleAmount=amount;
            saleAcknowledged=true; dismiss(e);
        }
        if (e.type==2) {
            if (view.dead && e.choices!=null) {
                for (int i=0;i<e.choices.size();i++) {
                    Object obj=e.choices.elementAt(i);
                    if (obj instanceof iCommand) {
                        iCommand cmd=(iCommand)obj;
                        String cap=TradeStock.normalize(cmd.caption!=null ? cmd.caption : "");
                        if (canUseReviveItem(cap)) {
                            GlobalService.gI().Dynamic_Menu((short)e.id,(byte)e.menu,(byte)i);
                            if (GameCanvas.menu2!=null) {
                                GameCanvas.menu2.menuSelectedItem=i;
                                GameCanvas.menu2.isShowMenu=false;
                            }
                            if (GameCanvas.currentDialog!=null) GameCanvas.end_Dialog();
                            return;
                        }
                    }
                }
            }
            if (view.dead) { resurrectVillage(); return; }
            if (e.id==-56 && isBuying() && GameScreen.ObjFocus!=beast) { if (GameCanvas.menu2!=null) GameCanvas.menu2.isShowMenu=false; return; }
            if (e.id==-56 && cargoRequestAnimal>=0 && e.map==cargoRequestMap && e.choices!=null) {
                for (int i=0;i<e.choices.size();i++) {
                    String label=TradeStock.normalize(((iCommand)e.choices.elementAt(i)).caption);
                    if ((label.indexOf("hanh trang")>=0 || label.indexOf("hang")>=0 || label.indexOf("thong tin")>=0)
                            && !TradeSignals.sell(label) && !TradeSignals.recall(label)
                            && label.indexOf("huy")<0 && label.indexOf("bo hang")<0
                            && !TradeSignals.destroysTrip(label)) {
                        GlobalService.gI().Dynamic_Menu((short)-56,(byte)e.menu,(byte)i);
                        GameCanvas.menu2.isShowMenu=false; return;
                    }
                }
            }
            if (e.id==-56) {
                if (intent==RECALL) {
                    menu=e.choices; menuId=e.menu; menuNpc=e.id; menuAt=now;
                }
            } else {
                boolean expected=buyer!=null && e.id==buyer.getIDnpc() && (intent==BUY || intent==SUMMON)
                        || seller!=null && e.id==seller.getIDnpc() && intent==SELL;
                if (expected) {
                    menu=e.choices; menuId=e.menu; menuNpc=e.id; menuAt=now;
                    if (!purchaseSent) engine.onActionProgress(now);
                }
            }
        }
        if (view.dead && e.type==3 && e.dialog==GameCanvas.currentDialog && e.choices!=null) {
            if (!canConfirmRevive(e.text)) { resurrectVillage(); return; }
            if (canConfirmRevive(e.text)) {
                for (int i=0;i<e.choices.size();i++) {
                    iCommand c=(iCommand)e.choices.elementAt(i);
                    if (c!=null && TradeSignals.isConfirmButton(c.caption) && canConfirmRevive(e.text+" "+c.caption)) {
                        c.perform();
                        if (e.dialog==GameCanvas.currentDialog) GameCanvas.end_Dialog();
                        break;
                    }
                }
            }
        }
        if (e.type==3 && e.dialog==GameCanvas.currentDialog && e.choices!=null) {
            if (checkAndConfirmSaleDialog(e.dialog, e.text, e.choices, now)) return;
        }
        if (tradeResponse && e.type==3 && e.dialog==GameCanvas.currentDialog && e.choices!=null
                && !view.dead && engine.state!=TradeEngine.PAUSED && !TradeConfig.tradingClosed(now)) {
            if (TradeSignals.destroysTrip(e.text)
                    && !(intent==RECALL && engine.state==TradeEngine.WAIT_RECALL)) {
                // Replacement must preserve this trip. Only the explicit abandon
                // path (empty animal / failed pickup timeout) may cancel it.
                GameCanvas.end_Dialog();
                detail="Không hủy chuyến đang có hàng khi đổi thú";
                return;
            }
            boolean confirm=intent==SELL && saleSent && TradeSignals.sell(s)
                    || intent==RECALL && recalled && (TradeSignals.recall(s) || TradeSignals.animalUseConfirmation(s)
                        || TradeSignals.replaceAnimalConfirmation(e.text))
                    || intent==SUMMON && summoned && (TradeSignals.animal(s,config.animal) || TradeSignals.animal(s,0) || TradeSignals.animal(s,1) || TradeSignals.animal(s,2)
                        || TradeSignals.animalUseConfirmation(s) || (beast==null && TradeSignals.replaceAnimalConfirmation(e.text)))
                    || intent==BUY && (purchaseSent || itemPurchaseSent) && (TradeSignals.gem(s,argument) || s.indexOf("mua")>=0 || s.indexOf("da quy")>=0 || s.indexOf("hang")>=0)
                    || TradeSignals.startTradeConfirmation(e.text)
                        && (intent==SUMMON && summoned || intent==BUY && buyer!=null);
            if (confirm && !s.equals(lastConfirmedText)) {
                for (int i=0;i<e.choices.size();i++) {
                    iCommand c=(iCommand)e.choices.elementAt(i); if (c==null) continue;
                    if (TradeSignals.isConfirmButton(c.caption)) {
                        lastConfirmedText=s; sentAt=now; engine.onActionProgress(now);
                        c.perform(); detail="Đã xác nhận; chờ phản hồi giao dịch";
                        if (e.dialog==GameCanvas.currentDialog) GameCanvas.end_Dialog();
                        break;
                    }
                }
            }
        }
    }
    private boolean teleportEvent(Event e,long now) {
        if (teleportTarget==null || now>=teleportUntil || e.map!=GameCanvas.loadmap.idMap) return false;
        String tNorm=TradeStock.normalize(teleportTarget);
        String intermediate=null;
        if (tNorm.indexOf("dia nguc")>=0) intermediate="lang soi trang";
        else if (tNorm.indexOf("cua bien")>=0) intermediate="thanh pho cang";
        else if (tNorm.indexOf("cheo leo")>=0) intermediate="thanh pho kho bau";
        else if (tNorm.indexOf("ky bi")>=0) intermediate="lang soi trang";

        if (e.type==2 && e.choices!=null) {
            int chosen=-1;
            for (int i=0;i<e.choices.size();i++) {
                String label=TradeStock.normalize(((iCommand)e.choices.elementAt(i)).caption);
                if (Navigator.same(label,teleportTarget) || (intermediate!=null && label.indexOf(intermediate)>=0)) {
                    chosen=i; break;
                }
            }
            if (chosen>=0) {
                GlobalService.gI().Dynamic_Menu((short)e.id,(byte)e.menu,(byte)chosen);
                GameCanvas.menu2.isShowMenu=false; teleportTarget=null; return true;
            } else {
                GameCanvas.menu2.isShowMenu=false; teleportTarget=null; return true;
            }
        }
        if (e.type==3 && e.sequence==dialogSequence && e.dialog==GameCanvas.currentDialog && e.text!=null && e.choices!=null) {
            String text=TradeStock.normalize(e.text);
            if (text.indexOf("dich chuyen")>=0 && (text.indexOf(tNorm)>=0 || (intermediate!=null && text.indexOf(intermediate)>=0))) {
                for (int i=0;i<e.choices.size();i++) {
                    iCommand command=(iCommand)e.choices.elementAt(i);
                    if (command!=null && TradeSignals.isConfirmButton(command.caption)) { command.perform(); teleportTarget=null; return true; }
                }
            }
        }
        return false;
    }
    private boolean checkAndConfirmSaleDialog(Object dialog, String text, mVector choices, long now) {
        if (dialog==null || choices==null || view.dead || engine.state==TradeEngine.PAUSED || TradeConfig.tradingClosed(now)) return false;
        String s=TradeStock.normalize(text!=null ? text : "");
        boolean inSellerContext=intent==SELL && (engine.state==TradeEngine.WAIT_SALE || engine.state==TradeEngine.SELL)
                && seller!=null && distance(seller)<=64 && Navigator.same(GameCanvas.loadmap.nameMap, TradeConfig.SELLER_MAP);
        boolean isSalePrompt=TradeSignals.sell(s);
        if (inSellerContext && isSalePrompt) {
            long amount=TradeEarnings.amount(text);
            if (amount>0) saleAmount=amount;
            for (int i=0;i<choices.size();i++) {
                iCommand c=(iCommand)choices.elementAt(i);
                if (c!=null && TradeSignals.isConfirmButton(c.caption)) {
                    saleSent=true;
                    saleConfirmedAt=now;
                    sentAt=now;
                    engine.onActionProgress(now);
                    c.perform();
                    detail="Đã xác nhận bán "+(saleAmount>0 ? (saleAmount+" vàng") : "hàng");
                    if (dialog==GameCanvas.currentDialog) GameCanvas.end_Dialog();
                    activeDialog=null; activeDialogText=null; activeDialogChoices=null;
                    return true;
                }
            }
        }
        return false;
    }
    private void dismiss(Event e) {
        if (e.dialog!=null && e.dialog==GameCanvas.currentDialog) GameCanvas.end_Dialog();
    }
    private void pump(long now) {
        if (intent==0 || now<nextAction) return;
        if ((intent==QUERY || intent==SUMMON && engine.state!=TradeEngine.WAIT_NEW && engine.state!=TradeEngine.WAIT_REPLACE || intent==BUY) && view.market!=engine.market) {
            clearIntent(); return;
        }
        if (intent==SELL && !Navigator.same(GameCanvas.loadmap.nameMap,TradeConfig.SELLER_MAP)) {
            clearIntent(); return;
        }
        nextAction=now+100;
        if (intent==QUERY) {
            if (buyer==null) buyer=findNamed("dylan");
            if (buyer==null) buyer=findNamed("thuong nhan");
            MainObject npc=buyer!=null?buyer:scanNpc();
            if (npc==null) { detail="Đang tìm NPC tại bãi mua"; return; }
            if (distance(npc)>64) { navigation.walk(npc.x,npc.y,2,null,now); return; }
            if (queriedNpc==npc.getIDnpc() && now-sentAt<5000) return;
            if (queriedNpc==npc.getIDnpc() && buyer==null) { scanIndex++; queriedNpc=-10000; return; }
            if (intent!=BUY) GameScreen.ObjFocus=npc;
            queriedNpc=npc.getIDnpc(); sentRequest=request; sentAt=now;
            GlobalService.gI().chat_npc((byte)npc.getIDnpc()); return;
        }
        if (intent==RECALL && (engine.state==TradeEngine.WAIT_REPLACE || engine.state==TradeEngine.WAIT_RECALL) && !recalled) {
            Item foundItem=null;
            for (int i=0;i<Item.VecInvetoryPlayer.size();i++) {
                Item item=(Item)Item.VecInvetoryPlayer.elementAt(i);
                if (item!=null && TradeSignals.animal(item.itemName,argument)) { foundItem=item; break; }
            }
            if (foundItem==null) {
                for (int i=0;i<Item.VecInvetoryPlayer.size();i++) {
                    Item item=(Item)Item.VecInvetoryPlayer.elementAt(i);
                    if (item!=null && (TradeSignals.animal(item.itemName,0) || TradeSignals.animal(item.itemName,1) || TradeSignals.animal(item.itemName,2))) {
                        foundItem=item; break;
                    }
                }
            }
            if (foundItem!=null) {
                recalled=true; sentAt=now; GlobalService.gI().Use_Potion((short)foundItem.Id); return;
            }
            detail="C\u1ea7n v\u1eadt ph\u1ea9m th\u00fa bu\u00f4n \u0111\u1ec3 h\u1ee7y th\u00fa"; return;
        }
        if (intent==SUMMON) {
            // Using the same item again can cancel a server-side animal whose
            // spawn packet is delayed. Wait for acknowledgement or timeout.
            if (!summoned) {
                Item foundItem=null;
                for (int i=0;i<Item.VecInvetoryPlayer.size();i++) {
                    Item item=(Item)Item.VecInvetoryPlayer.elementAt(i);
                    if (item!=null && TradeSignals.animal(item.itemName,argument)) { foundItem=item; break; }
                }
                if (foundItem==null) {
                    for (int i=0;i<Item.VecInvetoryPlayer.size();i++) {
                        Item item=(Item)Item.VecInvetoryPlayer.elementAt(i);
                        if (item!=null && (TradeSignals.animal(item.itemName,0) || TradeSignals.animal(item.itemName,1) || TradeSignals.animal(item.itemName,2))) {
                            foundItem=item; break;
                        }
                    }
                }
                if (foundItem!=null) {
                    if (GameScreen.player!=null && GameScreen.player.typeMount!=-1) {
                        GlobalService.gI().useMount((byte)-1);
                    }
                    summoned=true; sentAt=now; GlobalService.gI().Use_Potion((short)foundItem.Id); return;
                }
                detail="C\u1ea7n v\u1eadt ph\u1ea9m th\u00fa bu\u00f4n trong t\u00fai \u0111\u1ed3";
            }
        }
        if (intent==BUY && purchaseSent && shop==null) {
            if (now-sentAt>=15000) { purchaseSent=false; sentAt=now; }
            return;
        }
        if (intent==SELL && saleSent && menu==null) {
            if (GameCanvas.currentDialog!=null) return;
            if (now-sentAt>=2500) { saleSent=false; sentAt=0; }
            else return;
        }
        if (intent==RECALL && recalled || intent==SUMMON && summoned) return;
        if (intent==BUY && shop!=null) {
            if (lastPurchase>0 && now-lastPurchase<TradeConfig.BUY_INTERVAL_MS) { nextAction=lastPurchase+TradeConfig.BUY_INTERVAL_MS; return; }
            for (int i=0;i<shop.size();i++) {
                Item item=(Item)shop.elementAt(i);
                if (item!=null && TradeSignals.gem(item.itemName,argument)) {
                    // Repeated sends are not server progress. Otherwise the
                    // purchase deadline slides forever when replies are lost.
                    if (!itemPurchaseSent) engine.onActionProgress(now);
                    purchaseSent=true; itemPurchaseSent=true; sentAt=now; lastPurchase=now;
                    GlobalService.gI().buy_item((byte)shopId,(short)item.Id,(short)1);
                    return;
                }
            }
        }
        if (menu!=null && now-menuAt<=15000) {
            int chosen=-1, generic=-1;
            for (int i=0;i<menu.size();i++) {
                String label=TradeStock.normalize(((iCommand)menu.elementAt(i)).caption);
                if (TradeSignals.destroysTrip(label)
                        && !(intent==RECALL && engine.state==TradeEngine.WAIT_RECALL)) continue;
                if (intent==SUMMON && TradeSignals.animal(label,argument) || intent==BUY && TradeSignals.gem(label,argument)
                        || intent==SELL && TradeSignals.sell(label) || intent==RECALL && TradeSignals.recall(label)) { chosen=i; break; }
                if ((intent==BUY || intent==SELL) && label.equals("mua ban")
                        || intent==BUY && (label.equals("mua hang") || label.equals("mua da quy"))
                        || label.equals("buon ban") || label.equals("di buon")) generic=i;
            }
            if (chosen<0) chosen=generic;
            if (chosen>=0) {
                String label=TradeStock.normalize(((iCommand)menu.elementAt(chosen)).caption);
                if (intent==BUY && TradeSignals.gem(label,argument)) purchaseSent=true;
                if (intent==SELL) saleSent=true;
                if (intent==RECALL && TradeSignals.recall(label)) recalled=true;
                if (intent==SUMMON && TradeSignals.animal(label,argument)) summoned=true;
                sentAt=now; engine.onActionProgress(now);
                GlobalService.gI().Dynamic_Menu((short)menuNpc,(byte)menuId,(byte)chosen);
                if (GameCanvas.menu2!=null) GameCanvas.menu2.isShowMenu=false;
                menu=null; return;
            }
            detail="Chưa nhận diện lựa chọn phù hợp trong menu NPC"; return;
        }
        if (now-sentAt<1200) return;
        if (intent==RECALL) { info(now); sentAt=now; return; }
        if (intent==BUY && buyer==null) buyer=findNamed("dylan");
        if (intent==BUY && buyer==null) buyer=findNamed("thuong nhan");
        MainObject npc=intent==SELL?seller:buyer;
        if (npc==null) { detail="Chưa có NPC cho thao tác hiện tại"; return; }
        if (distance(npc)>64) { navigation.walk(npc.x,npc.y,2,beast,now); return; }
        if (intent!=BUY) GameScreen.ObjFocus=npc;
        else if (GameScreen.ObjFocus==null) GameScreen.ObjFocus=npc;
        sentAt=now; GlobalService.gI().getlist_from_npc((byte)npc.getIDnpc());
    }
}
