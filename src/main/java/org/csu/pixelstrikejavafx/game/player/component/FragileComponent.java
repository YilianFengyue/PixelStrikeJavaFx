package org.csu.pixelstrikejavafx.game.player.component;

import com.almasb.fxgl.animation.AnimationBuilder;
import com.almasb.fxgl.entity.component.Component;
import com.almasb.fxgl.entity.components.CollidableComponent;
import javafx.util.Duration;

// 确保以下两个 import 语句是存在的
import static com.almasb.fxgl.dsl.FXGL.getGameTimer;
import static com.almasb.fxgl.dsl.FXGL.play;


public class FragileComponent extends Component {

    private final Duration shakeDuration;
    private final Duration fallDelay;
    private boolean isTriggered = false;

    public FragileComponent(Duration shakeDuration, Duration fallDelay) {
        this.shakeDuration = shakeDuration;
        this.fallDelay = fallDelay;
    }

    public void trigger() {
        if (isTriggered) return;
        isTriggered = true;

        // 【最终修正】直接使用 AnimationBuilder 的 .buildAndPlay() 链式调用
        // 这是FXGL中最标准、最不可能出错的动画播放方式
        new AnimationBuilder()
                .duration(Duration.millis(50))
                .repeat((int)(shakeDuration.toMillis() / 50))
                .autoReverse(true)
                .translate(entity)
                .from(entity.getPosition())
                .to(entity.getPosition().add(5, 0))
                .buildAndPlay(); // 直接构建并播放

        play("fragile_platform_shake.wav");

        // 延迟后，禁用碰撞并播放下落动画，然后移除
        getGameTimer().runOnceAfter(() -> {
            if (entity == null || !entity.isActive()) return;

            entity.getComponent(CollidableComponent.class).setValue(false);
            play("fragile_platform_fall.wav");

            // 【最终修正】同样，对下落动画也使用 .buildAndPlay()
            new AnimationBuilder()
                    .duration(Duration.seconds(0.5))
                    .onFinished(entity::removeFromWorld) // 在动画结束时移除实体
                    .translate(entity)
                    .from(entity.getPosition())
                    .to(entity.getPosition().add(0, 400))
                    .buildAndPlay(); // 直接构建并播放

        }, fallDelay);
    }
}