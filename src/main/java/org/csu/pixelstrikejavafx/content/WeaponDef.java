package org.csu.pixelstrikejavafx.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

/** 最小武器定义：稳定字段 + 可扩展 props + 可选皮肤贴图 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class WeaponDef {

    public String id;
    public String name;

    /** 秒/发 */
    public double shootInterval = 0.15;

    /** 每发伤害（服务器会再做权威裁决，这里先用于客户端表现与上报） */
    public int damage = 10;

    /** 连发累积上抬角（度/发） */
    public double recoilKickDeg = 0.5;

    /** 枪口增量（叠加到角色 sockets） */
    public MuzzleDelta muzzleOffsetDelta = new MuzzleDelta();

    /** 可选：武器外观（静态贴图）。缺省=无贴图层 */
    public Skin skin;

    /** 预留扩展字段容器（散布/摆动/反冲/特效/弹体参数等） */
    public Map<String, Object> props;

    /** 可选：武器音效（缺省=静音/用通用音效） */
    public Sfx sfx;

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Sfx {
        /** 枪口音效文件名，相对 assets/sounds/weapons/ ，如 "ak47_muzzle.wav" */
        public String muzzle;
        /** 播放音量 0.0~1.0，缺省 1.0 */
        public Double volume;
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class MuzzleDelta {
        public double rightX = 0;
        public double leftX  = 0;
        public double y      = 0;
    }

    /** 静态贴图皮肤。放在 /assets/weapons/ 目录 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Skin {
        /** 文件名（例如 "手枪.png" / "AK47.png"） */
        public String image;

        /** 尺寸控制：三选一（都不填=原图 1:1）
         *  1) scale：整体缩放倍数；
         *  2) h：指定目标高度（像素，高度优先，按比例缩放）；
         *  3) w：指定目标宽度（像素，若同时给 h 也行，我们优先按 h 适配）。
         */
        public Double scale;
        public Integer w;
        public Integer h;

        /** 以“像素坐标”定义一个枢轴点（pivot），渲染时把该点对齐到“物理枪口” */
        public double pivotX = 0;
        public double pivotY = 0;

        /** 相对对齐点的小幅微调（世界坐标，单位像素）
         *   - 右手朝右时使用 offsetRight
         *   - 朝左时使用 offsetLeft（常用于少量非对称校准）
         */
        public Offset offsetRight = new Offset();
        public Offset offsetLeft  = new Offset();

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static final class Offset {
            public double x = 0;
            public double y = 0;
        }
    }
}
