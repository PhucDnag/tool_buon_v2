import autotrade.ToolInput;
public final class ToolInputTest {
    private static void check(boolean ok,String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        check(!ToolInput.button(100,80,1),"Animal/HUD clicks must pass through");
        check(!ToolInput.button(200,160,2),"Zoomed animal/HUD clicks must pass through");
        check(ToolInput.button(110,12,1) && ToolInput.button(220,24,2),"Only visible tool button opens tool at either zoom");
        check(!ToolInput.button(97,10,1) && !ToolInput.button(182,10,1) && !ToolInput.button(110,24,1),"No oversized invisible button area");
        int[] nativeKeys={-6,-7,-5,48,53,12,20,84,116};
        for(int i=0;i<nativeKeys.length;i++) check(!ToolInput.shortcut(nativeKeys[i]),"Preserve native softkeys, select, zero and T");
        check(ToolInput.shortcut(77) && ToolInput.shortcut(109),"M remains tool shortcut");
        check(!ToolInput.available(true,true,false,false),"Animal menu owns its clicks and keys");
        check(!ToolInput.available(true,false,true,false),"Dialog owns its input");
        check(!ToolInput.available(true,false,false,true),"Typing in chat cannot open tool");
        check(!ToolInput.available(false,false,false,false),"Inventory screen owns input");
        check(ToolInput.available(true,false,false,false),"Tool available on unobstructed game screen");
        System.out.println("PASS tool input isolation");
    }
}
