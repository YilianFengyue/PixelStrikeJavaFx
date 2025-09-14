package org.csu.pixelstrikejavafx.lobby.ui;

import com.almasb.fxgl.dsl.FXGL;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import java.io.IOException;
import java.net.URL;
import java.util.Objects;

/**
 * UI Manager (界面管理器)
 * 一个简单的工具类，用于在主容器 (Pane) 中加载和切换不同的 FXML 界面。
 * 它的所有方法都是静态的，方便在任何地方调用。
 */
public final class UIManager {

    // 私有构造函数，防止被实例化
    private UIManager() {}

    // 静态变量，持有对主 UI 容器的引用
    private static Pane rootContainer;
    private static String messageForNextScreen = null;

    /**
     * 获取主 UI 容器。
     * @return 根布局 Pane。
     */
    public static Pane getRoot() {
        return rootContainer;
    }

    /**
     * 初始化 UIManager，必须在程序启动时由主菜单调用一次。
     * @param rootPane FXMLMainMenu 中创建的那个根布局容器 (例如 StackPane)。
     */
    public static void setRoot(Pane rootPane) {
        rootContainer = rootPane;
    }

    public static void showMessageOnNextScreen(String message) {
        messageForNextScreen = message;
    }

    /**
     * 获取并清除“下一个界面的消息”。
     * @return 如果有消息则返回消息字符串，否则返回 null。
     */
    public static String getAndClearMessageForNextScreen() {
        String message = messageForNextScreen;
        messageForNextScreen = null; // 取走后立即清空
        return message;
    }

    /**
     * 加载一个新的 FXML 界面，并替换掉容器中当前的所有内容。
     * @param fxmlName 要加载的 FXML 文件的名字 (例如 "login-view.fxml")。
     */
    public static void load(String fxmlName) {
        if (rootContainer == null) {
            System.err.println("UIManager Error: Root container has not been set! Please call setRoot() first.");
            return;
        }

        try {
            // 从 /assets/ui/fxml/ 目录加载 FXML 文件
            URL url = FXGL.getAssetLoader().getURL("/fxml/" + fxmlName);
            if (url == null) {
                throw new IOException("Cannot find FXML file: " + fxmlName);
            }

            Parent view = FXMLLoader.load(Objects.requireNonNull(UIManager.class.getResource("/fxml/" + fxmlName)));

            // 2.【关键步骤】清空我们持久化的 rootPane 的所有子节点。
            rootContainer.getChildren().clear();

            // 3.【关键步骤】将新加载的视图作为子节点添加到 rootPane 中。
            rootContainer.getChildren().add(view);

        } catch (IOException e) {
            System.err.println("Failed to load FXML: " + fxmlName);
            e.printStackTrace();
        }
    }

    private static final Image DEFAULT_AVATAR = FXGL.getAssetLoader().loadImage("default_avatar.png");

    /**
     * 根据 URL 加载头像，如果 URL 无效或加载失败，则返回默认头像。
     * @param avatarUrl 头像的 URL 地址，可以为 null。
     * @return JavaFX Image 对象。
     */
    public static Image loadAvatar(String avatarUrl) {
        try {
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                // true 表示在后台线程加载，不会阻塞UI
                return new Image(avatarUrl);
            }
        } catch (Exception e) {
            System.err.println("加载头像失败，URL: " + avatarUrl + ", Error: " + e.getMessage());
        }
        // 如果 URL 为 null、为空或加载失败，都返回默认头像
        return DEFAULT_AVATAR;
    }


}