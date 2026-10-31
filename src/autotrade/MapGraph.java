package autotrade;

import java.util.Vector;

/** Directed graph: reverse exits must be observed independently. */
public final class MapGraph {
    public static final class Exit {
        public final String from, to;
        public final int x, y;
        public Exit(String f, String t, int px, int py) { from=f; to=t; x=px; y=py; }
    }
    private final Vector edges = new Vector();
    private final Vector blocked = new Vector();
    private final Vector confirmed = new Vector();
    public void block(String from,String to) {
        String key=TradeStock.normalize(from).trim()+"\n"+TradeStock.normalize(to).trim();
        if (!blocked.contains(key)) blocked.addElement(key);
    }
    private boolean usable(Exit e) { return !blocked.contains(e.from+"\n"+e.to); }
    public boolean canUse(String from,String to) {
        return !blocked.contains(TradeStock.normalize(from).trim()+"\n"+TradeStock.normalize(to).trim());
    }
    public void clearBlocks() { blocked.removeAllElements(); }
    /** A complete live portal list supersedes cached and seeded outgoing links. */
    public void replaceExits(String from,String[] names,int[] x,int[] y) {
        String key=TradeStock.normalize(from).trim();
        if (!confirmed.contains(key)) confirmed.addElement(key);
        for (int i=edges.size()-1;i>=0;i--) if (((Exit)edges.elementAt(i)).from.equals(key)) edges.removeElementAt(i);
        for (int i=0;i<names.length;i++) if (names[i]!=null && names[i].length()>0) add(from,names[i],x[i],y[i]);
    }
    private final Vector observed = new Vector(), ids = new Vector();
    public void observed(String name,int id) {
        name=TradeStock.normalize(name).trim(); int index=observed.indexOf(name);
        if (index<0) { observed.addElement(name); ids.addElement(new Integer(id)); }
        else ids.setElementAt(new Integer(id),index);
    }
    public String nameFor(int id) {
        for (int i=0;i<ids.size();i++) if (((Integer)ids.elementAt(i)).intValue()==id) return (String)observed.elementAt(i);
        return null;
    }
    public String frontier(String start) {
        start=TradeStock.normalize(start).trim(); Vector queue=new Vector(); queue.addElement(start);
        for (int q=0;q<queue.size();q++) {
            String node=(String)queue.elementAt(q);
            if (!observed.contains(node)) return node;
            for (int i=0;i<edges.size();i++) {
                Exit e=(Exit)edges.elementAt(i);
                if (usable(e) && e.from.equals(node) && !queue.contains(e.to)) queue.addElement(e.to);
            }
        }
        return null;
    }
    public void write(java.io.DataOutputStream out) throws java.io.IOException {
        out.writeInt(observed.size());
        for (int i=0;i<observed.size();i++) { out.writeUTF((String)observed.elementAt(i)); out.writeInt(((Integer)ids.elementAt(i)).intValue()); }
        out.writeInt(edges.size());
        for (int i=0;i<edges.size();i++) { Exit e=(Exit)edges.elementAt(i); out.writeUTF(e.from); out.writeUTF(e.to); out.writeInt(e.x); out.writeInt(e.y); }
        out.writeInt(confirmed.size());
        for (int i=0;i<confirmed.size();i++) out.writeUTF((String)confirmed.elementAt(i));
    }
    public void read(java.io.DataInputStream in) throws java.io.IOException {
        int n=in.readInt(); if (n<0 || n>2000) throw new java.io.IOException("Invalid map count");
        for (int i=0;i<n;i++) observed(in.readUTF(),in.readInt());
        n=in.readInt(); if (n<0 || n>10000) throw new java.io.IOException("Invalid exit count");
        for (int i=0;i<n;i++) add(in.readUTF(),in.readUTF(),in.readInt(),in.readInt());
        // Older route caches have no authoritative-map section.
        if (in.available()>0) {
            n=in.readInt(); if (n<0 || n>2000) throw new java.io.IOException("Invalid confirmed map count");
            for (int i=0;i<n;i++) { String key=TradeStock.normalize(in.readUTF()).trim(); if (!confirmed.contains(key)) confirmed.addElement(key); }
        }
    }
    public void add(String from, String to, int x, int y) {
        from = TradeStock.normalize(from).trim(); to = TradeStock.normalize(to).trim();
        for (int i = edges.size()-1; i >= 0; i--) {
            Exit e = (Exit)edges.elementAt(i);
            if (e.from.equals(from) && e.to.equals(to)) edges.removeElementAt(i);
        }
        edges.addElement(new Exit(from,to,x,y));
    }
    public Exit next(String start, String target) {
        start=TradeStock.normalize(start).trim(); target=TradeStock.normalize(target).trim();
        Vector nodes=new Vector(), first=new Vector();
        nodes.addElement(start); first.addElement(null);
        for (int q=0; q<nodes.size(); q++) {
            String current=(String)nodes.elementAt(q);
            if (current.equals(target)) return (Exit)first.elementAt(q);
            for (int i=0; i<edges.size(); i++) {
                Exit edge=(Exit)edges.elementAt(i);
                if (!usable(edge) || !edge.from.equals(current) || nodes.contains(edge.to)) continue;
                nodes.addElement(edge.to); first.addElement(q==0 ? edge : first.elementAt(q));
            }
        }
        return null;
    }
    public int distance(String start, String target) {
        return distance(start, target, null);
    }
    public int distance(String start, String target, String avoid) {
        start=TradeStock.normalize(start).trim(); target=TradeStock.normalize(target).trim();
        if (start.equals(target)) return 0;
        String avoidNorm = (avoid != null) ? TradeStock.normalize(avoid).trim() : null;
        Vector visited=new Vector(), queue=new Vector(), dist=new Vector();
        if (avoidNorm != null && avoidNorm.length() > 0 && !avoidNorm.equals(target)) {
            visited.addElement(avoidNorm);
        }
        visited.addElement(start);
        queue.addElement(start);
        dist.addElement(new Integer(0));
        for (int q=0; q<queue.size(); q++) {
            String current=(String)queue.elementAt(q);
            int d=((Integer)dist.elementAt(q)).intValue();
            if (current.equals(target)) return d;
            for (int i=0; i<edges.size(); i++) {
                Exit edge=(Exit)edges.elementAt(i);
                if (!usable(edge) || !edge.from.equals(current) || visited.contains(edge.to)) continue;
                visited.addElement(edge.to);
                queue.addElement(edge.to);
                dist.addElement(new Integer(d+1));
            }
        }
        return Integer.MAX_VALUE;
    }
    public int size() { return edges.size(); }

