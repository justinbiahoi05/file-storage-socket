package com.dut.filestorage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class ClientConsole {
    private String hostname;
    private int port;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    public ClientConsole(String hostname, int port) {
        this.hostname = hostname;
        this.port = port;
    }

    public void start() {
        try {
            socket = new Socket(hostname, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            System.out.println("Connected to the server. Type 'HELP' for a list of commands.");

            Scanner consoleScanner = new Scanner(System.in);
            while (true) {
                System.out.print("> ");
                String userInput = consoleScanner.nextLine();

                if (userInput.isEmpty()) continue;

                if ("QUIT".equalsIgnoreCase(userInput)) {
                    out.println("QUIT");
                    break;
                }

                processUserInput(userInput);
            }
        } catch (IOException e) {
            System.err.println("Client exception: " + e.getMessage());
        } finally {
            try {
                if (socket != null) socket.close();
            } catch (IOException e) { /* ignore */ }
        }
    }

    private void processUserInput(String userInput) throws IOException {
        String[] parts = userInput.split(" ");
        String command = parts[0].toUpperCase();

        switch (command) {
            case "UPLOAD":
                handleUpload(parts);
                break;
            case "DOWNLOAD":
                handleDownload(parts);
                break;
            case "LS":
                // LS là lệnh đa dòng, nên nó cần hàm xử lý riêng
                handleMultiLineCommand(userInput);
                break;
            case "HELP":
                printHelp();
                break;
            default:
                // Các lệnh còn lại hầu hết là lệnh đơn giản, chỉ có 1 dòng phản hồi
                handleSingleLineCommand(userInput);
                break;
        }
    }

    private void handleSingleLineCommand(String command) throws IOException {
        out.println(command);
        String serverResponse = in.readLine();
        // Một số lệnh đơn giản không có phản hồi (như QUIT), cần kiểm tra null
        if (serverResponse != null) {
            System.out.println("Server: " + serverResponse);
        }
    }

    private void handleMultiLineCommand(String command) throws IOException {
        out.println(command);
        String serverResponse;
        while ((serverResponse = in.readLine()) != null) {
            if ("END_OF_LIST".equals(serverResponse)) {
                break;
            }
            // Không in tiền tố "Server:" để hiển thị danh sách cho đẹp
            System.out.println(serverResponse);
        }
    }

    // --- HÀM UPLOAD ĐÃ SỬA LỖI PHÂN TÍCH ---
    private void handleUpload(String[] parts) throws IOException {
        // Tìm vị trí của cờ --group
        int groupFlagIndex = -1;
        for (int i = 1; i < parts.length; i++) {
            if ("--group".equalsIgnoreCase(parts[i])) {
                groupFlagIndex = i;
                break;
            }
        }

        String filePath;
        String groupIdStr = null;

        if (groupFlagIndex != -1) { // Nếu tìm thấy --group
            // Kiểm tra xem có group_id đi kèm không
            if (groupFlagIndex + 1 >= parts.length) {
                System.out.println("Usage: UPLOAD <path_to_local_file> --group <group_id>");
                return;
            }
            groupIdStr = parts[groupFlagIndex + 1];

            // Ghép các phần từ sau lệnh UPLOAD đến trước --group thành đường dẫn file
            StringBuilder pathBuilder = new StringBuilder();
            for (int i = 1; i < groupFlagIndex; i++) {
                pathBuilder.append(parts[i]).append(" ");
            }
            filePath = pathBuilder.toString().trim();
        } else { // Nếu không có --group
            // Ghép tất cả các phần còn lại thành đường dẫn file
            StringBuilder pathBuilder = new StringBuilder();
            for (int i = 1; i < parts.length; i++) {
                pathBuilder.append(parts[i]).append(" ");
            }
            filePath = pathBuilder.toString().trim();
        }

        if (filePath.isEmpty()) {
            System.out.println("Usage: UPLOAD <path_to_local_file> [--group <group_id>]");
            return;
        }

        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            System.out.println("Error: File not found or is a directory at '" + filePath + "'");
            return;
        }
        
        // Gửi metadata
        String fileName = file.getName();
        long fileSize = file.length();
        String fileType = "application/octet-stream";

        String metadataCommand = "UPLOAD " + fileName + " " + fileSize + " " + fileType;
        if (groupIdStr != null) {
            metadataCommand += " --group " + groupIdStr;
        }
        out.println(metadataCommand);
        
        // Chờ server sẵn sàng và gửi file
        String serverResponse = in.readLine();
        System.out.println("Server: " + serverResponse);

        if (serverResponse != null && serverResponse.startsWith("201 READY")) {
            System.out.println("Server is ready. Starting file transfer...");
            
            try (FileInputStream fis = new FileInputStream(file)) {
                OutputStream socketOutputStream = socket.getOutputStream();
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    socketOutputStream.write(buffer, 0, bytesRead);
                }
                socketOutputStream.flush();
            }
            
            serverResponse = in.readLine();
            System.out.println("Server: " + serverResponse);
        } else {
            System.out.println("Server did not approve upload. Aborting.");
        }
    }

    // --- HÀM DOWNLOAD ĐÃ SỬA LỖI PHÂN TÍCH ---
    private void handleDownload(String[] parts) throws IOException {
        if (parts.length < 3) {
            System.out.println("Usage: DOWNLOAD <file_id> <path_to_save_directory>");
            return;
        }
        
        String fileIdStr = parts[1];
        
        StringBuilder savePathBuilder = new StringBuilder();
        for (int i = 2; i < parts.length; i++) {
            savePathBuilder.append(parts[i]).append(" ");
        }
        String savePath = savePathBuilder.toString().trim();
        
        out.println("DOWNLOAD " + fileIdStr);

        String serverResponse = in.readLine();
        
        if (serverResponse == null || !serverResponse.startsWith("201 INFO")) {
            System.out.println("Server: " + serverResponse);
            System.out.println("Could not prepare the file for download.");
            return;
        }

        System.out.println("Server: " + serverResponse);
        String[] infoParts = serverResponse.split(" ");
        
        // Cần cẩn thận hơn nếu tên file có khoảng trắng
        long fileSize = Long.parseLong(infoParts[infoParts.length - 1]);
        StringBuilder fileNameBuilder = new StringBuilder();
        for (int i = 2; i < infoParts.length - 1; i++) {
            fileNameBuilder.append(infoParts[i]).append(" ");
        }
        String fileName = fileNameBuilder.toString().trim();
        
        File saveDir = new File(savePath);
        if (!saveDir.exists()) {
            if(!saveDir.mkdirs()) {
                 System.out.println("Error: Could not create save directory.");
                 return;
            }
        }

        out.println("CLIENT_READY");
        System.out.println("Client is ready. Receiving file: " + fileName);

        try (FileOutputStream fos = new FileOutputStream(new File(saveDir, fileName))) {
            InputStream socketInputStream = socket.getInputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytesRead = 0;
            
            while (totalBytesRead < fileSize && (bytesRead = socketInputStream.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalBytesRead))) != -1) {
                fos.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
            }
            System.out.println("File '" + fileName + "' download completed successfully!");
        }
    }
    
    // --- HÀM HELP ĐÃ CẬP NHẬT ---
    private void printHelp() {
        System.out.println("--- Available Commands (File-Centric Version) ---");
        System.out.println("REGISTER <user> <pass> <email>    - Register a new account");
        System.out.println("LOGIN <user> <pass>               - Log in to your account");
        System.out.println("LS                                - List your personal files");
        System.out.println("LS <group_id>                     - List files in a specific group");
        System.out.println("LS --shared                       - List files shared with you");
        System.out.println("LS --groups                       - List groups you are a member of");
        System.out.println("LS --members <group_id>           - List members of a group");
        System.out.println("UPLOAD <local_path> [--group <group_id>] - Upload a file to your space or a group");
        System.out.println("DOWNLOAD <file_id> <save_path>      - Download a file by its ID");
        System.out.println("DELETE <file_id>                  - Delete a file you own, or remove a shared file");
        System.out.println("SHARE <file_id> <username>          - Share a file with another user");
        System.out.println("GROUP_CREATE <group_name>         - Create a new group");
        System.out.println("GROUP_INVITE <group_id> <username>  - Invite a user to a group");
        System.out.println("GROUP_KICK <group_id> <username>    - Kick a user from a group");
        System.out.println("GROUP_DELETE <group_id>           - Delete a group (owner only)");
        System.out.println("QUIT                              - Disconnect from the server");
        System.out.println("-------------------------------------------------");
    }

    public static void main(String[] args) {
        String hostname = "127.0.0.1";
        int port = 9999;
        ClientConsole client = new ClientConsole(hostname, port);
        client.start();
    }
}