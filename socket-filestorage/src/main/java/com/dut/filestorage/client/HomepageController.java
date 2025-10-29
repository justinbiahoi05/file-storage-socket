package com.dut.filestorage.client;

import java.io.IOException;

import javafx.fxml.FXML;
import javafx.scene.control.Button;

public class HomepageController {

    // Khai báo các nút để có thể tương tác nếu cần
    @FXML private Button featuresButton;
    @FXML private Button useCasesButton;
    @FXML private Button aboutUsButton;
    @FXML private Button loginButton;
    @FXML private Button signUpButton;

    // Hàm này sẽ được gọi khi nút "Log in" được bấm
    @FXML
    protected void onLoginClick() {
        try {
            // Sử dụng SceneManager để chuyển sang màn hình login
            SceneManager.loadScene("login-view.fxml", "File Storage - Login");
        } catch (IOException e) {
            System.err.println("Failed to load the login view.");
            e.printStackTrace();
            // Có thể hiện Alert lỗi ở đây
        }
    }

    // Hàm này sẽ được gọi khi nút "Sign up" được bấm
    @FXML
    protected void onSignUpClick() {
        try {
            // Sử dụng SceneManager để chuyển sang màn hình register
            SceneManager.loadScene("register-view.fxml", "File Storage - Register");
        } catch (IOException e) {
            System.err.println("Failed to load the register view.");
            e.printStackTrace();
        }
    }

    @FXML
    protected void onFeaturesClick() {
        try {
            SceneManager.loadScene("features-view.fxml", "File Storage - Features");
        } catch (IOException e) {
            System.err.println("Failed to load the features view.");
            e.printStackTrace();
            // Có thể hiện Alert lỗi ở đây
        }
    }

    @FXML
    protected void onUseCasesClick() {
        try {
            SceneManager.loadScene("use-cases-view.fxml", "File Storage - Use Cases");
        } catch (IOException e) {
            System.err.println("Failed to load the use cases view.");
            e.printStackTrace();
            // Có thể hiện Alert lỗi ở đây
        }
    }

    @FXML
    protected void onAboutUsClick() {
        try {
            SceneManager.loadScene("about-us-view.fxml", "File Storage - About Us");
        } catch (IOException e) {
            System.err.println("Failed to load the about us view.");
            e.printStackTrace();
            // Có thể hiện Alert lỗi ở đây
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