package org.csu.pixelstrikejavafx.game.weapon;

import com.almasb.fxgl.dsl.components.ProjectileComponent;
import com.almasb.fxgl.entity.components.CollidableComponent;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.csu.pixelstrikejavafx.game.core.GameType;
import org.csu.pixelstrikejavafx.game.player.BulletComponent;
import org.csu.pixelstrikejavafx.game.player.Player;

import java.util.concurrent.ThreadLocalRandom;
import static com.almasb.fxgl.dsl.FXGL.entityBuilder;

public class Pistol implements Weapon {

    // --- 将所有射击参数从 PlayerShooting 移到这里 ---
    private static final double BULLET_SPEED = 1500.0;
    private static final double TIME_BETWEEN_BULLETS = 0.15;
    private static final double SHOOT_RANGE = 1200.0;
    private static final double DAMAGE = 10.0;
    private static final double MUZZLE_RIGHT_X = 150, MUZZLE_LEFT_X = 0, MUZZLE_Y = 0;
    private static final double SWAY_AMPL_DEG = 1.6, SWAY_SPEED = 18.0;
    private static final double SPREAD_NOISE_DEG = 0.6;
    private static final double RECOIL_KICK_DEG = 0.5, RECOIL_MAX_DEG = 6.0;
    private static final double RECOIL_RECOVER_DEG_PER_SEC = 8.0;
    private static final double RECOIL_KNOCKBACK_VX = 200.0, RECOIL_KNOCKBACK_UP = 20.0;

    // --- 武器自身的状态 ---
    private double timeSinceLastShot = 0.0;
    private double swayPhase = 0.0;
    private double recoilAngleDeg = 0.0;
    private boolean isFiring = false;

    @Override
    public boolean shoot(Player shooter) {
        if (timeSinceLastShot >= TIME_BETWEEN_BULLETS) {
            timeSinceLastShot = 0.0;

            Point2D origin = getShootOrigin(shooter);
            Point2D direction = calculateDirection(shooter);

            spawnBullet(origin, direction, shooter);

            // 应用后坐力
            recoilAngleDeg = Math.min(RECOIL_MAX_DEG, recoilAngleDeg + RECOIL_KICK_DEG);
            applyRecoilToPlayer(shooter);
            
            // 通知服务器
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
        swayPhase += SWAY_SPEED * tpf;

        if (recoilAngleDeg > 0) {
            recoilAngleDeg = Math.max(0, recoilAngleDeg - RECOIL_RECOVER_DEG_PER_SEC * tpf);
        }
        
        // 如果是自动武器（如机枪），可以在这里持续开火
        // if (isFiring) { shoot(owner); }
    }

    @Override
    public void onFireStart() {
        isFiring = true;
    }

    @Override
    public void onFireStop() {
        isFiring = false;
    }
    
    // --- 以下是从 PlayerShooting 移过来的辅助方法 ---
    private Point2D getShootOrigin(Player shooter) {
        var e = shooter.getEntity();
        double offsetX = shooter.getFacingRight() ? MUZZLE_RIGHT_X : -MUZZLE_LEFT_X;
        return new Point2D(e.getX() + e.getWidth() / 2.0 + offsetX, e.getY() + e.getHeight() / 2.0 + MUZZLE_Y);
    }
    
    private Point2D calculateDirection(Player shooter) {
        double baseDeg = shooter.getFacingRight() ? 0.0 : 180.0;
        double swayDeg = Math.sin(swayPhase) * SWAY_AMPL_DEG;
        double noiseDeg = ThreadLocalRandom.current().nextDouble(-SPREAD_NOISE_DEG, SPREAD_NOISE_DEG);
        double upDeg = recoilAngleDeg + swayDeg + noiseDeg;
        double finalDeg = baseDeg + (shooter.getFacingRight() ? -upDeg : +upDeg);
        double rad = Math.toRadians(finalDeg);
        return new Point2D(Math.cos(rad), Math.sin(rad)).normalize();
    }

    private void spawnBullet(Point2D origin, Point2D direction, Player shooter) {
        entityBuilder()
                .type(GameType.BULLET)
                .at(origin)
                .viewWithBBox(new Rectangle(10, 4, Color.ORANGERED))
                .with(new CollidableComponent(true))
                .with(new ProjectileComponent(direction, BULLET_SPEED))
                .with(new BulletComponent(shooter.getEntity()))
                .buildAndAttach();
    }
    
    private void applyRecoilToPlayer(Player shooter) {
        if (shooter.getPhysics() == null) return;
        double kickX = shooter.getFacingRight() ? -RECOIL_KNOCKBACK_VX : RECOIL_KNOCKBACK_VX;
        shooter.getPhysics().setVelocityX(shooter.getPhysics().getVelocityX() + kickX);
        shooter.getPhysics().setVelocityY(shooter.getPhysics().getVelocityY() - RECOIL_KNOCKBACK_UP);
    }
}