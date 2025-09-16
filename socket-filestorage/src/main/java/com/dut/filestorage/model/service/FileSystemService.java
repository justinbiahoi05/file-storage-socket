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
import com.dut.filestorage.model.entity.File;
import com.dut.filestorage.model.entity.Folder;

public class FileSystemService {
    private FolderDAO folderDAO;
    private FileDAO fileDAO;
    private final Path rootLocation = Paths.get("uploads"); // Thư mục lưu file vật lý

    public FileSystemService() {
        this.folderDAO = new FolderDAO();
        this.fileDAO = new FileDAO(); // Khởi tạo FileDAO

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
        // TODO: Kiểm tra tên thư mục trùng lặp trong cùng thư mục cha

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
    public void downloadFile(long fileId, Long userId, OutputStream outputStream) throws Exception {
        File file = fileDAO.findById(fileId);
        
        // Kiểm tra xem file có tồn tại không
        if (file == null) {
            throw new Exception("File not found.");
        }
        // Kiểm tra quyền sở hữu
        if (!file.getOwnerId().equals(userId)) {
            // TODO: Sau này sẽ kiểm tra cả quyền được chia sẻ
            throw new Exception("Access denied.");
        }
        
        // Đọc file vật lý và ghi ra output stream của socket
        java.io.File physicalFile = new java.io.File(file.getStoredPath());
        if (!physicalFile.exists()) {
            throw new Exception("Physical file is missing on server.");
        }
        
        try (FileInputStream fis = new FileInputStream(physicalFile)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
        }
    }

    // Xử lý logic xóa file
    public void deleteFile(long fileId, Long userId) throws Exception {
        File file = fileDAO.findById(fileId);
        
        if (file == null) {
            throw new Exception("File not found.");
        }
        if (!file.getOwnerId().equals(userId)) {
            throw new Exception("Access denied. You are not the owner.");
        }

        // 1. Xóa file vật lý
        java.io.File physicalFile = new java.io.File(file.getStoredPath());
        if (physicalFile.exists()) {
            if (!physicalFile.delete()) {
                throw new Exception("Failed to delete physical file.");
            }
        }

        // 2. Xóa bản ghi trong CSDL
        fileDAO.deleteById(fileId);
    }
}