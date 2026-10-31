package Main;
import Thread_More.LoadMap;
public class GameCanvas {
 public static LoadMap loadmap=new LoadMap(); public static Object currentDialog; public static Menu menu2=new Menu();
 public static class Menu { public boolean isShowMenu; }
 public static void end_Dialog(){currentDialog=null;} public static void addInfoChar(String text){}
}
