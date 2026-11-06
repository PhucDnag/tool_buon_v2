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
        GlobalService net=GlobalService.gI();
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
        missingSale.tick(null,65000);
        check(missingSale.engine.state==TradeEngine.TO_MARKET && GameCanvas.currentDialog==null,
            "Unsuccessful search returns to market without a blocking error popup");
        missingDialog=new Object(); GameCanvas.currentDialog=missingDialog;
        missingSale.dialog(missingDialog,missingText,buttons("Ok",null)); missingSale.tick(null,65100);
        check(GameCanvas.currentDialog==null && missingSale.engine.state==TradeEngine.TO_MARKET,
            "Late informational rejection cannot block or restart market navigation");
        missingSale=sale(beast); missingDialog=new Object(); GameCanvas.currentDialog=missingDialog;
        missingSale.dialog(missingDialog,missingText,buttons("Ok",null)); missingSale.tick(beast,4100);
        check(GameCanvas.currentDialog==null && missingSale.engine.state==TradeEngine.RESYNC,
            "Visible but server-rejected animal gets inventory verification rather than repeated sale");
        LiveTrade delayed=live(new TradeConfig(),"Sa Mạc");
        delayed.tick(null,2000); delayed.tick(beast,3000);
        check(delayed.view.cargo==-1 && delayed.engine.state==TradeEngine.TO_SELL,
            "Delayed existing animal keeps unknown cargo and is not recalled as empty");
        LiveTrade live=sale(beast);
        live.tick(null,4100);
        check(live.engine.state==TradeEngine.WAIT_SALE && live.earnings.gold==0 && live.earnings.unmeasured==0,
            "Disappearance at Graham alone cannot complete a sale or erase the trip");

        live=sale(beast); GameScreen.player.gold+=100;
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
        GameScreen.player.gold+=960000; live.tick(null,4200);
        check(live.engine.state==TradeEngine.TO_MARKET && live.earnings.gold==960000,
            "Delayed wallet receipt is counted before leaving seller");
        live.serverText("Bán hàng thành công"); live.tick(null,4300);
        check(live.earnings.gold==960000,"Duplicate success cannot double the receipt");

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
        System.out.println("PASS automatic trade lifecycle: "+checks+" assertions");
    }
}
