import autotrade.*;

public class TestGraph {
    public static void main(String[] args) {
        MapGraph g = new MapGraph();
        g.seedDefaults();
        
        System.out.println("Edges count: " + g.size());
        
        String[] targets = {
            "Cong dia nguc",
            "Cua Bien",
            "Vach da cheo leo",
            "Thung Lung Ky Bi",
            "Lang Soi Trang",
            "Thanh Pho Kho Bau",
            "Thanh pho cang"
        };
        
        for (String t : targets) {
            MapGraph.Exit exit = g.next("Lang Soi Trang", t);
            if (exit == null) {
                System.out.println("Path from Lang Soi Trang to " + t + " -> NULL (NO ROUTE!)");
            } else {
                System.out.println("Path from Lang Soi Trang to " + t + " -> Next exit: " + exit.to + " at (" + exit.x + "," + exit.y + ")");
            }
        }
    }
}