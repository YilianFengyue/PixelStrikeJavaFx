package org.csu.pixelstrikejavafx.player;

import com.almasb.fxgl.texture.AnimationChannel;
import com.almasb.fxgl.texture.AnimatedTexture;
import javafx.scene.Node;
import javafx.scene.transform.Scale;
import javafx.util.Duration;

import static com.almasb.fxgl.dsl.FXGL.*;

import org.csu.pixelstrikejavafx.content.AnimClip;
import org.csu.pixelstrikejavafx.content.CharacterDef;
import org.csu.pixelstrikejavafx.content.CharacterRegistry;

/** 远端影子动画：只负责“看起来正确”，不参与物理 */
public final class RemoteAvatar {

    private AnimatedTexture tex;
    private AnimationChannel idle, walk, run, die, atkBegin, atkIdle, atkEnd;

    // 来自网络的状态（可留空）
    String anim, phase;
    double lastVX, lastVY;

    // 与本地一致的翻转支点（你的精灵 200x200，用 120,100）
    private final Scale flip = new Scale(1, 1, 120, 100);

    /** 旧构造：保持兼容，默认用 ash */
    public RemoteAvatar() {
        buildFrom(CharacterRegistry.get("ash"), false);
    }

    /** 新构造：显式指定角色定义 */
    public RemoteAvatar(CharacterDef def) {
        buildFrom(def != null ? def : CharacterRegistry.get("ash"), false);
    }

    /** 若先创建为默认形象，后来拿到 charId，可调用此方法热切换外观 */
    public void rebuild(CharacterDef def) {
        buildFrom(def != null ? def : CharacterRegistry.get("ash"), true);
    }

    // —— 内部：从 JSON 构建通道；needReuse 表示是否复用已有 AnimatedTexture 节点 ——
    private void buildFrom(CharacterDef ch, boolean needReuse) {
        try {
            idle = mk(ch.idle);
            walk = mk(ch.walk);
            run  = mk(ch.run);
            die  = mk(ch.die);

            // 兼容：没有 begin/end 的角色，只有 loop
            if (ch.attack != null && "SPLIT".equalsIgnoreCase(ch.attack.layout)) {
                atkBegin = mk(ch.attack.begin);
                atkIdle  = mk(ch.attack.loop);
                atkEnd   = mk(ch.attack.end);
            } else if (ch.attack != null && ch.attack.loop != null) {
                atkBegin = null;
                atkIdle  = mk(ch.attack.loop);
                atkEnd   = null;
            } else {
                atkBegin = atkIdle = atkEnd = null;
            }

            if (!needReuse || tex == null) {
                tex = new AnimatedTexture(idle);
                tex.loop();
                tex.getTransforms().add(flip);
            } else {
                // 复用原节点：切回 idle
                tex.loopAnimationChannel(idle);
            }
        } catch (Exception e) {
            // 出问题回退到硬编码 ash（绝不崩）
            buildHardcodedAsh(needReuse);
        }
    }

    private AnimationChannel mk(AnimClip c) {
        return new AnimationChannel(
                image(c.sheet), c.frames, c.w, c.h,
                Duration.seconds(c.duration), c.from, c.to
        );
    }

    /** 回退：老的 ash 硬编码（资源一定在） */
    private void buildHardcodedAsh(boolean needReuse) {
        idle = new AnimationChannel(image("ash_idle1.png"), 81, 200, 200, Duration.seconds(2.7),   0, 80);
        walk = new AnimationChannel(image("ash_walk1.png"), 40, 200, 200, Duration.seconds(1.333), 0, 39);
        run  = new AnimationChannel(image("ash_walk1.png"), 40, 200, 200, Duration.seconds(0.683), 0, 39);
        try {
            atkBegin = new AnimationChannel(image("ash_attack1.png"), 41, 200, 200, Duration.seconds(0.3),   0,  8);
            atkIdle  = new AnimationChannel(image("ash_attack1.png"), 41, 200, 200, Duration.seconds(0.25), 10, 14);
            atkEnd   = new AnimationChannel(image("ash_attack1.png"), 41, 200, 200, Duration.seconds(0.8333),15, 39);
        } catch (Exception ignore) { atkBegin = atkIdle = atkEnd = null; }
        try {
            die = new AnimationChannel(image("ash_die.png"), 30, 200, 200, Duration.seconds(1.0), 0, 29);
        } catch (Exception ignore) {
            die = new AnimationChannel(image("ash_idle1.png"), 81, 200, 200, Duration.seconds(1.0), 0, 0);
        }

        if (!needReuse || tex == null) {
            tex = new AnimatedTexture(idle);
            tex.loop();
            tex.getTransforms().add(flip);
        } else {
            tex.loopAnimationChannel(idle);
        }
    }

    public Node view() { return tex; }

    /** 只改缩放系数，不直接 setScaleX，避免绕 (0,0) 翻转 */
    public void setFacingRight(boolean right) { flip.setX(right ? 1 : -1); }

    /** 简单状态机（网络没发 anim 时兜底）；BEGIN/END 缺失时自动用 loop */
    public void playState(String anim, String phase, double vx, boolean onGround) {
        if (anim == null || anim.isEmpty()) {
            if (!onGround)            { loopIfNot(idle); return; }
            if (Math.abs(vx) > 300.0) { loopIfNot(run);  return; }
            if (Math.abs(vx) >  20.0) { loopIfNot(walk); return; }
            loopIfNot(idle); return;
        }
        switch (anim) {
            case "DIE" -> playOnceIfNot(die);
            case "SHOOT" -> {
                String p = (phase == null ? "IDLE" : phase);
                switch (p) {
                    case "BEGIN" -> playOnceIfNot(atkBegin != null ? atkBegin : (atkIdle != null ? atkIdle : idle));
                    case "END"   -> playOnceIfNot(atkEnd   != null ? atkEnd   : (atkIdle != null ? atkIdle : idle));
                    default      -> loopIfNot    (atkIdle  != null ? atkIdle  : idle);
                }
            }
            case "RUN"  -> loopIfNot(run);
            case "WALK" -> loopIfNot(walk);
            case "JUMP", "FALL" -> loopIfNot(idle);   // 先占位
            default -> loopIfNot(idle);
        }
    }

    private void loopIfNot(AnimationChannel ch) { if (ch != null && tex.getAnimationChannel() != ch) tex.loopAnimationChannel(ch); }
    private void playOnceIfNot(AnimationChannel ch) { if (ch != null && tex.getAnimationChannel() != ch) tex.playAnimationChannel(ch); }
}
