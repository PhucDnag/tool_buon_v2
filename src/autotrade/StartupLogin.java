package autotrade;

/** One automatic attempt per process; failures remain visible for manual action. */
public final class StartupLogin {
    private boolean attempted;
    public boolean pending() { return !attempted; }
    public static String waitingReason(String user,String pass,int server,boolean form,boolean dialog,boolean menu) {
        if (user==null || user.length()==0 || pass==null || pass.length()==0) return "Chưa đăng nhập: thiếu tài khoản hoặc mật khẩu đã lưu";
        if (server<0) return "Chưa đăng nhập: không tìm thấy máy chủ đã chọn trong danh sách game";
        if (dialog) return "Chưa đăng nhập: cần xử lý thông báo trong game";
        if (menu) return "Chưa đăng nhập: hãy đóng menu trong game";
        if (!form) return "Chưa đăng nhập: chờ màn hình tài khoản sẵn sàng";
        return "Đang bắt đầu đăng nhập qua proxy";
    }
    public boolean claim(boolean ready, boolean connected, boolean connecting) {
        if (attempted || !ready || connected || connecting) return false;
        attempted=true;
        return true;
    }
}
