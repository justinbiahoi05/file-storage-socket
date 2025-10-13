package com.dut.filestorage.model.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.dut.filestorage.model.entity.Group;
import com.dut.filestorage.utils.DatabaseManager;

public class GroupDAO {

    public Group save(Group group) throws SQLException {
        String sql = "INSERT INTO groups (group_name, owner_id) VALUES (?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            pstmt.setString(1, group.getGroupName());
            pstmt.setLong(2, group.getOwnerId());
            
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating group failed, no rows affected.");
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    group.setGroupId(generatedKeys.getLong(1)); // Lấy ID vừa được tạo
                    return group;
                } else {
                    throw new SQLException("Creating group failed, no ID obtained.");
                }
            }
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
    
    public Group findById(long groupId) throws SQLException {
        String sql = "SELECT * FROM groups WHERE group_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, groupId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Group group = new Group();
                    group.setGroupId(rs.getLong("group_id"));
                    group.setGroupName(rs.getString("group_name"));
                    group.setOwnerId(rs.getLong("owner_id"));
                    if (rs.getTimestamp("created_at") != null) {
                        group.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    }
                    return group;
                }
            }
        }
        return null;
    }

    public List<Group> findAllGroupsByUserId(long userId) throws SQLException {
        List<Group> groups = new ArrayList<>();
        String sql = "SELECT g.* FROM groups g " +
                     "INNER JOIN group_members gm ON g.group_id = gm.group_id " +
                     "WHERE gm.user_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Group group = new Group();
                    group.setGroupId(rs.getLong("group_id"));
                    group.setGroupName(rs.getString("group_name"));
                    group.setOwnerId(rs.getLong("owner_id"));
                    if (rs.getTimestamp("created_at") != null) {
                        group.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    }
                    groups.add(group);
                }
            }
        }
        return groups;
    }
    
    public void deleteById(long groupId) throws SQLException {
        String sql = "DELETE FROM groups WHERE group_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, groupId);
            pstmt.executeUpdate();
        }
    }
}