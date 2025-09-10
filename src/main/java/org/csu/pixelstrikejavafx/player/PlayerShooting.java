package org.csu.pixelstrikejavafx.player;

import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.physics.RaycastResult;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import org.csu.pixelstrikejavafx.core.GameType;

import java.util.concurrent.ThreadLocalRandom;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;
import static com.almasb.fxgl.dsl.FXGL.getPhysicsWorld;
/**
 * 射击系统 - 基于射线检测的射击逻辑
 * 参考Unity的MyPlayerShooting设计
 */
public class PlayerShooting {



    // 伤害 / 击退

    private static final double KB_X   = 220.0;
    private static final double KB_Y   = 180.0;



    // ===== 射击参数配置 =====
    private static final double TIME_BETWEEN_BULLETS = 0.15;  // 射击间隔(秒)
    private static final double EFFECTS_DISPLAY_TIME = 0.2;   // 效果显示时间
    private static final double SHOOT_RANGE = 1200.0;          // 射击距离
    private static final double DAMAGE = 10.0;                // 伤害值

    // ===== 枪口/摆动/散布/后坐（都可手调） =====
    // 枪口偏移：只改这三项就能把射线对准你枪口
    private static final double MUZZLE_RIGHT_X = 150;  // 朝右时 X 偏移（像素）
    private static final double MUZZLE_LEFT_X  = 0;   // 朝左时 X 偏移（像素）
    private static final double MUZZLE_Y       = 0;   // Y 偏移（像素，向下为正）

    // 上下摆动（视为“很轻微的抖动”）
    private static final double SWAY_AMPL_DEG  = 1.6;  // 最大摆动角(°)
    private static final double SWAY_SPEED     = 18.0; // 摆动速度（弧度/秒）

    // 每枪随机散布
    private static final double SPREAD_NOISE_DEG = 0.6; // 随机角(°)

    // 连发“越打越上抬”的后坐角
    private static final double RECOIL_KICK_DEG            = 0.5; // 每枪新增(°)
    private static final double RECOIL_MAX_DEG             = 6.0; // 上限(°)
    private static final double RECOIL_RECOVER_DEG_PER_SEC = 8.0; // 不开火时恢复(°/s)

    // 施加在玩家身上的物理后坐力（速度增量）
    private static final double RECOIL_KNOCKBACK_VX = 200.0; // 水平反冲速度
    private static final double RECOIL_KNOCKBACK_UP = 20.0; // 轻微上抬速度

    // ===== 运行时状态 =====
    private double time = 0.0;                    // 计时器
    private boolean isShooting = false;           // 是否正在射击
    private final Player player;                  // 关联的玩家
    // 摆动相位 & 当前累计后坐角
    private double swayPhase = 0.0;
    private double recoilAngleDeg = 0.0;
    // ===== 视觉效果 =====
    private Entity shootLine = null;              // 射击线条实体

    public PlayerShooting(Player player) {
        this.player = player;
    }

    // 不侵入 PixelGameApp把“网络发送器”塞进来。
    public interface ShotReporter {
        void onShot(double ox, double oy, double dx, double dy, double range, int damage, long ts);
    }
    private ShotReporter reporter;
    public void setShotReporter(ShotReporter r) { this.reporter = r; }

    /**
     * 每帧更新 - 处理射击逻辑和效果
     */
    public void update(double tpf) {
        time += tpf;

        // 后坐角恢复（不开火时逐渐回零）
        if (recoilAngleDeg > 0) {
            recoilAngleDeg = Math.max(0, recoilAngleDeg - RECOIL_RECOVER_DEG_PER_SEC * tpf);
        }
        // 摆动相位推进（持续轻微抖动）
        swayPhase += SWAY_SPEED * tpf;

        if (isShooting && time >= TIME_BETWEEN_BULLETS) {
            performShoot();
        }

        if (time >= EFFECTS_DISPLAY_TIME) {
            hideShootEffects();
        }
    }


    /**
     * 开始射击（由输入系统调用）
     */
    public void startShooting() {
        isShooting = true;

        if (time < TIME_BETWEEN_BULLETS) time = TIME_BETWEEN_BULLETS;

        System.out.println("[Shoot] start by " + player.hashCode());
    }

    /**
     * 停止射击（由输入系统调用）
     */
    public void stopShooting() {
        isShooting = false;
        hideShootEffects();
    }

