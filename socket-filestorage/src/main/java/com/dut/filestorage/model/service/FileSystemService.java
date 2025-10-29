package com.dut.filestorage.model.service;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import com.dut.filestorage.model.dao.FileDAO;
import com.dut.filestorage.model.dao.FileVersionDAO;
import com.dut.filestorage.model.dao.GroupDAO;
import com.dut.filestorage.model.dao.ShareDAO;
import com.dut.filestorage.model.entity.File;
import com.dut.filestorage.model.entity.FileVersion;
import com.dut.filestorage.model.entity.Group;

public class FileSystemService {
    private FileDAO fileDAO;
    private ShareDAO shareDAO;
    private CollaborationService collaborationService;
    private final Path rootLocation = Paths.get("uploads");
    private FileVersionDAO fileVersionDAO;
    private GroupDAO groupDAO = new GroupDAO();

    public FileSystemService(CollaborationService collaborationService) {
        this.fileDAO = new FileDAO();
        this.shareDAO = new ShareDAO();
        this.collaborationService = collaborationService;
        this.fileVersionDAO = new FileVersionDAO();

        try {
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage directory!", e);
        }
    }

     public void checkUploadPermissions(Long uploaderId, Long groupId) throws Exception {
        if (groupId != null) {
            if (!collaborationService.isUserMemberOfGroup(groupId, uploaderId)) {
                throw new Exception("Access denied. You are not a member of the target group.");
            }
        }
        // Nếu groupId là null (upload cá nhân), mặc định cho phép.
    }

