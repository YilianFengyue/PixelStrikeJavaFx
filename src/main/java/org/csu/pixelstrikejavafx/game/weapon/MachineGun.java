package org.csu.pixelstrikejavafx.game.weapon;

import com.almasb.fxgl.dsl.components.ProjectileComponent;
import com.almasb.fxgl.entity.components.CollidableComponent;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.csu.pixelstrikejavafx.game.core.GameType;
import org.csu.pixelstrikejavafx.game.player.Player;
import org.csu.pixelstrikejavafx.game.player.component.BulletComponent;

import java.util.concurrent.ThreadLocalRandom;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;

public class MachineGun implements Weapon {

    private static final double TIME_BETWEEN_BULLETS = 0.08;
    private static final double BULLET_SPEED = 1800.0;
    private static final double SPREAD_NOISE_DEG = 2.5;
    private static final double DAMAGE = 8.0;
    private static final double SHOOT_RANGE = 1200.0;
    private static final double MUZZLE_RIGHT_X = 150, MUZZLE_LEFT_X = 0, MUZZLE_Y = 0;
    private static final double RECOIL_KICK_DEG = 0.8, RECOIL_MAX_DEG = 10.0;
    private static final double RECOIL_RECOVER_DEG_PER_SEC = 12.0;

    private double timeSinceLastShot = 0.0;
    private double recoilAngleDeg = 0.0;
    private boolean isFiring = false;
    private Player shooter; // 在装备时设置

    @Override
    public void onEquip(Player player) {
        this.shooter = player;
    }

    @Override
    public boolean shoot(Player shooter) {
        if (timeSinceLastShot >= TIME_BETWEEN_BULLETS) {
            timeSinceLastShot = 0.0;
            Point2D origin = getShootOrigin(shooter);
            Point2D direction = calculateDirection(shooter);
            spawnBullet(origin, direction, shooter);
            recoilAngleDeg = Math.min(RECOIL_MAX_DEG, recoilAngleDeg + RECOIL_KICK_DEG);

            if (shooter.getShootingSys().getReporter() != null) {
                shooter.getShootingSys().getReporter().onShot(
                        origin.getX(), origin.getY(),
                        direction.getX(), direction.getY(),
                        SHOOT_RANGE, (int)DAMAGE, System.currentTimeMillis()
                );
            }
            return true;
        }
        return false;
    }

    @Override
    public void update(double tpf) {
        timeSinceLastShot += tpf;
        if (recoilAngleDeg > 0) {
            recoilAngleDeg = Math.max(0, recoilAngleDeg - RECOIL_RECOVER_DEG_PER_SEC * tpf);
        }

        // 【修复】确保 shooter 对象不为 null 时才进行持续射击
        if (isFiring && this.shooter != null) {
            shoot(this.shooter);
        }
    }

    @Override
    public void onFireStart() {
        isFiring = true;
    }

    @Override
    public void onFireStop() {
        isFiring = false;
    }

    // --- 辅助方法 (无变动) ---
    private Point2D getShootOrigin(Player shooter) {
        var e = shooter.getEntity();
        double offsetX = shooter.getFacingRight() ? MUZZLE_RIGHT_X : -MUZZLE_LEFT_X;
        return new Point2D(e.getX() + e.getWidth() / 2.0 + offsetX, e.getY() + e.getHeight() / 2.0 + MUZZLE_Y);
    }

    private Point2D calculateDirection(Player shooter) {
        double baseDeg = shooter.getFacingRight() ? 0.0 : 180.0;
        double noiseDeg = ThreadLocalRandom.current().nextDouble(-SPREAD_NOISE_DEG, SPREAD_NOISE_DEG);
        double upDeg = recoilAngleDeg + noiseDeg;
        double finalDeg = baseDeg + (shooter.getFacingRight() ? -upDeg : +upDeg);
        double rad = Math.toRadians(finalDeg);
        return new Point2D(Math.cos(rad), Math.sin(rad)).normalize();
    }

    private void spawnBullet(Point2D origin, Point2D direction, Player shooter) {
        entityBuilder()
                .type(GameType.BULLET)
                .at(origin)
                .viewWithBBox(new Rectangle(12, 3, Color.YELLOW))
                .with(new CollidableComponent(true))
                .with(new ProjectileComponent(direction, BULLET_SPEED))
                .with(new BulletComponent(shooter.getEntity()))
                .buildAndAttach();
    }
}