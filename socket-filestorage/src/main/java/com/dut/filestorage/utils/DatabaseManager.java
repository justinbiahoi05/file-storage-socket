package com.dut.filestorage.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseManager {
     private static final String DB_URL = "jdbc:mysql://localhost:3306/file_storage_db?useSSL=false&serverTimezone=UTC";
    
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "root";

    // Hàm này cung cấp một kết nối mới cho bất cứ ai cần
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }
}