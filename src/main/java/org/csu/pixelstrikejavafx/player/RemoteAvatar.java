package org.csu.pixelstrikejavafx.player;

import com.almasb.fxgl.texture.AnimationChannel;
import com.almasb.fxgl.texture.AnimatedTexture;
import javafx.scene.Node;
import javafx.scene.transform.Scale;                 // ★ 新增
import javafx.util.Duration;

import static com.almasb.fxgl.dsl.FXGL.*;

/** 远端影子动画：只负责“看起来正确”，不参与物理 */
public final class RemoteAvatar {
    private final AnimatedTexture tex;
    private final AnimationChannel idle, walk, run, atkBegin, atkIdle, atkEnd, die;

    // 来自网络的状态（可留空）
    String anim, phase;
    double lastVX, lastVY;

    // ★ 和本地 PlayerAnimator 一致的翻转“支点”
    //   你的精灵是 200x200，本地用的是 (120, 100)，远端保持一致即可对齐
    private final Scale flip = new Scale(1, 1, 120, 100);

    public RemoteAvatar() {
        // ★ 根据 PlayerAnimator 的参数统一修改

        // idle: ash_idle1.png, 81帧, Duration.seconds(2.7), 0-80
        idle = new AnimationChannel(image("ash_idle1.png"), 81, 200, 200, Duration.seconds(2.7), 0, 80);

        // walk: ash_walk1.png, 81帧, Duration.seconds(2.7), 0-80
        walk = new AnimationChannel(image("ash_walk1.png"), 40, 200, 200, Duration.seconds(1.333), 0, 39);

        // run: ash_walk1.png, 81帧, Duration.seconds(1.3), 0-80 (复用walk但播放更快)
        run = new AnimationChannel(image("ash_walk1.png"), 40, 200, 200, Duration.seconds(0.683), 0, 39);

        AnimationChannel _atkB = null, _atkI = null, _atkE = null, _die = null;

        // 攻击动画：ash_attack1.png, 41帧
        try {
            // attackBegin: Duration.seconds(0.3), 0-8
            _atkB = new AnimationChannel(image("ash_attack1.png"), 41, 200, 200, Duration.seconds(0.3), 0, 8);
            // attackIdle: Duration.seconds(0.1666), 10-14
            _atkI = new AnimationChannel(image("ash_attack1.png"), 41, 200, 200, Duration.seconds(0.25), 10, 14);
            // attackEnd: Duration.seconds(0.8333), 15-39
            _atkE = new AnimationChannel(image("ash_attack1.png"), 41, 200, 200, Duration.seconds(0.8333), 15, 39);
        } catch (Exception ignored) {}

        // die: ash_die.png, 30帧, Duration.seconds(1.0), 0-29
        try {
            _die = new AnimationChannel(image("ash_die.png"), 30, 200, 200, Duration.seconds(1.0), 0, 29);
        } catch (Exception ignored) {}

        atkBegin = _atkB != null ? _atkB : idle;
        atkIdle  = _atkI != null ? _atkI : idle;
        atkEnd   = _atkE != null ? _atkE : idle;
        die      = _die  != null ? _die  : idle;

        tex = new AnimatedTexture(idle);
        tex.loop();
        tex.getTransforms().add(flip);        // ★ 关键：让翻转围绕精灵中心
    }

    public Node view() { return tex; }

    /** 只改缩放系数，不直接 setScaleX，避免围绕(0,0)翻转 */
    public void setFacingRight(boolean right) { flip.setX(right ? 1 : -1); }   // ★ 修正点

    /** 简单状态机（网络没发 anim 时兜底） */
    public void playState(String anim, String phase, double vx, boolean onGround) {
        if (anim == null || anim.isEmpty()) {
            if (!onGround)            { loopIfNot(idle); return; }  // 无空中序列先占位
            if (Math.abs(vx) > 300.0) { loopIfNot(run);  return; }
            if (Math.abs(vx) >  20.0) { loopIfNot(walk); return; }
            loopIfNot(idle); return;
        }
        switch (anim) {
            case "DIE"   -> playOnceIfNot(die);
            case "SHOOT" -> {
                String p = phase == null ? "IDLE" : phase;
                switch (p) {
                    case "BEGIN" -> playOnceIfNot(atkBegin);
                    case "END"   -> playOnceIfNot(atkEnd);
                    default      -> loopIfNot(atkIdle);
                }
            }
            case "RUN"   -> loopIfNot(run);
            case "WALK"  -> loopIfNot(walk);
            case "JUMP", "FALL" -> loopIfNot(idle);
            default      -> loopIfNot(idle);
        }
    }

    private void loopIfNot(AnimationChannel ch) { if (tex.getAnimationChannel() != ch) tex.loopAnimationChannel(ch); }
    private void playOnceIfNot(AnimationChannel ch) { if (tex.getAnimationChannel() != ch) tex.playAnimationChannel(ch); }
}