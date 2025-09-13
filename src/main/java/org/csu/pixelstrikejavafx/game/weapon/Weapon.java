package org.csu.pixelstrikejavafx.game.weapon;

import org.csu.pixelstrikejavafx.game.player.Player;

public interface Weapon {
    boolean shoot(Player shooter);
    void update(double tpf);
    void onFireStart();
    void onFireStop();

    /**
     * 当武器被装备时调用，允许武器保存对玩家的引用。
     * @param player 装备该武器的玩家
     */
    default void onEquip(Player player) {}
}