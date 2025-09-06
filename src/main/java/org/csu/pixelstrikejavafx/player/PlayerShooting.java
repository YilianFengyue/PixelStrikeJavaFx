package org.csu.pixelstrikejavafx.player;

import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.physics.RaycastResult;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import org.csu.pixelstrikejavafx.core.GameType;

import static com.almasb.fxgl.dsl.FXGL.*;

/**
 * 射击系统 - 基于射线检测的射击逻辑
 * 参考Unity的MyPlayerShooting设计
 */
public class PlayerShooting {

    // ===== 射击参数配置 =====
    private static final double TIME_BETWEEN_BULLETS = 0.15;  // 射击间隔(秒)
    private static final double EFFECTS_DISPLAY_TIME = 0.2;   // 效果显示时间
    private static final double SHOOT_RANGE = 800.0;          // 射击距离
    private static final double DAMAGE = 25.0;                // 伤害值

    // ===== 运行时状态 =====
    private double time = 0.0;                    // 计时器
    private boolean isShooting = false;           // 是否正在射击
    private final Player player;                  // 关联的玩家

    // ===== 视觉效果 =====
    private Entity shootLine = null;              // 射击线条实体

    public PlayerShooting(Player player) {
        this.player = player;
    }

    /**
     * 每帧更新 - 处理射击逻辑和效果
     */
    public void update(double tpf) {
        time += tpf;

        if (isShooting && time >= TIME_BETWEEN_BULLETS) {
            performShoot();
        }

        // 原来是 TIME_BETWEEN_BULLETS * EFFECTS_DISPLAY_TIME -> 0.03s，过短
        if (time >= EFFECTS_DISPLAY_TIME) {
            hideShootEffects();
        }
    }


    /**
     * 开始射击（由输入系统调用）
     */
    public void startShooting() {
        isShooting = true;
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
        Point2D shootDirection = getShootDirection();
        Point2D rayEnd = shootOrigin.add(shootDirection.multiply(SHOOT_RANGE));

        RaycastResult result = getPhysicsWorld().raycast(shootOrigin, rayEnd);

        Point2D hitPoint = rayEnd;   // 先默认打空到射线终点
        Entity hitEntity = null;

        // FXGL 的 RaycastResult 直接给 Optional<Point2D> / Optional<Entity>
        if (result.getPoint().isPresent()) {
            hitPoint = result.getPoint().get();
        }
        if (result.getEntity().isPresent()) {
            hitEntity = result.getEntity().get();

            // 命中玩家（避免自伤）
            if (hitEntity.getType() == GameType.PLAYER && hitEntity != player.getEntity()) {
                dealDamageToPlayer(hitEntity);
            }

            System.out.println("射击命中: " + hitEntity.getType() + " 位置: " + hitPoint);
        } else {
            System.out.println("射击未命中");
        }

        showShootEffects(shootOrigin, hitPoint);
    }

    /**
     * 获取射击起点（武器位置）
     */
    private Point2D getShootOrigin() {
        Entity playerEntity = player.getEntity();
        double offsetX = player.getFacingRight() ? 120 : 0; // 武器偏移
        double offsetY = 60; // 略微向上

        return new Point2D(
                playerEntity.getX() + playerEntity.getWidth()/2 + offsetX,
                playerEntity.getY() + playerEntity.getHeight()/2 + offsetY
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
        // 防止自伤
        if (targetEntity == player.getEntity()) {
            return;
        }

        // TODO: 获取目标玩家的Health组件并造成伤害
        System.out.println("对玩家造成 " + DAMAGE + " 点伤害");

        // 这里需要一个PlayerHealth系统来处理伤害
        // PlayerHealth targetHealth = targetEntity.getComponent(PlayerHealth.class);
        // if (targetHealth != null) {
        //     targetHealth.takeDamage((int)DAMAGE);
        // }
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
        shootingLine.setStroke(Color.YELLOW);
        shootingLine.setStrokeWidth(2);
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

    // ===== Getter方法 =====
    public boolean isShooting() {
        return isShooting;
    }

    public double getTimeBetweenBullets() {
        return TIME_BETWEEN_BULLETS;
    }
}