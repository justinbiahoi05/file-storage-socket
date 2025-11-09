package com.dut.filestorage.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.dut.filestorage.model.dao.UserDAO;
import com.dut.filestorage.model.entity.File;
import com.dut.filestorage.model.entity.FileVersion;
import com.dut.filestorage.model.entity.Group;
import com.dut.filestorage.model.entity.User;
import com.dut.filestorage.model.service.CollaborationService;
import com.dut.filestorage.model.service.FileSystemService;
import com.dut.filestorage.model.service.UserService;

public class ClientHandler extends Thread {
    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;

    private UserService userService;
    private FileSystemService fileSystemService;
    private CollaborationService collaborationService;

    private User loggedInUser = null;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
        
        UserDAO userDAO = new UserDAO();
        
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
                if (!processCommand(inputLine)) {
                    break;
                }
            }
        } catch (IOException e) {
            System.out.println("Client " + clientSocket.getInetAddress() + " disconnected.");
        } finally {
            try {
                if (clientSocket != null) clientSocket.close();
            } catch (IOException e) { /* ignore */ }
        }
    }

    private boolean processCommand(String commandLine) {
        List<String> partsList = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("([^\"]\\S*|\".+?\")\\s*").matcher(commandLine);
        while (m.find()) {
            partsList.add(m.group(1).replaceAll("^\"|\"$", ""));
        }
        String[] parts = partsList.toArray(new String[0]);
        
        if (parts.length == 0) return true;

        String command = parts[0].toUpperCase();
        if ("QUIT".equals(command)) return false;

        if (loggedInUser == null && !command.equals("REGISTER") && !command.equals("LOGIN") && !command.equals("ACCESS_LINK")) {
            out.println("401 ERROR Not logged in.");
            return true;
        }

        switch (command) {
            case "REGISTER": handleRegister(parts); break;
            case "LOGIN": handleLogin(parts); break;
            case "LS": handleLs(parts); break;
            case "UPLOAD": handleUpload(parts); break;
            case "DOWNLOAD": handleDownload(parts); break;
            case "DELETE": handleDelete(parts); break; // Đã sửa
            case "SHARE": handleShare(parts); break;
            case "GROUP_CREATE": handleGroupCreate(parts); break;
            case "GROUP_INVITE": handleGroupInvite(parts); break;
            case "GROUP_KICK": handleGroupKick(parts); break;
            case "LINK_CREATE": handleLinkCreate(parts); break;
            case "ACCESS_LINK": handleAccessLink(parts); break;
            case "SEARCH": handleSearch(parts); break;
            case "VERSIONS": handleVersions(parts); break;
            case "RESTORE": handleRestore(parts); break;
            case "LOCK_DOWNLOAD": handleLockDownload(parts); break;
            case "LOCK": handleLock(parts); break;
            case "UNLOCK": handleUnlock(parts); break;
            
            default:
                out.println("500 ERROR Unknown command: " + command);
        }
        return true;
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
         System.out.println("--- [DEBUG] SERVER handleUpload (LOCKED_LOGIC) ---");
        System.out.println("[DEBUG] Received command parts: " + Arrays.toString(parts));
        if (loggedInUser == null) { out.println("401 ERROR Not logged in."); return; }
        
        if (parts.length < 5) { 
            out.println("400 ERROR Bad syntax. Usage: UPLOAD <name> <size> <type> <notes> [--group <id>]"); 
            return; 
        }

        try {
            String fileName = parts[1];
            long fileSize = Long.parseLong(parts[2]);
            String fileType = parts[3];
            String notes = parts[4].equals("null") ? null : parts[4];
            
            Long groupId = null;
            
            for (int i = 5; i < parts.length; i++) {
                if ("--group".equalsIgnoreCase(parts[i]) && (i + 1 < parts.length)) {
                    groupId = Long.parseLong(parts[i + 1]);
                    break;
                }
            }
            
            fileSystemService.checkUploadPermissions(loggedInUser.getId(), groupId);
            out.println("201 READY");
            
            fileSystemService.receiveAndStoreFile(
                clientSocket.getInputStream(),
                fileName, fileSize, fileType,
                loggedInUser.getId(), groupId, notes
            );
            
            out.println("202 OK File uploaded successfully (and unlocked).");

        } catch (NumberFormatException e) {
            out.println("400 ERROR Invalid number format in command.");
        } catch (Exception e) {
            System.err.println("[DEBUG] EXCEPTION in handleUpload: " + e.getMessage());
            out.println("500 ERROR " + e.getMessage());
        }
    }

    private void handleLs(String[] parts) {
        if (loggedInUser == null) { out.println("401 ERROR Not logged in."); out.println("END_OF_LIST"); return; }

        try {
            if (parts.length > 1) {
                String flagOrId = parts[1];
                
                if ("--shared".equalsIgnoreCase(flagOrId)) {
                    List<File> files = collaborationService.listSharedFiles(loggedInUser.getId());
                    printFileList(files, "Shared With Me");
                } else if ("--groups".equalsIgnoreCase(flagOrId)) {
                    List<Group> groups = collaborationService.listUserGroups(loggedInUser.getId());
                    printGroupList(groups);
                } else if ("--members".equalsIgnoreCase(flagOrId)) {
                    if (parts.length < 3) throw new Exception("Usage: LS --members <group_id>");
                    long groupId = Long.parseLong(parts[2]);
                    List<User> members = collaborationService.listGroupMembers(groupId, loggedInUser.getId());
                    printUserList(members);
                } else {
                    long groupId = Long.parseLong(flagOrId);
                    List<File> files = fileSystemService.listFilesInGroup(groupId, loggedInUser.getId());
                    printFileList(files, "Files in Group " + groupId);
                }
            } else {
                List<File> files = fileSystemService.listFiles(loggedInUser.getId());
                printFileList(files, "My Files");
            }
        } catch (NumberFormatException e) {
            out.println("400 ERROR Invalid ID format. Expected a number for group ID.");
        } catch (Exception e) {
            out.println("500 ERROR " + e.getMessage());
        } finally {
            out.println("END_OF_LIST");
        }
    }
    
    // --- HÀM ĐÃ SỬA: Xử lý lỗi void-to-String ---
    private void handleDelete(String[] parts) {
        if (loggedInUser == null) { out.println("401 ERROR Not logged in."); return; }
        
        // Cú pháp mới: DELETE <type> <id>
        if (parts.length < 3) { 
            out.println("400 ERROR Bad syntax. Usage: DELETE <file|group> <id>"); 
            return; 
        }
    
        try {
            String type = parts[1].toLowerCase();
            long id = Long.parseLong(parts[2]);
            String resultMessage = "";
    
            if ("file".equals(type)) {
                // Hàm này trả về String
                resultMessage = fileSystemService.deleteFile(id, loggedInUser.getId());
                
            } else if ("group".equals(type)) {
                // *** SỬA LỖI Ở ĐÂY ***
                // 1. Gọi hàm void
                collaborationService.deleteGroup(id, loggedInUser.getId()); 
                // 2. Tự gán tin nhắn thành công
                resultMessage = "Group deleted successfully.";
            } else {
                throw new Exception("Invalid delete type. Must be 'file' or 'group'.");
            }
            
            // Gửi tin nhắn thành công chung
            out.println("200 OK " + resultMessage);
    
        } catch (NumberFormatException e) {
            out.println("400 ERROR Invalid ID format.");
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

    private void handleLockDownload(String[] parts) {
        if (loggedInUser == null) { out.println("401 ERROR Not logged in."); return; }
        if (parts.length < 2) { out.println("400 ERROR Usage: LOCK_DOWNLOAD <file_id>"); return; }
        
        try {
            long fileId = Long.parseLong(parts[1]);
            File fileToDownload = fileSystemService.lockAndPrepareDownload(fileId, loggedInUser.getId());
            
            out.println("201 INFO " + fileToDownload.getFileName() + " " + fileToDownload.getFileSize());
            
             String clientResponse = in.readLine();
            if ("CLIENT_READY".equals(clientResponse)) {
                fileSystemService.streamFileToOutput(fileId, clientSocket.getOutputStream());
            } else {
                System.out.println("Client canceled download for file " + fileId + ". Unlocking file...");
                fileSystemService.unlockFile(fileId, loggedInUser.getId());
            }
        } catch (NumberFormatException e) {
             out.println("400 ERROR Invalid file ID format.");
        } catch (Exception e) {
            out.println("400 ERROR " + e.getMessage());
        }
    }

    private void handleUnlock(String[] parts) {
        if (loggedInUser == null) { out.println("401 ERROR Not logged in."); return; }
        if (parts.length < 2) { out.println("400 ERROR Usage: UNLOCK <file_id>"); return; }
        
        try {
            long fileId = Long.parseLong(parts[1]);
            fileSystemService.unlockFile(fileId, loggedInUser.getId());
            out.println("200 OK File unlocked.");
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

    private void handleLinkCreate(String[] parts) {
        if (loggedInUser == null) { out.println("401 ERROR Not logged in."); return; }
        if (parts.length < 2) { out.println("400 ERROR Usage: LINK_CREATE <file_id> [options]"); return; }

        try {
            long fileId = Long.parseLong(parts[1]);
            String password = null;
            String expiresIn = null;

            for (int i = 2; i < parts.length - 1; i++) {
                if ("--password".equalsIgnoreCase(parts[i])) {
                    password = parts[i + 1];
                }
                if ("--expires_in".equalsIgnoreCase(parts[i])) {
                    expiresIn = parts[i + 1];
                }
            }
            String token = collaborationService.createPublicLink(fileId, loggedInUser.getId(), password, expiresIn);
            out.println("200 OK Link created. Token: " + token);
            
        } catch (NumberFormatException e) {
            out.println("400 ERROR Invalid file ID.");
        } catch (Exception e) {
            out.println("400 ERROR " + e.getMessage());
        }
    }

    private void handleAccessLink(String[] parts) {
        if (parts.length < 2) {
            out.println("400 ERROR Bad syntax. Usage: ACCESS_LINK <token> [password]");
            return;
        }

        try {
            String token = parts[1];
            String password = (parts.length > 2) ? parts[2] : "";
            
            File fileToDownload = collaborationService.validatePublicLink(token, password);
            
            out.println("201 INFO " + fileToDownload.getFileName() + " " + fileToDownload.getFileSize());
            
            String clientResponse = in.readLine();
            if (clientResponse != null && clientResponse.equals("CLIENT_READY")) {
                fileSystemService.streamFileToOutput(fileToDownload.getId(), clientSocket.getOutputStream());
            } else {
                System.out.println("Client canceled token download for file ID: " + fileToDownload.getId());
            }

        } catch (Exception e) {
            out.println("400 ERROR " + e.getMessage());
        }
    }

    private void handleVersions(String[] parts) {
        if (loggedInUser == null) { out.println("401 ERROR Not logged in."); out.println("END_OF_LIST"); return; }
        if (parts.length < 2) { out.println("400 ERROR Usage: VERSIONS <file_id>"); out.println("END_OF_LIST"); return; }
        
        try {
            long fileId = Long.parseLong(parts[1]);
            List<FileVersion> versions = fileSystemService.getVersionHistory(fileId, loggedInUser.getId());
            
            out.println("200 OK --- Version History ---");
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

            for (FileVersion v : versions) {
                String notes = v.getNotes() != null ? v.getNotes() : "";
                String date = v.getCreatedAt() != null ? v.getCreatedAt().format(formatter) : "N/A";
                
                out.println(String.format("%d!v%-4d | Uploader: %-15s | Date: %-16s | Notes: %s",
                    v.getVersionId(),
                    v.getVersionNumber(),
                    v.getUploaderName(),
                    date,
                    notes));
            }
        } catch (Exception e) {
            out.println("500 ERROR " + e.getMessage());
        } finally {
            out.println("END_OF_LIST");
        }
    }
    
    private void handleRestore(String[] parts) {
        if (loggedInUser == null) { out.println("401 ERROR Not logged in."); return; }
        if (parts.length < 2) { out.println("400 ERROR Usage: RESTORE <version_id>"); return; }
        
        try {
            long versionId = Long.parseLong(parts[1]);
            fileSystemService.restoreVersion(versionId, loggedInUser.getId());
            out.println("200 OK File restored successfully.");
        } catch (NumberFormatException e) {
            out.println("400 ERROR Invalid version ID.");
        } catch (Exception e) {
            out.println("400 ERROR " + e.getMessage());
        }
    }

    private void handleSearch(String[] parts) {
        if (loggedInUser == null) { out.println("401 ERROR Not logged in."); out.println("END_OF_LIST"); return; }
        if (parts.length < 3) { 
            out.println("400 ERROR Bad syntax for SEARCH command.");
            out.println("END_OF_LIST");
            return;
        }

        String searchScope = parts[1];
        String keyword;
        List<File> results = new ArrayList<>();

        try {
            if ("--my-files".equalsIgnoreCase(searchScope)) {
                keyword = parts[2];
                results = fileSystemService.searchMyFiles(keyword, loggedInUser.getId());
            } else if ("--shared".equalsIgnoreCase(searchScope)) {
                keyword = parts[2];
                results = fileSystemService.searchSharedFiles(keyword, loggedInUser.getId());
            } else if ("--group".equalsIgnoreCase(searchScope)) {
                if (parts.length < 4) throw new Exception("Group ID is missing for group search.");
                long groupId = Long.parseLong(parts[2]);
                keyword = parts[3];
                results = fileSystemService.searchGroupFiles(groupId, keyword, loggedInUser.getId());
            } else {
                throw new Exception("Invalid search scope: " + searchScope);
            }
            printFileList(results, "Search Results for '" + keyword + "'");

        } catch (Exception e) {
            out.println("500 ERROR " + e.getMessage());
        } finally {
            out.println("END_OF_LIST");
        }
    }

    private void handleLock(String[] parts) {
        if (loggedInUser == null) {
            out.println("401 ERROR Not logged in. Cannot perform this action.");
            return;
        }
        
        if (parts.length < 2) {
            out.println("400 ERROR Bad syntax. Usage: LOCK <file_id>");
            return;
        }
        
        try {
            long fileId = Long.parseLong(parts[1]);
            fileSystemService.lockFile(fileId, loggedInUser.getId());
            out.println("200 OK File locked successfully.");

        } catch (NumberFormatException e) {
            out.println("400 ERROR Invalid file ID format. Must be a number.");
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
            
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            out.println(String.format("%-5s | %-30s | %-10s | %-15s | %-19s | %-3s | %-15s",
                   "ID", "Name", "Size", "Owner", "Last Modified", "Ver", "Locked By"));
            out.println("----------------------------------------------------------------------------------------------------------------------");

            for (File file : files) {
                String uploadDateStr = (file.getUploadDate() != null) ? file.getUploadDate().format(formatter) : "N/A";
                String ownerName = file.getOwnerName() != null ? file.getOwnerName() : "N/A";
                
                String lockedBy = file.isLocked() ? file.getLockedByUsername() : "-";
                
                out.println(String.format("%-5d | %-30s | %-10d | %-15s | %-19s | %-3d | %-15s",
                        file.getId(),
                        file.getFileName(),
                        file.getFileSize(),
                        ownerName,
                        uploadDateStr,
                        file.getCurrentVersion(),
                        lockedBy));
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
                String role = user.getRoleInGroup() != null ? user.getRoleInGroup() : "N/A";
                
                out.println(String.format("ID: %-5d | Username: %-20s | Role: %s",
                        user.getId(),
                        user.getUsername(),
                        role));
            }
        }
    }
}