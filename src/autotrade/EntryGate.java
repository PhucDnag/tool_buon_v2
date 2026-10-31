package autotrade;

/** Let the original client finish map entry before sending tool actions. */
public final class EntryGate {
    private long enteredAt=-1;
    public boolean ready(boolean inGame,long now) {
        if (!inGame) { enteredAt=-1; return false; }
        if (enteredAt<0) enteredAt=now;
        return now-enteredAt>=5000;
    }
}
