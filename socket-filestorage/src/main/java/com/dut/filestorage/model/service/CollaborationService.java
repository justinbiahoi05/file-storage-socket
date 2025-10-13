package com.dut.filestorage.model.service;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.dut.filestorage.model.dao.FileDAO;
import com.dut.filestorage.model.dao.GroupDAO;
import com.dut.filestorage.model.dao.GroupMemberDAO;
import com.dut.filestorage.model.dao.PublicLinkDAO;
import com.dut.filestorage.model.dao.ShareDAO;
import com.dut.filestorage.model.dao.UserDAO;
import com.dut.filestorage.model.entity.File;
import com.dut.filestorage.model.entity.Group;
import com.dut.filestorage.model.entity.GroupMember;
import com.dut.filestorage.model.entity.PublicLink;
import com.dut.filestorage.model.entity.Share;
import com.dut.filestorage.model.entity.User;
import com.dut.filestorage.utils.PasswordUtils;

public class CollaborationService {
    private ShareDAO shareDAO;
    private FileDAO fileDAO;
    private UserDAO userDAO;
    private GroupDAO groupDAO;
    private GroupMemberDAO groupMemberDAO;
    private PublicLinkDAO publicLinkDAO;

    public CollaborationService(UserDAO userDAO) {
        this.userDAO = userDAO;
        this.shareDAO = new ShareDAO();
        this.fileDAO = new FileDAO();
        this.groupDAO = new GroupDAO();
        this.groupMemberDAO = new GroupMemberDAO();
        this.publicLinkDAO = new PublicLinkDAO();
    }

    // --- SHARE LOGIC ---
    public void shareFileWithUser(Long fileId, Long ownerId, String targetUsername) throws Exception {
        File file = fileDAO.findById(fileId);
        if (file == null) throw new Exception("File not found.");
        if (!file.getOwnerId().equals(ownerId)) throw new Exception("Access denied. You are not the owner of this file.");

        User targetUser = userDAO.findByUsername(targetUsername);
        if (targetUser == null) throw new Exception("Target user '" + targetUsername + "' not found.");
        if (targetUser.getId().equals(ownerId)) throw new Exception("You cannot share a file with yourself.");
        
        if (shareDAO.isAlreadyShared(fileId, targetUser.getId())) {
            throw new Exception("This file has already been shared with " + targetUsername);
        }
        
        Share newShare = new Share();
        newShare.setPermission("read_write"); // Mặc định quyền đọc/ghi
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
        if (groupDAO.findByName(groupName)) throw new Exception("Group name '" + groupName + "' already exists.");
        
        Group newGroup = new Group();
        newGroup.setGroupName(groupName);
        newGroup.setOwnerId(ownerId);
        
        Group savedGroup = groupDAO.save(newGroup);

        // Tự động thêm chủ nhóm làm thành viên owner
        addMemberToGroup(savedGroup.getGroupId(), ownerId, ownerId, "owner");
    }
    
    private void addMemberToGroup(long groupId, long inviterId, long targetUserId, String role) throws Exception {
        Group group = groupDAO.findById(groupId);
        if (group == null) throw new Exception("Group not found.");
        if (!group.getOwnerId().equals(inviterId)) throw new Exception("Access denied. Only the group owner can manage members.");
        if (groupMemberDAO.isMember(groupId, targetUserId)) throw new Exception("User is already a member of this group.");

        GroupMember newMember = new GroupMember();
        newMember.setGroupId(groupId);
        newMember.setUserId(targetUserId);
        newMember.setRole(role);
        groupMemberDAO.save(newMember);
    }

    public void inviteUserToGroup(long groupId, long inviterId, String targetUsername) throws Exception {
        User targetUser = userDAO.findByUsername(targetUsername);
        if (targetUser == null) throw new Exception("Target user '" + targetUsername + "' not found.");
        addMemberToGroup(groupId, inviterId, targetUser.getId(), "member");
    }

