package com.dut.filestorage;

import com.dut.filestorage.server.ServerListener;

public class MainServer {
    public static void main(String[] args) {
        int port = 9999;
        ServerListener server = new ServerListener(port);
        server.start();
    }
}