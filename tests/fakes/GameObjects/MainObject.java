package GameObjects;
import GameScreen.GameScreen;
public class MainObject {
 public int ID,typeObject,x,y,hp=100,maxHp=100,vMax=2; public boolean isRemove; public String name="",strChatPopup;
 public int npcWireId=-999;
 public boolean isStop, tradeAnimal; public String owner="";
 public boolean isMonstervantieu(){return tradeAnimal;}
 public String getnameOwner(){return owner;}
 public int getIDnpc(){return npcWireId==-999?ID:npcWireId;}
 public boolean isNPC_server(){return npcWireId!=-999;}
 public boolean isNPC(){return typeObject==2||isNPC_server();}
 public static int getDistance(int x,int y,int a,int b){return (int)Math.sqrt((x-a)*(x-a)+(y-b)*(y-b));}
 public static MainObject get_Object(int id,byte type){for(int i=0;i<GameScreen.Vecplayers.size();i++){MainObject o=(MainObject)GameScreen.Vecplayers.elementAt(i);if(type==2&&o.getIDnpc()==id||o.ID==id&&o.typeObject==type)return o;}return null;}
}
