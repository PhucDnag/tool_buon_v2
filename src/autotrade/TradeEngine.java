package autotrade;

/** Server-independent coordinator. A port must acknowledge mutations from server events. */
public final class TradeEngine {
    public static final int STOP=0, TO_MARKET=1, STOCK=2, SUMMON=3, BUY=4,
        TO_SELL=5, SELL=6, WAIT_SALE=7, RECALL=8, BACKOFF=9, RECOVER=10,
        REPLACE=11, WAIT_REPLACE=12, WAIT_RECALL=13, PAUSED=14, WAIT_BUY=15, WAIT_NEW=16;
    public interface Port {
        boolean ready();
        void stopMovement();
        void goMarket(int index);
        void goSeller();
        void queryStock(int market, int request);
        void summon(int animal);
        void buy(int gem);
        void sell();
        void recall();
        void recoverPreviousMap();
        void followAnimal();
        void resurrect();
        void resurrectVillage();
        void resetLostTrip();
        boolean hasLostTrip();
        void notifyUser(String text);
    }
    public static final class View {
        public boolean connected=true, dead, animal, atSeller, animalMissing, hasReviveItem;
        public int market=-1, cargo=-1, hp, maxHp, distance;
        public boolean full, gemDrops, inRecoveryMap;
        public long cargoRevision;
    }
    private final Port port;
    private final TradeConfig config;
    private final java.util.Random random = new java.util.Random();
    public int state=STOP, market, gem;
    public String status="Đã tắt";
    private int selectedGem, emptyMask, request, pauseState, recoverState, replaceState, previousCargo, buyAttempts;
    private long pickupUntil, due, deadline, stockAt, lastNotice, deathAt, buyRevision, pausedAt, recoverMapArrival;
    private long replaceCooldown;
    private TradeStock stock;
    private boolean returnToMarketAfterRecall, awaitingStock, deathSent, disconnected;
    private boolean waitAtFirstMarket;
    private int saleAttempts;

