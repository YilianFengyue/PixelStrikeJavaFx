package org.csu.pixelstrikejavafx.game.weapon;

/**
 * 武器数据容器 (POJO)
 * 集中存放所有武器的数值，便于在 GameConfig 中统一管理和调整。
 */
public class WeaponStats {
    // --- 通用属性 ---
    public final String name;
    public final double damage;
    public final double timeBetweenShots;
    public final double shootRange;

    // --- 子弹/物理 projectile 属性 ---
    public final double bulletSpeed; // 用于子弹
    public final double launchVelocity; // 用于榴弹

    // --- 枪口位置 ---
    public final double muzzleRightX;
    public final double muzzleLeftX;
    public final double muzzleY;

    // --- 霰弹枪专用 ---
    public final int pelletsCount;
    public final double spreadArcDeg;

    // --- 自动/半自动武器的后坐力、扩散和摆动 ---
    public final double swayAmplDeg;
    public final double swaySpeed;
    public final double spreadNoiseDeg;
    public final double recoilKickDeg;
    public final double recoilMaxDeg;
    public final double recoilRecoverDegPerSec;
    public final double recoilKnockbackVx;
    public final double recoilKnockbackUp;

    public WeaponStats(String name, double damage, double timeBetweenShots, double shootRange, double bulletSpeed,
                       double launchVelocity, double muzzleRightX, double muzzleLeftX, double muzzleY,
                       int pelletsCount, double spreadArcDeg, double swayAmplDeg, double swaySpeed,
                       double spreadNoiseDeg, double recoilKickDeg, double recoilMaxDeg,
                       double recoilRecoverDegPerSec, double recoilKnockbackVx, double recoilKnockbackUp) {
        this.name = name;
        this.damage = damage;
        this.timeBetweenShots = timeBetweenShots;
        this.shootRange = shootRange;
        this.bulletSpeed = bulletSpeed;
        this.launchVelocity = launchVelocity;
        this.muzzleRightX = muzzleRightX;
        this.muzzleLeftX = muzzleLeftX;
        this.muzzleY = muzzleY;
        this.pelletsCount = pelletsCount;
        this.spreadArcDeg = spreadArcDeg;
        this.swayAmplDeg = swayAmplDeg;
        this.swaySpeed = swaySpeed;
        this.spreadNoiseDeg = spreadNoiseDeg;
        this.recoilKickDeg = recoilKickDeg;
        this.recoilMaxDeg = recoilMaxDeg;
        this.recoilRecoverDegPerSec = recoilRecoverDegPerSec;
        this.recoilKnockbackVx = recoilKnockbackVx;
        this.recoilKnockbackUp = recoilKnockbackUp;
    }
}