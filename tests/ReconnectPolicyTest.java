import autotrade.ReconnectPolicy;
public final class ReconnectPolicyTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check(ReconnectPolicy.enteredGame(true,true,true), "manual entry must finish reconnect");
        check(!ReconnectPolicy.enteredGame(false,true,true), "stale player is not success");
        check(!ReconnectPolicy.enteredGame(true,false,true), "loading is not entry");
        check(!ReconnectPolicy.enteredGame(true,true,false), "player must be ready");
        check(!ReconnectPolicy.shouldLogin(true,false,60000,30000), "never relogin on connected socket after 30s");
        check(!ReconnectPolicy.shouldLogin(true,true,60000,30000), "do not interrupt loading");
        check(!ReconnectPolicy.shouldLogin(false,true,60000,30000), "do not duplicate pending connection");
        check(!ReconnectPolicy.shouldLogin(false,false,29999,30000), "respect backoff");
        check(ReconnectPolicy.shouldLogin(false,false,30000,30000), "retry disconnected socket when due");
        autotrade.EntryGate gate=new autotrade.EntryGate();
        check(!gate.ready(true,0), "no tool traffic on first game tick");
        check(!gate.ready(true,4999), "wait for stable entry");
        check(gate.ready(true,5000), "resume after entry settles");
        check(!gate.ready(false,6000), "reset on disconnect or loading");
        check(!gate.ready(true,7000), "new connection gets its own delay");
        check(gate.ready(true,12000), "new connection resumes");
        System.out.println("PASS reconnect policy: "+checks+" assertions");
    }
}
