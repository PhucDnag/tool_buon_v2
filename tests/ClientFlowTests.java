import autotrade.*;
import Main.GameCanvas;
import GameScreen.GameScreen;
import GameObjects.*;
import InterfaceComponents.iCommand;
import CLib.mVector;
import netcommand.global.GlobalService;

/** Simulated server messages exercise the actual live adapter, not a second implementation. */
public final class ClientFlowTests {
    static int checks;
    static void check(boolean value,String text){checks++;if(!value)throw new AssertionError(text);}
    static MainObject npc(int id,String name){MainObject o=new MainObject();o.ID=id;o.typeObject=2;o.name=name;o.x=200;o.y=200;return o;}
    static mVector menu(String text){mVector v=new mVector();v.addElement(new iCommand(text));return v;}
    public static void main(String[] args) {
        GameCanvas.loadmap.idMap=1;GameCanvas.loadmap.nameMap=TradeConfig.MARKETS[0];
        GameScreen.player.x=200;GameScreen.player.y=200;
        MainObject buyer=npc(5,"Thương nhân");GameScreen.Vecplayers.addElement(buyer);
        Item animal=new Item();animal.Id=50;animal.ItemCatagory=4;animal.numPotion=3;animal.itemName="Lừa vàng";Item.VecInvetoryPlayer.addElement(animal);
        MapGraph graph=new MapGraph();graph.observed(TradeConfig.MARKETS[0],1);graph.observed("Thung Lũng Kỳ Bí",8);
        graph.add(TradeConfig.MARKETS[0],"Thung Lũng Kỳ Bí",400,200);graph.add("Thung Lũng Kỳ Bí",TradeConfig.MARKETS[0],20,200);
        LiveTrade live=new LiveTrade(new TradeConfig(),graph); GlobalService net=GlobalService.gI();
        live.toggle(1000);live.tick(null,1000);
        check(net.queryCalls==1&&net.potionCalls==0,"Ask NPC without releasing animal");
        buyer.strChatPopup="Ta chỉ còn 3 viên kim cương, 0 viên riuby, 0 viên saphia";live.chat(buyer);live.tick(null,2000);
        check(net.potionCalls==1&&net.lastPotion==50,"Use observed gold animal item after stock reply");
        MainObject beast=new MainObject();beast.ID=70;beast.x=200;beast.y=200;
        live.tick(beast,3000);live.tick(beast,4000);
        check(net.shopCalls==1,"Open actual merchant menu");
        live.menu(menu("Mua Kim Cương"),0,99,"");live.tick(beast,5000);
        check(net.menuCalls==0,"Ignore menu from unrelated NPC");
        live.menu(menu("Mua Kim Cương"),2,5,"");live.tick(beast,6000);
        check(net.menuCalls==1&&net.lastNpc==5,"Select observed gem menu at expected NPC");
        live.tick(beast,7000);check(net.merchantActions==1,"No duplicate mutation while waiting");
        live.serverText("Mua thành công");live.tick(beast,8000);
        check(live.view.cargo==1,"Purchase acknowledgement updates cargo");
        live.tick(beast,9000);live.menu(menu("Mua Kim Cương"),2,5,"");live.tick(beast,10000);
        live.serverText("Thú không thể mua thêm");live.tick(beast,11000);
        check(live.engine.state==TradeEngine.TO_SELL,"Full message switches to escort");
        GameCanvas.loadmap.idMap=8;GameCanvas.loadmap.nameMap="Làng Sói Trắng";
        MainObject graham=npc(7,"Graham");GameScreen.Vecplayers.removeAllElements();GameScreen.Vecplayers.addElement(graham);
        live.tick(beast,11500);
        check(!live.view.atSeller && live.engine.state==TradeEngine.TO_SELL,"Map 8 with wrong name cannot be the selling destination even with Graham");
        GameCanvas.loadmap.idMap=77;GameCanvas.loadmap.nameMap="Thung Lũng Kỳ Bí";
        live.tick(beast,12000);live.tick(beast,13000);
        live.menu(menu("Bán hàng"),3,7,"");live.tick(beast,14000);
        check(net.lastNpc==7&&live.engine.state==TradeEngine.WAIT_SALE,"Sell only at Graham in Mystic Valley");
        Object dialog=new Object();GameCanvas.currentDialog=dialog;mVector commands=menu("Ok");iCommand ok=(iCommand)commands.elementAt(0);
        live.dialog(dialog,"Bạn muốn bán hàng?",commands);live.tick(beast,15000);
        check(ok.calls==1,"Confirm the pending trade dialog");
        Object dialogCo=new Object();GameCanvas.currentDialog=dialogCo;mVector commandsCo=menu("Có");iCommand co=(iCommand)commandsCo.elementAt(0);
        live.dialog(dialogCo,"Xác nhận bán hàng?",commandsCo);live.tick(beast,15500);
        check(co.calls==1,"Confirm dialog with 'Có'");
        live.menu(menu("Cất thú"),0,-56,"Lừa vàng - 6 viên Saphia");live.tick(beast,15600);
        check(live.view.cargo==6,"Beast menu text syncs cargo without hijacking menu");
        Object unrelated=new Object();GameCanvas.currentDialog=unrelated;mVector unrelatedCommands=menu("Ok");
        live.dialog(unrelated,"Bạn muốn xóa vật phẩm?",unrelatedCommands);live.tick(beast,16000);
        check(((iCommand)unrelatedCommands.elementAt(0)).calls==0,"Never accept unrelated destructive prompt");
        live.serverText("Bán hàng thành công");live.tick(null,17000);
        check(live.engine.state==TradeEngine.TO_MARKET&&live.view.cargo==0,"Sale acknowledged and animal gone loops to market");
        check(net.potionCalls==1,"No second summon in completed cycle");

        // Verify mount filtering: strictly mounts starting with ngựa, heo, lân (and kỳ lân)
        String[] realMounts = {"Ngựa trắng", "Ngựa xích thố", "Ngựa nâu", "Ngựa đen", "Ngựa chiến giáp", "Heo rừng", "Heo vàng", "Lân sư", "Kỳ lân", "Kỳ lân lửa"};
        for (int i=0;i<realMounts.length;i++) {
            Item m = new Item(); m.Id = 100+i; m.ItemCatagory = 4; m.itemName = realMounts[i];
            check(TradeConfig.isMount(m.Id, m.ItemCatagory, m.itemName), "Must accept real mount: " + realMounts[i]);
        }
        String[] noiseItems = {"Mề đay xạ thủ +2", "Phượng hoàng lửa", "Tuần lộc", "Cành xương rồng cấp 3", "Cành hoa hồng", "Thịt bò", "Trứng chim", "Lông phượng hoàng", "Đá nâng cấp", "Bí kíp võ công", "Sừng tuần lộc", "Da sói", "Răng sói", "Rương vàng", "Vé đi buôn"};
        for (int i=0;i<noiseItems.length;i++) {
            // Test ID 62 (which is horse ID in category 4 but medal ID in equipment category 3)
            Item medal = new Item(); medal.Id = 62; medal.ItemCatagory = 3; medal.itemName = noiseItems[i];
            check(!TradeConfig.isMount(medal.Id, medal.ItemCatagory, medal.itemName), "Must reject equipment/medal item: " + noiseItems[i]);
            Item noiseCat4 = new Item(); noiseCat4.Id = 62; noiseCat4.ItemCatagory = 4; noiseCat4.itemName = noiseItems[i];
            check(!TradeConfig.isMount(noiseCat4.Id, noiseCat4.ItemCatagory, noiseCat4.itemName), "Must reject non-mount item even if category 4: " + noiseItems[i]);
        }

        System.out.println("PASS live adapter cycle: "+checks+" assertions");
    }
}
