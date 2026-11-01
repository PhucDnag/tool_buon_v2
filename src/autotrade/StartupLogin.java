package autotrade;

/** One automatic attempt per process; failures remain visible for manual action. */
public final class StartupLogin {
    private boolean attempted;
    public boolean claim(boolean ready, boolean connected, boolean connecting) {
        if (attempted || !ready || connected || connecting) return false;
        attempted=true;
        return true;
    }
}
