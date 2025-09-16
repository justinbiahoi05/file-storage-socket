package com.dut.filestorage.model.entity;

import java.time.LocalDateTime;

public class Folder {
    private Long id;
    private String folderName;
    private LocalDateTime createdAt;
    private Long ownerId;
    private Long parentFolderId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFolderName() { return folderName; }
    public void setFolderName(String folderName) { this.folderName = folderName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public Long getParentFolderId() { return parentFolderId; }
    public void setParentFolderId(Long parentFolderId) { this.parentFolderId = parentFolderId; }
}