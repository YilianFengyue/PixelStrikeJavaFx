package org.csu.pixelstrikejavafx.app;

import com.almasb.fxgl.app.ApplicationMode;
import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.input.UserAction;
import com.almasb.fxgl.physics.CollisionHandler;
import javafx.application.Platform;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Region;
import org.csu.pixelstrikejavafx.camera.CameraFollow;
import org.csu.pixelstrikejavafx.core.GameConfig;
import org.csu.pixelstrikejavafx.core.GameType;
import org.csu.pixelstrikejavafx.map.MapBuilder;
import org.csu.pixelstrikejavafx.net.NetClient;
import org.csu.pixelstrikejavafx.player.Player;
import org.csu.pixelstrikejavafx.player.RemoteAvatar;
import org.csu.pixelstrikejavafx.state.GlobalState;
import org.csu.pixelstrikejavafx.ui.PixelStrikeSceneFactory;
import org.csu.pixelstrikejavafx.ui.PlayerHUD;
import org.csu.pixelstrikejavafx.ui.UIManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.almasb.fxgl.dsl.FXGL.*;

public class PixelGameApp extends GameApplication {

    // ================== 从 PixelGameAppGame 迁移过来的字段 ==================
    private Player player;
    private CameraFollow cameraFollow;
    private PlayerHUD hud;
    private double bootWarmup = 0.20;

    // 网络相关
    // [MODIFIED] WS_URL 不再是固定的，将在游戏开始时从 GlobalState 获取
    private NetClient netClient;
    private Integer myPlayerId = null;
    private boolean joinedAck = false;
    private long seq = 1;

    private double sendTimer = 0;
    private static final double SEND_INTERVAL = 1.0 / 60.0;

    private final Map<Integer, RemotePlayer> remotePlayers = new ConcurrentHashMap<>();
    private long welcomeSrvTS = 0L;

    // 远程玩家内部类 (保持不变)
    private static class RemotePlayer {
        Entity entity;
        RemoteAvatar avatar;
        long lastSeq = -1;
        double targetX, targetY;
        boolean targetFacing;
        boolean onGround;
        double lastVX, lastVY;
        String anim, phase;
        long lastUpdate = System.currentTimeMillis();

        RemotePlayer(Entity e, RemoteAvatar a) {
            this.entity = e;
            this.avatar = a;
        }
    }

    // ================== GameApplication 生命周期方法 ==================

    @Override
    protected void initSettings(GameSettings s) {
        s.setWidth(GameConfig.WINDOW_W);
        s.setHeight(GameConfig.WINDOW_H);
        s.setTitle("PixelStrike");
        s.setPixelsPerMeter(GameConfig.PPM);

        // [MODIFIED] 启用主菜单，这是大厅UI的入口
        s.setMainMenuEnabled(true);
        s.setGameMenuEnabled(false);
        s.setSceneFactory(new PixelStrikeSceneFactory());
        s.setScaleAffectedOnResize(false);

        // 开发模式保持开启，便于调试
        s.setApplicationMode(ApplicationMode.DEVELOPER);
        s.setDeveloperMenuEnabled(true);
    }

    @Override
    protected void initGame() {
        // 清理旧的游戏状态（如果从一个游戏返回到菜单再进入新游戏）
        remotePlayers.values().forEach(rp -> {
            if (rp.entity != null) rp.entity.removeFromWorld();
        });
        remotePlayers.clear();
        joinedAck = false;
        myPlayerId = null;
        seq = 1;
        welcomeSrvTS = 0L;

        // --- 以下是原 initGame() 的内容 ---
        getPhysicsWorld().setGravity(0, 1100);
        MapBuilder.buildLevel();
        setupPlayer();

        var vp = getGameScene().getViewport();
        vp.setBounds(0, 0,
                (int)(GameConfig.MAP_W - getAppWidth()),
                (int)(GameConfig.MAP_H - getAppHeight()));
        cameraFollow = new CameraFollow(vp, GameConfig.MAP_W, GameConfig.MAP_H, getAppWidth(), getAppHeight());
        cameraFollow.setTarget(player.getEntity());

        setupCollisionHandlers();

        // [MODIFIED] 连接到游戏服务器
        connectToGameServer();
    }

