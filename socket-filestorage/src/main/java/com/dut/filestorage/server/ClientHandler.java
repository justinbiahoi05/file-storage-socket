package com.dut.filestorage.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import com.dut.filestorage.model.service.UserService;

public class ClientHandler extends Thread {
    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;
    private UserService userService; // Controller sẽ sử dụng Service

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
        this.userService = new UserService();
    }

    @Override
    public void run() {
        try {
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                System.out.println("Received: " + inputLine);
                processCommand(inputLine);
            }
        } catch (IOException e) {
            System.out.println("Client disconnected.");
        } finally {
            // ... cleanup resources ...
        }
    }
    
    private void processCommand(String commandLine) {
        String[] parts = commandLine.split(" ", 4);
        String command = parts[0].toUpperCase();

        switch (command) {
            case "REGISTER":
                handleRegister(parts);
                break;
            // Các case khác...
            default:
                out.println("500 ERROR Unknown command");
        }
    }

    private void handleRegister(String[] parts) {
        if (parts.length < 4) {
            out.println("400 ERROR Bad syntax. Usage: REGISTER <username> <password> <email>");
            return;
        }
        try {
            String username = parts[1];
            String password = parts[2];
            String email = parts[3];
            
            userService.registerUser(username, password, email);
            out.println("200 OK Registration successful.");
        } catch (Exception e) {
            out.println("400 ERROR " + e.getMessage());
        }
    }
}