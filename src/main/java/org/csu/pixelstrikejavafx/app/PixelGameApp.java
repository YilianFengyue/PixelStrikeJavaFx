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
import org.csu.pixelstrikejavafx.content.CharacterRegistry;
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

// [NEW] 网络
import org.csu.pixelstrikejavafx.net.NetClient;
import javafx.application.Platform;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


import org.csu.pixelstrikejavafx.player.RemoteAvatar; // ★ 远端影子动画
import org.csu.pixelstrikejavafx.content.CharacterRegistry; // 新增
// ...

public class PixelGameApp extends GameApplication {

    private Player player;

    private CameraFollow cameraFollow;
    //UI左下角
    private PlayerHUD hud;
    private double bootWarmup = 0.20; // 200ms 预热

    private final double GROUND_TOP_Y = 980;  // ← “脚踩的那条线”，不对就只改这个数
    private final double GROUND_H     = 211;  // ← 你的 ground_base.png 高度

    // [NEW] 网络
    private static final String WS_URL = "ws://localhost:81/ws/game";
    private NetClient netClient;
    private Integer myPlayerId = null;
    private boolean joinedAck = false;       // ← 新增：拿到 welcome 才算就绪
    private long seq = 1;                    // ← 新增：本地递增序号

    private static String CURRENT_CHAR_ID = "bluep_marthe"; // 先用默认，后面接大厅/热切再改

    private String myName = "Player_" + System.currentTimeMillis();

    // [NEW] 发送频率控制（60Hz → 0.0167s；可改 30Hz=0.033）
    private double sendTimer = 0;
    private static final double SEND_INTERVAL = 1.0 / 60.0;

    // [NEW] 远端玩家容器
    private final Map<Integer, RemotePlayer> remotePlayers = new ConcurrentHashMap<>();
    private final java.util.Map<Integer, Long> leftBarrier = new java.util.concurrent.ConcurrentHashMap<>();
    // 【NEW】记录本次欢迎的服务器时刻，作为“代际栅栏”
    private long welcomeSrvTS = 0L;



    private static class RemotePlayer {
        Entity entity;
        RemoteAvatar avatar;     // ★ 新增：影子动画
        long lastSeq = -1;   // ★ 新增：记录最新处理到的state序号（单调递增过滤）
        // 插值目标
        double targetX, targetY;
        boolean targetFacing;

        // 状态同步字段（来自网络）
        boolean onGround;        // ★ 新增
        double lastVX, lastVY;   // ★ 新增
        String anim, phase;      // ★ 新增

        long lastUpdate = System.currentTimeMillis();

        String charId;   // ★ 新增：记录这个远端玩家的当前角色ID
        RemotePlayer(Entity e, RemoteAvatar a) {  // ★ 新构造
            this.entity = e;
            this.avatar = a;
        }
    }

    @Override
    protected void initSettings(GameSettings s) {
        s.setWidth(GameConfig.WINDOW_W);
        s.setHeight(GameConfig.WINDOW_H);
        s.setTitle("PixelStrike - Map & Camera");

        s.setPixelsPerMeter(GameConfig.PPM);
        s.setMainMenuEnabled(false);
        s.setGameMenuEnabled(false);
        s.setScaleAffectedOnResize(true);

        s.setApplicationMode(ApplicationMode.DEVELOPER);
        s.setDeveloperMenuEnabled(true);

        // ★ FXGL 21.1 支持：固定逻辑时步 60Hz（物理/组件按该步进）
        s.setTicksPerSecond(60);
    }


