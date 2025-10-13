package com.dut.filestorage.client;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.dut.filestorage.model.entity.File;
import com.dut.filestorage.model.entity.Group;
import com.dut.filestorage.model.entity.User;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

public class MainViewController {

    // --- FXML Components ---
    @FXML private TableView<Object> mainTableView;
    @FXML private Label statusLabel;
    @FXML private Label currentUserLabel;
    @FXML private Button viewMembersButton;
    private long currentGroupId = -1;

    // --- Buttons ---
    @FXML private Button myFilesButton;
    @FXML private Button sharedFilesButton;
    @FXML private Button myGroupsButton;
    @FXML private Button inviteButton;
    @FXML private Button kickButton;
    @FXML private Button backButton;
    @FXML private Button accessLinkButton;
    
    // --- Class Members ---
    private SocketClient socketClient;
    private enum CurrentView { MY_FILES, SHARED_FILES, MY_GROUPS, GROUP_MEMBERS, GROUP_FILES }
    private CurrentView currentView = CurrentView.MY_FILES;
    
    
    // DANH SÁCH DỮ LIỆU ĐỂ HIỂN THỊ LÊN BẢNG
    private final ObservableList<Object> tableData = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        this.socketClient = SocketClientSingleton.getInstance().getSocketClient();
        mainTableView.setItems(tableData);

