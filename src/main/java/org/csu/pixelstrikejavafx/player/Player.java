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
import com.almasb.fxgl.entity.components.CollidableComponent;
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
    private static final double HB_OFF_Y = 20; // 垂直下移到脚部
    private static final double HB_W = 86;   // 碰撞体宽度
    private static final double HB_H =160;       // 碰撞体高度

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

    // 生命组件（极简）
    private final PlayerHealth health = new PlayerHealth(this);
    //角色射击组件
    private  PlayerShooting shootingSys;

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


    // 攻击分段时长（毫秒）——方便以后微调
    private static final int ATTACK_BEGIN_MS = 200;
    private static final int ATTACK_END_MS   = 400;

    // 松手排队：在 BEGIN 阶段松手不马上切 END，等 BEGIN 播完再根据此标志决定
    private boolean stopQueued = false;

    // 松手后等待下一次按下的窗口（超过它才收枪 END）
    private static final int HOLSTER_DELAY_MS = 120;   // 90~180ms 自行微调手感
    private long lastShootUpTime = 0;                  // 最近一次松手时刻

    private static final int RAISE_SKIP_THRESHOLD_MS = 220; // 刚收枪后很快再按，直接从 idle 开枪
    private long lastShootEndTime = 0;                      // 最近一次 END 播完时间

    public Player(double spawnX, double spawnY) {
        createEntity(spawnX, spawnY);
        initAnimator(); // 动画状态
        shootingSys = new PlayerShooting(this);   // 角色射击
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

        // 让命中时可以从实体回取 Player
        entity.setProperty("playerRef", this);

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

        // 7) 先推进攻段机（这里可能把 BEGIN→IDLE / IDLE→END 或把 shooting=false）
        updateAttackPhase();

        // 8) 再更新动画：此时 determineAnimation() 能立刻拿到 *最新* 的 attackPhase
        if (animator != null) {
            animator.update();
        }

        // 9) 射线/冷却照旧
        if (shootingSys != null) {
            shootingSys.update(tpf);
        }
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
        if (dead) return;

        long now = System.currentTimeMillis();
        shooting = true;
        stopQueued = false;

        boolean skipBegin = (now - lastShootEndTime) <= RAISE_SKIP_THRESHOLD_MS;

        switch (attackPhase) {
            case BEGIN:
            case IDLE:
                // 已在拿枪或开枪，不重置，防止卡在 begin 看不到 idle
                break;

            case END:
                // 收枪过程/刚收完：可直接开枪覆盖 end
                if (skipBegin || (now - attackPhaseStartTime) < ATTACK_END_MS) {
                    attackPhase = AttackPhase.IDLE;
                } else {
                    attackPhase = AttackPhase.BEGIN;
                }
                attackPhaseStartTime = now;
                break;

            default:
                // 初始：根据阈值决定是否跳过 begin
                attackPhase = skipBegin ? AttackPhase.IDLE : AttackPhase.BEGIN;
                attackPhaseStartTime = now;
                break;
        }

        lastShotTime = now;
        if (shootingSys != null) shootingSys.startShooting();
    }


    /** 停止射击 */
    public void stopShooting() {
        stopQueued = true;                         // 仅标记“松手”
        lastShootUpTime = System.currentTimeMillis();   // ★ 记录松手时间

        // 不在这里切 END；是否收枪由 updateAttackPhase() + 窗口统一决定
        if (shootingSys != null) {
            shootingSys.stopShooting();
        }
    }

    /** 角色死亡 */
    public void die() {
//        dead = true;
//        physics.setVelocityX(0);
//        physics.setVelocityY(0);
        onDeath();
    }

    /** 复活 */
    public void revive() {
        health.reviveFull();
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

        long now = System.currentTimeMillis();
        long elapsed = now - attackPhaseStartTime;

        switch (attackPhase) {
            case BEGIN:
                if (elapsed >= ATTACK_BEGIN_MS) {
                    // ★ 一定先到开枪（idle）
                    attackPhase = AttackPhase.IDLE;
                    attackPhaseStartTime = now;
                }
                break;


            case IDLE:
                // 只有当“松手” 且 “超过窗口” 才收枪
                if (stopQueued && (now - lastShootUpTime) >= HOLSTER_DELAY_MS) {
                    attackPhase = AttackPhase.END;
                    attackPhaseStartTime = now;
                }
                break;

            case END:
                if (elapsed >= ATTACK_END_MS) {
                    shooting = false;
                    stopQueued = false;
                    lastShootEndTime = now;      // ★ 记录收枪完成
                    attackPhase = AttackPhase.BEGIN;
                }
                break;

            default:
                break;
        }
    }

    public PlayerHealth getHealth() { return health; }

    /** 合并入口：网络/本地命中都调它；先击退再扣血 */
    public void applyHit(int damage, double knockX, double knockY) {
        applyKnockback(knockX, knockY);
        health.takeDamage(damage);
    }

    /** 受击击退：+X 向右 / -X 向左；Y 为向上（正值会抬起） */
    public void applyKnockback(double kx, double ky) {
        if (physics == null) return;
        physics.setVelocityX(physics.getVelocityX() + kx);
        physics.setVelocityY(physics.getVelocityY() - ky);
    }

    /** 受伤回调 —— 预留动画/闪烁/受击硬直（此处不做具体表现） */
    public void onDamaged(int amount) {
        // TODO: 播放受击动画 / 屏幕闪红 / 无敌帧等
    }

    /** 死亡回调 —— 先隐藏与禁用碰撞；动画以后接 */
    public void onDeath() {
        dead = true;

        // 停止运动
        if (physics != null) {
            physics.setVelocityX(0);
            physics.setVelocityY(0);
        }

        // 先简单“消失”：隐藏 + 关闭碰撞（比 removeFromWorld 更安全，不影响相机引用）
        if (entity != null) {
            entity.setVisible(false);
            var coll = entity.getComponentOptional(CollidableComponent.class).orElse(null);
            if (coll != null) coll.setValue(false);
        }

        // TODO: 将来这里切换到“死亡动画”，动画播完再隐藏/移除
    }

    /** 复活回调 —— 恢复可见与碰撞，位置/血量由外部控制 */
    public void onRevived() {
        dead = false;
        if (entity != null) {
            entity.setVisible(true);
            var coll = entity.getComponentOptional(CollidableComponent.class).orElse(null);
            if (coll != null) coll.setValue(true);
        }
    }
    // —— Getter ——
    public Entity getEntity() { return entity; }
    public PhysicsComponent getPhysics() { return physics; }
    public State getState() { return state; }
    public boolean isOnGround() { return onGround; }
    public boolean isRunning() { return running; }
    public AttackPhase getAttackPhase() { return attackPhase; }


    public boolean isShooting() { return shooting; }
    public boolean isDead() { return dead; }

    public PlayerShooting getShootingSys() { return shootingSys; }


    public boolean getFacingRight() {
        return facingRight;
    }
}
