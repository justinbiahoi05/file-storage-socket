package com.dut.filestorage.model.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.dut.filestorage.model.entity.Folder;
import com.dut.filestorage.utils.DatabaseManager;

public class FolderDAO {
    public void save(Folder folder) throws SQLException {
        String sql = "INSERT INTO folders (folder_name, owner_id, parent_folder_id) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, folder.getFolderName());
            pstmt.setLong(2, folder.getOwnerId());

            if (folder.getParentFolderId() != null) {
                pstmt.setLong(3, folder.getParentFolderId());
            } else {
                pstmt.setNull(3, java.sql.Types.BIGINT);
            }
            
            pstmt.executeUpdate();
        }
    }
}