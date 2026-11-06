import autotrade.*;
import GameObjects.*;
import GameScreen.GameScreen;
import Main.GameCanvas;
import InterfaceComponents.iCommand;
import CLib.mVector;
import netcommand.global.GlobalService;

public final class TradeConfirmationTest {
    static final String PROMPT="Bạn có 31 phút để hoàn thành 1 chuyến đi buôn, nếu hết thời hạn đá quý sẽ bị hủy. Bạn có muốn đi không ?";
    static int checks;
    static void check(boolean pass,String text) { checks++; if(!pass) throw new AssertionError(text); }
    static mVector options(String caption) { mVector list=new mVector(); list.addElement(new iCommand(caption)); return list; }
    static LiveTrade start() { return start(new TradeConfig()); }
    static LiveTrade start(TradeConfig config) {
        GameCanvas.menu2.isShowMenu=false;
        GameCanvas.loadmap.idMap=19; GameCanvas.loadmap.nameMap="Cửa Biển";
        GameCanvas.currentDialog=null; GameScreen.player=new Player(); GameScreen.player.x=200; GameScreen.player.y=200;
        GameScreen.Vecplayers.removeAllElements(); Item.VecInvetoryPlayer.removeAllElements();
        MainObject npc=new MainObject(); npc.ID=5; npc.typeObject=2; npc.name="Dylan"; npc.x=200; npc.y=200;
        GameScreen.Vecplayers.addElement(npc);
        Item item=new Item(); item.Id=50; item.ItemCatagory=4; item.itemName="Lừa vàng"; item.numPotion=1;
        Item.VecInvetoryPlayer.addElement(item);
        config.market=1;
        LiveTrade live=new LiveTrade(config,new MapGraph()); live.toggle(1000); live.tick(null,1000);
        npc.strChatPopup="Ta chỉ còn 3 viên kim cương, 3 viên riuby, 3 viên saphia";
        live.chat(npc); live.tick(null,2000); return live;
    }
    static mVector sixCargo() {
        mVector list=new mVector(); for(int i=0;i<6;i++) list.addElement(new Item()); return list;
    }
    static mVector goods() {
        mVector items=new mVector(); Item gem=new Item(); gem.Id=81; gem.ItemCatagory=4; gem.itemName="Kim Cương"; items.addElement(gem); return items;
    }
    public static void main(String[] args) {
        String replacementPrompt="Con vật buôn của bạn đang ở map Hố Tử Thần vị trí 10 , 37. Bạn có muốn hủy con thú cũ để tạo con thú mới không ?";
        check(TradeSignals.replaceAnimalConfirmation(replacementPrompt),"Recognize remote old-animal replacement screenshot");
        check(TradeSignals.replaceAnimalConfirmation(replacementPrompt.replace(' ', '\n')),"Wrapped replacement prompt");
        check(!TradeSignals.replaceAnimalConfirmation("Bạn có muốn hủy con thú cũ không ?"),"Do not accept unrelated cancellation as replacement");
        LiveTrade replacementLive=start();
        Object replacementDialog=new Object(); GameCanvas.currentDialog=replacementDialog;
        mVector replacementButtons=options("Ok"); replacementButtons.addElement(new iCommand("Hủy"));
        iCommand replacementOk=(iCommand)replacementButtons.elementAt(0);
        int replacementUses=GlobalService.gI().potionCalls;
        replacementLive.dialog(replacementDialog,replacementPrompt,replacementButtons); replacementLive.tick(null,2500);
        check(replacementOk.calls==1 && ((iCommand)replacementButtons.elementAt(1)).calls==0,"Confirm creating replacement for off-map animal after summon");
        replacementLive.dialog(replacementDialog,replacementPrompt,replacementButtons); replacementLive.tick(null,2600);
        check(replacementOk.calls==1 && GlobalService.gI().potionCalls==replacementUses,"Do not duplicate confirmation or item use");
        check(replacementLive.engine.state==TradeEngine.SUMMON,"Wait for server animal instead of assuming success");
        check(TradeSignals.startTradeConfirmation(PROMPT),"Match exact supplied screenshot");
        check(TradeSignals.startTradeConfirmation(PROMPT.replace(' ', '\n')),"Line-wrapped prompt also matches");
        check(TradeSignals.startTradeConfirmation(PROMPT.replace('1', '9')),"Duration is not hardcoded");
        check(!TradeSignals.startTradeConfirmation("Bạn có muốn hủy chuyến đi buôn?"),"Do not confuse cancel with start");
        LiveTrade live=start(); GlobalService net=GlobalService.gI(); int summons=net.potionCalls;
        Object dialog=new Object(); GameCanvas.currentDialog=dialog; mVector buttons=options("Ok"); iCommand ok=(iCommand)buttons.elementAt(0);
        live.dialog(dialog,PROMPT,buttons); live.tick(null,2500);
        check(ok.calls==1,"Auto OK before animal appears");
        check(live.engine.state==TradeEngine.SUMMON,"OK still waits for server to create animal");
        live.dialog(dialog,PROMPT,buttons); live.tick(null,2600);
        check(ok.calls==1 && net.potionCalls==summons,"Repeated render cannot confirm or summon twice");
        MainObject beast=new MainObject(); beast.ID=70; beast.x=200; beast.y=200;
        live.tick(beast,3000); live.tick(beast,4000);
        live.menu(options("Mua Kim Cương"),2,5,""); live.tick(beast,5000);
        int buys=net.buyCalls;
        live.shop(goods(),-1,0); live.tick(beast,5500);
        check(net.buyCalls==buys,"Inventory gem list must not trigger purchase");
        live.shop(goods(),0,0); live.tick(beast,6000);
        check(net.buyCalls==buys+1 && net.lastItem==81 && net.lastShop==0 && net.lastQuantity==1,"Gem submenu opens normal shop then sends real buy_item");
        live.shop(goods(),0,0); live.tick(beast,6500);
        check(net.buyCalls==buys+1,"Duplicate shop response cannot repeat pending purchase");
        live.serverText("Mua thành công"); live.tick(beast,6700);
        check(live.view.cargo==1,"Read buy acknowledgement after OK and shop chain");
        live.tick(beast,7000); live.shop(goods(),13,11); live.tick(beast,7100);
        check(net.buyCalls==buys+2 && net.lastShop==0,"Next purchase reuses the observed shop without reopening menus");

        // Starting a trip through a merchant's generic menu also has no animal or gem name in its prompt.
        live=start(); live.tick(beast,3000); live.tick(beast,4000);
        live.menu(options("Đi buôn"),1,5,""); live.tick(beast,5000);
        dialog=new Object(); GameCanvas.currentDialog=dialog; buttons=options("Ok"); ok=(iCommand)buttons.elementAt(0);
        live.dialog(dialog,PROMPT,buttons); live.tick(beast,6000);
        check(ok.calls==1,"Confirm merchant trip-start prompt before specific buy request");
        buys=net.buyCalls; live.shop(goods(),13,11); live.tick(beast,7000);
        check(net.buyCalls==buys+1,"Continue shopping after merchant trip confirmation");

        // Exact root menu from the reported screenshot, followed by the gem submenu and shop.
        live=start(); live.tick(beast,3000); live.tick(beast,4000);
        int menus=net.menuCalls; buys=net.buyCalls;
        live.menu(options("Mua bán"),7,99,""); live.tick(beast,5000);
        check(net.menuCalls==menus,"Ignore Mua ban menu from an unrelated NPC");
        live.menu(options("Mua bán"),7,5,""); live.tick(beast,6000);
        check(net.menuCalls==menus+1 && net.lastNpc==5 && net.lastIndex==0,"Select exact Mua ban root menu");
        check(net.buyCalls==buys,"Opening trade menu is not a purchase");
        live.menu(options("Mua Kim Cương"),8,5,""); live.tick(beast,7000);
        check(net.menuCalls==menus+2,"Continue into gem submenu after Mua ban");
        live.shop(goods(),13,11); live.tick(beast,8000);
        check(net.buyCalls==buys+1,"Mua ban chain reaches actual purchase");
        live.shop(goods(),13,11); live.tick(beast,8500);
        check(net.buyCalls==buys+1,"Wait for acknowledgement without buying twice");
        live.serverText("Mua thành công"); live.tick(beast,8700);
        check(live.view.cargo==1,"Mua ban purchase acknowledgement updates cargo");

        live=start(); live.tick(beast,3000); live.tick(beast,4000);
        check(live.hideMenu(5) && !live.hideMenu(99),"Hide only the auto-buy merchant menu");
        dialog=new Object(); live.dialog(dialog,PROMPT,options("Ok"));
        check(live.hideDialog(dialog) && !live.hideDialog(new Object()),"Hide only the owned trade confirmation");
        buys=net.buyCalls; live.shop(goods(),13,11);
        check(live.takeHiddenShop() && !live.takeHiddenShop(),"Consume hidden shop flag once");
        live.tick(beast,5000);
        check(net.buyCalls==buys+1,"First hidden purchase is sent");
        live.serverText("Mua thành công"); live.tick(beast,5100); live.tick(beast,5200); live.tick(beast,5350);
        check(net.buyCalls==buys+1,"No repeat purchase before 1s cadence");
        live.tick(beast,5999);
        check(net.buyCalls==buys+1,"No purchase one millisecond before 1s");
        live.tick(beast,6000);
        check(net.buyCalls==buys+2,"Cached shop buys again after cadence");
        Object manual=new Object(); GameCanvas.currentDialog=manual;
        mVector manualButtons=options("Ok");
        live.dialog(manual,"Bạn có muốn xóa vật phẩm?",manualButtons);
        check(!live.hideDialog(manual),"Manual dialog stays visible during pending purchase");
        MainObject manualFocus=new MainObject(); GameScreen.ObjFocus=manualFocus;
        live.tick(beast,6200);
        check(GameScreen.ObjFocus==manualFocus,"Pending purchase does not steal manual target");
        check(GameCanvas.currentDialog==manual && ((iCommand)manualButtons.elementAt(0)).calls==0,"Pending purchase leaves manual dialog untouched");
        check(net.buyCalls==buys+2,"Fast cadence still waits for server acknowledgement");
        live.toggle(6300);
        check(!live.hideMenu(5) && !live.hideDialog(dialog),"Manual UI remains visible after auto stops");

        live=start(); dialog=new Object(); GameCanvas.currentDialog=dialog; buttons=options("Ok"); ok=(iCommand)buttons.elementAt(0);
        live.dialog(dialog,"Bạn có muốn xóa vật phẩm?",buttons); live.tick(null,2500);
        check(ok.calls==0,"Unrelated OK remains untouched");
        live.dialog(dialog,PROMPT,buttons); GameCanvas.currentDialog=new Object(); live.tick(null,2600);
        check(ok.calls==0,"Stale dialog instance is not accepted");
        live.toggle(2700); GameCanvas.currentDialog=dialog; live.dialog(dialog,PROMPT,buttons); live.tick(null,2800);
        check(ok.calls==0,"Disabled auto cannot press OK");

        // Verify extended bought signals
        check(TradeSignals.bought("Mua 1 Kim Cương thành công"), "Phrase: Mua 1 Kim Cương thành công");
        check(TradeSignals.bought("Bạn đã mua thành công 1 Kim Cương"), "Phrase: Bạn đã mua thành công");
        check(TradeSignals.bought("Giao dịch thành công"), "Phrase: Giao dịch thành công");
        check(TradeSignals.bought("Bạn nhận được 1 Kim Cương"), "Phrase: Bạn nhận được 1 Kim Cương");
        check(!TradeSignals.bought("Bạn không đủ vàng để mua"), "Negative purchase signal rejected");

        // Verify TradeEngine WAIT_BUY retry mechanism
        TradeEngine engine = live.engine;
        engine.start(30000);
        live.view.market = 1;
        engine.state = TradeEngine.BUY;
        live.view.cargo = 0;
        live.view.cargoRevision = 1;
        engine.tick(live.view, 31000);
        check(engine.state == TradeEngine.WAIT_BUY, "Engine enters WAIT_BUY");
        // Simulate 10s timeout without confirmation
        engine.tick(live.view, 42000);
        check(engine.state == TradeEngine.WAIT_BUY, "Engine retries purchase instead of failing on first timeout");
        check(engine.status.indexOf("Chờ xác nhận mua (1/2)") >= 0, "Engine updates status to retry attempt 1");
        // Second timeout
        engine.tick(live.view, 53000);
        check(engine.state == TradeEngine.WAIT_BUY, "Engine retries purchase on second timeout");
        check(engine.status.indexOf("Chờ xác nhận mua (2/2)") >= 0, "Engine updates status to retry attempt 2");
        // Third timeout -> exhausted -> now fails
        engine.tick(live.view, 64000);
        check(engine.state == TradeEngine.RESYNC, "Engine synchronizes after exhausting immediate retry attempts");
        check(engine.status.indexOf("Chưa xác nhận giao dịch mua") >= 0, "Engine reports failure only after 3 full cycles");

        // Exact full message must end purchasing without waiting for the retry timeout.
        live=start(); live.tick(beast,3000); live.tick(beast,4000);
        live.shop(goods(),13,11); live.tick(beast,5000);
        buys=net.buyCalls;
        dialog=new Object(); GameCanvas.currentDialog=dialog;
        live.dialog(dialog,"Không thể mua",options("Ok")); live.tick(beast,5100);
        check(live.engine.state==TradeEngine.TO_SELL && live.view.full,"Exact full message immediately starts escort");
        check(GameCanvas.currentDialog==null,"Dismiss full dialog so escort can move");
        live.tick(beast,5200);
        check(net.buyCalls==buys,"No more purchases after full message");
        check(!TradeSignals.full("Không thể mua vì không đủ vàng"),"Unrelated purchase failure is not full cargo");

        live=start(); live.tick(beast,3000); live.tick(beast,4000);
        live.shop(goods(),13,11); live.tick(beast,5000);
        live.serverText("Mua thành công"); live.tick(beast,5100); live.tick(beast,5200);
        live.serverText("Kim Cương: 0, Rubi: 0, Saphia: 0; Ngọc trai đen: 999"); live.tick(beast,5300);
        check(live.engine.state==TradeEngine.TO_SELL && live.view.cargo==1,"All gems exhausted during buying escorts partial load, ignoring pearls");
        check(live.intent==0,"Depleted partial load clears pending purchase actions before escort");

        live=start(); live.tick(beast,3000); live.tick(beast,4000);
        live.shop(goods(),13,11); live.tick(beast,5000);
        live.serverText("Hết hàng"); live.tick(beast,5100);
        check(live.engine.state==TradeEngine.STOCK,"One depleted gem triggers fresh stock check");
        MainObject merchant=MainObject.get_Object(5,(byte)2);
        merchant.strChatPopup="Ta chỉ còn 0 viên kim cương, 2 viên riuby, 0 viên saphia, 99 viên ngọc trai đen";
        live.chat(merchant); live.tick(beast,5300);
        check(live.engine.state==TradeEngine.BUY && live.engine.gem==1,"Continue buying ruby when diamonds are exhausted");
        live.tick(beast,5500);
        live.serverText("Hết hàng"); live.tick(beast,5600);
        merchant.strChatPopup="Chỉ còn ngọc trai đen"; live.chat(merchant); live.tick(beast,5800);
        check(live.engine.state==TradeEngine.RECALL,"Rechecked pearl-only stock cancels a confirmed empty animal rather than selling it");

        // Low-HP existing cargo is replaced outside a buying market using the item twice.
        live=start(); live.toggle(2200);
        GameCanvas.loadmap.idMap=25; GameCanvas.loadmap.nameMap="Rừng Chết";
        beast.name="Lừa vàng"; beast.hp=10; beast.maxHp=100;
        int beforeReplace=net.potionCalls, beforeInfo=net.menuCalls;
        live.toggle(3000); live.tick(beast,3000);
        check(live.engine.state==TradeEngine.WAIT_REPLACE && net.potionCalls==beforeReplace+1,
            "Use matching animal item to cancel existing low-HP trip outside market");
        live.tick(beast,3500);
        check(net.potionCalls==beforeReplace+1 && net.menuCalls==beforeInfo,"Do not cancel twice or use animal-info menu");
        live.tick(null,4000);
        check(live.engine.state==TradeEngine.WAIT_NEW && net.potionCalls==beforeReplace+2,"Use item again only after old animal disappears");
        MainObject replacement=new MainObject(); replacement.ID=999; replacement.name="Lừa vàng";
        replacement.x=200; replacement.y=200; replacement.hp=100; replacement.maxHp=100;
        MainObject drop=new MainObject(); drop.ID=998; drop.typeObject=4; drop.name="Kim Cương";
        GameScreen.Vecplayers.addElement(drop);
        live.tick(replacement,5000); live.tick(replacement,9000);
        check(live.engine.state==TradeEngine.WAIT_NEW && Player.autoItem!=null,"Wait with auto-pickup enabled while gems remain on ground");
        drop.isRemove=true; live.tick(replacement,10000); live.tick(replacement,11200);
        check(live.engine.state==TradeEngine.WAIT_NEW,"Wait for cargo confirmation after drops disappear");
        live.animalCargo("Lừa vàng",1); live.tick(replacement,11500);
        check(live.engine.state==TradeEngine.TO_SELL,"Resume escort after confirmed pickup");
        GameCanvas.loadmap.idMap=8; GameCanvas.loadmap.nameMap=TradeConfig.SELLER_MAP;
        MainObject graham=new MainObject(); graham.ID=77; graham.typeObject=2; graham.name="Graham";
        graham.x=200; graham.y=200; GameScreen.Vecplayers.addElement(graham);
        live.tick(replacement,12000);
        live.animalCargo("Lừa vàng",1); live.tick(replacement,12500); live.tick(replacement,13000);
        live.menu(options("Bán hàng"),2,77,""); live.tick(replacement,14000);
        live.menu(options("Bán tất cả hàng"),3,77,""); live.tick(replacement,15000);
        check(net.lastNpc==77 && net.lastIndex==0,"Continue sale submenu even after initial sell selection");
        GameScreen.player.coin+=12345;
        graham.strChatPopup="Bán hàng thành công"; live.chat(graham); live.tick(replacement,16000);
        check(live.engine.state==TradeEngine.WAIT_RECALL && live.view.cargo==0,
            "Graham acknowledgement completes sale and cancels the remaining empty animal");
        TradeConfig switched=new TradeConfig(); switched.gem=2;
        live=start(switched); live.engine.gem=2; beast=new MainObject(); beast.ID=70; beast.x=200; beast.y=200;
        live.tick(beast,3000); live.tick(beast,4000);
        mVector mixedGoods=goods(); Item sapphire=new Item(); sapphire.Id=82; sapphire.ItemCatagory=4; sapphire.itemName="Saphia";
        Item ruby=new Item(); ruby.Id=83; ruby.ItemCatagory=4; ruby.itemName="Ruby";
        mixedGoods.addElement(sapphire); mixedGoods.addElement(ruby);
        live.shop(mixedGoods,13,11); live.tick(beast,5000);
        check(net.lastItem==82,"Initial purchase uses sapphire");
        live.serverText("Mua thành công"); live.tick(beast,5100);
        live.toggle(5200); switched.gem=1; live.toggle(5300); live.tick(beast,5300);
        check(live.engine.state==TradeEngine.STOCK && live.view.cargo==1,"Paused partial purchase resumes stock check instead of escort and retains cargo");
        MainObject switchingMerchant=MainObject.get_Object(5,(byte)2);
        switchingMerchant.strChatPopup="Kim Cương: 5, Rubi: 5, Saphia: 5";
        live.chat(switchingMerchant); live.tick(beast,5500); live.tick(beast,5700);
        live.shop(mixedGoods,13,11); live.tick(beast,5800);
        int beforeRuby=net.buyCalls;
        live.tick(beast,5999);
        check(net.buyCalls==beforeRuby,"Off/on preserves 1s purchase cadence");
        live.tick(beast,6000);
        check(net.buyCalls==beforeRuby+1 && net.lastItem==83 && live.engine.gem==1,"Resumed purchase uses newly selected ruby");
        live.toggle(9100); live.toggle(9200); live.tick(beast,9200);
        switchingMerchant.strChatPopup="Chỉ còn ngọc trai đen"; live.chat(switchingMerchant); live.tick(beast,9400);
        check(live.engine.state==TradeEngine.TO_SELL,"Resumed purchase still escorts when all tradable gems are exhausted");

        live=start(); live.tick(beast,3000); live.tick(beast,4000);
        live.view.full=true; live.toggle(4100); live.toggle(4200); live.tick(beast,4200);
        check(live.engine.state==TradeEngine.TO_SELL,"Paused full animal cannot resume purchasing");
        live=start(); live.tick(beast,3000); live.tick(beast,4000); live.toggle(4100);
        GameCanvas.loadmap.idMap=25; GameCanvas.loadmap.nameMap="Rừng Chết";
        live.toggle(4200); live.tick(beast,4200);
        check(live.engine.state==TradeEngine.TO_SELL,"Leaving the buying map invalidates paused purchase resume");
        switched=new TradeConfig(); switched.gem=1; live=start(switched);
        live.tick(beast,3000); live.tick(beast,4000); live.shop(mixedGoods,13,11); live.tick(beast,5000);
        check(net.lastItem==83,"Configured ruby wins over available diamonds");
        live.serverText("Mua thành công"); live.tick(beast,5100);
        switched.gem=2; live.tick(beast,5300);
        check(live.engine.state==TradeEngine.STOCK,"Changing preference while active rechecks stock after pending purchase is acknowledged");
        switchingMerchant=MainObject.get_Object(5,(byte)2);
        switchingMerchant.strChatPopup="Kim Cương: 99, Rubi: 99, Saphia: 5";
        live.chat(switchingMerchant); live.tick(beast,5500); live.tick(beast,5700);
        live.shop(mixedGoods,13,11); live.tick(beast,5800); live.tick(beast,9000);
        check(net.lastItem==82 && live.engine.gem==2,"Active preference change purchases sapphire despite diamonds and ruby being available");

        switched=new TradeConfig(); switched.gem=1; live=start(switched);
        live.tick(beast,3000); live.tick(beast,4000); live.shop(mixedGoods,13,11); live.tick(beast,5000);
        live.serverText("Hết hàng"); live.tick(beast,5100);
        switchingMerchant=MainObject.get_Object(5,(byte)2);
        switchingMerchant.strChatPopup="Kim Cương: 99, Rubi: 0, Saphia: 5";
        live.chat(switchingMerchant); live.tick(beast,5300); live.tick(beast,5500);
        live.shop(mixedGoods,13,11); live.tick(beast,5700); live.tick(beast,9000);
        check(net.lastItem==82 && live.engine.gem==2,"Ruby depletion falls forward to sapphire, never diamonds");
        live.serverText("Hết hàng"); live.tick(beast,9100);
        switchingMerchant.strChatPopup="Kim Cương: 99, Rubi: 0, Saphia: 0";
        live.chat(switchingMerchant); live.tick(beast,9300);
        check(live.engine.state==TradeEngine.RECALL,"Exhausted ruby and sapphire cancel empty animal when no purchase succeeded");
        // Exact reported situation: fresh client at Vach Da, ruby configured,
        // an existing animal, no remembered pause or known capacity.
        start();
        GameCanvas.loadmap.idMap=39; GameCanvas.loadmap.nameMap="Vách Đá Cheo Leo";
        switched=new TradeConfig(); switched.market=2; switched.gem=1; switched.animal=2;
        live=new LiveTrade(switched,new MapGraph());
        live.toggle(1000); live.tick(beast,1000);
        check(live.engine.state==TradeEngine.STOCK,"Fresh start at Vach Da checks stock instead of escorting existing animal");
        switchingMerchant=MainObject.get_Object(5,(byte)2);
        switchingMerchant.strChatPopup="Ta chỉ còn 200 viên saphia 171 viên ruby mà thôi, nhanh tay lên kẻo hết";
        live.chat(switchingMerchant); live.tick(beast,2000); live.tick(beast,2200);
        live.shop(mixedGoods,13,11); live.tick(beast,2300);
        check(net.lastItem==83 && live.engine.state==TradeEngine.WAIT_BUY,"Screenshot stock selects and buys ruby with existing animal on cold start");
        live.view.cargo=6;
        live.toggle(2400); live.toggle(2500); live.tick(beast,2500);
        check(live.engine.state==TradeEngine.STOCK && live.view.cargo==6,"Six existing gems do not cause escort when restarting at selected market");
        // An escort paused before leaving the market must not restart purchases.
        live=start(); beast=new MainObject(); beast.ID=70; beast.x=200; beast.y=200;
        live.tick(beast,3000); live.tick(beast,4000);
        live.engine.state=TradeEngine.TO_SELL; live.view.cargo=6; live.view.full=false;
        live.toggle(4100);
        GameCanvas.loadmap.mapW=30; GameCanvas.loadmap.mapH=30; GameCanvas.loadmap.mapType=new int[900];
        GameScreen.player.x=400; GameScreen.player.y=200;
        int escortQueries=net.queryCalls, escortShops=net.shopCalls;
        live.toggle(4200); live.tick(beast,4200);
        check(live.engine.state==TradeEngine.TO_SELL && live.view.cargo==6,
            "Paused escort inside buying market resumes escort with partial cargo");
        check(net.queryCalls==escortQueries && net.shopCalls==escortShops,"Resumed escort never returns to merchant stock/shop");
        check(GameScreen.player.xStopMove==beast.x && GameScreen.player.yStopMove==beast.y,
            "Resumed escort first walks back to distant animal");
        live.toggle(4300); live.tick(null,4400); live.toggle(4500); live.tick(null,4500);
        check(GameScreen.player.posTransRoad==null && net.queryCalls==escortQueries,
            "Missing animal after re-enable stops movement and does not query market");
        live.tick(beast,4600);
        check(live.engine.state==TradeEngine.TO_SELL && GameScreen.player.xStopMove==beast.x,
            "Animal appearing later resumes reunion rather than independent travel");
        live.toggle(4700);
        GameCanvas.loadmap.idMap=25; GameCanvas.loadmap.nameMap="Rừng Chết";
        live.toggle(4800); live.tick(beast,4800); live.tick(beast,6500);
        check(live.engine.state==TradeEngine.TO_SELL && GameScreen.player.xStopMove==beast.x
            && GameScreen.player.yStopMove==beast.y && net.queryCalls==escortQueries,
            "Off/on outside market also reunites player and animal before following selling route");
        String usePrompt="Bạn có muốn sử dụng không ? Thời gian đi buôn còn lại : 4m : 3s";
        check(TradeSignals.animalUseConfirmation(usePrompt),"Recognize exact screenshot animal-use confirmation");
        check(!TradeSignals.animalUseConfirmation("Bạn có muốn sử dụng không ?"),"Generic item prompt alone is not trade confirmation");
        live=start(); live.toggle(2200);
        GameCanvas.loadmap.idMap=25; GameCanvas.loadmap.nameMap="Rừng Chết";
        beast=new MainObject(); beast.ID=70; beast.name="Lừa vàng"; beast.hp=10; beast.maxHp=100; beast.x=200; beast.y=200;
        live.toggle(3000); live.tick(beast,3000);
        dialog=new Object(); GameCanvas.currentDialog=dialog; buttons=options("Ok"); ok=(iCommand)buttons.elementAt(0);
        live.dialog(dialog,usePrompt,buttons); live.tick(beast,3200);
        check(ok.calls==1 && live.engine.state==TradeEngine.WAIT_REPLACE,"Auto OK screenshot prompt after replacement item use");
        live.dialog(dialog,usePrompt,buttons); live.tick(beast,3300);
        check(ok.calls==1,"Duplicate confirmation is not performed twice");
        Object replaceStep=new Object(); GameCanvas.currentDialog=replaceStep;
        mVector replaceStepButtons=options("OK");
        live.dialog(replaceStep,replacementPrompt.replace(' ', '\n'),replaceStepButtons); live.tick(beast,3400);
        check(((iCommand)replaceStepButtons.elementAt(0)).calls==1,"Confirm second replacement step after first item-use dialog");
        live.tick(null,3500); beast.ID=71; beast.hp=100; live.tick(beast,4000);
        live.toggle(4100); live.toggle(4200);
        // A server cargo report explicitly confirms the empty animal outside market.
        live.view.cargo=0; int emptyUses=net.potionCalls;
        live.tick(beast,4200);
        check(live.engine.state==TradeEngine.WAIT_RECALL && net.potionCalls==emptyUses+1,
            "Empty animal outside market is cancelled with its item instead of escorted");
        dialog=new Object(); GameCanvas.currentDialog=dialog; buttons=options("Ok"); ok=(iCommand)buttons.elementAt(0);
        live.dialog(dialog,usePrompt,buttons); live.tick(beast,4400);
        check(ok.calls==1,"Empty-animal cancellation also confirms exact trip-use prompt");
        live.tick(null,4600);
        check(live.engine.state==TradeEngine.TO_MARKET && live.engine.market==1 && live.view.cargo==0,
            "After cancellation return to configured market without rotating or selling");
        GameCanvas.loadmap.idMap=19; GameCanvas.loadmap.nameMap="Cửa Biển";
        live.tick(null,5000);
        switchingMerchant=MainObject.get_Object(5,(byte)2); switchingMerchant.strChatPopup="Kim Cương: 5, Rubi: 5, Saphia: 5";
        live.chat(switchingMerchant); live.tick(null,5200);
        check(live.engine.state==TradeEngine.SUMMON,"Normal buying trip restarts on arriving at configured market");
        // Real cargo-window response, rather than assigning view.cargo in the test.
        live=start(); live.toggle(2200);
        GameCanvas.loadmap.idMap=25; GameCanvas.loadmap.nameMap="Rừng Chết";
        beast=new MainObject(); beast.ID=901; beast.name="Lừa vàng"; beast.x=200; beast.y=200;
        int cargoRequests=net.menuCalls; emptyUses=net.potionCalls;
        live.toggle(3000); live.tick(beast,3000);
        check(net.menuCalls==cargoRequests+1 && GameScreen.player.posTransRoad==null,
            "Unknown animal cargo requests real inventory and stops escort movement");
        check(!live.animalCargo("Ngựa trắng",0),"Unrelated mount inventory cannot mark trade animal empty");
        live.tick(beast,4000);
        check(net.menuCalls==cargoRequests+1 && net.potionCalls==emptyUses,
            "Waiting for inventory neither spams request nor cancels unverified animal");
        check(live.cargoShop(new mVector(),13,11,"Lừa vàng"),"Accept empty cargo window for requested trade animal");
        live.tick(beast,4200);
        check(live.view.cargo==0 && live.engine.state==TradeEngine.WAIT_RECALL && net.potionCalls==emptyUses+1,
            "Real empty inventory cancels animal instead of escorting it");
        live.tick(null,4400);
        check(live.engine.state==TradeEngine.TO_MARKET && live.engine.market==1,"Empty inventory cancellation returns to configured buying market");
        live.toggle(4500); beast.ID=902; live.toggle(4600); live.tick(beast,4600);
        check(live.cargoShop(sixCargo(),13,11,"Lừa vàng"),"Receive nonempty animal inventory");
        live.tick(beast,4800);
        check(live.view.cargo==6 && live.engine.state==TradeEngine.TO_SELL,"Nonempty inventory permits escort without cancelling animal");
        live=start();
        beast=new MainObject(); beast.ID=950; beast.name="Lừa vàng"; beast.x=200; beast.y=200;
        live.tick(beast,3000);
        GameScreen.ObjFocus=beast;
        GameCanvas.menu2.isShowMenu=true;
        live.menu(options("Hành trang thú"),0,-56,"");
        live.tick(beast,3200);
        check(GameCanvas.menu2.isShowMenu,"Auto does not close manually opened animal menu");
        live.engine.state=TradeEngine.STOCK;
        check(live.cargoShop(sixCargo(),13,11,"Lừa vàng"),"Manual owned-animal inventory feeds authoritative cargo");
        check(!live.hasHiddenShop(),"Manual animal inventory remains visible during auto");
        live.tick(beast,3400);
        check(live.view.cargo==6 && live.view.full && live.engine.state==TradeEngine.TO_SELL,
            "Full server inventory leaves stock-check state and starts escort");
        check(live.intent==0,"Full inventory clears outstanding merchant actions");
        int fullBuys=net.buyCalls;
        live.tick(beast,3600);
        check(net.buyCalls==fullBuys,"No extra purchase after inventory confirms full");
        GameScreen.ObjFocus=null;
        // Exact screenshot menu: focus is on the cow, header text may be null.
        live=start(); live.toggle(2200);
        GameCanvas.loadmap.idMap=37; GameCanvas.loadmap.nameMap="Rừng Cao Nguyên";
        beast=new MainObject(); beast.ID=980; beast.name="Lừa vàng"; beast.x=200; beast.y=200;
        GameScreen.ObjFocus=beast;
        Thread_More.LoadMap.vecPointChange.removeAllElements();
        Model.Point exit=new Model.Point(); exit.name="Đồi Cát"; exit.x=48; exit.y=48;
        Thread_More.LoadMap.vecPointChange.addElement(exit);
        live.toggle(3000); live.tick(beast,3000);
        int autoMenus=net.menuCalls;
        mVector animalMenu=options("Hành trang");
        animalMenu.addElement(new iCommand("Tăng tốc")); animalMenu.addElement(new iCommand("Bảo hộ"));
        animalMenu.addElement(new iCommand("Hồi máu"));
        GameCanvas.menu2.isShowMenu=true;
        live.menu(animalMenu,0,-56,null); live.tick(beast,3200);
        check(net.menuCalls==autoMenus+1 && net.lastIndex==0 && !GameCanvas.menu2.isShowMenu,
            "Automatic cargo request selects Hanh trang despite animal focus and null header");
        check(live.cargoShop(sixCargo(),13,11,"Lừa vàng") && live.hasHiddenShop(),
            "Automatically requested inventory remains in background even with animal focus");
        live.takeHiddenShop(); live.tick(beast,3400); live.tick(beast,5000);
        check(live.view.cargo==6 && live.engine.state==TradeEngine.TO_SELL && GameScreen.player.posTransRoad!=null,
            "Inventory response leads animal toward Doi Cat instead of reopening animal menu");
        live.tick(beast,10000);
        check(net.menuCalls==autoMenus+1,"Known cargo does not repeat inventory menu checks");
        live=start(); live.toggle(2200);
        GameCanvas.loadmap.idMap=37; GameCanvas.loadmap.nameMap="Rừng Cao Nguyên";
        live.toggle(3000); live.tick(beast,3000);
        live.manualInput(); autoMenus=net.menuCalls;
        GameCanvas.menu2.isShowMenu=true;
        live.menu(animalMenu,0,-56,null); live.tick(beast,3200); live.tick(beast,9000);
        check(GameCanvas.menu2.isShowMenu && net.menuCalls==autoMenus,
            "Actual user input takes ownership and menu is not clicked or reopened by auto");
        GameScreen.ObjFocus=null;
        live=start(); live.toggle(2200);
        GameCanvas.loadmap.idMap=8; GameCanvas.loadmap.nameMap=TradeConfig.SELLER_MAP;
        GameCanvas.loadmap.mapW=20; GameCanvas.loadmap.mapH=20; GameCanvas.loadmap.mapType=new int[400];
        graham=new MainObject(); graham.ID=77; graham.typeObject=2; graham.name="Graham";
        graham.x=200; graham.y=200; GameScreen.Vecplayers.addElement(graham);
        beast=new MainObject(); beast.ID=990; beast.name="Lừa vàng"; beast.x=200; beast.y=200;
        live.toggle(3000); live.tick(beast,3000);
        live.view.cargo=3; live.engine.state=TradeEngine.TO_SELL;
        GameScreen.player.x=165; graham.x=200; beast.x=130;
        live.tick(beast,4000);
        check(!live.view.atSeller && live.engine.state==TradeEngine.TO_SELL,
            "Player is close to NPC and beast but beast is 70px from NPC: do not sell");
        // Secure the leash on approach, then leave the cow just outside sale range.
        GameScreen.player.x=120; graham.x=200; beast.x=120;
        live.tick(beast,5000);
        GameScreen.player.x=200; beast.x=145;
        live.tick(beast,5200);
        check(!live.view.atSeller && GameScreen.player.posTransRoad!=null && GameScreen.player.xStopMove==145,
            "At Graham with cow 55px away, walk back to cow instead of stopping at NPC forever");
        beast.x=200; live.tick(beast,5400); live.tick(beast,5600);
        check(live.engine.state==TradeEngine.WAIT_SALE && live.intent==LiveTrade.SELL,
            "Once cow is close, automatically request selling menu");
        live.menu(options("Bán hàng"),2,77,""); live.tick(beast,5800);
        check(net.lastNpc==77 && net.lastIndex==0,"Automatically select Graham sale option after reunion");
        // Verify spamming purchases every 1s ignoring "Thao tác quá nhanh" until full cargo or depleted
        live=start(); live.tick(beast,3000); live.tick(beast,4000);
        buys=net.buyCalls;
        live.shop(goods(),13,11); live.tick(beast,5000);
        check(net.buyCalls==buys+1,"First purchase sent at 5000");

        // Server sends "Thao tác quá nhanh" as a dialog at 5200
        Object fastDialog=new Object(); GameCanvas.currentDialog=fastDialog;
        live.dialog(fastDialog,"Bạn thao tác quá nhanh, xin chờ giây lát!",options("Ok"));
        check(live.hideDialog(fastDialog),"Thao tác quá nhanh dialog is hidden");
        live.tick(beast,5300);
        check(GameCanvas.currentDialog==null,"Thao tác quá nhanh dialog is dismissed");
        check(live.engine.state==TradeEngine.WAIT_BUY,"Thao tác quá nhanh does not fail or stop engine");

        // At 5999 (< 1s cadence), no new purchase sent
        live.tick(beast,5999);
        check(net.buyCalls==buys+1,"No spam purchase before 1s cadence");

        // At 6000 (1s cadence), bot ignores "quá nhanh" and spams purchase!
        live.tick(beast,6000);
        check(net.buyCalls==buys+2,"Bot spams buy request after 1s despite previous quá nhanh");

        // Server sends "Thao tác quá nhanh" as serverText at 6200
        live.serverText("Thao tác quá nhanh");
        live.tick(beast,6300);
        check(live.engine.state==TradeEngine.WAIT_BUY,"ServerText quá nhanh ignored and engine keeps running");

        // At 7000 (1s cadence), bot spams again!
        live.tick(beast,7000);
        check(net.buyCalls==buys+3,"Bot spams buy request again at 7000");

        // This one succeeds: server sends "Mua thành công" at 7200
        live.serverText("Mua thành công 1 Kim Cương");
        live.tick(beast,7300);
        check(live.view.cargo==1,"Purchase succeeds and increments cargo to 1");

        // At 8000, bot continues spamming for remaining capacity
        live.tick(beast,8000);
        check(net.buyCalls==buys+4,"Bot continues spamming for remaining capacity");

        // Simulate reaching full capacity via full message
        live.serverText("Không thể mua thêm, chở đã đầy");
        live.tick(beast,8200);
        check(live.engine.state==TradeEngine.TO_SELL && live.view.full,"Full cargo message stops spamming and begins escort");

        // Ensure no further purchases are sent once full
        live.tick(beast,9000); live.tick(beast,10000);
        check(net.buyCalls==buys+4,"No more purchases sent after full cargo");

        // --- Regression test 1: Player dies with AC_DIE / timedie / hp<=0 and revives ---
        GameScreen.player.hp=100; GameScreen.player.Action=0; GameScreen.player.timedie=0;
        live.tick(beast,11000);
        check(!live.view.dead,"Player is initially alive");

        // Player dies via mob / cuop may (Action=4, timedie>0, hp=0)
        GameScreen.player.hp=0; GameScreen.player.Action=4; GameScreen.player.timedie=11500;
        live.tick(beast,11500);
        check(live.view.dead,"Dead player recognized via Action=4 and hp<=0");
        check(live.engine.state==TradeEngine.TO_SELL,"Engine retains state while waiting to revive");

        // Verify resurrectVillage sends gohome(0) and dismisses any death dialog
        Object deathDialog=new Object(); GameCanvas.currentDialog=deathDialog;
        int revivesBefore=net.revives;
        live.resurrectVillage();
        check(net.revives==revivesBefore+1 && net.lastGohomeType==0,"resurrectVillage sends gohome(0)");
        check(GameCanvas.currentDialog==null,"resurrectVillage dismisses death dialog");

        // --- Regression test 2: Escort recovery after death in TO_SELL ---
        // Player was in "Sa Mạc" escorting beast with 6 cargo
        GameCanvas.loadmap.idMap=30; GameCanvas.loadmap.nameMap="Sa Mạc";
        GameScreen.player.hp=100; GameScreen.player.Action=0; GameScreen.player.timedie=0;
        beast.x=150; beast.y=150;
        live.view.cargo=6; live.view.full=true;
        live.tick(beast,12000);
        check(live.lastBeastMap.equals("Sa Mạc"),"LiveTrade records last beast map");

        // Player dies and respawns at "Thành Phố Kho Báu"
        GameCanvas.loadmap.idMap=33; GameCanvas.loadmap.nameMap="Thành Phố Kho Báu";
        // Beast is not in "Thành Phố Kho Báu"
        live.tick(null,13000);
        check(!live.view.animalMissing,"Animal missing requires 2s absence grace");
        live.tick(null,15500);
        check(live.view.animalMissing,"Animal missing detected after 2s absence");
        check(live.engine.state==TradeEngine.RECOVER,"Engine enters RECOVER state");
        check(live.view.cargo==6,"Cargo is preserved during recovery");

        // After 20 seconds, while still traveling in "Thành Phố Kho Báu" / "Khu phía Tây" (!inRecoveryMap)
        // the bot MUST NOT abandon the trip or reset to TO_MARKET!
        check(!live.view.inRecoveryMap,"Player is not yet in recovery map");
        live.tick(null,36000); // 21 seconds later!
        check(live.engine.state==TradeEngine.RECOVER,"Trip is NOT abandoned after 20s while traveling to recovery map");
        check(live.view.cargo==6,"Cargo is NOT wiped after 20s");
        live.tick(null,60000); // 45 seconds later!
        check(live.engine.state==TradeEngine.RECOVER,"Trip is still active after 45s");

        // Now player arrives back in "Sa Mạc"
        GameCanvas.loadmap.idMap=30; GameCanvas.loadmap.nameMap="Sa Mạc";
        live.tick(null,65000);
        check(live.view.inRecoveryMap,"Player arrived in recovery map");
        check(live.engine.state==TradeEngine.RECOVER,"Searching in recovery map");

        // Player reunites with the beast
        live.tick(beast,66000);
        check(live.view.animal,"Beast is reunited");
        check(live.engine.state==TradeEngine.TO_SELL,"Engine returns to TO_SELL escorting to Graham");
        check(live.view.cargo==6,"Cargo 6 is preserved and trip continues!");

        // --- Regression test 3: Replacing animal summons new beast FIRST before picking up gems ---
        beast.hp=10; beast.maxHp=100;
        live.tick(beast,70000);
        check(live.engine.state==TradeEngine.WAIT_REPLACE,"Low HP beast initiates replacement via WAIT_REPLACE");

        // Old beast disappears; gems dropped on map; player currently without animal
        MainObject gemDrop=new MainObject(); gemDrop.ID=888; gemDrop.name="Kim Cương"; gemDrop.typeObject=4;
        gemDrop.x=GameScreen.player.x+30; gemDrop.y=GameScreen.player.y;
        GameScreen.Vecplayers.addElement(gemDrop);
        int itemPickupsBefore=net.getItemMapCalls;
        int potionsBefore=net.potionCalls;

        // While animal is missing, bot MUST summon new animal and MUST NOT prematurely try to pick up gems
        live.tick(null,70500);
        check(live.engine.state==TradeEngine.WAIT_NEW,"Enters WAIT_NEW after old animal removed");
        check(live.view.gemDrops,"Gem drop detected on ground");
        check(net.getItemMapCalls==itemPickupsBefore,"Does NOT attempt gem pickup without animal");
        check(net.potionCalls>potionsBefore,"Calls Use_Potion to summon new trade beast");

        // New beast spawns in the world
        MainObject newBeast=new MainObject(); newBeast.ID=777; newBeast.name="Lừa vàng"; newBeast.hp=100; newBeast.maxHp=100;
        newBeast.x=GameScreen.player.x; newBeast.y=GameScreen.player.y;

        // With new beast present, bot now proceeds to pick up dropped gems
        live.tick(newBeast,71000);
        check(net.getItemMapCalls==itemPickupsBefore+1,"Bot calls Get_Item_Map for dropped gem once beast is spawned");
        check(Player.autoItem!=null,"autoItem active during pickup");

        // Remove drop once picked up
        GameScreen.Vecplayers.removeElement(gemDrop);
        live.tick(newBeast,73500);
        check(!live.view.gemDrops,"No more gem drops on map");
        check(live.engine.state==TradeEngine.WAIT_NEW && live.view.cargo==-1,"Disappearing drops cannot fabricate recovered cargo");
        live.animalCargo("Hành trang",6);
        live.tick(newBeast,74000);
        check(live.engine.state==TradeEngine.TO_SELL,"WAIT_NEW transitions back to TO_SELL after gem pickup");
        check(live.view.cargo==6,"Server confirms cargo after gem pickup");

                // --- Regression test 4: Disconnect during recovery does not reset to TO_MARKET ---
        live=start();
        GameCanvas.loadmap.idMap=24; GameCanvas.loadmap.nameMap="Nghĩa địa cát";
        beast=new MainObject(); beast.ID=999; beast.name="p0vantai0o"; beast.x=150; beast.y=160;
        live.view.cargo=6; live.engine.state=TradeEngine.TO_SELL;
        live.tick(beast,80000);
        check(live.lastBeastMap.equals("Nghĩa địa cát") && live.lastBeastX==150,"Last beast map and position captured");
        // Player dies and respawns in town
        live.tick(null,80500);
        live.tick(null,83000);
        check(live.engine.state==TradeEngine.RECOVER,"Player enters recovery mode");
        // Disconnect occurs while running back
        live.disconnected();
        check(!live.view.connected,"Disconnected flagged");
        // Reconnect in town without animal
        GameCanvas.loadmap.idMap=33; GameCanvas.loadmap.nameMap="Thành Phố Kho Báu";
        live.tick(null,84000);
        check(live.engine.state==TradeEngine.RECOVER,"Reconnecting preserves recovery trip instead of resetting to TO_MARKET");
        check(live.hasLostTrip(),"Port reports lost trip active");

        // --- Regression test 5: Restart / out game during recovery restores trip via TradeRecovery ---
        TradeRecovery.save(GameScreen.player.ID,"Nghĩa địa cát",150,160,-1,1,0,2,85000);
        LiveTrade freshLive=new LiveTrade(new TradeConfig(),new MapGraph()); freshLive.toggle(85000);
        check(freshLive.engine.state==TradeEngine.RECOVER,"Fresh start restores recovery mode from saved record");
        check(freshLive.lastBeastMap.equals("Nghĩa địa cát") && freshLive.lastBeastX==150,"Saved beast map restored");

        // --- Regression test 6: Empty trade beast with owner-formatted name is recalled and begins new trip ---
        GameCanvas.loadmap.idMap=24; GameCanvas.loadmap.nameMap="Nghĩa địa cát";
        freshLive.tick(beast,86000);
        check(freshLive.engine.state==TradeEngine.TO_SELL,"Reunited beast transitions to TO_SELL");
        check(freshLive.view.cargo<0,"Cargo initially unknown before check");
        // Server sends cargo shop with 0 items and 'Hành trang' title
        check(freshLive.cargoShop(new mVector(),13,11,"Hành trang"),"Accept cargo shop with Hanh trang title");
        freshLive.tick(beast,87000);
        check(freshLive.view.cargo==0,"Cargo recognized as 0");
        check(freshLive.engine.state==TradeEngine.WAIT_RECALL,"Empty beast triggers WAIT_RECALL");
        freshLive.tick(null,88000);
        check(freshLive.engine.state==TradeEngine.TO_MARKET,"Recalled empty beast transitions to TO_MARKET for fresh trip");

        // --- Regression test 7: Graham sale retries every 2.5s without getting stuck ---
        live=start();
        live.engine.state=TradeEngine.TO_SELL;
        GameCanvas.loadmap.idMap=77; GameCanvas.loadmap.nameMap="Thung Lũng Kỳ Bí";
        MainObject sellerNpc=new MainObject(); sellerNpc.ID=77; sellerNpc.typeObject=2; sellerNpc.name="Graham";
        sellerNpc.x=200; sellerNpc.y=200;
        GameScreen.Vecplayers.removeAllElements(); GameScreen.Vecplayers.addElement(sellerNpc);
        beast=new MainObject(); beast.ID=555; beast.name="Bò vàng"; beast.x=200; beast.y=200; beast.maxHp=100; beast.hp=100;
        GameScreen.player.x=200; GameScreen.player.y=200;
        live.view.cargo=24; live.view.full=true;
        live.tick(beast,90000); live.tick(beast,91000);
        check(live.engine.state==TradeEngine.WAIT_SALE && live.intent==LiveTrade.SELL,"Enter sale state at Graham");
        live.menu(options("Bán hàng"),2,77,"");
        live.tick(beast,92000);
        int menuCallsBefore=net.menuCalls;
        // Without response for 2600ms, pump resets saleSent and retries querying Graham
        live.tick(beast,94600);
        check(net.lastNpc==77,"Graham menu queried again on attempt 2 without hanging");
        live.menu(options("Bán hàng"),2,77,"");
        live.tick(beast,94700);
        check(net.menuCalls==menuCallsBefore+1,"Sale option selected again for retry attempt 2");

        // --- Regression test 8: Spot revival with revive item, only return to village when empty ---
        Item.VecInvetoryPlayer.removeAllElements();
        Item reviveItem=new Item(); reviveItem.Id=99; reviveItem.itemName="Thánh giá"; reviveItem.numPotion=5;
        Item.VecInvetoryPlayer.addElement(reviveItem);
        check(live.hasReviveItem(),"Revive item detected in inventory");
        GameScreen.player.hp=0; GameScreen.player.Action=4;
        live.tick(beast,95000);
        check(live.view.dead && live.view.hasReviveItem,"Dead with revive item recognized");
        // Spot resurrection called, gohome(1) invoked
        live.resurrect();
        check(net.revives>0 && net.lastGohomeType==1,"Spot revival sends gohome(1)");
        // Server responds with revive popup menu: 0: Dùng vật phẩm hồi sinh, 1: Hồi sinh với 2 Ngọc
        mVector reviveOptions=new mVector();
        reviveOptions.addElement(new iCommand("Dùng vật phẩm hồi sinh"));
        reviveOptions.addElement(new iCommand("Hồi sinh với 2 Ngọc"));
        int menuCallsBeforeRevive=net.menuCalls;
        live.menu(reviveOptions,1,0,"Menu");
        live.tick(beast,95100);
        check(net.menuCalls>menuCallsBeforeRevive && net.lastIndex==0,"Automatically select 'Dùng vật phẩm hồi sinh' option 0 from revive menu");
        // When revive item is removed, only then resurrectVillage sends gohome(0)
        Item.VecInvetoryPlayer.removeAllElements();
        check(!live.hasReviveItem(),"No revive items left");
        live.resurrectVillage();
        check(net.lastGohomeType==0,"Return to village only when out of revive items");

        // --- Regression test 9: Unrelated gem drops ignored when animal already has cargo ---
        live=start();
        live.engine.state=TradeEngine.TO_SELL;
        GameCanvas.loadmap.idMap=20; GameCanvas.loadmap.nameMap="Sa Mạc";
        beast=new MainObject(); beast.ID=8162; beast.name="Bò vàng"; beast.hp=300000; beast.maxHp=400000;
        beast.x=200; beast.y=200;
        GameScreen.player.x=200; GameScreen.player.y=200;
        live.view.cargo=24; live.view.full=true;
        // Drops from another player on the ground
        MainObject strangerGem=new MainObject(); strangerGem.ID=9999; strangerGem.name="Rubi"; strangerGem.typeObject=4;
        strangerGem.x=230; strangerGem.y=200;
        GameScreen.Vecplayers.removeAllElements();
        GameScreen.Vecplayers.addElement(strangerGem);
        int itemPickups=net.getItemMapCalls;
        live.tick(beast,96000);
        check(!live.view.gemDrops,"Foreign gem drops ignored because beast has cargo");
        check(net.getItemMapCalls==itemPickups,"Did not send Get_Item_Map for foreign gem");
        check(live.engine.state==TradeEngine.TO_SELL,"Continued escorting to Graham without interruption");

        // If animal replacement cannot recall animal (e.g. full inventory), don't fail, continue trip
        beast.hp=10;
        live.tick(beast,97000); // Triggers REPLACE
        live.tick(beast,98000); // Triggers WAIT_REPLACE with deadline=now+10000
        check(live.engine.state==TradeEngine.WAIT_REPLACE,"Enters WAIT_REPLACE when low HP");
        // Exceed deadline for recall (now >= 108000)
        live.tick(beast,120000);
        check(live.engine.state==TradeEngine.TO_SELL,"WAIT_REPLACE timeout with cargo intact safely resumes TO_SELL");

        // --- Regression test 10: Graham sale confirmation dialog confirms [Ok] and tracks 960000 gold ---
        live=start();
        live.engine.state=TradeEngine.TO_SELL;
        GameCanvas.loadmap.idMap=77; GameCanvas.loadmap.nameMap=TradeConfig.SELLER_MAP;
        MainObject sellerGraham=new MainObject(); sellerGraham.ID=77; sellerGraham.typeObject=2; sellerGraham.name="Graham";
        sellerGraham.x=200; sellerGraham.y=200;
        GameScreen.Vecplayers.removeAllElements(); GameScreen.Vecplayers.addElement(sellerGraham);
        beast=new MainObject(); beast.ID=555; beast.name="Bò vàng"; beast.x=200; beast.y=200; beast.maxHp=100; beast.hp=100;
        GameScreen.player.x=200; GameScreen.player.y=200;
        live.view.cargo=24; live.view.full=true;
        live.tick(beast,130000); live.tick(beast,131000);
        check(live.engine.state==TradeEngine.WAIT_SALE,"Entered WAIT_SALE at Graham");
        live.menu(options("Bán hàng"),2,77,"");
        live.tick(beast,132000);
        // Graham pops up confirmation dialog with 960,000 gold
        Object grahamDialog=new Object(); GameCanvas.currentDialog=grahamDialog;
        mVector grahamButtons=options("Ok");
        grahamButtons.addElement(new iCommand("Hủy"));
        iCommand grahamOk=(iCommand)grahamButtons.elementAt(0);
        live.dialog(grahamDialog,"Bạn có muốn bán tất cả với giá 960000 Vàng ?",grahamButtons);
        live.tick(beast,133000);
        check(grahamOk.calls==1,"Confirmed Graham sale dialog button [Ok]");
        check(GameCanvas.currentDialog==null,"Graham sale dialog dismissed after confirmation");
        check(live.saleAmount==960000,"Extracted 960,000 gold sale amount from dialog text");
        // Graham finishes transaction
        live.serverText("Bán hàng thành công");
        live.tick(null,134000);
        check(live.earnings.gold==960000,"Trade earnings recorded 960,000 gold");
        check(live.engine.state==TradeEngine.TO_MARKET,"Completed sale successfully loops back to market");

        // --- Regression test 11: Return to village when no spot-revive and run out to continue escorting beast ---
        live=start();
        live.engine.state=TradeEngine.TO_SELL;
        GameCanvas.loadmap.idMap=42; GameCanvas.loadmap.nameMap="Đèo hoang sơ";
        beast=new MainObject(); beast.ID=8526; beast.name="Bò vàng"; beast.hp=156000; beast.maxHp=400000;
        beast.x=500; beast.y=300;
        GameScreen.player.x=500; GameScreen.player.y=300;
        live.view.cargo=24; live.view.full=true;
        live.tick(beast,140000);
        // Player dies without any revive items
        Item.VecInvetoryPlayer.removeAllElements();
        GameScreen.player.hp=0; GameScreen.player.Action=4;
        live.tick(beast,140000);
        // Exceed revive delay (9-11s), initial attempt
        live.tick(beast,152000);
        // Retry period expires, falls back to resurrectVillage
        live.tick(beast,156500);
        check(net.lastGohomeType==0,"Return to village when having no spot revive items");
        // Player respawns alive in village
        GameScreen.player.hp=100000; GameScreen.player.Action=0;
        GameCanvas.loadmap.idMap=8; GameCanvas.loadmap.nameMap="Làng Sói Trắng";
        live.tick(null,157000);
        check(live.engine.state==TradeEngine.RECOVER,"State transitions to RECOVER to run out and find beast");
        check(live.view.cargo==24,"Cargo 24 is preserved while returning to beast");
        // Player navigates back and re-enters "Đèo hoang sơ"
        GameCanvas.loadmap.idMap=42; GameCanvas.loadmap.nameMap="Đèo hoang sơ";
        GameScreen.player.x=beast.x; GameScreen.player.y=beast.y;
        live.tick(beast,158000);
        check(live.engine.state==TradeEngine.TO_SELL,"Reunited with beast in old map, continues TO_SELL");

        // --- Regression test 12: Sale retry and gold increase detection at Graham ---
        live=start();
        live.engine.state=TradeEngine.TO_SELL;
        GameCanvas.loadmap.idMap=77; GameCanvas.loadmap.nameMap=TradeConfig.SELLER_MAP;
        MainObject grahamNpc=new MainObject(); grahamNpc.ID=77; grahamNpc.typeObject=2; grahamNpc.name="Graham";
        grahamNpc.x=200; grahamNpc.y=200;
        GameScreen.Vecplayers.removeAllElements(); GameScreen.Vecplayers.addElement(grahamNpc);
        beast=new MainObject(); beast.ID=8526; beast.name="Bò vàng"; beast.x=200; beast.y=200; beast.maxHp=400000; beast.hp=212000;
        GameScreen.player.x=200; GameScreen.player.y=200; GameScreen.player.coin=50000;
        live.view.cargo=24; live.view.full=true;
        live.tick(beast,160000); live.tick(beast,161000);
        check(live.engine.state==TradeEngine.WAIT_SALE,"Enter WAIT_SALE at Graham attempt 1");
        // Graham sale dialog appears
        Object dia1=new Object(); GameCanvas.currentDialog=dia1;
        mVector btns1=options("Ok"); btns1.addElement(new iCommand("Hủy"));
        iCommand ok1=(iCommand)btns1.elementAt(0);
        live.dialog(dia1,"Bạn có muốn bán tất cả với giá 480000 Vàng ?",btns1);
        live.tick(beast,162000);
        check(ok1.calls==1,"Confirmed first sale dialog");
        // Server ignores sale (beast still has 24 cargo, gold unchanged at 50000)
        live.tick(beast,163000);
        // Exceed fast sale timeout (4000ms), engine retries without hanging 20s
        live.tick(beast,166000);
        check(live.engine.state==TradeEngine.TO_SELL,"Unconfirmed sale timed out quickly and returned to TO_SELL");
        // Next tick re-enters SELL and starts attempt 2
        live.tick(beast,167500); live.tick(beast,167800);
        check(live.engine.state==TradeEngine.WAIT_SALE,"Entered WAIT_SALE for attempt 2");
        // Second attempt dialog
        Object dia2=new Object(); GameCanvas.currentDialog=dia2;
        mVector btns2=options("Ok"); btns2.addElement(new iCommand("Hủy"));
        iCommand ok2=(iCommand)btns2.elementAt(0);
        live.dialog(dia2,"Bạn có muốn bán tất cả với giá 480000 Vàng ?",btns2);
        live.tick(beast,168000);
        check(ok2.calls==1,"Confirmed second sale dialog");
        // Server awards gold to player and consumes trade beast
        GameScreen.player.coin=530000;
        live.tick(null,169000);
        check(live.engine.state==TradeEngine.TO_MARKET,"Gold increase confirms sale and loops back to market");
        check(live.earnings.gold==480000,"Earnings recorded gold gained from sale");

        live=start(); live.engine.state=TradeEngine.TO_SELL;
        GameCanvas.loadmap.idMap=20; GameCanvas.loadmap.nameMap="Sa Mạc";
        GameScreen.Vecplayers.removeAllElements();
        beast=new MainObject(); beast.ID=9100; beast.name="Bò vàng";
        beast.x=200; beast.y=200; beast.hp=100; beast.maxHp=100;
        live.view.cargo=24; live.view.full=true;
        int saves=Model.CRes.saveCalls;
        for (int n=0;n<2000;n++) live.tick(beast,200000+n*5L);
        check(Model.CRes.saveCalls-saves==2,"Two thousand update ticks persist recovery only twice over ten seconds: "+(Model.CRes.saveCalls-saves));
        live.tick(null,210000);
        check(Model.CRes.saveCalls-saves==3,"Losing animal immediately persists last known location");
        System.out.println("PASS trade confirmation and purchasing: "+checks+" assertions");
    }
}
