package com.dut.filestorage.client;

import java.io.IOException;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {
    @Override
        public void start(Stage stage) throws IOException {
            SceneManager.setPrimaryStage(stage);
        
        SocketClientSingleton.getInstance();
        
        FXMLLoader fxmlLoader = new FXMLLoader(MainApp.class.getResource("login-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 350, 270);
        
        stage.setTitle("File Storage Client");
        stage.setScene(scene);
        stage.show();
        
        stage.setOnCloseRequest(event -> {
            SocketClientSingleton.getInstance().close();
        });
    }

    public static void main(String[] args) {
        launch();
    }
}