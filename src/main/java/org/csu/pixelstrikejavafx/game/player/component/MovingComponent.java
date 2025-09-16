package org.csu.pixelstrikejavafx.game.player.component;

import com.almasb.fxgl.entity.component.Component;
import com.almasb.fxgl.physics.PhysicsComponent;
import javafx.geometry.Point2D;

public class MovingComponent extends Component {

    private final double distanceX;
    private final double distanceY;
    private final double speed;
    private PhysicsComponent physics;
    private Point2D startPosition;
    private Point2D endPosition;
    private boolean movingToEnd = true;

    /**
     * @param distanceX 水平移动总距离
     * @param distanceY 垂直移动总距离
     * @param speed     移动速度
     */
    public MovingComponent(double distanceX, double distanceY, double speed) {
        this.distanceX = distanceX;
        this.distanceY = distanceY;
        this.speed = speed;
    }

    @Override
    public void onAdded() {
        this.startPosition = entity.getPosition();
        this.endPosition = startPosition.add(distanceX, distanceY);
        this.physics = entity.getComponent(PhysicsComponent.class);
        // 关键：将平台刚体设置为 Kinematic，这样它才能移动并推动玩家
        physics.setBodyType(com.almasb.fxgl.physics.box2d.dynamics.BodyType.KINEMATIC);
    }

    @Override
    public void onUpdate(double tpf) {
        Point2D target = movingToEnd ? endPosition : startPosition;
        Point2D current = entity.getPosition();

        Point2D direction = target.subtract(current).normalize();
        double distance = current.distance(target);

        if (distance <= speed * tpf) {
            // 到达目标点，反向
            physics.setVelocityX(0);
            physics.setVelocityY(0);
            entity.setPosition(target); // 精准定位到目标点
            movingToEnd = !movingToEnd;
        } else {
            // 朝目标移动
            physics.setVelocityX(direction.getX() * speed);
            physics.setVelocityY(direction.getY() * speed);
        }
    }
}