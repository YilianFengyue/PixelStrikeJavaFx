package org.csu.pixelstrikejavafx;

import com.almasb.fxgl.app.ApplicationMode;
import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.components.CollidableComponent;
import com.almasb.fxgl.input.UserAction;
import com.almasb.fxgl.physics.CollisionHandler;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.csu.pixelstrikejavafx.game.player.component.PoisonedComponent;
import org.csu.pixelstrikejavafx.game.player.component.SupplyDropComponent;
import org.csu.pixelstrikejavafx.game.services.NetworkService;
import org.csu.pixelstrikejavafx.game.services.PlayerManager;
import org.csu.pixelstrikejavafx.game.world.CameraFollow;
import org.csu.pixelstrikejavafx.game.core.GameConfig;
import org.csu.pixelstrikejavafx.game.core.GameType;
import org.csu.pixelstrikejavafx.game.world.MapBuilder;
import org.csu.pixelstrikejavafx.game.player.Player;
import org.csu.pixelstrikejavafx.game.player.RemoteAvatar;
import org.csu.pixelstrikejavafx.core.GlobalState;
import org.csu.pixelstrikejavafx.core.PixelStrikeSceneFactory;
import org.csu.pixelstrikejavafx.game.ui.PlayerHUD;
import org.csu.pixelstrikejavafx.lobby.ui.UIManager;
import org.csu.pixelstrikejavafx.game.player.component.BulletComponent;

import static com.almasb.fxgl.dsl.FXGL.*;

public class PixelGameApp extends GameApplication {

    private PlayerManager playerManager;
    private NetworkService networkService;
    private CameraFollow cameraFollow;
    private PlayerHUD hud;

    private double sendTimer = 0;
    private static final double SEND_INTERVAL = 1.0 / 60.0;

    public static class RemotePlayer {
        public Entity entity;
        public RemoteAvatar avatar;
        public long lastSeq = -1;
        public double targetX, targetY;
        public boolean targetFacing;
        public boolean onGround;
        public double lastVX, lastVY;
        public String anim, phase;
        public long lastUpdate = System.currentTimeMillis();
        public String charId;

        public RemotePlayer(Entity e, RemoteAvatar a) {
            this.entity = e;
            this.avatar = a;
        }
    }

    @Override
    protected void initSettings(GameSettings s) {
        s.setWidth(GameConfig.WINDOW_W);
        s.setHeight(GameConfig.WINDOW_H);
        s.setTitle("PixelStrike");
        s.setPixelsPerMeter(GameConfig.PPM);
        s.setMainMenuEnabled(true);
        s.setGameMenuEnabled(false);
        s.setSceneFactory(new PixelStrikeSceneFactory());
        s.setScaleAffectedOnResize(false);
        s.setApplicationMode(ApplicationMode.DEVELOPER);
        s.setDeveloperMenuEnabled(true);
        s.setManualResizeEnabled(true);
        s.setTicksPerSecond(60);
    }

    @Override
    protected void initGame() {
        getGameWorld().getEntitiesCopy().forEach(Entity::removeFromWorld);
        getGameScene().clearGameViews();
        getPhysicsWorld().clear();

        getPhysicsWorld().setGravity(0, 3200);
        MapBuilder.buildLevel();

        playerManager = new PlayerManager();
        networkService = new NetworkService(this::handleServerMessage);

        Player localPlayer = playerManager.createLocalPlayer(networkService, GlobalState.charId != null ? GlobalState.charId : "shu");

        var vp = getGameScene().getViewport();
        double zoom = 0.85;
        vp.setZoom(zoom);
        double zoomedViewWidth = getAppWidth() / zoom;
        double zoomedViewHeight = getAppHeight() / zoom;
        vp.setBounds(0, 0, (int)(GameConfig.MAP_W - zoomedViewWidth), (int)(GameConfig.MAP_H - zoomedViewHeight));

        cameraFollow = new CameraFollow(vp, GameConfig.MAP_W, GameConfig.MAP_H, zoomedViewWidth, zoomedViewHeight, getAppWidth(), getAppHeight());
        cameraFollow.setTarget(localPlayer.getEntity());

        setupCollisionHandlers();
        networkService.connect();
    }

