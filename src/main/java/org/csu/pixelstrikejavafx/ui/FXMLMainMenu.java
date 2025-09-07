package org.csu.pixelstrikejavafx.ui;

import com.almasb.fxgl.app.scene.FXGLMenu;
import com.almasb.fxgl.app.scene.MenuType;
import com.almasb.fxgl.dsl.FXGL;
import javafx.scene.layout.StackPane;

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

        // 3. 加载初始界面 (例如登录页)
        UIManager.load("login-view.fxml");

        // 4. 将我们的 UI 根容器添加到 FXGL 菜单场景中
        getContentRoot().getChildren().add(uiRoot);
    }
}