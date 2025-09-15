package org.csu.pixelstrikejavafx.game.weapon;

import com.almasb.fxgl.dsl.components.ProjectileComponent;
import com.almasb.fxgl.entity.components.CollidableComponent;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.csu.pixelstrikejavafx.game.core.GameType;
import org.csu.pixelstrikejavafx.game.player.OnFireCallback;
import org.csu.pixelstrikejavafx.game.player.component.BulletComponent;
import org.csu.pixelstrikejavafx.game.player.Player;

import java.util.concurrent.ThreadLocalRandom;
import static com.almasb.fxgl.dsl.FXGL.entityBuilder;
import static com.almasb.fxgl.dsl.FXGLForKtKt.play;

public class Pistol implements Weapon {

    private final WeaponStats stats;

    private double timeSinceLastShot = 0.0;
    private double swayPhase = 0.0;
    private double recoilAngleDeg = 0.0;
    private boolean isFiring = false;

    public Pistol(WeaponStats stats) {
        this.stats = stats;
    }

    @Override
    public boolean shoot(Player shooter, OnFireCallback callback) {
        if (timeSinceLastShot >= stats.timeBetweenShots) {
            timeSinceLastShot = 0.0;
            play("pistol_shot.wav");

            Point2D origin = getShootOrigin(shooter);
            Point2D direction = calculateDirection(shooter);

            spawnBullet(origin, direction, shooter);

            recoilAngleDeg = Math.min(stats.recoilMaxDeg, recoilAngleDeg + stats.recoilKickDeg);
            applyRecoilToPlayer(shooter);

            if (shooter.getShootingSys().getReporter() != null) {
                shooter.getShootingSys().getReporter().onShot(
                        origin.getX(), origin.getY(),
                        direction.getX(), direction.getY(),
                        stats.shootRange, (int)stats.damage, System.currentTimeMillis(),
                        stats.name
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
        swayPhase += stats.swaySpeed * tpf;

        if (recoilAngleDeg > 0) {
            recoilAngleDeg = Math.max(0, recoilAngleDeg - stats.recoilRecoverDegPerSec * tpf);
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

    private Point2D getShootOrigin(Player shooter) {
        var e = shooter.getEntity();
        double offsetX = shooter.getFacingRight() ? stats.muzzleRightX : -stats.muzzleLeftX;
        return new Point2D(e.getX() + e.getWidth() / 2.0 + offsetX, e.getY() + e.getHeight() / 2.0 + stats.muzzleY);
    }

    private Point2D calculateDirection(Player shooter) {
        double baseDeg = shooter.getFacingRight() ? 0.0 : 180.0;
        double swayDeg = Math.sin(swayPhase) * stats.swayAmplDeg;
        double noiseDeg = ThreadLocalRandom.current().nextDouble(-stats.spreadNoiseDeg, stats.spreadNoiseDeg);
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
                .with(new ProjectileComponent(direction, stats.bulletSpeed))
                .with(new BulletComponent(shooter.getEntity()))
                .buildAndAttach();
    }

    private void applyRecoilToPlayer(Player shooter) {
        if (shooter.getPhysics() == null) return;
        double kickX = shooter.getFacingRight() ? -stats.recoilKnockbackVx : stats.recoilKnockbackVx;
        shooter.getPhysics().setVelocityX(shooter.getPhysics().getVelocityX() + kickX);
        shooter.getPhysics().setVelocityY(shooter.getPhysics().getVelocityY() - stats.recoilKnockbackUp);
    }
}