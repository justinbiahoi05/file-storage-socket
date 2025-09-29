package com.dut.filestorage.model.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.dut.filestorage.model.entity.Share;
import com.dut.filestorage.utils.DatabaseManager;

public class ShareDAO {
    public void save(Share share) throws SQLException {
        String sql = "INSERT INTO shares (permission, shared_by_user_id, file_id, shared_with_user_id) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, share.getPermission());
            pstmt.setLong(2, share.getSharedByUserId());
            pstmt.setLong(3, share.getFileId());
            pstmt.setLong(4, share.getSharedWithUserId());
            pstmt.executeUpdate();
        }
    }
    public boolean isAlreadyShared(Long fileId, Long targetUserId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM shares WHERE file_id = ? AND shared_with_user_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, fileId);
            pstmt.setLong(2, targetUserId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }
    public boolean isFileSharedWithUser(long fileId, long userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM shares WHERE file_id = ? AND shared_with_user_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setLong(1, fileId);
            pstmt.setLong(2, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }
     // Xóa một lượt chia sẻ dựa trên file_id và user_id của người được chia sẻ
    public void removeShare(long fileId, long sharedWithUserId) throws SQLException {
        String sql = "DELETE FROM shares WHERE file_id = ? AND shared_with_user_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, fileId);
            pstmt.setLong(2, sharedWithUserId);
            pstmt.executeUpdate();
        }
    }
}