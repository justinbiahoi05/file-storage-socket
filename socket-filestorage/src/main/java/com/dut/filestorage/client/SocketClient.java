package com.dut.filestorage.client;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.dut.filestorage.model.entity.File;
import com.dut.filestorage.model.entity.FileVersion;
import com.dut.filestorage.model.entity.Group;
import com.dut.filestorage.model.entity.User;

public class SocketClient {
    private String hostname;
    private int port;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    public SocketClient(String hostname, int port) throws IOException {
        this.hostname = hostname;
        this.port = port;
        this.socket = new Socket(hostname, port);
        this.out = new PrintWriter(socket.getOutputStream(), true);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    // --- CÁC HÀM GỬI LỆNH VÀ NHẬN PHẢN HỒI (Nền tảng) ---

    private String sendSingleLineCommand(String command) throws IOException {
        out.println(command);
        return in.readLine();
    }

    private List<String> sendMultiLineCommand(String command) throws IOException {
        out.println(command);
        List<String> responseLines = new ArrayList<>();
        String serverResponse;
        while ((serverResponse = in.readLine()) != null) {
            if ("END_OF_LIST".equals(serverResponse)) {
                break;
            }
            responseLines.add(serverResponse);
        }
        return responseLines;
    }

    // --- CÁC HÀM LOGIC CHO GIAO DIỆN ---

    // === Module User ===
    public User login(String username, String password) {
        try {
            if (socket == null || socket.isClosed()) {
                return null;
            }
            
            String response = sendSingleLineCommand("LOGIN " + username + " " + password);
            
            if (response != null && response.startsWith("200 OK")) {
                User user = new User();
                user.setUsername(username);
                return user;
            }
            return null;
        } catch (IOException e) {
            System.err.println("Login failed due to network error: " + e.getMessage());
            return null;
        }
    }

    public String register(String username, String password, String email) throws IOException {
        return sendSingleLineCommand("REGISTER " + username + " " + password + " " + email);
    }

    // === Module File & Listing ===
    public List<File> listFiles() throws IOException {
        return parseFileList(sendMultiLineCommand("LS"));
    }

    public List<File> listSharedFiles() throws IOException {
        return parseFileList(sendMultiLineCommand("LS --shared"));
    }

    public List<File> listGroupFiles(long groupId) throws IOException {
        return parseFileList(sendMultiLineCommand("LS " + groupId));
    }

    // === Module Upload / Download ===
    
    // --- HÀM ĐÃ SỬA: Bỏ baseVersion ---
    public String uploadFile(java.io.File localFile, Long groupId, String notes) throws IOException {
        String fileName = localFile.getName();
        long fileSize = localFile.length();
        String fileType = "application/octet-stream";
        String notesToSend = (notes == null || notes.trim().isEmpty()) ? "null" : "\"" + notes + "\"";

        // Gửi 4 tham số: name, size, type, notes
        String metadataCommand = String.format("UPLOAD \"%s\" %d %s %s",
                fileName, fileSize, fileType, notesToSend);

        if (groupId != null) {
            metadataCommand += " --group " + groupId;
        }
        out.println(metadataCommand);

        String serverResponse = in.readLine();
        if (serverResponse == null || !serverResponse.startsWith("201 READY")) {
            return "Server rejected upload: " + (serverResponse != null ? serverResponse : "No response");
        }

        try (FileInputStream fis = new FileInputStream(localFile)) {
            OutputStream socketOutputStream = socket.getOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                socketOutputStream.write(buffer, 0, bytesRead);
            }
            socketOutputStream.flush();
        }

        return in.readLine();
    }

