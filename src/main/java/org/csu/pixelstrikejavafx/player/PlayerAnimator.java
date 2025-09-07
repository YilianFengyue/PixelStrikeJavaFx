// main/java/org/csu/pixelstrikejavafx/player/PlayerAnimator.java

package org.csu.pixelstrikejavafx.player;

import com.almasb.fxgl.texture.AnimationChannel;
import com.almasb.fxgl.texture.AnimatedTexture;
import javafx.util.Duration;
import javafx.scene.transform.Scale;
import lombok.Getter;

import static com.almasb.fxgl.dsl.FXGL.image;

@Getter
public class PlayerAnimator {

    private AnimatedTexture animatedTexture;
    private AnimationChannel idleAnimation, runAnimation, attackIdleAnimation, dieAnimation, jumpAnimation, fallAnimation;
    private String currentAnimationName = "";
    private boolean animationLoaded = false;
    private Scale flip;

    public PlayerAnimator(Player player) {
        setupAnimations();
    }

    private void setupAnimations() {
        try {
            idleAnimation = new AnimationChannel(image("ash_idle.png"), 15, 200, 200, Duration.seconds(2.0), 0, 14);
            runAnimation = new AnimationChannel(image("ash_walk.png"), 14, 200, 200, Duration.seconds(0.7), 0, 13);
            attackIdleAnimation = new AnimationChannel(image("ash_attack.png"), 21, 200, 200, Duration.seconds(0.45), 4, 12);
            jumpAnimation = new AnimationChannel(image("ash_walk.png"), 14, 200, 200, Duration.seconds(1.0), 5, 5);
            fallAnimation = new AnimationChannel(image("ash_walk.png"), 14, 200, 200, Duration.seconds(1.0), 8, 8);

            try {
                dieAnimation = new AnimationChannel(image("ash_die.png"), 8, 200, 200, Duration.seconds(1.5), 0, 7);
            } catch (Exception e) {
                dieAnimation = new AnimationChannel(image("ash_idle.png"), 15, 200, 200, Duration.seconds(1.0), 0, 0);
            }

            animatedTexture = new AnimatedTexture(idleAnimation);
            animatedTexture.loop();
            flip = new Scale(1, 1, 100, 100);
            animatedTexture.getTransforms().add(flip);
            currentAnimationName = "idle";
            animationLoaded = true;
        } catch (Exception e) {
            System.err.println("PlayerAnimator: 动画加载失败 - " + e.getMessage());
        }
    }

    public void update(Player.LocalState localState, boolean isFacingRight) {
        if (!animationLoaded) return;
        String newAnimationName = determineAnimation(localState);
        if (!newAnimationName.equals(currentAnimationName)) {
            currentAnimationName = newAnimationName;
            switchAnimation(newAnimationName);
        }
        updateFacing(isFacingRight);
    }

    private String determineAnimation(Player.LocalState localState) {
        return switch (localState) {
            case RUN -> "run";
            case JUMP -> "jump";
            case FALL -> "fall";
            case SHOOT -> "attack_idle";
            case DEAD, HIT -> "die";
            default -> "idle";
        };
    }

    private void updateFacing(boolean isFacingRight) {
        if (flip == null) return;
        flip.setX(isFacingRight ? 1 : -1);
    }

    private void switchAnimation(String animName) {
        AnimationChannel channel;
        boolean shouldLoop = true;
        switch (animName) {
            case "run": channel = runAnimation; break;
            case "attack_idle": channel = attackIdleAnimation; break;
            case "jump": channel = jumpAnimation; shouldLoop = false; break;
            case "fall": channel = fallAnimation; shouldLoop = false; break;
            case "die": channel = dieAnimation; shouldLoop = false; break;
            default: channel = idleAnimation; break;
        }
        if (shouldLoop) {
            animatedTexture.loopAnimationChannel(channel);
        } else {
            animatedTexture.playAnimationChannel(channel);
        }
    }
}