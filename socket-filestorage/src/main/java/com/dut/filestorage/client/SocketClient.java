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
                // Đăng nhập thành công, tạo một đối tượng User để trả về
                User user = new User();
                user.setUsername(username);
                return user;
            }
            // Nếu không thành công, trả về null
            return null;
        } catch (IOException e) {
            System.err.println("Login failed due to network error: " + e.getMessage());
            return null; // Trả về null nếu có lỗi mạng
        }
    }

    public String register(String username, String password, String email) throws IOException {
        return sendSingleLineCommand("REGISTER " + username + " " + password + " " + email);
    }

    // === Module File & Listing ===
   public List<File> listFiles() {
        try {
            if (socket == null || socket.isClosed()) return new ArrayList<>();
            return parseFileList(sendMultiLineCommand("LS"));
        } catch (IOException e) {
            System.err.println("listFiles failed due to network error: " + e.getMessage());
            return new ArrayList<>(); // Trả về danh sách rỗng nếu lỗi
        }
    }

    public List<File> listSharedFiles() throws IOException {
        try{
            return parseFileList(sendMultiLineCommand("LS --shared"));
        } catch (IOException e) {
            System.err.println("listSharedFiles failed due to network error: " + e.getMessage());
            return new ArrayList<>(); // Trả về danh sách rỗng nếu lỗi
        }
    }

    public List<File> listGroupFiles(long groupId) throws IOException {
        try{
            return parseFileList(sendMultiLineCommand("LS " + groupId));
        } catch (IOException e) {
            System.err.println("listGroupFiles failed due to network error: " + e.getMessage());
            return new ArrayList<>(); // Trả về danh sách rỗng nếu lỗi
        }
    }

    // === Module Upload / Download ===
    public String uploadFile(java.io.File localFile, Long groupId, int baseVersion, String notes) throws IOException {
        String fileName = localFile.getName();
        long fileSize = localFile.length();
        String fileType = "application/octet-stream";
        String notesToSend = (notes == null || notes.trim().isEmpty()) ? "null" : "\"" + notes + "\"";
        String metadataCommand = "UPLOAD " + fileName + " " + fileSize + " " + fileType + " " + baseVersion + " " + notesToSend;
   
        if (groupId != null) {
            metadataCommand += " --group " + groupId;
        }
        out.println(metadataCommand);

        String serverResponse = in.readLine();
        if (serverResponse == null || !serverResponse.startsWith("201 READY")) {
            return "Server rejected upload: " + (serverResponse != null ? serverResponse : "No response");
        }

        // Bắt đầu gửi file
        try (FileInputStream fis = new FileInputStream(localFile)) {
            OutputStream socketOutputStream = socket.getOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                socketOutputStream.write(buffer, 0, bytesRead);
            }
            socketOutputStream.flush();
        }

        // Nhận phản hồi cuối cùng
        return in.readLine();
    }

    public String downloadFile(long fileId, String saveDirectoryPath) throws IOException {
        out.println("DOWNLOAD " + fileId);
        String serverResponse = in.readLine();

        if (serverResponse == null || !serverResponse.startsWith("201 INFO")) {
            return "Server error: " + (serverResponse != null ? serverResponse : "No response");
        }
        
        String[] infoParts = serverResponse.split(" ");
        String fileName = infoParts[2];
        long fileSize = Long.parseLong(infoParts[3]);

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

    public String deleteFile(long fileId) throws IOException {
        return sendSingleLineCommand("DELETE " + fileId);
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
                // Phân tích chuỗi "ID: 1 | Name: NhomPBL4"
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
                    
                    // Giờ chuỗi có 3 phần
                    if (parts.length >= 3) {
                        long id = Long.parseLong(parts[0].split(":")[1].trim());
                        String username = parts[1].split(":")[1].trim();
                        String role = parts[2].split(":")[1].trim(); // Lấy role
                        
                        User user = new User();
                        user.setId(id);
                        user.setUsername(username);
                        user.setRoleInGroup(role); // Gán role
                        
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
        return parseFileList(sendMultiLineCommand("SEARCH --my-files " + keyword));
    }

    public List<File> searchSharedFiles(String keyword) throws IOException {
        return parseFileList(sendMultiLineCommand("SEARCH --shared " + keyword));
    }

    public List<File> searchGroupFiles(long groupId, String keyword) throws IOException {
        return parseFileList(sendMultiLineCommand("SEARCH --group " + groupId + " " + keyword));
    }
    public String createGroup(String groupName) throws IOException {
        return sendSingleLineCommand("GROUP_CREATE " + groupName);
    }
    
    public String inviteToGroup(long groupId, String username) throws IOException {
        return sendSingleLineCommand("GROUP_INVITE " + groupId + " " + username);
    }
    
    public String kickFromGroup(long groupId, String username) throws IOException {
        return sendSingleLineCommand("GROUP_KICK " + groupId + " " + username);
    }

    public String deleteGroup(long groupId) throws IOException {
        return sendSingleLineCommand("GROUP_DELETE " + groupId);
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
        // Xây dựng lệnh, gửi cả mật khẩu dù nó là chuỗi rỗng
        String command = "ACCESS_LINK " + token + " " + password;
        out.println(command);
        
        String serverResponse = in.readLine();

        if (serverResponse == null || !serverResponse.startsWith("201 INFO")) {
            return "Server error: " + (serverResponse != null ? serverResponse : "No response");
        }
        
        String[] infoParts = serverResponse.split(" ");
        String fileName = infoParts[2];
        long fileSize = Long.parseLong(infoParts[3]);

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
        
        // Regex mới để hiểu chuỗi 4 phần (sau khi đã tách versionId)
        String regex = "v(\\d+)\\s*\\|\\s*Uploader:\\s*(.*?)\\s*\\|\\s*Date:\\s*(.*?)\\s*\\|\\s*Notes:\\s*(.*)";
        Pattern pattern = Pattern.compile(regex);

        for (int i = 1; i < responseLines.size(); i++) {
            String line = responseLines.get(i);
            
            String[] idAndContent = line.split("!", 2);
            if (idAndContent.length < 2) continue; // Bỏ qua dòng lỗi

            long versionId = Long.parseLong(idAndContent[0]);
            String content = idAndContent[1];
            
            Matcher matcher = pattern.matcher(content);

            if (matcher.find()) {
                try {
                    FileVersion version = new FileVersion();
                    
                    version.setVersionId(versionId); // Gán versionId
                    version.setVersionNumber(Integer.parseInt(matcher.group(1).trim()));
                    version.setUploaderName(matcher.group(2).trim());
                    
                    String dateStr = matcher.group(3).trim();
                    String notesStr = matcher.group(4).trim();
                    
                    // Gộp Date và Notes để hiển thị trong 1 cột cho tiện
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

    public int findLatestVersion(String fileName, Long groupId) throws IOException {
        String command = "GET_VERSION " + fileName;
        if (groupId != null) {
            command += " --group " + groupId;
        }
        String response = sendSingleLineCommand(command);
        if (response != null && response.startsWith("200 OK")) {
            return Integer.parseInt(response.split(" ")[2]);
        }
        return 0; // Trả về 0 nếu có lỗi
    }

    // --- HÀM TIỆN ÍCH VÀ DỌN DẸP ---

    private List<File> parseFileList(List<String> responseLines) {
        List<File> files = new ArrayList<>();
        if (responseLines.isEmpty() || !responseLines.get(0).startsWith("200 OK")) {
            return files;
        }
        
        // Regex vẫn giữ nguyên
        String regex = "ID:\\s*(\\d+)\\s*\\|\\s*Name:\\s*(.*?)\\s*\\|\\s*Size:\\s*(\\d+)\\s*\\|\\s*Owner:\\s*(.*?)\\s*\\|\\s*Last Modified:\\s*(.*?)\\s*\\|\\s*Version:\\s*(\\d+)";
        Pattern pattern = Pattern.compile(regex);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        for (int i = 1; i < responseLines.size(); i++) {
            String line = responseLines.get(i);
            Matcher matcher = pattern.matcher(line);

            if (matcher.find()) {
                try {
                    long id = Long.parseLong(matcher.group(1));
                    String name = matcher.group(2).trim();
                    long size = Long.parseLong(matcher.group(3));
                    String ownerName = matcher.group(4).trim();
                    String dateStr = matcher.group(5).trim();
                    int version = Integer.parseInt(matcher.group(6));

                    File file = new File();
                    file.setId(id);
                    file.setFileName(name);
                    file.setFileSize(size);
                    file.setOwnerName(ownerName);
                    file.setCurrentVersion(version);
                    
                    if (!"N/A".equals(dateStr)) {
                        file.setUploadDate(LocalDateTime.parse(dateStr, formatter)); 
                    }
                    files.add(file);
                } catch (Exception e) {
                    System.err.println("Error parsing matched line: '" + line + "'");
                    e.printStackTrace();
                }
            } else {
                System.err.println("Could not parse file list line (no regex match): '" + line + "'");
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