    public void kickUserFromGroup(long groupId, long kickerId, String targetUsername) throws Exception {
        User targetUser = userDAO.findByUsername(targetUsername);
        if (targetUser == null) throw new Exception("Target user '" + targetUsername + "' not found.");
        
        Group group = groupDAO.findById(groupId);
        if (group == null) throw new Exception("Group not found.");
        if (!group.getOwnerId().equals(kickerId)) throw new Exception("Access denied. Only the group owner can kick members.");
        if (kickerId == targetUser.getId()) throw new Exception("Group owner cannot be kicked.");

        groupMemberDAO.removeMember(groupId, targetUser.getId());
    }
    
    public void deleteGroup(long groupId, long userId) throws Exception {
        Group group = groupDAO.findById(groupId);
        if (group == null) throw new Exception("Group not found.");
        if (!group.getOwnerId().equals(userId)) throw new Exception("Access denied. Only the group owner can delete the group.");
        
        groupDAO.deleteById(groupId);
    }

    public List<Group> listUserGroups(long userId) throws Exception {
        return groupDAO.findAllGroupsByUserId(userId);
    }
    
    public List<User> listGroupMembers(long groupId, long userId) throws Exception {
        if (!groupMemberDAO.isMember(groupId, userId)) {
            throw new Exception("Access denied. You are not a member of this group.");
        }
        return groupMemberDAO.findMembersByGroupId(groupId);
    }
    
    public boolean isUserMemberOfGroup(long groupId, long userId) throws SQLException {
        return groupMemberDAO.isMember(groupId, userId);
    }

    // --- PUBLIC LINK LOGIC ---
     public String createPublicLink(long fileId, long ownerId, String password, String expiresInString) throws Exception {
        File file = fileDAO.findById(fileId);
        if (file == null) throw new Exception("File not found.");
        if (!file.getOwnerId().equals(ownerId)) throw new Exception("Access denied. You are not the owner of this file.");

        String token = UUID.randomUUID().toString();
        String passwordHash = null;
        if (password != null && !password.isEmpty()) {
            passwordHash = PasswordUtils.hashPassword(password);
        }

        LocalDateTime expiresAt = null;
        if (expiresInString != null && !expiresInString.isEmpty()) {
            expiresAt = parseExpiresIn(expiresInString);
        }
        
        PublicLink newLink = new PublicLink();
        newLink.setToken(token);
        newLink.setPasswordHash(passwordHash);
        newLink.setExpiresAt(expiresAt);
        newLink.setFileId(fileId);
        
        publicLinkDAO.save(newLink);
        
        // Trả về token để client có thể xây dựng URL
        return token;
    }

    // Hàm để phân tích chuỗi thời gian
    private LocalDateTime parseExpiresIn(String expiresIn) {
        if (expiresIn.endsWith("h")) {
            long hours = Long.parseLong(expiresIn.replace("h", ""));
            return LocalDateTime.now().plusHours(hours);
        } else if (expiresIn.endsWith("d")) {
            long days = Long.parseLong(expiresIn.replace("d", ""));
            return LocalDateTime.now().plusDays(days);
        }
        return null; // Mặc định không hết hạn nếu định dạng sai
    }
    
    public File validatePublicLink(String token, String password) throws Exception {
        PublicLink link = publicLinkDAO.findByToken(token);
        
        if (link == null) {
            throw new Exception("Link not found or has been deleted.");
        }
        
        if (link.getExpiresAt() != null && LocalDateTime.now().isAfter(link.getExpiresAt())) {
            throw new Exception("This link has expired.");
        }
        
        if (link.getPasswordHash() != null) { // Nếu link yêu cầu mật khẩu
            if (password == null || !PasswordUtils.checkPassword(password, link.getPasswordHash())) {
                throw new Exception("Invalid password.");
            }
        }
        
        // Nếu mọi thứ hợp lệ, trả về thông tin file
        return fileDAO.findById(link.getFileId());
    }
}