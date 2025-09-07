package org.csu.pixelstrikejavafx.ui;

import com.almasb.fxgl.dsl.FXGL;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import org.csu.pixelstrikejavafx.events.FriendStatusEvent;
import org.csu.pixelstrikejavafx.events.MatchSuccessEvent;
import org.csu.pixelstrikejavafx.http.ApiClient;
import org.csu.pixelstrikejavafx.network.NetworkManager;
import org.csu.pixelstrikejavafx.state.GlobalState;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * LobbyController 负责处理 lobby-view.fxml 的所有用户交互。
 * 例如：显示好友列表、处理匹配按钮点击、监听服务器事件等。
 */
public class LobbyController implements Initializable {

    @FXML
    private ListView<String> friendsListView; // 假设你的 FXML 中有一个 ListView 来显示好友

    @FXML
    private Button startMatchButton;

    @FXML
    private Label matchStatusLabel;

    @FXML
    private Label nicknameLabel;

    private final ApiClient apiClient = new ApiClient();

    /**
     * FXML 界面加载完成时，该方法会自动被调用。
     * 我们在这里注册所有需要的事件监听器。
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("LobbyController initialized. Setting up event handlers.");

        // ==========================================================
        // ============ 这里是修改过的部分，使用新的事件监听方式 ============
        // ==========================================================

        // 1. 监听好友状态更新事件
        FXGL.getEventBus().addEventHandler(FriendStatusEvent.ANY, event -> {
            // 这个回调可能不在JavaFX应用线程，所以更新UI需要用 Platform.runLater
            Platform.runLater(() -> {
                JsonObject data = event.getData();
                String nickname = data.get("nickname").getAsString();
                String status = data.get("status").getAsString();
                System.out.println(String.format("UI received: Friend %s is now %s", nickname, status));

                // 在这里编写更新好友列表UI的逻辑
                // 例如：遍历 friendsListView 找到对应的项并更新其状态文本
            });
        });

        // 2. 监听匹配成功事件
        FXGL.getEventBus().addEventHandler(MatchSuccessEvent.ANY, event -> {
            Platform.runLater(() -> {
                System.out.println("UI received: Match Success!");
                matchStatusLabel.setText("匹配成功！准备进入游戏...");

                // 断开当前的全局 WebSocket
                NetworkManager.getInstance().disconnect();

                // 准备连接到游戏服务器...
                String gameServerUrl = event.getServerAddress();
                System.out.println("Game Server URL: " + gameServerUrl);

                // 在这里，你需要编写切换到游戏场景的逻辑
                // 例如：FXGL.getGameController().startNewGame();
                // 并且在游戏场景的 initGame() 中，使用 gameServerUrl 建立新的游戏内 WebSocket 连接
            });
        });

        // 初始加载好友列表 (可以在这里或用一个刷新按钮来触发)
        loadFriendsList();

        if (GlobalState.nickname != null) {
            nicknameLabel.setText("昵称: " + GlobalState.nickname);
        } else {
            nicknameLabel.setText("昵称: 未知");
        }
    }

    /**
     * 处理点击“开始匹配”按钮的事件
     */
    @FXML
    private void handleStartMatch() {
        matchStatusLabel.setText("正在寻找对局...");
        startMatchButton.setDisable(true); // 防止重复点击

        // 后台线程调用匹配 API
        new Thread(() -> {
            try {
                // 假设你的 ApiClient 有一个 startMatchmaking 方法
                // apiClient.startMatchmaking();
                Platform.runLater(() -> matchStatusLabel.setText("已进入匹配队列，等待服务器通知..."));
            } catch (Exception e) {
                Platform.runLater(() -> {
                    matchStatusLabel.setText("开始匹配失败: " + e.getMessage());
                    startMatchButton.setDisable(false);
                });
            }
        }).start();
    }

    /**
     * 从后端加载好友列表并显示在UI上
     */
    private void loadFriendsList() {
        // ... 在这里可以实现调用 apiClient.getFriends() 并更新 friendsListView 的逻辑 ...
        System.out.println("Loading friends list...");
    }

    @FXML
    private void handleStartGame() {
        System.out.println("开始游戏！正在加载游戏世界...");
        // 调用 FXGL 核心方法来启动游戏
        // 这会触发 PixelGameApp.java 中的 initGame() 方法
        FXGL.getGameController().startNewGame();
    }

    @FXML
    private void handleLogout() {
        System.out.println("用户请求登出...");

        // 因为登出需要进行网络请求，所以必须放在后台线程
        new Thread(() -> {
            try {
                // 1. 先调用后端的登出接口
                apiClient.logout();
                // 2. 后端成功登出后，再执行客户端的清理 (必须在UI线程)
                Platform.runLater(() -> {
                    // 主动断开全局 WebSocket 连接
                    NetworkManager.getInstance().disconnect();
                    // 清除本地存储的用户凭证 (token)
                    GlobalState.authToken = null;
                    // 使用 UIManager 切换回登录界面
                    UIManager.load("login-view.fxml");
                });
            } catch (Exception e) {
                // 如果登出失败，在UI上给出提示
                Platform.runLater(() -> {
                    System.err.println("登出失败: " + e.getMessage());
                    // 你可以在大厅界面添加一个 Label 来显示这个错误
                });
                e.printStackTrace();
            }
        }).start();
    }
}