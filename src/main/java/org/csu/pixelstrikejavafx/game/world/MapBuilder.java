package org.csu.pixelstrikejavafx.game.world;

import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.components.CollidableComponent;
import com.almasb.fxgl.physics.PhysicsComponent;
import com.almasb.fxgl.physics.box2d.dynamics.BodyType;
import com.almasb.fxgl.texture.Texture;
import javafx.scene.Group;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Effect;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.csu.pixelstrikejavafx.game.core.GameConfig;
import org.csu.pixelstrikejavafx.game.core.GameType;
import org.csu.pixelstrikejavafx.game.player.component.BouncyComponent;
import org.csu.pixelstrikejavafx.game.player.component.FragileComponent;
import org.csu.pixelstrikejavafx.game.player.component.MovingComponent;

import static com.almasb.fxgl.dsl.FXGL.*;

public final class MapBuilder {

    private MapBuilder() {}

    private static double ipx(double v) { return Math.round(v); }

    public static void buildLevel(String mapName) {
        if (mapName == null || mapName.isEmpty()) {
            buildDefaultMap();
            return;
        }
        switch (mapName) {
            case "雪地哨站": buildSnowMap(); break;
            case "沙漠小镇": buildDesertMap(); break;
            case "神圣之地": buildHallowMap(); break;
            case "森林湖畔": buildForestLakeMap(); break;
            case "丛林遗迹": default: buildDefaultMap(); break;
        }
    }

    private static void buildDefaultMap() { // 丛林遗迹 - 加入了移动平台
        GameConfig.BG_IMAGE = "Jungle_background_2.png";
        addBackground();
        buildMainGroundStrip(0, GameConfig.MAP_H - 211, GameConfig.MAP_W);

        // 静态平台
        buildAirPlatform(800, GameConfig.MAP_H - 470, 600, null);
        buildAirPlatform(3200, GameConfig.MAP_H - 470, 600, null);

        // 【新设计】用一个水平移动的平台连接左右
        buildMovingPlatform(1600, GameConfig.MAP_H - 600, 500, 800, 0); // 移动800像素
    }

    private static void buildSnowMap() { // 雪地哨站 - 加入了易碎平台
        GameConfig.BG_IMAGE = "Snow_biome_background_9.png";
        addBackground();
        buildMainGroundStrip(0, GameConfig.MAP_H - 211, GameConfig.MAP_W);

        ColorAdjust iceEffect = new ColorAdjust(0.6, -0.2, 0.1, 0);

        // 稳定的平台
        buildAirPlatform(300, GameConfig.MAP_H - 500, 600, iceEffect);
        buildAirPlatform(2800, GameConfig.MAP_H - 550, 700, iceEffect);

        // 【新设计】一条由易碎冰块组成的危险路径
        buildFragilePlatform(1200, GameConfig.MAP_H - 650, 250, iceEffect);
        buildFragilePlatform(1600, GameConfig.MAP_H - 700, 250, iceEffect);
        buildFragilePlatform(2000, GameConfig.MAP_H - 650, 250, iceEffect);
    }

    private static void buildDesertMap() { // 沙漠小镇 - 加入了弹跳平台
        GameConfig.BG_IMAGE = "Desert_background_4.png";
        addBackground();
        buildMainGroundStrip(0, GameConfig.MAP_H - 211, GameConfig.MAP_W);

        ColorAdjust sandstoneEffect = new ColorAdjust(-0.1, 0, 0.05, 0);

        buildAirPlatform(500, GameConfig.MAP_H - 550, 1200, sandstoneEffect);
        buildAirPlatform(2300, GameConfig.MAP_H - 550, 1200, sandstoneEffect);

        // 一个高处的平台，需要通过下方的弹跳平台才能上去
        buildAirPlatform(1700, GameConfig.MAP_H - 950, 600, sandstoneEffect); // 进一步调高
        buildBouncyPlatform(1900, GameConfig.MAP_H - 400, 200, 1800.0); // 强大的弹力
    }
    private static void buildHallowMap() { // 神圣之地 - 调整高度并加入垂直移动平台
        GameConfig.BG_IMAGE = "Hallow_background_1.png";
        addBackground();
        buildMainGroundStrip(0, GameConfig.MAP_H - 211, GameConfig.MAP_W);

        DropShadow crystalGlow = new DropShadow(25, Color.rgb(255, 180, 255, 0.8));
        crystalGlow.setSpread(0.6);

        // 【高度调整】显著提高了第一个平台的高度
        buildAirPlatform(400, GameConfig.MAP_H - 650, 500, crystalGlow);

        // 【新设计】一个垂直移动的电梯平台，通往更高的区域
        buildMovingPlatform(1400, GameConfig.MAP_H - 700, 300, 0, -400); // 垂直向上移动400
        buildAirPlatform(1200, GameConfig.MAP_H - 1150, 500, crystalGlow); // 只有电梯能到达的高处
    }

    private static void buildFragilePlatform(double x, double y, double width, Effect effect) {
        Entity platform = buildAirPlatform(x, y, width, effect);
        platform.addComponent(new FragileComponent(Duration.seconds(0.5), Duration.seconds(1.0))); // 晃动0.5秒后，1秒内消失
    }

