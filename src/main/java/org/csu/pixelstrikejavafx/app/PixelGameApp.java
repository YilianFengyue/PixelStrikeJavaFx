package org.csu.pixelstrikejavafx.app;

import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.input.UserAction;
import com.almasb.fxgl.physics.CollisionHandler;
import javafx.scene.input.KeyCode;
import org.csu.pixelstrikejavafx.camera.CameraFollow;
import org.csu.pixelstrikejavafx.core.GameConfig;
import org.csu.pixelstrikejavafx.core.GameType;
import org.csu.pixelstrikejavafx.map.MapBuilder;
import org.csu.pixelstrikejavafx.player.Player;

import static com.almasb.fxgl.dsl.FXGL.*;

public class PixelGameApp extends GameApplication {

    private Player player;
    private CameraFollow cameraFollow;

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
    }

    @Override
    protected void initGame() {
        getPhysicsWorld().setGravity(0, 760);

        // 1) 地图（背景 + 地面 + 跳台）
        MapBuilder.buildLevel();

        // 2) 玩家
        setupPlayer();

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
    }

    @Override
    protected void onUpdate(double tpf) {
        if (player != null) player.update(tpf);
        if (cameraFollow != null) cameraFollow.update();
    }

    private void setupPlayer() {
        // 你已有的 Player 构造，摆个合理出生点（贴着地面）
        player = new Player(500, GameConfig.MAP_H - 211 - 128);  // MAP底 - 地面高 - 角色高
    }

    private void setupCollisionHandlers() {
        getPhysicsWorld().addCollisionHandler(new CollisionHandler(GameType.PLAYER, GameType.GROUND) {
            @Override protected void onCollisionBegin(Entity a, Entity b) { player.setOnGround(true); }
            @Override protected void onCollisionEnd(Entity a, Entity b)   { player.setOnGround(false); }
        });

        getPhysicsWorld().addCollisionHandler(new CollisionHandler(GameType.PLAYER, GameType.PLATFORM) {
            @Override protected void onCollisionBegin(Entity a, Entity b) { player.setOnGround(true); }
            @Override protected void onCollisionEnd(Entity a, Entity b)   { player.setOnGround(false); }
        });
    }

    public static void main(String[] args) { launch(args); }
}
