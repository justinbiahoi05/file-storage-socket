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
            // Thiết lập kết nối và các stream
            socket = new Socket(hostname, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            System.out.println("Connected to the server. Type 'HELP' for a list of commands.");
            
            // Chạy một luồng riêng để lắng nghe các thông điệp từ server
            // Điều này hữu ích cho các thông báo bất đồng bộ sau này
            // Tạm thời, luồng này sẽ không làm gì nhiều
            Thread serverListener = new Thread(() -> {
                try {
                    // Tạm thời để trống
                } catch (Exception e) {
                    System.out.println("\nConnection to server lost.");
                }
            });
            serverListener.start();

            // Vòng lặp chính để nhận lệnh từ người dùng
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

    // Hàm xử lý logic chính, phân tích lệnh người dùng
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
                handleMultiLineCommand(userInput);
                break;
            case "HELP":
                printHelp();
                break;
            default:
                // Các lệnh đơn giản khác (REGISTER, LOGIN, MKDIR, DELETE)
                handleSingleLineCommand(userInput);
                break;
        }
    }
    
    // Xử lý các lệnh chỉ có 1 dòng phản hồi
    private void handleSingleLineCommand(String command) throws IOException {
        out.println(command);
        System.out.println("Server: " + in.readLine());
    }

    // Xử lý các lệnh có thể có nhiều dòng phản hồi (như LS)
    private void handleMultiLineCommand(String command) throws IOException {
    out.println(command); // Gửi lệnh đi
    
    String serverResponse;
    // Vòng lặp này sẽ đọc cho đến khi gặp đúng tín hiệu kết thúc
    while ((serverResponse = in.readLine()) != null) {
        // Nếu gặp tín hiệu kết thúc, thoát khỏi vòng lặp ngay lập tức
        if ("END_OF_LIST".equals(serverResponse)) {
            break; 
        }
        
        // Nếu không phải tín hiệu kết thúc, in nó ra
        System.out.println(serverResponse);
    }
}
    
    // Logic upload (gần như giữ nguyên, chỉ tách ra)
    private void handleUpload(String[] parts) throws IOException {
        if (parts.length < 2) {
            System.out.println("Usage: UPLOAD <path_to_local_file>");
            return;
        }
        
        File file = new File(parts[1]);
        if (!file.exists() || !file.isFile()) {
            System.out.println("Error: File not found or is a directory.");
            return;
        }

        String fileName = file.getName();
        long fileSize = file.length();
        String fileType = "application/octet-stream"; 

        String metadataCommand = "UPLOAD " + fileName + " " + fileSize + " " + fileType;
        out.println(metadataCommand);

        String serverResponse = in.readLine();
        System.out.println("Server: " + serverResponse);

        if (serverResponse != null && serverResponse.startsWith("201 READY")) {
            System.out.println("Server is ready. Starting file transfer...");
            
            try (FileInputStream fis = new FileInputStream(file)) {
                OutputStream socketOutputStream = socket.getOutputStream();
                byte[] buffer = new byte[4096];
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

    private void handleDownload(String[] parts) throws IOException {
        // Cú pháp: DOWNLOAD <file_id> <path_to_save_directory>
        if (parts.length < 3) {
            System.out.println("Usage: DOWNLOAD <file_id> <path_to_save_directory>");
            return;
        }
        
        String fileIdStr = parts[1];
        String savePath = parts[2];
        
        // 1. Gửi lệnh ngắn gọn cho server
        out.println("DOWNLOAD " + fileIdStr);

        // 2. Nhận phản hồi từ server
        String serverResponse = in.readLine();
        
        // 3. KIỂM TRA PHẢN HỒI TRƯỚC KHI LÀM TIẾP
        if (serverResponse == null || !serverResponse.startsWith("201 INFO")) {
            // Nếu server trả về lỗi (400, 401, 404, 500...) hoặc null, in ra và dừng lại
            System.out.println("Server: " + serverResponse);
            System.out.println("Could not prepare the file for download.");
            return; // Dừng hàm tại đây
        }

        // Nếu không có lỗi, tiếp tục xử lý
        System.out.println("Server: " + serverResponse);
        String[] infoParts = serverResponse.split(" ");
        
        String fileName = infoParts[2];
        long fileSize = Long.parseLong(infoParts[3]);
        
        File saveDir = new File(savePath);
        if (!saveDir.exists()) saveDir.mkdirs();

        // 4. Báo sẵn sàng
        out.println("CLIENT_READY");
        System.out.println("Client is ready. Receiving file: " + fileName);

        // 5. Nhận dữ liệu file
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
    
    private void printHelp() {
        System.out.println("--- Available Commands ---");
        System.out.println("REGISTER <user> <pass> <email>   - Register a new account");
        System.out.println("LOGIN <user> <pass>            - Log in to your account");
        System.out.println("LS                               - List files and folders in current directory");
        System.out.println("MKDIR <folder_name>              - Create a new directory");
        System.out.println("UPLOAD <local_file_path>         - Upload a file to the current directory");
        System.out.println("DOWNLOAD <file_id> <save_path>     - Download a file by its ID");
        System.out.println("DELETE <file_id>                 - Delete a file by its ID");
        System.out.println("QUIT                             - Disconnect from the server");
        System.out.println("-------------------------");
    }

    public static void main(String[] args) {
        String hostname = "127.0.0.1";
        int port = 9999;
        ClientConsole client = new ClientConsole(hostname, port);
        client.start();
    }
}