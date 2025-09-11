package org.csu.pixelstrikejavafx.lobby.ui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.csu.pixelstrikejavafx.lobby.network.ApiClient;
import org.csu.pixelstrikejavafx.lobby.network.NetworkManager;
import org.csu.pixelstrikejavafx.core.GlobalState;

import java.util.Objects;

/**
 * LoginController 负责处理 login-view.fxml 的所有用户交互。
 */
public class LoginController {

    // 通过 @FXML 注解，将 FXML 文件中定义的控件与这里的变量关联起来
    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label statusLabel;

    @FXML
    private Button loginButton;

    @FXML
    private ImageView backgroundImageView;

    // 创建 ApiClient 实例，用于发起 HTTP 请求
    private final ApiClient apiClient = new ApiClient();

    // 获取 NetworkManager 单例，用于管理 WebSocket 连接
    private final NetworkManager networkManager = NetworkManager.getInstance();

    @FXML
    private void handleGoToRegister() {
        UIManager.load("register-view.fxml");
    }


    @FXML
    public void initialize() {
        // 加载背景图
        try {
            // 这行代码就是加载背景图的关键
            Image bg = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/assets/textures/background.png")));

            backgroundImageView.setImage(bg);
        } catch (Exception e) {
            System.err.println("登录页背景图加载失败: " + e.getMessage());
        }
    }

    @FXML
    private void handleLogin() {
        String username = usernameField.getText();
        String password = passwordField.getText();

        // 校验输入是否为空
        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("用户名或密码不能为空");
            return;
        }

        // 给用户一个即时反馈
        statusLabel.setText("正在登录...");
        loginButton.setDisable(true);

        new Thread(() -> {
            try {
                // 1. 只需调用一次 login 方法
                // 所有信息 (token, userId, nickname) 都会被自动存入 GlobalState
                apiClient.login(username, password);

                // 2. 建立 WebSocket 连接
                networkManager.connect();

                // 3. 直接切换到大厅
                Platform.runLater(() -> {
                    System.out.println(String.format("登录成功! 用户: %s (ID: %d)", GlobalState.nickname, GlobalState.userId));
                    UIManager.load("lobby-view.fxml");
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("登录失败: " + e.getMessage());
                    loginButton.setDisable(false); // 登录失败，恢复按钮
                });
                e.printStackTrace();
            }
        }).start();
    }


}