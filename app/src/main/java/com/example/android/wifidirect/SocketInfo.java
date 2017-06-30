package com.example.android.wifidirect;

import java.net.InetAddress;


public class SocketInfo {

    public static InetAddress getInetAddress() {
        return inetAddress;
    }

    public static void setInetAddress(InetAddress inetAddress) {
        SocketInfo.inetAddress = inetAddress;
    }

    public static InetAddress inetAddress;
}
