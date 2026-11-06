import autotrade.*;
import java.io.*;
public final class RuntimeMaintenanceTest {
    static void check(boolean ok,String reason) { if (!ok) throw new AssertionError(reason); }
    static void write(String path,String value) throws Exception { Writer out=new FileWriter(path); try { out.write(value); } finally { out.close(); } }
    public static void main(String[] args) throws Exception {
        check(!RuntimeMaintenance.memoryPressure(4*1024*1024L,32*1024*1024L,256*1024*1024L),
            "Expandable heap does not trigger forced collection");
        check(RuntimeMaintenance.memoryPressure(4*1024*1024L,256*1024*1024L,256*1024*1024L),
            "Actual low heap headroom is detected");
        check(TradeEarnings.amount("Bạn nhận được 12.345 vàng") == 12345,"Vietnamese sale amount");
        check(TradeEarnings.amount("Nhận được 12,345 vàng") == 12345,"Thousands separator");
        check(TradeEarnings.amount("Bán hàng trên Bò vàng nhận được 960.000 vàng") == 960000,"Animal color is not the currency amount");
        check(TradeEarnings.amount("Bán Lừa vàng nhận được 12,345 xu") == 12345,"Skip animal color before receipt currency");
        check(TradeEarnings.amount("Bán thành công") == -1,"No invented receipt");
        check(TradeEarnings.amount("Nhận được 1.5 vàng") == -1,"Ambiguous amount rejected");
        check(TradeEarnings.amount("Nhận được 100 ngọc") == -1,"Other currency excluded");
        TradeEarnings earnings=new TradeEarnings(); earnings.complete(12345); earnings.complete(-1);
        check(earnings.gold==12345 && earnings.unmeasured==1,"Track unmeasured sales separately");
        File root=new File("build/maintenance-"+System.nanoTime()); root.mkdirs();
        String control=new File(root,"session.cmd").getPath();
        RuntimeMaintenance maintenance=new RuntimeMaintenance();
        long healthNow=System.currentTimeMillis();
        RuntimeMaintenance.sent(); maintenance.health(healthNow+1000,control,true);
        BufferedReader healthReader=new BufferedReader(new FileReader(control+".health"));
        String[] health=healthReader.readLine().split(":"); healthReader.close();
        check(Long.parseLong(health[1])>0 && health[2].equals("1"),"Track unanswered outbound traffic");
        RuntimeMaintenance.received(); maintenance.health(healthNow+2000,control,true);
        healthReader=new BufferedReader(new FileReader(control+".health"));
        check(healthReader.readLine().equals((healthNow+2000)+":0:1"),"Any received packet clears network stall timer"); healthReader.close();
        RuntimeMaintenance.sent(); maintenance.health(healthNow+3000,control,false);
        healthReader=new BufferedReader(new FileReader(control+".health"));
        check(healthReader.readLine().equals((healthNow+3000)+":0:0"),"Login and reconnect disable stall recovery"); healthReader.close();
        write(control+".runtime","1:0"); maintenance.update(1000,control,earnings,false);
        check(maintenance.paint(1000) && !maintenance.paint(1001) && maintenance.paint(2000),"Hidden render throttled without stopping updates");
        BufferedReader reader=new BufferedReader(new FileReader(control+".stats"));
        check("12345:1".equals(reader.readLine()),"Telemetry snapshot"); reader.close();
        write(control+".runtime","0:0"); maintenance.update(2000,control,earnings,false);
        check(maintenance.paint(2001) && maintenance.paint(2002),"Visible rendering restored immediately");
        write(control+".runtime","broken"); maintenance.update(3000,control,earnings,false);
        check(maintenance.paint(3001),"Incomplete settings preserve prior state");
        earnings.complete(480000); maintenance.update(4000,control,earnings,false);
        reader=new BufferedReader(new FileReader(control+".stats"));
        check("492345:1".equals(reader.readLine()),"New sale republishes cumulative manager gold snapshot"); reader.close();
        maintenance.checkMemory(4000);
        maintenance.onMapChange(5000);
        System.out.println("PASS earnings parsing, telemetry, hidden/visible rendering");
    }
}
