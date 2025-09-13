package org.csu.pixelstrikejavafx.game.weapon;

import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.Entity;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.util.Duration;
import org.csu.pixelstrikejavafx.game.player.OnFireCallback;
import org.csu.pixelstrikejavafx.game.player.Player;

public class Railgun implements Weapon {

    private static final double TIME_BETWEEN_BULLETS = 1.0; // 充能时间长
    private static final double DAMAGE = 75.0;
    private static final double SHOOT_RANGE = 4000.0; // 射程极远
    private static final double MUZZLE_RIGHT_X = 150, MUZZLE_Y = 0;

    private double timeSinceLastShot = 0.0;

    @Override
    public boolean shoot(Player shooter, OnFireCallback callback) {
        if (timeSinceLastShot >= TIME_BETWEEN_BULLETS) {
            timeSinceLastShot = 0.0;

            Point2D origin = getShootOrigin(shooter);
            Point2D direction = shooter.getFacingRight() ? new Point2D(1, 0) : new Point2D(-1, 0);
            Point2D end = origin.add(direction.multiply(SHOOT_RANGE));

            // **核心逻辑**：创建一道短暂存在的激光视觉效果
            Line laserBeam = new Line(origin.getX(), origin.getY(), end.getX(), end.getY());
            laserBeam.setStroke(Color.CYAN);
            laserBeam.setStrokeWidth(4);

            Entity laserEffect = FXGL.entityBuilder()
                    .at(0, 0) // 位置不重要，因为它使用绝对坐标
                    .view(laserBeam)
                    .buildAndAttach();

            // 激光效果在 0.1 秒后消失
            FXGL.getGameTimer().runOnceAfter(laserEffect::removeFromWorld, Duration.seconds(0.1));

            // **立即**通知服务器发生了这次射击
            if (shooter.getShootingSys().getReporter() != null) {
                shooter.getShootingSys().getReporter().onShot(
                    origin.getX(), origin.getY(),
                    direction.getX(), direction.getY(),
                    SHOOT_RANGE, (int)DAMAGE, System.currentTimeMillis(),
                    "Railgun"
                );
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
    public void onFireStart() {}

    @Override
    public void onFireStop() {}
    
    private Point2D getShootOrigin(Player shooter) {
        var e = shooter.getEntity();
        double offsetX = shooter.getFacingRight() ? MUZZLE_RIGHT_X : -MUZZLE_RIGHT_X;
        return new Point2D(e.getX() + e.getWidth() / 2.0 + offsetX, e.getY() + e.getHeight() / 2.0 + MUZZLE_Y);
    }
}