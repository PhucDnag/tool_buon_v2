import autotrade.*;
import GameObjects.*;
import GameScreen.*;
import Main.GameCanvas;
import CLib.mGraphics;
public class LowDetailTest {
 static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
 public static void main(String[] args){
  int[] cells={0,1,-1,0,2,0};
  check(LowDetail.walkable(cells,3,2,0,0),"Open cell");
  check(!LowDetail.walkable(cells,3,2,1,0)&&!LowDetail.walkable(cells,3,2,2,0),"Both collision markers excluded");
  check(!LowDetail.walkable(cells,3,2,-1,0)&&!LowDetail.walkable(null,3,2,0,0),"Invalid map never invents walkable cells");
  GameCanvas.loadmap.mapW=3;GameCanvas.loadmap.mapH=2;GameCanvas.loadmap.mapType=cells;
  GameScreen.player=new Player(); GameScreen.player.x=12;GameScreen.player.y=12;
  MainObject other=new MainObject(); other.typeObject=0;
  MainObject npc=new MainObject();npc.typeObject=0;npc.npcWireId=5;
  MainObject beast=new MainObject();beast.typeObject=1;
  GameScreen.Vecplayers.removeAllElements();GameScreen.Vecplayers.addElement(GameScreen.player);
  GameScreen.Vecplayers.addElement(other);GameScreen.Vecplayers.addElement(npc);GameScreen.Vecplayers.addElement(beast);
  mGraphics g=new mGraphics();g.translate(5,7);LowDetailRenderer.paint(g);
  check(g.rectangles==4,"Only four walkable squares, obstacles left blank");
  check(g.tx==5&&g.ty==7,"Restore graphics translation for dialogs and HUD");
  check(other.paintCalls==0&&GameScreen.player.paintCalls==1&&npc.paintCalls==1&&beast.paintCalls==1,"Hide other players, retain self NPC and beast");
  MainScreen.cameraMain.xCam=10000;LowDetailRenderer.paint(g);
  check(g.rectangles==4,"Offscreen map not rendered");
  System.out.println("PASS low-detail collision grid, viewport, actors and graphics state");
 }
}
