package com.dut.filestorage.model.entity;

import java.time.LocalDateTime;
import java.util.Objects;

// Không có @Data ở đây nữa
public class User {
    private Long id;
    private String username;
    private String email;
    private String passwordHash;
    private LocalDateTime createdAt;

    // --- BẮT ĐẦU PHẦN TỰ VIẾT THÊM ---

    // 1. Constructor rỗng (cần thiết cho nhiều thư viện)
    public User() {
    }

    // 2. Constructor đầy đủ (tiện lợi khi tạo đối tượng mới)
    public User(Long id, String username, String email, String passwordHash, LocalDateTime createdAt) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    // 3. Getters và Setters cho từng thuộc tính
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // 4. (Tùy chọn nhưng khuyến khích) Ghi đè phương thức toString() để debug cho dễ
    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                // Không in password hash ra để bảo mật
                ", createdAt=" + createdAt +
                '}';
    }

    // 5. (Tùy chọn nhưng khuyến khích) Ghi đè equals() và hashCode() để so sánh các đối tượng
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id) && Objects.equals(username, user.username) && Objects.equals(email, user.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, username, email);
    }
    
    // --- KẾT THÚC PHẦN TỰ VIẾT THÊM ---
}