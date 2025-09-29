package com.dut.filestorage.model.entity;

public class Share {
    private Long shareId;
    private String permission;
    private Long sharedByUserId;
    private Long fileId;
    private Long folderId;
    private Long sharedWithUserId;
    private Long sharedWithGroupId;

    public Long getShareId() {
        return shareId;
    }
    public void setShareId(Long shareId) {
        this.shareId = shareId;
    }
    public String getPermission() {
        return permission;
    }
    public void setPermission(String permission) {
        this.permission = permission;
    }
    public Long getSharedByUserId() {
        return sharedByUserId;
    }
    public void setSharedByUserId(Long sharedByUserId) {
        this.sharedByUserId = sharedByUserId;
    }
    public Long getFileId() {
        return fileId;
    }
    public void setFileId(Long fileId) {
        this.fileId = fileId;
    }
    public Long getFolderId() {
        return folderId;
    }
    public void setFolderId(Long folderId) {
        this.folderId = folderId;
    }
    public Long getSharedWithUserId() {
        return sharedWithUserId;
    }
    public void setSharedWithUserId(Long sharedWithUserId) {
        this.sharedWithUserId = sharedWithUserId;
    }
    public Long getSharedWithGroupId() {
        return sharedWithGroupId;
    }
    public void setSharedWithGroupId(Long sharedWithGroupId) {
        this.sharedWithGroupId = sharedWithGroupId;
    }
    
}