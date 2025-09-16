package org.csu.pixelstrikejavafx.game.world;

import com.almasb.fxgl.app.scene.Viewport;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.physics.PhysicsComponent;
import javafx.geometry.Point2D;

/**
 * 弹簧-阻尼平滑相机：
 * 拥有边界限制、垂直偏移、视域死区、基于平滑速度的动态变焦，以及基于物理的弹簧跟随系统。
 */
public final class CameraFollow {
    private final Viewport vp;
    private final double mapW, mapH;
    private Entity target;

    private final double baseWindowW, baseWindowH;
    private double viewW, viewH;

    // --- 弹簧-阻尼系统参数 ---
    private double stiffness = 12.0; // 弹簧劲度系数。值越高，跟随越紧密。推荐 5.0 ~ 20.0
    private double damping = 8.0;   // 阻尼系数。防止相机抖动。推荐设为 2 * sqrt(stiffness) 附近
    private Point2D cameraVelocity = Point2D.ZERO; // 追踪相机自身的速度

    // --- 视觉偏移与死区 (参数不变) ---
    private double verticalOffset = 100.0;
    private final double deadZoneWidth = 450.0;
    private final double deadZoneHeight = 300.0;

    // --- 动态变焦参数 ---
    private final double maxZoom = 0.90;
    private final double minZoom = 0.75;
    private final double zoomSmoothness = 0.04;
    private final double maxSpeedForZoom = 800.0;

    // 用于平滑处理速度，解决跳跃最高点变焦问题
    private double smoothedSpeed = 0.0;
    private final double speedSmoothFactor = 0.1; // 速度变化的平滑系数

    public CameraFollow(Viewport vp, double mapW, double mapH, double viewW, double viewH, double baseWindowW, double baseWindowH) {
        this.vp = vp;
        this.mapW = mapW;
        this.mapH = mapH;
        this.viewW = viewW;
        this.viewH = viewH;
        this.baseWindowW = baseWindowW;
        this.baseWindowH = baseWindowH;
    }

    public void setTarget(Entity target) {
        this.target = target;
        if (target != null) {
            // 初始定位
            double tx = target.getX() + target.getWidth() / 2.0 - viewW / 2.0;
            double ty = target.getY() + target.getHeight() / 2.0 - viewH / 2.0 + verticalOffset;
            tx = Math.max(0, Math.min(tx, mapW - viewW));
            ty = Math.max(0, Math.min(ty, mapH - viewH));
            vp.setX(tx);
            vp.setY(ty);
            cameraVelocity = Point2D.ZERO; // 重置速度
        }
    }

    public void update() {
        if (target == null) return;

        // --- 基于“平滑速度”的动态变焦 ---
        PhysicsComponent physics = target.getComponent(PhysicsComponent.class);
        double speedX = Math.abs(physics.getVelocityX());
        double speedY = Math.abs(physics.getVelocityY());
        double effectiveSpeed = Math.max(speedX, speedY);

        // 使用平滑后的速度，而不是瞬时速度
        smoothedSpeed = smoothedSpeed + (effectiveSpeed - smoothedSpeed) * speedSmoothFactor;

        double speedRatio = Math.min(1.0, smoothedSpeed / maxSpeedForZoom);
        double targetZoom = maxZoom - (maxZoom - minZoom) * speedRatio;

        double currentZoom = vp.getZoom();
        double newZoom = currentZoom + (targetZoom - currentZoom) * zoomSmoothness;
        vp.setZoom(newZoom);

        // --- 实时重新计算视口尺寸 ---
        this.viewW = baseWindowW / newZoom;
        this.viewH = baseWindowH / newZoom;

        // --- 计算目标相机位置 ---
        double targetCenterX = target.getRightX() - target.getWidth() / 2;
        double targetCenterY = target.getBottomY() - target.getHeight() / 2;

        // 相机死区
        double deadZoneLeft = vp.getX() + (this.viewW - deadZoneWidth) / 2;
        double deadZoneRight = vp.getX() + (this.viewW + deadZoneWidth) / 2;
        double deadZoneTop = vp.getY() + (this.viewH - deadZoneHeight) / 2 - verticalOffset;
        double deadZoneBottom = vp.getY() + (this.viewH + deadZoneHeight) / 2 - verticalOffset;

        double targetCamX = vp.getX();
        double targetCamY = vp.getY();

        if (targetCenterX < deadZoneLeft) {
            targetCamX = targetCenterX - (this.viewW - deadZoneWidth) / 2;
        } else if (targetCenterX > deadZoneRight) {
            targetCamX = targetCenterX - (this.viewW + deadZoneWidth) / 2;
        }

        if (targetCenterY < deadZoneTop) {
            targetCamY = targetCenterY - (this.viewH - deadZoneHeight) / 2 + verticalOffset;
        } else if (targetCenterY > deadZoneBottom) {
            targetCamY = targetCenterY - (this.viewH + deadZoneHeight) / 2 + verticalOffset;
        }

        // --- 使用弹簧-阻尼系统更新相机位置 ---
        Point2D currentPos = new Point2D(vp.getX(), vp.getY());
        Point2D targetPos = new Point2D(targetCamX, targetCamY);

        // 计算弹簧力和阻尼力
        Point2D springForce = targetPos.subtract(currentPos).multiply(stiffness);
        Point2D dampingForce = cameraVelocity.multiply(-damping);
        Point2D totalForce = springForce.add(dampingForce);

        // 根据力更新速度和位置 (这里假设 tpf 约为 1/60 秒)
        double tpf = 1.0 / 60.0;
        cameraVelocity = cameraVelocity.add(totalForce.multiply(tpf));
        Point2D newPos = currentPos.add(cameraVelocity.multiply(tpf));

        // --- 边界限制 ---
        double finalX = Math.max(0, Math.min(newPos.getX(), mapW - this.viewW));
        double finalY = Math.max(0, Math.min(newPos.getY(), mapH - this.viewH));

        // 如果撞到边界，则将该方向的速度清零，防止“粘”在墙上
        if (newPos.getX() != finalX) {
            cameraVelocity = new Point2D(0, cameraVelocity.getY());
        }
        if (newPos.getY() != finalY) {
            cameraVelocity = new Point2D(cameraVelocity.getX(), 0);
        }

        vp.setX(finalX);
        vp.setY(finalY);
    }
}