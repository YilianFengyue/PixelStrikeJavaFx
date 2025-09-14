package org.csu.pixelstrikejavafx.game.player;

import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.physics.RaycastResult;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import org.csu.pixelstrikejavafx.game.core.GameType;
import java.util.concurrent.ThreadLocalRandom;
import org.csu.pixelstrikejavafx.content.WeaponDef;

import static com.almasb.fxgl.dsl.FXGL.*;

public class PlayerShooting {

    private final Player player;
    private WeaponDef currentWeapon;
    private double time = 0.0;
    private boolean isShooting = false;
    private double swayPhase = 0.0;
    private double recoilAngleDeg = 0.0;
    private Entity shootLine = null;

    public interface ShotReporter {
        void onShot(double ox, double oy, double dx, double dy, double range, int damage, long ts, String weaponType);
    }

    private ShotReporter reporter;
    public void setShotReporter(ShotReporter r) { this.reporter = r; }
    public ShotReporter getReporter() { return this.reporter; }

    public PlayerShooting(Player player) {
        this.player = player;
    }

    public void setWeapon(WeaponDef def) {
        this.currentWeapon = def;
        this.recoilAngleDeg = 0; // 切换武器时重置后坐力
    }

    public void update(double tpf) {
        time += tpf;

        if (recoilAngleDeg > 0) {
            recoilAngleDeg = Math.max(0, recoilAngleDeg - (getRecoilRecoverDegPerSec() * tpf));
        }
        swayPhase += getSwaySpeed() * tpf;

        if (isShooting && time >= getInterval()) {
            performShoot();
        }

        if (shootLine != null && time >= 0.07) { // 射击特效持续时间
            hideShootEffects();
        }
    }

    public void startShooting() {
        isShooting = true;
        // 立即射击一次，而不是等待下一次更新
        if (time >= getInterval()) {
            performShoot();
        }
    }

    public void stopShooting() {
        isShooting = false;
    }

    private void performShoot() {
        if (player.isDead()) return;
        time = 0.0;

        Point2D shootOrigin = player.getMuzzleWorld();

        // 复杂的射击角度计算（后坐力、摆动、随机散射）
        double baseDeg = player.getFacingRight() ? 0.0 : 180.0;
        double swayDeg = Math.sin(swayPhase) * getSwayAmplDeg();
        double noiseDeg = ThreadLocalRandom.current().nextDouble(-getSpreadNoiseDeg(), getSpreadNoiseDeg());
        double upDeg = recoilAngleDeg + swayDeg + noiseDeg;
        double finalDeg = baseDeg + (player.getFacingRight() ? -upDeg : +upDeg);
        double rad = Math.toRadians(finalDeg);
        Point2D shootDirection = new Point2D(Math.cos(rad), Math.sin(rad)).normalize();

        double range = getShootRange();
        Point2D rayEnd = shootOrigin.add(shootDirection.multiply(range));
        RaycastResult result = getPhysicsWorld().raycast(shootOrigin, rayEnd);

        Point2D hitPoint = rayEnd;
        if (result.getPoint().isPresent()) {
            hitPoint = result.getPoint().get();
        }

        playMuzzleSfx();
        showShootEffects(shootOrigin, hitPoint);

        recoilAngleDeg = Math.min(getRecoilMaxDeg(), recoilAngleDeg + getRecoilKickDeg());
        applyRecoilToPlayer();

        // 将射击事件报告给网络服务
        if (reporter != null) {
            reporter.onShot(
                    shootOrigin.getX(), shootOrigin.getY(),
                    shootDirection.getX(), shootDirection.getY(),
                    range, getDamage(), System.currentTimeMillis(),
                    currentWeapon.id
            );
        }

        // 通知Player类，以便触发动画
        if (player instanceof OnFireCallback) {
            ((OnFireCallback) player).onSuccessfulShot();
        }
    }

    private void applyRecoilToPlayer() {
        if (player.getPhysics() == null) return;
        double kickX = player.getFacingRight() ? -getRecoilKnockbackVx() : getRecoilKnockbackVx();
        player.getPhysics().setVelocityX(player.getPhysics().getVelocityX() + kickX);
        player.getPhysics().setVelocityY(player.getPhysics().getVelocityY() - getRecoilKnockbackUp());
    }

    private void showShootEffects(Point2D start, Point2D end) {
        hideShootEffects();
        Line line = new Line(start.getX(), start.getY(), end.getX(), end.getY());
        line.setStroke(Color.ORANGERED);
        line.setStrokeWidth(2);
        shootLine = entityBuilder().view(line).buildAndAttach();
    }

    private void hideShootEffects() {
        if (shootLine != null) {
            shootLine.removeFromWorld();
            shootLine = null;
        }
    }

    private void playMuzzleSfx() {
        if (currentWeapon == null || currentWeapon.sfx == null || currentWeapon.sfx.muzzle == null) {
            return;
        }
        try {
            String soundPath = "weapons/" + currentWeapon.sfx.muzzle;
            // 注意: FXGL的音量是在全局设置的，这里为了简单起见，我们直接播放
            // 如果需要独立音量，需要更复杂的处理
            getAudioPlayer().playSound(getAssetLoader().loadSound(soundPath));
        } catch (Exception e) {
            System.err.println("播放声音失败: " + e.getMessage());
        }
    }

    // --- 从WeaponDef安全获取属性的辅助方法 ---
    private double getInterval() { return (currentWeapon != null) ? currentWeapon.shootInterval : 0.2; }
    private int getDamage() { return (currentWeapon != null) ? currentWeapon.damage : 10; }
    private double getRecoilKickDeg() { return (currentWeapon != null) ? currentWeapon.recoilKickDeg : 0.5; }
    private double getSwayAmplDeg() { return (currentWeapon != null && currentWeapon.props != null) ? ((Number)currentWeapon.props.getOrDefault("swayAmplDeg", 1.0)).doubleValue() : 1.0; }
    private double getSpreadNoiseDeg() { return (currentWeapon != null && currentWeapon.props != null) ? ((Number)currentWeapon.props.getOrDefault("spreadDeg", 0.5)).doubleValue() : 0.5; }
    private double getRecoilKnockbackVx() { return (currentWeapon != null && currentWeapon.props != null) ? ((Number)currentWeapon.props.getOrDefault("recoilKnockbackVX", 150.0)).doubleValue() : 150.0; }
    private double getRecoilKnockbackUp() { return (currentWeapon != null && currentWeapon.props != null) ? ((Number)currentWeapon.props.getOrDefault("recoilKnockbackUp", 0.0)).doubleValue() : 0.0; }
    private double getRecoilMaxDeg() { return 10.0; } // 默认最大后坐力
    private double getRecoilRecoverDegPerSec() { return 15.0; } // 默认后坐力恢复速度
    private double getSwaySpeed() { return 4.0; } // 默认摆动速度
    private double getShootRange() { return 2000.0; } // 默认射程
}