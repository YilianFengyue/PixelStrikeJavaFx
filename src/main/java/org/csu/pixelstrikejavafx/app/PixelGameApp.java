package org.csu.pixelstrikejavafx.app;

import com.almasb.fxgl.app.ApplicationMode;
import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.input.UserAction;
import com.almasb.fxgl.physics.CollisionHandler;
import javafx.scene.Node;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.input.KeyCode;
import org.csu.pixelstrikejavafx.camera.CameraFollow;
import org.csu.pixelstrikejavafx.core.GameConfig;
import org.csu.pixelstrikejavafx.core.GameType;
import org.csu.pixelstrikejavafx.map.MapBuilder;
import org.csu.pixelstrikejavafx.player.Player;
import javafx.scene.layout.Region;  // 文件头需要这行
import static com.almasb.fxgl.dsl.FXGL.*;
//简易UI逻辑
import io.github.palexdev.materialfx.controls.*; // 仅为编译友好
import javafx.scene.image.Image;
import org.csu.pixelstrikejavafx.ui.PlayerHUD;

public class PixelGameApp extends GameApplication {

    private Player player;
    private Player player2;
    private CameraFollow cameraFollow;
    //UI左下角
    private PlayerHUD hud;
    private double bootWarmup = 0.20; // 200ms 预热

    private final double GROUND_TOP_Y = 980;  // ← “脚踩的那条线”，不对就只改这个数
    private final double GROUND_H     = 211;  // ← 你的 ground_base.png 高度
    @Override
    protected void initSettings(GameSettings s) {
        s.setWidth(GameConfig.WINDOW_W);
        s.setHeight(GameConfig.WINDOW_H);
        s.setTitle("PixelStrike - Map & Camera");

        s.setPixelsPerMeter(GameConfig.PPM);
        s.setMainMenuEnabled(false);
        s.setGameMenuEnabled(false);
        s.setScaleAffectedOnResize(false);

        s.setApplicationMode(ApplicationMode.DEVELOPER); // 关键
        s.setDeveloperMenuEnabled(true);                 // 关键
        s.setScaleAffectedOnResize(true);
    }

    @Override
    protected void initGame() {
        getPhysicsWorld().setGravity(0, 1100);

        // 1) 地图（背景 + 地面 + 跳台）
        MapBuilder.buildLevel();

        // 2) 玩家
        setupPlayer();
//        setupPlayer2();         // ★ 新增 p2
        // 3) 相机
        var vp = getGameScene().getViewport();
        vp.setBounds(0, 0,
                (int)(GameConfig.MAP_W - getAppWidth()),
                (int)(GameConfig.MAP_H - getAppHeight()));
        cameraFollow = new CameraFollow(
                vp, GameConfig.MAP_W, GameConfig.MAP_H, getAppWidth(), getAppHeight());
        cameraFollow.setTarget(player.getEntity());

        // 4) 碰撞（落地）
        setupCollisionHandlers();


    }
    @Override
    protected void initUI() {
        Image avatar = null;
        try { avatar = getAssetLoader().loadImage("ash_avatar.png"); } catch (Exception ignored) {}

        hud = new PlayerHUD(
                avatar,
                () -> spawnOrRespawnP2(),                       // 生成/重生 P2
                () -> { if (player != null) player.die(); },    // 击杀自己
                () -> { if (player != null) {                   // 复活自己
                    player.revive();
                    player.onRevived();
                }},
                () -> {                                         // ★ P2 开火
                    if (player2 == null) spawnOrRespawnP2();
                    if (player2 != null) {
                        player2.startShooting();
                        runOnce(() -> { if (player2 != null) player2.stopShooting(); },
                                javafx.util.Duration.seconds(0.25));
                    }
                }
        );

        Region uiRoot = (Region) getGameScene().getRoot();
        hud.getRoot().prefWidthProperty().bind(uiRoot.widthProperty());
        hud.getRoot().prefHeightProperty().bind(uiRoot.heightProperty());
        getGameScene().addUINode(hud.getRoot());
    }
    @Override
    protected void initInput() {
        // 保留你原先的回调写法
        getInput().addAction(new UserAction("Move Left") {
            @Override protected void onActionBegin() { if (player != null) player.startMoveLeft(); }
            @Override protected void onActionEnd()   { if (player != null) player.stopMoveLeft(); }
        }, KeyCode.A);

        getInput().addAction(new UserAction("Move Right") {
            @Override protected void onActionBegin() { if (player != null) player.startMoveRight(); }
            @Override protected void onActionEnd()   { if (player != null) player.stopMoveRight(); }
        }, KeyCode.D);

        getInput().addAction(new UserAction("Jump") {
            @Override protected void onActionBegin() { if (player != null) player.jump(); }
        }, KeyCode.SPACE);
        getInput().addAction(new UserAction("Jump W") {
            @Override protected void onActionBegin() { if (player != null) player.jump(); }
        }, KeyCode.W);
        // 在现有输入后添加
        getInput().addAction(new UserAction("Shoot") {
            @Override protected void onActionBegin() {
                if (player != null) player.startShooting();
            }
            @Override protected void onActionEnd() {
                if (player != null) player.stopShooting();
            }
        }, KeyCode.J);  // 或者用鼠标左键


    }

