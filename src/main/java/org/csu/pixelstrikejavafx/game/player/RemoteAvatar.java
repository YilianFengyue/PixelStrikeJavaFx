package org.csu.pixelstrikejavafx.game.player;

import com.almasb.fxgl.texture.AnimatedTexture;
import com.almasb.fxgl.texture.AnimationChannel;
import javafx.scene.Node;
import javafx.scene.transform.Scale;
import javafx.util.Duration;
import org.jetbrains.annotations.NotNull;

import static com.almasb.fxgl.dsl.FXGL.image;

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

    public RemoteAvatar(int characterId) {
        String characterName = getCharacterFolderName(characterId);

        // --- ↓↓↓ 核心修改点 ↓↓↓ ---
        idle = new AnimationChannel(image("characters/" + characterName + "/" + characterName + "_idle.png"), 15, 200, 200, Duration.seconds(2.0), 0, 14);
        walk = new AnimationChannel(image("characters/" + characterName + "/" + characterName + "_walk.png"), 14, 200, 200, Duration.seconds(1.2), 0, 13);
        run  = new AnimationChannel(image("characters/" + characterName + "/" + characterName + "_walk.png"), 14, 200, 200, Duration.seconds(0.5), 0, 13);

        AnimationChannel _atkB = null, _atkI = null, _atkE = null, _die = null;
        try {
            _atkB = new AnimationChannel(image("characters/" + characterName + "/" + characterName + "_attack.png"), 21, 200, 200, Duration.seconds(0.20), 0, 3);
            _atkI = new AnimationChannel(image("characters/" + characterName + "/" + characterName + "_attack.png"), 21, 200, 200, Duration.seconds(0.45), 4, 12);
            _atkE = new AnimationChannel(image("characters/" + characterName + "/" + characterName + "_attack.png"), 21, 200, 200, Duration.seconds(0.40), 13, 20);
        } catch (Exception ignored) {}
        try {
            _die = new AnimationChannel(image("characters/" + characterName + "/" + characterName + "_die.png"), 8, 200, 200, Duration.seconds(1.5), 0, 7);
        } catch (Exception ignored) {}
        // --- ↑↑↑ 核心修改点 ↑↑↑ ---

        atkBegin = _atkB != null ? _atkB : idle;
        atkIdle  = _atkI != null ? _atkI : idle;
        atkEnd   = _atkE != null ? _atkE : idle;
        die      = _die  != null ? _die  : idle;

        tex = new AnimatedTexture(idle);
        tex.loop();
        tex.getTransforms().add(flip);
    }

    // <-- 新增这个辅助方法 -->
    private String getCharacterFolderName(int characterId) {
        return getCharacterString(characterId);
    }

    @NotNull
    static String getCharacterString(int characterId) {
        switch (characterId) {
            case 1: return "ash";
            case 2: return "shu";
            case 3: return "angel_neng";
            case 4: return "bluep_marthe";
            default: return "ash";
        }
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