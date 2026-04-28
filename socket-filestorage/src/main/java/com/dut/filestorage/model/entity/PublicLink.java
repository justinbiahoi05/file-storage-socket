package com.dut.filestorage.model.entity;

import java.time.LocalDateTime;

public class PublicLink {
    private Long linkId;
    private String token;
    private String passwordHash;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private Long fileId;

    public Long getLinkId() {
        return linkId;
    }
    public void setLinkId(Long linkId) {
        this.linkId = linkId;
    }
    public String getToken() {
        return token;
    }
    public void setToken(String token) {
        this.token = token;
    }
    public String getPasswordHash() {
        return passwordHash;
    }
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    public Long getFileId() {
        return fileId;
    }
    public void setFileId(Long fileId) {
        this.fileId = fileId;
    }
}