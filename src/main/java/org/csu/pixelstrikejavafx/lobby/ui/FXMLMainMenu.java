package org.csu.pixelstrikejavafx.lobby.ui;

import com.almasb.fxgl.app.scene.FXGLMenu;
import com.almasb.fxgl.app.scene.MenuType;
import com.almasb.fxgl.dsl.FXGL;
import javafx.application.Platform;
import javafx.scene.layout.StackPane;
import org.csu.pixelstrikejavafx.lobby.network.ApiClient;
import org.csu.pixelstrikejavafx.lobby.network.NetworkManager;
import org.csu.pixelstrikejavafx.core.GlobalState;
import org.csu.pixelstrikejavafx.lobby.ui.dialog.DialogManager;

public class FXMLMainMenu extends FXGLMenu {

    public FXMLMainMenu() {
        super(MenuType.MAIN_MENU);

        // 1. 创建一个 StackPane 作为所有 UI 的根容器
        StackPane uiRoot = new StackPane();
        uiRoot.setPrefSize(FXGL.getAppWidth(), FXGL.getAppHeight());
        uiRoot.setStyle("-fx-background-color: #282c34;"); // 可以设置一个默认背景色

        // 2. 将这个根容器传递给 UIManager 进行管理 (关键一步！)
        // 必须在调用任何 UIManager.load() 之前执行
        UIManager.setRoot(uiRoot);
        DialogManager.setRoot(uiRoot);

        // 3. 加载初始界面 (例如登录页)
        // 2. 【核心】智能调度逻辑
        /*if (GlobalState.shouldShowLastMatchResults && GlobalState.lastMatchResults != null) {
            // 场景一：信使旗帜升起，并且战绩数据存在 -> 显示战绩！
            System.out.println("检测到游戏结束状态，正在跳转到战绩页面...");
            GlobalState.shouldShowLastMatchResults = false; // **重要**：检查后立即放下旗帜，防止下次误判
            UIManager.load("results-view.fxml"); // 加载您已写好的战绩 FXML

        } else if (GlobalState.authToken != null) {
            // 场景二：无战绩，但用户已登录 -> 显示大厅
            System.out.println("用户已登录，正在加载大厅...");
            UIManager.load("lobby-view.fxml");

        } else {
            // 场景三：未登录 -> 显示登录页
            System.out.println("用户未登录，正在加载登录页面...");
            UIManager.load("login-view.fxml");
        }*/

        // 4. 将我们的 UI 根容器添加到 FXGL 菜单场景中
        getContentRoot().getChildren().add(uiRoot);
        // 4) 自动路由：下一帧执行一次 + 每次该菜单被显示时再次执行
        setupAutoRoute();
        addExitHandler();
    }

    /**
     * 安装自动路由：构造后下一帧触发一次 + 每次菜单重新显示时触发。
     */
    private void setupAutoRoute() {
        // 下一帧执行（确保 FXGL/Scene 布局已完成）
        Platform.runLater(this::routeToProperView);

        // 监听菜单可见性（FXGL 切换菜单时会切换可见/激活状态）
        getContentRoot().visibleProperty().addListener((obs, wasVisible, nowVisible) -> {
            if (nowVisible) {
                Platform.runLater(this::routeToProperView);
            }
        });
    }

    /**
     * 根据全局状态决定加载哪个界面。
     */
    private void routeToProperView() {
        try {
            if (GlobalState.lastMatchResults != null) {
                UIManager.load("results-view.fxml");
            } else if (GlobalState.authToken != null) {
                UIManager.load("lobby-view.fxml");
            } else {
                UIManager.load("login-view.fxml");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            FXGL.getDialogService().showMessageBox("加载主菜单界面失败: " + ex.getMessage());
        }
    }
    /**
     * 为游戏窗口添加关闭请求处理器。
     * 当用户点击窗口的 "X" 按钮时，这个处理器会被调用。
     */
    private void addExitHandler() {
        // 获取主舞台 (Stage) 并设置关闭请求事件
        FXGL.getPrimaryStage().setOnCloseRequest(event -> {
            // 阻止窗口立即关闭，以便我们执行异步的网络操作
            event.consume();
            // 执行登出逻辑
            performLogout(() -> FXGL.getGameController().exit());
        });
    }

    /**
     * 执行优雅退出操作，并在完成后调用回调函数。(最终健壮版)
     * @param onLogoutFinished 操作完成后要执行的动作。
     */
    private void performLogout(Runnable onLogoutFinished) {
        if (GlobalState.authToken != null) {
            System.out.println("窗口关闭，执行优雅退出...");
            new Thread(() -> {
                try {
                    ApiClient apiClient = new ApiClient();

                    // 1. 检查并离开房间
                    if (GlobalState.currentRoomInfo != null) {
                        System.out.println("检测到玩家在房间内，正在执行离开房间操作...");
                        apiClient.leaveRoom();
                        System.out.println("成功离开房间。");
                    }

                    // 2. 执行登出
                    System.out.println("正在执行登出操作...");
                    apiClient.logout();
                    NetworkManager.getInstance().disconnect();
                    System.out.println("优雅退出成功。");
                    Platform.runLater(onLogoutFinished);

                } catch (Exception e) {
                    // 如果中途发生任何错误
                    System.err.println("优雅退出时发生严重错误: " + e.getMessage());

                    // 在UI上弹出一个对话框，告诉用户发生了什么
                    Platform.runLater(() -> {
                        FXGL.getDialogService().showConfirmationBox("无法安全退出，可能是网络问题。\n是否强制退出？", (yes) -> {
                            if (yes) {
                                // 如果用户选择“是”，则强行关闭程序
                                onLogoutFinished.run();
                            }
                        });
                    });
                }
                // 【删除】这里的 finally 块，因为它会导致无论成功与否都退出
            }).start();
        } else {
            // 如果未登录，直接退出
            Platform.runLater(onLogoutFinished);
        }
    }
}