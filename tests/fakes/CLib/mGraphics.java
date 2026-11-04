package CLib;
public class mGraphics {
 public int tx,ty,rectangles;
 public int getTranslateX(){return tx;} public int getTranslateY(){return ty;}
 public void translate(int x,int y){tx+=x;ty+=y;}
 public void setColor(int color){} public void fillRect(int x,int y,int w,int h,boolean b){}
 public void drawRect(int x,int y,int w,int h,boolean b){rectangles++;}
}
