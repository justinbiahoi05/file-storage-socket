package com.dut.filestorage.model.entity;

import java.time.LocalDateTime;

public class Group {
    private Long groupId;
    private String groupName;
    private LocalDateTime createdAt;
    private Long ownerId;

    public Long getGroupId() {
        return groupId;
    }
    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }
    public String getGroupName() {
        return groupName;
    }
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    public Long getOwnerId() {
        return ownerId;
    }
    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }
    
}