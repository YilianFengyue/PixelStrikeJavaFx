package org.csu.pixelstrikejavafx.game.player.component;

import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import javafx.util.Duration;

/**
 * 代表一个受重力影响的抛物线射弹（炮弹/榴弹）。
 */
public class GrenadeComponent extends Component {

    private final Entity shooter;
    private final double rotationSpeed = 360; // 每秒旋转360度

    public GrenadeComponent(Entity shooter) {
        this.shooter = shooter;
    }

    @Override
    public void onAdded() {
        // 炮弹在5秒后消失
        FXGL.getGameTimer().runOnceAfter(entity::removeFromWorld, Duration.seconds(5));
    }

    /**
     * 【修复】使用 onUpdate 方法来处理每一帧的旋转逻辑
     * @param tpf a value of time per frame
     */
    @Override
    public void onUpdate(double tpf) {
        // 每帧旋转的角度 = 每秒旋转速度 * 每帧的时间(tpf)
        entity.rotateBy(rotationSpeed * tpf);
    }

    public Entity getShooter() {
        return shooter;
    }
}