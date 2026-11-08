import autotrade.StartupLogin;
public final class StartupLoginTest {
    private static void check(boolean ok) { if (!ok) throw new AssertionError("Startup login gate"); }
    public static void main(String[] args) {
        StartupLogin login=new StartupLogin();
        check(!login.claim(false,false,false)); // Missing credentials/server or open dialog.
        check(!login.claim(true,false,true)); // Connection in progress.
        check(!login.claim(true,true,false)); // Already connected.
        check(StartupLogin.waitingReason("user","pass",-1,true,false,false).indexOf("máy chủ")>=0);
        check(StartupLogin.waitingReason("user","",0,true,false,false).indexOf("mật khẩu")>=0);
        check(StartupLogin.waitingReason("user","pass",0,true,true,false).indexOf("thông báo")>=0);
        check(login.pending());
        check(login.claim(true,false,false));
        check(!login.pending()); // Fresh profile: no RMS needed.
        for (int i=0;i<100;i++) check(!login.claim(true,false,false)); // Failure must not spam login.
        check(new StartupLogin().claim(true,false,false)); // Another account is independent.
        System.out.println("PASS startup login readiness, one-shot and account isolation");
    }
}
