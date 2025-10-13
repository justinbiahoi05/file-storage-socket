package com.dut.filestorage.model.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.dut.filestorage.model.entity.GroupMember;
import com.dut.filestorage.model.entity.User;
import com.dut.filestorage.utils.DatabaseManager;

public class GroupMemberDAO {
    public void save(GroupMember groupMember) throws SQLException {
        String sql = "INSERT INTO group_members (role, user_id, group_id) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, groupMember.getRole());
            pstmt.setLong(2, groupMember.getUserId());
            pstmt.setLong(3, groupMember.getGroupId());
            pstmt.executeUpdate();
        }
    }
    public boolean isMember(Long groupId, Long userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM group_members WHERE group_id = ? AND user_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, groupId);
            pstmt.setLong(2, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }
    public void removeMember(long groupId, long userId) throws SQLException {
        String sql = "DELETE FROM group_members WHERE group_id = ? AND user_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, groupId);
            pstmt.setLong(2, userId);
            pstmt.executeUpdate();
        }
    }
      public List<User> findMembersByGroupId(long groupId) throws SQLException {
        List<User> members = new ArrayList<>();
        String sql = "SELECT u.user_id, u.username FROM users u " +
                     "INNER JOIN group_members gm ON u.user_id = gm.user_id " +
                     "WHERE gm.group_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, groupId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    User user = new User();
                    user.setId(rs.getLong("user_id"));
                    user.setUsername(rs.getString("username"));
                    members.add(user);
                }
            }
        }
        return members;
    }
}