    // --- HÀM MỚI: lockAndDownload ---
    public String lockAndDownload(long fileId, String saveDirectoryPath) throws IOException {
        out.println("LOCK_DOWNLOAD " + fileId);
        String serverResponse = in.readLine();

        if (serverResponse == null || !serverResponse.startsWith("201 INFO")) {
            return "Server error: " + (serverResponse != null ? serverResponse : "No response");
        }
        
        // Tách chuỗi cẩn thận hơn
        Matcher m = Pattern.compile("201 INFO \"(.*?)\" (\\d+)").matcher(serverResponse);
        if (!m.find()) {
             // Thử lại với tên file không có khoảng trắng
             m = Pattern.compile("201 INFO (.*?) (\\d+)").matcher(serverResponse);
             if (!m.find() || m.groupCount() < 2) {
                 return "500 ERROR: Client could not parse server response: " + serverResponse;
             }
        }
        String fileName = m.group(1);
        long fileSize = Long.parseLong(m.group(2));


        out.println("CLIENT_READY");
        
        java.io.File saveDir = new java.io.File(saveDirectoryPath);
        if (!saveDir.exists()) saveDir.mkdirs();

        try (FileOutputStream fos = new FileOutputStream(new java.io.File(saveDir, fileName))) {
            InputStream socketInputStream = socket.getInputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytesRead = 0;
            
            while (totalBytesRead < fileSize && (bytesRead = socketInputStream.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalBytesRead))) != -1) {
                fos.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
            }

