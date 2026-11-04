package Main;
import Thread_More.LoadMap;
public class GameCanvas {
 public static int w=300,h=300;
 public static LoadMap loadmap=new LoadMap(); public static Object currentDialog; public static Menu menu2=new Menu();
 public static class Menu { public boolean isShowMenu; public CLib.mVector menuItems; public int IdNpc; public int menuSelectedItem; public void commandPointer(int n, int n2) {} }
 public static void end_Dialog(){currentDialog=null;} public static void addInfoChar(String text){}
}