    @Override
    protected void initInput() {
        getInput().addAction(new UserAction("Move Left") {
            @Override protected void onActionBegin() { if(getLocalPlayer() != null) getLocalPlayer().startMoveLeft(); }
            @Override protected void onActionEnd() { if(getLocalPlayer() != null) getLocalPlayer().stopMoveLeft(); }
        }, KeyCode.A);

        getInput().addAction(new UserAction("Move Right") {
            @Override protected void onActionBegin() { if(getLocalPlayer() != null) getLocalPlayer().startMoveRight(); }
            @Override protected void onActionEnd() { if(getLocalPlayer() != null) getLocalPlayer().stopMoveRight(); }
        }, KeyCode.D);

        getInput().addAction(new UserAction("Jump") {
            @Override protected void onActionBegin() { if(getLocalPlayer() != null) getLocalPlayer().jump(); }
        }, KeyCode.SPACE);

        getInput().addAction(new UserAction("Jump W") {
            @Override protected void onActionBegin() { if(getLocalPlayer() != null) getLocalPlayer().jump(); }
        }, KeyCode.W);

        getInput().addAction(new UserAction("Shoot") {
            @Override protected void onActionBegin() { if(getLocalPlayer() != null) getLocalPlayer().startShooting(); }
            @Override protected void onActionEnd() { if(getLocalPlayer() != null) getLocalPlayer().stopShooting(); }
        }, KeyCode.J);
    }

    private Player getLocalPlayer() {
        return playerManager != null ? playerManager.getLocalPlayer() : null;
    }


    @Override
    protected void initUI() {
        Platform.runLater(() -> {
            getGameScene().clearUINodes();
            hud = new PlayerHUD(UIManager.loadAvatar(GlobalState.avatarUrl), null,
                    () -> { if (getLocalPlayer() != null) getLocalPlayer().die(); },
                    () -> { if (getLocalPlayer() != null) getLocalPlayer().revive(); },
                    null
            );
            Region uiRoot = getGameScene().getRoot();
            hud.getRoot().prefWidthProperty().bind(uiRoot.widthProperty());
            hud.getRoot().prefHeightProperty().bind(uiRoot.heightProperty());
            getGameScene().addUINode(hud.getRoot());
            var backButton = new com.almasb.fxgl.ui.FXGLButton("返回大厅");
            backButton.setOnAction(e -> {
                networkService.sendLeaveMessage();
                getGameController().gotoMainMenu();
            });
            addUINode(backButton, 20, 20);
        });
    }

    @Override
    protected void onUpdate(double tpf) {
        Player localPlayer = getLocalPlayer();
        if (localPlayer == null) return;

        double dt = Math.min(tpf, 1.0 / 30.0);
        localPlayer.update(dt);

        if (cameraFollow != null) cameraFollow.update();
        if (hud != null) {
            hud.updateHP(localPlayer.getHealth().getHp(), localPlayer.getHealth().getMaxHp());
        }

        pumpNetwork(dt);
        updateRemotePlayers(dt);
    }

    private void pumpNetwork(double tpf) {
        sendTimer += tpf;
        if (sendTimer >= SEND_INTERVAL) {
            sendTimer = 0;
            Player localPlayer = getLocalPlayer();
            if (localPlayer != null && networkService.isJoinedAck()) {
                var e = localPlayer.getEntity();
                var phy = localPlayer.getPhysics();
                networkService.sendState(
                        e.getX(), e.getY(),
                        phy.getVelocityX(), phy.getVelocityY(),
                        localPlayer.getFacingRight(), localPlayer.isOnGround(),
                        localPlayer.getNetAnim(), localPlayer.getNetPhase()
                );
            }
        }
    }

