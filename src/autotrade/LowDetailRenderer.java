package autotrade;
import CLib.mGraphics;
import CLib.mFont;
import GameObjects.MainObject;
import GameScreen.GameScreen;
import GameScreen.MainScreen;
import Main.GameCanvas;
import Model.Point;
import Thread_More.LoadMap;

/** Draw only visible collision cells and actors; no map art or world effects. */
public final class LowDetailRenderer {
    public static void paint(mGraphics g) {
        int tx=g.getTranslateX(),ty=g.getTranslateY();
        int cx=MainScreen.cameraMain.xCam,cy=MainScreen.cameraMain.yCam;
        g.translate(-tx,-ty);
        try {
            g.setColor(0x101619); g.fillRect(0,0,GameCanvas.w,GameCanvas.h,false);
            int tile=Math.max(2,LoadMap.wTile),w=GameCanvas.loadmap.mapW,h=GameCanvas.loadmap.mapH;
            int left=Math.max(0,cx/tile),top=Math.max(0,cy/tile);
            int right=Math.min(w-1,(cx+GameCanvas.w)/tile),bottom=Math.min(h-1,(cy+GameCanvas.h)/tile);
            g.setColor(0x39504c);
            for (int y=top;y<=bottom;y++) for (int x=left;x<=right;x++)
                if (LowDetail.walkable(GameCanvas.loadmap.mapType,w,h,x,y))
                    g.drawRect(x*tile-cx+1,y*tile-cy+1,tile-2,tile-2,false);
            g.translate(-cx,-cy);
            boolean paintedSelf=false;
            for (int i=0;i<GameScreen.Vecplayers.size();i++) {
                MainObject object=(MainObject)GameScreen.Vecplayers.elementAt(i);
                if (object==null || object.isRemove || object.isStop
                        || !LowDetail.showObject(object==GameScreen.player,LiveTrade.isNpc(object),object.typeObject)
                        || object.x<cx-80 || object.x>cx+GameCanvas.w+80 || object.y<cy-80 || object.y>cy+GameCanvas.h+120) continue;
                object.paint(g);
                if (object==GameScreen.player) paintedSelf=true;
                if (LiveTrade.isNpc(object) && object.chat!=null && object.chat.strChat!=null) object.chat.paint(g);
            }
            if (!paintedSelf) GameScreen.player.paint(g);
            for (int i=0;i<LoadMap.vecPointChange.size();i++) {
                Point p=(Point)LoadMap.vecPointChange.elementAt(i);
                if (p.x<cx-24 || p.x>cx+GameCanvas.w+24 || p.y<cy-24 || p.y>cy+GameCanvas.h+24) continue;
                g.setColor(0xffcc55); g.drawRect(p.x-5,p.y-5,10,10,false);
                mFont.tahoma_7_yellow.drawString(g,p.name,p.x,p.y-16,2,false);
            }
            g.translate(cx,cy);
            mFont.tahoma_7_yellow.drawString(g,"HP: "+GameScreen.player.hp+"/"+GameScreen.player.maxHp,3,29,0,false);
        } finally { g.translate(tx-g.getTranslateX(),ty-g.getTranslateY()); }
    }
}
