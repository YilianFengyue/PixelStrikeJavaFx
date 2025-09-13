package org.csu.pixelstrikejavafx.game.player.component;

import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import javafx.util.Duration;

public class BulletComponent extends Component {

    private final Entity shooter;

    public BulletComponent(Entity shooter) {
        this.shooter = shooter;
    }

    @Override
    public void onAdded() {
        // 让子弹在2秒后自动消失，防止无限飞行
        FXGL.getGameTimer().runOnceAfter(() -> {
            if (entity != null && entity.isActive()) {
                entity.removeFromWorld();
            }
        }, Duration.seconds(2));
    }

    public Entity getShooter() {
        return shooter;
    }
}