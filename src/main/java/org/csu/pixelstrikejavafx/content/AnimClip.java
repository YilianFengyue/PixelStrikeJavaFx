package org.csu.pixelstrikejavafx.content;

public final class AnimClip {
    public String sheet;        // png 文件名（放在 assets/textures/）
    public int frames;          // 图集总帧数
    public int w, h;            // 单帧宽高
    public int from, to;        // 播放区间（含）
    public double duration;     // 该区间总时长（秒）
    public boolean loop = true; // 是否循环
}
