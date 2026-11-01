import autotrade.*;
import GameObjects.*;
import GameScreen.GameScreen;
import Main.GameCanvas;
import CLib.mVector;
import InterfaceComponents.iCommand;
import netcommand.global.GlobalService;

public final class DylanPurchaseRegressionTest {
    static int checks;
    static void check(boolean ok,String message){ checks++; if(!ok)throw new AssertionError(message); }
    public static void main(String[] args) {
        String emptyMessage="Ta hết hàng rồi. Một lát nữa hãy quay lại ta sẽ có đủ hàng";
        check(TradeStock.parse(emptyMessage).empty(),"Screenshot: merchant says all stock is exhausted");
        check(TradeStock.parse(emptyMessage.replace(' ', '\n')).empty(),"Wrapped screenshot message");
        check(!TradeStock.parse("Ta chưa hết hàng").empty(),"Do not infer zero stock from negation");
        check(!TradeStock.parse("Kim cương hết hàng rồi").empty(),"Single gem depletion is not all-stock depletion");
        String message="Ta chỉ còn 23 viên\nsaphia 17 viên ruby 8 viên kim cương 150\nNgọc trai đen mà thôi,\nnhanh tay lên kẻo hết.";
        TradeStock stock=TradeStock.parse(message);
        check(stock.count[0]==8&&stock.count[1]==17&&stock.count[2]==23,"Parse complete Dylan sentence without commas");
        check(stock.pearls&&stock.choose(0)==0,"150 pearls are not added to gem quantities");
        stock=TradeStock.parse("Ta chỉ\ncòn 0 viên saphia 0 viên ruby 0 viên kim\ncương 150 Ngọc trai đen mà thôi");
        check(stock.empty(),"Wrapped gem names and zero stock");
        check(TradeStock.parse("Ta chỉ còn 150\nNgọc trai đen mà thôi, nhanh tay lên kẻo hết").empty(),"Pearl-only report without vien");
        check(TradeStock.parse("Ta chỉ còn xx viên saphia xx viên ruby xx viên kim cương 150 Ngọc trai đen").choose(0)==-1,"Placeholder xx never turns into available stock");

        GameCanvas.loadmap.idMap=19;GameCanvas.loadmap.nameMap="Cửa Biển";GameCanvas.currentDialog=null;
        GameScreen.player=new Player();GameScreen.player.x=200;GameScreen.player.y=200;
        MainObject beast=new MainObject();beast.ID=1085;beast.typeObject=1;beast.x=200;beast.y=200;
        MainObject dylan=new MainObject();dylan.ID=1087;dylan.npcWireId=-25;dylan.typeObject=0;dylan.name="Mr Dylan";dylan.x=200;dylan.y=200;
        GameScreen.Vecplayers.removeAllElements();GameScreen.Vecplayers.addElement(beast);GameScreen.Vecplayers.addElement(dylan);
        GameScreen.ObjFocus=beast;
        TradeConfig config=new TradeConfig();config.market=1;config.gem=1;
        LiveTrade live=new LiveTrade(config,new MapGraph());GlobalService net=GlobalService.gI();
        int originalPotions=net.potionCalls,originalMenus=net.menuCalls;
        int originalQueries=net.queryCalls, originalShops=net.shopCalls;
        beast.hp=100; beast.maxHp=100;
        live.toggle(1000);live.tick(beast,1000);
        check(live.engine.state==TradeEngine.STOCK,"Enabling at selected market continues existing unknown trip purchase");
        check(net.queryCalls==originalQueries+1 && net.shopCalls==originalShops && net.menuCalls==originalMenus,
            "Existing trip checks merchant stock without animal info or replacement");
        live.toggle(1100); live.toggle(1200); live.tick(beast,1200);
        check(live.engine.state==TradeEngine.STOCK,"Repeated off/on continues loading at the selected market");
        live.toggle(1300); beast.hp=100;
        // Resume the same unknown-cargo animal at the configured market.
        live.toggle(1400); live.tick(beast,1400);
        check(net.lastNpc==-25&&GameScreen.ObjFocus==dylan,"Find Type-0 server NPC and send its protocol ID, not object ID");
        check(live.view.cargo==-1,"Existing cargo stays unknown rather than being falsely declared empty");
        dylan.strChatPopup=message;live.chat(dylan);live.tick(beast,2000);live.tick(beast,3000);
        check(net.shopCalls==1&&net.lastNpc==-25,"Existing animal proceeds to Dylan shop after stock check");
        check(net.potionCalls==originalPotions,"Do not summon another animal");
        mVector options=new mVector();options.addElement(new iCommand("Mua ruby"));
        live.menu(options,3,-25,"");live.tick(beast,4000);
        check(net.menuCalls==originalMenus+1&&net.lastNpc==-25,"Accept merchant menu keyed by protocol ID");
        for(int time=5000;time<=8000;time+=1000)live.tick(beast,time);
        check(net.menuCalls==originalMenus+1,"No repeated -56 animal-info requests while buying");
        check(GameScreen.ObjFocus==dylan,"Keep focus on Dylan instead of animal");
        live.serverText("Mua thành công");live.tick(beast,9000);
        check(live.view.cargo>0&&live.engine.state==TradeEngine.BUY,"Acknowledged purchase resumes buying");
        live.tick(beast,10000);live.menu(options,3,-25,"");live.tick(beast,11000);
        live.serverText("Không thể mua thêm, thú đã đầy");live.tick(beast,12000);
        check(live.engine.state==TradeEngine.TO_SELL,"Server full-load reply ends purchase loop");
        MainObject person=new MainObject();person.typeObject=0;person.name="Mr Dylan";
        check(!LiveTrade.isNpc(person),"A player named Dylan is not an NPC");
        check(!LiveTrade.isNpc(beast),"Trade animal is not an NPC");
        live.engine.stop();live.toggle(13000);live.tick(beast,13000);
        live.disconnected();
        // Replay uses the same connected client with unknown cargo and a visible animal at the market.
        live.tick(beast,14000);
        check(live.engine.state==TradeEngine.STOCK,"Reconnect at selected market continues loading existing unknown cargo");
        check(live.view.cargo==-1,"Resumed trip does not invent a cargo quantity");
        for (int rotate=0;rotate<2;rotate++) {
            GameScreen.Vecplayers.removeAllElements(); GameScreen.Vecplayers.addElement(dylan);
            GameCanvas.currentDialog=null;
            TradeConfig emptyConfig=new TradeConfig(); emptyConfig.market=1; emptyConfig.rotateMarkets=rotate==1;
            LiveTrade emptyLive=new LiveTrade(emptyConfig,new MapGraph());
            emptyLive.toggle(1000); emptyLive.tick(null,1000);
            int queried=net.queryCalls;
            dylan.strChatPopup=emptyMessage; emptyLive.chat(dylan); emptyLive.tick(null,2000);
            if (rotate==1) check(emptyLive.engine.market==2 && emptyLive.engine.state==TradeEngine.TO_MARKET,"Screenshot triggers configured market rotation");
            else {
                check(emptyLive.engine.market==1 && emptyLive.engine.state==TradeEngine.STOCK,"Screenshot waits at selected market when rotation disabled");
                emptyLive.tick(null,3000);
                check(net.queryCalls==queried,"Empty-market wait does not spam NPC");
            }
        }
        System.out.println("PASS Dylan identity, stock and existing-animal purchase: "+checks+" assertions");
    }
}
