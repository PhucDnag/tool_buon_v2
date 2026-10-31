import autotrade.ManagerControl;
import java.io.*;

public final class ManagerControlTest {
    private static void check(boolean ok,String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static void write(File file,String value) throws Exception {
        FileWriter out=new FileWriter(file);
        try { out.write(value); } finally { out.close(); }
    }
    public static void main(String[] args) throws Exception {
        File file=File.createTempFile("manager-control-", ".cmd",new File("build"));
        try {
            ManagerControl control=new ManagerControl(), other=new ManagerControl();
            check(control.poll(null,0)==-1,"Standalone clients need no mailbox");
            write(file,"0:first");
            check(control.poll(file.getPath(),1000)==0,"Read stop command");
            check(control.poll(file.getPath(),1300)==-1,"Do not replay stop after in-game resume");
            write(file,"1:second");
            check(control.poll(file.getPath(),1400)==-1,"Throttle polling");
            check(control.poll(file.getPath(),1600)==1,"Read start command");
            write(file,"1:third");
            check(control.poll(file.getPath(),1900)==1,"Repeated explicit start has unique token");
            write(file,"garbage");
            check(control.poll(file.getPath(),2200)==-1,"Ignore malformed commands");
            check(other.poll(file.getPath()+".missing",2200)==-1,"Other session cannot receive this command");
            write(file,"0:fourth");
            check(control.poll(file.getPath(),2500)==0,"Recover after invalid command");
            System.out.println("PASS manager control: 9 assertions");
        } finally { file.delete(); }
    }
}
