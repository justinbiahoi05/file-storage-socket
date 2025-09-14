package com.dut.filestorage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class ClientConsole {
    public static void main(String[] args) {
        String hostname = "127.0.0.1";
        int port = 9999;

        try (
            Socket socket = new Socket(hostname, port);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            Scanner consoleScanner = new Scanner(System.in)
        ) {
            System.out.println("Connected. Type 'QUIT' to exit.");
            
            String userInput;
            while (true) {
                System.out.print("> ");
                userInput = consoleScanner.nextLine();
                
                out.println(userInput); // Gửi lệnh đến server
                
                if ("QUIT".equalsIgnoreCase(userInput)) {
                    break;
                }

                System.out.println("Server: " + in.readLine()); // Đọc và in phản hồi
            }

        } catch (IOException e) {
            System.err.println("Client exception: " + e.getMessage());
        }
    }
}