            if (totalBytesRead == fileSize) {
                return "200 OK Download completed successfully.";
            } else {
                return "500 ERROR Download incomplete.";
            }
        }
    }

    /**
     * Chỉ download file (không khóa).
     * Server sẽ kiểm tra xem file có bị NGƯỜI KHÁC khóa không.
     */
    public String downloadFile(long fileId, String saveDirectoryPath) throws IOException {
        // Gửi lệnh DOWNLOAD (lệnh này đã có sẵn trong ClientHandler)
        out.println("DOWNLOAD " + fileId);
        String serverResponse = in.readLine();

        if (serverResponse == null || !serverResponse.startsWith("201 INFO")) {
            return "Server error: " + (serverResponse != null ? serverResponse : "No response");
        }
        
        // Phân tích phản hồi (Tên file và Kích thước)
        String[] infoParts = serverResponse.split(" ");
        String fileName = infoParts[2];
        long fileSize = Long.parseLong(infoParts[3]);

        // Báo cho server "Tôi sẵn sàng nhận"
        out.println("CLIENT_READY");
        
        // Chuẩn bị thư mục lưu
        java.io.File saveDir = new java.io.File(saveDirectoryPath);
        if (!saveDir.exists()) saveDir.mkdirs();

        // Bắt đầu nhận file
        try (FileOutputStream fos = new FileOutputStream(new java.io.File(saveDir, fileName))) {
            InputStream socketInputStream = socket.getInputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytesRead = 0;
            
            while (totalBytesRead < fileSize && (bytesRead = socketInputStream.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalBytesRead))) != -1) {
                fos.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
            }

            if (totalBytesRead == fileSize) {
                return "200 OK Download completed successfully.";
            } else {
                return "500 ERROR Download incomplete.";
            }
        }
    }

    // --- HÀM MỚI: lockFile ---
    public String lockFile(long fileId) throws IOException {
        return sendSingleLineCommand("LOCK " + fileId);
    }
    
    // --- HÀM MỚI: unlockFile ---
    public String unlockFile(long fileId) throws IOException {
        return sendSingleLineCommand("UNLOCK " + fileId);
    }
    
    // --- HÀM MỚI: delete (để đồng bộ với MainViewController) ---
    public String delete(String type, long id) throws IOException {
        return sendSingleLineCommand("DELETE " + type + " " + id);
    }
    
    // --- HÀM CŨ: (Giờ chỉ để backup) ---
    public String deleteFile(long fileId) throws IOException {
        return delete("file", fileId);
    }

    public String deleteGroup(long groupId) throws IOException {
        return delete("group", groupId);
    }
    
    // === Module Collaboration ===
    public String shareFile(long fileId, String username) throws IOException {
        return sendSingleLineCommand("SHARE " + fileId + " " + username);
    }

    public List<Group> listGroups() throws IOException {
        List<String> responseLines = sendMultiLineCommand("LS --groups");
        List<Group> groups = new ArrayList<>();
        if (!responseLines.isEmpty() && responseLines.get(0).startsWith("200 OK")) {
            for (int i = 1; i < responseLines.size(); i++) {
                try {
                    String line = responseLines.get(i);
                    String[] parts = line.split("\\|");
                    long id = Long.parseLong(parts[0].split(":")[1].trim());
                    String name = parts[1].split(":")[1].trim();
                    Group group = new Group();
                    group.setGroupId(id);
                    group.setGroupName(name);
                    groups.add(group);
                } catch (Exception e) {
                    System.err.println("Could not parse group list line: " + responseLines.get(i));
                }
            }
        }
        return groups;
    }
    
    public List<User> listGroupMembers(long groupId) throws IOException {
        List<String> responseLines = sendMultiLineCommand("LS --members " + groupId);
        List<User> users = new ArrayList<>();
        if (!responseLines.isEmpty() && responseLines.get(0).startsWith("200 OK")) {
            for (int i = 1; i < responseLines.size(); i++) {
                try {
                    String line = responseLines.get(i);
                    String[] parts = line.split("\\|");
                    
                    if (parts.length >= 3) {
                        long id = Long.parseLong(parts[0].split(":")[1].trim());
                        String username = parts[1].split(":")[1].trim();
                        String role = parts[2].split(":")[1].trim();
                        
                        User user = new User();
                        user.setId(id);
                        user.setUsername(username);
                        user.setRoleInGroup(role);
                        
                        users.add(user);
                    }
                } catch (Exception e) {
                    System.err.println("Could not parse user list line: " + responseLines.get(i));
                }
            }
        }
        return users;
    }
    
    public List<File> searchMyFiles(String keyword) throws IOException {
         return parseFileList(sendMultiLineCommand("SEARCH --my-files \"" + keyword + "\""));
    }
    public List<File> searchSharedFiles(String keyword) throws IOException {
         return parseFileList(sendMultiLineCommand("SEARCH --shared \"" + keyword + "\""));
    }
    public List<File> searchGroupFiles(long groupId, String keyword) throws IOException {
         return parseFileList(sendMultiLineCommand("SEARCH --group " + groupId + " \"" + keyword + "\""));
    }
    public String createGroup(String groupName) throws IOException {
         return sendSingleLineCommand("GROUP_CREATE \"" + groupName + "\"");
    }
    
    public String inviteToGroup(long groupId, String username) throws IOException {
        return sendSingleLineCommand("GROUP_INVITE " + groupId + " " + username);
    }
    
    public String kickFromGroup(long groupId, String username) throws IOException {
        return sendSingleLineCommand("GROUP_KICK " + groupId + " " + username);
    }

    public String createPublicLink(long fileId, String password, String expiresIn) throws IOException {
        String command = "LINK_CREATE " + fileId;
        if (password != null && !password.isEmpty()) {
            command += " --password " + password;
        }
        if (expiresIn != null) {
            command += " --expires_in " + expiresIn;
        }
        return sendSingleLineCommand(command);
    }
    
    public String downloadFileByToken(String token, String password, String saveDirectoryPath) throws IOException {
        String command = "ACCESS_LINK " + token + " " + password;
        out.println(command);
        
        String serverResponse = in.readLine();

        if (serverResponse == null || !serverResponse.startsWith("201 INFO")) {
            return "Server error: " + (serverResponse != null ? serverResponse : "No response");
        }
        
        Matcher m = Pattern.compile("201 INFO \"(.*?)\" (\\d+)").matcher(serverResponse);
         if (!m.find()) {
              m = Pattern.compile("201 INFO (.*?) (\\d+)").matcher(serverResponse);
              if (!m.find() || m.groupCount() < 2) {
                  return "500 ERROR: Client could not parse server response: " + serverResponse;
              }
         }
        String fileName = m.group(1);
        long fileSize = Long.parseLong(m.group(2));


        out.println("CLIENT_READY");
        
        java.io.File saveDir = new java.io.File(saveDirectoryPath);
        if (!saveDir.exists()) saveDir.mkdirs();

        try (FileOutputStream fos = new FileOutputStream(new java.io.File(saveDir, fileName))) {
            InputStream socketInputStream = socket.getInputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytesRead = 0;
            
            while (totalBytesRead < fileSize && (bytesRead = socketInputStream.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalBytesRead))) != -1) {
                fos.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
            }

            if (totalBytesRead == fileSize) {
                return "200 OK Download completed successfully via link.";
            } else {
                return "500 ERROR Download incomplete.";
            }
        }
    }

    public List<FileVersion> getVersionHistory(long fileId) throws IOException {
        List<String> responseLines = sendMultiLineCommand("VERSIONS " + fileId);
        List<FileVersion> versions = new ArrayList<>();
        
        if (responseLines.isEmpty() || !responseLines.get(0).startsWith("200 OK")) {
            if (!responseLines.isEmpty()) throw new IOException(responseLines.get(0));
            return versions;
        }
        
        String regex = "v(\\d+)\\s*\\|\\s*Uploader:\\s*(.*?)\\s*\\|\\s*Date:\\s*(.*?)\\s*\\|\\s*Notes:\\s*(.*)";
        Pattern pattern = Pattern.compile(regex);

        for (int i = 1; i < responseLines.size(); i++) {
            String line = responseLines.get(i);
            
            String[] idAndContent = line.split("!", 2);
            if (idAndContent.length < 2) continue;

            long versionId = Long.parseLong(idAndContent[0]);
            String content = idAndContent[1];
            
            Matcher matcher = pattern.matcher(content);

            if (matcher.find()) {
                try {
                    FileVersion version = new FileVersion();
                    
                    version.setVersionId(versionId);
                    version.setVersionNumber(Integer.parseInt(matcher.group(1).trim()));
                    version.setUploaderName(matcher.group(2).trim());
                    
                    String dateStr = matcher.group(3).trim();
                    String notesStr = matcher.group(4).trim();
                    
                    version.setNotes(dateStr + " | " + notesStr); 
                    
                    versions.add(version);
                } catch (Exception e) {
                    System.err.println("Could not parse version history line content: '" + content + "'");
                }
            } else {
                System.err.println("Could not parse version history line (no regex match): '" + line + "'");
            }
        }
        return versions;
    }

    public String restoreVersion(long versionId) throws IOException {
        return sendSingleLineCommand("RESTORE " + versionId);
    }

    // --- HÀM ĐÃ XÓA ---
    // public int findLatestVersion(...) { ... }

    // --- HÀM TIỆN ÍCH VÀ DỌN DẸP ---

    // --- HÀM ĐÃ SỬA: Cập nhật Regex để parse 7 cột (thêm "Locked By") ---
    private List<File> parseFileList(List<String> responseLines) {
        List<File> files = new ArrayList<>();
        if (responseLines.isEmpty() || !responseLines.get(0).startsWith("200 OK")) {
            return files;
        }

        // Regex này mong đợi 7 phần, đúng với format mới nhất của server
         String regex = "(\\d+)\\s*\\|\\s*(.*?)\\s*\\|\\s*(\\d+)\\s*\\|\\s*(.*?)\\s*\\|\\s*(.*?)\\s*\\|\\s*(\\d+)\\s*\\|\\s*(.*)";
        Pattern pattern = Pattern.compile(regex);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        // Bắt đầu lặp từ dòng 1 để bỏ qua header
        for (int i = 1; i < responseLines.size(); i++) {
            String line = responseLines.get(i);
            // Bỏ qua các dòng gạch ngang trang trí và dòng tiêu đề
            if (line.trim().startsWith("---") || line.trim().startsWith("ID")) continue;

            Matcher matcher = pattern.matcher(line.trim());
            if (matcher.find()) {
                try {
                    File file = new File();
                    file.setId(Long.parseLong(matcher.group(1).trim()));
                    file.setFileName(matcher.group(2).trim());
                    file.setFileSize(Long.parseLong(matcher.group(3).trim()));
                    file.setOwnerName(matcher.group(4).trim());
                    
                    String dateStr = matcher.group(5).trim();
                    if (!"N/A".equals(dateStr)) {
                        file.setUploadDate(LocalDateTime.parse(dateStr, formatter));
                    }
                    
                    file.setCurrentVersion(Integer.parseInt(matcher.group(6).trim()));
                    
                    String lockedBy = matcher.group(7).trim();
                    if (!"-".equals(lockedBy) && !lockedBy.isEmpty()) {
                        file.setLocked(true);
                        file.setLockedByUsername(lockedBy);
                    } else {
                        file.setLocked(false);
                    }
                    
                    files.add(file);
                } catch (Exception e) {
                    System.err.println("Error parsing matched line: '" + line + "'");
                    e.printStackTrace();
                }
            } else {
                if (!line.trim().isEmpty()) {
                     System.err.println("Could not parse file list line (no regex match): '" + line + "'");
                }
            }
        }
        return files;
    }

    public void close() throws IOException {
        if (socket != null && !socket.isClosed()) {
            try {
                out.println("QUIT");
            } finally {
                socket.close();
            }
        }
    }
}