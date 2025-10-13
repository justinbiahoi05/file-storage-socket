package com.dut.filestorage.client;

import java.io.IOException;

public class SocketClientSingleton {

    private static SocketClientSingleton instance;
    private SocketClient socketClient;

    private SocketClientSingleton() {
        try {
            this.socketClient = new SocketClient("127.0.0.1", 9999);
        } catch (IOException e) {
            System.err.println("Failed to connect to the server on startup.");
            this.socketClient = null;
            e.printStackTrace();
        }
    }

    public static synchronized SocketClientSingleton getInstance() {
        if (instance == null) {
            instance = new SocketClientSingleton();
        }
        return instance;
    }

    public SocketClient getSocketClient() {
        return this.socketClient;
    }
    
     public boolean reconnect() {
        // Đóng kết nối cũ nếu nó vẫn còn tồn tại
        close(); 
        
        // Cố gắng tạo lại một kết nối mới
        try {
            this.socketClient = new SocketClient("127.0.0.1", 9999);
            System.out.println("Reconnected to the server successfully.");
            return true;
        } catch (IOException e) {
            System.err.println("Failed to reconnect to the server.");
            this.socketClient = null;
            e.printStackTrace();
            return false;
        }
    }
    
    public void close() {
        if (socketClient != null) {
            try {
                socketClient.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            socketClient = null;
        }
    }
}