package autotrade;

/** Tool shortcuts must not overlap native game interactions. */
public final class ToolInput {
    public static final int X=98,Y=4,WIDTH=84,HEIGHT=20;
    public static boolean button(int x,int y,int zoom) {
        if (zoom<=0) return false;
        x/=zoom; y/=zoom;
        return x>=X && x<X+WIDTH && y>=Y && y<Y+HEIGHT;
    }
    public static boolean shortcut(int key) { return key==77 || key==109; }
    public static boolean available(boolean inGame,boolean menu,boolean dialog,boolean chat) {
        return inGame && !menu && !dialog && !chat;
    }
}