    @Override
    protected void initInput() {
        // --- 以下是原 initInput() 的内容 ---
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

        getInput().addAction(new UserAction("Shoot") {
            @Override protected void onActionBegin() { if (player != null) player.startShooting(); }
            @Override protected void onActionEnd() { if (player != null) player.stopShooting(); }
        }, KeyCode.J);
    }

    @Override
    protected void initUI() {
        // --- 以下是原 initUI() 的内容 ---
        hud = new PlayerHUD(
                UIManager.loadAvatar(GlobalState.avatarUrl), // [MODIFIED] 使用UIManager加载全局头像
                null,
                () -> { if (player != null) player.die(); },
                () -> { if (player != null) {
                    player.revive();
                    player.onRevived();
                }},
                null
        );

        Region uiRoot = getGameScene().getRoot();
        hud.getRoot().prefWidthProperty().bind(uiRoot.widthProperty());
        hud.getRoot().prefHeightProperty().bind(uiRoot.heightProperty());
        getGameScene().addUINode(hud.getRoot());

        // [NEW] 添加一个返回大厅的按钮
        var backButton = new com.almasb.fxgl.ui.FXGLButton("返回大厅");
        backButton.setOnAction(e -> {
            // 断开游戏服务器连接
            if (netClient != null) {
                netClient.send("{\"type\":\"leave\"}");
            }
            // 返回主菜单（即我们的大厅UI）
            getGameController().gotoMainMenu();
        });
        addUINode(backButton, 20, 20);
    }

    @Override
    protected void onUpdate(double tpf) {
        // --- 以下是原 onUpdate() 的内容 ---
        if (bootWarmup > 0) {
            bootWarmup -= tpf;
            double dt = Math.min(tpf, 1.0 / 60.0);
            if (player != null) player.update(dt);
            if (cameraFollow != null) cameraFollow.update();
            if (hud != null && player != null) {
                hud.updateHP(player.getHealth().getHp(), player.getHealth().getMaxHp());
            }
            return;
        }

        double dt = Math.min(tpf, 1.0 / 30.0);
        if (player != null) player.update(dt);
        if (cameraFollow != null) cameraFollow.update();
        if (hud != null && player != null) {
            hud.updateHP(player.getHealth().getHp(), player.getHealth().getMaxHp());
        }

        pumpNetwork(tpf);
        updateRemotePlayers(tpf);
    }

    // ================== 从 PixelGameAppGame 迁移过来的方法 ==================

    private void setupPlayer() {
        player = new Player(500, GameConfig.MAP_H - 211 - 128);
        player.getShootingSys().setShotReporter((ox, oy, dx, dy, range, dmg, ts) -> {
            if (netClient != null && joinedAck) {
                netClient.sendShot(ox, oy, dx, dy, range, dmg, ts, seq++);
            }
        });
    }

    private void setupCollisionHandlers() {
        java.util.function.BiConsumer<Entity, Boolean> setGround = (playerEntity, on) -> {
            Object ref = playerEntity.getProperties().getObject("playerRef");
            if (ref instanceof Player p) {
                p.setOnGround(on);
            }
        };

        getPhysicsWorld().addCollisionHandler(new CollisionHandler(GameType.PLAYER, GameType.GROUND) {
            @Override protected void onCollisionBegin(Entity a, Entity b) { setGround.accept(a, true);  }
            @Override protected void onCollisionEnd(Entity a, Entity b)   { setGround.accept(a, false); }
        });

        getPhysicsWorld().addCollisionHandler(new CollisionHandler(GameType.PLAYER, GameType.PLATFORM) {
            @Override protected void onCollisionBegin(Entity a, Entity b) { setGround.accept(a, true);  }
            @Override protected void onCollisionEnd(Entity a, Entity b)   { setGround.accept(a, false); }
        });
    }

