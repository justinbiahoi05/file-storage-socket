package com.dut.filestorage.model.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.dut.filestorage.model.entity.Group;
import com.dut.filestorage.utils.DatabaseManager;

public class GroupDAO {
    public void save(Group group) throws SQLException {
        String sql = "INSERT INTO groups (group_name, owner_id) VALUES (?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, group.getGroupName());
            pstmt.setLong(2, group.getOwnerId());
            
            pstmt.executeUpdate();
        }
    }
    public boolean findByName(String groupName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM groups WHERE group_name = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, groupName);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }
    public Group findById(Long groupId) throws SQLException {
        String sql = "SELECT * FROM groups WHERE group_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, groupId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Group group = new Group();
                    group.setGroupId(rs.getLong("group_id"));
                    group.setGroupName(rs.getString("group_name"));
                    if (rs.getTimestamp("created_at") != null) {
                        group.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    }
                    group.setOwnerId(rs.getLong("owner_id"));
                    return group;
                }
            }
        }
        return null;
    }
    
}