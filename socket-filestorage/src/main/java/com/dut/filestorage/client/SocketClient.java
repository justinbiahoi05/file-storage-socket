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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.dut.filestorage.model.entity.File;
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
    public boolean login(String username, String password) {
        try {
            if (socket == null || socket.isClosed()) return false; // Kiểm tra kết nối trước khi gửi
            String response = sendSingleLineCommand("LOGIN " + username + " " + password);
            return response != null && response.startsWith("200 OK");
        } catch (IOException e) {
            System.err.println("Login failed due to network error: " + e.getMessage());
            return false; // Trả về false nếu có lỗi mạng
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
    public String uploadFile(java.io.File localFile, Long groupId) throws IOException {
        String fileName = localFile.getName();
        long fileSize = localFile.length();
        String fileType = "application/octet-stream";

        String metadataCommand = "UPLOAD " + fileName + " " + fileSize + " " + fileType;
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
                    
                    if (parts.length >= 2) { // Chỉ cần ít nhất 2 phần
                        long id = Long.parseLong(parts[0].split(":")[1].trim());
                        String username = parts[1].split(":")[1].trim();
                        
                        User user = new User();
                        user.setId(id);
                        user.setUsername(username);
                        
                        // Kiểm tra xem có phần email không trước khi đọc
                        if (parts.length > 2) {
                            String email = parts[2].split(":")[1].trim();
                            user.setEmail(email);
                        }
                        users.add(user);
                    }
                    
                } catch (Exception e) {
                    System.err.println("Could not parse user list line: " + responseLines.get(i));
                }
            }
        }
        return users;
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

    // --- HÀM TIỆN ÍCH VÀ DỌN DẸP ---

    private List<File> parseFileList(List<String> responseLines) {
        List<File> files = new ArrayList<>();
        if (responseLines.isEmpty() || !responseLines.get(0).startsWith("200 OK")) {
            return files;
        }

        // 1. Định nghĩa một "khuôn mẫu" (Pattern) để tìm kiếm thông tin
        // Pattern này sẽ tìm các nhóm dữ liệu được đánh dấu bằng dấu ngoặc đơn ()
        String regex = "ID:\\s*(\\d+)\\s*\\|\\s*Name:\\s*(.*?)\\s*\\|\\s*Size:\\s*(\\d+)\\s*\\|\\s*Last Modified:\\s*(.*)";
        Pattern pattern = Pattern.compile(regex);

        for (int i = 1; i < responseLines.size(); i++) {
            String line = responseLines.get(i);
            // 2. Áp dụng khuôn mẫu vào từng dòng
            Matcher matcher = pattern.matcher(line);

            // 3. Nếu tìm thấy khớp
            if (matcher.find()) {
                try {
                    // Lấy ra các nhóm dữ liệu đã tìm được
                    long id = Long.parseLong(matcher.group(1)); // Nhóm 1: (\d+) - các chữ số của ID
                    String name = matcher.group(2).trim();      // Nhóm 2: (.*?) - bất kỳ ký tự nào của Name
                    long size = Long.parseLong(matcher.group(3)); // Nhóm 3: (\d+) - các chữ số của Size
                    String dateStr = matcher.group(4).trim();   // Nhóm 4: (.*) - phần còn lại của Date

                    File file = new File();
                    file.setId(id);
                    file.setFileName(name);
                    file.setFileSize(size);

                    if (!"N/A".equals(dateStr)) {
                        // Cắt bỏ phần nano giây nếu có (để parse an toàn hơn)
                        if (dateStr.contains(".")) {
                            dateStr = dateStr.substring(0, dateStr.indexOf('.'));
                        }
                        file.setUploadDate(LocalDateTime.parse(dateStr));
                    }
                    files.add(file);
                    
                } catch (Exception e) {
                    // Nếu lỗi xảy ra ở đây, thường là do định dạng số hoặc ngày tháng sai
                    System.err.println("Error parsing matched line: '" + line + "'");
                    e.printStackTrace();
                }
            } else {
                // Nếu không khớp với khuôn mẫu, in ra để debug
                System.err.println("Could not parse file list line: '" + line + "'");
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