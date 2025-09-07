package org.csu.pixelstrikejavafx.net.dto;


import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PlayerState {

    private String playerId;

    // 服务器权威的位置和速度
    private double x;
    private double y;
    private double velocityX;
    private double velocityY;

    private int health;
    private int currentWeaponId;
    private int ammo; //当前武器的剩余弹药
    private int kills = 0; // 本局游戏中的击杀数
    private int deaths = 0; //本局游戏中的死亡数。
    private int ping = 0; // 玩家的网络延迟(ms)
    private boolean isFacingRight; // 角色是否朝向右边，是否翻转精灵图。

    /**
     * 玩家当前的动画状态。
     * 这是一个关键字段，用于告诉客户端C(Dev5)应该播放哪个动画。
     * 例如：IDLE, RUN, JUMP, FALL, SHOOT, HIT, DEAD
     */
    private PlayerActionState currentAction;
    private Integer gameTimeRemainingSeconds;

    // 二段跳属性
    private boolean canDoubleJump = false;

    public PlayerState(PlayerState original) {
        this.playerId = original.playerId;
        this.x = original.x;
        this.y = original.y;
        this.velocityX = original.velocityX;
        this.velocityY = original.velocityY;
        this.health = original.health;
        this.currentWeaponId = original.currentWeaponId;
        this.ammo = original.ammo;
        this.kills = original.kills;
        this.deaths = original.deaths;
        this.isFacingRight = original.isFacingRight;
        this.currentAction = original.currentAction;
        this.canDoubleJump = original.canDoubleJump;
    }

    public enum PlayerActionState {
        IDLE, RUN, JUMP, FALL, SHOOT, HIT, DEAD
    }
}