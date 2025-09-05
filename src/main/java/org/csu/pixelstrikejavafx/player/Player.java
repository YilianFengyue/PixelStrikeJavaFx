package org.csu.pixelstrikejavafx.player;

import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.components.CollidableComponent;
import com.almasb.fxgl.physics.PhysicsComponent;
import com.almasb.fxgl.physics.box2d.dynamics.BodyType;
import com.almasb.fxgl.physics.box2d.dynamics.FixtureDef;
import com.almasb.fxgl.texture.Texture;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.csu.pixelstrikejavafx.core.GameType;

import static com.almasb.fxgl.dsl.FXGL.*;

/**
 * 角色控制（行走/跑步/跳跃/二段跳），不含动画。
 * 输入仍由外部（GameApp 的 UserAction）调用 start/stop/jump。
 */
public class Player {

    //动画对象
    private PlayerAnimator animator;
    //状态枚举
    public enum State {
        IDLE, WALK, RUN, JUMP, FALL, DOUBLE_JUMP,
        SHOOTING, DIE  // 新增：射击和死亡状态
    }

    public enum AttackPhase {
        BEGIN,    // 拿枪阶段 (帧0-3)
        IDLE,     // 开枪循环 (帧4-12)
        END       // 收枪阶段 (帧13-20)
    }

    // 角色贴图
    private static final double PLAYER_W = 200;  // 匹配精灵图尺寸
    private static final double PLAYER_H = 200;
    // 碰撞体调整常量
    private static final double HB_OFF_X =  80;   // 水平居中偏移
    private static final double HB_OFF_Y = 140;  // 垂直下移到脚部
    private static final double HB_W = 86;       // 碰撞体宽度
    private static final double HB_H = 40;       // 碰撞体高度

    // —— 手感参数（可按需微调）——
    private static final double WALK_SPEED = 200.0;
    private static final double RUN_SPEED  = 400.0;
    private static final double ACCEL      = 400.0;  // 水平加速度
    private static final double JUMP_VY    = 650.0;
    private static final double DJUMP_VY   = 500.0;

    private static final long   DOUBLE_TAP_MS = 300;  // A/D 双击触发跑步

    // 组件
    private Entity entity;
    private PhysicsComponent physics;

    // 运行时状态
    private State  state = State.IDLE;
    private double vxTarget = 0.0;
    private double vxCurrent = 0.0;

    // 新增：动画和战斗状态
    private boolean shooting = false;
    private boolean dead = false;
    private long lastShotTime = 0;
    private static final long SHOT_COOLDOWN = 150; // 射击间隔ms

    private boolean movingLeft  = false;
    private boolean movingRight = false;
    private boolean running     = false;
    private boolean onGround    = false;

    private int jumpsUsed = 0;      // 0/1/2
    private boolean facingRight = true;  // 记录当前朝向

    // 攻击状态
    private AttackPhase attackPhase = AttackPhase.BEGIN;
    private long attackPhaseStartTime = 0;

    private long lastLeftTap  = 0;
    private long lastRightTap = 0;

    public Player(double spawnX, double spawnY) {
        createEntity(spawnX, spawnY);
        initAnimator(); // 动画状态
    }

