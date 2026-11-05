package autotrade;

import java.io.*;
import java.net.*;

/** SOCKS5 CONNECT with explicit authentication and no direct fallback. */
public final class ProxyConnection {
    private static String state="Chưa kết nối";
    private static String setting(String key,String fallback) {
        String value=System.getProperty(key); return value==null ? fallback : value;
    }
    public static boolean enabled() { return "1".equals(System.getProperty("phuc.proxy.enabled")); }
    public static synchronized String hud() {
        return enabled() ? "Proxy: "+state : "Proxy: Tắt (IP mạng hiện tại)";
    }
    public static synchronized void status(String value) {
        state=value;
        String path=System.getProperty("phuc.control");
        if (path==null) return;
        try {
            Writer out=new OutputStreamWriter(new FileOutputStream(path+".proxy"),"UTF-8");
            try { out.write(value); } finally { out.close(); }
        } catch (IOException ignored) {}
    }
    public static Socket connect(String target,int port) throws IOException {
        status("Đang kết nối SOCKS5");
        Socket socket=new Socket(Proxy.NO_PROXY);
        try {
            String host=setting("phuc.proxy.host","");
            int proxyPort=Integer.parseInt(setting("phuc.proxy.port","0"));
            if (host.length()==0 || proxyPort<1 || proxyPort>65535) throw new IOException("Cấu hình proxy không hợp lệ");
            socket.connect(new InetSocketAddress(host,proxyPort),10000);
            socket.setSoTimeout(10000);
            DataInputStream in=new DataInputStream(socket.getInputStream());
            DataOutputStream out=new DataOutputStream(socket.getOutputStream());
            byte[] user=setting("phuc.proxy.user","").getBytes("UTF-8");
            byte[] pass=setting("phuc.proxy.pass","").getBytes("UTF-8");
            int method=user.length==0 ? 0 : 2;
            if (user.length>255 || pass.length>255) throw new IOException("Thông tin xác thực quá dài");
            out.write(new byte[]{5,1,(byte)method}); out.flush();
            if (in.readUnsignedByte()!=5 || in.readUnsignedByte()!=method) throw new IOException("Proxy từ chối phương thức xác thực");
            if (method==2) {
                out.writeByte(1); out.writeByte(user.length); out.write(user);
                out.writeByte(pass.length); out.write(pass); out.flush();
                if (in.readUnsignedByte()!=1 || in.readUnsignedByte()!=0) throw new IOException("Sai tài khoản hoặc mật khẩu proxy");
            }
            byte[] address=target.getBytes("UTF-8");
            if (address.length<1 || address.length>255 || port<1 || port>65535) throw new IOException("Địa chỉ game không hợp lệ");
            out.write(new byte[]{5,1,0,3}); out.writeByte(address.length); out.write(address); out.writeShort(port); out.flush();
            int version=in.readUnsignedByte(),reply=in.readUnsignedByte(),reserved=in.readUnsignedByte(),type=in.readUnsignedByte();
            if (version!=5 || reserved!=0) throw new IOException("Phản hồi SOCKS5 không hợp lệ");
            if (reply!=0) throw new IOException("Proxy không nối được game (mã "+reply+")");
            int count=type==1 ? 4 : type==4 ? 16 : type==3 ? in.readUnsignedByte() : -1;
            if (count<0) throw new IOException("Địa chỉ SOCKS5 không hợp lệ");
            byte[] bound=new byte[count+2]; in.readFully(bound);
            socket.setSoTimeout(0); socket.setKeepAlive(true);
            status("Đã nối game qua SOCKS5");
            return socket;
        } catch (Exception error) {
            try { socket.close(); } catch (IOException ignored) {}
            String reason=error instanceof SocketTimeoutException ? "Hết thời gian chờ" :
                error instanceof ConnectException ? "Không tới được proxy" :
                error instanceof EOFException ? "Proxy đóng kết nối" :
                error instanceof IOException ? error.getMessage() : "Cấu hình proxy không hợp lệ";
            status("Lỗi: "+reason);
            throw new IOException("SOCKS5: "+reason);
        }
    }
}