     public void receiveAndStoreFile(InputStream socketInputStream,
                                String originalFileName,
                                long fileSize,
                                String fileType,
                                Long uploaderId,
                                Long groupId,
                                int baseVersion,
                                String notes) throws Exception {

        java.io.File tempFile = java.io.File.createTempFile("upload-", ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytesRead = 0;
            
            while (totalBytesRead < fileSize && (bytesRead = socketInputStream.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalBytesRead))) != -1) {
                fos.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
            }
            if (fileSize != totalBytesRead) {
                throw new IOException("File transfer incomplete. Expected " + fileSize + " bytes but received " + totalBytesRead);
            }
        } catch (IOException e) {
            tempFile.delete();
            throw e;
        }

        String storedFileName = UUID.randomUUID().toString() + "_" + originalFileName;
        Path destinationPath = this.rootLocation.resolve(storedFileName);
        
        try {
            File existingFile = fileDAO.findByNameAndLocation(originalFileName, uploaderId, groupId);
            
            if (existingFile != null) {
                int latestVersionNum = fileVersionDAO.findLatestVersionNumber(existingFile.getId());
                if (baseVersion != latestVersionNum) {
                    throw new Exception("409 CONFLICT: File has been modified by another user. Please download the latest version and merge your changes.");
                }
                
                Files.move(tempFile.toPath(), destinationPath, StandardCopyOption.REPLACE_EXISTING);
                
                FileVersion newVersion = new FileVersion();
                newVersion.setFileId(existingFile.getId());
                newVersion.setVersionNumber(latestVersionNum + 1);
                newVersion.setStoredPath(destinationPath.toString());
                newVersion.setUploaderId(uploaderId);
                newVersion.setNotes(notes);
                fileVersionDAO.save(newVersion);
                
                existingFile.setFileSize(fileSize);
                existingFile.setStoredPath(destinationPath.toString());
                fileDAO.update(existingFile);

            } else {
                Files.move(tempFile.toPath(), destinationPath, StandardCopyOption.REPLACE_EXISTING);
                
                File newFileMetadata = new File();
                newFileMetadata.setFileName(originalFileName);
                newFileMetadata.setStoredPath(destinationPath.toString());
                newFileMetadata.setFileSize(fileSize);
                newFileMetadata.setFileType(fileType);
                newFileMetadata.setOwnerId(uploaderId);
                newFileMetadata.setGroupId(groupId);
                File savedFile = fileDAO.save(newFileMetadata);
                
                FileVersion firstVersion = new FileVersion();
                firstVersion.setFileId(savedFile.getId());
                firstVersion.setVersionNumber(1);
                firstVersion.setStoredPath(destinationPath.toString());
                firstVersion.setUploaderId(uploaderId);
                firstVersion.setNotes(notes != null ? notes : "First version.");
                fileVersionDAO.save(firstVersion);
            }
        } catch (Exception e) {
            Files.deleteIfExists(destinationPath);
            throw e;
        } finally {
            Files.deleteIfExists(tempFile.toPath());
        }
    }
    
    public List<File> listFiles(Long ownerId) throws Exception {
        return fileDAO.findByOwnerId(ownerId);
    }
    
    public List<File> listFilesInGroup(long groupId, long userId) throws Exception {
         System.out.println("DEBUG: Service listFilesInGroup called for group " + groupId + " by user " + userId); // LOG 9
        if (!collaborationService.isUserMemberOfGroup(groupId, userId)) {
             System.out.println("DEBUG: User is NOT a member. Access denied."); // LOG 10
            throw new Exception("Access denied. You are not a member of this group.");
        }
        System.out.println("DEBUG: User is a member. Calling DAO..."); // LOG 11
        List<File> result = fileDAO.findByGroupId(groupId);
         System.out.println("DEBUG: DAO returned " + result.size() + " files."); // LOG 12

        return result;
    }

    public String deleteFile(long fileId, Long requestUserId) throws Exception {
        File file = fileDAO.findById(fileId);
        if (file == null) {
            throw new Exception("File not found.");
        }

        if (file.getOwnerId().equals(requestUserId)) {
            java.io.File physicalFile = new java.io.File(file.getStoredPath());
            if (physicalFile.exists() && !physicalFile.delete()) {
                throw new Exception("Failed to delete physical file.");
            }
            fileDAO.deleteById(fileId);
            return "File deleted permanently.";
        } else if (shareDAO.isFileSharedWithUser(fileId, requestUserId)) {
            shareDAO.removeShare(fileId, requestUserId);
            return "File removed from your shared list.";
        } else {
            throw new Exception("Access denied. You do not have permission to delete this file.");
        }
    }

    public File getFileForDownload(long fileId, Long requestUserId) throws Exception {
        File file = fileDAO.findById(fileId);
        if (file == null) {
            throw new Exception("File not found.");
        }
        
        boolean isOwner = file.getOwnerId().equals(requestUserId);
        boolean isSharedWith = shareDAO.isFileSharedWithUser(fileId, requestUserId);
        boolean isMemberOfGroup = (file.getGroupId() != null) && collaborationService.isUserMemberOfGroup(file.getGroupId(), requestUserId);

        if (!isOwner && !isSharedWith && !isMemberOfGroup) {
            throw new Exception("Access denied. You do not have permission to download this file.");
        }

        return file;
    }

    public List<File> searchMyFiles(String keyword, long userId) throws Exception {
        return fileDAO.searchInMyFiles(keyword, userId);
    }

    public List<File> searchSharedFiles(String keyword, long userId) throws Exception {
        return fileDAO.searchInSharedFiles(keyword, userId);
    }

    public List<File> searchGroupFiles(long groupId, String keyword, long userId) throws Exception {
        // Vẫn cần kiểm tra quyền xem nhóm
        if (!collaborationService.isUserMemberOfGroup(groupId, userId)) {
            throw new Exception("Access denied. You are not a member of this group.");
        }
        return fileDAO.searchInGroupFiles(groupId, keyword);
    }

    public void streamFileToOutput(long fileId, OutputStream outputStream) throws Exception {
        File file = fileDAO.findById(fileId);
        if (file == null) throw new Exception("File not found during streaming.");

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
    public List<FileVersion> getVersionHistory(long fileId, long userId) throws Exception {
        File file = fileDAO.findById(fileId);
        if(file == null) throw new Exception("File not found.");
        
        // Kiểm tra quyền (là owner, được share, hoặc là thành viên của nhóm chứa file)
        boolean hasPermission = file.getOwnerId().equals(userId) || 
                                shareDAO.isFileSharedWithUser(fileId, userId) || 
                                (file.getGroupId() != null && collaborationService.isUserMemberOfGroup(file.getGroupId(), userId));
        
        if(!hasPermission) throw new Exception("Access denied.");

        return fileVersionDAO.findByFileId(fileId);
    }

    public void restoreVersion(long versionId, long requestUserId) throws Exception {
        // --- BƯỚC 1: LẤY THÔNG TIN PHIÊN BẢN CẦN KHÔI PHỤC ---
        FileVersion versionToRestore = fileVersionDAO.findById(versionId);
        if (versionToRestore == null) {
        throw new Exception("Version not found.");
        }
        // --- BƯỚC 2: LẤY THÔNG TIN FILE GỐC VÀ KIỂM TRA QUYỀN ---
        long fileId = versionToRestore.getFileId();
        File mainFile = fileDAO.findById(fileId);
        if (mainFile == null) {
            throw new Exception("Original file associated with this version is missing.");
        }

        // Kiểm tra quyền: Người khôi phục phải là chủ sở hữu file, hoặc là chủ nhóm chứa file đó
        boolean isOwner = mainFile.getOwnerId().equals(requestUserId);
        boolean isGroupOwner = false;
        if (mainFile.getGroupId() != null) {
            Group group = groupDAO.findById(mainFile.getGroupId());
            if (group != null && group.getOwnerId().equals(requestUserId)) {
                isGroupOwner = true;
            }
        }

        if (!isOwner && !isGroupOwner) {
            throw new Exception("Access denied. Only the file owner or group owner can restore versions.");
        }

        // --- BƯỚC 3: TẠO MỘT PHIÊN BẢN MỚI LÀ BẢN SAO CỦA PHIÊN BẢN CŨ ---
        // Điều này giữ lại lịch sử của hành động khôi phục

        // Lấy số hiệu phiên bản mới nhất
        int latestVersionNum = fileVersionDAO.findLatestVersionNumber(fileId);

        // Tạo bản ghi phiên bản mới
        FileVersion restoreVersion = new FileVersion();
        restoreVersion.setFileId(fileId);
        restoreVersion.setVersionNumber(latestVersionNum + 1);
        restoreVersion.setStoredPath(versionToRestore.getStoredPath()); // QUAN TRỌNG: Trỏ đến nội dung của phiên bản cũ
        restoreVersion.setUploaderId(requestUserId); // Người khôi phục là người "upload" phiên bản này
        restoreVersion.setNotes("Restored from v" + versionToRestore.getVersionNumber());
        fileVersionDAO.save(restoreVersion);

        // --- BƯỚC 4: CẬP NHẬT FILE CHÍNH ---
        // Cập nhật lại file chính để trỏ đến nội dung của phiên bản vừa được khôi phục
        mainFile.setStoredPath(versionToRestore.getStoredPath());

        // Lấy kích thước của file vật lý được khôi phục
        java.io.File physicalFile = new java.io.File(versionToRestore.getStoredPath());
        if(physicalFile.exists()){
            mainFile.setFileSize(physicalFile.length());
        }

        fileDAO.update(mainFile);
    }
    public int findLatestVersion(String fileName, Long uploaderId, Long groupId) throws SQLException {
        File existingFile = fileDAO.findByNameAndLocation(fileName, uploaderId, groupId);
        if (existingFile != null) {
            return fileVersionDAO.findLatestVersionNumber(existingFile.getId());
        }
        return 0; // Nếu file chưa tồn tại, base version là 0
    }
}