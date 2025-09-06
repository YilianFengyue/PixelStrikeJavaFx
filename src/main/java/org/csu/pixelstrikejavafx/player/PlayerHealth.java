package org.csu.pixelstrikejavafx.player;

/**
 * 玩家生命值系统
 * 参考Unity的MyPlayerHealth设计
 */
public class PlayerHealth {

    // ===== 生命值配置 =====
    private static final int STARTING_HEALTH = 100;

    // ===== 运行时状态 =====
    private int currentHealth;
    private boolean isDead = false;
    private boolean damaged = false;  // 本帧是否受伤（用于UI效果）

    // ===== 关联组件 =====
    private final Player player;

    // ===== 受伤效果参数 =====
    private double damageFlashTime = 0.0;
    private static final double DAMAGE_FLASH_DURATION = 0.3;

    public PlayerHealth(Player player) {
        this.player = player;
        this.currentHealth = STARTING_HEALTH;
    }

    /**
     * 每帧更新 - 处理受伤效果
     */
    public void update(double tpf) {
        // 更新受伤闪烁效果
        if (damageFlashTime > 0) {
            damageFlashTime -= tpf;
            if (damageFlashTime <= 0) {
                damaged = false;
            }
        }
    }

    /**
     * 受到伤害
     */
    public void takeDamage(int amount) {
        if (isDead) {
            return;
        }

        // 标记受伤状态
        damaged = true;
        damageFlashTime = DAMAGE_FLASH_DURATION;

        // 扣除生命值
        currentHealth -= amount;

        // 播放受伤音效
        // TODO: 添加音效系统

        System.out.println("玩家受到 " + amount + " 点伤害，剩余生命值: " + currentHealth);

        // 检查死亡
        if (currentHealth <= 0) {
            currentHealth = 0;
            die();
        }
    }

    /**
     * 玩家死亡
     */
    private void die() {
        if (isDead) return;

        isDead = true;

        System.out.println("玩家死亡");

        // 通知玩家组件死亡
        player.die();

        // TODO: 播放死亡音效
        // TODO: 播放死亡动画
        // TODO: 禁用移动和射击
    }

    /**
     * 治疗
     */
    public void heal(int amount) {
        if (isDead) return;

        currentHealth = Math.min(currentHealth + amount, STARTING_HEALTH);
        System.out.println("玩家治疗 " + amount + " 点，当前生命值: " + currentHealth);
    }

    /**
     * 复活
     */
    public void revive() {
        isDead = false;
        currentHealth = STARTING_HEALTH;
        damaged = false;
        damageFlashTime = 0;

        // 通知玩家组件复活
        player.revive();

        System.out.println("玩家复活，生命值恢复满值");
    }

    /**
     * 重置生命值（用于重生或重新开始）
     */
    public void resetHealth() {
        currentHealth = STARTING_HEALTH;
        isDead = false;
        damaged = false;
        damageFlashTime = 0;
    }

    // ===== Getter方法 =====
    public int getCurrentHealth() {
        return currentHealth;
    }

    public int getMaxHealth() {
        return STARTING_HEALTH;
    }

    public boolean isDead() {
        return isDead;
    }

    public boolean isDamaged() {
        return damaged;
    }

    /**
     * 获取生命值百分比（用于UI显示）
     */
    public double getHealthPercentage() {
        return (double) currentHealth / STARTING_HEALTH;
    }

    /**
     * 获取受伤闪烁透明度（用于UI效果）
     */
    public double getDamageFlashAlpha() {
        if (!damaged || damageFlashTime <= 0) {
            return 0.0;
        }
        return (damageFlashTime / DAMAGE_FLASH_DURATION) * 0.3; // 最大30%透明度
    }
}