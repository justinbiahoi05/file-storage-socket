package com.dut.filestorage.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;

import com.dut.filestorage.model.dao.UserDAO;
import com.dut.filestorage.model.entity.File;
import com.dut.filestorage.model.entity.Group;
import com.dut.filestorage.model.entity.User;
import com.dut.filestorage.model.service.CollaborationService;
import com.dut.filestorage.model.service.FileSystemService;
import com.dut.filestorage.model.service.UserService;

public class ClientHandler extends Thread {
    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;

    // Các Service sẽ được sử dụng
    private UserService userService;
    private FileSystemService fileSystemService;
    private CollaborationService collaborationService;

    // Trạng thái của client
    private User loggedInUser = null;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
        
        // --- Dependency Injection Thủ công ---
        // Khởi tạo các đối tượng DAO một lần duy nhất
        UserDAO userDAO = new UserDAO();
        
        // Tiêm các DAO cần thiết vào các Service
        this.userService = new UserService(userDAO);
        this.collaborationService = new CollaborationService(userDAO);
        this.fileSystemService = new FileSystemService(collaborationService);
    }

    @Override
    public void run() {
        try {
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

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
            case "REGISTER": handleRegister(parts); break;
            case "LOGIN": handleLogin(parts); break;
            case "LS": handleLs(parts); break;
            case "UPLOAD": handleUpload(parts); break;
            case "DOWNLOAD": handleDownload(parts); break;
            case "DELETE": handleDelete(parts); break;
            case "SHARE": handleShare(parts); break;
            case "GROUP_CREATE": handleGroupCreate(parts); break;
            case "GROUP_INVITE": handleGroupInvite(parts); break;
            case "GROUP_KICK": handleGroupKick(parts); break;
            case "GROUP_DELETE": handleGroupDelete(parts); break;
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
            out.println("400 ERROR You are already logged in.");
            return;
        }
        if (parts.length < 3) {
            out.println("400 ERROR Bad syntax. Usage: LOGIN <username> <password>");
            return;
        }
        
        try {
            User user = userService.loginUser(parts[1], parts[2]);
            if (user != null) {
                this.loggedInUser = user;
                out.println("200 OK Login successful. Welcome " + user.getUsername());
            } else {
                out.println("401 ERROR Invalid username or password.");
            }
        } catch (Exception e) {
            out.println("500 ERROR Database error during login: " + e.getMessage());
        }
    }

    private void handleUpload(String[] parts) {
        if (loggedInUser == null) {
            out.println("401 ERROR Not logged in.");
            return;
        }
        
        // Cú pháp nội bộ Client gửi: UPLOAD <name> <size> <type> [--group <id>]
        if (parts.length < 4) {
            out.println("400 ERROR Bad syntax for UPLOAD command.");
            return;
        }

        try {
            String fileName = parts[1];
            long fileSize = Long.parseLong(parts[2]);
            String fileType = parts[3];
            Long groupId = null;

            // --- PHẦN SỬA LỖI QUAN TRỌNG NHẤT NẰM Ở ĐÂY ---
            // Vòng lặp này sẽ quét qua các tham số để tìm "--group"
            // thay vì giả định vị trí cố định của nó.
            for (int i = 4; i < parts.length - 1; i++) {
                if ("--group".equalsIgnoreCase(parts[i])) {
                    try {
                        groupId = Long.parseLong(parts[i + 1]);
                    } catch (NumberFormatException e) {
                        throw new Exception("Invalid group ID format provided with --group flag.");
                    }
                    break; // Tìm thấy thì dừng lại
                }
            }
            // --- KẾT THÚC SỬA LỖI ---

            // Bây giờ, biến `groupId` đã có giá trị đúng (hoặc vẫn là null nếu không có --group)
            
            fileSystemService.checkUploadPermissions(loggedInUser.getId(), groupId);
            
            out.println("201 READY");
            
            fileSystemService.receiveAndStoreFile(
                clientSocket.getInputStream(),
                fileName, fileSize, fileType,
                loggedInUser.getId(), groupId
            );
            
            out.println("202 OK File uploaded successfully.");

        } catch (NumberFormatException e) {
            out.println("400 ERROR Invalid number format in command.");
        } catch (Exception e) {
            out.println("500 ERROR " + e.getMessage());
            try { clientSocket.close(); } catch (IOException ioException) {}
        }
    }

    private void handleLs(String[] parts) {
        if (loggedInUser == null) { out.println("401 ERROR Not logged in."); out.println("END_OF_LIST"); return; }

        System.out.println("DEBUG: handleLs called with " + String.join(" ", parts)); // LOG 1

        try {
            if (parts.length > 1) {
                String flagOrId = parts[1];
                
                if ("--shared".equalsIgnoreCase(flagOrId)) {
                    System.out.println("DEBUG: Entering --shared branch."); // LOG 2
                    List<File> files = collaborationService.listSharedFiles(loggedInUser.getId());
                    printFileList(files, "Shared With Me");
                } else if ("--groups".equalsIgnoreCase(flagOrId)) {
                    System.out.println("DEBUG: Entering --groups branch."); // LOG 3
                    List<Group> groups = collaborationService.listUserGroups(loggedInUser.getId());
                    printGroupList(groups);
                } else if ("--members".equalsIgnoreCase(flagOrId)) {
                     System.out.println("DEBUG: Entering --members branch."); // LOG 4
                    if (parts.length < 3) throw new Exception("Usage: LS --members <group_id>");
                    long groupId = Long.parseLong(parts[2]);
                    List<User> members = collaborationService.listGroupMembers(groupId, loggedInUser.getId());
                    printUserList(members);
                } else {
                      System.out.println("DEBUG: Entering GROUP FILES branch for ID: " + flagOrId); // LOG 5
                    // Mặc định là liệt kê file trong group, ví dụ: LS 1
                    // đảm bảo đây là một con số
                    long groupId = Long.parseLong(flagOrId);
                    List<File> files = fileSystemService.listFilesInGroup(groupId, loggedInUser.getId());
                    System.out.println("DEBUG: Found " + files.size() + " files in group " + groupId); // LOG 6
                    printFileList(files, "Files in Group " + groupId);
                }
            } else {
                System.out.println("DEBUG: Entering PERSONAL FILES branch."); // LOG 7
                // Liệt kê file cá nhân
                List<File> files = fileSystemService.listFiles(loggedInUser.getId());
                printFileList(files, "My Files");
            }
        } catch (NumberFormatException e) {
            out.println("400 ERROR Invalid ID format. Expected a number for group ID.");
        } catch (Exception e) {
              System.err.println("DEBUG: EXCEPTION in handleLs: " + e.getMessage()); // LOG 8
            out.println("500 ERROR " + e.getMessage());
        } finally {
            out.println("END_OF_LIST");
        }
    }
    
    private void handleDelete(String[] parts) {
        if (loggedInUser == null) { out.println("401 ERROR Not logged in."); return; }
        if (parts.length < 2) { out.println("400 ERROR Usage: DELETE <file_id>"); return; }
        
        try {
            long fileId = Long.parseLong(parts[1]);
            String resultMessage = fileSystemService.deleteFile(fileId, loggedInUser.getId());
            out.println("200 OK " + resultMessage);
        } catch (NumberFormatException e) {
            out.println("400 ERROR Invalid file ID.");
        } catch (Exception e) {
            out.println("400 ERROR " + e.getMessage());
        }
    }

    private void handleDownload(String[] parts) {
        if (loggedInUser == null) { out.println("401 ERROR Not logged in."); return; }
        if (parts.length < 2) { out.println("400 ERROR Usage: DOWNLOAD <file_id>"); return; }

        try {
            long fileId = Long.parseLong(parts[1]);
            File fileToDownload = fileSystemService.getFileForDownload(fileId, loggedInUser.getId());
            
            out.println("201 INFO " + fileToDownload.getFileName() + " " + fileToDownload.getFileSize());
            
            String clientResponse = in.readLine();
            if (clientResponse != null && clientResponse.equals("CLIENT_READY")) {
                fileSystemService.streamFileToOutput(fileId, clientSocket.getOutputStream());
            } else {
                System.out.println("Client canceled download for file ID: " + fileId);
            }
        } catch (NumberFormatException e) {
            out.println("400 ERROR Invalid file ID format.");
        } catch (Exception e) {
            out.println("400 ERROR " + e.getMessage());
        }
    }
    
    private void handleShare(String[] parts) {
        if (loggedInUser == null) { out.println("401 ERROR Not logged in."); return; }
        if (parts.length < 3) { out.println("400 ERROR Usage: SHARE <file_id> <target_username>"); return; }

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
        if (parts.length < 2) { out.println("400 ERROR Usage: GROUP_CREATE <group_name>"); return; }
        
        String groupName = parts[1];
        try {
            collaborationService.createGroup(groupName, loggedInUser.getId());
            out.println("200 OK Group '" + groupName + "' created successfully.");
        } catch (Exception e) {
            out.println("400 ERROR " + e.getMessage());
        }
    }

    private void handleGroupInvite(String[] parts) {
        if (loggedInUser == null) { out.println("401 ERROR Not logged in."); return; }
        if (parts.length < 3) { out.println("400 ERROR Usage: GROUP_INVITE <group_id> <target_username>"); return; }
        
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

    private void handleGroupKick(String[] parts) {
        if (loggedInUser == null) { out.println("401 ERROR Not logged in."); return; }
        if (parts.length < 3) { out.println("400 ERROR Usage: GROUP_KICK <group_id> <username_to_kick>"); return; }
        try {
            long groupId = Long.parseLong(parts[1]);
            String targetUsername = parts[2];
            collaborationService.kickUserFromGroup(groupId, loggedInUser.getId(), targetUsername);
            out.println("200 OK " + targetUsername + " has been kicked from the group.");
        } catch (NumberFormatException e) {
            out.println("400 ERROR Invalid group ID.");
        } catch (Exception e) {
            out.println("400 ERROR " + e.getMessage());
        }
    }

    private void handleGroupDelete(String[] parts) {
        if (loggedInUser == null) { out.println("401 ERROR Not logged in."); return; }
        if (parts.length < 2) { out.println("400 ERROR Usage: GROUP_DELETE <group_id>"); return; }
        try {
            long groupId = Long.parseLong(parts[1]);
            collaborationService.deleteGroup(groupId, loggedInUser.getId());
            out.println("200 OK Group deleted successfully.");
        } catch (NumberFormatException e) {
            out.println("400 ERROR Invalid group ID.");
        } catch (Exception e) {
            out.println("400 ERROR " + e.getMessage());
        }
    } 

    // --- CÁC HÀM PHỤ ĐỂ IN DANH SÁCH ---
    private void printFileList(List<File> files, String header) {
        if (files.isEmpty()) {
            out.println("200 OK No files found.");
        } else {
            out.println("200 OK --- " + header + " ---");
            for (File file : files) {
                String uploadDateStr = (file.getUploadDate() != null) ? file.getUploadDate().toString() : "N/A";
            
                out.println(String.format("ID: %-5d | Name: %-30s | Size: %-10d | Last Modified: %s",
                        file.getId(),
                        file.getFileName(),
                        file.getFileSize(),
                        uploadDateStr));
            }
        }
    }

    private void printGroupList(List<Group> groups) {
        if (groups.isEmpty()) {
            out.println("200 OK You are not a member of any group.");
        } else {
            out.println("200 OK --- My Groups ---");
            for (Group group : groups) {
                out.println(String.format("ID: %-5d | Name: %s", group.getGroupId(), group.getGroupName()));
            }
        }
    }

    private void printUserList(List<User> users) {
        if (users.isEmpty()) {
            out.println("200 OK No members found.");
        } else {
            out.println("200 OK --- Group Members ---");
            for (User user : users) {
                out.println(String.format("ID: %-5d | Username: %s", user.getId(), user.getUsername()));
            }
        }
    }
}