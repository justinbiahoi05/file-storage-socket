package com.dut.filestorage.model.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.dut.filestorage.model.entity.GroupMember;
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
}