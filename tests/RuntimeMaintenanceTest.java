import autotrade.*;
import java.io.*;
public final class RuntimeMaintenanceTest {
    static void check(boolean ok,String reason) { if (!ok) throw new AssertionError(reason); }
    static void write(String path,String value) throws Exception { Writer out=new FileWriter(path); try { out.write(value); } finally { out.close(); } }
    public static void main(String[] args) throws Exception {
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
        write(control+".runtime","1:0"); maintenance.update(1000,control,earnings,false);
        check(maintenance.paint(1000) && !maintenance.paint(1001) && maintenance.paint(2000),"Hidden render throttled without stopping updates");
        BufferedReader reader=new BufferedReader(new FileReader(control+".stats"));
        check("12345:1".equals(reader.readLine()),"Telemetry snapshot"); reader.close();
        write(control+".runtime","0:0"); maintenance.update(2000,control,earnings,false);
        check(maintenance.paint(2001) && maintenance.paint(2002),"Visible rendering restored immediately");
        write(control+".runtime","broken"); maintenance.update(3000,control,earnings,false);
        check(maintenance.paint(3001),"Incomplete settings preserve prior state");
        maintenance.checkMemory(4000);
        maintenance.onMapChange(5000);
        System.out.println("PASS earnings parsing, telemetry, hidden/visible rendering");
    }
}
