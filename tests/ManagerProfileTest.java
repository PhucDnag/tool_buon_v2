import java.io.*;
import org.microemu.app.Config;

/** Run in separate JVMs against the bundled emulator, like real game instances. */
public final class ManagerProfileTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home",new File(args[0]).getCanonicalPath());
        ManagerLauncher.initializeProfile(new String[]{"--resizableDevice","300","300","--id",args[1],"game.jar"});
        File expected=new File(args[0],".microemulator/"+args[1]).getCanonicalFile();
        File actual=Config.getConfigPath().getCanonicalFile();
        if (!expected.equals(actual)) throw new AssertionError("Emulator used shared profile: "+actual);
        // Repeat the late --id processing performed by Common after Main loads Config.
        Config.setEmulatorID(args[1]);
        if (!expected.equals(Config.getConfigPath().getCanonicalFile())) throw new AssertionError("Profile changed");
        File suite=new File(actual,"suite-KnightOnline Midlet Suite");
        suite.mkdirs();
        File marker=new File(suite,"isolation-test.dat");
        if ("write".equals(args[2])) {
            if (marker.exists()) throw new AssertionError("Inherited another account's data");
            DataOutputStream out=new DataOutputStream(new FileOutputStream(marker));
            out.writeUTF(args[1]); out.close();
        } else {
            DataInputStream in=new DataInputStream(new FileInputStream(marker));
            String value=in.readUTF(); in.close();
            if (!args[1].equals(value)) throw new AssertionError("Account data overwritten");
        }
        System.out.println("PASS emulator profile "+args[1]+" "+args[2]);
    }
}
