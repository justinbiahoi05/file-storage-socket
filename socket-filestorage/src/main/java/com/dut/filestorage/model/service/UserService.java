package com.dut.filestorage.model.service;

import com.dut.filestorage.model.dao.UserDAO;
import com.dut.filestorage.model.entity.User;
import com.dut.filestorage.utils.PasswordUtils;


public class UserService {
    private UserDAO userDAO;

    public UserService() {
        this.userDAO = new UserDAO();
    }

    public void registerUser(String username, String password, String email) throws Exception {
        // 1. Kiểm tra nghiệp vụ (ví dụ: username không được quá ngắn)
        if (username == null || username.length() < 3) {
            throw new Exception("Username must be at least 3 characters long.");
        }
        
        // 2. Kiểm tra sự tồn tại (gọi đến DAO)
        if (userDAO.isUsernameExists(username)) {
            throw new Exception("Username '" + username + "' already exists.");
        }

        // 3. Băm mật khẩu
        String hashedPassword = PasswordUtils.hashPassword(password);
        
        // 4. Tạo đối tượng User
        User newUser = new User();
        newUser.setUsername(username);
        newUser.setPasswordHash(hashedPassword);
        newUser.setEmail(email);
        
        // 5. Ra lệnh cho DAO lưu lại
        userDAO.save(newUser);
    }
}