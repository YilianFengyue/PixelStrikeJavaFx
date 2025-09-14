package org.csu.pixelstrikejavafx.game.core;

import org.csu.pixelstrikejavafx.game.weapon.WeaponStats;

/** 集中配置信息，后续只改这里 */
public final class GameConfig {
    private GameConfig() {}

    // --- 窗口（1080p） ---
    public static final int WINDOW_W = 1980;
    public static final int WINDOW_H = 1080;

    // --- 世界/地图尺寸（按你的背景图） ---
    public static final double MAP_W = 4620;   // 背景宽
    public static final double MAP_H = 3030;   // 背景高

    // --- 物理换算 ---
    public static final double PPM = 50.0;     // pixels per meter

    // --- Tile 约定（后续贴图平铺会用到，可先不动） ---
    public static final int TILE = 64;

    // --- 资源名（你可以按需改） ---
    public static final String BG_IMAGE = "background.png";  // 放 assets/textures
    public static final String GROUND_BASE = "ground_base.png";        // 可无→回退色块
    public static final String GROUND_EDGE = "ground_edge.png";        // 可无→回退色块
    public static final String G_BASE_STRIP = "ground_base.png";


    // --- ★ 武器数据配置中心 ★ ---
    public static final class Weapons {
        private Weapons() {}
        public static final WeaponStats PISTOL = new WeaponStats(
                "Pistol", 10.0, 0.15, 1200.0, 1500.0, 0.0,
                150.0, 0.0, 0.0,
                1, 0.0,
                1.6, 18.0, 0.6,
                0.5, 6.0, 8.0,
                200.0, 20.0
        );

        public static final WeaponStats MACHINE_GUN = new WeaponStats(
                "MachineGun", 8.0, 0.08, 1200.0, 1800.0, 0.0,
                150.0, 0.0, 0.0,
                1, 0.0,
                0.0, 0.0, 2.5,
                0.8, 10.0, 12.0,
                0.0, 0.0
        );

        public static final WeaponStats SHOTGUN = new WeaponStats(
                "Shotgun", 4.0, 0.8, 600.0, 2000.0, 0.0,
                150.0, 0.0, 0.0,
                8, 15.0,
                0.0, 0.0, 0.0,
                0.0, 0.0, 0.0,
                0.0, 0.0
        );
        public static final WeaponStats RAILGUN = new WeaponStats(
                "Railgun", 75.0, 1.0, 4000.0, 0.0, 0.0,
                150.0, 150.0, 0.0, // Muzzle Left X set to 150 for symmetry with -offsetX
                1, 0.0,
                0.0, 0.0, 0.0,
                0.0, 0.0, 0.0,
                0.0, 0.0
        );
    }

}
