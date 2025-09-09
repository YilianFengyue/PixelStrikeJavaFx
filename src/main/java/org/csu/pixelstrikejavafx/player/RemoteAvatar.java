package org.csu.pixelstrikejavafx.player;

import com.almasb.fxgl.texture.AnimationChannel;
import com.almasb.fxgl.texture.AnimatedTexture;
import javafx.scene.Node;
import javafx.util.Duration;

import static com.almasb.fxgl.dsl.FXGL.*;

public final class RemoteAvatar {
    private final AnimatedTexture tex;
    private final AnimationChannel idle, walk, run, atkBegin, atkIdle, atkEnd, die;

    String anim, phase;    // 新增：来自网络的动画状态
    double lastVX, lastVY; // 你已有或可保留


    public RemoteAvatar() {
        idle = new AnimationChannel(image("ash_idle.png"), 15, 200, 200, Duration.seconds(2.0), 0, 14);
        walk = new AnimationChannel(image("ash_walk.png"), 14, 200, 200, Duration.seconds(1.2), 0, 13);
        run  = new AnimationChannel(image("ash_walk.png"), 14, 200, 200, Duration.seconds(0.5), 0, 13);

        AnimationChannel tmp = idle;
        AnimationChannel _atk = null, _atk2 = null, _atk3 = null, _die = null;
        try {
            _atk  = new AnimationChannel(image("ash_attack.png"), 21, 200, 200, Duration.seconds(0.20), 0, 3);
            _atk2 = new AnimationChannel(image("ash_attack.png"), 21, 200, 200, Duration.seconds(0.45), 4, 12);
            _atk3 = new AnimationChannel(image("ash_attack.png"), 21, 200, 200, Duration.seconds(0.40), 13, 20);
        } catch (Exception ignored) {}
        try {
            _die = new AnimationChannel(image("ash_die.png"), 8, 200, 200, Duration.seconds(1.5), 0, 7);
        } catch (Exception ignored) {}

        atkBegin = _atk  != null ? _atk  : idle;
        atkIdle  = _atk2 != null ? _atk2 : idle;
        atkEnd   = _atk3 != null ? _atk3 : idle;
        die      = _die  != null ? _die  : idle;

        tex = new AnimatedTexture(idle);
        tex.loop();
    }

    public Node view() { return tex; }

    public void setFacingRight(boolean right) { tex.setScaleX(right ? 1 : -1); }

    public void playState(String anim, String phase, double vx, boolean onGround) {
        // 后备：如果没有传 anim，用简单规则兜底
        if (anim == null || anim.isEmpty()) {
            if (!onGround)       { loopIfNot(idle); return; } // 先占位
            if (Math.abs(vx)>300){ loopIfNot(run);  return; }
            if (Math.abs(vx)>20) { loopIfNot(walk); return; }
            loopIfNot(idle); return;
        }
        switch (anim) {
            case "DIE"      -> playOnceIfNot(die);
            case "SHOOT"    -> {
                String p = phase == null ? "IDLE" : phase;
                switch (p) {
                    case "BEGIN" -> playOnceIfNot(atkBegin);
                    case "END"   -> playOnceIfNot(atkEnd);
                    default      -> loopIfNot(atkIdle);
                }
            }
            case "RUN"      -> loopIfNot(run);
            case "WALK"     -> loopIfNot(walk);
            case "JUMP", "FALL" -> loopIfNot(idle); // 你有跳跃序列再替换
            default         -> loopIfNot(idle);
        }
    }

    private void loopIfNot(AnimationChannel ch) {
        if (tex.getAnimationChannel() != ch) tex.loopAnimationChannel(ch);
    }
    private void playOnceIfNot(AnimationChannel ch) {
        if (tex.getAnimationChannel() != ch) tex.playAnimationChannel(ch);
    }
}
