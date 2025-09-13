package org.csu.pixelstrikejavafx.game.weapon;

import com.almasb.fxgl.dsl.components.ProjectileComponent;
import com.almasb.fxgl.entity.components.CollidableComponent;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.csu.pixelstrikejavafx.game.core.GameType;
import org.csu.pixelstrikejavafx.game.player.OnFireCallback;
import org.csu.pixelstrikejavafx.game.player.Player;
import org.csu.pixelstrikejavafx.game.player.component.BulletComponent;

import java.util.concurrent.ThreadLocalRandom;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;

public class Shotgun implements Weapon {

    // 霰弹枪参数
    private static final double TIME_BETWEEN_BULLETS = 0.8; // 射速慢
    private static final int PELLETS_COUNT = 8; // 弹丸数量
    private static final double SPREAD_ARC_DEG = 15.0; // 扩散角度
    private static final double BULLET_SPEED = 2000.0;
    private static final double DAMAGE_PER_PELLET = 4.0;
    private static final double SHOOT_RANGE = 600.0; // 射程较近

    private double timeSinceLastShot = 0.0;

    @Override
    public boolean shoot(Player shooter, OnFireCallback callback) {
        if (timeSinceLastShot >= TIME_BETWEEN_BULLETS) {
            timeSinceLastShot = 0.0;

            Point2D origin = getShootOrigin(shooter);

            // **核心逻辑**：循环多次生成弹丸
            for (int i = 0; i < PELLETS_COUNT; i++) {
                Point2D direction = calculateDirection(shooter);
                spawnBullet(origin, direction, shooter);

                // 只向服务器报告一次射击事件（或多次，取决于服务器验证逻辑）
                // 为简化，我们报告一次中心射击
                if (i == 0 && shooter.getShootingSys().getReporter() != null) {
                    shooter.getShootingSys().getReporter().onShot(
                        origin.getX(), origin.getY(),
                        direction.getX(), direction.getY(),
                        SHOOT_RANGE, (int)DAMAGE_PER_PELLET, System.currentTimeMillis(),
                        "Shotgun"
                    );
                }
            }
            if (callback != null) callback.onSuccessfulShot();
            return true;
        }
        return false;
    }

    @Override
    public void update(double tpf) {
        timeSinceLastShot += tpf;
    }

    @Override
    public void onFireStart() {
        // 霰弹枪是半自动的，按一下打一发
    }

    @Override
    public void onFireStop() {
    }

    private Point2D getShootOrigin(Player shooter) {
        var e = shooter.getEntity();
        double offsetX = shooter.getFacingRight() ? 150 : 0;
        return new Point2D(e.getX() + e.getWidth() / 2.0 + offsetX, e.getY() + e.getHeight() / 2.0);
    }

    private Point2D calculateDirection(Player shooter) {
        double baseDeg = shooter.getFacingRight() ? 0.0 : 180.0;
        // 在扩散弧度内随机一个角度
        double spread = ThreadLocalRandom.current().nextDouble(-SPREAD_ARC_DEG / 2, SPREAD_ARC_DEG / 2);
        double finalDeg = baseDeg + spread;
        double rad = Math.toRadians(finalDeg);
        return new Point2D(Math.cos(rad), Math.sin(rad)).normalize();
    }

    private void spawnBullet(Point2D origin, Point2D direction, Player shooter) {
        entityBuilder()
                .type(GameType.BULLET)
                .at(origin)
                .viewWithBBox(new Rectangle(6, 6, Color.DARKSLATEGRAY)) // 弹丸小一点
                .with(new CollidableComponent(true))
                .with(new ProjectileComponent(direction, BULLET_SPEED))
                .with(new BulletComponent(shooter.getEntity()))
                .buildAndAttach();
    }
}