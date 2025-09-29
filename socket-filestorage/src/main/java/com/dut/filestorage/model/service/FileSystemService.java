package com.dut.filestorage.model.service;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

import com.dut.filestorage.model.dao.FileDAO;
import com.dut.filestorage.model.dao.FolderDAO;
import com.dut.filestorage.model.dao.ShareDAO;
import com.dut.filestorage.model.entity.File;
import com.dut.filestorage.model.entity.Folder;

public class FileSystemService {
    private FolderDAO folderDAO;
    private FileDAO fileDAO;
     private ShareDAO shareDAO;
    private final Path rootLocation = Paths.get("uploads"); // Thư mục lưu file vật lý

    public FileSystemService() {
        this.folderDAO = new FolderDAO();
        this.fileDAO = new FileDAO();
        this.shareDAO = new ShareDAO();

        try {
            Files.createDirectories(rootLocation);
        } catch (Exception e) {
            throw new RuntimeException("Could not initialize storage directory!", e);
        }
    }

    public void createDirectory(String folderName, Long ownerId, Long parentFolderId) throws Exception {
        if (folderName == null || folderName.trim().isEmpty() || folderName.contains(" ")) {
            throw new Exception("Folder name cannot be empty or contain spaces.");
        }

        Folder newFolder = new Folder();
        newFolder.setFolderName(folderName);
        newFolder.setOwnerId(ownerId);
        newFolder.setParentFolderId(parentFolderId);

        folderDAO.save(newFolder);
    }
     public void storeFile(InputStream inputStream, String originalFileName, long fileSize, String fileType, Long ownerId, Long folderId) throws Exception {
        if (originalFileName == null || originalFileName.isEmpty()) {
            throw new Exception("File name cannot be empty.");
        }
        
        // 1. Tạo tên file duy nhất để lưu trên server
        String storedFileName = UUID.randomUUID().toString() + "_" + originalFileName;
        Path destinationFile = this.rootLocation.resolve(storedFileName).normalize().toAbsolutePath();

        // 2. Lưu file vật lý vào thư mục 'uploads'
        try {
            Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new Exception("Failed to store file.", e);
        }
        
        // 3. Chuẩn bị thông tin để lưu vào CSDL
        File newFile = new File();
        newFile.setFileName(originalFileName);
        newFile.setStoredPath(destinationFile.toString());
        newFile.setFileSize(fileSize);
        newFile.setFileType(fileType);
        newFile.setOwnerId(ownerId);
        newFile.setFolderId(folderId); // Tạm thời là null

        // 4. Gọi DAO để lưu thông tin file vào CSDL
        fileDAO.save(newFile);
    }
    
    // Lấy danh sách file
    public List<File> listFiles(Long ownerId) throws Exception {
        return fileDAO.findByOwnerId(ownerId);
    }

     // Xử lý logic download
    public void downloadFile(long fileId, Long requestUserId, OutputStream outputStream) throws Exception {
        File file = fileDAO.findById(fileId);
        
        if (file == null) {
            throw new Exception("File not found.");
        }
        
        boolean isOwner = file.getOwnerId().equals(requestUserId);
        boolean isSharedWith = shareDAO.isFileSharedWithUser(fileId, requestUserId);

        // Người dùng được phép truy cập NẾU họ là chủ sở hữu HOẶC file được chia sẻ cho họ
        if (!isOwner && !isSharedWith) {
            throw new Exception("Access denied. You do not have permission to download this file.");
        }
       
        // Nếu qua được vòng kiểm tra, tiếp tục gửi file như bình thường
        java.io.File physicalFile = new java.io.File(file.getStoredPath());
        if (!physicalFile.exists()) {
            throw new Exception("Physical file is missing on server.");
        }
        
        try (FileInputStream fis = new FileInputStream(physicalFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
        }
    }

    // Xử lý logic xóa file
     public String deleteOrRemoveShare(long fileId, Long requestUserId) throws Exception {
        File file = fileDAO.findById(fileId);
        
        if (file == null) {
            throw new Exception("File not found.");
        }

        // Kịch bản 1: Người yêu cầu là CHỦ SỞ HỮU
        if (file.getOwnerId().equals(requestUserId)) {
            // Xóa vĩnh viễn file
           
            // Xóa file vật lý
            java.io.File physicalFile = new java.io.File(file.getStoredPath());
            if (physicalFile.exists()) {
                if (!physicalFile.delete()) {
                    throw new Exception("Failed to delete physical file.");
                }
            }
            // Xóa bản ghi trong CSDL
            fileDAO.deleteById(fileId);

            return "File deleted permanently."; // Trả về thông báo để Controller biết
        }
        
        // Kịch bản 2: Người yêu cầu là NGƯỜI ĐƯỢC CHIA SẺ
        else if (shareDAO.isFileSharedWithUser(fileId, requestUserId)) {
            // Chỉ xóa lượt chia sẻ
            shareDAO.removeShare(fileId, requestUserId);
            return "File removed from your shared list."; // Trả về thông báo khác
        }
        
        // Kịch bản 3: Không có quyền gì cả
        else {
            throw new Exception("Access denied. You do not have permission to delete this file.");
        }
    }
}