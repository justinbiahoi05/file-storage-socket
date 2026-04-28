package com.dut.filestorage.client;

import java.io.IOException;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class SceneManager {

    private static Stage primaryStage;

    public static void setPrimaryStage(Stage stage) {
        primaryStage = stage;
    }

    public static void switchScene(String fxmlFile) throws IOException {
        FXMLLoader loader = new FXMLLoader(MainApp.class.getResource(fxmlFile));
        Parent root = loader.load();
        primaryStage.getScene().setRoot(root);
    }
    
    // Hàm này đặc biệt hơn để chuyển scene và thay đổi kích thước cửa sổ
    public static void loadScene(String fxmlFile, String title) throws IOException {
        FXMLLoader loader = new FXMLLoader(MainApp.class.getResource(fxmlFile));
        Scene scene = new Scene(loader.load());
        primaryStage.setTitle(title);
        primaryStage.setScene(scene);
        primaryStage.centerOnScreen();
    }
}