package com.dut.filestorage.client;

import java.util.List;

import com.dut.filestorage.model.entity.File;
import com.dut.filestorage.model.entity.FileVersion;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

public class VersionHistoryController {

    @FXML private DialogPane dialogPane;
    @FXML private TableView<FileVersion> versionTableView;
    @FXML private TableColumn<FileVersion, Integer> versionNumberColumn;
    @FXML private TableColumn<FileVersion, String> uploaderNameColumn;
    @FXML private TableColumn<FileVersion, String> detailsColumn;

    private File currentFile;
    private SocketClient socketClient;

    @FXML
    public void initialize() {
        this.socketClient = SocketClientSingleton.getInstance().getSocketClient();

        // Cấu hình các cột
        versionNumberColumn.setCellValueFactory(new PropertyValueFactory<>("versionNumber"));
        uploaderNameColumn.setCellValueFactory(new PropertyValueFactory<>("uploaderName"));
        detailsColumn.setCellValueFactory(new PropertyValueFactory<>("notes"));
    }

    // Hàm này được gọi từ MainViewController để truyền file vào
    public void setFile(File file) {
        this.currentFile = file;
        dialogPane.setHeaderText("Version History for: " + file.getFileName());
        loadVersionHistory();
    }

    private void loadVersionHistory() {
        new Thread(() -> {
            try {
                List<FileVersion> versions = socketClient.getVersionHistory(currentFile.getId());
                Platform.runLater(() -> {
                    versionTableView.setItems(FXCollections.observableArrayList(versions));
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    // Hiển thị lỗi bên trong dialog
                    Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to load version history: " + e.getMessage());
                    alert.showAndWait();
                });
            }
        }).start();
    }

    // Hàm này sẽ được gọi bởi nút "Restore"
    public void onRestoreClick() {
        FileVersion selectedVersion = versionTableView.getSelectionModel().getSelectedItem();
        if (selectedVersion == null) {
             new Alert(Alert.AlertType.WARNING, "Please select a version to restore.").show();
             return;
        }

        new Thread(() -> {
            try {
                String response = socketClient.restoreVersion(selectedVersion.getVersionId());
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION, response);
                    alert.showAndWait();
                    if(response.startsWith("200 OK")){
                         System.out.println("Restore successful, dialog will close automatically.");
                    }
                });
            } catch (Exception e) {
                 Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, e.getMessage()).show());
            }
        }).start();
    }
}