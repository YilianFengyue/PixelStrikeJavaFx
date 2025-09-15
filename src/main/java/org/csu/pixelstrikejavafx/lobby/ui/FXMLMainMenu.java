package org.csu.pixelstrikejavafx.lobby.ui;

import com.almasb.fxgl.app.scene.FXGLMenu;
import com.almasb.fxgl.app.scene.MenuType;
import com.almasb.fxgl.dsl.FXGL;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.stage.WindowEvent;
import org.csu.pixelstrikejavafx.game.core.MusicManager;
import org.csu.pixelstrikejavafx.lobby.network.ApiClient;
import org.csu.pixelstrikejavafx.lobby.network.NetworkManager;
import org.csu.pixelstrikejavafx.core.GlobalState;
import org.csu.pixelstrikejavafx.lobby.ui.dialog.DialogManager;

public class FXMLMainMenu extends FXGLMenu {

    public FXMLMainMenu() {
        super(MenuType.MAIN_MENU);

        // --- 诊断代码: 为FXGL的根节点设置一个红色背景 ---
        // 如果切换全屏后，整个屏幕都不是红色了，说明问题出在FXGL层面。
        getContentRoot().setStyle("-fx-background-color: red;");

        // 1. 创建我们的根容器，用于居中和缩放。
        StackPane rootPane = new StackPane();
        rootPane.setAlignment(Pos.CENTER);
        // --- 诊断代码: 为我们的根容器设置一个半透明的绿色背景 ---
        // 这个绿色区域理论上应该永远填充整个红色区域。
        rootPane.setStyle("-fx-background-color: rgba(0, 255, 0, 0.5);"); // 半透明绿色

        // 2. 创建内容面板，固定设计尺寸
        StackPane contentPane = new StackPane();
        contentPane.setPrefSize(1920, 1080);
        contentPane.setMinSize(1920, 1080);
        contentPane.setMaxSize(1920, 1080);
        // --- 诊断代码: 为您的UI内容面板设置一个半透明的蓝色背景 ---
        // 这个蓝色区域应该永远在绿色区域的正中央。
        contentPane.setStyle("-fx-background-color: rgba(0, 0, 255, 0.5);"); // 半透明蓝色

        // 将内容面板放入根容器
        rootPane.getChildren().add(contentPane);

        // 3. 设置缩放绑定 (逻辑保持不变)
        var scaleXBinding = rootPane.widthProperty().divide(1920.0);
        var scaleYBinding = rootPane.heightProperty().divide(1080.0);
        var scaleBinding = Bindings.min(scaleXBinding, scaleYBinding);
        contentPane.scaleXProperty().bind(scaleBinding);
        contentPane.scaleYProperty().bind(scaleBinding);

        // 4. 加载UI (这会将您的登录界面等加载到蓝色的 contentPane 中)
        UIManager.setRoot(contentPane);
        DialogManager.setRoot(contentPane);
        UIManager.load("login-view.fxml");

        // 5. 将我们设置好的“根容器”添加到FXGL场景中
        getContentRoot().getChildren().add(rootPane);

        // 6. 添加必要的事件处理器
        getContentRoot().setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                // 【修改】不再直接退出，而是弹出确认对话框
                DialogManager.showConfirmation("确认退出", "您确定要退出游戏吗？", () -> {
                    // 当用户点击“确认”时，执行安全退出逻辑
                    var stage = FXGL.getPrimaryStage();
                    if (stage != null) {
                        stage.fireEvent(
                                new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST)
                        );
                    }
                });
            }
        });

        addExitHandler();
        MusicManager.getInstance().playMenuMusic();
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