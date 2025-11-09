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
import java.sql.Connection;
import java.util.List;
import java.util.UUID;

import com.dut.filestorage.model.dao.FileDAO;
import com.dut.filestorage.model.dao.FileVersionDAO;
import com.dut.filestorage.model.dao.GroupDAO;
import com.dut.filestorage.model.dao.ShareDAO;
import com.dut.filestorage.model.entity.File;
import com.dut.filestorage.model.entity.FileVersion;
import com.dut.filestorage.model.entity.Group;
import com.dut.filestorage.utils.DatabaseManager;

public class FileSystemService {
    private FileDAO fileDAO;
    private ShareDAO shareDAO;
    private CollaborationService collaborationService;
    private final Path rootLocation = Paths.get("uploads");
    private FileVersionDAO fileVersionDAO;
    private GroupDAO groupDAO;

    public FileSystemService(CollaborationService collaborationService) {
        this.fileDAO = new FileDAO();
        this.shareDAO = new ShareDAO();
        this.collaborationService = collaborationService;
        this.fileVersionDAO = new FileVersionDAO();
        this.groupDAO = new GroupDAO();

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

        Connection conn = null;
        Path destinationPath = null;
        File existingFile = null; 

        try {
            conn = DatabaseManager.getConnection();
            conn.setAutoCommit(false);

            existingFile = fileDAO.findByNameAndLocationForUpdate(conn, originalFileName, uploaderId, groupId);
            
            String storedFileName = UUID.randomUUID().toString() + "_" + originalFileName;
            destinationPath = this.rootLocation.resolve(storedFileName);

            if (existingFile != null) {
                // *** KIỂM TRA QUYỀN KHÓA ***
                if (!existingFile.isLocked() || !existingFile.getLockedByUserId().equals(uploaderId)) {
                    throw new Exception("403 FORBIDDEN: You must lock the file before uploading a new version.");
                }
                
                int latestVersionNum = fileVersionDAO.findLatestVersionNumber(conn, existingFile.getId());
                
                Files.move(tempFile.toPath(), destinationPath, StandardCopyOption.REPLACE_EXISTING);
                
                FileVersion newVersion = new FileVersion();
                newVersion.setFileId(existingFile.getId());
                newVersion.setVersionNumber(latestVersionNum + 1);
                newVersion.setStoredPath(destinationPath.toString());
                newVersion.setUploaderId(uploaderId);
                newVersion.setNotes(notes);
                fileVersionDAO.save(conn, newVersion);
                
                existingFile.setFileSize(fileSize);
                existingFile.setStoredPath(destinationPath.toString());
                fileDAO.update(conn, existingFile);

            } else {
                Files.move(tempFile.toPath(), destinationPath, StandardCopyOption.REPLACE_EXISTING);
                
                File newFileMetadata = new File();
                newFileMetadata.setFileName(originalFileName);
                newFileMetadata.setStoredPath(destinationPath.toString());
                newFileMetadata.setFileSize(fileSize);
                newFileMetadata.setFileType(fileType);
                newFileMetadata.setOwnerId(uploaderId);
                newFileMetadata.setGroupId(groupId);
                File savedFile = fileDAO.save(conn, newFileMetadata);
                
                FileVersion firstVersion = new FileVersion();
                firstVersion.setFileId(savedFile.getId());
                firstVersion.setVersionNumber(1);
                firstVersion.setStoredPath(destinationPath.toString());
                firstVersion.setUploaderId(uploaderId);
                firstVersion.setNotes(notes != null ? notes : "First version.");
                fileVersionDAO.save(conn, firstVersion);
            }
            
            // *** TỰ ĐỘNG MỞ KHÓA SAU KHI UPLOAD ***
            if (existingFile != null) {
                // Yêu cầu hàm unlockFile(Connection, long) trong FileDAO
                fileDAO.unlockFile(conn, existingFile.getId()); 
            }
            
            conn.commit();

        } catch (Exception e) {
            if (conn != null) conn.rollback();
            if (destinationPath != null) Files.deleteIfExists(destinationPath);
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
            Files.deleteIfExists(tempFile.toPath());
        }
    }
    
    // ---  lockAndPrepareDownload ---
    public File lockAndPrepareDownload(long fileId, long userId) throws Exception {
        File file = fileDAO.findById(fileId);
        if (file == null) throw new Exception("File not found.");
        
        boolean isOwner = file.getOwnerId().equals(userId);
        boolean isSharedWith = shareDAO.isFileSharedWithUser(fileId, userId);
        boolean isMemberOfGroup = (file.getGroupId() != null) && collaborationService.isUserMemberOfGroup(file.getGroupId(), userId);

        if (!isOwner && !isSharedWith && !isMemberOfGroup) {
            throw new Exception("Access denied. You do not have permission to download this file.");
        }

        if (file.isLocked()) {
             // Nếu đã khóa bởi chính mình, cho phép download
            if (file.getLockedByUserId().equals(userId)) {
                 return fileDAO.findById(fileId);
            }
            throw new Exception("409 CONFLICT: File is currently locked by user " + file.getLockedByUsername());
        }
        
        fileDAO.lockFile(fileId, userId);
        return fileDAO.findById(fileId); // Lấy lại thông tin mới nhất (với tên người khóa)
    }

    // ---  unlockFile ---
    public void unlockFile(long fileId, long userId) throws Exception {
        File file = fileDAO.findById(fileId);
        if (file == null) throw new Exception("File not found.");
        
        if (!file.isLocked() || !file.getLockedByUserId().equals(userId)) {
            throw new Exception("Access denied. You are not locking this file.");
        }
        fileDAO.unlockFile(fileId);
    }