    private void connectToGameServer() {
        String baseUrl = GlobalState.currentGameServerUrl;
        if (baseUrl == null || baseUrl.isBlank()) {
            getDialogService().showMessageBox("错误：找不到游戏服务器地址！", () -> getGameController().gotoMainMenu());
            return;
        }

        String token = GlobalState.authToken;
        if (token == null) {
            getDialogService().showMessageBox("错误：无法连接游戏服务器，用户未认证！", () -> getGameController().gotoMainMenu());
            return;
        }

        Long gameId = GlobalState.currentGameId;
        if (gameId == null) {
            getDialogService().showMessageBox("错误：找不到游戏ID！", () -> getGameController().gotoMainMenu());
            return;
        }

        // URL 构建逻辑是正确的，保持不变
        String finalUrl = baseUrl + "?gameId=" + gameId + "&token=" + token;

        System.out.println("=== Connecting to game server with final URL: " + finalUrl);
        netClient = new NetClient();

        // --- 【核心修改】 ---
        netClient.connect(finalUrl,
                () -> {
                    // onOpen 回调变为空。我们不再需要发送任何消息。
                    // 服务器会在连接建立后自动处理我们的加入逻辑。
                    System.out.println("[WS] >> Connection opened. Waiting for 'welcome' message from server...");
                },
                msg -> Platform.runLater(() -> handleServerMessage(msg))
        );
    }

    private void handleServerMessage(String json) {
        try {
            String type = extractString(json, "\"type\":\"");
            if (type == null) return;

            switch (type) {
                case "welcome" -> {
                    // ★ 清表，防止旧影子
                    remotePlayers.values().forEach(rp -> { if (rp.entity != null) rp.entity.removeFromWorld(); });
                    remotePlayers.clear();

                    myPlayerId   = extractInt(json, "\"id\":");
                    welcomeSrvTS = extractLong(json, "\"serverTime\":");   // 【NEW】代际时间栅栏
                    joinedAck    = true;
                    System.out.println("WELCOME myId=" + myPlayerId + " srvTS=" + welcomeSrvTS);
                }

                case "join_broadcast" -> {
                    if (!joinedAck) return; // 必须在自己 welcome 之后才处理别人的加入

                    int id = extractInt(json, "\"id\":");
                    // 如果是自己的加入广播，则忽略
                    if (myPlayerId != null && id == myPlayerId) return;

                    System.out.println("A new player joined with id: " + id);
                    // 我们不知道新玩家的确切位置，暂时在 (0,0) 创建一个影子
                    // 服务器下一次广播这个新玩家的 state 时，它的位置就会被正确更新
                    upsertRemotePlayer(id, 0, 0, true);
                }

                case "state" -> {
                    if (!joinedAck) return;

                    // 【NEW】丢弃“早于 welcome 的旧时代包”
                    long srvTS = readSrvTS(json);
                    if (welcomeSrvTS > 0 && srvTS > 0 && srvTS < welcomeSrvTS) {
                        // System.out.println("DROP old state srvTS=" + srvTS);
                        return;
                    }

                    int id = extractInt(json, "\"id\":");
                    if (id == 0 || (myPlayerId != null && id == myPlayerId)) return;

                    // ...（原有解析与 upsertRemotePlayer 保持不变）...
                    double x = extractDouble(json, "\"x\":");
                    double y = extractDouble(json, "\"y\":");
                    double vx = extractDouble(json, "\"vx\":");
                    double vy = extractDouble(json, "\"vy\":");
                    boolean facing   = json.contains("\"facing\":true");
                    boolean onGround = json.contains("\"onGround\":true");
                    String  anim  = extractString(json, "\"anim\":\"");
                    String  phase = extractString(json, "\"phase\":\"");
                    long    seq   = extractLong(json, "\"seq\":");

                    upsertRemotePlayer(id, x, y, facing);
                    RemotePlayer rp = remotePlayers.get(id);
                    if (rp == null) return;

                    // 单调过滤
                    if (seq > 0 && seq <= rp.lastSeq) return;
                    rp.lastSeq = seq;

                    rp.onGround     = onGround;
                    rp.lastVX       = vx;
                    rp.lastVY       = vy;
                    rp.anim         = anim;
                    rp.phase        = phase;
                    rp.targetX      = x;
                    rp.targetY      = y;
                    rp.targetFacing = facing;
                    rp.lastUpdate   = System.currentTimeMillis();
                }

                case "shot" -> {
                    // 【NEW】代际栅栏
                    long srvTS = readSrvTS(json);
                    if (welcomeSrvTS > 0 && srvTS > 0 && srvTS < welcomeSrvTS) return;

                    double ox = extractDouble(json, "\"ox\":");
                    double oy = extractDouble(json, "\"oy\":");
                    double dx = extractDouble(json, "\"dx\":");
                    double dy = extractDouble(json, "\"dy\":");
                    double range = extractDouble(json, "\"range\":");
                    playShotEffect(ox, oy, dx, dy, range);
                }

                case "damage" -> {
                    // 【NEW】代际栅栏
                    long srvTS = readSrvTS(json);
                    if (welcomeSrvTS > 0 && srvTS > 0 && srvTS < welcomeSrvTS) return;

                    int victim = extractInt(json, "\"victim\":");
                    int dmg    = extractInt(json, "\"damage\":");
                    boolean hasKx = json.contains("\"kx\":");
                    boolean hasKy = json.contains("\"ky\":");
                    double kx  = hasKx ? extractDouble(json, "\"kx\":")
                            : (player != null && player.getFacingRight() ? -220.0 : 220.0);
                    double ky  = hasKy ? extractDouble(json, "\"ky\":") : 0.0;

                    if (myPlayerId != null && victim == myPlayerId && player != null) {
                        player.applyHit(Math.max(1, dmg), kx, ky);
                    }
                }

                case "leave" -> {
                    int id = extractInt(json, "\"id\":");
                    RemotePlayer rp = remotePlayers.remove(id);
                    if (rp != null && rp.entity != null) rp.entity.removeFromWorld();
                }

                default -> { /* ignore */ }
            }
        } catch (Exception e) {
            System.err.println("handleServerMessage error: " + e.getMessage());
        }
    }