    /**
     * 执行射击 - 核心射击逻辑
     */
    private void performShoot() {
        time = 0.0;

        Point2D shootOrigin = getShootOrigin();

        // 基础角：朝右0°，朝左180°
        double baseDeg = player.getFacingRight() ? 0.0 : 180.0;
        // 轻微上下摆动
        double swayDeg  = Math.sin(swayPhase) * SWAY_AMPL_DEG;
        // 每枪一些随机散布
        double noiseDeg = ThreadLocalRandom.current().nextDouble(-SPREAD_NOISE_DEG, SPREAD_NOISE_DEG);
        // 累计后坐角 + 摆动 + 噪声（“上抬”为正）
        double upDeg = recoilAngleDeg + swayDeg + noiseDeg;
        // 屏幕Y向下为正：朝右时想“上抬”需减角，朝左则加角
        double finalDeg = baseDeg + (player.getFacingRight() ? -upDeg : +upDeg);
        double rad = Math.toRadians(finalDeg);
        Point2D shootDirection = new Point2D(Math.cos(rad), Math.sin(rad)).normalize();

        Point2D rayEnd = shootOrigin.add(shootDirection.multiply(SHOOT_RANGE));

        RaycastResult result = getPhysicsWorld().raycast(shootOrigin, rayEnd);

        Point2D hitPoint = rayEnd;   // 先默认打空到射线终点
        Entity hitEntity = null;
        System.out.println("[Shoot] ray " + shootOrigin + " -> " + rayEnd);
        // FXGL 的 RaycastResult 直接给 Optional<Point2D> / Optional<Entity>
        if (result.getPoint().isPresent()) {
            hitPoint = result.getPoint().get();

        }
        if (result.getEntity().isPresent()) {
            hitEntity = result.getEntity().get();
            System.out.println("[Shoot] firstHit type=" + hitEntity.getType());
            // 命中玩家（避免自伤）
            if (hitEntity.getType() == GameType.PLAYER && hitEntity != player.getEntity()) {
                dealDamageToPlayer(hitEntity);
            }

            System.out.println("射击命中: " + hitEntity.getType() + " 位置: " + hitPoint);
        } else {
            System.out.println("射击未命中");
        }

        showShootEffects(shootOrigin, hitPoint);

        // 连发后坐角累积（上限保护）+ 给角色一个小反冲
        recoilAngleDeg = Math.min(RECOIL_MAX_DEG, recoilAngleDeg + RECOIL_KICK_DEG);
        applyRecoilToPlayer();

        //末尾“报送一次射击
        if (reporter != null) {
            reporter.onShot(
                    shootOrigin.getX(), shootOrigin.getY(),
                    shootDirection.getX(), shootDirection.getY(),
                    SHOOT_RANGE, (int)DAMAGE, System.currentTimeMillis()   // [NEW]
            );
        }
    }

    /**
     * 获取射击起点（武器位置）
     */
    private Point2D getShootOrigin() {
        Entity e = player.getEntity();
        double offsetX = player.getFacingRight() ? MUZZLE_RIGHT_X : -MUZZLE_LEFT_X;
        double offsetY = MUZZLE_Y;
        return new Point2D(
                e.getX() + e.getWidth()  / 2.0 + offsetX,
                e.getY() + e.getHeight() / 2.0 + offsetY
        );
    }

    /**
     * 获取射击方向
     */
    private Point2D getShootDirection() {
        return new Point2D(player.getFacingRight() ? 1 : -1, 0);
    }

    /**
     * 对玩家造成伤害
     */
    private void dealDamageToPlayer(Entity targetEntity) {
        if (targetEntity == player.getEntity()) return;  // 防自伤

        // 从实体属性拿回 Player 引用（你在 Player.createEntity() 已 setProperty("playerRef", this)）
        Object ref = targetEntity.getProperties().getObject("playerRef");
        if (!(ref instanceof Player target)) {
            System.out.println("[Hit] target has no playerRef");
            return;
        }

        double dir = player.getFacingRight() ? +1 : -1;     // 向右推正，向左推负
        target.applyHit((int) DAMAGE, dir * KB_X, 0);       // ★ 只做水平击退
        System.out.printf("[Hit] victim=%s hp=%d/%d knock=(%.0f,%.0f)%n",
                target.hashCode(), target.getHealth().getHp(), target.getHealth().getMaxHp(),
                dir * KB_X, 0.0);                           // ★ 日志也改成 0
    }

    /**
     * 显示射击效果（射线）
     */
    private void showShootEffects(Point2D start, Point2D end) {
        hideShootEffects(); // 先清除之前的效果

        // 创建射线视觉效果
        Line shootingLine = new Line();
        shootingLine.setStartX(0);
        shootingLine.setStartY(0);
        shootingLine.setEndX(end.getX() - start.getX());
        shootingLine.setEndY(end.getY() - start.getY());
        shootingLine.setStroke(Color.ORANGE);
        shootingLine.setStrokeWidth(3);
        shootingLine.setOpacity(0.8);

        // 创建实体显示射线
        shootLine = entityBuilder()
                .at(start.getX(), start.getY())
                .view(shootingLine)
                .zIndex(2000) // 高层级显示
                .buildAndAttach();


    }

    /**
     * 隐藏射击效果
     */
    private void hideShootEffects() {
        if (shootLine != null && shootLine.isActive()) {
            shootLine.removeFromWorld();
            shootLine = null;
        }
    }
    /** 对玩家施加小后坐力（速度增量） */
    private void applyRecoilToPlayer() {
        if (player.getPhysics() == null) return;
        double kickX = player.getFacingRight() ? -RECOIL_KNOCKBACK_VX : RECOIL_KNOCKBACK_VX;
        player.getPhysics().setVelocityX(player.getPhysics().getVelocityX() + kickX);
        player.getPhysics().setVelocityY(player.getPhysics().getVelocityY() - RECOIL_KNOCKBACK_UP);
    }
    // ===== Getter方法 =====
    public boolean isShooting() {
        return isShooting;
    }

    public double getTimeBetweenBullets() {
        return TIME_BETWEEN_BULLETS;
    }
}