package org.csu.pixelstrikejavafx.ui;

import com.almasb.fxgl.dsl.FXGL;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.Pane;
import java.io.IOException;
import java.net.URL;

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

    /**
     * 初始化 UIManager，必须在程序启动时由主菜单调用一次。
     * @param rootPane FXMLMainMenu 中创建的那个根布局容器 (例如 StackPane)。
     */
    public static void setRoot(Pane rootPane) {
        rootContainer = rootPane;
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

            Parent newContent = FXMLLoader.load(url);

            // 替换掉 rootContainer 中的所有子节点为新的 FXML 内容
            rootContainer.getChildren().setAll(newContent);

        } catch (IOException e) {
            System.err.println("Failed to load FXML: " + fxmlName);
            e.printStackTrace();
        }
    }
}