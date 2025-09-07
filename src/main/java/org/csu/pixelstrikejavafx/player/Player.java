package org.csu.pixelstrikejavafx.player;

import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.components.CollidableComponent;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import lombok.Getter;
import org.csu.pixelstrikejavafx.core.GameConfig;
import org.csu.pixelstrikejavafx.core.GameType;
import org.csu.pixelstrikejavafx.net.dto.PlayerState;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;

public class Player {

    // 状态枚举，用于客户端动画预测
    public enum LocalState {
        IDLE, RUN, JUMP, FALL, SHOOT, DEAD, HIT
    }

    @Getter
    private Entity entity;
    private PlayerAnimator animator;

    // --- 客户端状态 (用于预测) ---
    private LocalState localState = LocalState.IDLE;
    private boolean isFacingRight = true;
    private boolean onGround = true;

    // --- 网络同步 (用于修正) ---
    private Point2D networkTargetPosition;
    private static final double INTERPOLATION_FACTOR = 15.0; // 平滑插值系数

    // -- 视觉修正常量 --
    private static final double VISUAL_X_OFFSET = -80.0;

    public Player(double spawnX, double spawnY) {
        double renderY = spawnY + GameConfig.Y_OFFSET;
        this.networkTargetPosition = new Point2D(spawnX + VISUAL_X_OFFSET, renderY);
        entity = entityBuilder()
                .type(GameType.PLAYER)
                .at(spawnX, renderY)
                .viewWithBBox(new Rectangle(86, 160, Color.TRANSPARENT))
                .with(new CollidableComponent(false))
                .zIndex(1000)
                .buildAndAttach();
        initAnimator();
    }

    private void initAnimator() {
        animator = new PlayerAnimator(this);
        if (animator.isAnimationLoaded()) {
            entity.getViewComponent().addChild(animator.getAnimatedTexture());
        }
    }

    /**
     * 由网络线程调用，用于更新目标位置和修正状态
     */
    public void networkUpdate(PlayerState serverState) {
        // 坐标转换

        double renderY = serverState.getY() + GameConfig.Y_OFFSET;
        // 1. 更新网络位置目标
        this.networkTargetPosition = new Point2D(serverState.getX() + VISUAL_X_OFFSET, renderY);

        // 2. 服务器是朝向、是否在地的唯一权威
        this.isFacingRight = serverState.isFacingRight();

        // this.onGround = serverState.getY() >= 499.0; // 用一个近似值判断
        this.onGround = renderY >= (GameConfig.MAP_H - 211 - 10);

        // 3. 用服务器的权威动作来修正客户端的预测
        // 如果服务器说你在跳/下落/射击/死亡，客户端必须服从
        switch(serverState.getCurrentAction()) {
            case JUMP:
                this.localState = LocalState.JUMP;
                break;
            case FALL:
                this.localState = LocalState.FALL;
                break;
            case SHOOT:
                // 只有当本地不在跳跃时，才接受射击状态，避免空中射击动画覆盖跳跃
                if (this.localState != LocalState.JUMP && this.localState != LocalState.FALL) {
                    this.localState = LocalState.SHOOT;
                }
                break;
            case DEAD:
                this.localState = LocalState.DEAD;
                break;
            case HIT:
                this.localState = LocalState.HIT;
                break;
            // 对于IDLE和RUN，我们更相信客户端的即时输入，所以不在这里强制修正
            // 除非本地状态是跳跃/下落，但服务器说已经在IDLE了（说明已落地）
            case IDLE:
            case RUN:
                if (this.localState == LocalState.JUMP || this.localState == LocalState.FALL) {
                    this.localState = LocalState.IDLE;
                }
                break;
        }
    }

    /**
     * 由客户端主循环 (onUpdate) 调用，负责平滑移动和动画更新
     */
    public void clientUpdate(double tpf) {
        // 1. 平滑移动到网络目标位置
        Point2D currentPosition = entity.getPosition();
        Point2D newPosition = lerp(currentPosition, networkTargetPosition, tpf * INTERPOLATION_FACTOR);
        entity.setPosition(newPosition);

        // 2. 根据客户端预测的状态来更新动画
        if (animator != null) {
            animator.update(this.localState, this.isFacingRight);
        }
    }

    /**
     * 【核心修正】：一个统一的、带优先级的状态更新方法
     */
    public void updateLocalState(boolean isMoving, boolean isShooting) {
        // 优先级: JUMP/FALL > SHOOT > RUN > IDLE
        // 空中状态由服务器修正，这里不处理
        if (localState == LocalState.JUMP || localState == LocalState.FALL) {
            return;
        }

        if (isShooting) {
            localState = LocalState.SHOOT;
        } else if (isMoving) {
            localState = LocalState.RUN;
        } else {
            localState = LocalState.IDLE;
        }
    }

    private Point2D lerp(Point2D a, Point2D b, double alpha) {
        double t = Math.max(0.0, Math.min(1.0, alpha));
        double newX = a.getX() + (b.getX() - a.getX()) * t;
        double newY = a.getY() + (b.getY() - a.getY()) * t;
        return new Point2D(newX, newY);
    }

}