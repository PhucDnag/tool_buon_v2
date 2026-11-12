import autotrade.*;
import CLib.mVector;
import GameObjects.*;
import GameScreen.GameScreen;
import Main.GameCanvas;
import Model.CRes;
import Thread_More.LoadMap;
import InterfaceComponents.iCommand;
import netcommand.global.GlobalService;

/** Cross-state regressions found in the full automatic-trade audit. */
public final class AutoTradeLifecycleTest {
    static int checks;
    static void check(boolean ok,String message) { checks++; if (!ok) throw new AssertionError(message); }
    static mVector buttons(String first,String second) {
        mVector result=new mVector(); result.addElement(new iCommand(first));
        if (second!=null) result.addElement(new iCommand(second)); return result;
    }
    static MainObject animal(String name) {
        MainObject a=new MainObject(); a.ID=70; a.name=name; a.x=200; a.y=200; return a;
    }
    static LiveTrade live(TradeConfig config,String map) {
        CRes.clearAllRMS();
        GameScreen.player=new Player(); GameScreen.player.ID=100; GameScreen.player.x=200; GameScreen.player.y=200;
        GameScreen.ObjFocus=null; GameScreen.Vecplayers.removeAllElements();
        Item.VecInvetoryPlayer.removeAllElements(); LoadMap.vecPointChange.removeAllElements();
        GameCanvas.currentDialog=null; GameCanvas.menu2.isShowMenu=false;
        GameCanvas.loadmap.idMap=77; GameCanvas.loadmap.nameMap=map;
        GameCanvas.loadmap.mapW=20; GameCanvas.loadmap.mapH=20; GameCanvas.loadmap.mapType=new int[400];
        LiveTrade result=new LiveTrade(config,new MapGraph()); result.toggle(1000); return result;
    }
    static LiveTrade sale(MainObject beast) {
        LiveTrade live=live(new TradeConfig(),TradeConfig.SELLER_MAP);
        MainObject seller=animal("Graham"); seller.ID=7; seller.typeObject=2; GameScreen.Vecplayers.addElement(seller);
        live.engine.state=TradeEngine.TO_SELL; live.view.cargo=6; live.view.full=true;
        live.tick(beast,2000); live.tick(beast,3000);
        live.menu(buttons("Bán hàng",null),1,7,""); live.tick(beast,4000);
        return live;
    }
    static final class Port implements TradeEngine.Port {
        int villages,spots,buys,queries; boolean lost;
        public boolean ready(){return true;} public void stopMovement(){}
        public void goMarket(int i){} public void goSeller(){} public void queryStock(int i,int n){queries++;}
        public void summon(int i){} public void buy(int i){buys++;} public void sell(){} public void recall(){}
        public void recoverPreviousMap(){} public void followAnimal(){}
        public void resurrect(){spots++;} public void resurrectVillage(){villages++;}
        public void resetLostTrip(){lost=false;} public boolean hasLostTrip(){return lost;}
        public void notifyUser(String s){}
    }
    public static void main(String[] args) {
        // A persisted location with unknown/empty cargo must not renew the
        // search forever and prevent the next summon/purchase cycle.
        for (int savedCargo=-1;savedCargo<=0;savedCargo++) {
            TradeConfig recoveryConfig=new TradeConfig(); recoveryConfig.animal=2;
            LiveTrade recovered=live(recoveryConfig,"Hố Tử Thần");
            recovered.toggle(1100);
            TradeRecovery.save(100,"Hố Tử Thần",200,200,savedCargo,0,0,2,1200);
            recovered.toggle(1300); recovered.tick(null,2000);
            check(recovered.engine.state==TradeEngine.RECOVER && recovered.view.inRecoveryMap,
                "Saved location starts a bounded search in the last animal map");
            recovered.tick(null,61000);
            check(recovered.engine.state==TradeEngine.RECOVER,"Allow full sixty-second search before restarting");
            recovered.tick(null,62000);
            check(recovered.engine.state==TradeEngine.TO_MARKET && !recovered.hasLostTrip()
                    && TradeRecovery.load(100,62000)==null,
                "Unknown/empty saved cargo expires and clears all stale recovery evidence");
            recovered.view.connected=false; recovered.engine.tick(recovered.view,62100);
            recovered.tick(null,63000);
            check(recovered.engine.state==TradeEngine.TO_MARKET,
                "Reconnect cannot restore expired recovery state");
            GameCanvas.loadmap.idMap=TradeConfig.MARKET_MAP_IDS[0];
            GameCanvas.loadmap.nameMap=TradeConfig.MARKETS[0];
            MainObject merchant=animal("Dylan"); merchant.ID=5; merchant.typeObject=2;
            GameScreen.Vecplayers.addElement(merchant);
            Item item=new Item(); item.Id=242; item.ItemCatagory=4;
            item.numPotion=1; item.itemName="Bò vàng"; Item.VecInvetoryPlayer.addElement(item);
            recovered.tick(null,64000);
            merchant.strChatPopup="Ta chỉ còn 3 viên kim cương, 3 viên riuby, 3 viên saphia";
            recovered.chat(merchant); recovered.tick(null,65000);
            check(recovered.engine.state==TradeEngine.SUMMON,"Expired recovery resumes stock check and summon");
            MainObject cow=animal("Bò vàng");
            recovered.tick(cow,66000); recovered.tick(cow,67000);
            recovered.menu(buttons("Mua Kim Cương",null),2,5,""); recovered.tick(cow,68000);
            mVector goods=new mVector(); Item gem=new Item();
            gem.Id=81; gem.ItemCatagory=4; gem.itemName="Kim Cương"; goods.addElement(gem);
            int purchases=GlobalService.gI().buyCalls;
            recovered.shop(goods,0,0); recovered.tick(cow,69000);
            check(GlobalService.gI().buyCalls==purchases+1,
                "Stale recovery regression reaches an actual gem purchase request");
        }
        {
            TradeConfig recoveryConfig=new TradeConfig();
            LiveTrade expired=live(recoveryConfig,"Hố Tử Thần");
            expired.tick(animal("Lừa vàng"),2000);
            expired.engine.state=TradeEngine.TO_MARKET; expired.toggle(2100);
            TradeRecovery.clear(); expired.toggle(2200);
            check(!expired.hasLostTrip(),"Fresh start cannot reuse old in-memory location without a recovery record");

            LiveTrade visible=live(recoveryConfig,TradeConfig.MARKETS[0]);
            MainObject cow=animal("Lừa vàng"); visible.tick(cow,2000);
            visible.engine.state=TradeEngine.TO_MARKET; visible.toggle(2100);
            TradeRecovery.save(100,TradeConfig.MARKETS[0],200,200,2,0,0,0,2200);
            visible.toggle(2300); visible.tick(cow,2400);
            check(visible.engine.state==TradeEngine.STOCK,
                "Restored visible partial load at buying market continues loading instead of escorting");
        }
        Port missingPort=new Port();
        TradeEngine missingEngine=new TradeEngine(new TradeConfig(),missingPort);
        TradeEngine.View missingView=new TradeEngine.View(); missingView.market=0;
        missingEngine.start(1000); missingEngine.state=TradeEngine.BUY;
        missingEngine.tick(missingView,2000);
        check(missingPort.buys==0 && missingEngine.state==TradeEngine.BUY,
            "Missing animal cannot trigger a purchase during map-loading grace");
        missingEngine.tick(missingView,4100);
        check(missingPort.buys==0 && missingEngine.state==TradeEngine.RECOVER,
            "Buying without an animal recovers even without adapter missing flag");
        missingEngine.start(5000); missingView.animal=true; missingEngine.state=TradeEngine.BUY;
        missingEngine.tick(missingView,6000);
        check(missingPort.buys==1,"Visible animal can begin purchase");
        missingView.animal=false; missingEngine.tick(missingView,17000);
        check(missingPort.buys==1,"Expired buy deadline cannot retry after animal disappears");
        GlobalService net=GlobalService.gI();
        {
            TradeConfig config=new TradeConfig(); config.animal=2;
            LiveTrade noItem=live(config,TradeConfig.MARKETS[0]);
            MainObject merchant=animal("Dylan"); merchant.ID=5; merchant.typeObject=2;
            GameScreen.Vecplayers.addElement(merchant);
            noItem.tick(null,2000);
            merchant.strChatPopup="Ta chỉ còn 3 viên kim cương, 3 viên riuby, 3 viên saphia";
            noItem.chat(merchant);
            int npcCalls=net.shopCalls,uses=net.potionCalls;
            noItem.tick(null,3000); noItem.tick(null,16000); noItem.tick(null,20000);
            check(noItem.engine.state==TradeEngine.SUMMON && net.shopCalls==npcCalls && net.potionCalls==uses,
                "Missing animal item waits for inventory without asking merchant or looping resync");
            check(noItem.engine.status.indexOf("vật phẩm")>=0,"HUD reports actual missing animal item blocker");
            noItem.serverText("Ta không thấy con vật buôn của ngươi"); noItem.tick(null,21000);
            check(noItem.intent==LiveTrade.SUMMON,"Unrelated NPC response is not an item-use rejection when no item was used");
            Item item=new Item(); item.Id=242; item.ItemCatagory=4; item.numPotion=1; item.itemName="Bò vàng";
            Item.VecInvetoryPlayer.addElement(item); noItem.tick(null,22000);
            check(net.potionCalls==uses+1 && net.lastPotion==242,"Inventory arrival resumes pending summon automatically");

            // Real log: low HP at map 42, a portal gap at map 22, same animal
            // appears there before the replacement confirmation/removal.
            LiveTrade portal=live(config,"Đèo Hoang Sơ"); GameCanvas.loadmap.idMap=42;
            Item.VecInvetoryPlayer.addElement(item);
            MainObject old=animal("Bò vàng"); old.ID=8468; old.hp=189600; old.maxHp=400000;
            portal.engine.state=TradeEngine.TO_SELL; portal.view.cargo=24; portal.view.full=true;
            portal.tick(old,2000); uses=net.potionCalls;
            GameCanvas.loadmap.idMap=22; GameCanvas.loadmap.nameMap="Vực Lún";
            old.isRemove=true; portal.tick(null,2200);
            check(portal.engine.state==TradeEngine.WAIT_REPLACE && portal.view.cargo==24 && net.potionCalls==uses,
                "Portal loading gap cannot be mistaken for replacement removal or use item twice");
            old.isRemove=false; portal.tick(old,2400);
            check(portal.engine.state==TradeEngine.WAIT_REPLACE && portal.view.cargo==24,
                "Same animal arriving on new map preserves replacement phase and loaded cargo");
            Object prompt=new Object(); GameCanvas.currentDialog=prompt;
            portal.dialog(prompt,"Con vật buôn của bạn đang ở map Vực Lún. Bạn có muốn hủy con thú cũ để tạo con thú mới không ?",buttons("Ok",null));
            portal.tick(old,2600);
            old.hp=0; old.isStop=true; portal.tick(null,2800);
            check(portal.engine.state==TradeEngine.WAIT_NEW && net.potionCalls==uses,
                "Confirmed removal allows spawn grace without redundant item use");
            portal.tick(null,5900);
            check(net.potionCalls==uses+1,"Missing replacement spawns after grace even when old animal crossed a portal");
            MainObject replacement=animal("Bò vàng"); replacement.ID=8470; replacement.hp=400000; replacement.maxHp=400000;
            portal.tick(replacement,6100);
            portal.animalCargo("Bò vàng",24); portal.tick(replacement,7100);
            check(portal.engine.state==TradeEngine.TO_SELL && portal.view.cargo==24,
                "Portal replacement resumes escort after confirmed pickup");
        }
        {
        // Rejected item use must not leave WAIT_NEW waiting for a spawn that
        // the server explicitly said did not happen.
        TradeConfig cowConfig=new TradeConfig(); cowConfig.animal=2;
        LiveTrade rejected=live(cowConfig,TradeConfig.MARKETS[0]);
        Item cowItem=new Item(); cowItem.Id=242; cowItem.ItemCatagory=4;
        cowItem.numPotion=1; cowItem.itemName="Bò vàng"; Item.VecInvetoryPlayer.addElement(cowItem);
        rejected.tick(null,2000); rejected.engine.state=TradeEngine.WAIT_NEW;
        rejected.summon(2); rejected.tick(null,2100);
        int uses=net.potionCalls;
        Object rejectDialog=new Object(); GameCanvas.currentDialog=rejectDialog;
        rejected.dialog(rejectDialog,"Ta không thấy con vật buôn của ngươi",buttons("Ok",null));
        rejected.tick(null,2200);
        check(GameCanvas.currentDialog==null && rejected.intent==0 && rejected.engine.state==TradeEngine.WAIT_NEW,
            "Item rejection closes error and retains replacement pickup phase");
        rejected.tick(null,3600);
        check(net.potionCalls==uses,"Rejected item retry is paced");
        rejected.tick(null,3800);
        check(net.potionCalls==uses+1 && net.lastPotion==242,"Explicit rejection retries cow item without querying nonexistent animal");
        rejected.tick(null,8000);
        check(net.potionCalls==uses+1,"Silent delayed spawn cannot trigger repeated item use");
        MainObject newCow=animal("Bò vàng"); newCow.ID=243;
        MainObject spilled=animal("Saphia"); spilled.ID=244; spilled.typeObject=4;
        GameScreen.Vecplayers.addElement(spilled);
        int pickups=net.getItemMapCalls;
        rejected.tick(newCow,9000);
        check(net.getItemMapCalls>pickups && net.potionCalls==uses+1,
            "Replacement cow picks dropped gems without being used/cancelled again");

        // Death and drop packets may arrive on different ticks, outside a market.
        LiveTrade died=live(cowConfig,"Vực Lún");
        Item.VecInvetoryPlayer.addElement(cowItem);
        MainObject dyingCow=animal("Bò vàng");
        died.engine.state=TradeEngine.TO_SELL; died.view.cargo=24; died.view.full=true;
        died.tick(dyingCow,2000); dyingCow.hp=0;
        died.tick(null,2100);
        GameScreen.Vecplayers.addElement(spilled); uses=net.potionCalls;
        died.tick(null,2500);
        check(died.engine.state==TradeEngine.WAIT_NEW && net.potionCalls==uses+1 && net.lastPotion==242,
            "Observed dead cow and delayed drops summon replacement outside market");
        check(died.view.cargo==-1 && !died.view.full,"Dropped full load cannot masquerade as cargo on new cow");
        uses=net.potionCalls;
        died.serverText("Ta không thấy con vật buôn của ngươi"); died.tick(null,2600); died.tick(null,4200);
        died.serverText("Ta không thấy con vật buôn của ngươi"); died.tick(null,4300); died.tick(null,5900);
        died.serverText("Ta không thấy con vật buôn của ngươi"); died.tick(null,6000); died.tick(null,8000);
        check(net.potionCalls==uses+2,"Repeated explicit rejection allows only two paced item retries");
        LiveTrade vanished=live(cowConfig,"Vực Lún"); Item.VecInvetoryPlayer.addElement(cowItem);
        MainObject healthyCow=animal("Bò vàng");
        vanished.engine.state=TradeEngine.TO_SELL; vanished.view.cargo=24; vanished.view.full=true;
        vanished.tick(healthyCow,2000); GameScreen.Vecplayers.addElement(spilled); uses=net.potionCalls;
        vanished.tick(null,2100); vanished.tick(null,4500);
        check(net.potionCalls==uses && vanished.engine.state==TradeEngine.RECOVER,
            "Other ground gems plus a map-loading gap cannot authorize replacing an unconfirmed dead cow");
        }
        {
            TradeConfig config=new TradeConfig(); config.animal=2;
            LiveTrade replacementFlow=live(config,"Vực Lún");
            Item item=new Item(); item.Id=242; item.ItemCatagory=4; item.numPotion=1; item.itemName="Bò vàng";
            Item.VecInvetoryPlayer.addElement(item);
            MainObject oldCow=animal("Bò vàng"); oldCow.ID=8418; oldCow.hp=172035; oldCow.maxHp=400000;
            replacementFlow.view.cargo=24; replacementFlow.view.full=true; replacementFlow.engine.state=TradeEngine.TO_SELL;
            replacementFlow.tick(oldCow,2000);
            Object prompt=new Object(); GameCanvas.currentDialog=prompt;
            replacementFlow.dialog(prompt,"Con vật buôn của bạn đang ở map Vực Lún. Bạn có muốn hủy con thú cũ để tạo con thú mới không ?",buttons("Ok",null));
            replacementFlow.tick(oldCow,2100);
            int itemUses=net.potionCalls;
            oldCow.hp=0; oldCow.isStop=true; replacementFlow.tick(null,2300);
            check(replacementFlow.engine.state==TradeEngine.WAIT_NEW && net.potionCalls==itemUses,
                "Replacement allows a short spawn grace period after old cow disappears");
            replacementFlow.tick(null,5200);
            check(net.potionCalls==itemUses,"Do not reuse item during initial spawn grace");
            replacementFlow.tick(null,5400);
            check(net.potionCalls==itemUses+1 && net.lastPotion==242 && replacementFlow.intent==LiveTrade.SUMMON,
                "Log regression: absent replacement must trigger cow item without manual input");
            itemUses=net.potionCalls;
            replacementFlow.tick(null,10000); replacementFlow.tick(null,15000);
            check(net.potionCalls==itemUses,"Pending replacement summon is not repeatedly toggled");
            MainObject newCow=animal("Bò vàng"); newCow.ID=8421; newCow.hp=400000; newCow.maxHp=400000;
            replacementFlow.tick(newCow,20000);
            check(replacementFlow.view.cargo==-1 && !replacementFlow.view.full && replacementFlow.engine.state==TradeEngine.WAIT_NEW,
                "Log regression: new cow must not inherit old full flag and escort unknown cargo");
            replacementFlow.tick(newCow,43000);
            check(replacementFlow.engine.state==TradeEngine.WAIT_NEW && net.potionCalls==itemUses,
                "Unknown replacement inventory still waits after timeout without cancellation");
            MainObject gem=animal("Saphia"); gem.typeObject=4; gem.ID=8422; GameScreen.Vecplayers.addElement(gem);
            int pickups=net.getItemMapCalls; replacementFlow.tick(newCow,44000);
            check(net.getItemMapCalls>pickups,"Unverified replacement continues collecting visible cargo");
            gem.isRemove=true;
            check(replacementFlow.animalCargo("Bò vàng",24),"Accept authoritative new cow inventory");
            replacementFlow.tick(newCow,47000);
            check(replacementFlow.engine.state==TradeEngine.TO_SELL && replacementFlow.view.cargo==24,
                "Only new cow inventory confirmation releases replacement escort");
        }
        MainObject beast=animal("Lừa vàng");
        String missingText="Ta không thấy con vật buôn của ngươi";
        check(TradeSignals.animalNotFound(missingText),"Recognize exact screenshot rejection");
        check(TradeSignals.animalNotFound("Ta không thấy con vật\nbuôn của ngươi"),"Recognize wrapped rejection");
        check(!TradeSignals.animalNotFound("Ta không thấy món đồ của ngươi"),"Do not classify unrelated NPC error as missing animal");
        LiveTrade missingSale=sale(beast);
        Object missingDialog=new Object(); GameCanvas.currentDialog=missingDialog;
        missingSale.dialog(missingDialog,missingText,buttons("Ok",null)); missingSale.tick(null,4100);
        check(GameCanvas.currentDialog==null && missingSale.engine.state==TradeEngine.RECOVER,
            "Missing animal sale rejection closes popup and immediately searches previous location");
        check(missingSale.view.cargo==6 && missingSale.earnings.gold==0 && missingSale.intent==0,
            "Rejection preserves cargo and clears sale action without counting income");
        missingSale.tick(null,64099);
        check(missingSale.engine.state==TradeEngine.RECOVER && missingSale.earnings.failedTrips==0,
            "Search preserves the trip until the 60-second local budget expires");
        missingSale.tick(null,64100);
        check(missingSale.engine.state==TradeEngine.TO_MARKET && GameCanvas.currentDialog==null && missingSale.earnings.failedTrips==1,
            "Local search ends after 60 seconds and counts the abandoned loaded trip once");
        missingDialog=new Object(); GameCanvas.currentDialog=missingDialog;
        missingSale.dialog(missingDialog,missingText,buttons("Ok",null)); missingSale.tick(null,65100);
        check(GameCanvas.currentDialog==null && missingSale.engine.state!=TradeEngine.RECOVER && missingSale.earnings.failedTrips==1,
            "Late informational rejection cannot restart expired recovery");
        missingSale=sale(beast); missingDialog=new Object(); GameCanvas.currentDialog=missingDialog;
        missingSale.dialog(missingDialog,missingText,buttons("Ok",null)); missingSale.tick(beast,4100);
        check(GameCanvas.currentDialog==null && missingSale.engine.state==TradeEngine.RESYNC,
            "Visible but server-rejected animal gets inventory verification rather than repeated sale");
        LiveTrade delayed=live(new TradeConfig(),"Sa Mạc");
        delayed.tick(null,2000); delayed.tick(beast,3000);
        check(delayed.view.cargo==-1 && delayed.engine.state==TradeEngine.TO_SELL,
            "Delayed existing animal keeps unknown cargo and is not recalled as empty");
        LiveTrade live=sale(beast);
        MainObject different=animal("Lừa vàng"); different.ID=beast.ID+1;
        live.tick(different,4100);
        check(live.view.cargo==-1 && !live.view.full && live.engine.state==TradeEngine.RESYNC,
            "Unexpected animal identity cannot inherit old cargo or complete the old sale");
        check(live.earnings.gold==0 && live.earnings.unmeasured==0,
            "Switching animal is never evidence of sale success");
        live=sale(beast);
        live.serverText("Bán hàng thành công");
        live.disconnected();
        check(live.view.cargo==-1 && !live.view.full && live.intent==0,
            "Disconnect invalidates full cargo and outstanding transaction context");
        live.tick(beast,4100);
        check(live.earnings.gold==0 && live.earnings.unmeasured==0,
            "Queued pre-disconnect sale event cannot complete a new session transaction");
        live=sale(beast);
        live.tick(null,4100);
        check(live.engine.state==TradeEngine.WAIT_SALE && live.earnings.gold==0 && live.earnings.unmeasured==0,
            "Disappearance at Graham alone cannot complete a sale or erase the trip");

        live=sale(beast);
        live.view.cargo=0; live.view.full=false;
        live.tick(beast,4100); live.tick(beast,6200);
        check(live.earnings.successfulTrips==0 && live.earnings.unmeasured==0,
            "Lost cargo while waiting for sale is not successful without acknowledgement or coins");
        live.resetLostTrip(); live.resetLostTrip();
        check(live.earnings.failedTrips==1,"Lost cargo abandoned for another trip fails exactly once");

        live=sale(beast); GameScreen.player.coin+=100;
        live.tick(beast,4100);
        check(live.engine.state==TradeEngine.WAIT_SALE,"Unrelated gold gain while cargo remains is not a sale");
        Object dialog=new Object(); GameCanvas.currentDialog=dialog;
        mVector choices=buttons("Hủy","Đồng ý");
        live.dialog(dialog,"Bạn có muốn bán tất cả với giá 960000 Vàng ?",choices); live.tick(beast,4200);
        check(((iCommand)choices.elementAt(0)).calls==0 && ((iCommand)choices.elementAt(1)).calls==1,
            "Sale confirmation chooses affirmative button even when cancel is first");
        live.serverText("Bán hàng thành công"); live.tick(null,4300);
        check(live.engine.state==TradeEngine.TO_MARKET,"Explicit sale acknowledgement completes the trip");

        live=sale(beast);
        live.serverText("Bán hàng thành công"); live.tick(null,4100);
        check(live.engine.state==TradeEngine.WAIT_SALE && live.earnings.unmeasured==0,
            "Wait for wallet packet after success without a quoted price");
        GameScreen.player.coin+=960000; live.tick(null,4200);
        check(live.engine.state==TradeEngine.TO_MARKET && live.earnings.gold==960000,
            "Delayed wallet receipt is counted before leaving seller");
        live.serverText("Bán hàng thành công"); live.tick(null,4300);
        check(live.earnings.gold==960000,"Duplicate success cannot double the receipt");

        // The real client stores visible yellow money in coin, green gems in gold.
        live=sale(beast); GameScreen.player.gold=100;
        live.tick(null,4100);
        check(live.engine.state==TradeEngine.WAIT_SALE && live.earnings.gold==0,
            "Premium currency increase must not confirm a sale or count as receipts");
        GameScreen.player.coin=480000; live.tick(null,4200);
        check(live.earnings.gold==480000 && live.earnings.unmeasured==0,
            "Actual coin balance confirms sale even without a priced dialog");
        live.engine.state=TradeEngine.TO_SELL; live.view.cargo=6; live.view.full=true;
        live.tick(beast,5000); live.tick(beast,6000);
        live.menu(buttons("Bán hàng",null),1,7,""); live.tick(beast,7000);
        GameScreen.player.coin=960000; live.serverText("Bán hàng thành công"); live.tick(null,7100);
        check(live.earnings.gold==960000 && live.earnings.unmeasured==0,
            "Second sale adds only coin gain above the previous nonzero balance");

        live=sale(beast);
        live.serverText("Bán hàng trên Lừa vàng nhận được 12.345 vàng"); live.tick(null,4100);
        check(live.earnings.gold==12345,"Receipt containing animal name reaches manager earnings");

        live=sale(beast);
        live.serverText("Bán hàng thành công"); live.tick(null,4100); live.tick(null,6200);
        check(live.engine.state==TradeEngine.TO_MARKET && live.earnings.unmeasured==1 && live.earnings.gold==0,
            "Missing wallet packet has bounded wait and never invents income");

        live=live(new TradeConfig(),TradeConfig.SELLER_MAP); live.engine.state=TradeEngine.TO_SELL;
        live.view.cargo=6; live.tick(beast,2000);
        dialog=new Object(); GameCanvas.currentDialog=dialog; choices=buttons("Ok",null);
        live.dialog(dialog,"Bạn có muốn bán vật phẩm với giá 100 Vàng ?",choices); live.tick(beast,2200);
        check(((iCommand)choices.elementAt(0)).calls==0,"Being on seller map cannot confirm an unrelated sale");

        live=live(new TradeConfig(),TradeConfig.MARKETS[0]);
        Item item=new Item(); item.Id=50; item.ItemCatagory=4; item.numPotion=1; item.itemName="Lừa vàng";
        Item.VecInvetoryPlayer.addElement(item);
        live.tick(null,2000); live.engine.state=TradeEngine.SUMMON; live.summon(0);
        int uses=net.potionCalls; live.tick(null,2100); live.tick(null,6000); live.tick(null,9000);
        check(net.potionCalls==uses+1,"Delayed spawn never toggles the same summon item repeatedly");

        TradeConfig config=new TradeConfig(); config.animal=2;
        live=live(config,TradeConfig.SELLER_MAP); live.engine.state=TradeEngine.TO_SELL;
        live.view.cargo=-1; live.tick(beast,2000); GameScreen.ObjFocus=beast;
        mVector cargo=new mVector(); for (int i=0;i<6;i++) cargo.addElement(new Item());
        check(live.cargoShop(cargo,13,11,"Lừa vàng"),"Accept requested animal cargo");
        live.tick(beast,2200);
        check(live.view.full,"Observed donkey capacity overrides configured cow capacity");

        live=live(new TradeConfig(),TradeConfig.MARKETS[0]); live.engine.state=TradeEngine.BUY;
        live.tick(beast,2000); live.buy(0);
        mVector shop=new mVector(); item=new Item(); item.Id=81; item.itemName="Kim Cương"; shop.addElement(item);
        live.shop(shop,0,0); live.tick(beast,2100);
        live.serverText("Mua thành công. Số hàng: 2"); live.tick(beast,2200);
        check(live.view.cargo==2,"One message with purchase and cargo count is not counted twice");

        Port port=new Port(); config=new TradeConfig(); TradeEngine engine=new TradeEngine(config,port);
        TradeEngine.View timedSearch=new TradeEngine.View(); timedSearch.cargo=6;
        port.lost=true; engine.resumeRecovery(1000);
        engine.tick(timedSearch,180999);
        check(engine.state==TradeEngine.RECOVER,"Travel search retains trip before the three-minute deadline");
        engine.tick(timedSearch,181000);
        check(engine.state==TradeEngine.TO_MARKET && !port.lost && timedSearch.cargo==0,
            "Travel search ends at three minutes even without reaching the recovery map");
        TradeEngine.View view=new TradeEngine.View(); engine.start(0); engine.resumeRecovery(1000);
        engine.stop(); engine.start(2000); view.connected=false; engine.tick(view,2100);
        view.connected=true; engine.tick(view,2200);
        check(engine.state==TradeEngine.TO_MARKET,"Restart does not inherit stale recovery state on reconnect");

        port=new Port(); engine=new TradeEngine(config,port); engine.start(0);
        long closed=3*3600000L; engine.tick(view,closed); view.dead=true;
        engine.tick(view,closed+1000); engine.tick(view,closed+13000);
        check(port.villages==1 && port.spots==0,"Revive directly in village without items even during trading ban");
        view.dead=false; engine.tick(view,closed+14000);
        check(engine.state==TradeEngine.PAUSED,"Revival during ban does not resume transactions");

        port=new Port(); port.lost=true; engine=new TradeEngine(config,port); engine.start(0);
        view=new TradeEngine.View(); view.market=0; view.animal=true; view.cargo=2; view.hp=100; view.maxHp=100;
        engine.state=TradeEngine.WAIT_BUY; view.dead=true; engine.tick(view,1000); engine.tick(view,13000);
        view.dead=false; view.animal=false; view.animalMissing=true; engine.tick(view,14000);
        check(engine.state==TradeEngine.RECOVER,"Death during purchase preserves trip and searches for animal");
        view.animal=true; view.animalMissing=false; engine.tick(view,15000);
        check(engine.state==TradeEngine.STOCK && port.queries==1,"Reunion rechecks stock instead of stale purchase timeout");

        port=new Port(); engine=new TradeEngine(config,port); engine.start(0);
        view=new TradeEngine.View(); view.animal=true; view.market=0; view.cargo=2;
        engine.state=TradeEngine.BUY; engine.tick(view,1000);
        engine.tick(view,12000); engine.tick(view,23000); engine.tick(view,34000);
        check(engine.state==TradeEngine.RESYNC && port.buys==3,"Lost purchase replies stop repeated mutations and enter resync");
        engine.tick(view,64000); engine.tick(view,65000);
        check(engine.state==TradeEngine.RESYNC && port.buys==3,"Stale cargo cannot authorize another purchase after cooldown");
        view.cargoRevision++; engine.tick(view,66000); engine.tick(view,67000);
        check(engine.state==TradeEngine.STOCK && port.queries==1,"Fresh cargo resumes stock check automatically");

        live=live(new TradeConfig(),TradeConfig.MARKETS[0]); live.tick(beast,2000);
        live.engine.state=TradeEngine.BUY; live.tick(beast,2100); live.buy(0);
        live.shop(shop,0,0); live.tick(beast,2200);
        for (long at=3200;at<=50000;at+=1000) live.tick(beast,at);
        check(live.engine.state==TradeEngine.RESYNC,"Live purchase cadence cannot postpone missing-reply timeout forever");
        int bought=net.buyCalls;
        live.tick(beast,51000);
        check(net.buyCalls==bought,"Resync live adapter only reads cargo, never sends another buy");

        live=live(new TradeConfig(),"Sa Mạc"); live.engine.market=2; live.engine.state=TradeEngine.TO_SELL;
        live.view.cargo=6; live.tick(beast,2000);
        TradeRecovery.Record record=TradeRecovery.load(100,2000);
        check(record!=null && record.market==2,"Recovery saves actual rotated market");
        TradeRecovery.save(100,"Sa Mạc",200,200,6,99,0,0,2100);
        check(TradeRecovery.load(100,2200)==null,"Invalid persisted configuration cannot crash automatic recovery");

        live=live(new TradeConfig(),"Sa Mạc");
        for (int x=0;x<20;x++) GameCanvas.loadmap.mapType[x]=1;
        GameScreen.player.x=240; GameScreen.player.y=120;
        live.navigation.walk(240,0,0,null,2000);
        int end=GameScreen.player.yStopMove/24*20+GameScreen.player.xStopMove/24;
        check(GameScreen.player.posTransRoad!=null && GameCanvas.loadmap.mapType[end]==0,
            "Final emulator waypoint stays on BFS walkable endpoint beside border portal");
        GameScreen.player.x=GameScreen.player.xStopMove; GameScreen.player.y=GameScreen.player.yStopMove;
        Navigator.stop(); live.navigation.walk(240,0,0,null,2100);
        check(GameScreen.player.posTransRoad==null,"Standing beside blocked portal cannot force movement into wall");
        live=sale(beast);
        live.serverText("Không bán được hàng"); live.tick(beast,4100); live.tick(beast,6200);
        check(live.earnings.successfulTrips==0 && live.view.cargo==6,
            "Negative sale reply cannot complete trip and erase loaded cargo");
        live=sale(beast);
        live.serverText("Bạn nhận được 1 Kim Cương"); live.tick(beast,4100); live.tick(beast,6200);
        check(live.earnings.successfulTrips==0 && live.view.cargo==6,
            "Unrelated item receipt cannot count as a sale");
        live=sale(beast);
        Object unrelated=new Object(); GameCanvas.currentDialog=unrelated;
        mVector unrelatedButtons=buttons("Ok", "Hủy");
        live.dialog(unrelated,"Bạn có muốn dùng 100 vàng để nâng cấp?",unrelatedButtons); live.tick(beast,4100);
        check(((iCommand)unrelatedButtons.elementAt(0)).calls==0 && GameCanvas.currentDialog==unrelated,
            "Vietnamese pronoun ban with gold must not be mistaken for selling");
        live=sale(beast);
        live.engine.state=TradeEngine.BUY;
        GameScreen.ObjFocus=beast;
        check(live.animalCargo("Lừa vàng",6),"Receive refreshed inventory after interrupted escort");
        live.tick(beast,4100);
        check(live.earnings.failedTrips==0,"Re-reading unchanged inventory cannot count as buying a new trip");
        live=live(new TradeConfig(),"Sa Mạc");
        live.view.cargo=6; live.view.full=true; live.engine.state=TradeEngine.TO_SELL;
        beast.hp=1; live.tick(beast,2000);
        check(live.engine.state==TradeEngine.WAIT_REPLACE,"Low HP starts replacement");
        MainObject replacement=animal("Lừa vàng"); replacement.ID=71;
        int potions=net.potionCalls;
        live.tick(replacement,3000);
        check(live.engine.state==TradeEngine.WAIT_NEW && live.view.cargo==-1 && !live.view.full,
            "Atomic replacement waits for real new inventory instead of retaining old full cargo");
        check(net.potionCalls==potions,"Atomic replacement must not use the item again to cancel the new animal");
        live.tick(replacement,44000);
        check(live.engine.state==TradeEngine.WAIT_NEW,"Inventory delay cannot destroy replacement after 40s");
        beast.hp=100;

        live=live(new TradeConfig(),"Sa Mạc");
        live.view.cargo=6; live.engine.state=TradeEngine.TO_SELL; live.tick(beast,2000);
        GameScreen.player.posTransRoad=new short[]{1,2,3};
        live.tick(null,2100);
        check(GameScreen.player.posTransRoad==null,"Stop old road immediately during the missing-animal grace period");
        live.tick(null,4300);
        live.tick(beast,5000);
        GameCanvas.loadmap.idMap=78; GameCanvas.loadmap.nameMap="Đồi Cát";
        live.tick(beast,6000);
        live.tick(null,6100); live.tick(null,8300);
        check(live.view.inRecoveryMap && live.lastBeastMap.equals("Đồi Cát"),
            "Second disappearance uses latest sighting, not stale first recovery map");
        live.tick(null,200000);
        check(live.engine.state==TradeEngine.TO_MARKET && live.earnings.failedTrips==1 && !live.hasLostTrip(),
            "Loaded trip search expires and returns to market instead of renewing forever");
        live.tick(null,201100);
        check(live.engine.state!=TradeEngine.RECOVER && live.earnings.failedTrips==1,
            "Expired recovery cannot restart from stale animal data or count failure twice");

        live=live(new TradeConfig(),"Sa Mạc");
        live.view.cargo=6; live.engine.state=TradeEngine.TO_SELL; live.tick(beast,2000);
        live.toggle(3000); live.toggle(4000); live.tick(null,25000);
        check(live.engine.state==TradeEngine.RECOVER && live.hasLostTrip() && live.earnings.failedTrips==0,
            "Toggle resume timeout searches saved animal instead of abandoning it");
        System.out.println("PASS automatic trade lifecycle: "+checks+" assertions");
    }
}
