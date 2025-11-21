package com.dut.filestorage.model.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.dut.filestorage.model.entity.FileVersion;
import com.dut.filestorage.utils.DatabaseManager;

public class FileVersionDAO {
    public void save(FileVersion version) throws SQLException {
        String sql = "INSERT INTO file_versions (file_id, version_number, stored_path, uploader_id, notes) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setLong(1, version.getFileId());
            pstmt.setInt(2, version.getVersionNumber());
            pstmt.setString(3, version.getStoredPath());
            pstmt.setLong(4, version.getUploaderId());
            pstmt.setString(5, version.getNotes());
            
            pstmt.executeUpdate();
        }
    }


    public void save(Connection conn, FileVersion version) throws SQLException {
        String sql = "INSERT INTO file_versions (file_id, version_number, stored_path, uploader_id, notes) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setLong(1, version.getFileId());
            pstmt.setInt(2, version.getVersionNumber());
            pstmt.setString(3, version.getStoredPath());
            pstmt.setLong(4, version.getUploaderId());
            pstmt.setString(5, version.getNotes());
            
            pstmt.executeUpdate();
        }
    }
    
    public List<FileVersion> findByFileId(long fileId) throws SQLException {
        List<FileVersion> versions = new ArrayList<>();
        String sql = "SELECT fv.*, u.username as uploader_name FROM file_versions fv " +
                     "JOIN users u ON fv.uploader_id = u.user_id " +
                     "WHERE fv.file_id = ? ORDER BY fv.version_number DESC";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, fileId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                     FileVersion version = new FileVersion();
                version.setVersionId(rs.getLong("version_id"));
                version.setFileId(rs.getLong("file_id"));
                version.setVersionNumber(rs.getInt("version_number"));
                version.setStoredPath(rs.getString("stored_path")); // Bổ sung để findById đầy đủ hơn
                version.setUploaderId(rs.getLong("uploader_id"));
                version.setUploaderName(rs.getString("uploader_name"));
                if(rs.getTimestamp("created_at") != null)
                    version.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                version.setNotes(rs.getString("notes"));
                versions.add(version);
                }
            }
        }
        return versions;
    }

    public FileVersion findById(long versionId) throws SQLException {
        String sql = "SELECT * FROM file_versions WHERE version_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, versionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    FileVersion version = new FileVersion();
                    version.setVersionId(rs.getLong("version_id"));
                    version.setFileId(rs.getLong("file_id"));
                    version.setVersionNumber(rs.getInt("version_number"));
                    version.setStoredPath(rs.getString("stored_path"));
                    version.setUploaderId(rs.getLong("uploader_id"));
                    if(rs.getTimestamp("created_at") != null)
                        version.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    version.setNotes(rs.getString("notes"));
                    
                    return version;
                }
            }
        }
        return null;
    }

    public int findLatestVersionNumber(long fileId) throws SQLException {
        String sql = "SELECT MAX(version_number) FROM file_versions WHERE file_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setLong(1, fileId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                // Nếu tìm thấy kết quả (ngay cả khi là 0)
                if (rs.next()) {
                    // getInt(1) sẽ trả về 0 nếu không có phiên bản nào được tìm thấy (giá trị MAX của một tập rỗng là NULL, getInt(NULL) = 0)
                    return rs.getInt(1);
                }
            }
        }
        // Trả về 0 nếu có lỗi xảy ra
        return 0;
    }

     public int findLatestVersionNumber(Connection conn, long fileId) throws SQLException {
        String sql = "SELECT MAX(version_number) FROM file_versions WHERE file_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, fileId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }
}