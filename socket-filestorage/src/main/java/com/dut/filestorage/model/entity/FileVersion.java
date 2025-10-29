package com.dut.filestorage.model.entity;

import java.time.LocalDateTime;

public class FileVersion {
    private Long versionId;
    private Long fileId;
    private int versionNumber;
    private String storedPath;
    private Long uploaderId;
    private LocalDateTime createdAt;
    private String notes;
    private String uploaderName;

    public Long getVersionId() {
        return versionId;
    }
    public void setVersionId(Long versionId) {
        this.versionId = versionId;
    }
    public Long getFileId() {
        return fileId;
    }
    public void setFileId(Long fileId) {
        this.fileId = fileId;
    }
    public int getVersionNumber() {
        return versionNumber;
    }
    public void setVersionNumber(int versionNumber) {
        this.versionNumber = versionNumber;
    }
    public String getStoredPath() {
        return storedPath;
    }
    public void setStoredPath(String storedPath) {
        this.storedPath = storedPath;
    }
    public Long getUploaderId() {
        return uploaderId;
    }
    public void setUploaderId(Long uploaderId) {
        this.uploaderId = uploaderId;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    public String getNotes() {
        return notes;
    }
    public void setNotes(String notes) {
        this.notes = notes;
    }
    public String getUploaderName() {
        return uploaderName;
    }
    public void setUploaderName(String uploaderName) {
        this.uploaderName = uploaderName;
    }
    
}