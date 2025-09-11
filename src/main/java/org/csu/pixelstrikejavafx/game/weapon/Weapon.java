package org.csu.pixelstrikejavafx.game.weapon;

import org.csu.pixelstrikejavafx.game.player.Player;

public interface Weapon {
    /**
     * 尝试射击。
     * @param shooter 开火的玩家对象
     * @return 如果成功开火则返回 true，否则（例如，正在冷却中）返回 false。
     */
    boolean shoot(Player shooter);

    /**
     * 每帧更新武器的状态（例如，冷却时间）。
     * @param tpf 每帧的时间间隔
     */
    void update(double tpf);
    
    /**
     * 玩家开始按住开火键时调用。
     */
    void onFireStart();

    /**
     * 玩家松开开火键时调用。
     */
    void onFireStop();
}