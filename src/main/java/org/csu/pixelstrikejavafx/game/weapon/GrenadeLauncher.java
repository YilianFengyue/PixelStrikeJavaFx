package org.csu.pixelstrikejavafx.game.weapon;

import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.components.CollidableComponent;
import com.almasb.fxgl.physics.PhysicsComponent;
import com.almasb.fxgl.physics.box2d.dynamics.BodyType;
import com.almasb.fxgl.physics.box2d.dynamics.FixtureDef;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.csu.pixelstrikejavafx.game.core.GameType;
import org.csu.pixelstrikejavafx.game.player.OnFireCallback;
import org.csu.pixelstrikejavafx.game.player.Player;
import org.csu.pixelstrikejavafx.game.player.component.GrenadeComponent;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;

public class GrenadeLauncher implements Weapon {

    private final WeaponStats stats;

    private double timeSinceLastShot = 0.0;

    public GrenadeLauncher(WeaponStats stats) {
        this.stats = stats;
    }

    @Override
    public boolean shoot(Player shooter, OnFireCallback callback) {
        if (timeSinceLastShot >= stats.timeBetweenShots) {
            timeSinceLastShot = 0.0;

            Point2D origin = getShootOrigin(shooter);
            Point2D direction = (shooter.getFacingRight() ? new Point2D(1, -0.8) : new Point2D(-1, -0.8)).normalize();

            spawnGrenade(origin, direction, shooter);

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

    private void spawnGrenade(Point2D origin, Point2D direction, Player shooter) {
        PhysicsComponent physics = new PhysicsComponent();
        physics.setBodyType(BodyType.DYNAMIC);
        physics.setFixtureDef(new FixtureDef().density(0.5f).restitution(0.4f));

        Entity grenade = entityBuilder()
                .type(GameType.PROJECTILE)
                .at(origin)
                .viewWithBBox(new Circle(8, Color.DARKOLIVEGREEN))
                .with(new CollidableComponent(true))
                .with(physics)
                .with(new GrenadeComponent(shooter.getEntity()))
                .buildAndAttach();

        physics.setLinearVelocity(direction.multiply(stats.launchVelocity));
    }
}