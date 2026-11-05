import autotrade.*;
import java.io.*;
import java.util.Properties;
public final class LaunchConfigTest {
    private static void check(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        String[][] settings={{"slot","2"},{"market","1"},{"gem","2"},{"animal","1"},{"hp","3"},
            {"autoon","1"},{"reconnect","1"},{"automount","1"},{"rotate","0"},{"teleport","1"},
            {"hud","0"},{"visual","0"},{"fakename","0"},{"silentbuy","0"},
            {"proxy.enabled","1"},{"proxy.host","127.0.0.1"},{"proxy.port","1080"},{"proxy.user","fixture-user"},{"proxy.pass","fixture-proxy-secret"},
            {"server","Chiến Thần"},{"pass","fixture-đ-\"-\\-\n"}};
        StringBuffer wire=new StringBuffer();
        for (int i=0;i<settings.length;i++) {
            wire.append("phuc.").append(settings[i][0]).append('=');
            byte[] bytes=settings[i][1].getBytes("UTF-8");
            for(int j=0;j<bytes.length;j++) { String hex=Integer.toHexString(bytes[j]&255); if(hex.length()<2) wire.append('0'); wire.append(hex); }
            wire.append('\n');
        }
        Properties values=ManagerLauncher.read(new ByteArrayInputStream(wire.toString().getBytes("UTF-8")));
        for(int i=0;i<settings.length;i++) {
            String key="phuc."+settings[i][0];
            check(settings[i][1].equals(values.getProperty(key)),"Pipe preserves Unicode and special characters");
            System.setProperty(key,values.getProperty(key));
            check(settings[i][1].equals(org.microemu.app.util.MIDletSystemProperties.getProperty(key)),"Emulator can read inherited launch properties");
        }
        TradeConfig c=new TradeConfig(); LaunchConfig.apply(c);
        check(c.lowDetail,"Low-detail defaults on");
        System.setProperty("phuc.lowdetail","0"); LaunchConfig.apply(c);
        check(!c.lowDetail,"Manager can disable low-detail mode per account");
        check(c.charSlot==2 && c.market==1 && c.gem==2 && c.animal==1 && c.hpIndex==3,"Manager numeric configuration overrides defaults");
        check(c.resumeTrade && c.reconnect && c.autoMount && c.teleport && !c.rotateMarkets && !c.hud && !c.visual && !c.fakeName && !c.silentBuy,"Manager flags override RMS/defaults");
        // Simulate a different row overriding a profile that already has saved settings.
        System.setProperty("phuc.market","0"); System.setProperty("phuc.animal","2");
        System.setProperty("phuc.autoon","0"); System.setProperty("phuc.hud","1");
        System.setProperty("phuc.mountname","fixture mount");
        TradeConfig other=new TradeConfig(); other.market=2; other.resumeTrade=true;
        LaunchConfig.apply(other);
        check(other.market==0 && other.animal==2 && !other.resumeTrade && other.hud && "fixture mount".equals(other.mountName),
            "Second account overrides stale saved settings with its own manager values");
        check(c.market==1 && c.animal==1 && c.resumeTrade && !c.hud,"Applying another account does not change the first config");
        System.setProperty("phuc.slot","invalid"); c.gem=0; LaunchConfig.apply(c);
        check(c.gem==2,"Invalid field cannot suppress remaining configuration");
        check(LaunchConfig.serverIndex("chien than",null)==-1,"Wait for server list to load");
        check(LaunchConfig.serverIndex("chien than",new String[][]{{"Rồng"},{"Chiến Thần"}})==1,"Resolve requested server after list loads");
        System.out.println("PASS launch pipe, emulator property bridge, config and server selection");
    }
}
