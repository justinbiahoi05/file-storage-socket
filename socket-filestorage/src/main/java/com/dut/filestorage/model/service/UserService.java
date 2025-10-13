package com.dut.filestorage.model.service;

import com.dut.filestorage.model.dao.UserDAO;
import com.dut.filestorage.model.entity.User;
import com.dut.filestorage.utils.PasswordUtils;

public class UserService {
    private UserDAO userDAO;

    // Constructor nhận UserDAO từ bên ngoài
    public UserService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    /**
     * Xử lý logic đăng ký người dùng mới.
     */
    public void registerUser(String username, String password, String email) throws Exception {
        // 1. Kiểm tra nghiệp vụ
        if (username == null || username.length() < 3) {
            throw new Exception("Username must be at least 3 characters long.");
        }
        if (password == null || password.length() < 6) {
            throw new Exception("Password must be at least 6 characters long.");
        }
        if (email == null || !email.contains("@")) {
            throw new Exception("Invalid email format.");
        }
        
        // 2. Kiểm tra sự tồn tại (gọi đến DAO)
        if (userDAO.isUsernameExists(username)) {
            throw new Exception("Username '" + username + "' already exists.");
        }
        if (userDAO.isEmailExists(email)) { // Cần thêm hàm này vào UserDAO
            throw new Exception("Email '" + email + "' already exists.");
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

    /**
     * Xử lý logic đăng nhập.
     * @return Đối tượng User nếu thành công, null nếu thất bại.
     */
    public User loginUser(String username, String password) throws Exception {
        User user = userDAO.findByUsername(username);

        if (user != null && PasswordUtils.checkPassword(password, user.getPasswordHash())) {
            // Đăng nhập thành công, trả về đối tượng User đầy đủ thông tin
            return user;
        }
        
        // Đăng nhập thất bại
        return null;
    }
}