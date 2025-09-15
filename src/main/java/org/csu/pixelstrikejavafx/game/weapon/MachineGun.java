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
import static com.almasb.fxgl.dsl.FXGLForKtKt.play;

public class MachineGun implements Weapon {

    private final WeaponStats stats;

    private double timeSinceLastShot = 0.0;
    private double recoilAngleDeg = 0.0;
    private boolean isFiring = false;
    private Player shooter;

    public MachineGun(WeaponStats stats) {
        this.stats = stats;
    }

    @Override
    public void onEquip(Player player) {
        this.shooter = player;
    }

    @Override
    public boolean shoot(Player shooter, OnFireCallback callback) {
        if (timeSinceLastShot >= stats.timeBetweenShots) {
            timeSinceLastShot = 0.0;

            play("machinegun_shot.wav");
            Point2D origin = getShootOrigin(shooter);
            Point2D direction = calculateDirection(shooter);
            spawnBullet(origin, direction, shooter);
            recoilAngleDeg = Math.min(stats.recoilMaxDeg, recoilAngleDeg + stats.recoilKickDeg);

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
        if (recoilAngleDeg > 0) {
            recoilAngleDeg = Math.max(0, recoilAngleDeg - stats.recoilRecoverDegPerSec * tpf);
        }
        if (isFiring && this.shooter != null) {
            shoot(this.shooter, this.shooter);
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
        double noiseDeg = ThreadLocalRandom.current().nextDouble(-stats.spreadNoiseDeg, stats.spreadNoiseDeg);
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
                .with(new ProjectileComponent(direction, stats.bulletSpeed))
                .with(new BulletComponent(shooter.getEntity()))
                .buildAndAttach();
    }
}