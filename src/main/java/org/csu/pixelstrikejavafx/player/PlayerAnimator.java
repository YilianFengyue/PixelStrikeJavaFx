package org.csu.pixelstrikejavafx.player;

import com.almasb.fxgl.texture.AnimationChannel;
import com.almasb.fxgl.texture.AnimatedTexture;
import javafx.util.Duration;

import static com.almasb.fxgl.dsl.FXGL.*;
import javafx.scene.transform.Scale;

/**
 * 角色动画系统 - 监听Player状态，切换对应动画
 * 支持：idle, walk, run, attack, die
 */
public class PlayerAnimator {

    private final Player player;
    private AnimatedTexture animatedTexture;

    // 动画通道
    private AnimationChannel idleAnimation;
    private AnimationChannel walkAnimation;
    private AnimationChannel runAnimation;
    private AnimationChannel attackAnimation;
    private AnimationChannel dieAnimation;
    private AnimationChannel attackBeginAnimation;
    private AnimationChannel attackIdleAnimation;
    private AnimationChannel attackEndAnimation;

    private String currentAnimationName = "";
    private boolean animationLoaded = false;

    private Scale flip;   // 新增：专门控制朝向的变换

    public PlayerAnimator(Player player) {
        this.player = player;
        setupAnimations();
    }

    private void setupAnimations() {
        try {
            // idle动画：3000x200，15帧，每帧200x200
            idleAnimation = new AnimationChannel(
                    image("ash_idle1.png"), 81, 200, 200,
                    Duration.seconds(2.7), 0, 80
            );

            // walk动画：2800x200，14帧，每帧200x200
            walkAnimation = new AnimationChannel(
                    image("ash_walk1.png"), 40, 200, 200,
                    Duration.seconds(1.333), 0, 39
            );

            // run动画：复用walk但播放更快
            runAnimation = new AnimationChannel(
                    image("ash_walk1.png"), 81, 200, 200,
                    Duration.seconds(0.683), 0, 39
            );


            try {
                attackBeginAnimation = new AnimationChannel(
                        image("ash_attack1.png"), 41, 200, 200,
                        Duration.seconds(0.3), 0, 8    // begin: 帧0-9
                );
                attackIdleAnimation = new AnimationChannel(
                        image("ash_attack1.png"), 41, 200, 200,
                        Duration.seconds(0.25),10 , 14  // idle: 帧4-12 (循环)
                );
                attackEndAnimation = new AnimationChannel(
                        image("ash_attack1.png"), 41, 200, 200,
                        Duration.seconds(0.8333), 15, 39  // end: 帧13-20
                );
            } catch (Exception e) {
                attackBeginAnimation = attackIdleAnimation = attackEndAnimation = idleAnimation;
            }
            // die动画：假设有die精灵图（如果没有先用idle的最后一帧）
            try {
                dieAnimation = new AnimationChannel(
                        image("ash_die.png"), 30, 200, 200,
                        Duration.seconds(1.0), 0, 29
                );
            } catch (Exception e) {
                // 没有die图就用idle的第一帧当作"倒地"
                dieAnimation = new AnimationChannel(
                        image("ash_idle1.png"), 81, 200, 200,
                        Duration.seconds(1.0), 0, 0  // 只播放第一帧
                );
            }

            // 创建动画贴图，默认idle
            animatedTexture = new AnimatedTexture(idleAnimation);
            animatedTexture.loop();
            // ✅ 关键：以精灵中心为支点
            flip = new Scale(1, 1, 120, 100); // 200x200 -> pivotX=100, pivotY=100
            animatedTexture.getTransforms().add(flip);
            currentAnimationName = "idle";
            animationLoaded = true;

            System.out.println("PlayerAnimator: 动画系统初始化成功");

        } catch (Exception e) {
            System.err.println("PlayerAnimator: 动画加载失败 - " + e.getMessage());
            animationLoaded = false;
        }
    }

    /**
     * 每帧更新 - 根据Player状态切换动画
     */
    public void update() {
        if (!animationLoaded || animatedTexture == null) return;

        String newAnimationName = determineAnimation();

        // 只在动画改变时才切换
        if (!newAnimationName.equals(currentAnimationName)) {
            currentAnimationName = newAnimationName;
            switchAnimation(newAnimationName);
        }

        // 处理左右翻转
        updateFacing();

    }

    private String determineAnimation() {
        Player.State state = player.getState();

        // 优先级：死亡 > 射击 > 移动
        switch (state) {
            case DIE:
                return "die";
            case SHOOTING:
                switch (player.getAttackPhase()) {
                    case BEGIN: return "attack_begin";
                    case IDLE: return "attack_idle";
                    case END: return "attack_end";
                    default: return "attack_begin";
                }
            case WALK:
                return "walk";
            case RUN:
                return "run";
            case IDLE:
            case JUMP:
            case FALL:
            case DOUBLE_JUMP:
            default:
                return "idle";
        }
    }

    private void switchAnimation(String animName) {
        AnimationChannel channel;
        boolean shouldLoop = true;

        switch (animName) {
            case "idle":
                channel = idleAnimation;
                break;
            case "walk":
                channel = walkAnimation;
                break;
            case "run":
                channel = runAnimation;
                break;

            case "attack_begin":
                channel = attackBeginAnimation;
                shouldLoop = false;
                break;
            case "attack_idle":
                channel = attackIdleAnimation;
                shouldLoop = true;  // 循环播放
                break;
            case "attack_end":
                channel = attackEndAnimation;
                shouldLoop = false;
                break;
            case "die":
                channel = dieAnimation;
                shouldLoop = false; // 死亡动画只播放一次
                break;
            default:
                channel = idleAnimation;
                break;
        }

        if (shouldLoop) {
            animatedTexture.loopAnimationChannel(channel);
        } else {
            animatedTexture.playAnimationChannel(channel);
        }

        System.out.println("切换动画: " + animName);
    }

    private void updateFacing() {
        if (player.getEntity() == null) return;
        flip.setX(player.getFacingRight() ? 1 : -1);
//        // 根据Player的朝向翻转角色
//        boolean facingRight = player.getFacingRight();
//        double scaleX = facingRight ? 1.0 : -1.0;
//        player.getEntity().setScaleX(scaleX);
    }

    /**
     * 获取动画贴图 - 供Player创建Entity时使用
     */
    public AnimatedTexture getAnimatedTexture() {
        return animatedTexture;
    }

    /**
     * 检查动画是否加载成功
     */
    public boolean isAnimationLoaded() {
        return animationLoaded;
    }

    /**
     * 获取当前播放的动画名称
     */
    public String getCurrentAnimationName() {
        return currentAnimationName;
    }
}