    @Override
    protected void initGame() {
        getPhysicsWorld().setGravity(0, 1100);

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

        // [NEW] 连接服务器
        connectToServer();

    }
    @Override
    protected void initUI() {
        Image avatar = null;
        try { avatar = getAssetLoader().loadImage("ash_avatar.png"); } catch (Exception ignored) {}

        hud = new PlayerHUD(
                avatar,
                null,  // onSpawnP2  -> null                     // 生成/重生 P2
                () -> { if (player != null) player.die(); },    // 击杀自己
                () -> { if (player != null) {                   // 复活自己
                    player.revive();
                    player.onRevived();
                }},
                null   // onP2Shoot -> null
        );

        Region uiRoot = (Region) getGameScene().getRoot();
        hud.getRoot().prefWidthProperty().bind(uiRoot.widthProperty());
        hud.getRoot().prefHeightProperty().bind(uiRoot.heightProperty());
        getGameScene().addUINode(hud.getRoot());
        // 关键：把键盘焦点交还给游戏根节点，让空格走到 FXGL 的输入系统
        getGameScene().getRoot().requestFocus();

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
        // ★ 统一小步长，首帧再大也只按 1/60 走本地逻辑
        final double dt = Math.min(tpf, 1.0 / 60.0);

        if (bootWarmup > 0) {
            bootWarmup -= dt;

            if (player != null) player.update(dt);
            if (cameraFollow != null) cameraFollow.update();
            if (hud != null && player != null) {
                hud.updateHP(player.getHealth().getHp(), player.getHealth().getMaxHp());
            }

            // ★ 预热期同样用 dt，避免网络/远端在首帧“加速”
            pumpNetwork(dt);          // ★ from tpf -> dt
            updateRemotePlayers(dt);  // ★ from tpf -> dt
            return;
        }

        if (player != null) player.update(dt);
        if (cameraFollow != null) cameraFollow.update();
        if (hud != null && player != null) {
            hud.updateHP(player.getHealth().getHp(), player.getHealth().getMaxHp());
        }

        pumpNetwork(dt);          // ★ from tpf -> dt
        updateRemotePlayers(dt);  // ★ from tpf -> dt
    }

