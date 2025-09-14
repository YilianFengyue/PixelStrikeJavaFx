package org.csu.pixelstrikejavafx.game.player;

import com.almasb.fxgl.texture.AnimatedTexture;
import com.almasb.fxgl.texture.AnimationChannel;
import javafx.scene.transform.Scale;
import javafx.util.Duration;
import org.csu.pixelstrikejavafx.content.AnimClip;
import org.csu.pixelstrikejavafx.content.CharacterDef;

import static com.almasb.fxgl.dsl.FXGL.image;

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

    private CharacterDef def;   // 可为空：为空走旧硬编码

    public PlayerAnimator(Player player) {
        this.player = player;
        setupAnimations();
    }

    public PlayerAnimator(Player player, CharacterDef def) {
        this.player = player;
        this.def = def;
        setupAnimations(def); // 使用接收CharacterDef的setup方法
    }

    private void setupAnimations() {
        // 这个方法作为从JSON加载失败时的后备
        try {
            idleAnimation = new AnimationChannel(image("ash_idle.png"), 15, 200, 200, Duration.seconds(2.0), 0, 14);
            walkAnimation = new AnimationChannel(image("ash_walk.png"), 14, 200, 200, Duration.seconds(1.2), 0, 13);
            runAnimation = new AnimationChannel(image("ash_walk.png"), 14, 200, 200, Duration.seconds(0.5), 0, 13);
            attackBeginAnimation = new AnimationChannel(image("ash_attack.png"), 21, 200, 200, Duration.seconds(0.2), 0, 3);
            attackIdleAnimation = new AnimationChannel(image("ash_attack.png"), 21, 200, 200, Duration.seconds(0.45), 4, 12);
            attackEndAnimation = new AnimationChannel(image("ash_attack.png"), 21, 200, 200, Duration.seconds(0.4), 13, 20);
            dieAnimation = new AnimationChannel(image("ash_die.png"), 8, 200, 200, Duration.seconds(1.5), 0, 7);

            animatedTexture = new AnimatedTexture(idleAnimation);
            animatedTexture.loop();
            flip = new Scale(1, 1, 120, 100);
            animatedTexture.getTransforms().add(flip);
            currentAnimationName = "idle";
            animationLoaded = true;
        } catch (Exception e) {
            System.err.println("PlayerAnimator: 硬编码动画加载失败 - " + e.getMessage());
            animationLoaded = false;
        }
    }

    public void update() {
        if (!animationLoaded || animatedTexture == null) return;
        String newAnimationName = determineAnimation();
        if (!newAnimationName.equals(currentAnimationName)) {
            currentAnimationName = newAnimationName;
            switchAnimation(newAnimationName);
        }
        updateFacing();
    }

    private String determineAnimation() {
        Player.State state = player.getState();
        switch (state) {
            case DIE: return "die";
            case SHOOTING:
                switch (player.getAttackPhase()) {
                    case BEGIN: return "attack_begin";
                    case IDLE: return "attack_idle";
                    case END: return "attack_end";
                    default: return "attack_begin";
                }
            case WALK: return "walk";
            case RUN: return "run";
            default: return "idle";
        }
    }

    private void switchAnimation(String animName) {
        AnimationChannel channel = idleAnimation;
        boolean shouldLoop = true;

        switch (animName) {
            case "walk": channel = walkAnimation; break;
            case "run": channel = runAnimation; break;
            case "attack_begin": channel = attackBeginAnimation; shouldLoop = false; break;
            case "attack_idle": channel = attackIdleAnimation; break;
            case "attack_end": channel = attackEndAnimation; shouldLoop = false; break;
            case "die": channel = dieAnimation; shouldLoop = false; break;
        }

        if (channel == null) channel = idleAnimation;

        if (shouldLoop) animatedTexture.loopAnimationChannel(channel);
        else animatedTexture.playAnimationChannel(channel);
    }

    private void updateFacing() {
        if (player.getEntity() == null) return;
        flip.setX(player.getFacingRight() ? 1 : -1);
    }

    private AnimationChannel mk(AnimClip c) {
        if (c == null || c.sheet == null) return null;
        return new AnimationChannel(image(c.sheet), c.frames, c.w, c.h, Duration.seconds(c.duration), c.from, c.to);
    }

    private void setupAnimations(CharacterDef ch) {
        try {
            idleAnimation = mk(ch.idle);
            walkAnimation = mk(ch.walk);
            runAnimation = mk(ch.run);
            dieAnimation = mk(ch.die);

            if (ch.attack != null) {
                if ("SPLIT".equalsIgnoreCase(ch.attack.layout)) {
                    attackBeginAnimation = mk(ch.attack.begin);
                    attackIdleAnimation = mk(ch.attack.loop);
                    attackEndAnimation = mk(ch.attack.end);
                } else { // LOOP or other
                    attackBeginAnimation = null;
                    attackIdleAnimation = mk(ch.attack.loop);
                    attackEndAnimation = null;
                }
            } else {
                attackBeginAnimation = attackIdleAnimation = attackEndAnimation = null;
            }

            animatedTexture = new AnimatedTexture(idleAnimation);
            animatedTexture.loop();
            flip = new Scale(1, 1, 120, 100);
            animatedTexture.getTransforms().add(flip);
            currentAnimationName = "idle";
            animationLoaded = true;
        } catch (Exception e) {
            System.err.println("PlayerAnimator(json)失败，回退到硬编码资源: " + e.getMessage());
            this.def = null;
            setupAnimations();
        }
    }

    public AnimatedTexture getAnimatedTexture() {
        return animatedTexture;
    }

    public boolean isAnimationLoaded() {
        return animationLoaded;
    }

    public String getCurrentAnimationName() {
        return currentAnimationName;
    }
}