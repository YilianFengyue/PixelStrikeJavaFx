// main/java/org/csu/pixelstrikejavafx/game/player/PlayerAnimator.java
package org.csu.pixelstrikejavafx.game.player;

import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.texture.AnimatedTexture;
import com.almasb.fxgl.texture.AnimationChannel;
import javafx.scene.transform.Scale;
import javafx.util.Duration;

import static com.almasb.fxgl.dsl.FXGL.image;
import static org.csu.pixelstrikejavafx.game.player.RemoteAvatar.getCharacterString;

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
    private final String characterName;

    private Scale flip;   // 新增：专门控制朝向的变换

    public PlayerAnimator(Player player, int characterId) {
        this.player = player;
        this.characterName = getCharacterFolderName(characterId);
        setupAnimations();
    }

    private String getCharacterFolderName(int characterId) {
        return getCharacterString(characterId);
    }

    private void setupAnimations() {
        try {
            idleAnimation = new AnimationChannel(
                    image("characters/" + characterName + "/" + characterName + "_idle.png"), 15, 200, 200,
                    Duration.seconds(2.0), 0, 14
            );

            walkAnimation = new AnimationChannel(
                    image("characters/" + characterName + "/" + characterName + "_walk.png"), 14, 200, 200,
                    Duration.seconds(1.2), 0, 13
            );

            runAnimation = new AnimationChannel(
                    image("characters/" + characterName + "/" + characterName + "_walk.png"), 14, 200, 200,
                    Duration.seconds(0.5), 0, 13
            );

            // --- 核心修复：移除多余的、硬编码的 'ash' 动画加载块 ---
            // 之前的错误代码已被删除，现在所有角色的攻击和死亡动画都会从正确的动态路径加载。
            attackBeginAnimation = new AnimationChannel(
                    image("characters/" + characterName + "/" + characterName + "_attack.png"), 21, 200, 200,
                    Duration.seconds(0.2), 0, 3
            );
            attackIdleAnimation = new AnimationChannel(
                    image("characters/" + characterName + "/" + characterName + "_attack.png"), 21, 200, 200,
                    Duration.seconds(0.45), 4, 12
            );
            attackEndAnimation = new AnimationChannel(
                    image("characters/" + characterName + "/" + characterName + "_attack.png"), 21, 200, 200,
                    Duration.seconds(0.4), 13, 20
            );

            dieAnimation = new AnimationChannel(
                    image("characters/" + characterName + "/" + characterName + "_die.png"), 8, 200, 200,
                    Duration.seconds(1.5), 0, 7
            );

            // 创建动画贴图，默认idle
            animatedTexture = new AnimatedTexture(idleAnimation);
            animatedTexture.loop();
            // ✅ 关键：以精灵中心为支点
            flip = new Scale(1, 1, 120, 100); // 200x200 -> pivotX=100, pivotY=100
            animatedTexture.getTransforms().add(flip);
            currentAnimationName = "idle";
            animationLoaded = true;

            System.out.println("PlayerAnimator: 动画系统初始化成功 for character: " + characterName);

        } catch (Exception e) {
            System.err.println("PlayerAnimator: 动画加载失败 for " + characterName + " - " + e.getMessage());
            // 如果任何一个动画文件缺失，这里可以设置回退到默认角色 'ash'
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
                // --- 核心修复：播放死亡动画后，延迟隐藏实体 ---
                FXGL.getGameTimer().runOnceAfter(() -> {
                    if (player.getEntity() != null) {
                        player.getEntity().setVisible(false);
                    }
                }, Duration.seconds(1.5)); // 延迟时间应与死亡动画时长匹配
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