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

    private static final double TIME_BETWEEN_BULLETS = 1.5; // 射速很慢
    private static final double LAUNCH_VELOCITY = 800.0; // 初始速度
    private static final double DAMAGE = 40.0;
    private static final double SHOOT_RANGE = 2000.0;

    private double timeSinceLastShot = 0.0;

    @Override
    public boolean shoot(Player shooter, OnFireCallback callback) {
        if (timeSinceLastShot >= TIME_BETWEEN_BULLETS) {
            timeSinceLastShot = 0.0;

            Point2D origin = getShootOrigin(shooter);
            // 榴弹有固定的向上发射角度
            Point2D direction = (shooter.getFacingRight() ? new Point2D(1, -0.8) : new Point2D(-1, -0.8)).normalize();

            spawnGrenade(origin, direction, shooter);

            // 通知服务器
            if (shooter.getShootingSys().getReporter() != null) {
                shooter.getShootingSys().getReporter().onShot(
                        origin.getX(), origin.getY(),
                        direction.getX(), direction.getY(),
                        SHOOT_RANGE, (int)DAMAGE, System.currentTimeMillis(),
                        "GrenadeLauncher"
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
        double offsetX = shooter.getFacingRight() ? 150 : 0;
        return new Point2D(e.getX() + e.getWidth() / 2.0 + offsetX, e.getY() + e.getHeight() / 2.0);
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

        // 【修复】 使用正确的方法 setLinearVelocity
        physics.setLinearVelocity(direction.multiply(LAUNCH_VELOCITY));
    }

    // onEquip 是 Weapon 接口的一部分，即使这里不用，也最好实现一下
    @Override
    public void onEquip(Player player) {
        // GrenadeLauncher 不需要保存 player 状态，所以这里为空
    }
}