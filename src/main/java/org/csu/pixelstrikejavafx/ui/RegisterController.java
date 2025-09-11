package org.csu.pixelstrikejavafx.ui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.csu.pixelstrikejavafx.http.ApiClient;

import java.util.Objects;

public class RegisterController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField emailField;
    @FXML private TextField nicknameField;
    @FXML private Label statusLabel;
    @FXML private ImageView backgroundImageView;

    private final ApiClient apiClient = new ApiClient();

    @FXML
    private void handleRegister() {
        String username = usernameField.getText();
        String password = passwordField.getText();
        String email = emailField.getText();
        String nickname = nicknameField.getText();

        if (username.isEmpty() || password.isEmpty() || email.isEmpty() || nickname.isEmpty()) {
            statusLabel.setText("所有字段都不能为空");
            return;
        }

        statusLabel.setText("正在注册...");

        // 在后台线程执行网络请求
        new Thread(() -> {
            try {
                apiClient.register(username, password, email, nickname);
                // 注册成功
                Platform.runLater(() -> {
                    statusLabel.setText("注册成功！请返回登录。");
                    // 可以在这里自动跳转回登录页面
                    // handleBackToLogin();
                });
            } catch (Exception e) {
                // 注册失败
                Platform.runLater(() -> {
                    statusLabel.setText("注册失败: " + e.getMessage());
                });
                e.printStackTrace();
            }
        }).start();
    }

    @FXML
    private void handleBackToLogin() {
        // 使用 UIManager 返回登录界面
        UIManager.load("login-view.fxml");
    }

    @FXML
    public void initialize() {
        try {
            Image bg = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/assets/textures/background.png")));
            backgroundImageView.setImage(bg);
        } catch (Exception e) {
            System.err.println("注册页背景图加载失败: " + e.getMessage());
        }
    }
}