package org.csu.pixelstrikejavafx.game.player;

/**
 * 极简生命系统：只管 HP / 死亡；受伤与死亡时回调 Player。
 * 动画与UI不在本类处理（留给 Player / Animator）。
 */
public class PlayerHealth {

    private static final int MAX_HP = 300;

    private final Player owner;
    private int hp = MAX_HP;
    private boolean dead = false;

    public PlayerHealth(Player owner) {
        this.owner = owner;
    }

    /** 扣血（<=0 直接忽略）；到 0 触发死亡回调 */
    public void takeDamage(int amount) {
        if (dead || amount <= 0) return;

        hp -= amount;
        if (hp < 0) hp = 0;

        // 通知玩家做受伤表现（此方法里先占位，不播放具体动画）
        owner.onDamaged(amount);

        if (hp == 0) {
            dead = true;
            owner.onDeath();   // 由 Player 决定隐藏/动画/禁用输入
        }
    }

    /** 满血复活（给调试/重开） */
    public void reviveFull() {
        dead = false;
        hp = MAX_HP;
        owner.onRevived();
    }
    public void setHp(int newHp) {
        if (dead) return; // 死亡状态下不能修改HP
        this.hp = Math.max(0, Math.min(MAX_HP, newHp));
    }

    // getters
    public int getHp()          { return hp; }
    public int getMaxHp()       { return MAX_HP; }
    public boolean isDead()     { return dead; }
}
