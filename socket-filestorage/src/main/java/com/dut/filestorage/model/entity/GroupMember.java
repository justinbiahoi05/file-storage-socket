package com.dut.filestorage.model.entity;

public class GroupMember {
    private Long groupMemberId;
    private String role;
    private Long userId;
    private Long groupId;

    public Long getGroupMemberId() {
        return groupMemberId;
    }
    public void setGroupMemberId(Long groupMemberId) {
        this.groupMemberId = groupMemberId;
    }
    public String getRole() {
        return role;
    }
    public void setRole(String role) {
        this.role = role;
    }
    public Long getUserId() {
        return userId;
    }
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    public Long getGroupId() {
        return groupId;
    }
    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }
    
}