public final class VerifyPatched {
    public static void main(String[] args) throws Exception {
        String[] names={"CLib.Session_ME","CLib.Session_ME$MessageCollector","autotrade.ConnectionTrace","Main.GameCanvas","GameScreen.GameScreen","GameObjects.MainObject", "Model.Menu2",
            "netcommand.global.GlobalService","netcommand.global.GlobalLogicHandler","netcommand.global.ReadMessenge",
            "autotrade.LowDetailRenderer","GameScreen.LoginScreen","GameScreen.SelectionBridge","autotrade.ClientMod","autotrade.TradeEngine","autotrade.LiveTrade","autotrade.Navigator",
            "InterfaceComponents.infoShow","InterfaceComponents.MsgDialog","InterfaceComponents.TabShopNew","GameScreen.TabScreenNew"};
        for (int i=0;i<names.length;i++) {
            Class type=Class.forName(names[i],false,VerifyPatched.class.getClassLoader());
            type.getDeclaredMethods();
            System.out.println("Verified "+names[i]);
        }
    }
}