    private void setupPlayer() {
        var ch = org.csu.pixelstrikejavafx.content.CharacterRegistry.get(CURRENT_CHAR_ID); // 先写死
        player = new Player(500, GameConfig.MAP_H - 211 - 128, ch);

        player.getShootingSys().setShotReporter((ox,oy,dx,dy,range,dmg,ts)->{
            if (netClient != null && joinedAck) {
                netClient.sendShot(ox,oy,dx,dy,range,dmg,ts, seq++);
            }
        });
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



    // [NEW]
    private void updateRemotePlayers(double tpf) {
        long now = System.currentTimeMillis();
        java.util.List<Integer> toRemove = new java.util.ArrayList<>();

        for (var entry : remotePlayers.entrySet()) {
            int rid = entry.getKey();
            RemotePlayer rp = entry.getValue();
            if (rp == null || rp.entity == null) { toRemove.add(rid); continue; }

            // ★ 短超时：1.2s 无更新直接回收
            if (now - rp.lastUpdate > 1200) {
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
    // [NEW]
    private void connectToServer() {
        System.out.println("=== WS connect: " + WS_URL + " as " + myName);
        netClient = new NetClient();
        netClient.connect(WS_URL,
                () -> {
                    netClient.sendJoin(myName, CURRENT_CHAR_ID);   // ★ 改这里：带上当前选角
                },
                msg -> Platform.runLater(() -> handleServerMessage(msg))
        );
    }

    // 2) pumpNetwork：没有 welcome 前一律不发
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

    // PixelGameApp.java —— 完整替换 handleServerMessage
    private void handleServerMessage(String json) {
        try {
            String type = extractString(json, "\"type\":\"");
            if (type == null) return;

            switch (type) {
                case "welcome" -> {
                    // 清旧影子
                    remotePlayers.values().forEach(rp -> { if (rp.entity != null) rp.entity.removeFromWorld(); });
                    remotePlayers.clear();

                    leftBarrier.clear();   // ☆ 新增：清除旧会话的离线栅栏

                    myPlayerId   = extractInt(json, "\"id\":");
                    welcomeSrvTS = extractLong(json, "\"serverTime\":");
                    joinedAck    = true;
                    System.out.println("WELCOME myId=" + myPlayerId + " srvTS=" + welcomeSrvTS);
                }

                case "state" -> {
                    if (!joinedAck) return;

                    long srvTS = readSrvTS(json);
                    // 代际栅栏：丢弃“早于 welcome 的旧时代包”
                    if (welcomeSrvTS > 0 && srvTS > 0 && srvTS < welcomeSrvTS) return;

                    int id = extractInt(json, "\"id\":");
                    if (id == 0 || (myPlayerId != null && id == myPlayerId)) return;

                    // ❗离线时间栅栏：丢弃 <= leave.srvTS 的迟到 state（防止幽灵复活）
                    Long barrier = leftBarrier.get(id);
                    if (barrier != null && srvTS <= barrier) {
                        // System.out.println("[BARRIER_DROP] id="+id+" srvTS="+srvTS+" <= "+barrier);
                        return;
                    }

                    double x = extractDouble(json, "\"x\":");
                    double y = extractDouble(json, "\"y\":");
                    double vx = extractDouble(json, "\"vx\":");
                    double vy = extractDouble(json, "\"vy\":");
                    boolean facing   = json.contains("\"facing\":true");
                    boolean onGround = json.contains("\"onGround\":true");
                    String  anim  = extractString(json, "\"anim\":\"");
                    String  phase = extractString(json, "\"phase\":\"");
                    long    seq   = extractLong(json, "\"seq\":");
                    //远端解析
                    String  charId = extractString(json, "\"charId\":\"");  // 可能为 null（兼容旧服务器）
                    upsertRemotePlayer(id, x, y, facing, charId);
                    RemotePlayer rp = remotePlayers.get(id);
                    if (rp == null) return;

                    // 单调过滤（乱序包不影响画面）
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
                    long srvTS = readSrvTS(json);
                    if (id != 0) leftBarrier.put(id, srvTS);     // ☆ 立“离线时间栅栏”
                    RemotePlayer rp = remotePlayers.remove(id);
                    if (rp != null && rp.entity != null) rp.entity.removeFromWorld();
                }

                default -> { /* ignore */ }
            }
        } catch (Exception e) {
            System.err.println("handleServerMessage error: " + e.getMessage());
        }
    }

    private String extractString(String json, String keyPrefix) {
        int i = json.indexOf(keyPrefix);
        if (i < 0) return null;
        int s = i + keyPrefix.length();
        int e = json.indexOf('"', s);
        if (e < 0) return null;
        return json.substring(s, e);
    }


    // 旧签名：保留，兼容旧消息；内部转调到带 charId 的版本
    private void upsertRemotePlayer(int id, double x, double y, boolean facing) {
        upsertRemotePlayer(id, x, y, facing, null);
    }

    // 新签名：携带 charId（可能为 null）
    private void upsertRemotePlayer(int id, double x, double y, boolean facing, String charId) {
        RemotePlayer rp = remotePlayers.get(id);
        if (rp == null) {
            // >>> 关键一行：没有 charId 就强制用 "shu"（想看谁就把 "shu" 改成 "angel_neng"/"bluep_marthe"/"ash"）
            RemoteAvatar avatar = new RemoteAvatar(
                    org.csu.pixelstrikejavafx.content.CharacterRegistry.get(
                            (charId != null) ? charId : "ash"
                    )
            );

            var ent = entityBuilder()
                    .at(x, y)
                    .view(avatar.view())
                    .zIndex(999)
                    .buildAndAttach();
            avatar.setFacingRight(facing);

            double hue = ((id * 57) % 360) / 360.0;
            var adj = new javafx.scene.effect.ColorAdjust();
            adj.setHue(hue * 2 - 1);
            adj.setSaturation(0.2);
            avatar.view().setEffect(adj);

            rp = new RemotePlayer(ent, avatar);
            rp.charId = (charId != null) ? charId : "ash";
            remotePlayers.put(id, rp);
            System.out.println("spawn remote " + id + " @(" + x + "," + y + ")");
        } else {
            if (charId != null && (rp.charId == null || !rp.charId.equals(charId))) {
                RemoteAvatar newAvatar = new RemoteAvatar(
                        org.csu.pixelstrikejavafx.content.CharacterRegistry.get(charId)
                );
                rp.entity.getViewComponent().clearChildren();
                rp.entity.getViewComponent().addChild(newAvatar.view());
                newAvatar.setFacingRight(facing);
                var oldFx = rp.avatar.view().getEffect();
                newAvatar.view().setEffect(oldFx);
                rp.avatar = newAvatar;
                rp.charId = charId;
            }
        }

        rp.targetX = x;
        rp.targetY = y;
        rp.targetFacing = facing;
        rp.lastUpdate = System.currentTimeMillis();
    }


    // [NEW]
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

    // 【NEW】统一读取消息里的服务器时刻（state 用 serverTime；shot/damage 用 srvTS）
    private long readSrvTS(String json) {
        long t = extractLong(json, "\"serverTime\":");
        if (t == 0L) t = extractLong(json, "\"srvTS\":");
        return t;
    }
    public static void main(String[] args) { launch(args); }
}
