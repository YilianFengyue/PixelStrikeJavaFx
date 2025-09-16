package org.csu.pixelstrikejavafx.game.core;

import org.csu.pixelstrikejavafx.game.player.AnimationData;
import org.csu.pixelstrikejavafx.game.player.CharacterAnimationSet;
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
    public static String BG_IMAGE = "background.png";  // 放 assets/textures
    public static final String GROUND_BASE = "ground_base.png";        // 可无→回退色块
    public static final String GROUND_EDGE = "ground_edge.png";        // 可无→回退色块
    public static final String G_BASE_STRIP = "ground_base.png";


    // --- ★ 武器数据配置中心 ★ ---
    public static final class Weapons {
        private Weapons() {}
        public static final WeaponStats PISTOL = new WeaponStats(
                "Pistol",
                15.0,
                0.2,
                1200.0, 1800.0, 0.0,
                150.0, 0.0, 0.0,
                1, 0.0,
                1.2, 18.0, 0.2,
                0.2, 4.0, 6.0,
                100.0, 10.0
        );

        public static final WeaponStats MACHINE_GUN = new WeaponStats(
                "MachineGun",
                9.0,
                0.07,
                1200.0, 1800.0, 0.0,
                150.0, 0.0, 0.0,
                1, 0.0,
                0.0, 0.0, 4.0,
                1.2, 15.0, 18.0,
                0.0, 0.0
        );

        public static final WeaponStats SHOTGUN = new WeaponStats(
                "Shotgun",
                8.0,
                1.0,
                500.0,
                2000.0, 0.0,
                150.0, 0.0, 0.0,
                8,
                22.0,
                0.0, 0.0, 0.0,
                0.0, 0.0, 0.0,
                0.0, 0.0
        );
        public static final WeaponStats RAILGUN = new WeaponStats(
                "Railgun",
                90.0,
                1.5,
                4000.0, 0.0, 0.0,
                150.0, 150.0, 0.0,
                1, 0.0,
                0.0, 0.0, 0.0,
                0.0, 0.0, 0.0,
                0.0, 0.0
        );
    }

    public static final class Animations {
        private static final int FRAME_W = 200;
        private static final int FRAME_H = 200;

        public static final CharacterAnimationSet ASH = new CharacterAnimationSet(
                new AnimationData("characters/ash/ash_idle.png",
                        81, 1.0, 0, 80),
                new AnimationData("characters/ash/ash_walk.png",
                        41, 0.6, 0, 40),
                new AnimationData("characters/ash/ash_walk.png",
                        41, 0.4, 0, 40),
                new AnimationData("characters/ash/ash_attack.png",
                        41, 0.1, 0, 12),
                new AnimationData("characters/ash/ash_attack.png",
                        41, 0.2, 14, 26),
                new AnimationData("characters/ash/ash_attack.png",
                        41, 0.2, 28, 29),
                new AnimationData("characters/ash/ash_die.png",
                        30, 0.3, 0, 29)
        );

        public static final CharacterAnimationSet SHU = new CharacterAnimationSet(
                new AnimationData("characters/shu/shu_idle.png",
                        101, 1.0, 0, 100), // 假设值
                new AnimationData("characters/shu/shu_walk.png",
                        101, 0.6, 0, 100), // 假设值
                new AnimationData("characters/shu/shu_walk.png",
                        101, 0.4, 0, 100), // 假设值
                new AnimationData("characters/shu/shu_attack.png",
                        37, 0.15, 0, 12),
                new AnimationData("characters/shu/shu_attack.png",
                        37, 0.25, 13, 24),
                new AnimationData("characters/shu/shu_attack.png",
                        37, 0.25, 19, 36),
                new AnimationData("characters/shu/shu_die.png",
                        30, 0.3, 0, 29) // 假设值
        );

        public static final CharacterAnimationSet ANGEL_NENG = new CharacterAnimationSet(
                new AnimationData("characters/angel_neng/angel_neng_idle.png",
                        63, 1.0, 0, 62), // 占位
                new AnimationData("characters/angel_neng/angel_neng_walk.png",
                        41, 0.6, 0, 40), // 占位
                new AnimationData("characters/angel_neng/angel_neng_walk.png",
                        41, 0.4, 0, 40), // 占位
                new AnimationData("characters/angel_neng/angel_neng_attack.png",
                31, 0.1, 0, 10),
                new AnimationData("characters/angel_neng/angel_neng_attack.png",
                31, 0.2, 5, 20),
                new AnimationData("characters/angel_neng/angel_neng_attack.png",
                31, 0.2, 19, 30),
                new AnimationData("characters/angel_neng/angel_neng_die.png",
                        30, 0.4, 0, 29)
        );

        public static final CharacterAnimationSet BLUEP_MARTHE = new CharacterAnimationSet(
                new AnimationData("characters/bluep_marthe/bluep_marthe_idle.png",
                        101, 1.2, 0, 100),
                new AnimationData("characters/bluep_marthe/bluep_marthe_walk.png",
                101, 0.7, 0, 100),
                new AnimationData("characters/bluep_marthe/bluep_marthe_walk.png",
                101, 0.7, 0, 100),
                new AnimationData("characters/bluep_marthe/bluep_marthe_attack.png",
                        31, 0.1, 0, 10),
                new AnimationData("characters/bluep_marthe/bluep_marthe_attack.png",
                        31, 0.2, 5, 20),
                new AnimationData("characters/bluep_marthe/bluep_marthe_attack.png",
                        31, 0.2, 19, 30),
                new AnimationData("characters/bluep_marthe/bluep_marthe_die.png",
                        30, 0.4, 0, 29)
        );
    }

}