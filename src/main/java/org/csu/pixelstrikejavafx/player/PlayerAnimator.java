package org.csu.pixelstrikejavafx.player;

import com.almasb.fxgl.texture.AnimationChannel;
import com.almasb.fxgl.texture.AnimatedTexture;
import javafx.util.Duration;

import static com.almasb.fxgl.dsl.FXGL.*;

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

    private String currentAnimationName = "";
    private boolean animationLoaded = false;

    public PlayerAnimator(Player player) {
        this.player = player;
        setupAnimations();
    }

    private void setupAnimations() {
        try {
            // idle动画：3000x200，15帧，每帧200x200
            idleAnimation = new AnimationChannel(
                    image("ash_idle.png"), 15, 200, 200,
                    Duration.seconds(2.0), 0, 14
            );

            // walk动画：2800x200，14帧，每帧200x200
            walkAnimation = new AnimationChannel(
                    image("ash_walk.png"), 14, 200, 200,
                    Duration.seconds(1.2), 0, 13
            );

            // run动画：复用walk但播放更快
            runAnimation = new AnimationChannel(
                    image("ash_walk.png"), 14, 200, 200,
                    Duration.seconds(0.7), 0, 13
            );

            // attack动画：假设有attack精灵图（如果没有先用idle）
            try {
                attackAnimation = new AnimationChannel(
                        image("ash_attack.png"), 10, 200, 200,
                        Duration.seconds(0.8), 0, 9
                );
            } catch (Exception e) {
                // 没有attack图就复用idle
                attackAnimation = idleAnimation;
            }

            // die动画：假设有die精灵图（如果没有先用idle的最后一帧）
            try {
                dieAnimation = new AnimationChannel(
                        image("ash_die.png"), 8, 200, 200,
                        Duration.seconds(1.5), 0, 7
                );
            } catch (Exception e) {
                // 没有die图就用idle的第一帧当作"倒地"
                dieAnimation = new AnimationChannel(
                        image("ash_idle.png"), 15, 200, 200,
                        Duration.seconds(1.0), 0, 0  // 只播放第一帧
                );
            }

            // 创建动画贴图，默认idle
            animatedTexture = new AnimatedTexture(idleAnimation);
            animatedTexture.loop();
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
                return "attack";
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
            case "attack":
                channel = attackAnimation;
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

        // 根据Player的朝向翻转角色
        boolean facingRight = player.getFacingRight();
        double scaleX = facingRight ? 1.0 : -1.0;
        player.getEntity().setScaleX(scaleX);
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