    private void createEntity(double x, double y) {
        // 1) 视图（优先使用贴图，失败则用矩形）
        Node viewNode;

        Texture tex = null;
        try {
            // 优先读取你的现有资源名；没有则改为 "player.png"
            tex = getAssetLoader().loadTexture("lapu.png");
            tex.setSmooth(false);
            tex.setFitWidth(PLAYER_W);
            tex.setFitHeight(PLAYER_H);
            viewNode = tex;
        } catch (Exception e) {
            Rectangle rect = new Rectangle(PLAYER_W, PLAYER_H);
            rect.setFill(Color.CRIMSON);
            rect.setStroke(Color.BLACK);
            viewNode = rect;
        }

        // 2) 物理
        physics = new PhysicsComponent();
//        physics.setBodyType(BodyType.DYNAMIC);


        FixtureDef fd = new FixtureDef()
                .friction(0.1f)
                .restitution(0f)
                .density(1.0f);
        physics.setFixtureDef(fd);
        // 添加这两行防止旋转和稳定物理
        // ✅ 关键：用 BodyDef 锁定旋转 + 设为 DYNAMIC
        com.almasb.fxgl.physics.box2d.dynamics.BodyDef bd =
                new com.almasb.fxgl.physics.box2d.dynamics.BodyDef();
        bd.setType(BodyType.DYNAMIC);
        bd.setFixedRotation(true);
        physics.setBodyDef(bd);
        // 3) 实体
        entity = entityBuilder()
                .type(GameType.PLAYER)
                .at(x, y)
                .view(viewNode)  // 只设置视图
                .bbox(new com.almasb.fxgl.physics.HitBox(
                        new javafx.geometry.Point2D(HB_OFF_X, HB_OFF_Y),
                        com.almasb.fxgl.physics.BoundingShape.box(HB_W, HB_H)
                ))  // 自定义碰撞体到脚部
                .with(new CollidableComponent(true))
                .with(physics)
                .zIndex(1000)
                .buildAndAttach();

    }

    private void initAnimator() {
        animator = new PlayerAnimator(this);
        if (animator.isAnimationLoaded()) {
            // 如果动画加载成功，替换entity的view
            entity.getViewComponent().clearChildren();
            entity.getViewComponent().addChild(animator.getAnimatedTexture());
        }
    }
    /** 每帧更新：处理水平速度与状态机 */
    public void update(double tpf) {

        // 1) 计算目标速度
        if (movingLeft && !movingRight) {
            vxTarget = running ? -RUN_SPEED : -WALK_SPEED;
        } else if (movingRight && !movingLeft) {
            vxTarget = running ? RUN_SPEED : WALK_SPEED;
        } else {
            vxTarget = 0;
        }

        // 2) 平滑趋近
        double diff = vxTarget - vxCurrent;
        double step = ACCEL * tpf;
        if (Math.abs(diff) > step) {
            vxCurrent += Math.signum(diff) * step;
        } else {
            vxCurrent = vxTarget;
        }
        physics.setVelocityX(vxCurrent);

//        // 3) 状态机
//
//        if (!onGround) {
//            if (vy < -50) {
//                state = (jumpsUsed >= 2) ? State.DOUBLE_JUMP : State.JUMP;
//            } else {
//                state = State.FALL;
//            }
//        } else {
//            if (Math.abs(vxCurrent) < 1) {
//                state = State.IDLE;
//            } else {
//                state = running ? State.RUN : State.WALK;
//            }
//        }
        //死亡和射击
        double vy = physics.getVelocityY();
        if (dead) {
            state = State.DIE;
        } else if (shooting) {
            state = State.SHOOTING;
        } else {
            // 原有的状态机逻辑保持不变
            if (!onGround) {
                if (vy < -50) {
                    state = (jumpsUsed >= 2) ? State.DOUBLE_JUMP : State.JUMP;
                } else {
                    state = State.FALL;
                }
            } else {
                if (Math.abs(vxCurrent) < 1) {
                    state = State.IDLE;
                } else {
                    state = running ? State.RUN : State.WALK;
                }
            }
        }
//        // 4)角色方向
//        if (vxCurrent > 1) {
//            entity.getViewComponent().getChildren().get(0).setScaleX(1);  // 面向右
//        } else if (vxCurrent < -1) {
//            entity.getViewComponent().getChildren().get(0).setScaleX(-1); // 面向左
//        }
        //5)摩擦力
        if (Math.abs(vxTarget) < 10 && Math.abs(vxCurrent) > 10) {
            vxCurrent *= 0.85;  // 松手时快速减速
            physics.setVelocityX(vxCurrent);
        }
        // 6)统一翻转处理 - 放在最后确保每帧都执行
        if (movingRight && !movingLeft) {
            facingRight = true;
        } else if (movingLeft && !movingRight) {
            facingRight = false;
        }

        // 新增：更新动画
        if (animator != null) {
            animator.update();
        }
        updateAttackPhase();

    }

    // —— 供外部（输入系统）调用的接口 ——

