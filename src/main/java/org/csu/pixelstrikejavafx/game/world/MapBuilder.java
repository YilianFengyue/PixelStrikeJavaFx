package org.csu.pixelstrikejavafx.game.world;

import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.components.CollidableComponent;
import com.almasb.fxgl.physics.PhysicsComponent;
import com.almasb.fxgl.physics.box2d.dynamics.BodyType;
import com.almasb.fxgl.texture.Texture;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.csu.pixelstrikejavafx.game.core.GameConfig;
import org.csu.pixelstrikejavafx.game.core.GameType;


import static com.almasb.fxgl.dsl.FXGL.*;

/** 只做：背景 + 平台（碰撞体）+ 可见层(含遮挡草沿) */
public final class MapBuilder {
    private MapBuilder() {}

    private static double ipx(double v) { return Math.round(v); }  // 强制整数像素
    private static com.almasb.fxgl.entity.Entity MAIN_GROUND;
    public static void buildLevel() {
        addBackground();
        buildMainGroundStrip(0, GameConfig.MAP_H - 211, GameConfig.MAP_W);  // 211=你的地面贴图高度
//        buildMainGroundStrip(150, GameConfig.MAP_H - 211 - 300, 400);  // 离地面150像素高，宽400像素
        buildAirPlatform(800, GameConfig.MAP_H - 470, 925, "floating_platform.png");
        buildAirPlatform(2000, GameConfig.MAP_H - 600, 925, "floating_platform.png");
        // --- 若干跳台（简单矩形，可走） ---



    }

    /* ========== 背景 ========== */
    private static void addBackground() {
        try {
            Texture bg = getAssetLoader().loadTexture(GameConfig.BG_IMAGE);
            bg.setFitWidth(GameConfig.MAP_W);
            bg.setFitHeight(GameConfig.MAP_H);
            entityBuilder().at(0, 0).view(bg).zIndex(-1200).buildAndAttach();
        } catch (Exception e) {
            // 回退：简易渐变色块
            Rectangle sky = new Rectangle(GameConfig.MAP_W, GameConfig.MAP_H);
            sky.setFill(Color.web("#78c3a3"));
            entityBuilder().at(0, 0).view(sky).zIndex(-1200).buildAndAttach();
        }
    }
    //空中跳跃平台
    private static void buildAirPlatform(double x, double topY, double width, String textureName) {
        try {
            Texture platformTex = getAssetLoader().loadTexture(textureName);
            platformTex.setSmooth(false);
            platformTex.setFitWidth(width);

            PhysicsComponent phy = new PhysicsComponent();
            phy.setBodyType(BodyType.STATIC);

            entityBuilder()
                    .type(GameType.PLATFORM)
                    .at(ipx(x), ipx(topY))
                    .viewWithBBox(platformTex)
                    .with(new CollidableComponent(true))
                    .with(phy)
                    .zIndex(-50)
                    .buildAndAttach();
        } catch (Exception e) {
            solidPlatform(x, topY, width, 30, Color.web("#666666"));
        }
    }
    /** 在 x/topY 处拼接 ground_base.png（1646x211）直到覆盖给定 width；碰撞顶线=topY */
    private static void buildMainGroundStrip(double x, double topY, double width) {
        final int GRASS = 30;   // 想“脚更沉”就加大，反之减小

        Texture seg;
        try {
            seg = getAssetLoader().loadTexture(GameConfig.G_BASE_STRIP);
            seg.setSmooth(false);
        } catch (Exception e) {
            solidPlatform(x, topY, width, 211, javafx.scene.paint.Color.web("#3b2f2f"));
            return;
        }

        double sw = seg.getImage().getWidth();   // 1646
        double sh = seg.getImage().getHeight();  // 211

        // 2) 视觉：平铺，不拉伸
        javafx.scene.Group view = new javafx.scene.Group();
        double cur = 0;
        while (cur < width) {
            Texture t = new Texture(seg.getImage());
            t.setSmooth(false);
            t.setX(ipx(cur));
            t.setY(0);
            view.getChildren().add(t);
            cur += sw;
        }

        // 3) 碰撞：用“带偏移”的 HitBox，顶面下移 GRASS 像素
        //    —— 关键在这里，不再用 viewWithBBox(Rectangle) ——
        com.almasb.fxgl.physics.PhysicsComponent phy = new com.almasb.fxgl.physics.PhysicsComponent();
        phy.setBodyType(com.almasb.fxgl.physics.box2d.dynamics.BodyType.STATIC);

        // 需要的导入：
        // import com.almasb.fxgl.physics.HitBox;
        // import com.almasb.fxgl.physics.BoundingShape;
        // import javafx.geometry.Point2D;

        com.almasb.fxgl.physics.HitBox hb = new com.almasb.fxgl.physics.HitBox(
                "GROUND",
                new javafx.geometry.Point2D(0, GRASS),                       // 向下偏移
                com.almasb.fxgl.physics.BoundingShape.box(width, sh - GRASS) // 高度扣掉草沿
        );

        entityBuilder()
                .type(GameType.GROUND)
                .at(ipx(x), ipx(topY))   // 仍然以草沿顶作为视觉基线
                .view(view)              // 只设置视图
                .bbox(hb)                // 单独设置“有偏移”的碰撞盒
                .with(new com.almasb.fxgl.entity.components.CollidableComponent(true))
                .with(phy)
                .zIndex(-100)
                .buildAndAttach();
    }

