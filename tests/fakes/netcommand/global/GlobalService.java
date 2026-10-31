package netcommand.global;
public class GlobalService {
 public int cargoQueries; public void requestinfoMount(byte type){cargoQueries++;}
 public static final GlobalService instance=new GlobalService(); public int potionCalls,buyCalls,menuCalls,merchantActions,queryCalls,shopCalls,revives,lastNpc,lastIndex,lastPotion,lastShop,lastItem,lastQuantity;
 public static GlobalService gI(){return instance;}
 public void chat_npc(byte id){queryCalls++; lastNpc=id;}
 public void Dynamic_Menu(short npc,byte menu,byte index){menuCalls++;if(npc==5||npc==7)merchantActions++;lastNpc=npc;lastIndex=index;}
 public void Use_Potion(short id){potionCalls++;lastPotion=id;}
 public void buy_item(byte shop,short item,short qty){buyCalls++;lastShop=shop;lastItem=item;lastQuantity=qty;}
 public void getlist_from_npc(byte id){shopCalls++;lastNpc=id;}
 public void gohome(byte type){revives++;}
 public void useMount(byte by){}
}
