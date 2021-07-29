package com;

import com.server.MyServer;

import java.io.IOException;

public class MainServer {
    public static void main(String[] args) throws IOException {
        MyServer myServer = new MyServer(args);
        myServer.run();
    }
}
