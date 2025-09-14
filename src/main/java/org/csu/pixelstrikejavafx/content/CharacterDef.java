package org.csu.pixelstrikejavafx.content;

import java.util.Map;

public final class CharacterDef {
    public String id;
    public Hitbox hitbox;
    public Sockets sockets;
    public AnimClip idle, walk, run, die;     // 基础四个
    public Attack attack;                     // 可选：SPLIT 或 LOOP

    public static final class Hitbox {
        public double offX, offY, w, h;
    }
    public static final class Sockets {
        public double muzzleRightX, muzzleLeftX, muzzleY;
    }
    public static final class Attack {
        public String layout;                 // "SPLIT" | "LOOP"
        public AnimClip begin, loop, end;     // SPLIT 用 begin/loop/end；LOOP 只用 loop
    }
}
