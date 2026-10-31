package GameScreen;

/** Package-local field access keeps character reselection tied to the original character ID. */
public final class SelectionBridge {
    public static void select(SelectCharScreen screen,int index) {
        if (screen.isSend || index<0 || index>=SelectCharScreen.VecSelectChar.size()) return;
        // Match manual selection: cancel the original client's reconnect timer.
        netcommand.global.GlobalLogicHandler.timeReconnect=0;
        netcommand.global.GlobalLogicHandler.isDisConect=false;
        netcommand.global.GlobalLogicHandler.isMelogin=false;
        screen.selectChar=index;
        screen.autoSelect();
    }
}
