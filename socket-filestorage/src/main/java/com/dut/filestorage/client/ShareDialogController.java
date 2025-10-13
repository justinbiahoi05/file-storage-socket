package com.dut.filestorage.client;

import com.dut.filestorage.model.entity.File;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class ShareDialogController {

    @FXML private Label fileNameLabel;
    @FXML private TextField usernameField;
    @FXML private CheckBox passwordCheckBox;
    @FXML private PasswordField passwordField;
    @FXML private CheckBox expiresCheckBox;
    @FXML private TextField expiresTimeField;
    @FXML private ComboBox<String> expiresUnitComboBox;
    @FXML private TextField generatedLinkField;

    private File fileToShare;
    private SocketClient socketClient;

    @FXML
    public void initialize() {
        this.socketClient = SocketClientSingleton.getInstance().getSocketClient();

        // Thêm các tùy chọn đơn vị thời gian
        expiresUnitComboBox.getItems().addAll("Hour(s)", "Day(s)");
        expiresUnitComboBox.getSelectionModel().selectFirst(); // Chọn "Hour(s)" làm mặc định

        // Logic để bật/tắt ô mật khẩu
        passwordField.disableProperty().bind(passwordCheckBox.selectedProperty().not());
        
        // Logic để bật/tắt các ô hết hạn
        expiresTimeField.disableProperty().bind(expiresCheckBox.selectedProperty().not());
        expiresUnitComboBox.disableProperty().bind(expiresCheckBox.selectedProperty().not());
    }

    public void setFileInfo(File file) {
        this.fileToShare = file;
        fileNameLabel.setText("Share Options for: " + file.getFileName());
    }

    @FXML
    protected void onGenerateLinkClick() {
        String password = passwordCheckBox.isSelected() ? passwordField.getText() : null;
        
        String expiresInParam = null;
        // Chỉ xử lý nếu người dùng tick vào ô "Set Expiration"
        if (expiresCheckBox.isSelected()) {
            String timeValue = expiresTimeField.getText();
            String unit = expiresUnitComboBox.getSelectionModel().getSelectedItem();

            // Kiểm tra xem người dùng có nhập số hợp lệ không
            if (timeValue.matches("\\d+")) { // Regex kiểm tra có phải là số hay không
                if ("Hour(s)".equals(unit)) {
                    expiresInParam = timeValue + "h";
                } else if ("Day(s)".equals(unit)) {
                    expiresInParam = timeValue + "d";
                }
            } else {
                // Có thể hiển thị một thông báo lỗi ở đây
                generatedLinkField.setText("Error: Please enter a valid number for the time.");
                return;
            }
        }
        
        final String finalExpiresIn = expiresInParam;
        generatedLinkField.setText("Generating...");

        new Thread(() -> {
            try {
                String response = socketClient.createPublicLink(fileToShare.getId(), password, finalExpiresIn);
                Platform.runLater(() -> {
                    if (response.startsWith("200 OK")) {
                        String token = response.substring(response.indexOf("Token: ") + 7);
                        generatedLinkField.setText(token);
                    } else {
                        generatedLinkField.setText("Error: " + response);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> generatedLinkField.setText("Error: Failed to create link."));
            }
        }).start();
    }
    
    public String getUsernameToShare() {
        return usernameField.getText();
    }
}