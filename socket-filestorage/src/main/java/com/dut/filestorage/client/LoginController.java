package com.dut.filestorage.client;

import java.io.IOException;

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
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Username and password cannot be empty.");
            return;
        }
        
        if (socketClient == null) {
            statusLabel.setText("Error: Not connected to the server.");
            return;
        }

        // Thực hiện tác vụ mạng trong một luồng riêng để không làm treo giao diện
        new Thread(() -> {
            boolean loginSuccess = socketClient.login(username, password);
            
            Platform.runLater(() -> {
                if (loginSuccess) {
                    try {
                        SceneManager.loadScene("main-view.fxml", "File Storage - Main");
                    } catch (IOException e) {
                        statusLabel.setText("Error: Failed to load main view.");
                         e.printStackTrace();
                    }
                } else {
                    statusLabel.setText("Invalid username or password, or network error.");
                }
            });
        }).start();
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
    
}