package org.csu.pixelstrikejavafx.ui;

import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.csu.pixelstrikejavafx.http.ApiClient;
import org.csu.pixelstrikejavafx.network.NetworkManager;
import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.csu.pixelstrikejavafx.state.GlobalState;
import org.csu.pixelstrikejavafx.ui.UIManager;

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

    // 创建 ApiClient 实例，用于发起 HTTP 请求
    private final ApiClient apiClient = new ApiClient();

    // 获取 NetworkManager 单例，用于管理 WebSocket 连接
    private final NetworkManager networkManager = NetworkManager.getInstance();

    @FXML
    private void handleGoToRegister() {
        UIManager.load("register-view.fxml");
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

        new Thread(() -> {
            try {
                // 1. 调用 API 登录，获取 token
                String token = apiClient.login(username, password);

                // 2. 解析 Token 获取 userId
                DecodedJWT jwt = JWT.decode(token);
                long userId = jwt.getClaim("userId").asLong();
                GlobalState.userId = userId; // 存入全局状态

                // 3. 使用 userId 获取用户详细信息
              /*  JsonObject profileData = apiClient.getUserProfile(userId);
                String nickname = profileData.getAsJsonObject("profile").get("nickname").getAsString();*/
                GlobalState.nickname = username; // 存入全局状态

                // 4. 建立 WebSocket 连接
                networkManager.connect();

                // 5. 所有信息准备就绪，切换到大厅
                Platform.runLater(() -> {
                    System.out.println(String.format("登录成功! 用户: %s (ID: %d)", GlobalState.nickname, userId));
                    UIManager.load("lobby-view.fxml");
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("登录失败: " + e.getMessage());
                });
                e.printStackTrace();
            }
        }).start();
    }
}