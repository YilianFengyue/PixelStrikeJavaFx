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

    private final WeaponStats stats;

    private double timeSinceLastShot = 0.0;

    public Shotgun(WeaponStats stats) {
        this.stats = stats;
    }

    @Override
    public boolean shoot(Player shooter, OnFireCallback callback) {
        if (timeSinceLastShot >= stats.timeBetweenShots) {
            timeSinceLastShot = 0.0;

            Point2D origin = getShootOrigin(shooter);

            for (int i = 0; i < stats.pelletsCount; i++) {
                Point2D direction = calculateDirection(shooter);
                spawnBullet(origin, direction, shooter);

                if (i == 0 && shooter.getShootingSys().getReporter() != null) {
                    shooter.getShootingSys().getReporter().onShot(
                            origin.getX(), origin.getY(),
                            direction.getX(), direction.getY(),
                            stats.shootRange, (int)stats.damage, System.currentTimeMillis(),
                            stats.name
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
    public void onFireStart() {}

    @Override
    public void onFireStop() {}

    private Point2D getShootOrigin(Player shooter) {
        var e = shooter.getEntity();
        double offsetX = shooter.getFacingRight() ? stats.muzzleRightX : -stats.muzzleLeftX;
        return new Point2D(e.getX() + e.getWidth() / 2.0 + offsetX, e.getY() + e.getHeight() / 2.0 + stats.muzzleY);
    }

    private Point2D calculateDirection(Player shooter) {
        double baseDeg = shooter.getFacingRight() ? 0.0 : 180.0;
        double spread = ThreadLocalRandom.current().nextDouble(-stats.spreadArcDeg / 2, stats.spreadArcDeg / 2);
        double finalDeg = baseDeg + spread;
        double rad = Math.toRadians(finalDeg);
        return new Point2D(Math.cos(rad), Math.sin(rad)).normalize();
    }

    private void spawnBullet(Point2D origin, Point2D direction, Player shooter) {
        entityBuilder()
                .type(GameType.BULLET)
                .at(origin)
                .viewWithBBox(new Rectangle(6, 6, Color.DARKSLATEGRAY))
                .with(new CollidableComponent(true))
                .with(new ProjectileComponent(direction, stats.bulletSpeed))
                .with(new BulletComponent(shooter.getEntity()))
                .buildAndAttach();
    }
}