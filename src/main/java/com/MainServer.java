package com;

import com.server.MyServer;

public class MainServer {
    public static void main(String[] args) {
        MyServer myServer = new MyServer();
        myServer.run(args);
    }
}