    private void pumpNetwork(double tpf) {
        if (netClient == null || player == null || !joinedAck) return;  // ← 新增 joinedAck
        sendTimer += tpf;
        if (sendTimer >= SEND_INTERVAL) {
            sendTimer = 0;
            var e = player.getEntity();
            var phy = player.getPhysics();
            netClient.sendState(
                    e.getX(), e.getY(),
                    phy.getVelocityX(), phy.getVelocityY(),
                    player.getFacingRight(), player.isOnGround(),
                    player.getNetAnim(), player.getNetPhase(),      // ← 新增：把本地真实动画状态随 state 一起发；其余不动。
                    System.currentTimeMillis(), seq++
            );
        }
    }

    private void updateRemotePlayers(double tpf) {
        long now = System.currentTimeMillis();
        java.util.List<Integer> toRemove = new java.util.ArrayList<>();

        for (var entry : remotePlayers.entrySet()) {
            int rid = entry.getKey();
            RemotePlayer rp = entry.getValue();
            if (rp == null || rp.entity == null) { toRemove.add(rid); continue; }

            // ★ 短超时：3s 无更新直接回收
            if (now - rp.lastUpdate > 3000) {
                rp.entity.removeFromWorld();
                toRemove.add(rid);
                continue;
            }

            // ★ 温和插值 + 1px 死区，避免“抖1像素”
            double curX = rp.entity.getX();
            double curY = rp.entity.getY();
            double dx = rp.targetX - curX;
            double dy = rp.targetY - curY;

            if (Math.abs(dx) < 0.8) curX = rp.targetX; // ★ 死区：小于1px 直接贴到目标
            else curX = curX + dx * 10.0 * tpf;        //   否则按速度插值

            if (Math.abs(dy) < 0.8) curY = rp.targetY;
            else curY = curY + dy * 10.0 * tpf;

            rp.entity.setX(curX);
            rp.entity.setY(curY);

            // 朝向 & 动画
            if (rp.avatar != null) {
                rp.avatar.setFacingRight(rp.targetFacing);
                rp.avatar.playState(rp.anim, rp.phase, rp.lastVX, rp.onGround);
            }
        }

        // ★ 统一删除，遍历更稳
        for (int id : toRemove) {
            remotePlayers.remove(id);
        }
    }