    public boolean hasDirect(String from, String to) {
        from = TradeStock.normalize(from).trim();
        to = TradeStock.normalize(to).trim();
        for (int i = 0; i < edges.size(); i++) {
            Exit e = (Exit) edges.elementAt(i);
            if (e.from.equals(from) && e.to.equals(to)) return true;
        }
        return false;
    }

    public void seedDefaults() {
        String[] known = {
            "Ngoi Lang Nho", "Lang Soi Trang", "Khu mo", "Bia Rung", "Hang Lua", "Rung Ao Giac", "Khe Vuc", "Canh Dong Soi",
            "Thung Lung Ky Bi", "Ho Ky Uc", "Bai Dat Trong", "Bo Bien", "Vuc Da", "Rang Da Ngam", "Nghia Dia Tau Dam", "Dam Lay",
            "Den Co", "Hang Doi", "Hang Soi Quy", "Cua Bien", "Sa Mac", "Doi Cat", "Vuc Lun", "Ho Tu Than",
            "Nghia dia cat", "Rung Chet", "Suoi Ma", "Thung Lung Da", "Boss Guardian", "Ham Mo Tang 1", "Ham Mo Tang 2", "Ham Mo Tang 3",
            "Ham mo quai vat", "Thanh Pho Kho Bau", "Khu phia Tay", "Khu phia Dong", "Dau Truong", "Rung cao nguyen", "Con duong hiem tro", "Vach da cheo leo",
            "Nui Cau Vong", "Loi len Thuong gioi", "Deo hoang so", "Duong xuong long dat", "Cay Cau ma am", "Cong vao Ha gioi", "Dau Truong", "Doi xac chet",
            "Nga tu tu than", "Pho ban moi", "Khu vuon", "Cong thien dang", "Cong dia nguc", "Chuan bi", "Lang anh sang", "Chuan bi",
            "Lang gio", "Chuan bi", "Lang set", "Chuan bi", "Lang lua", "Chien truong", "Dia nguc tang 1", "Rung medusa",
            "Rung Chimera", "Rung quai vat", "Thac reo", "Thanh pho cang", "Khu bo nam", "Khu bo bac", "Khu bo tay", "Rung chuot",
            "Rung hoa do", "Vinh Caribe", "Me cung", "Me cung tang 1", "Me cung tang 2", "Me cung tang 3", "Me cung tang 4", "Me cung tang cuoi",
            "Thi dau", "", "Khu mua ban"
        };
        for (int i = 0; i < known.length; i++) {
            if (known[i].length() > 0 && nameFor(i) == null) observed(known[i], i);
        }
        seedLink("Lang Soi Trang", "Canh Dong Soi", 240, 24, 240, 400);
        seedLink("Canh Dong Soi", "Thung Lung Ky Bi", 24, 240, 400, 240);
        seedLink("Canh Dong Soi", "Bo Bien", 400, 240, 24, 240);
        seedLink("Canh Dong Soi", "Hang Soi Quy", 240, 24, 240, 400);
        seedLink("Hang Soi Quy", "Rung Chet", 240, 24, 240, 400);
        seedLink("Rung Chet", "Thanh Pho Kho Bau", 240, 24, 240, 400);

        // Core trading route: Graham (Thung Lung Ky Bi) <-> Ho Ky Uc <-> Thanh Pho Kho Bau
        seedLink("Thung Lung Ky Bi", "Ho Ky Uc", 24, 240, 400, 240);
        seedLink("Ho Ky Uc", "Thanh Pho Kho Bau", 24, 240, 400, 240);

        // Branch 1: Market Vách Đá Cheo Leo (Map 39)
        seedLink("Thanh Pho Kho Bau", "Rung cao nguyen", 400, 240, 24, 240);
        seedLink("Rung cao nguyen", "Con duong hiem tro", 400, 240, 24, 240);
        seedLink("Con duong hiem tro", "Vach da cheo leo", 400, 240, 24, 240);

        // Branch 2: Market Cửa Biển (Map 19)
        seedLink("Thanh Pho Kho Bau", "Sa Mac", 24, 240, 400, 240);
        seedLink("Sa Mac", "Doi Cat", 240, 24, 240, 400);
        seedLink("Doi Cat", "Ho Tu Than", 240, 24, 240, 400);
        seedLink("Ho Tu Than", "Nghia Dia Cat", 240, 24, 240, 400);
        seedLink("Nghia Dia Cat", "Cua Bien", 240, 24, 240, 400);

        // Remove the old fictitious shortcut, including links from older RMS caches.
        for (int i=edges.size()-1; i>=0; i--) {
            Exit e=(Exit)edges.elementAt(i);
            if (e.from.equals("doi xac chet") && e.to.equals("cong vao ha gioi")
                    || e.from.equals("cong vao ha gioi") && e.to.equals("doi xac chet")) edges.removeElementAt(i);
        }
        seedLink("Cua Bien", "Doi xac chet", 240, 24, 240, 400);
        for (int i=1; i<TradeConfig.HELL_ROUTE.length-1; i++)
            seedLink(TradeConfig.HELL_ROUTE[i],TradeConfig.HELL_ROUTE[i+1],0,0,0,0);

        seedLink("Thanh pho cang", "Cua Bien", 24, 240, 400, 240);
        seedLink("Khu mua ban", "Lang Soi Trang", 24, 240, 400, 240);
        // User-confirmed selling corridor. Only seed the confirmed direction;
        // Navigator always obtains the actual exit coordinates from the live map.
        for (int i=0; i<TradeConfig.SELLER_ROUTE.length-1; i++) {
            String from=TradeConfig.SELLER_ROUTE[i], to=TradeConfig.SELLER_ROUTE[i+1];
            if (!confirmed.contains(TradeStock.normalize(from).trim()) && !hasDirect(from,to))
                add(from,to,0,0);
        }
    }
    private void seedLink(String a, String b, int ax, int ay, int bx, int by) {
        if (!confirmed.contains(TradeStock.normalize(a).trim()) && !hasDirect(a, b)) add(a, b, ax, ay);
        if (!confirmed.contains(TradeStock.normalize(b).trim()) && !hasDirect(b, a)) add(b, a, bx, by);
    }
}
