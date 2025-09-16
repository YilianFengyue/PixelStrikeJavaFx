package org.csu.pixelstrikejavafx.game.player.component;

import com.almasb.fxgl.entity.component.Component;

public class BouncyComponent extends Component {

    private final double bounceVelocity;

    /**
     * @param bounceVelocity 向上弹射的速度值
     */
    public BouncyComponent(double bounceVelocity) {
        this.bounceVelocity = bounceVelocity;
    }

    public double getBounceVelocity() {
        return bounceVelocity;
    }
}