    private void upsertRemotePlayer(int id, double x, double y, boolean facing) {
        RemotePlayer rp = remotePlayers.get(id);
        if (rp == null) {
            // ★ 影子动画
            RemoteAvatar avatar = new RemoteAvatar();

            // ★ 视图 = 动画贴图；初始朝向
            var ent = entityBuilder()
                    .at(x, y)
                    .view(avatar.view())
                    .zIndex(999)
                    .buildAndAttach();
            avatar.setFacingRight(facing);

            // ★ 按 id 稳定变色（可选）
            double hue = ((id * 57) % 360) / 360.0;      // 0..1
            var adj = new ColorAdjust();
            adj.setHue(hue * 2 - 1);                     // -1..1
            adj.setSaturation(0.2);
            avatar.view().setEffect(adj);

            rp = new RemotePlayer(ent, avatar);
            remotePlayers.put(id, rp);
            System.out.println("spawn remote " + id + " @(" + x + "," + y + ")");
        }
        // 更新插值目标
        rp.targetX = x;
        rp.targetY = y;
        rp.targetFacing = facing;
        rp.lastUpdate = System.currentTimeMillis();
    }

    private String extractString(String json, String keyPrefix) {
        int i = json.indexOf(keyPrefix);
        if (i < 0) return null;
        int s = i + keyPrefix.length();
        int e = json.indexOf('"', s);
        if (e < 0) return null;
        return json.substring(s, e);
    }

    private int extractInt(String json, String key) {
        int i = json.indexOf(key);
        if (i < 0) return 0;
        int s = i + key.length();
        while (s < json.length() && (json.charAt(s) == ' ' || json.charAt(s) == '"')) s++;
        int e = s;
        while (e < json.length() && (Character.isDigit(json.charAt(e)) || json.charAt(e) == '-')) e++;
        if (s == e) return 0;
        try { return Integer.parseInt(json.substring(s, e)); } catch (NumberFormatException ex) { return 0; }
    }
    /** 从JSON里解析 long（形如 "key":12345），失败返回0 */
    private long extractLong(String json, String key) {
        int i = json.indexOf(key);
        if (i < 0) return 0L;
        int s = i + key.length();
        while (s < json.length() && (json.charAt(s) == ' ' || json.charAt(s) == '"')) s++;
        int e = s;
        while (e < json.length() && (Character.isDigit(json.charAt(e)) || json.charAt(e) == '-')) e++;
        if (s == e) return 0L;
        try { return Long.parseLong(json.substring(s, e)); } catch (NumberFormatException ex) { return 0L; }
    }
    private double extractDouble(String json, String key) {
        int i = json.indexOf(key);
        if (i < 0) return 0.0;
        int s = i + key.length();
        while (s < json.length() && (json.charAt(s) == ' ' || json.charAt(s) == '"')) s++;
        int e = s;
        while (e < json.length()) {
            char c = json.charAt(e);
            if (("0123456789-+.eE").indexOf(c) >= 0) e++; else break;
        }
        if (s == e) return 0.0;
        try { return Double.parseDouble(json.substring(s, e)); } catch (NumberFormatException ex) { return 0.0; }
    }
    // 极小的射线特效工具
    private void playShotEffect(double ox, double oy, double dx, double dy, double range) {
        var line = new javafx.scene.shape.Line(0, 0, dx * range, dy * range);
        line.setStroke(javafx.scene.paint.Color.ORANGE);
        line.setStrokeWidth(3);
        line.setOpacity(0.85);
        var fx = entityBuilder().at(ox, oy).view(line).zIndex(2000).buildAndAttach();

        // ★ 用 JavaFX 的 Duration，别用 java.time.Duration
        runOnce(() -> { if (fx.isActive()) fx.removeFromWorld(); }, javafx.util.Duration.millis(100));
        //                      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^  ← 关键
    }

    private long readSrvTS(String json) {
        long t = extractLong(json, "\"serverTime\":");
        if (t == 0L) t = extractLong(json, "\"srvTS\":");
        return t;
    }

    public static void main(String[] args) {
        launch(args);
    }
}