package com.dut.filestorage.client;

import java.io.IOException;
import java.net.URL; 

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image; 
import javafx.scene.image.ImageView; 

public class HomepageController {

    // <<< PHẦN LAZY LOADING: Khai báo tất cả các ImageView có thể có >>>
    
    @FXML private ScrollPane scrollPane;
    
    // Các ảnh từ features-view.fxml
    @FXML private ImageView secureStorageImg;
    @FXML private ImageView teamCollabImg;
    @FXML private ImageView publicLinksImg;
    @FXML private ImageView versionControlImg;

    // Các ảnh từ about-us-view.fxml
    @FXML private ImageView ourStoryImg;
    @FXML private ImageView huyImg;
    @FXML private ImageView duongImg;

    // <<< MỚI: Các ảnh từ use-cases-view.fxml
    @FXML private ImageView studentImg;
    @FXML private ImageView workerImg;
    @FXML private ImageView memoriesImg;


    // Cờ (Flag) cho features-view.fxml
    private boolean isSecureStorageLoaded = false;
    private boolean isTeamCollabLoaded = false;
    private boolean isPublicLinksLoaded = false;
    private boolean isVersionControlLoaded = false;

    // Cờ (Flag) cho about-us-view.fxml
    private boolean isOurStoryLoaded = false;
    private boolean isHuyLoaded = false;
    private boolean isDuongLoaded = false;

    // <<< MỚI: Cờ (Flag) cho use-cases-view.fxml
    private boolean isStudentLoaded = false;
    private boolean isWorkerLoaded = false;
    private boolean isMemoriesLoaded = false;


    /**
     * Hàm này được FXML tự động gọi sau khi tải xong.
     */
    @FXML
    public void initialize() {
        // Nếu file FXML không có scrollPane (ví dụ file login-view), thì không làm gì cả.
        if (scrollPane == null) {
            return; 
        }

        // Thêm một "bộ lắng nghe" vào thanh cuộn
        // Nó sẽ được gọi MỖI KHI bạn cuộn
        scrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> {
            checkAndLoadImages();
        });

