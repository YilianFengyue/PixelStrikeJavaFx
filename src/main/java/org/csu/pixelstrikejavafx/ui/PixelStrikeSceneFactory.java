package org.csu.pixelstrikejavafx.ui;

import com.almasb.fxgl.app.scene.FXGLMenu;
import com.almasb.fxgl.app.scene.MenuType;
import com.almasb.fxgl.app.scene.SceneFactory;
import org.jetbrains.annotations.NotNull;


public class PixelStrikeSceneFactory extends SceneFactory {
    @NotNull
    @Override
    public FXGLMenu newMainMenu() {
        // 它只负责执行这一句，创建一个实例
        return new FXMLMainMenu();
    }
}