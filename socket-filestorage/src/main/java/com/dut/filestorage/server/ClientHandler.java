package com.dut.filestorage.server;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.sql.SQLException;
import java.util.List;

import com.dut.filestorage.model.dao.FileDAO;
import com.dut.filestorage.model.dao.UserDAO;
import com.dut.filestorage.model.entity.File;
import com.dut.filestorage.model.entity.User;
import com.dut.filestorage.model.service.CollaborationService;
import com.dut.filestorage.model.service.FileSystemService;
import com.dut.filestorage.model.service.UserService;
import com.dut.filestorage.utils.PasswordUtils;

public class ClientHandler extends Thread {
    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;

    // Các Service sẽ được sử dụng
    private UserService userService;
    private FileSystemService fileSystemService;
    private FileDAO fileDAO;
    private CollaborationService collaborationService;

    // Trạng thái của client
    private User loggedInUser = null;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
        this.userService = new UserService();
        this.fileSystemService = new FileSystemService();
        this.fileDAO = new FileDAO();
        this.collaborationService = new CollaborationService();
    }

    @Override
    public void run() {
       try (
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        ) {
            this.out = out; // Lưu lại để các hàm khác có thể dùng
            this.in = in;

            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                System.out.println("Received from " + clientSocket.getInetAddress() + ": " + inputLine);
                processCommand(inputLine);
            }
        } catch (IOException e) {
            System.out.println("Client " + clientSocket.getInetAddress() + " disconnected.");
        } finally {
            try {
                if (clientSocket != null) clientSocket.close();
            } catch (IOException e) { /* ignore */ }
        }
    }

    private void processCommand(String commandLine) {
        String[] parts = commandLine.split(" ");
        String command = parts[0].toUpperCase();

        switch (command) {
            case "REGISTER":
                handleRegister(parts);
                break;
            case "LOGIN":
                handleLogin(parts);
                break;
            case "MKDIR":
                handleMkdir(parts);
                break;
             case "LS":
                if (parts.length > 1 && "--shared".equalsIgnoreCase(parts[1])) {
                 handleLsShared();
                } else {
                    handleLs(parts);
                }
            break;
            case "DOWNLOAD":
                handleDownload(parts);
                break;
            case "DELETE":
                handleDelete(parts);
                break;
            case "UPLOAD":
                handleUpload(parts);
                break;
             case "SHARE":
                handleShare(parts);
                break;
            case "GROUP_CREATE":
                handleGroupCreate(parts);
                break;
            case "GROUP_INVITE":
                handleGroupInvite(parts);
            break;
            default:
                out.println("500 ERROR Unknown command: " + command);
        }
    }

    // --- CÁC HÀM XỬ LÝ LỆNH ---

    private void handleRegister(String[] parts) {
        if (parts.length < 4) {
            out.println("400 ERROR Bad syntax. Usage: REGISTER <username> <password> <email>");
            return;
        }
        try {
            userService.registerUser(parts[1], parts[2], parts[3]);
            out.println("200 OK Registration successful.");
        } catch (Exception e) {
            out.println("400 ERROR " + e.getMessage());
        }
    }

    private void handleLogin(String[] parts) {
        if (loggedInUser != null) {
            out.println("400 ERROR You are already logged in as " + loggedInUser.getUsername());
            return;
        }
        if (parts.length < 3) {
            out.println("400 ERROR Bad syntax. Usage: LOGIN <username> <password>");
            return;
        }
        
        try {
            UserDAO userDAO = new UserDAO(); // Tạm thời new ở đây, sau này có thể tối ưu
            User user = userDAO.findByUsername(parts[1]);

            if (user != null && PasswordUtils.checkPassword(parts[2], user.getPasswordHash())) {
                this.loggedInUser = user;
                out.println("200 OK Login successful. Welcome " + user.getUsername());
            } else {
                out.println("401 ERROR Invalid username or password.");
            }
        } catch (SQLException e) {
            out.println("500 ERROR Database error during login: " + e.getMessage());
        }
    }

    private void handleMkdir(String[] parts) {
        if (loggedInUser == null) {
            out.println("401 ERROR You must be logged in to create a directory.");
            return;
        }
        if (parts.length < 2) {
            out.println("400 ERROR Bad syntax. Usage: MKDIR <folder_name>");
            return;
        }

        try {
            // Tạm thời, mặc định tạo thư mục ở gốc (parentFolderId = null)
            fileSystemService.createDirectory(parts[1], loggedInUser.getId(), null);
            out.println("200 OK Directory '" + parts[1] + "' created.");
        } catch (Exception e) {
            out.println("400 ERROR " + e.getMessage());
        }
    }

    private void handleUpload(String[] parts) {
        if (loggedInUser == null) {
            out.println("401 ERROR You must be logged in to upload a file.");
            return;
        }
        // Cú pháp: UPLOAD <file_name> <file_size> <file_type>
        if (parts.length < 4) {
            out.println("400 ERROR Bad syntax. Usage: UPLOAD <file_name> <file_size> <file_type>");
            return;
        }

        try {
            String fileName = parts[1];
            long fileSize = Long.parseLong(parts[2]);
            String fileType = parts[3];

            // 1. Báo cho client là server đã sẵn sàng nhận file
            out.println("201 READY");

            // 2. Nhận dữ liệu file thô từ client
            // Lấy InputStream trực tiếp từ socket để đọc dữ liệu byte
            InputStream socketInputStream = clientSocket.getInputStream();
            
            // Tạo một file tạm để lưu dữ liệu nhận được
            java.io.File tempFile = java.io.File.createTempFile("upload-", ".tmp");
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                long totalBytesRead = 0;
                
                // Vòng lặp đọc dữ liệu cho đến khi đủ số byte đã báo trước
                while (totalBytesRead < fileSize && (bytesRead = socketInputStream.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalBytesRead))) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                }
            }

            if (fileSize != tempFile.length()) {
                 out.println("500 ERROR File size mismatch.");
                 tempFile.delete();
                 return;
            }
            
            // 3. Gọi Service để xử lý file tạm này
            // Tạo một InputStream mới từ file tạm để đưa cho service
            try (InputStream tempFileInputStream = new java.io.FileInputStream(tempFile)) {
                fileSystemService.storeFile(tempFileInputStream, fileName, fileSize, fileType, loggedInUser.getId(), null);
                // 4. Báo thành công cho client
                out.println("202 OK File uploaded successfully.");
            } finally {
                // Xóa file tạm đi sau khi đã xử lý xong
                tempFile.delete();
            }
            
        } catch (NumberFormatException e) {
            out.println("400 ERROR Invalid file size.");
        } catch (Exception e) {
            out.println("500 ERROR " + e.getMessage());
            e.printStackTrace(); // In lỗi ra console server để debug
        }
    }
    
    private void handleLs(String[] parts) {
        if (loggedInUser == null) { /*error */ return; }
        try {
            List<File> files = fileSystemService.listFiles(loggedInUser.getId());
            if (files.isEmpty()) {
                out.println("200 OK Directory is empty.");
                out.println("END_OF_LIST"); // Vẫn gửi tín hiệu kết thúc
            } else {
                out.println("200 OK --- File List ---");
                for (File file : files) {
                    out.println(String.format("ID: %d | Name: %s | Size: %d bytes",
                            file.getId(), file.getFileName(), file.getFileSize()));
                }
                out.println("END_OF_LIST"); // Gửi tín hiệu báo hết danh sách
            }
        } catch (Exception e) {
            out.println("500 ERROR " + e.getMessage());
        }
    }

    private void handleDelete(String[] parts) {
        if (loggedInUser == null) { out.println("401 ERROR Not logged in."); return; }
        if (parts.length < 2) { out.println("400 ERROR Usage: DELETE <file_id>"); return; }
        
        try {
            long fileId = Long.parseLong(parts[1]);
            // Gọi hàm service đã được nâng cấp
            String resultMessage = fileSystemService.deleteOrRemoveShare(fileId, loggedInUser.getId());
            // In ra thông báo mà service trả về
            out.println("200 OK " + resultMessage);

        } catch (NumberFormatException e) {
            out.println("400 ERROR Invalid file ID.");
        } catch (Exception e) {
            out.println("400 ERROR " + e.getMessage());
        }
    }

    private void handleDownload(String[] parts) {
        if (loggedInUser == null) {
            out.println("401 ERROR Not logged in.");
            return;
        }
        // Cú pháp mới mà server mong đợi: DOWNLOAD <file_id>
        if (parts.length < 2) {
            out.println("400 ERROR Usage: DOWNLOAD <file_id>");
            return;
        }

        try {
            long fileId = Long.parseLong(parts[1]);
            
            File fileToDownload = fileDAO.findById(fileId); // Cần có UserDAO và FileDAO
            
            if (fileToDownload == null) {
                out.println("404 ERROR File not found.");
                return;
            }

            // 1. Gửi thông tin file về cho client trước
            out.println("201 INFO " + fileToDownload.getFileName() + " " + fileToDownload.getFileSize());
            
            // 2. Chờ client xác nhận sẵn sàng
            String clientResponse = in.readLine();
            if (clientResponse != null && clientResponse.equals("CLIENT_READY")) {
                // 3. Bắt đầu gửi dữ liệu file
                fileSystemService.downloadFile(fileId, loggedInUser.getId(), clientSocket.getOutputStream());
            } else {
                // Client đã hủy, không làm gì cả
                System.out.println("Client canceled download for file ID: " + fileId);
            }
        } catch (NumberFormatException e) {
            out.println("400 ERROR Invalid file ID format.");
        } catch (Exception e) {
            out.println("500 ERROR " + e.getMessage());
            e.printStackTrace();
        }
    }
    private void handleShare(String[] parts) {
        if (loggedInUser == null) { out.println("401 ERROR Not logged in."); return; }
        // Cú pháp: SHARE <file_id> <target_username>
        if (parts.length < 3) {
            out.println("400 ERROR Usage: SHARE <file_id> <target_username>");
            return;
        }

        try {
            long fileId = Long.parseLong(parts[1]);
            String targetUsername = parts[2];
            collaborationService.shareFileWithUser(fileId, loggedInUser.getId(), targetUsername);
            out.println("200 OK File shared successfully with " + targetUsername);
        } catch (NumberFormatException e) {
            out.println("400 ERROR Invalid file ID.");
        } catch (Exception e) {
            out.println("400 ERROR " + e.getMessage());
        }
    }

    private void handleGroupCreate(String[] parts) {
        if (loggedInUser == null) { out.println("401 ERROR Not logged in."); return; }
        // Cú pháp: GROUP_CREATE <group_name>
        if (parts.length < 2) {
            out.println("400 ERROR Usage: GROUP_CREATE <group_name>");
            return;
        }
        
        String groupName = parts[1];
        try {
            collaborationService.createGroup(groupName, loggedInUser.getId());
            out.println("200 OK Group '" + groupName + "' created successfully.");
        } catch (Exception e) {
            out.println("400 ERROR " + e.getMessage());
        }
    }
    private void handleLsShared() {
    if (loggedInUser == null) { out.println("401 ERROR Not logged in."); return; }
    
    try {
        List<File> files = collaborationService.listSharedFiles(loggedInUser.getId());
        if (files.isEmpty()) {
            out.println("200 OK No files have been shared with you.");
        } else {
            out.println("200 OK --- Shared Files ---");
            for (File file : files) {
                 out.println(String.format("ID: %d | Name: %s | Size: %d bytes",
                        file.getId(), 
                        file.getFileName(), 
                        file.getFileSize()));
            }
        }
    } catch (Exception e) {
        out.println("500 ERROR " + e.getMessage());
    } finally {
        out.println("END_OF_LIST");
    }
}

    private void handleGroupInvite(String[] parts) {
        if (loggedInUser == null) { out.println("401 ERROR Not logged in."); return; }
        // Cú pháp: GROUP_INVITE <group_id> <target_username>
        if (parts.length < 3) {
            out.println("400 ERROR Usage: GROUP_INVITE <group_id> <target_username>");
            return;
        }
        
        try {
            long groupId = Long.parseLong(parts[1]);
            String targetUsername = parts[2];
            collaborationService.inviteUserToGroup(groupId, loggedInUser.getId(), targetUsername);
            out.println("200 OK " + targetUsername + " has been invited to the group.");
        } catch (NumberFormatException e) {
            out.println("400 ERROR Invalid group ID.");
        } catch (Exception e) {
            out.println("400 ERROR " + e.getMessage());
        }
    }
        
}