    public TradeEngine(TradeConfig c, Port p) { config=c; port=p; }
    public boolean start(long now) {
        if (!port.ready()) { status="Chưa xác minh giao thức máy chủ"; return false; }
        market=config.market; gem=config.gem; selectedGem=config.gem; emptyMask=0; stock=null;
        returnToMarketAfterRecall=false; state=TO_MARKET; due=now; deadline=0; deathAt=0; deathSent=false;
        awaitingStock=false; disconnected=false; waitAtFirstMarket=false; saleAttempts=0; status="Đến bãi mua"; return true;
    }
    public void resumeRecovery(long now) {
        market=config.market; gem=config.gem; selectedGem=config.gem; emptyMask=0; stock=null;
        returnToMarketAfterRecall=false; recoverState=TO_SELL; state=RECOVER;
        deadline=now+180000; recoverMapArrival=0; due=now; deathAt=0; deathSent=false;
        awaitingStock=false; disconnected=false; waitAtFirstMarket=false; saleAttempts=0;
        status="Đang về map dắt tiếp thú";
    }
    /** Resume only a purchase session paused on the same animal and buying map. */
    public void resumePurchases(View view,long now) {
        if (state!=TO_MARKET || !view.animal || view.market!=market || view.full) return;
        state=STOCK; buyAttempts=0; due=now;
        requestStock(now);
    }
    public void resumeEscort(long now) {
        if (state!=TO_MARKET) return;
        state=TO_SELL; due=now; stock=null; awaitingStock=false;
        status="Tiếp tục dắt thú về bán";
    }
    public void stop() { state=STOP; awaitingStock=false; stock=null; port.stopMovement(); status="Đã tắt"; }
    public int stockRequest() { return request; }
    /** A real menu/shop/confirmation response is progress, not a completed purchase. */
    public void onActionProgress(long now) {
        if (state==SUMMON || state==WAIT_NEW || state==WAIT_BUY || state==WAIT_SALE
                || state==WAIT_RECALL || state==WAIT_REPLACE) deadline=now+15000;
    }
    public void onStock(int sourceMarket, int requestId, String text, long now) {
        if (!awaitingStock || sourceMarket!=market || requestId!=request) return;
        TradeStock parsed=TradeStock.parse(text);
        if (parsed.choose(config.gem)<0 && !parsed.exhausted(config.gem)) return;
        stock=parsed; selectedGem=config.gem; stockAt=now; awaitingStock=false;
    }
    /** Call after a sale acknowledgement or confirmed animal removal at the seller. */
    public void onSaleConfirmed(boolean completed, long now) {
        if (state!=WAIT_SALE || !completed) return;
        saleAttempts=0;
        if (waitAtFirstMarket) { nextMarket(now); return; }
        state=TO_MARKET; due=now; stock=null; emptyMask=0; status="Đã bán; quay lại bãi mua";
    }
    public void onOutOfStock(View view, long now) {
        if (state!=BUY && state!=WAIT_BUY) return;
        // A depleted gem does not prove that the other two gems are exhausted.
        state=STOCK; buyAttempts=0; due=now;
        requestStock(now);
    }
    public void onAllGemsDepleted(View view,long now) {
        if (state!=BUY && state!=WAIT_BUY && state!=STOCK) return;
        emptyMask|=1<<market;
        if (market==2 || emptyMask==7) waitAtFirstMarket=true;
        stock=null; awaitingStock=false; buyAttempts=0;
        if (view.animal && view.cargo==0) { state=RECALL; returnToMarketAfterRecall=false; }
        else if (view.animal) state=TO_SELL;
        else { nextMarket(now); return; }
        due=now;
    }
    private void fail(String reason) { state=STOP; status=reason; port.stopMovement(); port.notifyUser(reason); }
    private void nextMarket(long now) {
        if (waitAtFirstMarket) {
            boolean alreadyHere=market==0;
            market=0; stock=null; awaitingStock=false;
            state=alreadyHere ? STOCK : BACKOFF;
            due=alreadyHere ? now+60000 : now;
            status="Hết hàng; về Cổng Địa Ngục chờ hàng mới";
            port.stopMovement(); return;
        }
        if (!config.rotateMarkets) {
            stock=null; awaitingStock=false; emptyMask=0;
            state=STOCK; due=now+60000;
            status="Bãi đã chọn hết hàng; chờ 60 giây rồi kiểm tra lại";
            port.stopMovement(); return;
        }
        emptyMask|=1<<market; stock=null;
        if (emptyMask==7) { market=0; state=BACKOFF; due=now; status="Cả 3 bãi hết hàng; về Cổng Địa Ngục"; }
        else { market=(market+1)%3; state=TO_MARKET; due=now; }
    }
    private void requestStock(long now) {
        awaitingStock=true; stock=null; request++;
        deadline=now+10000; port.queryStock(market,request);
    }
    public void tick(View view, long now) {
        if (state==STOP) return;
        if (!view.connected) { disconnected=true; port.stopMovement(); status="Chờ kết nối; phải đồng bộ hàng sau đăng nhập"; return; }
        if (disconnected) {
            disconnected=false; stock=null; awaitingStock=false;
            if (view.animal) {
                state=view.animal && (view.full || view.market!=market && view.cargo!=0) ? TO_SELL : TO_MARKET;
            } else if (port.hasLostTrip() || recoverState==TO_SELL || state==RECOVER || view.animalMissing) {
                recoverState=TO_SELL; state=RECOVER; deadline=now+180000; recoverMapArrival=0;
                status="Đang về map dắt tiếp thú";
            } else {
                state=TO_MARKET;
            }
            due=now;
        }
        if (state==TO_MARKET && view.animal) {
            // At the selected buying market, continue loading until the server
            // reports full or eligible stock is exhausted. Existing/unknown cargo
            // alone cannot prove full, even after a fresh application launch.
            if (view.full || view.market!=market && view.cargo!=0) { state=TO_SELL; due=now; }
        }
        if (TradeConfig.tradingClosed(now)) {
            if (state!=PAUSED) { pauseState=state; state=PAUSED; pausedAt=now; port.stopMovement(); }
            if (lastNotice==0 || now-lastNotice>=60000) { lastNotice=now; port.notifyUser("Tạm dừng buôn 10h–16h (UTC+7)"); }
            status="Chờ hết giờ cấm buôn"; return;
        }
        if (state==PAUSED) { state=pauseState; if (deadline>0) deadline+=now-pausedAt; stock=null; awaitingStock=false; due=now; if (state==STOCK) deadline=0; }
        if (view.dead) {
            if (deathAt==0) { deathAt=now+config.reviveDelay(random); deathSent=false; port.stopMovement(); }
            if (now>=deathAt && !deathSent) {
                deathSent=true; port.resurrect(); due=now+4000;
            }
            if (deathSent && now>=due) {
                if (view.hasReviveItem) {
                    port.resurrect(); due=now+4000;
                } else {
                    port.resurrectVillage(); due=now+3000;
                }
            }
            if (!deathSent) {
                long leftSec=Math.max(1,(deathAt-now)/1000);
                status="Chờ hồi sinh ("+leftSec+"s)";
            } else {
                status=view.hasReviveItem ? "Dùng vật phẩm hồi sinh tại chỗ" : "Hết vật phẩm hồi sinh; về làng";
            }
            return;
        }
        deathAt=0; deathSent=false;
        if (view.animalMissing && state!=RECOVER && state!=SUMMON && state!=WAIT_NEW && state!=WAIT_RECALL && state!=WAIT_REPLACE && state!=WAIT_SALE) {
            recoverState=state; state=RECOVER; deadline=now+(recoverState==TO_SELL || view.cargo>0 ? 180000 : 20000); recoverMapArrival=0; due=now;
        }
        if (state==RECOVER) {
            if (view.animal) { state=recoverState; due=now; }
            else if (recoverState==TO_SELL || view.cargo>0) {
                if (!view.inRecoveryMap) {
                    if (now>=deadline) {
                        port.resetLostTrip(); state=TO_MARKET; stock=null; awaitingStock=false; emptyMask=0; due=now+1000;
                        status="Không về được map tìm thú sau 3 phút; bắt đầu lại chuyến"; return;
                    }
                    if (now>=due) { port.recoverPreviousMap(); due=now+1000; }
                    status="Đang về map dắt tiếp thú"; return;
                } else {
                    if (recoverMapArrival==0) recoverMapArrival=now;
                    if (now-recoverMapArrival>=30000 || now>=deadline) {
                        port.resetLostTrip(); state=TO_MARKET; stock=null; awaitingStock=false; emptyMask=0; due=now+1000;
                        status="Không tìm thấy thú tại map cũ; bắt đầu lại chuyến"; return;
                    }
                    if (now>=due) { port.recoverPreviousMap(); due=now+1000; }
                    status="Đang tìm thú trong map"; return;
                }
            }
            else if (now>=deadline) {
                port.resetLostTrip(); state=TO_MARKET; stock=null; awaitingStock=false; emptyMask=0; due=now+1000;
                status="Không tìm thấy thú sau 20s; bắt đầu lại chuyến"; return;
            }
            else { if (now>=due) { port.recoverPreviousMap(); due=now+1000; } status="Lùi map tìm thú (20s)"; return; }
        }
        // A confirmed empty animal must not be escorted to a seller. Do not
        // mistake unknown cargo (-1) or gems awaiting pickup for an empty trip.
        if (view.animal && view.cargo==0 && !view.full && !view.gemDrops
                && state!=WAIT_REPLACE && state!=WAIT_NEW
                && (state==TO_SELL || state==SELL || state==RECOVER || state==TO_MARKET && view.market!=market)) {
            view.full=false; returnToMarketAfterRecall=true; state=RECALL; due=now;
            port.stopMovement();
        }
        // Replace low-HP animals using their item, then wait for gem pickup.
        // Timer-only trip responses do not provide a cargo count.
        if (view.animal && view.maxHp>0 && (long)view.hp*100 < (long)view.maxHp*TradeConfig.HP[config.hpIndex]
                && now>=replaceCooldown
                && (state==BUY || state==WAIT_BUY || state==STOCK || state==TO_SELL)) {
            previousCargo=view.cargo>0 ? view.cargo : (previousCargo>0 ? previousCargo : TradeConfig.MAX_CARGO[config.animal]);
            replaceState=state==TO_SELL ? TO_SELL : TO_MARKET;
            awaitingStock=false; stock=null; state=REPLACE; due=now; port.stopMovement();
            replaceCooldown=now+30000;
        }
        if (now<due) return;
        if (view.animal && view.full && (state==STOCK || state==BUY || state==WAIT_BUY || state==SUMMON)) {
            state=TO_SELL; stock=null; awaitingStock=false; buyAttempts=0;
        }
        due=now+((state==BUY || state==WAIT_BUY)?100:150);
        switch (state) {
            case TO_MARKET:
                int mIdx = (market>=0 && market<TradeConfig.MARKETS.length) ? market : 0;
                status="Đến "+TradeConfig.MARKETS[mIdx];
                if (view.market!=market) port.goMarket(market);
                else { state=STOCK; requestStock(now); }
                break;
            case STOCK:
                status="Kiểm tra tồn kho NPC";
                if (stock!=null && now-stockAt<=10000) {
                    gem=stock.choose(config.gem);
                    if (gem>=0) {
                        waitAtFirstMarket=false; emptyMask=0;
                        if (view.animal) {
                            state=BUY;
                        } else { state=SUMMON; deadline=now+15000; port.summon(config.animal); }
                    }
                    else if (stock.exhausted(config.gem)) onAllGemsDepleted(view,now);
                } else if (!awaitingStock || now>=deadline) requestStock(now);
                break;
            case SUMMON:
                if (view.animal) {
                    if (view.cargo>0) state=TO_SELL; else state=BUY;
                    emptyMask=0;
                    due=now;
                }
                else if (now>=deadline) fail("Chưa xác nhận thả thú; dừng để tránh thả trùng");
                break;
            case BUY:
                if (!view.full && selectedGem!=config.gem) {
                    state=STOCK; requestStock(now); break;
                }
                status="Mua "+TradeConfig.GEMS[gem];
                if (view.full) { state=TO_SELL; buyAttempts=0; due=now; break; }
                buyRevision=view.cargoRevision; previousCargo=view.cargo;
                port.buy(gem); state=WAIT_BUY; deadline=now+10000;
                break;
            case WAIT_BUY:
                if (view.full) { state=TO_SELL; buyAttempts=0; due=now; }
                else if (view.cargoRevision>buyRevision && view.cargo>previousCargo) { state=BUY; buyAttempts=0; due=now; }
                else if (now>=deadline) {
                    if (buyAttempts<2) {
                        buyAttempts++;
                        buyRevision=view.cargoRevision; previousCargo=view.cargo;
                        port.buy(gem); deadline=now+10000;
                        status="Chờ xác nhận mua ("+buyAttempts+"/2)";
                    } else {
                        fail("Chưa xác nhận giao dịch mua; không gửi mua lặp");
                    }
                }
                break;
            case TO_SELL:
                status="Dẫn thú về Graham (Thung Lũng Kỳ Bí)";
                if (view.atSeller) state=SELL; else port.goSeller();
                break;
            case SELL:
                if (!view.atSeller) { state=TO_SELL; port.goSeller(); break; }
                saleAttempts++; port.sell(); state=WAIT_SALE; deadline=now+20000; break;
            case WAIT_SALE:
                status="Chờ máy chủ xác nhận bán hàng";
                if (now>=deadline) {
                    if (view.animal && (view.cargo>0 || view.full)) {
                        if (saleAttempts>=3) { fail("Bán hàng chưa được xác nhận sau 3 lần; kiểm tra Graham"); break; }
                        state=TO_SELL; due=now+2000; port.goSeller();
                        status="Chưa bán được; dẫn thú tới Graham rồi thử lại";
                    } else status="Chờ đồng bộ thú/hàng trước khi thử bán lại";
                }
                break;
            case RECALL:
                port.recall(); state=WAIT_RECALL; deadline=now+10000; break;
            case WAIT_RECALL:
                if (!view.animal) {
                    port.resetLostTrip();
                    if (returnToMarketAfterRecall) {
                        returnToMarketAfterRecall=false; state=TO_MARKET; due=now;
                        stock=null; awaitingStock=false; status="Đã hủy thú trống; về bãi mua";
                    } else nextMarket(now);
                }
                else if (now>=deadline) fail("Chưa xác nhận thu hồi thú");
                break;
            case REPLACE:
                port.recall(); state=WAIT_REPLACE; deadline=now+10000; break;
            case WAIT_REPLACE:
                if (!view.animal) { port.summon(config.animal); state=WAIT_NEW; deadline=now+30000; pickupUntil=now+4000;
                    status="Đổi thú; chờ thú mới và nhặt lại đá quý";
                } else if (now>=deadline) {
                    if (view.animal && (view.cargo>0 || view.full)) {
                        state=replaceState; due=now; replaceCooldown=now+30000;
                        status="Không cất được thú; tiếp tục buôn";
                    } else fail("Không xác nhận được cất thú để thay");
                }
                break;
            case WAIT_NEW:
                status="Chờ thú mới nhặt lại đá quý";
                if (view.animal && (view.cargo>0 || view.full)) {
                    replaceCooldown=now+30000;
                    state=replaceState; due=now;
                    break;
                }
                if (view.gemDrops) pickupUntil=now+2000;
                if (view.animal && !view.gemDrops && now>=pickupUntil) {
                    if (previousCargo>0) { view.cargo=previousCargo; view.cargoRevision++; }
                    view.full=(view.cargo>=TradeConfig.MAX_CARGO[config.animal]);
                    replaceCooldown=now+30000;
                    state=replaceState; due=now;
                } else if (now>=deadline) {
                    if (view.gemDrops) deadline=now+15000;
                    else status="Chờ nhặt đá quý hoặc thú mới; kiểm tra túi đồ";
                }
                break;
            case BACKOFF:
                if (view.market!=0) port.goMarket(0);
                else { emptyMask=0; state=STOCK; due=now+60000; awaitingStock=false; stock=null; }
                break;
        }
    }
}