    /* ========== 实心平台（产生碰撞 + 简单可见色块） ========== */
    private static void solidPlatform(double x, double y, double w, double h, Color viewColor) {
        Rectangle rect = new Rectangle(w, h);
        rect.setFill(viewColor);
        rect.setStroke(Color.BLACK);

        PhysicsComponent phy = new PhysicsComponent();
        phy.setBodyType(BodyType.STATIC);

        entityBuilder()
                .type(GameType.PLATFORM)
                .at(x, y)
                .viewWithBBox(rect)
                .with(new CollidableComponent(true))
                .with(phy)
                .zIndex(-100)     // 在玩家之下
                .buildAndAttach();
    }

    /* ========== 地面：底层(可走+碰撞) + 草沿遮挡层（覆盖玩家） ========== */
    private static void groundWithOverlay(double x, double topY, double width, double thickness) {
        // 1) 碰撞与基础可见层（Base）
        Entity base = createGroundBase(x, topY, width, thickness);

        // 2) 草沿遮挡层（Overlay）
        createEdgeOverlay(x, topY, width);
    }

    private static Entity createGroundBase(double x, double topY, double w, double h) {
        // 视图：尽量用贴图，否则色块回退
        Group viewGroup = new Group();

        boolean usedTexture = false;
        try {
            Texture baseTex = getAssetLoader().loadTexture(GameConfig.GROUND_BASE);
            // 基础层可直接等比拉伸填满（后续再做 tile 版）
            baseTex.setFitWidth(w);
            baseTex.setFitHeight(h);
            viewGroup.getChildren().add(baseTex);
            usedTexture = true;
        } catch (Exception ignore) { }

        if (!usedTexture) {
            Rectangle fill = new Rectangle(w, h);
            fill.setFill(Color.web("#3b2f2f"));
            fill.setStroke(Color.BLACK);
            viewGroup.getChildren().add(fill);
        }

        PhysicsComponent phy = new PhysicsComponent();
        phy.setBodyType(BodyType.STATIC);

        return entityBuilder()
                .type(GameType.GROUND)
                .at(x, topY)
                .viewWithBBox(viewGroup)   // 可见 + 碰撞
                .with(new CollidableComponent(true))
                .with(phy)
                .zIndex(-100)              // 在玩家之下
                .buildAndAttach();
    }

    private static void createEdgeOverlay(double x, double topY, double w) {
        // 草沿遮挡层：放在“玩家 zIndex 之上”，实现遮挡脚
        // 贴图允许按宽度等比缩放；如果没有贴图就用一条深色条回退
        try {
            Texture edge = getAssetLoader().loadTexture(GameConfig.GROUND_EDGE);
            double edgeH = edge.getImage().getHeight();

            // 只需要“上沿”那一部分：如果你的边沿图包含底部，请按需要裁切
            // 这里演示：取整张（可按需用 setViewport 做裁剪）
            // edge.setViewport(new Rectangle2D(0, 0, edge.getImage().getWidth(), Math.min(edgeH, 64)));

            edge.setPreserveRatio(true);
            edge.setFitWidth(w);               // 等比缩放到平台宽
            // 让上沿压住地面一点点（负 offset）
            double y = topY - (edge.getFitHeight() > 0 ? edge.getFitHeight() - 4 : (edgeH - 4));

            entityBuilder()
                    .at(x, y)
                    .view(edge)
                    .zIndex(1100)             // 高于玩家（假设玩家 1000）
                    .buildAndAttach();
        } catch (Exception e) {
            // 回退：一条深色“草沿”
            Rectangle rim = new Rectangle(w, 18);
            rim.setFill(Color.web("#1e1e1e"));
            entityBuilder()
                    .at(x, topY - 10)
                    .view(rim)
                    .zIndex(1100)
                    .buildAndAttach();
        }
    }
}
