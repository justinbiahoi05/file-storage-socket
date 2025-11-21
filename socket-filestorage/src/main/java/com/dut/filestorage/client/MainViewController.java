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
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

public class MainViewController {

    // --- FXML Components ---
    @FXML private TableView<Object> mainTableView;
    @FXML private Label statusLabel;
    @FXML private Label currentUserLabel;
    @FXML private Button viewMembersButton;
    @FXML private TextField searchField;
    private long currentGroupId = -1;

    // --- Buttons ---
    @FXML private Button inviteButton;
    @FXML private Button kickButton;
    @FXML private Button backButton;
    @FXML private Button lockButton;
    @FXML private Button unlockButton;
    @FXML private Button viewButton;
    @FXML private Button uploadButton;
    @FXML private Button deleteButton;
    @FXML private Button shareButton;
    @FXML private Button newGroupButton;
    @FXML private Button historyButton;
    @FXML private Button accessLinkButton;
    @FXML private Pane searchPane;
    
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
        User currentUser = SocketClientSingleton.getInstance().getCurrentUser();
        if (currentUser != null) {
            currentUserLabel.setText(currentUser.getUsername());
        } else {
            currentUserLabel.setText("Welcome, Guest!");
        }

        onMyFilesClick();
    }

    // --- HÀM QUẢN LÝ TRẠNG THÁI GIAO DIỆN ---
    private void updateButtonVisibility() {
        // --- ẨN TẤT CẢ CÁC NÚT ĐIỀU KHIỂN ---
        
        // Nút thanh công cụ chính
        uploadButton.setVisible(false);
        viewButton.setVisible(false);
        lockButton.setVisible(false);
        unlockButton.setVisible(false);
        deleteButton.setVisible(false);
        shareButton.setVisible(false);
        newGroupButton.setVisible(false);
        historyButton.setVisible(false);
        accessLinkButton.setVisible(false);
        
        // Nút thanh tìm kiếm/lịch sử
        if (searchField != null) searchField.setVisible(false);
        if (searchField != null && searchField.getParent() != null && searchField.getParent() instanceof Pane) {
             ((Pane)searchField.getParent()).setVisible(false);
        }

        // Nút ngữ cảnh nhóm
        viewMembersButton.setVisible(false);
        inviteButton.setVisible(false);
        kickButton.setVisible(false);
        backButton.setVisible(false);

        // --- CHỌN LỌC HIỂN THỊ LẠI DỰA TRÊN VIEW ---
        
        // Hiện thanh tìm kiếm và Access Link cho tất cả các view
        if (searchField != null) searchField.setVisible(true);
        if (searchField != null && searchField.getParent() != null && searchField.getParent() instanceof Pane) {
             ((Pane)searchField.getParent()).setVisible(true);
        }
        accessLinkButton.setVisible(true);


        switch (currentView) {
            case MY_FILES:
            case GROUP_FILES: // View My Files và Group Files có các nút giống nhau
                uploadButton.setVisible(true);
                viewButton.setVisible(true);
                lockButton.setVisible(true);
                unlockButton.setVisible(true);
                deleteButton.setVisible(true);
                shareButton.setVisible(true);
                newGroupButton.setVisible(true);
                historyButton.setVisible(true);
                
                if (currentView == CurrentView.GROUP_FILES) {
                    viewMembersButton.setVisible(true);
                    backButton.setVisible(true);
                }
                break;
                
            case SHARED_FILES: // <-- LOGIC MỚI CỦA BẠN
                // Chỉ hiện View, Delete (để xóa lượt share), và New Group
                viewButton.setVisible(true);
                deleteButton.setVisible(true);
                newGroupButton.setVisible(true);
                break;

            case MY_GROUPS:
                // Chỉ hiện New Group và Delete
                newGroupButton.setVisible(true);
                deleteButton.setVisible(true);
                break;

            case GROUP_MEMBERS:
                // Ẩn thanh tìm kiếm
                 if (searchField != null && searchField.getParent() != null && searchField.getParent() instanceof Pane) {
                     ((Pane)searchField.getParent()).setVisible(false);
                 }
                accessLinkButton.setVisible(false); // Ẩn luôn access link

                // Hiện các nút quản lý thành viên
                inviteButton.setVisible(true);
                kickButton.setVisible(true);
                backButton.setVisible(true);
                break;
                
            default:
                // Mặc định (phòng hờ)
                newGroupButton.setVisible(true);
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
            // Phải chạy trên luồng nền để không treo UI
            new Thread(() -> {
                try {
                    // 1. Lấy danh sách nhóm trước
                    List<Group> userGroups = socketClient.listGroups();
                    List<String> choices = new ArrayList<>();
                    choices.add("My Files (Personal)"); // Lựa chọn 1
                    userGroups.forEach(group -> choices.add("Group: " + group.getGroupName())); // Lựa chọn 2, 3...

                    // 2. Hiển thị dialog trên luồng JavaFX
                    Platform.runLater(() -> {
                        
                        ChoiceDialog<String> dialog = new ChoiceDialog<>(choices.get(0), choices);
                        dialog.setTitle("Upload Destination");
                        dialog.setHeaderText("Choose where to upload '" + selectedFile.getName() + "'");
                        dialog.setContentText("Upload to:");

                        Optional<String> result = dialog.showAndWait();
                        
                        // 3. Xử lý kết quả
                        result.ifPresent(destination -> {
                            Long targetGroupId = null;
                            if (destination.startsWith("Group: ")) {
                                String groupName = destination.substring(7);
                                // Tìm ID của nhóm đã chọn
                                targetGroupId = userGroups.stream()
                                        .filter(g -> g.getGroupName().equals(groupName))
                                        .findFirst()
                                        .map(Group::getGroupId)
                                        .orElse(null);
                            }
                            
                            // 4. Gọi hàm uploadFileThread với đúng targetGroupId
                            // (Hàm uploadFileThread và continueUploadProcess của bạn đã đúng, không cần sửa)
                            uploadFileThread(selectedFile, targetGroupId);
                        });
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Error", "Could not fetch group list: " + e.getMessage()));
                }
            }).start();
        }
    }

    // Đây là logic "Lock-on-demand" (Khóa khi cần) MỚI của bạn
    private void uploadFileThread(java.io.File fileToUpload, Long groupId) {
        statusLabel.setText("Preparing to upload " + fileToUpload.getName() + "...");

        // --- BƯỚC 1: XÁC ĐỊNH isUpdate VÀ fileIdToLock TRƯỚC KHI TẠO LUỒNG ---
        final boolean[] isUpdateArr = {false};
        final long[] fileIdToLockArr = {-1};
        
        new Thread(() -> {
            try {
                // Xác định danh sách nguồn (cá nhân hay nhóm)
                List<File> sourceList;
                if (groupId != null) {
                    sourceList = socketClient.listGroupFiles(groupId);
                } else {
                    sourceList = socketClient.listFiles();
                }

                // Tìm file trùng tên
                for (File f : sourceList) {
                    if (f.getFileName().equals(fileToUpload.getName())) {
                        isUpdateArr[0] = true;
                        fileIdToLockArr[0] = f.getId();
                        break;
                    }
                }
                
                // --- BƯỚC 2: SAU KHI ĐÃ CÓ KẾT QUẢ, GỌI MỘT HÀM XỬ LÝ TIẾP THEO ---
                Platform.runLater(() -> {
                    continueUploadProcess(fileToUpload, groupId, isUpdateArr[0], fileIdToLockArr[0]);
                });

            } catch (IOException e) {
                Platform.runLater(() -> showAlert(AlertType.ERROR, "Error", "Could not get file info: " + e.getMessage()));
            }
        }).start();
    }

    private void continueUploadProcess(java.io.File fileToUpload, Long groupId, boolean isUpdate, long fileIdToLock) {
        // Hỏi ghi chú
        TextInputDialog notesDialog = new TextInputDialog(isUpdate ? "Updated content" : "First version");
        notesDialog.setTitle("Version Notes");
        notesDialog.setHeaderText("Enter notes for '" + fileToUpload.getName() + "'");
        notesDialog.setContentText("Notes:");
        Optional<String> notesResult = notesDialog.showAndWait();

        if (notesResult.isEmpty()) {
            statusLabel.setText("Upload canceled.");
            return;
        }
        String notes = notesResult.get();

        // BẮT ĐẦU LUỒNG UPLOAD CUỐI CÙNG
        statusLabel.setText("Uploading " + fileToUpload.getName() + "...");
        new Thread(() -> {
            try {
                // Nếu là cập nhật, thử khóa file NGAY BÂY GIỜ
                if (isUpdate) {
                    String lockResponse = socketClient.lockFile(fileIdToLock);
                    if (lockResponse == null || !lockResponse.startsWith("200 OK")) {
                        final String errorMsg = lockResponse != null ? lockResponse : "Failed to lock file.";
                        Platform.runLater(() -> showAlert(AlertType.ERROR, "Lock Failed", errorMsg + "\nUpload canceled."));
                        return; // Dừng lại nếu không khóa được
                    }
                }

                // Thực hiện upload (đã bỏ baseVersion)
                String uploadResponse = socketClient.uploadFile(fileToUpload, groupId, notes);
                
                // Server sẽ tự động mở khóa sau khi upload thành công
                
                Platform.runLater(() -> {
                    showAlert(AlertType.INFORMATION, "Upload Status", uploadResponse);
                    if (uploadResponse != null && uploadResponse.startsWith("202 OK")) {
                        refreshCurrentView();
                    }
                });

            } catch (IOException e) {
                Platform.runLater(() -> showAlert(AlertType.ERROR, "Upload Error", "An error occurred: " + e.getMessage()));
            }
        }).start();
    }

    /**
     * Nút này giờ là "Lock & Edit".
     */
    @FXML
    protected void onLockAndEditClick() {
        Object selectedItem = mainTableView.getSelectionModel().getSelectedItem();
        if (selectedItem == null || !(selectedItem instanceof File)) {
            showAlert(AlertType.WARNING, "Selection Error", "Please select a file to lock and edit.");
            return;
        }

        File selectedFile = (File) selectedItem;

        if (selectedFile.isLocked()) {
             String currentUser = SocketClientSingleton.getInstance().getCurrentUser().getUsername();
             if (selectedFile.getLockedByUsername() != null && !selectedFile.getLockedByUsername().equals(currentUser)) {
                 showAlert(AlertType.ERROR, "File Locked", "This file is already locked by " + selectedFile.getLockedByUsername() + ".");
                 return;
             }
        }

        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Save Location for Editing");
        java.io.File saveDirectory = directoryChooser.showDialog(mainTableView.getScene().getWindow());

        if (saveDirectory != null) {
            statusLabel.setText("Locking and downloading " + selectedFile.getFileName() + "...");
            new Thread(() -> {
                try {
                    String response = socketClient.lockAndDownload(selectedFile.getId(), saveDirectory.getAbsolutePath());
                    
                    Platform.runLater(() -> {
                        showAlert(AlertType.INFORMATION, "Lock and Download Status", response);
                        if(response.startsWith("200 OK")) {
                            refreshCurrentView();
                        }
                    });
                } catch (IOException e) {
                    Platform.runLater(() -> showAlert(AlertType.ERROR, "Lock Error", "Lock and download failed: " + e.getMessage()));
                }
            }).start();
        }
    }

    @FXML
    protected void onViewButtonClick() {
        Object selectedItem = mainTableView.getSelectionModel().getSelectedItem();
        if (selectedItem == null || !(selectedItem instanceof File)) {
            showAlert(AlertType.WARNING, "Selection Error", "Please select a file to view/download.");
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
                    // Gọi hàm downloadFile (KHÔNG PHẢI lockAndDownload)
                    String response = socketClient.downloadFile(selectedFile.getId(), saveDirectory.getAbsolutePath());
                    
                    Platform.runLater(() -> {
                        showAlert(AlertType.INFORMATION, "Download Status", response);
                        statusLabel.setText("Download finished.");
                    });
                } catch (IOException e) {
                    Platform.runLater(() -> showAlert(AlertType.ERROR, "Download Error", "Download failed: " + e.getMessage()));
                }
            }).start();
        }
    }
    
    /**
     * HÀM MỚI: Xử lý việc mở khóa file.
     */
    @FXML
    protected void onUnlockButtonClick() {
        Object selectedItem = mainTableView.getSelectionModel().getSelectedItem();
        if (selectedItem == null || !(selectedItem instanceof File)) {
            showAlert(AlertType.WARNING, "Selection Error", "Please select a file to unlock.");
            return;
        }
        File selectedFile = (File) selectedItem;

        if (!selectedFile.isLocked()) {
            showAlert(AlertType.INFORMATION, "File Not Locked", "This file is not currently locked.");
            return;
        }

        String currentUsername = SocketClientSingleton.getInstance().getCurrentUser().getUsername();
        if (selectedFile.getLockedByUsername() == null || !selectedFile.getLockedByUsername().equals(currentUsername)) {
            showAlert(AlertType.ERROR, "Permission Denied", "You cannot unlock a file locked by another user (" + selectedFile.getLockedByUsername() + ").");
            return;
        }

        if (confirmAction("Confirm Unlock", "Are you sure you want to cancel editing and unlock '" + selectedFile.getFileName() + "'?")) {
            statusLabel.setText("Unlocking file...");
            
            new Thread(() -> {
                try {
                    String response = socketClient.unlockFile(selectedFile.getId());
                    
                    Platform.runLater(() -> {
                        showAlert(AlertType.INFORMATION, "Unlock Status", response);
                        if (response != null && response.startsWith("200 OK")) {
                            refreshCurrentView();
                        }
                    });
                } catch (IOException e) {
                    Platform.runLater(() -> showAlert(AlertType.ERROR, "Unlock Error", "Action failed: " + e.getMessage()));
                }
            }).start();
        }
    }

    /**
     * HÀM ĐÃ SỬA: Gọi đúng hàm trong SocketClient
     */
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
                         // SỬA LỖI: Gọi hàm delete
                         response = socketClient.delete("file", finalItemId);
                     } else if (finalSelectedItem instanceof Group) {
                         // SỬA LỖI: Gọi hàm delete
                         response = socketClient.delete("group", finalItemId);
                     }
                     final String finalResponse = response;
                     Platform.runLater(() -> {
                         showAlert(AlertType.INFORMATION, "Delete Status", finalResponse);
                         refreshCurrentView();
                     });
                 } catch (IOException e) {
                     Platform.runLater(() -> showAlert(AlertType.ERROR, "Delete Error", "Action failed: " + e.getMessage()));
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
                    } catch (IOException e) { 
                        Platform.runLater(() -> showAlert(AlertType.ERROR, "Error", "Action failed: " + e.getMessage()));
                    }
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
            
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setDialogPane(loader.load());
            dialog.setTitle("Share File");

            ShareDialogController controller = loader.getController();
            controller.setFileInfo(selectedFile);

            Optional<ButtonType> result = dialog.showAndWait();

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
        Dialog<String[]> dialog = new Dialog<>();
        dialog.setTitle("Access Public Link");
        dialog.setHeaderText("Enter the token you received to access the file.");

        ButtonType accessButtonType = new ButtonType("Access File", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(accessButtonType, ButtonType.CANCEL);

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

        Platform.runLater(tokenField::requestFocus);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == accessButtonType) {
                return new String[]{tokenField.getText(), passwordField.getText()};
            }
            return null;
        });

        Optional<String[]> result = dialog.showAndWait();

        result.ifPresent(credentials -> {
            String token = credentials[0].trim();
            String password = credentials[1];

            if (!token.isEmpty()) {
                DirectoryChooser directoryChooser = new DirectoryChooser();
                directoryChooser.setTitle("Select Save Location for Linked File");
                java.io.File saveDirectory = directoryChooser.showDialog(mainTableView.getScene().getWindow());

                if (saveDirectory != null) {
                    downloadFileByTokenThread(token, password, saveDirectory.getAbsolutePath());
                }
            }
        });
    }
    
    @FXML
    protected void onLogoutAction() {
        if (confirmAction("Confirm Logout", "Are you sure you want to log out and return to the login screen?")) {
            
            new Thread(() -> {
                SocketClientSingleton.getInstance().close();
                Platform.runLater(() -> {
                    try {
                        SceneManager.loadScene("login-view.fxml", "File Storage - Login");
                    } catch (IOException e) {
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
                        if (response.startsWith("200 OK")) refreshCurrentView();
                    });
                } catch (IOException e) { 
                    Platform.runLater(() -> showAlert(AlertType.ERROR, "Error", "Action failed: " + e.getMessage()));
                }
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
                } catch (IOException e) { 
                    Platform.runLater(() -> showAlert(AlertType.ERROR, "Error", "Action failed: " + e.getMessage()));
                }
            }).start();
        }
    }

    @FXML
    protected void onHistoryButtonClick() {
        Object selectedItem = mainTableView.getSelectionModel().getSelectedItem();
        if (selectedItem == null || !(selectedItem instanceof File)) {
            showAlert(AlertType.WARNING, "Selection Error", "Please select a file to view its history.");
            return;
        }
        File selectedFile = (File) selectedItem;

        try {
            FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("version-history-view.fxml"));
            DialogPane dialogPane = loader.load();
            
            VersionHistoryController controller = loader.getController();
            controller.setFile(selectedFile);
            
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setDialogPane(dialogPane);
            dialog.setTitle("Version History");
            
            ButtonType restoreButtonType = new ButtonType("Restore Selected Version", ButtonBar.ButtonData.OK_DONE);
            dialogPane.getButtonTypes().add(restoreButtonType);
            
            Button restoreButton = (Button) dialogPane.lookupButton(restoreButtonType);
            restoreButton.setOnAction(event -> controller.onRestoreClick());
            
            dialogPane.getButtonTypes().add(ButtonType.CLOSE);
            
            dialog.showAndWait();
            
            refreshCurrentView();

        } catch (IOException e) {
            e.printStackTrace();
            showAlert(AlertType.ERROR, "Error", "Could not open version history window.");
        }
    }

    @FXML
    protected void onSearchAction() {
        String keyword = searchField.getText().trim();
        if (keyword.isEmpty()) {
            refreshCurrentView();
            return;
        }

        statusLabel.setText("Searching for '" + keyword + "'...");
        tableData.clear();
        
        final CurrentView viewForSearch = currentView;
        final long groupIdForSearch = currentGroupId;

        new Thread(() -> {
            try {
                List<File> searchResults;
                
                switch (viewForSearch) {
                    case SHARED_FILES:
                        searchResults = socketClient.searchSharedFiles(keyword);
                        break;
                    case GROUP_FILES:
                        searchResults = socketClient.searchGroupFiles(groupIdForSearch, keyword);
                        break;
                    case MY_FILES:
                    default:
                        searchResults = socketClient.searchMyFiles(keyword);
                        break;
                }
                
                Platform.runLater(() -> {
                    setupFileViewColumns();
                    tableData.setAll(searchResults);
                    statusLabel.setText(searchResults.size() + " result(s) found.");
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert(AlertType.ERROR, "Search Error", "Failed to perform search."));
            }
        }).start();
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
                String response = socketClient.downloadFileByToken(token, password, savePath);
                Platform.runLater(() -> {
                    showAlert(AlertType.INFORMATION, "Download Status", response);
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

    /**
     * HÀM ĐÃ SỬA: Thêm cột "Version" và "Status" (Lock)
     */
    private void setupFileViewColumns() {
        mainTableView.getColumns().clear();
        
        TableColumn<Object, String> lockCol = new TableColumn<>("Status");
        lockCol.setCellValueFactory(cellData -> {
            if (cellData.getValue() instanceof File) {
                File file = (File) cellData.getValue();
                if (file.isLocked()) {
                    String currentUsername = SocketClientSingleton.getInstance().getCurrentUser().getUsername();
                    if (file.getLockedByUsername() != null && file.getLockedByUsername().equals(currentUsername)) {
                        return new SimpleStringProperty("LOCKED (By You)");
                    } else {
                        return new SimpleStringProperty("LOCKED by " + file.getLockedByUsername());
                    }
                }
            }
            return new SimpleStringProperty("");
        });
        lockCol.setPrefWidth(150);


        TableColumn<Object, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(cellData -> {
            if (cellData.getValue() instanceof File) {
                return new SimpleStringProperty(((File)cellData.getValue()).getFileName());
            }
            return new SimpleStringProperty("");
        });
        nameCol.setPrefWidth(250);
        
        TableColumn<Object, Long> sizeCol = new TableColumn<>("Size (bytes)");
        sizeCol.setCellValueFactory(new PropertyValueFactory<>("fileSize"));
        sizeCol.setPrefWidth(100);

        TableColumn<Object, String> ownerCol = new TableColumn<>("Owner");
        ownerCol.setCellValueFactory(cellData -> {
            if (cellData.getValue() instanceof File) {
                return new SimpleStringProperty(((File)cellData.getValue()).getOwnerName());
            }
            return new SimpleStringProperty("");
        });
        ownerCol.setPrefWidth(120);
        
        TableColumn<Object, String> dateCol = new TableColumn<>("Last Modified");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        dateCol.setCellValueFactory(cellData -> {
            if (cellData.getValue() instanceof File) {
                LocalDateTime date = ((File)cellData.getValue()).getUploadDate();
                return new SimpleStringProperty(date != null ? date.format(formatter) : "N/A");
            }
            return new SimpleStringProperty("");
        });
        dateCol.setPrefWidth(130);
        
        TableColumn<Object, Integer> versionCol = new TableColumn<>("Ver");
        versionCol.setCellValueFactory(new PropertyValueFactory<>("currentVersion"));
        versionCol.setPrefWidth(50);


        mainTableView.getColumns().addAll(lockCol, nameCol, sizeCol, ownerCol, dateCol, versionCol);
    }

    private void setupGroupViewColumns() {
        mainTableView.getColumns().clear();

        TableColumn<Object, Long> idCol = new TableColumn<>("Group ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("groupId"));
        
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
        nameCol.setPrefWidth(250);
        
        TableColumn<Object, String> roleCol = new TableColumn<>("Role");
        roleCol.setCellValueFactory(new PropertyValueFactory<>("roleInGroup")); 
        roleCol.setPrefWidth(150);
        
        mainTableView.getColumns().addAll(idCol, nameCol, roleCol);
    }
}