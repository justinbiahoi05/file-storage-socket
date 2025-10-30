package com.dut.filestorage.model.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.dut.filestorage.model.entity.File;
import com.dut.filestorage.utils.DatabaseManager;

public class FileDAO {
    
    public File save(File file) throws SQLException {
        String sql = "INSERT INTO files (file_name, stored_path, file_size, file_type, owner_id, group_id) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            pstmt.setString(1, file.getFileName());
            pstmt.setString(2, file.getStoredPath());
            pstmt.setLong(3, file.getFileSize());
            pstmt.setString(4, file.getFileType());
            pstmt.setLong(5, file.getOwnerId());
            
            if (file.getGroupId() != null) {
                pstmt.setLong(6, file.getGroupId());
            } else {
                pstmt.setNull(6, java.sql.Types.BIGINT);
            }
            
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating file failed, no rows affected.");
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    file.setId(generatedKeys.getLong(1));
                    return file;
                } else {
                    throw new SQLException("Creating file failed, no ID obtained.");
                }
            }
        }
    }
    
    public File findById(long fileId) throws SQLException {
        String sql = "SELECT f.*, u.username as owner_name, " +
                     "(SELECT MAX(fv.version_number) FROM file_versions fv WHERE fv.file_id = f.file_id) as current_version " +
                     "FROM files f JOIN users u ON f.owner_id = u.user_id WHERE f.file_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, fileId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToFile(rs);
                }
            }
        }
        return null;
    }

    public List<File> findByOwnerId(long ownerId) throws SQLException {
        List<File> files = new ArrayList<>();
        String sql = "SELECT f.*, u.username as owner_name, " +
                     "(SELECT MAX(fv.version_number) FROM file_versions fv WHERE fv.file_id = f.file_id) as current_version " +
                     "FROM files f JOIN users u ON f.owner_id = u.user_id " +
                     "WHERE f.owner_id = ? AND f.group_id IS NULL";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, ownerId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    files.add(mapRowToFile(rs));
                }
            }
        }
        return files;
    }
    
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
        String sql = "SELECT f.*, u.username as owner_name, " +
                     "(SELECT MAX(fv.version_number) FROM file_versions fv WHERE fv.file_id = f.file_id) as current_version " +
                     "FROM files f INNER JOIN shares s ON f.file_id = s.file_id " +
                     "INNER JOIN users u ON f.owner_id = u.user_id " +
                     "WHERE s.shared_with_user_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    files.add(mapRowToFile(rs));
                }
            }
        }
        return files;
    }
    
    public List<File> findByGroupId(long groupId) throws SQLException {
        List<File> files = new ArrayList<>();
        String sql = "SELECT f.*, u.username as owner_name, " +
                     "(SELECT MAX(fv.version_number) FROM file_versions fv WHERE fv.file_id = f.file_id) as current_version " +
                     "FROM files f JOIN users u ON f.owner_id = u.user_id " +
                     "WHERE f.group_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, groupId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    files.add(mapRowToFile(rs));
                }
            }
        }
        return files;
    }

    public List<File> searchInMyFiles(String keyword, long userId) throws SQLException {
        List<File> files = new ArrayList<>();
        String sql = "SELECT f.*, u.username as owner_name, " +
                    "(SELECT MAX(fv.version_number) FROM file_versions fv WHERE fv.file_id = f.file_id) as current_version " +
                    "FROM files f JOIN users u ON f.owner_id = u.user_id " +
                    "WHERE f.owner_id = ? AND f.group_id IS NULL AND f.file_name LIKE ?";
        try (Connection conn = DatabaseManager.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.setString(2, "%" + keyword + "%");
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    files.add(mapRowToFile(rs));
                }
            }
        }
        return files;
    }

    public List<File> searchInSharedFiles(String keyword, long userId) throws SQLException {
        List<File> files = new ArrayList<>();
        String sql = "SELECT f.*, u.username as owner_name, " +
                    "(SELECT MAX(fv.version_number) FROM file_versions fv WHERE fv.file_id = f.file_id) as current_version " +
                    "FROM files f JOIN users u ON f.owner_id = u.user_id " +
                    "JOIN shares s ON f.file_id = s.file_id " +
                    "WHERE s.shared_with_user_id = ? AND f.file_name LIKE ?";
        try (Connection conn = DatabaseManager.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.setString(2, "%" + keyword + "%");
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    files.add(mapRowToFile(rs));
                }
            }
        }
        return files;
    }

    public List<File> searchInGroupFiles(long groupId, String keyword) throws SQLException {
        List<File> files = new ArrayList<>();
        String sql = "SELECT f.*, u.username as owner_name, " +
                    "(SELECT MAX(fv.version_number) FROM file_versions fv WHERE fv.file_id = f.file_id) as current_version " +
                    "FROM files f JOIN users u ON f.owner_id = u.user_id " +
                    "WHERE f.group_id = ? AND f.file_name LIKE ?";
        try (Connection conn = DatabaseManager.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, groupId);
            pstmt.setString(2, "%" + keyword + "%");
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    files.add(mapRowToFile(rs));
                }
            }
        }
        return files;
    }
    
    // --- HÀM MAPPING ---

    private File mapRowToFile(ResultSet rs) throws SQLException {
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
        file.setCurrentVersion(rs.getInt("current_version"));
        
        long groupId = rs.getLong("group_id");
        if (!rs.wasNull()) {
            file.setGroupId(groupId);
        }
        
        file.setOwnerName(rs.getString("owner_name"));
        
        return file;
    }

    public File findByNameAndLocation(String fileName, Long uploaderId, Long groupId) throws SQLException {
        String sql;
        PreparedStatement pstmt = null;
        Connection conn = DatabaseManager.getConnection();
        
        try {
            if (groupId != null) {
                sql = "SELECT * FROM files WHERE file_name = ? AND group_id = ?";
                pstmt = conn.prepareStatement(sql);
                pstmt.setString(1, fileName);
                pstmt.setLong(2, groupId);
            } else {
                sql = "SELECT * FROM files WHERE file_name = ? AND owner_id = ? AND group_id IS NULL";
                pstmt = conn.prepareStatement(sql);
                pstmt.setString(1, fileName);
                pstmt.setLong(2, uploaderId);
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    File file = new File();
                    file.setId(rs.getLong("file_id"));
                    file.setFileName(rs.getString("file_name"));
                    file.setStoredPath(rs.getString("stored_path"));
                    file.setFileSize(rs.getLong("file_size"));
                    file.setFileType(rs.getString("file_type"));
                    file.setOwnerId(rs.getLong("owner_id"));
                    long foundGroupId = rs.getLong("group_id");
                    if (!rs.wasNull()) {
                        file.setGroupId(foundGroupId);
                    }
                    return file;
                }
            }
        } finally {
            if (pstmt != null) pstmt.close();
            if (conn != null) conn.close();
        }
        return null;
    }

    public void update(File file) throws SQLException {
        String sql = "UPDATE files SET stored_path = ?, file_size = ?, upload_date = NOW() WHERE file_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, file.getStoredPath());
            pstmt.setLong(2, file.getFileSize());
            pstmt.setLong(3, file.getId());
            pstmt.executeUpdate();
        }
    }
}