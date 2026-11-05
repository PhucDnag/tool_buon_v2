package CLib;

import java.io.*;
import java.net.Socket;
import javax.microedition.io.Connector;
import javax.microedition.io.SocketConnection;
import autotrade.ProxyConnection;

public class mSocket {
    SocketConnection s;
    private Socket proxy;
    public mSocket(String host,int port) throws IOException {
        if (ProxyConnection.enabled()) proxy=ProxyConnection.connect(host,port);
        else s=(SocketConnection)Connector.open("socket://"+host+":"+port);
    }
    public void close() {
        try {
            if (proxy!=null) { proxy.close(); ProxyConnection.status("Đã ngắt kết nối game"); }
            if (s!=null) s.close();
        } catch (IOException ignored) {}
    }
    public void setKeepAlive(boolean value) {}
    public DataOutputStream getOutputStream() throws IOException {
        return proxy!=null ? new DataOutputStream(proxy.getOutputStream()) : s.openDataOutputStream();
    }
    public DataInputStream getInputStream() throws IOException {
        return proxy!=null ? new DataInputStream(proxy.getInputStream()) : s.openDataInputStream();
    }
}
