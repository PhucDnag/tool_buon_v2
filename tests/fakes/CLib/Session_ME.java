package CLib;
public class Session_ME { public static final Session_ME instance=new Session_ME(); public boolean connected=true; public static Session_ME gI(){return instance;} public boolean isConnected(){return connected;} }