    private static void buildForestLakeMap() {
        GameConfig.BG_IMAGE = "Forest_background_9.png";
        addBackground();
        buildMainGroundStrip(0, GameConfig.MAP_H - 211, GameConfig.MAP_W);
        // 【新增特效】创建苔藓效果：调整色相偏绿，降低亮度
        ColorAdjust mossyEffect = new ColorAdjust();
        mossyEffect.setHue(0.3); // 绿色
        mossyEffect.setBrightness(-0.1);
        mossyEffect.setSaturation(0.2);

        buildAirPlatform(600, GameConfig.MAP_H - 600, 700, mossyEffect);
        buildAirPlatform(GameConfig.MAP_W - 1300, GameConfig.MAP_H - 600, 700, mossyEffect);
        buildAirPlatform(GameConfig.MAP_W / 2.0 - 400, GameConfig.MAP_H - 900, 800, mossyEffect);
    }

    private static void buildMovingPlatform(double x, double y, double width, double moveDistanceX, double moveDistanceY) {
        Entity platform = buildAirPlatform(x, y, width, null); // 先创建一个普通平台
        // 为这个平台添加移动组件
        platform.addComponent(new MovingComponent(moveDistanceX, moveDistanceY, 150.0)); // 150是移动速度
    }

    /**
     * 构建一个可以把玩家弹起来的平台。
     * @param bounceVelocity 弹起的垂直速度
     */
    private static void buildBouncyPlatform(double x, double y, double width, double bounceVelocity) {
        // 创建一个发光的特效来提示玩家
        DropShadow bounceGlow = new DropShadow(20, Color.LIMEGREEN);
        bounceGlow.setSpread(0.7);
        Entity platform = buildAirPlatform(x, y, width, bounceGlow);
        platform.addComponent(new BouncyComponent(bounceVelocity));
    }


    // vvvvvvvvvv 以下的辅助方法保持不变 vvvvvvvvvv

    private static void addBackground() {
        try {
            Texture bg = getAssetLoader().loadTexture(GameConfig.BG_IMAGE);
            bg.setFitWidth(GameConfig.MAP_W);
            bg.setFitHeight(GameConfig.MAP_H);
            entityBuilder().at(0, 0).view(bg).zIndex(-1200).buildAndAttach();
        } catch (Exception e) {
            Rectangle sky = new Rectangle(GameConfig.MAP_W, GameConfig.MAP_H);
            sky.setFill(Color.web("#78c3a3"));
            entityBuilder().at(0, 0).view(sky).zIndex(-1200).buildAndAttach();
        }
    }

    private static Entity buildAirPlatform(double x, double topY, double width, Effect platformEffect) {
        try {
            Texture platformTex = getAssetLoader().loadTexture("floating_platform.png");
            platformTex.setSmooth(false);
            platformTex.setFitWidth(width);
            if (platformEffect != null) {
                platformTex.setEffect(platformEffect);
            }
            PhysicsComponent phy = new PhysicsComponent();
            phy.setBodyType(BodyType.STATIC);

            // 【修改】将 entityBuilder 的结果返回
            return entityBuilder()
                    .type(GameType.PLATFORM)
                    .at(ipx(x), ipx(topY))
                    .viewWithBBox(platformTex)
                    .with(new CollidableComponent(true))
                    .with(phy)
                    .zIndex(-50)
                    .buildAndAttach();
        } catch (Exception e) {
            // 回退情况下，也返回创建的实体
            return solidPlatform(x, topY, width, 30, Color.web("#666666"));
        }
    }

    private static Entity solidPlatform(double x, double y, double w, double h, Color viewColor) {
        Rectangle rect = new Rectangle(w, h);
        rect.setFill(viewColor);
        rect.setStroke(Color.BLACK);
        PhysicsComponent phy = new PhysicsComponent();
        phy.setBodyType(BodyType.STATIC);

        // 【修改】返回创建的实体
        return entityBuilder()
                .type(GameType.PLATFORM)
                .at(x, y)
                .viewWithBBox(rect)
                .with(new CollidableComponent(true))
                .with(phy)
                .zIndex(-100)
                .buildAndAttach();
    }

    private static void buildMainGroundStrip(double x, double topY, double width) {
        final int GRASS = 30;
        Texture seg;
        try {
            seg = getAssetLoader().loadTexture(GameConfig.G_BASE_STRIP);
            seg.setSmooth(false);
        } catch (Exception e) {
            solidPlatform(x, topY, width, 211, Color.web("#3b2f2f"));
            return;
        }
        double sw = seg.getImage().getWidth();
        double sh = seg.getImage().getHeight();
        Group view = new Group();
        double cur = 0;
        while (cur < width) {
            Texture t = new Texture(seg.getImage());
            t.setSmooth(false);
            t.setX(ipx(cur));
            t.setY(0);
            view.getChildren().add(t);
            cur += sw;
        }
        PhysicsComponent phy = new PhysicsComponent();
        phy.setBodyType(BodyType.STATIC);
        com.almasb.fxgl.physics.HitBox hb = new com.almasb.fxgl.physics.HitBox(
                "GROUND",
                new javafx.geometry.Point2D(0, GRASS),
                com.almasb.fxgl.physics.BoundingShape.box(width, sh - GRASS)
        );
        entityBuilder()
                .type(GameType.GROUND)
                .at(ipx(x), ipx(topY))
                .view(view)
                .bbox(hb)
                .with(new CollidableComponent(true))
                .with(phy)
                .zIndex(-100)
                .buildAndAttach();
    }


}