        // Chạy kiểm tra 1 lần lúc khởi tạo (để tải bất kỳ ảnh nào đã nằm trong tầm nhìn)
        checkAndLoadImages();
    }
    
    /**
     * Kiểm tra vị trí cuộn và tải ảnh nếu cần
     * <<< NÂNG CẤP: Hàm này giờ xử lý tất cả các trang >>>
     */
    private void checkAndLoadImages() {
        if (scrollPane == null) return;

        // Tự động lấy chiều cao của nội dung và khung nhìn
        double totalHeight = scrollPane.getContent().getBoundsInParent().getHeight();
        double viewportHeight = scrollPane.getHeight();
        
        // Tính toán vị trí đáy của khung nhìn hiện tại
        double currentBottom = (scrollPane.getVvalue() * (totalHeight - viewportHeight)) + viewportHeight;
        double buffer = 100.0; // Tải ảnh trước khi nó xuất hiện 100px

        // --- Kiểm tra và tải ảnh cho features-view.fxml ---
        // (Kiểm tra null để đảm bảo code không crash khi ở file FXML khác)
        
        if (secureStorageImg != null && !isSecureStorageLoaded && currentBottom > (secureStorageImg.getLayoutY() - buffer)) {
            loadImage("images/secure_storage.jpg", secureStorageImg);
            isSecureStorageLoaded = true;
        }
        if (teamCollabImg != null && !isTeamCollabLoaded && currentBottom > (teamCollabImg.getLayoutY() - buffer)) {
            loadImage("images/team_collaboration.png", teamCollabImg);
            isTeamCollabLoaded = true;
        }
        if (publicLinksImg != null && !isPublicLinksLoaded && currentBottom > (publicLinksImg.getLayoutY() - buffer)) {
            loadImage("images/public_links.jpg", publicLinksImg);
            isPublicLinksLoaded = true;
        }
        if (versionControlImg != null && !isVersionControlLoaded && currentBottom > (versionControlImg.getLayoutY() - buffer)) {
            loadImage("images/version_controller.jpg", versionControlImg);
            isVersionControlLoaded = true;
        }

        // --- Kiểm tra và tải ảnh cho about-us-view.fxml ---
        if (ourStoryImg != null && !isOurStoryLoaded && currentBottom > (getAbsoluteY(ourStoryImg) - buffer)) {
            loadImage("images/our_story.png", ourStoryImg);
            isOurStoryLoaded = true;
        }
        if (huyImg != null && !isHuyLoaded && currentBottom > (getAbsoluteY(huyImg) - buffer)) {
            loadImage("images/huy.png", huyImg);
            isHuyLoaded = true;
        }
        if (duongImg != null && !isDuongLoaded && currentBottom > (getAbsoluteY(duongImg) - buffer)) {
            loadImage("images/duong.png", duongImg);
            isDuongLoaded = true;
        }

        // --- <<< MỚI: Kiểm tra và tải ảnh cho use-cases-view.fxml ---
        if (studentImg != null && !isStudentLoaded && currentBottom > (studentImg.getLayoutY() - buffer)) {
            loadImage("images/student.jpg", studentImg);
            isStudentLoaded = true;
        }
        if (workerImg != null && !isWorkerLoaded && currentBottom > (workerImg.getLayoutY() - buffer)) {
            loadImage("images/worker.jpg", workerImg);
            isWorkerLoaded = true;
        }
        if (memoriesImg != null && !isMemoriesLoaded && currentBottom > (memoriesImg.getLayoutY() - buffer)) {
            loadImage("images/memories.jpg", memoriesImg);
            isMemoriesLoaded = true;
        }
    }

    /**
     * Hàm trợ giúp lấy vị trí Y tuyệt đối của ảnh (so với AnchorPane)
     * Dùng cho các ảnh nằm trong 1 Pane con (như trang about-us)
     */
    private double getAbsoluteY(Node node) {
        // Lấy vị trí Y của Pane cha + vị trí Y của ảnh bên trong Pane
        return node.getParent().getLayoutY() + node.getLayoutY();
    }


    /**
     * Hàm trợ giúp tải ảnh ở chế độ nền (background)
     */
    private void loadImage(String imageUrl, ImageView imageView) {
        if (imageView == null) {
             System.err.println("ImageView is null for image: " + imageUrl);
             return;
        }
        try {
            URL url = getClass().getResource(imageUrl); // Tìm ảnh trong resources
            if (url != null) {
                Image img = new Image(url.toExternalForm(), true); // true = tải nền
                imageView.setImage(img);
            } else {
                System.err.println("Không tìm thấy ảnh: " + imageUrl);
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi tải ảnh: " + imageUrl);
            e.printStackTrace();
        }
    }

    // <<< KẾT THÚC PHẦN MỚI ---------------------------------------


    // ▼▼▼ CÁC HÀM CŨ CỦA BẠN (Giữ nguyên) ▼▼▼

    @FXML
    protected void onLoginClick() {
        try {
            SceneManager.loadScene("login-view.fxml", "File Storage - Login");
        } catch (IOException e) {
            System.err.println("Failed to load the login view.");
            e.printStackTrace();
        }
    }

    @FXML
    protected void onSignUpClick() {
        try {
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
        }
    }

    @FXML
    protected void onUseCasesClick() {
        try {
            SceneManager.loadScene("use-cases-view.fxml", "File Storage - Use Cases");
        } catch (IOException e) {
            System.err.println("Failed to load the use cases view.");
            e.printStackTrace();
        }
    }

    @FXML
    protected void onAboutUsClick() {
        try {
            SceneManager.loadScene("about-us-view.fxml", "File Storage - About Us");
        } catch (IOException e) {
            System.err.println("Failed to load the about us view.");
            e.printStackTrace();
        }
    }

    @FXML
    protected void onLogoClick() {
        try {
            SceneManager.loadScene("homepage-view.fxml", "File Storage - Welcome");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}