    /** 按下 A */
    public void startMoveLeft() {
        long now = System.currentTimeMillis();
        if (now - lastLeftTap < DOUBLE_TAP_MS) running = true;
        lastLeftTap = now;
        movingLeft = true;
    }

    /** 松开 A */
    public void stopMoveLeft() {
        movingLeft = false;
        if (!movingRight) running = false;
    }

    /** 按下 D */
    public void startMoveRight() {
        long now = System.currentTimeMillis();
        if (now - lastRightTap < DOUBLE_TAP_MS) running = true;
        lastRightTap = now;
        movingRight = true;
    }

    /** 松开 D */
    public void stopMoveRight() {
        movingRight = false;
        if (!movingLeft) running = false;
    }

    /** 跳跃/二段跳 */
    public void jump() {
        if (jumpsUsed == 0 && onGround) {
            physics.setVelocityY(-JUMP_VY);
            jumpsUsed = 1;
            onGround = false;
        } else if (jumpsUsed == 1) {
            physics.setVelocityY(-DJUMP_VY);
            jumpsUsed = 2;
        }
    }

    /** GameApp 的碰撞回调里调用 */
    public void setOnGround(boolean onGround) {
        boolean was = this.onGround;
        this.onGround = onGround;
        if (onGround && !was) {
            jumpsUsed = 0;
        }
    }

    /** 调试输出 */
    public void printDebugInfo() {
        System.out.printf(
                "[Player] pos=(%.1f,%.1f) vel=(%.1f,%.1f) state=%s onGround=%s run=%s%n",
                entity.getX(), entity.getY(),
                physics.getVelocityX(), physics.getVelocityY(),
                state, onGround, running
        );
    }
    public void startShooting() {
        if (!dead && System.currentTimeMillis() - lastShotTime > SHOT_COOLDOWN) {
            shooting = true;
            if (attackPhase == AttackPhase.END) {
                attackPhase = AttackPhase.BEGIN;  // 收枪时重新按，回到拿枪
            }
            attackPhaseStartTime = System.currentTimeMillis();
            lastShotTime = System.currentTimeMillis();
        }
    }

    /** 停止射击 */
    public void stopShooting() {
        if (attackPhase == AttackPhase.IDLE) {
            attackPhase = AttackPhase.END;
            attackPhaseStartTime = System.currentTimeMillis();
        }
    }

    /** 角色死亡 */
    public void die() {
        dead = true;
        physics.setVelocityX(0);
        physics.setVelocityY(0);
    }

    /** 复活 */
    public void revive() {
        dead = false;
    }
    /** 重置到某坐标 */
    public void reset(double x, double y) {
        entity.setPosition(x, y);
        physics.setVelocityX(0);
        physics.setVelocityY(0);
        movingLeft = movingRight = running = false;
        onGround = false;
        jumpsUsed = 0;
        vxCurrent = vxTarget = 0;
        state = State.IDLE;
        facingRight = true;  // 新增这行
    }
    private void updateAttackPhase() {
        if (!shooting) return;

        long elapsed = System.currentTimeMillis() - attackPhaseStartTime;

        switch (attackPhase) {
            case BEGIN:
                if (elapsed > 200) {  // 0.2秒后进入循环
                    attackPhase = AttackPhase.IDLE;
                    attackPhaseStartTime = System.currentTimeMillis();
                }
                break;
            case IDLE:
                // 持续循环，直到松开
                break;
            case END:
                if (elapsed > 400) {  // 0.4秒后结束
                    shooting = false;
                    attackPhase = AttackPhase.BEGIN;  // 重置为begin
                }
                break;
        }
    }


    // —— Getter ——
    public Entity getEntity() { return entity; }
    public PhysicsComponent getPhysics() { return physics; }
    public State getState() { return state; }
    public boolean isOnGround() { return onGround; }
    public boolean isRunning() { return running; }
    public AttackPhase getAttackPhase() { return attackPhase; }

    // 新增getter方法
    public boolean isShooting() { return shooting; }
    public boolean isDead() { return dead; }
    // 改为：
    public boolean getFacingRight() {
        return facingRight;
    }
}