    private void handleServerMessage(String json) {
        try {
            String type = extractString(json, "\"type\":\"");
            if (type == null) return;

            switch (type) {
                case "welcome":
                    playerManager.clearAllRemotePlayers();
                    networkService.setMyPlayerId(extractInt(json, "\"id\":"));
                    networkService.setWelcomeSrvTS(extractLong(json, "\"serverTime\":"));
                    networkService.setJoinedAck(true);
                    break;
                case "join_broadcast":
                    if (!networkService.isJoinedAck()) return;
                    int joinId = extractInt(json, "\"id\":");
                    if (networkService.getMyPlayerId() != null && joinId == networkService.getMyPlayerId()) return;
                    String joinCharId = extractString(json, "\"charId\":\"");
                    playerManager.updateRemotePlayer(joinId, 0.0, 0.0, true, "IDLE", "IDLE", 0.0, 0.0, false, 0, joinCharId);
                    break;
                case "state":
                    if (!networkService.isJoinedAck()) return;
                    long srvTS = readSrvTS(json);
                    if (networkService.getWelcomeSrvTS() > 0 && srvTS > 0 && srvTS < networkService.getWelcomeSrvTS()) return;
                    int stateId = extractInt(json, "\"id\":");
                    if (stateId == 0 || (networkService.getMyPlayerId() != null && stateId == networkService.getMyPlayerId())) return;
                    String stateCharId = extractString(json, "\"charId\":\"");
                    playerManager.updateRemotePlayer(
                            stateId,
                            extractDouble(json, "\"x\":"),
                            extractDouble(json, "\"y\":"),
                            json.contains("\"facing\":true"),
                            extractString(json, "\"anim\":\""),
                            extractString(json, "\"phase\":\""),
                            extractDouble(json, "\"vx\":"),
                            extractDouble(json, "\"vy\":"),
                            json.contains("\"onGround\":true"),
                            extractLong(json, "\"seq\":"),
                            stateCharId
                    );
                    break;
                case "shot":
                    if (!networkService.isJoinedAck()) return;
                    int attackerId = extractInt(json, "\"attacker\":");
                    if (networkService.getMyPlayerId() != null && attackerId != networkService.getMyPlayerId()) {
                        playShotEffect(
                                extractDouble(json, "\"ox\":"),
                                extractDouble(json, "\"oy\":"),
                                extractDouble(json, "\"dx\":"),
                                extractDouble(json, "\"dy\":"),
                                extractDouble(json, "\"range\":")
                        );
                    }
                    break;
                case "damage":
                    int victimId = extractInt(json, "\"victim\":");
                    boolean isDead = json.contains("\"dead\":true");
                    if (networkService.getMyPlayerId() != null && victimId == networkService.getMyPlayerId()) {
                        double kx = json.contains("\"kx\":") ? extractDouble(json, "\"kx\":") : 0.0;
                        double ky = json.contains("\"ky\":") ? extractDouble(json, "\"ky\":") : 0.0;
                        getLocalPlayer().applyHit(extractInt(json, "\"damage\":"), kx, ky);
                    } else {
                        RemotePlayer rp = playerManager.getRemotePlayers().get(victimId);
                        if (rp != null && rp.entity != null && isDead) {
                            rp.entity.setVisible(false);
                        }
                    }
                    break;

                case "respawn":
                    int respawnId = extractInt(json, "\"id\":");
                    double x = extractDouble(json, "\"x\":");
                    double y = extractDouble(json, "\"y\":");
                    if (networkService.getMyPlayerId() != null && respawnId == networkService.getMyPlayerId()) {
                        getLocalPlayer().reset(x, y);
                        getLocalPlayer().revive();
                    } else {
                        playerManager.removeRemotePlayer(respawnId);
                        playerManager.updateRemotePlayer(respawnId, x, y, true, "IDLE", "IDLE", 0.0, 0.0, true, 0, extractString(json, "\"charId\":\""));
                    }
                    break;
                case "game_over":
                    com.google.gson.JsonObject gameOverMsg = new com.google.gson.Gson().fromJson(json, com.google.gson.JsonObject.class);
                    if (gameOverMsg.has("results") && gameOverMsg.get("results").isJsonObject()) {
                        org.csu.pixelstrikejavafx.core.MatchResultsModel.setMatchResults(gameOverMsg.getAsJsonObject("results"));
                    }
                    getGameScene().getViewport().fade(() -> getDialogService().showMessageBox("游戏结束!", () -> getGameController().gotoMainMenu()));
                    break;

                case "leave":
                    playerManager.removeRemotePlayer(extractInt(json, "\"id\":"));
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void playShotEffect(double ox, double oy, double dx, double dy, double range) {
        var line = new Line(ox, oy, ox + dx * range, oy + dy * range);
        line.setStroke(Color.ORANGERED);
        line.setStrokeWidth(2);
        var fx = entityBuilder().view(line).buildAndAttach();
        FXGL.getGameTimer().runOnceAfter(fx::removeFromWorld, Duration.seconds(0.1));
    }


    private void updateRemotePlayers(double tpf) {
        long now = System.currentTimeMillis();
        playerManager.getRemotePlayers().entrySet().removeIf(entry -> {
            RemotePlayer rp = entry.getValue();
            if (now - rp.lastUpdate > 5000) { // 5秒超时
                if (rp.entity != null) rp.entity.removeFromWorld();
                return true;
            }
            if (rp.entity == null) return false;

            double curX = rp.entity.getX();
            double curY = rp.entity.getY();
            rp.entity.setPosition(curX + (rp.targetX - curX) * 15 * tpf, curY + (rp.targetY - curY) * 15 * tpf);

            if (rp.avatar != null) {
                rp.avatar.setFacingRight(rp.targetFacing);
                rp.avatar.playState(rp.anim, rp.phase, rp.lastVX, rp.onGround);
            }
            return false;
        });
    }

    private void setupCollisionHandlers() {
        java.util.function.BiConsumer<Entity, Boolean> setGround = (playerEntity, on) -> {
            if (playerEntity.getProperties().exists("playerRef")) {
                Object ref = playerEntity.getProperties().getObject("playerRef");
                if (ref instanceof Player p) {
                    p.setOnGround(on);
                }
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

    private String extractString(String json, String keyPrefix) { int i = json.indexOf(keyPrefix); if (i < 0) return null; int s = i + keyPrefix.length(); int e = json.indexOf('"', s); if (e < 0) return null; return json.substring(s, e); }
    private int extractInt(String json, String key) { int i = json.indexOf(key); if (i < 0) return 0; int s = i + key.length(); while (s < json.length() && (json.charAt(s)==' '||json.charAt(s)=='"')) s++; int e = s; while (e < json.length() && (Character.isDigit(json.charAt(e))||json.charAt(e)=='-')) e++; if (s == e) return 0; try { return Integer.parseInt(json.substring(s, e)); } catch (NumberFormatException ex) { return 0; } }
    private long extractLong(String json, String key) { int i = json.indexOf(key); if (i < 0) return 0L; int s = i + key.length(); while (s < json.length() && (json.charAt(s)==' '||json.charAt(s)=='"')) s++; int e = s; while (e < json.length() && (Character.isDigit(json.charAt(e))||json.charAt(e)=='-')) e++; if (s == e) return 0L; try { return Long.parseLong(json.substring(s, e)); } catch (NumberFormatException ex) { return 0L; } }
    private double extractDouble(String json, String key) { int i = json.indexOf(key); if (i < 0) return 0.0; int s = i + key.length(); while (s < json.length() && (json.charAt(s)==' '||json.charAt(s)=='"')) s++; int e = s; while (e < json.length()) { char c = json.charAt(e); if (("0123456789-+.eE").indexOf(c) >= 0) e++; else break; } if (s == e) return 0.0; try { return Double.parseDouble(json.substring(s, e)); } catch (NumberFormatException ex) { return 0.0; } }
    private long readSrvTS(String json) { long t = extractLong(json, "\"serverTime\":"); if (t == 0L) t = extractLong(json, "\"srvTS\":"); return t; }

    public static void main(String[] args) {
        launch(args);
    }
}