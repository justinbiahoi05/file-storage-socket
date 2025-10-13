package com.dut.filestorage.client;

import java.io.IOException;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class RegisterController {

    @FXML private TextField usernameField;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label statusLabel;

    private SocketClient socketClient;

    @FXML
    public void initialize() {
        this.socketClient = SocketClientSingleton.getInstance().getSocketClient();
        if (this.socketClient == null) {
            statusLabel.setText("Error: Could not connect to the server.");
        }
    }

    @FXML
    protected void onRegisterButtonClick() {
        String username = usernameField.getText();
        String email = emailField.getText();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        // --- Kiểm tra đầu vào phía Client ---
        if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
            statusLabel.setText("All fields are required.");
            return;
        }
        if (!password.equals(confirmPassword)) {
            statusLabel.setText("Passwords do not match.");
            return;
        }
        if (socketClient == null) {
            statusLabel.setText("Error: Not connected to the server.");
            return;
        }

        // --- Gửi yêu cầu đến Server trong luồng riêng ---
        new Thread(() -> {
            try {
                String serverResponse = socketClient.register(username, password, email);

                Platform.runLater(() -> {
                    if (serverResponse != null && serverResponse.startsWith("200 OK")) {
                        statusLabel.setStyle("-fx-text-fill: green;");
                        statusLabel.setText("Registration successful! You can now go back to login.");
                    } else {
                        statusLabel.setStyle("-fx-text-fill: red;");
                        // Trích xuất thông báo lỗi từ server
                        String errorMessage = serverResponse != null ? serverResponse.substring(10) : "Unknown error.";
                        statusLabel.setText("Error: " + errorMessage);
                    }
                });

            } catch (IOException e) {
                Platform.runLater(() -> {
                    statusLabel.setStyle("-fx-text-fill: red;");
                    statusLabel.setText("Error: Communication with server failed.");
                });
                e.printStackTrace();
            }
        }).start();
    }

    @FXML
    protected void onBackButtonClick() {
        try {
            SceneManager.switchScene("login-view.fxml");
        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Error loading login page.");
        }
    }
}