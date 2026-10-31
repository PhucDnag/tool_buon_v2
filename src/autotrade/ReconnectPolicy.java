package autotrade;

/** Socket state takes precedence over retry timers and selection callbacks. */
public final class ReconnectPolicy {
    private ReconnectPolicy() {}
    public static boolean enteredGame(boolean connected, boolean gameScreen, boolean hasPlayer) {
        return connected && gameScreen && hasPlayer;
    }
    public static boolean shouldLogin(boolean connected, boolean connecting, long now, long retryAt) {
        return !connected && !connecting && now>=retryAt;
    }
}
