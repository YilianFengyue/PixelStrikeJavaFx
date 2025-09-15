package org.csu.pixelstrikejavafx.game.weapon;

import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.Entity;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.util.Duration;
import org.csu.pixelstrikejavafx.game.player.OnFireCallback;
import org.csu.pixelstrikejavafx.game.player.Player;

import static com.almasb.fxgl.dsl.FXGLForKtKt.play;

public class Railgun implements Weapon {

    private final WeaponStats stats;

    private double timeSinceLastShot = 0.0;

    public Railgun(WeaponStats stats) {
        this.stats = stats;
    }

    @Override
    public boolean shoot(Player shooter, OnFireCallback callback) {
        if (timeSinceLastShot >= stats.timeBetweenShots) {
            timeSinceLastShot = 0.0;

            play("railgun_shot.wav");

            Point2D origin = getShootOrigin(shooter);
            Point2D direction = shooter.getFacingRight() ? new Point2D(1, 0) : new Point2D(-1, 0);
            Point2D end = origin.add(direction.multiply(stats.shootRange));

            Line laserBeam = new Line(origin.getX(), origin.getY(), end.getX(), end.getY());
            laserBeam.setStroke(Color.CYAN);
            laserBeam.setStrokeWidth(4);

            Entity laserEffect = FXGL.entityBuilder()
                    .at(0, 0)
                    .view(laserBeam)
                    .buildAndAttach();

            FXGL.getGameTimer().runOnceAfter(laserEffect::removeFromWorld, Duration.seconds(0.1));

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
}