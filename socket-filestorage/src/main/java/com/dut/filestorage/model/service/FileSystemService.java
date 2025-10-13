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
import com.dut.filestorage.model.dao.ShareDAO;
import com.dut.filestorage.model.entity.File;

public class FileSystemService {
    private FileDAO fileDAO;
    private ShareDAO shareDAO;
    private CollaborationService collaborationService;
    private final Path rootLocation = Paths.get("uploads");

    public FileSystemService(CollaborationService collaborationService) {
        this.fileDAO = new FileDAO();
        this.shareDAO = new ShareDAO();
        this.collaborationService = collaborationService;

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
                                    Long groupId) throws Exception {

        if (originalFileName == null || originalFileName.isEmpty()) {
            throw new Exception("File name cannot be empty.");
        }
        
        java.io.File tempFile = java.io.File.createTempFile("upload-", ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytesRead = 0;
            
            while (totalBytesRead < fileSize && (bytesRead = socketInputStream.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalBytesRead))) != -1) {
                fos.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
            }
            if (fileSize != totalBytesRead) throw new IOException("File size mismatch during transfer.");
        }

        try (InputStream tempFileInputStream = new java.io.FileInputStream(tempFile)) {
            storeFileMetadataAndPhysical(tempFileInputStream, originalFileName, fileSize, fileType, uploaderId, groupId);
        } finally {
            tempFile.delete();
        }
    }

    private void storeFileMetadataAndPhysical(InputStream finalInputStream,
                                               String originalFileName,
                                               long fileSize,
                                               String fileType,
                                               Long uploaderId,
                                               Long groupId) throws Exception {
        
        String storedFileName = UUID.randomUUID().toString() + "_" + originalFileName;
        Path destinationPath = this.rootLocation.resolve(storedFileName);

        try {
            Files.copy(finalInputStream, destinationPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new Exception("Failed to store physical file.", e);
        }
        
        File newFileMetadata = new File();
        newFileMetadata.setFileName(originalFileName);
        newFileMetadata.setStoredPath(destinationPath.toString());
        newFileMetadata.setFileSize(fileSize);
        newFileMetadata.setFileType(fileType);
        newFileMetadata.setOwnerId(uploaderId);
        newFileMetadata.setGroupId(groupId);

        try {
            fileDAO.save(newFileMetadata);
        } catch (SQLException e) {
            Files.deleteIfExists(destinationPath);
            throw new Exception("Failed to save file metadata.", e);
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
}