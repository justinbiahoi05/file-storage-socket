package com.dut.filestorage.model.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;

import com.dut.filestorage.model.entity.PublicLink;
import com.dut.filestorage.utils.DatabaseManager;

public class PublicLinkDAO {
    public void save(PublicLink link) throws SQLException {
        String sql = "INSERT INTO public_links (token, password_hash, expires_at, file_id) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, link.getToken());
            pstmt.setString(2, link.getPasswordHash());
            
            if (link.getExpiresAt() != null) {
                pstmt.setTimestamp(3, Timestamp.valueOf(link.getExpiresAt()));
            } else {
                pstmt.setNull(3, Types.TIMESTAMP);
            }
            
            pstmt.setLong(4, link.getFileId());
            pstmt.executeUpdate();
        }
    }

    public PublicLink findByToken(String token) throws SQLException {
        String sql = "SELECT * FROM public_links WHERE token = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, token);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    PublicLink link = new PublicLink();
                    link.setLinkId(rs.getLong("link_id"));
                    link.setToken(rs.getString("token"));
                    link.setPasswordHash(rs.getString("password_hash"));
                    if (rs.getTimestamp("expires_at") != null) {
                        link.setExpiresAt(rs.getTimestamp("expires_at").toLocalDateTime());
                    }
                    link.setFileId(rs.getLong("file_id"));
                    return link;
                }
            }
        }
        return null;
    }
}