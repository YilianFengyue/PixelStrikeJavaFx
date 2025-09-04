package org.csu.pixelstrikejavafx.camera;

import com.almasb.fxgl.app.scene.Viewport;
import com.almasb.fxgl.entity.Entity;

/** 简洁平滑跟随相机：有边界限制和线性插值 */
public final class CameraFollow {
    private final Viewport vp;
    private final double mapW, mapH, viewW, viewH;
    private Entity target;

    private double smooth = 0.12;     // 平滑系数 0.08~0.16 之间可调

    public CameraFollow(Viewport vp, double mapW, double mapH, double viewW, double viewH) {
        this.vp = vp;
        this.mapW = mapW;
        this.mapH = mapH;
        this.viewW = viewW;
        this.viewH = viewH;
    }

    public void setTarget(Entity target) {
        this.target = target;
        if (target != null) {
            double tx = target.getX() + target.getWidth() / 2.0 - viewW / 2.0;
            double ty = target.getY() + target.getHeight() / 2.0 - viewH / 2.0;

            // 边界限制
            tx = Math.max(0, Math.min(tx, mapW - viewW));
            ty = Math.max(0, Math.min(ty, mapH - viewH));

            vp.setX(tx);
            vp.setY(ty);
        }

    }

    public void update() {
        if (target == null) return;

        double tx = target.getX() + target.getWidth() / 2.0 - viewW / 2.0;
        double ty = target.getY() + target.getHeight() / 2.0 - viewH / 2.0;

        // 边界限制
        tx = Math.max(0, Math.min(tx, mapW - viewW));
        ty = Math.max(0, Math.min(ty, mapH - viewH));

        // 线性插值（平滑跟随）
        vp.setX(vp.getX() + (tx - vp.getX()) * smooth);
        vp.setY(vp.getY() + (ty - vp.getY()) * smooth);
    }
}