    // ---: lockFile (chỉ khóa, không download) ---
    public void lockFile(long fileId, long userId) throws Exception {
        File file = fileDAO.findById(fileId);
        if (file == null) {
            throw new Exception("File not found.");
        }

        if (file.isLocked()) {
            if (file.getLockedByUserId().equals(userId)) {
                System.out.println("File " + fileId + " is already locked by the same user " + userId);
                return; // Không báo lỗi
            } else {
                throw new Exception("409 CONFLICT: File is currently locked by user " + file.getLockedByUsername());
            }
        }
        
        fileDAO.lockFile(fileId, userId);
    }

    public List<File> listFiles(Long ownerId) throws Exception {
        return fileDAO.findByOwnerId(ownerId);
    }
    
    public List<File> listFilesInGroup(long groupId, long userId) throws Exception {
        if (!collaborationService.isUserMemberOfGroup(groupId, userId)) {
            throw new Exception("Access denied. You are not a member of this group.");
        }
        return fileDAO.findByGroupId(groupId);
    }

    // ---  Thêm check-lock ---
     public String deleteFile(long fileId, Long requestUserId) throws Exception {
         File file = fileDAO.findById(fileId);
         if (file == null) throw new Exception("File not found.");

         if (file.isLocked() && !file.getLockedByUserId().equals(requestUserId)) {
              throw new Exception("409 CONFLICT: File is locked by " + file.getLockedByUsername() + ". Cannot delete.");
         }

         boolean isFileOwner = file.getOwnerId().equals(requestUserId);
         boolean isGroupOwner = false;
         if (file.getGroupId() != null) {
             Group group = groupDAO.findById(file.getGroupId());
             if (group != null && group.getOwnerId().equals(requestUserId)) isGroupOwner = true;
         }

         if (isFileOwner || isGroupOwner) {
             deleteFilePermanently(file);
             return isFileOwner ? "File deleted permanently." : "File deleted permanently by group owner.";
         }
         
         if (shareDAO.isFileSharedWithUser(fileId, requestUserId)) {
             shareDAO.removeShare(fileId, requestUserId);
             return "File removed from your shared list.";
         } 
         
         throw new Exception("Access denied. You do not have permission to delete this file.");
     }

    // --- Thêm check-lock ---
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

        if (file.isLocked() && !file.getLockedByUserId().equals(requestUserId)) {
             throw new Exception("409 CONFLICT: File is locked by " + file.getLockedByUsername() + ". Cannot download.");
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
        
        boolean hasPermission = file.getOwnerId().equals(userId) || 
                                 shareDAO.isFileSharedWithUser(fileId, userId) || 
                                 (file.getGroupId() != null && collaborationService.isUserMemberOfGroup(file.getGroupId(), userId));
        
        if(!hasPermission) throw new Exception("Access denied.");

        return fileVersionDAO.findByFileId(fileId);
    }

    public void restoreVersion(long versionId, long requestUserId) throws Exception {
        Connection conn = null;
        try {
            conn = DatabaseManager.getConnection();
            conn.setAutoCommit(false);

            FileVersion versionToRestore = fileVersionDAO.findById(versionId);
            if (versionToRestore == null) throw new Exception("Version not found.");
            
            long fileId = versionToRestore.getFileId();
            File mainFile = fileDAO.findById(fileId);
            if (mainFile == null) throw new Exception("Original file is missing.");

            if (mainFile.isLocked() && !mainFile.getLockedByUserId().equals(requestUserId)) {
                throw new Exception("409 CONFLICT: File is locked by " + mainFile.getLockedByUsername() + ". Cannot restore version.");
            }
            
            boolean isOwner = mainFile.getOwnerId().equals(requestUserId);
            boolean isGroupOwner = false;
            if (mainFile.getGroupId() != null) {
                Group group = groupDAO.findById(mainFile.getGroupId());
                if (group != null && group.getOwnerId().equals(requestUserId)) {
                    isGroupOwner = true;
                }
            }
            if (!isOwner && !isGroupOwner) {
                throw new Exception("Access denied. Only file owner or group owner can restore versions.");
            }

            int latestVersionNum = fileVersionDAO.findLatestVersionNumber(conn, fileId);
            
            FileVersion restoreAsNewVersion = new FileVersion();
            restoreAsNewVersion.setFileId(fileId);
            restoreAsNewVersion.setVersionNumber(latestVersionNum + 1);
            restoreAsNewVersion.setStoredPath(versionToRestore.getStoredPath());
            restoreAsNewVersion.setUploaderId(requestUserId);
            restoreAsNewVersion.setNotes("Restored from v" + versionToRestore.getVersionNumber());
            fileVersionDAO.save(conn, restoreAsNewVersion);
            
            mainFile.setStoredPath(versionToRestore.getStoredPath());
            java.io.File physicalFile = new java.io.File(versionToRestore.getStoredPath());
            if(physicalFile.exists()){
                mainFile.setFileSize(physicalFile.length());
            }
            fileDAO.update(conn, mainFile);

            conn.commit();

        } catch (Exception e) {
            if (conn != null) conn.rollback();
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }


     private void deleteFilePermanently(File file) throws Exception {
         // Xóa tất cả các phiên bản vật lý trước
         List<FileVersion> versions = fileVersionDAO.findByFileId(file.getId());
         for (FileVersion version : versions) {
             try {
                 Files.deleteIfExists(Paths.get(version.getStoredPath()));
             } catch (IOException e) {
                 System.err.println("Warning: Could not delete physical file for version: " + version.getStoredPath());
             }
         }
         // (Xóa bản thân file đã được bao gồm trong vòng lặp trên)
         
         // Xóa trong DB (DB nên được setup ON DELETE CASCADE)
         fileDAO.deleteById(file.getId());
     }
}