    @Override
    protected void onUpdate(double tpf) {
        // 冷启动阶段逐步吃掉大 tpf：既不跳帧，也不让角色猛冲
        if (bootWarmup > 0) {
            bootWarmup -= tpf;
            // 预热期内，把给到你逻辑的 dt 钳得更小，且不响应突变
            double dt = Math.min(tpf, 1.0 / 60.0);
            if (player != null) player.update(dt);
            //Player2测试
            //Player2测试
            //Player2测试
            if (player2 != null) player2.update(dt);   // ★ 每帧也要驱动 P2
            if (cameraFollow != null) cameraFollow.update();
            // ★ 刷 HUD
            if (hud != null && player != null) {
                hud.updateHP(player.getHealth().getHp(), player.getHealth().getMaxHp());
            }
            return;
        }

        double dt = Math.min(tpf, 1.0 / 30.0);
        if (player != null) player.update(dt);
        //Player2测试
        //Player2测试
        //Player2测试
        if (player2 != null) player2.update(dt);       // ★ 关键：别忘了 P2
        if (cameraFollow != null) cameraFollow.update();
        // ★ 刷 HUD
        if (hud != null && player != null) {
            hud.updateHP(player.getHealth().getHp(), player.getHealth().getMaxHp());
        }
    }

    private void setupPlayer() {
        // 你已有的 Player 构造，摆个合理出生点（贴着地面）
        player = new Player(500, GameConfig.MAP_H - 211 - 128);  // MAP底 - 地面高 - 角色高
    }
    private void setupPlayer2() {
        player2 = new Player(1200, GameConfig.MAP_H - 211 - 128);

        // 直接对实体 view children 上色（不改 Player 源码）
        ColorAdjust ca = new ColorAdjust();
        ca.setHue(+0.35);
        var ent = player2.getEntity();
        if (ent != null) {
            var children = ent.getViewComponent().getChildren();
            if (children != null) {
                for (Node n : children) {
                    if (n != null) n.setEffect(ca);
                }
            } else {
//                ent.getViewComponent().setEffect(ca);
            }
        }
    }
    private void setupCollisionHandlers() {

        // 通用设置函数：把事件里真正碰撞的那个玩家设为 on/off ground
        java.util.function.BiConsumer<Entity, Boolean> setGround = (playerEntity, on) -> {
            Object ref = playerEntity.getProperties().getObject("playerRef");
            if (ref instanceof Player p) {
                p.setOnGround(on);
            }
        };

        // PLAYER vs GROUND
        getPhysicsWorld().addCollisionHandler(new CollisionHandler(GameType.PLAYER, GameType.GROUND) {
            @Override protected void onCollisionBegin(Entity a, Entity b) { setGround.accept(a, true);  }
            @Override protected void onCollisionEnd(Entity a, Entity b)   { setGround.accept(a, false); }
        });

        // PLAYER vs PLATFORM
        getPhysicsWorld().addCollisionHandler(new CollisionHandler(GameType.PLAYER, GameType.PLATFORM) {
            @Override protected void onCollisionBegin(Entity a, Entity b) { setGround.accept(a, true);  }
            @Override protected void onCollisionEnd(Entity a, Entity b)   { setGround.accept(a, false); }
        });
    }
    private void spawnOrRespawnP2() {
        if (player2 == null) {
            setupPlayer2();
        } else {
            player2.revive();
            player2.onRevived();
            player2.reset(1200, GameConfig.MAP_H - 211 - 128);
        }
    }
    public static void main(String[] args) { launch(args); }
}