        mainTableView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                handleDoubleClickOnTable();
            }
        });

        onMyFilesClick(); // Load view mặc định
    }

    // --- HÀM QUẢN LÝ TRẠNG THÁI GIAO DIỆN ---
    private void updateButtonVisibility() {
        viewMembersButton.setVisible(false);
        inviteButton.setVisible(false);
        kickButton.setVisible(false);
        backButton.setVisible(false);

        switch (currentView) {
            case GROUP_FILES:
                viewMembersButton.setVisible(true);
                backButton.setVisible(true);
                break;
            case GROUP_MEMBERS:
                inviteButton.setVisible(true);
                kickButton.setVisible(true);
                backButton.setVisible(true);
                break;
            default:
                break;
        }
    }

    // --- CÁC HÀM XỬ LÝ SỰ KIỆN ĐIỀU HƯỚNG ---
    @FXML
    protected void onMyFilesClick() {
        currentView = CurrentView.MY_FILES;
        updateButtonVisibility();
        statusLabel.setText("Loading My Files...");
        tableData.clear();
        
        new Thread(() -> {
            try {
                List<File> files = socketClient.listFiles();
                Platform.runLater(() -> {
                    setupFileViewColumns();
                    tableData.setAll(files);
                    statusLabel.setText(files.size() + " file(s) found.");
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Error", "Failed to load files: " + e.getMessage()));
            }
        }).start();
    }

    @FXML
    protected void onSharedFilesClick() {
        currentView = CurrentView.SHARED_FILES;
        updateButtonVisibility();
        statusLabel.setText("Loading files shared with me...");
        tableData.clear();
        
        new Thread(() -> {
            try {
                List<File> files = socketClient.listSharedFiles();
                Platform.runLater(() -> {
                    setupFileViewColumns();
                    tableData.setAll(files);
                    statusLabel.setText(files.size() + " shared file(s) found.");
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Error", "Failed to load shared files: " + e.getMessage()));
            }
        }).start();
    }

    @FXML
    protected void onMyGroupsClick() {
        currentView = CurrentView.MY_GROUPS;
        updateButtonVisibility();
        statusLabel.setText("Loading my groups...");
        tableData.clear();

        new Thread(() -> {
            try {
                List<Group> groups = socketClient.listGroups();
                Platform.runLater(() -> {
                    setupGroupViewColumns();
                    tableData.setAll(groups);
                    statusLabel.setText(groups.size() + " group(s) found.");
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Error", "Failed to load groups: " + e.getMessage()));
            }
        }).start();
    }
    
    @FXML
    protected void onBackButtonClick() {
        onMyGroupsClick();
    }

    // --- HÀM XỬ LÝ SỰ KIỆN NGỮ CẢNH ---
    private void handleDoubleClickOnTable() {
        Object selectedItem = mainTableView.getSelectionModel().getSelectedItem();
        if (selectedItem instanceof Group) {
            Group selectedGroup = (Group) selectedItem;
            loadGroupFilesView(selectedGroup.getGroupId());
        }
    }

    private void loadGroupFilesView(long groupId) {
        this.currentGroupId = groupId;
        currentView = CurrentView.GROUP_FILES;
        updateButtonVisibility();
        statusLabel.setText("Loading files in group " + groupId + "...");
        
        new Thread(() -> {
            try {
                List<File> files = socketClient.listGroupFiles(groupId);
                Platform.runLater(() -> {
                    setupFileViewColumns();
                    tableData.setAll(files);
                    statusLabel.setText(files.size() + " file(s) found in group.");
                });
            } catch (IOException e) {
                 Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Error", "Failed to load group files: " + e.getMessage()));
            }
        }).start();
    }
    
    private void loadGroupMembersView(long groupId) {
        this.currentGroupId = groupId;
        currentView = CurrentView.GROUP_MEMBERS;
        updateButtonVisibility();
        statusLabel.setText("Loading members of group " + groupId + "...");
        
        new Thread(() -> {
            try {
                List<User> members = socketClient.listGroupMembers(groupId);
                Platform.runLater(() -> {
                    setupUserViewColumns();
                    tableData.setAll(members);
                    statusLabel.setText(members.size() + " member(s) found.");
                });
            } catch (IOException e) {
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Error", "Failed to load group members: " + e.getMessage()));
            }
        }).start();
    }

    // --- CÁC HÀM XỬ LÝ SỰ KIỆN THANH CÔNG CỤ ---

    @FXML
    protected void onUploadButtonClick() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select File to Upload");
        java.io.File selectedFile = fileChooser.showOpenDialog(mainTableView.getScene().getWindow());

        if (selectedFile != null) {
            new Thread(() -> {
                try {
                    List<Group> userGroups = socketClient.listGroups();
                    List<String> choices = new ArrayList<>();
                    choices.add("My Files (Personal)");
                    userGroups.forEach(group -> choices.add("Group: " + group.getGroupName()));

                    Platform.runLater(() -> {
                        ChoiceDialog<String> dialog = new ChoiceDialog<>(choices.get(0), choices);
                        dialog.setTitle("Upload Destination");
                        dialog.setHeaderText("Choose where to upload '" + selectedFile.getName() + "'");
                        dialog.setContentText("Upload to:");

                        Optional<String> result = dialog.showAndWait();
                        result.ifPresent(destination -> {
                            Long targetGroupId = null;
                            if (destination.startsWith("Group: ")) {
                                String groupName = destination.substring(7);
                                targetGroupId = userGroups.stream()
                                        .filter(g -> g.getGroupName().equals(groupName))
                                        .findFirst()
                                        .map(Group::getGroupId)
                                        .orElse(null);
                            }
                            uploadFileThread(selectedFile, targetGroupId);
                        });
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Error", "Could not fetch group list: " + e.getMessage()));
                }
            }).start();
        }
    }

    private void uploadFileThread(java.io.File fileToUpload, Long groupId) {
        statusLabel.setText("Uploading " + fileToUpload.getName() + "...");
        new Thread(() -> {
            try {
                String response = socketClient.uploadFile(fileToUpload, groupId);
                Platform.runLater(() -> {
                    showAlert(Alert.AlertType.INFORMATION, "Upload Status", response);
                    if (response != null && response.startsWith("202 OK")) {
                        if (groupId != null) {
                            loadGroupFilesView(groupId);
                        } else {
                            onMyFilesClick();
                        }
                    }
                });
            } catch (IOException e) {
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Upload Error", "Upload failed: " + e.getMessage()));
            }
        }).start();
    }

    @FXML
    protected void onDownloadButtonClick() {
        Object selectedItem = mainTableView.getSelectionModel().getSelectedItem();
        if (selectedItem == null || !(selectedItem instanceof File)) {
            showAlert(AlertType.WARNING, "Selection Error", "Please select a file to download.");
            return;
        }

        File selectedFile = (File) selectedItem;
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Save Location");
        java.io.File saveDirectory = directoryChooser.showDialog(mainTableView.getScene().getWindow());

        if (saveDirectory != null) {
            statusLabel.setText("Downloading " + selectedFile.getFileName() + "...");
            new Thread(() -> {
                try {
                    String response = socketClient.downloadFile(selectedFile.getId(), saveDirectory.getAbsolutePath());
                    Platform.runLater(() -> showAlert(AlertType.INFORMATION, "Download Status", response));
                } catch (IOException e) {
                    Platform.runLater(() -> showAlert(AlertType.ERROR, "Download Error", "Download failed."));
                }
            }).start();
        }
    }

    @FXML
    protected void onDeleteButtonClick() {
        Object selectedItem = mainTableView.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            showAlert(AlertType.WARNING, "Selection Error", "Please select an item to delete.");
            return;
        }

        String itemName = "";
        long itemId = -1;

        if (selectedItem instanceof File) {
            itemName = ((File) selectedItem).getFileName();
            itemId = ((File) selectedItem).getId();
        } else if (selectedItem instanceof Group) {
            itemName = ((Group) selectedItem).getGroupName();
            itemId = ((Group) selectedItem).getGroupId();
        }

        if (confirmAction("Confirm Deletion", "Are you sure you want to delete '" + itemName + "'?")) {
            final long finalItemId = itemId;
            final Object finalSelectedItem = selectedItem;

            new Thread(() -> {
                try {
                    String response = "";
                    if (finalSelectedItem instanceof File) {
                        response = socketClient.deleteFile(finalItemId);
                    } else if (finalSelectedItem instanceof Group) {
                        response = socketClient.deleteGroup(finalItemId);
                    }
                    final String finalResponse = response;
                    Platform.runLater(() -> {
                        showAlert(AlertType.INFORMATION, "Delete Status", finalResponse);
                        refreshCurrentView();
                    });
                } catch (IOException e) {
                     Platform.runLater(() -> showAlert(AlertType.ERROR, "Delete Error", "Action failed."));
                }
            }).start();
        }
    }

    @FXML
    protected void onNewGroupButtonClick() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("New Group");
        dialog.setHeaderText("Create a new user group");
        dialog.setContentText("Please enter the group name:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(groupName -> {
            if (!groupName.trim().isEmpty()) {
                new Thread(() -> {
                    try {
                        String response = socketClient.createGroup(groupName);
                        Platform.runLater(() -> {
                            showAlert(AlertType.INFORMATION, "Group Creation", response);
                            if (response.startsWith("200 OK")) refreshCurrentView();
                        });
                    } catch (IOException e) { /* ... */ }
                }).start();
            }
        });
    }
    
    @FXML
    protected void onShareButtonClick() {
        Object selectedItem = mainTableView.getSelectionModel().getSelectedItem();
        if (selectedItem == null || !(selectedItem instanceof File)) {
            showAlert(AlertType.WARNING, "Selection Error", "Please select a file to share.");
            return;
        }
        File selectedFile = (File) selectedItem;

        try {
            FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("share-dialog.fxml"));
            
            // Tạo một Dialog mới và set DialogPane đã load từ FXML
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setDialogPane(loader.load());
            dialog.setTitle("Share File");

            // Lấy controller và truyền thông tin file vào
            ShareDialogController controller = loader.getController();
            controller.setFileInfo(selectedFile);

            // Hiển thị dialog và chờ kết quả
            Optional<ButtonType> result = dialog.showAndWait();

            // Xử lý khi người dùng bấm nút "Share with User"
            if (result.isPresent() && result.get().getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                String username = controller.getUsernameToShare();
                if (username != null && !username.trim().isEmpty()) {
                    new Thread(() -> {
                        try {
                            String response = socketClient.shareFile(selectedFile.getId(), username);
                            Platform.runLater(() -> showAlert(AlertType.INFORMATION, "Share Status", response));
                        } catch (IOException e) {
                            Platform.runLater(() -> showAlert(AlertType.ERROR, "Share Error", "Failed to share file."));
                        }
                    }).start();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            showAlert(AlertType.ERROR, "Error", "Could not open share dialog.");
        }
    }

    @FXML
    protected void onAccessLinkClick() {
        // 1. TẠO DIALOG TÙY CHỈNH
        Dialog<String[]> dialog = new Dialog<>();
        dialog.setTitle("Access Public Link");
        dialog.setHeaderText("Enter the token you received to access the file.");

        // 2. TẠO CÁC NÚT BẤM
        ButtonType accessButtonType = new ButtonType("Access File", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(accessButtonType, ButtonType.CANCEL);

        // 3. TẠO LAYOUT VÀ CÁC TRƯỜNG NHẬP LIỆU
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField tokenField = new TextField();
        tokenField.setPromptText("Enter token here");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password (if required)");

        grid.add(new Label("Token:"), 0, 0);
        grid.add(tokenField, 1, 0);
        grid.add(new Label("Password:"), 0, 1);
        grid.add(passwordField, 1, 1);
        
        dialog.getDialogPane().setContent(grid);

        // Yêu cầu focus vào ô token khi dialog mở ra
        Platform.runLater(tokenField::requestFocus);
        
        // 4. CHUYỂN ĐỔI KẾT QUẢ KHI NGƯỜI DÙNG BẤM NÚT "ACCESS FILE"
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == accessButtonType) {
                // Trả về một mảng chứa token và password
                return new String[]{tokenField.getText(), passwordField.getText()};
            }
            return null;
        });

        // 5. HIỂN THỊ DIALOG VÀ CHỜ KẾT QUẢ
        Optional<String[]> result = dialog.showAndWait();

        // 6. XỬ LÝ KẾT QUẢ
        result.ifPresent(credentials -> {
            String token = credentials[0].trim();
            String password = credentials[1];

            if (!token.isEmpty()) {
                // Mở cửa sổ chọn nơi lưu file
                DirectoryChooser directoryChooser = new DirectoryChooser();
                directoryChooser.setTitle("Select Save Location for Linked File");
                java.io.File saveDirectory = directoryChooser.showDialog(mainTableView.getScene().getWindow());

                if (saveDirectory != null) {
                    // Bắt đầu luồng download bằng token
                    downloadFileByTokenThread(token, password, saveDirectory.getAbsolutePath());
                }
            }
        });
    }
    
    @FXML
    protected void onLogoutAction() {
        // Hiển thị hộp thoại xác nhận để tránh người dùng bấm nhầm
        if (confirmAction("Confirm Logout", "Are you sure you want to log out and return to the login screen?")) {
            
            // Thực hiện hành động logout trên một luồng riêng để không làm treo giao diện
            new Thread(() -> {
                // Lấy instance của SocketClient và đóng kết nối
                // Việc đóng kết nối sẽ tự động gửi lệnh QUIT nếu hàm close() được viết đúng
                SocketClientSingleton.getInstance().close();

                // Chuyển về màn hình Login trên luồng chính của JavaFX
                Platform.runLater(() -> {
                    try {
                        // Tải lại màn hình login
                        SceneManager.loadScene("login-view.fxml", "File Storage - Login");
                    } catch (IOException e) {
                        // Hiển thị lỗi nếu không thể tải lại màn hình login
                        showAlert(AlertType.ERROR, "UI Error", "Could not load the login screen.");
                        e.printStackTrace();
                    }
                });
            }).start();
        }
    }
    @FXML
    protected void onViewMembersClick() {
        if (currentGroupId != -1) {
            loadGroupMembersView(currentGroupId);
        }
    }
    @FXML
    protected void onInviteMemberClick() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Invite Member");
        dialog.setHeaderText("Invite a new member to group " + currentGroupId);
        dialog.setContentText("Enter username:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(username -> {
            new Thread(() -> {
                try {
                    String response = socketClient.inviteToGroup(currentGroupId, username);
                    Platform.runLater(() -> {
                        showAlert(AlertType.INFORMATION, "Invite Status", response);
                        if (response.startsWith("200 OK")) refreshCurrentView(); // Tải lại danh sách member
                    });
                } catch (IOException e) { /* ... */ }
            }).start();
        });
    }

    @FXML
    protected void onKickMemberClick() {
        Object selectedItem = mainTableView.getSelectionModel().getSelectedItem();
        if (selectedItem == null || !(selectedItem instanceof User)) {
            showAlert(AlertType.WARNING, "Selection Error", "Please select a member to kick.");
            return;
        }
        User selectedUser = (User) selectedItem;

        if (confirmAction("Confirm Kick", "Are you sure you want to kick '" + selectedUser.getUsername() + "'?")) {
            new Thread(() -> {
                try {
                    String response = socketClient.kickFromGroup(currentGroupId, selectedUser.getUsername());
                    Platform.runLater(() -> {
                        showAlert(AlertType.INFORMATION, "Kick Status", response);
                        if (response.startsWith("200 OK")) refreshCurrentView();
                    });
                } catch (IOException e) { /* ... */ }
            }).start();
        }
    }

    // --- HÀM TIỆN ÍCH ---
    private void showAlert(AlertType alertType, String title, String content) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private boolean confirmAction(String title, String content) {
        Alert alert = new Alert(AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private void downloadFileByTokenThread(String token, String password, String savePath) {
        statusLabel.setText("Accessing link and downloading...");
        new Thread(() -> {
            try {
                // Gọi đến hàm trong SocketClient mà mình sẽ tạo ở bước tiếp theo
                String response = socketClient.downloadFileByToken(token, password, savePath);
                Platform.runLater(() -> {
                    showAlert(AlertType.INFORMATION, "Download Status", response);
                    // Không cần refresh view vì file này không thuộc về người dùng
                });
            } catch (IOException e) {
                Platform.runLater(() -> showAlert(AlertType.ERROR, "Error", "Action failed: " + e.getMessage()));
            }
        }).start();
    }

    private void refreshCurrentView() {
        switch(currentView) {
            case MY_FILES: 
                onMyFilesClick(); 
                break;
            case SHARED_FILES: 
                onSharedFilesClick(); 
                break;
            case MY_GROUPS: 
                onMyGroupsClick(); 
                break;
            case GROUP_FILES:
                if (currentGroupId != -1) {
                    loadGroupFilesView(currentGroupId);
                }
                break;
            case GROUP_MEMBERS:
                if (currentGroupId != -1) {
                    loadGroupMembersView(currentGroupId);
                }
                break;
        }
    }

    // --- CÁC HÀM PHỤ ĐỂ CẤU HÌNH CỘT CHO TABLEVIEW ---
private void setupFileViewColumns() {
        mainTableView.getColumns().clear();
        
        TableColumn<Object, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(cellData -> {
            if (cellData.getValue() instanceof File) {
                return new SimpleStringProperty(((File)cellData.getValue()).getFileName());
            }
            return new SimpleStringProperty("");
        });
        nameCol.setPrefWidth(300);
        
        TableColumn<Object, Long> sizeCol = new TableColumn<>("Size (bytes)");
        sizeCol.setCellValueFactory(new PropertyValueFactory<>("fileSize"));

        TableColumn<Object, String> dateCol = new TableColumn<>("Last Modified");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        dateCol.setCellValueFactory(cellData -> {
            if (cellData.getValue() instanceof File) {
                LocalDateTime date = ((File)cellData.getValue()).getUploadDate();
                return new SimpleStringProperty(date != null ? date.format(formatter) : "N/A");
            }
            return new SimpleStringProperty("");
        });
        dateCol.setPrefWidth(150);

        mainTableView.getColumns().addAll(nameCol, sizeCol, dateCol);
    }

    private void setupGroupViewColumns() {
        mainTableView.getColumns().clear();

        TableColumn<Object, Long> idCol = new TableColumn<>("Group ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("groupId")); // SỬA LẠI: 'g' thành 'G'
        
        TableColumn<Object, String> nameCol = new TableColumn<>("Group Name");
        nameCol.setCellValueFactory(cellData -> {
            if (cellData.getValue() instanceof Group) {
                return new SimpleStringProperty(((Group)cellData.getValue()).getGroupName());
            }
            return new SimpleStringProperty("");
        });
        nameCol.setPrefWidth(350);
        
        mainTableView.getColumns().addAll(idCol, nameCol);
    }

    private void setupUserViewColumns() {
        mainTableView.getColumns().clear();
        
        TableColumn<Object, Long> idCol = new TableColumn<>("User ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        
        TableColumn<Object, String> nameCol = new TableColumn<>("Username");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("username"));
        nameCol.setPrefWidth(350);
        
        mainTableView.getColumns().addAll(idCol, nameCol);
    }
}