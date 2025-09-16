// main/java/org/csu/pixelstrikejavafx/game/player/component/FloatingComponent.java
package org.csu.pixelstrikejavafx.game.player.component;

import com.almasb.fxgl.entity.component.Component;

public class FloatingComponent extends Component {

    private final double floatHeight;
    private final double cycleDurationSeconds;
    private double originalY;
    private double time = 0.0; // 用一个简单的double来累积时间

    /**
     * @param floatHeight   上下浮动的总高度 (例如, 10 像素)
     * @param cycleDuration 完成一次完整浮动周期的时间 (例如, 2.0 秒)
     */
    public FloatingComponent(double floatHeight, double cycleDuration) {
        this.floatHeight = floatHeight;
        this.cycleDurationSeconds = cycleDuration;
    }

    @Override
    public void onAdded() {
        // 当组件被添加到实体时，只记录它的初始Y坐标
        originalY = entity.getY();
    }

    @Override
    public void onUpdate(double tpf) {
        // --- ★ 核心修正：使用 tpf 累积时间 ---

        // 1. 累加每一帧的时间差
        time += tpf;

        // 2. 计算当前时间在周期内的比例
        double ratio = (time % cycleDurationSeconds) / cycleDurationSeconds;

        // 3. 使用正弦函数计算垂直方向的偏移量
        double offsetY = Math.sin(ratio * 2 * Math.PI) * (floatHeight / 2.0);

        // 4. 将计算出的偏移量应用到实体的Y坐标上
        entity.setY(originalY + offsetY);
    }
}