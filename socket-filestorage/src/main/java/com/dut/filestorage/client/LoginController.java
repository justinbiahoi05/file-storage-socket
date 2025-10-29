package com.dut.filestorage.client;

import java.io.IOException;

import com.dut.filestorage.model.entity.User;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class LoginController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label statusLabel;
    
    // Singleton pattern để quản lý SocketClient
    private SocketClient socketClient;

    // Hàm này sẽ được gọi tự động sau khi file FXML được load
    @FXML
    public void initialize() {
        // Lấy instance duy nhất của SocketClient
        SocketClientSingleton singleton = SocketClientSingleton.getInstance();
        this.socketClient = singleton.getSocketClient();
        if (this.socketClient == null) {
            // Cố gắng kết nối lại
            boolean reconnected = singleton.reconnect();
            if (reconnected) {
                // Lấy lại đối tượng socketClient mới
                this.socketClient = singleton.getSocketClient();
            } else {
                statusLabel.setText("Error: Could not connect to the server.");
            }
        }
    }

   @FXML
    protected void onLoginButtonClick() {
        String username = usernameField.getText().trim(); // Thêm trim() để loại bỏ khoảng trắng thừa
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Username and password cannot be empty.");
            return;
        }

        if (socketClient == null) {
            statusLabel.setText("Error: Not connected to the server.");
            return;
        }
        
        // Vô hiệu hóa nút bấm để tránh người dùng click nhiều lần
        usernameField.setDisable(true);
        passwordField.setDisable(true);
        statusLabel.setText("Logging in...");

        // --- PHẦN ĐÃ SỬA LẠI HOÀN CHỈNH ---
        new Thread(() -> {
            // Gọi hàm login mới, nó sẽ trả về một đối tượng User hoặc null
            User loggedInUser = socketClient.login(username, password);
            
            // Cập nhật giao diện trên luồng chính của JavaFX
            Platform.runLater(() -> {
                // Kích hoạt lại các ô nhập liệu
                usernameField.setDisable(false);
                passwordField.setDisable(false);

                if (loggedInUser != null) {
                    // --- BƯỚC QUAN TRỌNG: LƯU THÔNG TIN USER LẠI ---
                    SocketClientSingleton.getInstance().setCurrentUser(loggedInUser);
                    
                    statusLabel.setStyle("-fx-text-fill: green;");
                    statusLabel.setText("Login Successful! Loading main view...");
                    
                    try {
                        // Chuyển sang màn hình chính, có thể truyền cả username lên title
                        SceneManager.loadScene("main-view.fxml", "File Storage - " + loggedInUser.getUsername());
                    } catch (IOException e) {
                        statusLabel.setStyle("-fx-text-fill: red;");
                        statusLabel.setText("Error: Failed to load main view.");
                        e.printStackTrace(); // In lỗi đầy đủ ra console để debug
                    }
                } else {
                    // Nếu login trả về null, nghĩa là thất bại
                    statusLabel.setStyle("-fx-text-fill: red;");
                    statusLabel.setText("Invalid username or password, or network error.");
                }
            });
        }).start();
        // --- KẾT THÚC PHẦN SỬA LẠI ---
    }

    @FXML
    protected void onGoToRegisterClick() {
        try {
            SceneManager.switchScene("register-view.fxml");
        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Error loading registration page.");
        }
    }

    @FXML
    protected void onLogoClick() {
    // Nếu đang ở trang chủ rồi thì không cần làm gì
    // Nếu ở các trang khác, hàm này sẽ tải lại trang chủ
        try {
            SceneManager.loadScene("homepage-view.fxml", "File Storage - Welcome");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
}