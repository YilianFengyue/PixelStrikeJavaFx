package org.csu.pixelstrikejavafx.game.player.component;

import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.component.Component;
import javafx.scene.effect.ColorAdjust;
import javafx.util.Duration;

public class PoisonedComponent extends Component {

    private final ColorAdjust poisonEffect = new ColorAdjust();
    private final double durationSeconds;

    public PoisonedComponent(double durationSeconds) {
        this.durationSeconds = durationSeconds;
        // 设置效果：增加绿色，降低亮度和饱和度，模拟中毒
        poisonEffect.setHue(0.4); 
        poisonEffect.setSaturation(0.5);
        poisonEffect.setBrightness(-0.2);
    }

    @Override
    public void onAdded() {
        if (entity != null && !entity.getViewComponent().getChildren().isEmpty()) {
            entity.getViewComponent().getChildren().get(0).setEffect(poisonEffect);
        }
        // 设置一个计时器，在中毒结束后移除此组件和效果
        FXGL.getGameTimer().runOnceAfter(this::removeEffect, Duration.seconds(durationSeconds));
    }

    private void removeEffect() {
        if (entity != null && !entity.getViewComponent().getChildren().isEmpty()) {
            // 恢复实体的正常外观
            entity.getViewComponent().getChildren().get(0).setEffect(null);
        }
        // 从实体上移除此组件
        if (entity != null) {
            entity.removeComponent(PoisonedComponent.class);
        }
    }
}