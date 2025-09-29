package com.dut.filestorage.model.service;

import java.util.List;

import com.dut.filestorage.model.dao.FileDAO;
import com.dut.filestorage.model.dao.GroupDAO;
import com.dut.filestorage.model.dao.GroupMemberDAO;
import com.dut.filestorage.model.dao.ShareDAO;
import com.dut.filestorage.model.dao.UserDAO;
import com.dut.filestorage.model.entity.File;
import com.dut.filestorage.model.entity.Group;
import com.dut.filestorage.model.entity.GroupMember;
import com.dut.filestorage.model.entity.Share;
import com.dut.filestorage.model.entity.User;

public class CollaborationService {
    private ShareDAO shareDAO;
    private FileDAO fileDAO;
    private UserDAO userDAO;
    private GroupDAO groupDAO;
    private GroupMemberDAO groupMemberDAO;

    public CollaborationService() {
        this.shareDAO = new ShareDAO();
        this.fileDAO = new FileDAO();
        this.userDAO = new UserDAO();
        this.groupDAO = new GroupDAO();
        this.groupMemberDAO = new GroupMemberDAO();
    }

    // --- SHARE LOGIC ---
    public void shareFileWithUser(Long fileId, Long ownerId, String targetUsername) throws Exception {
        File file = fileDAO.findById(fileId);
        if (file == null) throw new Exception("File not found.");
        if (!file.getOwnerId().equals(ownerId)) throw new Exception("Access denied. You are not the owner.");

        User targetUser = userDAO.findByUsername(targetUsername);
        if (targetUser == null) throw new Exception("Target user '" + targetUsername + "' not found.");
        if (targetUser.getId().equals(ownerId)) throw new Exception("You cannot share a file with yourself.");
        
         if (shareDAO.isAlreadyShared(fileId, targetUser.getId())) {
            throw new Exception("This file has already been shared with " + targetUsername);
        }
        
        Share newShare = new Share();
        newShare.setPermission("read"); // Tạm thời mặc định quyền đọc
        newShare.setSharedByUserId(ownerId);
        newShare.setFileId(fileId);
        newShare.setSharedWithUserId(targetUser.getId());
        
        shareDAO.save(newShare);
    }
     public List<File> listSharedFiles(long userId) throws Exception {
        return fileDAO.findSharedWithUser(userId);
    }

    // --- GROUP LOGIC ---
    public void createGroup(String groupName, Long ownerId) throws Exception {
        if (groupName == null || groupName.trim().isEmpty()) throw new Exception("Group name cannot be empty.");

        if (groupDAO.findByName(groupName)) {
            throw new Exception("Group name '" + groupName + "' already exists.");
        }
        
        Group newGroup = new Group();
        newGroup.setGroupName(groupName);
        newGroup.setOwnerId(ownerId);
        
        groupDAO.save(newGroup); // Lưu vào CSDL
    }
    public void inviteUserToGroup(Long groupId, Long inviterId, String targetUsername) throws Exception {
        Group group = groupDAO.findById(groupId);
        if (group == null) throw new Exception("Group not found.");
        // Kiểm tra xem người mời có phải chủ nhóm không
        if (!group.getOwnerId().equals(inviterId)) throw new Exception("Access denied. Only group owner can invite members.");

        User targetUser = userDAO.findByUsername(targetUsername);
        if (targetUser == null) throw new Exception("Target user '" + targetUsername + "' not found.");
        
        // User đã ở trong nhóm chưa?
        if (groupMemberDAO.isMember(groupId, targetUser.getId())) {
            throw new Exception(targetUsername + " is already a member of this group.");
        }

        GroupMember newMember = new GroupMember();
        newMember.setGroupId(groupId);
        newMember.setUserId(targetUser.getId());
        newMember.setRole("member"); // Mặc định là member
        groupMemberDAO.save(newMember);
    }
    
}