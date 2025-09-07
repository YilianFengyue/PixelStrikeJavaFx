package org.csu.pixelstrikejavafx.core;

/** 集中配置信息，后续只改这里 */
public final class GameConfig {
    private GameConfig() {}

    // --- 窗口（1080p） ---
    public static final int WINDOW_W = 1920;
    public static final int WINDOW_H = 1080;

    // --- 世界/地图尺寸（按你的背景图） ---
    public static final double MAP_W = 4620;   // 背景宽
    public static final double MAP_H = 3030;   // 背景高

    // --- 物理换算 ---
    public static final double PPM = 50.0;     // pixels per meter

    // --- Tile 约定（后续贴图平铺会用到，可先不动） ---
    public static final int TILE = 64;

    // --- 新增：坐标系转换常量 ---
    // 后端地面Y坐标 - 前端角色脚底到图片顶部的距离 (估算值, 可微调)
    // 估算: 后端地面500 - (前端地面顶2819 - 玩家身高一半100) = 500 - 2719 = -2219
    public static final double Y_OFFSET = 2169.0; //  (后端地面500对应前端2819，差值2319)

    // --- 资源名（你可以按需改） ---
    public static final String BG_IMAGE = "background.png";  // 放 assets/textures
    public static final String GROUND_BASE = "ground_base.png";        // 可无→回退色块
    public static final String GROUND_EDGE = "ground_edge.png";        // 可无→回退色块
    public static final String G_BASE_STRIP = "ground_base.png";


  
}
