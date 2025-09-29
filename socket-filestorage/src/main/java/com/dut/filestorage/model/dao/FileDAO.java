package com.dut.filestorage.model.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.dut.filestorage.model.entity.File;
import com.dut.filestorage.utils.DatabaseManager;

public class FileDAO {
    
    public void save(File file) throws SQLException {
        String sql = "INSERT INTO files (file_name, stored_path, file_size, file_type, owner_id, folder_id) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, file.getFileName());
            pstmt.setString(2, file.getStoredPath());
            pstmt.setLong(3, file.getFileSize());
            pstmt.setString(4, file.getFileType());
            pstmt.setLong(5, file.getOwnerId());
            
            if (file.getFolderId() != null) {
                pstmt.setLong(6, file.getFolderId());
            } else {
                pstmt.setNull(6, java.sql.Types.BIGINT);
            }
            
            pstmt.executeUpdate();
        }
    }
    // Tìm file theo ID
    public File findById(long fileId) throws SQLException {
        String sql = "SELECT * FROM files WHERE file_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, fileId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    File file = new File();
                    file.setId(rs.getLong("file_id"));
                    file.setFileName(rs.getString("file_name"));
                    file.setStoredPath(rs.getString("stored_path"));
                    file.setFileSize(rs.getLong("file_size"));
                    file.setFileType(rs.getString("file_type"));
                    if (rs.getTimestamp("upload_date") != null) {
                        file.setUploadDate(rs.getTimestamp("upload_date").toLocalDateTime());
                    }
                    file.setOwnerId(rs.getLong("owner_id"));
                    // Xử lý folder_id có thể là NULL
                    long folderId = rs.getLong("folder_id");
                    if (!rs.wasNull()) {
                        file.setFolderId(folderId);
                    }
                    return file;
                }
            }
        }
        return null;
    }

    // Lấy danh sách file của một user
    public List<File> findByOwnerId(long ownerId) throws SQLException {
        List<File> files = new ArrayList<>();
        String sql = "SELECT * FROM files WHERE owner_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, ownerId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    File file = new File();
                    file.setId(rs.getLong("file_id"));
                    file.setFileName(rs.getString("file_name"));
                    file.setStoredPath(rs.getString("stored_path"));
                    file.setFileSize(rs.getLong("file_size"));
                    file.setFileType(rs.getString("file_type"));
                    if (rs.getTimestamp("upload_date") != null) {
                        file.setUploadDate(rs.getTimestamp("upload_date").toLocalDateTime());
                    }
                    file.setOwnerId(rs.getLong("owner_id"));
                    // Xử lý folder_id có thể là NULL
                    long folderId = rs.getLong("folder_id");
                    if (!rs.wasNull()) {
                        file.setFolderId(folderId);
                    }
                    files.add(file);
                }
            }
        }
        return files;
    }
    
    // Xóa file theo ID
    public void deleteById(long fileId) throws SQLException {
        String sql = "DELETE FROM files WHERE file_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, fileId);
            pstmt.executeUpdate();
        }
    }
    public List<File> findSharedWithUser(long userId) throws SQLException {
        List<File> files = new ArrayList<>();
        String sql = "SELECT f.* FROM files f " +
                    "INNER JOIN shares s ON f.file_id = s.file_id " + 
                    "WHERE s.shared_with_user_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    File file = new File();

                    // Ánh xạ dữ liệu từ CSDL vào đối tượng file
                    file.setId(rs.getLong("file_id"));
                    file.setFileName(rs.getString("file_name"));
                    file.setStoredPath(rs.getString("stored_path"));
                    file.setFileSize(rs.getLong("file_size"));
                    file.setFileType(rs.getString("file_type"));
                    if (rs.getTimestamp("upload_date") != null) {
                        file.setUploadDate(rs.getTimestamp("upload_date").toLocalDateTime());
                    }
                    file.setOwnerId(rs.getLong("owner_id"));
                    long folderId = rs.getLong("folder_id");
                    if (!rs.wasNull()) {
                        file.setFolderId(folderId);
                    }
                    files.add(file);
                }
            }
        }
        return files;
    }
}