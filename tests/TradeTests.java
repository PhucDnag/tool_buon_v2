import autotrade.*;

public final class TradeTests {
    private static int checks;
    private static void check(boolean value,String message) { checks++; if (!value) throw new AssertionError(message); }
    private static final class Port implements TradeEngine.Port {
        int summons, buys, sells, recalls, queries, recoveries, resurrects;
        boolean ready=true;
        public boolean ready() { return ready; }
        public void stopMovement() {}
        public void goMarket(int i) {}
        public void goSeller() {}
        public void queryStock(int m,int r) { queries++; }
        public void summon(int a) { summons++; }
        public void buy(int g) { buys++; }
        public void sell() { sells++; }
        public void recall() { recalls++; }
        public void recoverPreviousMap() { recoveries++; }
        public void followAnimal() {}
        public void resurrect() { resurrects++; }
        public void resurrectVillage() {}
        public void resetLostTrip() {}
        public boolean hasLostTrip() { return false; }
        public void notifyUser(String t) {}
    }
    private static TradeEngine begin(Port p,TradeEngine.View v) {
        TradeEngine e=new TradeEngine(new TradeConfig(),p); e.start(0); v.market=0; e.tick(v,0); return e;
    }
    public static void main(String[] args) {
        TradeStock s=TradeStock.parse("Kim Cương: 12, Rubi: 0, Saphia: 7; Ngọc trai đen: 999");
        check(s.count[0]==12 && s.count[1]==0 && s.count[2]==7,"Vietnamese stock quantities");
        check(s.choose(1)==2 && s.pearls,"Ruby falls forward to sapphire, never diamonds or pearls");
        s=TradeStock.parse("Ta chỉ còn 12 viên saphia, 34 viên riuby, 5 viên kim cương.");
        check(s.count[0]==5 && s.count[1]==34 && s.count[2]==12,"Actual NPC quantity-before-gem sentence");
        s=TradeStock.parse("Ta chỉ còn 0 viên saphia, 4 viên riuby, 0 viên kim cương, 99 viên ngọc trai đen.");
        check(s.choose(0)==1 && s.pearls,"NPC sentence fallback ignores pearls");
        check(TradeStock.parse("Ta chỉ còn 0 viên saphia, 0 viên riuby, 0 viên kim cương.").empty(),"All explicit zero quantities");
        s=TradeStock.parse("Ta chỉ còn 9 viên saphia.");
        check(s.count[2]==9 && s.count[0]==-1 && s.count[1]==-1,"Unmentioned quantities remain unknown");
        check(TradeStock.parse("Ta chỉ còn ... viên saphia, .. viên riuby").choose(0)==-1,"Placeholder text is not stock");
        check(TradeStock.parse("Ta chỉ còn -2 viên saphia").choose(0)==-1,"Negative prefix rejected");
        check(TradeStock.parse("Ta chỉ còn 1.5 viên saphia").choose(0)==-1,"Fractional prefix rejected");
        check(TradeStock.parse("Ta chỉ còn 99999999999999 viên riuby").choose(0)==-1,"Overflow prefix rejected");
        check(TradeStock.parse("Mua 20 viên saphia giá 500 vàng?").choose(0)==-1,"Purchase prompt is not inventory");
        check(TradeStock.parse("Ta chỉ còn ngọc trai đen và 3 viên saphia.").count[2]==3,"Pearl wording cannot erase actual gems");
        check(TradeStock.parse("Ta chỉ còn saphia, 3 viên riuby.").count[2]==-1,"Next gem quantity is not assigned to previous gem");
        check(TradeStock.parse("Ta chỉ còn 5 viên riubyfake").choose(0)==-1,"Gem alias requires word boundary");
        check(TradeStock.parse("Riuby: 6").count[1]==6,"Riuby alias works in label-first format too");
        check(TradeStock.parse("Ta chỉ còn 50 viên ngọc trai đen.").empty(),"Exclusive counted pearls contain no tradable gems");
        check(!TradeStock.parse("Tồn kho: 50 viên ngọc trai đen.").empty(),"Non-exclusive partial pearl report stays unknown");
        check(!TradeStock.parse("Ngọc trai: 50").empty(),"Partial reply is not empty stock");
        check(TradeStock.parse("Chỉ còn ngọc trai đen").empty(),"Explicit pearl-only reply");
        check(TradeStock.parse("Kim cương giá 500 vàng").choose(0)==-1,"Price is not stock");
        check(TradeStock.parse("Kim Cương: 500 vàng").choose(0)==-1,"Currency is not stock");
        check(TradeStock.parse("Kim cương: 999999999999999999999").choose(0)==-1,"Overflow rejected");
        check(TradeStock.parse("Kim cương: -1").choose(0)==-1,"Negative quantity rejected");
        check(TradeStock.parse("Kim cương: 12.500 vàng").choose(0)==-1,"Decimal price rejected");
        check(TradeStock.normalize("VÁCH ĐÁ CHEO LEO").equals("vach da cheo leo"),"Combining marks normalized");
        check(!TradeConfig.tradingClosed(3*3600000L-1),"Before 10h UTC+7");
        check(TradeConfig.tradingClosed(3*3600000L),"10h boundary inclusive");
        check(TradeConfig.tradingClosed(9*3600000L-1),"Before 16h");
        check(!TradeConfig.tradingClosed(9*3600000L),"16h boundary exclusive");
        TradeConfig c=new TradeConfig(); java.util.Random rng=new java.util.Random(1);
        for (int i=0;i<1000;i++) { int delay=c.reviveDelay(rng); check(delay>=9000 && delay<=11000,"Revive range"); }
        MapGraph g=new MapGraph(); g.add("A","B",1,2); g.add("B","C",3,4); g.add("A","D",5,6); g.add("D","E",7,8); g.add("E","C",9,10);
        check(g.next("A","C").to.equals("b"),"Shortest directed route");
        check(g.next("C","A")==null,"No invented reverse link");
        g.add("A","B",10,20); check(g.size()==5 && g.next("A","C").x==10,"Updated exit replaces old coordinates");

        TradeConfig fixedConfig=new TradeConfig(); fixedConfig.market=1; fixedConfig.rotateMarkets=false;
        Port retryPort=new Port(); TradeEngine.View retryView=new TradeEngine.View();
        TradeEngine retryEngine=new TradeEngine(new TradeConfig(),retryPort); retryEngine.start(0);
        retryView.animal=true; retryView.cargo=3; retryView.hp=retryView.maxHp=100;
        retryEngine.state=TradeEngine.SELL; retryView.atSeller=false; retryEngine.tick(retryView,0);
        check(retryPort.sells==0 && retryEngine.state==TradeEngine.TO_SELL,"Recheck range between approach and sale");
        retryView.atSeller=true; retryEngine.tick(retryView,1000); retryEngine.tick(retryView,2000);
        check(retryPort.sells==1,"First sale after range check");
        retryEngine.tick(retryView,21000); check(retryPort.sells==1,"No rapid duplicate sale");
        retryEngine.tick(retryView,23000);
        check(retryEngine.state==TradeEngine.TO_SELL,"Timed out sale returns to approach");
        retryEngine.tick(retryView,25000); retryEngine.tick(retryView,26000);
        check(retryPort.sells==2,"Retry first unconfirmed sale with cargo still present");
        retryEngine.tick(retryView,47000); retryEngine.tick(retryView,49000); retryEngine.tick(retryView,50000);
        check(retryPort.sells==3,"Third attempt allowed");
        retryEngine.tick(retryView,71000);
        check(retryEngine.state==TradeEngine.STOP && retryPort.sells==3,"Stop after bounded retries rather than hanging or spamming");
        for (int cargo=0;cargo<2;cargo++) {
            TradeConfig lastConfig=new TradeConfig(); lastConfig.market=2;
            Port lastPort=new Port(); TradeEngine.View lastView=new TradeEngine.View(); lastView.market=2;
            lastView.animal=true; lastView.cargo=cargo; lastView.hp=lastView.maxHp=100;
            TradeEngine lastEngine=new TradeEngine(lastConfig,lastPort); lastEngine.start(0); lastEngine.tick(lastView,0);
            lastEngine.onStock(2,lastEngine.stockRequest(),"Ta hết hàng rồi",100); lastEngine.tick(lastView,1000);
            if (cargo==0) {
                check(lastEngine.state==TradeEngine.RECALL,"Recall empty animal before returning to first market");
                lastEngine.tick(lastView,2000); lastView.animal=false; lastEngine.tick(lastView,3000);
            } else {
                check(lastEngine.state==TradeEngine.TO_SELL,"Preserve cargo and sell before restock wait");
                lastView.atSeller=true; lastEngine.tick(lastView,2000); lastEngine.tick(lastView,3000);
                lastEngine.onSaleConfirmed(true,4000);
            }
            check(lastEngine.market==0 && lastEngine.state==TradeEngine.BACKOFF,"After recall/sale return to first market for restock");
        }
        for (int rotate=0;rotate<2;rotate++) {
            TradeConfig lastConfig=new TradeConfig(); lastConfig.market=2; lastConfig.rotateMarkets=rotate==1;
            Port lastPort=new Port(); TradeEngine.View lastView=new TradeEngine.View(); lastView.market=2;
            TradeEngine lastEngine=new TradeEngine(lastConfig,lastPort); lastEngine.start(0); lastEngine.tick(lastView,0);
            lastEngine.onStock(2,lastEngine.stockRequest(),"Ta hết hàng rồi",100); lastEngine.tick(lastView,1000);
            check(lastEngine.market==0 && lastEngine.state==TradeEngine.BACKOFF,"Last market empty returns to first even when starting at last market");
            lastView.market=0; lastEngine.tick(lastView,2000);
            int queryCount=lastPort.queries; lastEngine.tick(lastView,61000);
            check(lastPort.queries==queryCount,"Wait sixty seconds at first market");
            lastEngine.tick(lastView,63000);
            lastEngine.onStock(0,lastEngine.stockRequest(),"Ta hết hàng rồi",63100); lastEngine.tick(lastView,64000);
            check(lastEngine.market==0 && lastEngine.state==TradeEngine.STOCK,"Stay at first instead of cycling again");
            lastEngine.tick(lastView,125000);
            lastEngine.onStock(0,lastEngine.stockRequest(),"Kim Cương: 5, Rubi: 5, Saphia: 5",125100); lastEngine.tick(lastView,126000);
            check(lastEngine.state==TradeEngine.SUMMON && lastPort.summons==1,"New stock resumes normal purchase flow");
        }
        Port fixedPort=new Port(); TradeEngine.View fixedView=new TradeEngine.View(); fixedView.market=1;
        TradeEngine fixedEngine=new TradeEngine(fixedConfig,fixedPort); fixedEngine.start(0); fixedEngine.tick(fixedView,0);
        fixedEngine.onStock(1,fixedEngine.stockRequest(),"Kim Cương: 0, Rubi: 0, Saphia: 0",100);
        fixedEngine.tick(fixedView,1000);
        check(fixedEngine.market==1 && fixedEngine.state==TradeEngine.STOCK,"Fixed Cua Bien must not leave for another market");
        int fixedQueries=fixedPort.queries;
        fixedEngine.tick(fixedView,60000);
        check(fixedPort.queries==fixedQueries,"Fixed market waits before polling");
        fixedEngine.tick(fixedView,62000);
        check(fixedPort.queries==fixedQueries+1 && fixedEngine.market==1,"Recheck stock at Cua Bien after 60 seconds");
        Port p=new Port(); TradeEngine.View v=new TradeEngine.View(); TradeEngine e=begin(p,v);
        check(p.queries==1 && p.summons==0,"Check stock before summon");
        e.onStock(1,e.stockRequest(),"Kim Cương: 9",100); e.tick(v,1000);
        check(p.summons==0,"Wrong-market reply ignored");
        e.onStock(0,e.stockRequest()-1,"Kim Cương: 9",1100); e.tick(v,2000);
        check(p.summons==0,"Stale request ignored");
        e.onStock(0,e.stockRequest(),"Ngọc trai: 10",2100); e.tick(v,3000);
        check(p.summons==0,"Unknown gems do not summon");
        e.onStock(0,e.stockRequest(),"Ta chỉ còn 0 viên kim cương, 3 viên riuby, 0 viên saphia.",3100); e.tick(v,4000);
        check(p.summons==1 && e.gem==1,"Summon once with alternate gem in stock");
        e.tick(v,5000); check(p.summons==1,"No duplicate summon while waiting");
        v.animal=true; v.cargo=0; e.tick(v,6000); e.tick(v,7000); e.tick(v,8000);
        check(p.buys==1,"Do not duplicate unacknowledged purchase");
        v.cargo=1; v.cargoRevision=1; e.tick(v,9000); e.tick(v,10000);
        check(p.buys==2,"Buy again only after server cargo revision");
        e.onOutOfStock(v,10001); check(e.state==TradeEngine.STOCK,"Recheck all gems when one gem is depleted");
        e.onStock(0,e.stockRequest(),"Kim Cương: 0, Rubi: 0, Saphia: 0; Ngọc trai đen: 999",10002);
        e.tick(v,10003);
        check(e.state==TradeEngine.TO_SELL && v.cargo==1,"Preserve partial cargo and sell when only pearls remain");
        v.atSeller=true; e.tick(v,11000); e.tick(v,12000); v.animal=false; e.tick(v,13000);
        check(p.sells==1 && e.state==TradeEngine.WAIT_SALE,"Disappearance alone does not prove sale");
        e.onSaleConfirmed(true,14000); check(e.state==TradeEngine.TO_MARKET,"Loop after explicit sale confirmation");

        p=new Port(); v=new TradeEngine.View(); e=begin(p,v);
        long t=0;
        for (int market=0;market<3;market++) {
            if (market>0) { v.market=market; e.tick(v,t); }
            e.onStock(market,e.stockRequest(),"Chỉ còn ngọc trai",t+100);
            e.tick(v,t+1000); t+=2000;
        }
        check(e.state==TradeEngine.BACKOFF && e.market==0 && p.summons==0,"Three empty markets back off at first market");
        v.market=0; e.tick(v,6000); int queries=p.queries; e.tick(v,65000);
        check(p.queries==queries,"Backoff does not poll too early");
        e.tick(v,66000); check(p.queries==queries+1,"Backoff rechecks stock");

        p=new Port(); p.ready=false; e=new TradeEngine(c,p);
        check(!e.start(0) && e.state==TradeEngine.STOP,"Unverified adapter cannot enable trading");
        p=new Port(); v=new TradeEngine.View(); e=begin(p,v); v.animalMissing=true; e.tick(v,1000); e.tick(v,20000);
        check(e.state==TradeEngine.RECOVER,"Search lost animal for full twenty seconds");
        e.tick(v,21000); check(e.state==TradeEngine.TO_MARKET && p.summons==0,"Reset only after full recovery period, no immediate summon");
        p=new Port(); v=new TradeEngine.View(); e=begin(p,v); v.dead=true;
        e.tick(v,1000); e.tick(v,9999); check(p.resurrects==0,"No early resurrection");
        e.tick(v,12000); e.tick(v,13000); check(p.resurrects==1,"Resurrection sent once per death");
        p=new Port(); v=new TradeEngine.View(); e=begin(p,v); v.connected=false; e.tick(v,1000);
        v.connected=true; v.cargo=-1; e.tick(v,2000); check(p.summons==0,"Reconnect waits for cargo synchronization");
        v.cargo=2; v.animal=true; e.tick(v,3000); check(e.state==TradeEngine.STOCK && v.cargo==2,"Reconnect at selected market preserves cargo and continues loading");
        p=new Port(); v=new TradeEngine.View(); v.market=0; v.animal=true; v.cargo=-1; v.hp=100; v.maxHp=100;
        e=new TradeEngine(c,p); e.start(0); e.tick(v,1000);
        check(e.state==TradeEngine.STOCK && p.queries==1 && p.recalls==0 && p.summons==0,
            "Existing unknown cargo at selected market checks stock without replacing");
        e.tick(v,2000);
        check(e.state==TradeEngine.STOCK,"Unknown cargo waits for stock rather than leaving to sell");
        v.market=-1; e.stop(); e.start(3000); e.tick(v,3000);
        check(e.state==TradeEngine.TO_SELL,"Restart outside market also resumes unknown-cargo escort");
        v.atSeller=true; e.tick(v,4000); e.tick(v,5000);
        check(e.state==TradeEngine.WAIT_SALE && p.sells==1,"Unknown-cargo trip can reach seller and request sale");
        check(TradeSignals.cargo("Thú đang chở 12 viên saphia") == 12,"Cargo parsed from animal info");
        check(TradeSignals.cargo("HP: 123/456") == -1,"HP cannot become cargo");
        check(TradeSignals.cargo("Số hàng: 0/20") == 0,"Empty animal confirmed");
        check(TradeSignals.cargo("Số lượng hàng: 18") == 18,"Cargo variant so luong hang");
        check(TradeSignals.cargo("Sức chở: 6/6") == 6,"Cargo variant suc cho");
        check(TradeSignals.cargo("Hàng: 24") == 24,"Cargo variant hang");
        check(TradeSignals.full("Thú đã đầy, không thể mua thêm"),"Full load signal");
        check(TradeSignals.full("Hàng đã đầy"),"Full load variant");
        check(!TradeSignals.depleted("Chưa hết hàng"),"Negative depletion signal");
        check(TradeSignals.animal("Lạc đà vàng",1) && !TradeSignals.animal("Lạc đà trắng",1),"Gold animal selection");
        check(TradeSignals.animal("Bò rừng vàng",2) && TradeSignals.animal("Bò vàng",2),"Gold bull variants");
        check(TradeSignals.isConfirmButton("Ok") && TradeSignals.isConfirmButton("Có") && TradeSignals.isConfirmButton("Đồng ý"),"Dialog confirm buttons");
        check(TradeSignals.isConfirmButton("Xác nhận") && TradeSignals.isConfirmButton("Chấp nhận") && TradeSignals.isConfirmButton("Mua") && TradeSignals.isConfirmButton("Bán"),"Extended dialog confirm buttons");
        check(TradeConfig.MAX_CARGO[0]==6 && TradeConfig.MAX_CARGO[1]==12 && TradeConfig.MAX_CARGO[2]==24,"Max cargo capacities");
        check(TradeConfig.SELLER_MAP.equals("Thung Lũng Kỳ Bí") && TradeConfig.MAP_CONG_DIA_NGUC==52 && TradeConfig.MAP_CUA_BIEN==19 && TradeConfig.MAP_VACH_DA_CHEO_LEO==39,"Configured trade map IDs");
        MapGraph mg=new MapGraph(); mg.seedDefaults();
        check(mg.nameFor(8)!=null && mg.next("Lang Soi Trang","Canh Dong Soi")!=null,"Seed defaults provides route network");
        check(mg.next("Lang Soi Trang","Cua Bien")!=null,"Seed defaults path to Cua Bien");
        check(mg.next("Thanh Pho Kho Bau","Vach da cheo leo")!=null,"Seed defaults path to Vach Da");
        check(mg.next("Thanh Pho Kho Bau","Cong dia nguc")!=null,"Seed defaults path to Cong Dia Nguc");
        int[] tiles={0,1,0, 0,1,0, 0,0,0};
        int[] path=GridPath.find(tiles,3,3,0,0,2,0,0);
        check(path!=null && path.length==6,"Collision BFS routes around wall");
        check(GridPath.find(new int[]{0,1,1,0},2,2,0,0,1,1,0)==null,"No diagonal corner cutting");
        check(GridPath.find(new int[]{0,-1,0},3,1,0,0,2,0,0)==null,"Void tiles are impassable");
        check(GridPath.find(tiles,3,3,-1,0,2,0,0)==null,"Reject coordinates outside map");
        check(GridPath.find(tiles,3,3,0,0,0,0,0).length==0,"Already at destination");
        g.observed("A",1); g.observed("B",2);
        check(g.frontier("A").equals("d"),"Explore closest unseen map");
        check(g.nameFor(2).equals("b"),"Resolve server map ID");
        try {
            java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream(); g.write(new java.io.DataOutputStream(bytes));
            MapGraph restored=new MapGraph(); restored.read(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())));
            check(restored.next("A","C").x==10 && restored.nameFor(2).equals("b"),"Persist route graph and IDs");
        } catch(java.io.IOException failure) { throw new AssertionError(failure); }
        s=TradeStock.parse("Kim Cương: 99, Rubi: 0, Saphia: 5; Ngọc trai đen: 99");
        check(s.choose(0)==0 && s.choose(1)==2 && s.choose(2)==2,"Configured gem is the beginning of a forward-only priority order");
        s=TradeStock.parse("Kim Cương: 99, Rubi: 0, Saphia: 0");
        check(s.choose(1)==-1 && s.exhausted(1) && !s.empty(),"Diamonds cannot restart ruby/sapphire purchasing after both are exhausted");
        check(!TradeStock.parse("Rubi: 0").exhausted(1),"Unknown sapphire stock is not treated as exhausted");
        p=new Port(); v=new TradeEngine.View(); e=begin(p,v); v.animal=true; v.cargo=99;
        e.state=TradeEngine.BUY; e.tick(v,1000);
        check(p.buys==1 && e.state==TradeEngine.WAIT_BUY && !v.full,"Local estimated capacity cannot end purchases before server says full");
        v.full=true; e.tick(v,2000);
        check(e.state==TradeEngine.TO_SELL,"Server full flag still ends purchases immediately");
        int[] lowStates={TradeEngine.WAIT_BUY,TradeEngine.STOCK};
        for(int i=0;i<lowStates.length;i++) {
            p=new Port(); v=new TradeEngine.View(); e=begin(p,v);
            v.animal=true; v.hp=10; v.maxHp=100; v.cargo=2; e.state=lowStates[i];
            e.tick(v,2000);
            check(e.state==TradeEngine.WAIT_REPLACE,"Low HP triggers replacement while waiting for buy/stock response");
            v.animal=false; e.tick(v,3000);
            check(e.state==TradeEngine.WAIT_NEW,"Replacement waits for new animal");
            v.animal=true; v.hp=100; e.tick(v,8000);
            check(e.state==TradeEngine.TO_MARKET,"After replacement recheck market instead of waiting on obsolete buy request");
        }
        // Unknown sale outcomes must not wait forever or start another trip.
        p=new Port(); v=new TradeEngine.View(); e=begin(p,v);
        e.state=TradeEngine.SELL; v.atSeller=true; e.tick(v,1000);
        e.tick(v,40000);
        check(e.state==TradeEngine.STOP && p.sells==1,"Unknown sale outcome stops after synchronization grace period");
        TradeConfig fastSale=new TradeConfig(); fastSale.saleTimeoutMs=4000; fastSale.maxSaleAttempts=2;
        p=new Port(); v=new TradeEngine.View(); e=new TradeEngine(fastSale,p); e.start(0);
        v.animal=true; v.cargo=2; v.atSeller=true; e.state=TradeEngine.SELL;
        for (int n=0;n<30;n++) e.tick(v,n*1000L);
        check(e.state==TradeEngine.STOP && p.sells==2,"Fast sale policy also bounds rejected sale attempts");
        p=new Port(); v=new TradeEngine.View(); e=begin(p,v);
        v.animal=true; v.cargo=6; v.hp=1; v.maxHp=100; e.state=TradeEngine.TO_SELL;
        e.tick(v,1000); v.animal=false; e.tick(v,2000);
        v.animal=true; v.hp=100; v.cargo=-1; e.tick(v,7000);
        check(e.state==TradeEngine.WAIT_NEW && v.cargo==-1,"Replacement cannot invent cargo from previous animal");
        v.gemDrops=true; v.cargo=1; e.tick(v,8000);
        check(e.state==TradeEngine.WAIT_NEW,"Partial pickup keeps collecting remaining drops");
        e.onActionProgress(31000); e.tick(v,33000);
        check(e.state==TradeEngine.STOP,"Uncollectable drops and repeated menus cannot extend replacement forever");
        p=new Port(); v=new TradeEngine.View(); e=begin(p,v);
        e.state=TradeEngine.SELL; v.atSeller=true; v.animal=true; v.cargo=6; e.tick(v,1000);
        v.dead=true; e.tick(v,2000); e.tick(v,14000);
        v.dead=false; v.animal=false; v.animalMissing=true; v.atSeller=false; e.tick(v,15000);
        check(e.state==TradeEngine.RECOVER && p.recoveries>0,"Death during sale resumes recovery after village revival");
        v.animal=true; v.animalMissing=false; e.tick(v,16000);
        check(e.state==TradeEngine.TO_SELL,"Reunited animal resumes interrupted sale trip");
        System.out.println("PASS "+checks+" assertions");
    }
}
