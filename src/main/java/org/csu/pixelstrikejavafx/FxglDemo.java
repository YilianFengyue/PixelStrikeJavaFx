package org.csu.pixelstrikejavafx;

import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.dsl.FXGL;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 * 一个最小的 FXGL Demo1
 */
public class FxglDemo extends GameApplication {

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setWidth(800);
        settings.setHeight(600);
        settings.setTitle("FXGL Test Demo");
    }

    @Override
    protected void initGame() {
        // 添加一个矩形，看看能不能显示
        FXGL.entityBuilder()
                .at(400, 300)
                .view(new Rectangle(40, 40, Color.RED))
                .buildAndAttach();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
