package org.csu.pixelstrikejavafx;

import com.almasb.fxgl.animation.AnimationBuilder;
import com.almasb.fxgl.app.ApplicationMode;
import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.app.scene.Viewport;
import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.dsl.components.ProjectileComponent;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.components.CollidableComponent;
import com.almasb.fxgl.input.UserAction;
import com.almasb.fxgl.physics.CollisionHandler;
import com.almasb.fxgl.texture.Texture;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.effect.Glow;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;
import org.csu.pixelstrikejavafx.game.core.MusicManager;
import org.csu.pixelstrikejavafx.game.player.component.*;
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
import org.csu.pixelstrikejavafx.lobby.ui.dialog.DialogManager;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static com.almasb.fxgl.dsl.FXGL.*;

public class PixelGameApp extends GameApplication {

    private PlayerManager playerManager;
    private NetworkService networkService;

    // UI 和相机依然由主类管理
    private CameraFollow cameraFollow;
    private PlayerHUD hud;

    // 定时发送器
    private double sendTimer = 0;
    private static final double SEND_INTERVAL = 1.0 / 60.0;

    // 用于显示倒计时的UI组件和变量
    private Text gameTimerText;
    private int gameTimeRemainingSeconds = 0;
    private GridPane scoreboardGrid;
    private List<Map<String, Object>> currentScoreboardData = new ArrayList<>();

    // 远程玩家内部类定义保持不变，设为 public 以便 PlayerManager 访问
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
        s.setFullScreenAllowed(true);
        s.setFullScreenFromStart(true);
        s.setPixelsPerMeter(GameConfig.PPM);
        s.setMainMenuEnabled(true);
        s.setGameMenuEnabled(false);
        s.setSceneFactory(new PixelStrikeSceneFactory());
        s.setScaleAffectedOnResize(false);
        s.setApplicationMode(ApplicationMode.DEVELOPER);
        s.setDeveloperMenuEnabled(true);
        s.setManualResizeEnabled(true);
    }

    @Override
    protected void initGame() {
        playerManager = null;
        networkService = null;
        cameraFollow = null;
        if (hud != null) {
            getGameScene().removeUINode(hud.getRoot());
            hud = null;
        }
        playerManager = new PlayerManager();
        networkService = new NetworkService(this::handleServerMessage);
        getGameWorld().getEntitiesCopy().forEach(Entity::removeFromWorld);
        getGameScene().clearGameViews();
        getPhysicsWorld().clear();

        getPhysicsWorld().setGravity(0, 3200);
        runOnce(() -> {
            String mapToBuild = GlobalState.selectedMapName;
            System.out.println("Building level: " + mapToBuild);
            MapBuilder.buildLevel(mapToBuild);
        }, Duration.ZERO);


        int characterId = (GlobalState.selectedCharacterId != null) ? GlobalState.selectedCharacterId : 1;
        Player localPlayer = playerManager.createLocalPlayer(networkService, characterId);

        // 将完整的角色选择信息传递给 PlayerManager
        if (GlobalState.characterSelections != null) {
            playerManager.setCharacterSelections(GlobalState.characterSelections);
        }
        var vp = getGameScene().getViewport();
        double zoom = 0.85;
        vp.setZoom(zoom);

        // 计算缩放后的实际视口宽高
        double zoomedViewWidth = getAppWidth() / zoom;
        double zoomedViewHeight = getAppHeight() / zoom;

        // 使用缩放后的尺寸来设置边界 (这行依然重要，用于物理世界的边界)
        vp.setBounds(0, 0, (int)(GameConfig.MAP_W - zoomedViewWidth), (int)(GameConfig.MAP_H - zoomedViewHeight));

        // 将原始窗口尺寸 getAppWidth() 和 getAppHeight() 传递给 CameraFollow
        cameraFollow = new CameraFollow(vp, GameConfig.MAP_W, GameConfig.MAP_H, zoomedViewWidth, zoomedViewHeight, getAppWidth(), getAppHeight());

        cameraFollow.setTarget(localPlayer.getEntity());
        setupCollisionHandlers();

        MusicManager.getInstance().playInGameMusic();

        // 连接到服务器
        networkService.connect();
    }

    @Override
    protected void initInput() {

            // 【修改这里的 ESCAPE 键绑定】
        onKey(KeyCode.ESCAPE, "退出游戏", () -> {
            // 【修改】弹出确认对话框
            DialogManager.showConfirmation("确认退出", "您确定要退出游戏吗？", () -> {
                // 当用户点击“确认”时，执行安全退出逻辑
                Stage primaryStage = FXGL.getPrimaryStage();
                if (primaryStage != null) {
                    primaryStage.fireEvent(
                            new WindowEvent(primaryStage, WindowEvent.WINDOW_CLOSE_REQUEST)
                    );
                }
            });
        });

        getInput().addAction(new UserAction("Move Left") {
            @Override protected void onActionBegin() {
                // 正确做法：在动作被触发时，才去获取玩家对象
                Player localPlayer = playerManager.getLocalPlayer();
                if (localPlayer != null) localPlayer.startMoveLeft();
            }
            @Override protected void onActionEnd() {
                Player localPlayer = playerManager.getLocalPlayer();
                if (localPlayer != null) localPlayer.stopMoveLeft();
            }
        }, KeyCode.A);

        getInput().addAction(new UserAction("Move Right") {
            @Override protected void onActionBegin() {
                Player localPlayer = playerManager.getLocalPlayer();
                if (localPlayer != null) localPlayer.startMoveRight();
            }
            @Override protected void onActionEnd() {
                Player localPlayer = playerManager.getLocalPlayer();
                if (localPlayer != null) localPlayer.stopMoveRight();
            }
        }, KeyCode.D);

        getInput().addAction(new UserAction("Jump") {
            @Override protected void onActionBegin() {
                Player localPlayer = playerManager.getLocalPlayer();
                if (localPlayer != null) localPlayer.jump();
            }
        }, KeyCode.SPACE);

        getInput().addAction(new UserAction("Jump W") {
            @Override protected void onActionBegin() {
                Player localPlayer = playerManager.getLocalPlayer();
                if (localPlayer != null) localPlayer.jump();
            }
        }, KeyCode.W);

        getInput().addAction(new UserAction("Shoot") {
            @Override protected void onActionBegin() {
                Player localPlayer = playerManager.getLocalPlayer();
                if (localPlayer != null) localPlayer.startShooting();
            }
            @Override protected void onActionEnd() {
                Player localPlayer = playerManager.getLocalPlayer();
                if (localPlayer != null) localPlayer.stopShooting();
            }
        }, KeyCode.J);
    }

    @Override
    protected void initUI() {
        Platform.runLater(() -> {
            getGameScene().clearUINodes();

            hud = new PlayerHUD(
                    UIManager.loadAvatar(GlobalState.avatarUrl),
                    GlobalState.nickname
            );
            // (这段代码保持不变)
            Region uiRoot = getGameScene().getRoot();
            hud.getRoot().prefWidthProperty().bind(uiRoot.widthProperty());
            hud.getRoot().prefHeightProperty().bind(uiRoot.heightProperty());
            getGameScene().addUINode(hud.getRoot());

            AnchorPane topBar = new AnchorPane();
            topBar.setPrefWidth(getAppWidth());

            // 2. 初始化游戏计时器
            gameTimerText = new Text("5:00");
            gameTimerText.setFont(Font.font("Press Start 2P", 36));
            gameTimerText.setFill(Color.WHITE);
            gameTimerText.setStroke(Color.BLACK);
            gameTimerText.setStrokeWidth(1.5);

            // 3. 初始化排行榜GridPane
            scoreboardGrid = new GridPane();
            scoreboardGrid.setAlignment(Pos.CENTER_RIGHT);
            scoreboardGrid.setHgap(15);
            scoreboardGrid.setVgap(5);

            // 4. 将计时器和排行榜都放入 AnchorPane 并精确定位
            VBox timerContainer = new VBox(gameTimerText);
            timerContainer.setAlignment(Pos.CENTER);
            timerContainer.setPadding(new Insets(10, 0, 10, 0));
            timerContainer.setStyle("-fx-background-color: rgba(0, 0, 0, 0.4); -fx-background-radius: 0 0 10 10;");

            VBox scoreboardContainer = new VBox(scoreboardGrid);
            scoreboardContainer.setAlignment(Pos.CENTER_RIGHT);
            scoreboardContainer.setPadding(new Insets(10, 20, 10, 20));
            scoreboardContainer.setStyle("-fx-background-color: rgba(0, 0, 0, 0.4); -fx-background-radius: 0 0 10 10;");

            topBar.getChildren().addAll(timerContainer, scoreboardContainer);

            // 定位计时器 (水平居中)
            AnchorPane.setTopAnchor(timerContainer, 0.0);
            AnchorPane.setLeftAnchor(timerContainer, (getAppWidth() / 2.0) - 100);
            AnchorPane.setRightAnchor(timerContainer, (getAppWidth() / 2.0) - 100);

            // 定位排行榜 (靠右)
            AnchorPane.setTopAnchor(scoreboardContainer, 0.0);
            AnchorPane.setRightAnchor(scoreboardContainer, 150.0);

            addUINode(topBar);

            // --- 返回和全屏按钮的逻辑保持不变 ---
            var backButton = new com.almasb.fxgl.ui.FXGLButton("返回大厅");
            var fullscreenButton = new com.almasb.fxgl.ui.FXGLButton("切换全屏");
            fullscreenButton.setOnAction(e -> FXGL.getPrimaryStage().setFullScreen(!FXGL.getPrimaryStage().isFullScreen()));
            addUINode(fullscreenButton, 150, 80); // 往下移动一点，避免和TopBar重叠
            backButton.setOnAction(e -> {
                networkService.sendLeaveMessage();
                MusicManager.getInstance().playMenuMusic();
                getGameController().gotoMainMenu();
            });
            addUINode(backButton, 150, 40); // 往下移动一点
        });
    }


    @Override
    protected void onUpdate(double tpf) {
        Player localPlayer = playerManager.getLocalPlayer();
        if (localPlayer == null) return;

        double dt = Math.min(tpf, 1.0 / 30.0);
        localPlayer.update(dt);

        if (cameraFollow != null) cameraFollow.update();
        if (hud != null) {
            hud.updateHP(localPlayer.getHealth().getHp(), localPlayer.getHealth().getMaxHp());
        }

        pumpNetwork(tpf);
        updateRemotePlayers(tpf);

        if (gameTimerText != null) {
            int minutes = gameTimeRemainingSeconds / 60;
            int seconds = gameTimeRemainingSeconds % 60;
            gameTimerText.setText(String.format("%d:%02d", minutes, seconds));
        }
    }


    private void pumpNetwork(double tpf) {
        sendTimer += tpf;
        if (sendTimer >= SEND_INTERVAL) {
            sendTimer = 0;
            Player localPlayer = playerManager.getLocalPlayer();
            if (localPlayer != null) {
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
            if (type == null) {
                System.err.println("Received a WebSocket message without a 'type' field: " + json);
                return; // 忽略这条无法处理的消息，防止崩溃
            }
            switch (type) {
                case "welcome" -> {
                    playerManager.clearAllRemotePlayers();
                    networkService.setMyPlayerId(extractInt(json, "\"id\":"));
                    networkService.setWelcomeSrvTS(extractLong(json, "\"serverTime\":"));
                    networkService.setJoinedAck(true);
                    System.out.println("WELCOME myId=" + networkService.getMyPlayerId() + " srvTS=" + networkService.getWelcomeSrvTS());
                }
                case "join_broadcast" -> {
                    if (!networkService.isJoinedAck()) return;
                    int id = extractInt(json, "\"id\":");
                    if (networkService.getMyPlayerId() != null && id == networkService.getMyPlayerId()) return;
                    playerManager.updateRemotePlayer(id, 0, 0, true, "IDLE", "IDLE", 0, 0, false, 0);
                }
                case "state" -> {
                    if (!networkService.isJoinedAck()) return;
                    long srvTS = readSrvTS(json);
                    if (networkService.getWelcomeSrvTS() > 0 && srvTS > 0 && srvTS < networkService.getWelcomeSrvTS()) return;

                    int id = extractInt(json, "\"id\":");
                    if (id == 0 || (networkService.getMyPlayerId() != null && id == networkService.getMyPlayerId())) return;

                    playerManager.updateRemotePlayer(
                            id,
                            extractDouble(json, "\"x\":"),
                            extractDouble(json, "\"y\":"),
                            json.contains("\"facing\":true"),
                            extractString(json, "\"anim\":\""),
                            extractString(json, "\"phase\":\""),
                            extractDouble(json, "\"vx\":"),
                            extractDouble(json, "\"vy\":"),
                            json.contains("\"onGround\":true"),
                            extractLong(json, "\"seq\":")
                    );
                }
                case "shot" -> {
                    if (!networkService.isJoinedAck()) return;
                    int attackerId = extractInt(json, "\"attacker\":");
                    if (networkService.getMyPlayerId() != null && attackerId != networkService.getMyPlayerId()) {
                        double ox = extractDouble(json, "\"ox\":");
                        double oy = extractDouble(json, "\"oy\":");
                        double dx = extractDouble(json, "\"dx\":");
                        double dy = extractDouble(json, "\"dy\":");
                        String weaponType = extractString(json, "\"weaponType\":\"");
                        switch (weaponType) {
                            case "Pistol":
                                play("pistol_shot.wav");
                                spawnRemotePistolBullet(attackerId, ox, oy, new Point2D(dx, dy));
                                break;
                            case "MachineGun":
                                play("machinegun_shot.wav");
                                spawnRemoteMachineGunBullet(attackerId, ox, oy, new Point2D(dx, dy));
                                break;
                            case "Shotgun":
                                play("shotgun_shot.wav");
                                spawnRemoteShotgunBlast(attackerId, ox, oy);
                                break;
                            case "Railgun":
                                play("railgun_shot.wav");
                                spawnRemoteRailgunBeam(attackerId, ox, oy, new Point2D(dx, dy));
                                break;
                            default:
                                System.err.println("Received unknown weaponType for remote shot: " + weaponType);
                                break;
                        }
                    }
                }
                case "damage" -> {
                    long srvTS = readSrvTS(json);
                    if (networkService.getWelcomeSrvTS() > 0 && srvTS > 0 && srvTS < networkService.getWelcomeSrvTS()) return;

                    int victimId = extractInt(json, "\"victim\":");
                    boolean isDead = json.contains("\"dead\":true");

                    // ★ 核心修复：无论是本地玩家还是远程玩家，都需要处理
                    if (networkService.getMyPlayerId() != null && victimId == networkService.getMyPlayerId()) {
                        // 是我被击中了
                        double kx = json.contains("\"kx\":") ? extractDouble(json, "\"kx\":") : (playerManager.getLocalPlayer().getFacingRight() ? -220.0 : 220.0);
                        double ky = json.contains("\"ky\":") ? extractDouble(json, "\"ky\":") : 0.0;
                        playerManager.getLocalPlayer().applyHit(
                                Math.max(1, extractInt(json, "\"damage\":")), kx, ky
                        );
                    } else {
                        // 远程玩家被击中逻辑
                        RemotePlayer remotePlayer = playerManager.getRemotePlayers().get(victimId);
                        if (remotePlayer != null && remotePlayer.entity != null) {
                            // 如果伤害消息表明该玩家已死亡，则触发他的死亡动画状态，而不是直接隐藏
                            if (isDead) {
                                System.out.println("Remote player " + victimId + " has died. Playing death animation.");
                                remotePlayer.anim = "DIE"; // 强制设置动画状态为DIE
                            }
                            // （可选）在这里也可以为远程玩家添加受击特效
                        }
                    }
                }
                case "respawn" -> {
                    int id = extractInt(json, "\"id\":");
                    double x = extractDouble(json, "\"x\":");
                    double y = extractDouble(json, "\"y\":");

                    if (networkService.getMyPlayerId() != null && id == networkService.getMyPlayerId()) {
                        playerManager.getLocalPlayer().reset(x, y);
                        playerManager.getLocalPlayer().revive();
                    } else {
                        // ★ 核心修复：远程玩家复活时，不仅要更新位置，还要确保模型可见
                        RemotePlayer remotePlayer = playerManager.getRemotePlayers().get(id);
                        if (remotePlayer != null && remotePlayer.entity != null) {
                            remotePlayer.entity.setPosition(x, y);
                            remotePlayer.entity.setVisible(true); // 确保模型恢复可见
                            System.out.println("Showing remote player " + id + " because they respawned.");
                        } else {
                            // 如果玩家不存在（可能是在死亡期间加入的），则直接创建
                            playerManager.updateRemotePlayer(id, x, y, true, "IDLE", "IDLE", 0, 0, true, 0);
                        }
                    }
                }

                case "game_over" -> {
                    System.out.println("Received game over message from server.");

                    com.google.gson.JsonObject gameOverMsg = new com.google.gson.Gson().fromJson(json, com.google.gson.JsonObject.class);

                    if (gameOverMsg.has("results") && gameOverMsg.get("results").isJsonObject()) {
                        org.csu.pixelstrikejavafx.core.MatchResultsModel.setMatchResults(
                                gameOverMsg.getAsJsonObject("results")
                        );
                        System.out.println("Match results updated in the model.");
                    } else {
                        org.csu.pixelstrikejavafx.core.MatchResultsModel.setMatchResults(null);
                    }

                    DialogManager.showFullScreenMessage("游戏结束!", "点击确认结算战绩", () -> {
                        getGameController().gotoMainMenu();
                    });
                }

                case "leave" -> playerManager.removeRemotePlayer(extractInt(json, "\"id\":"));
                case "health_update" -> {
                    int userId = extractInt(json, "\"userId\":");
                    int hp = extractInt(json, "\"hp\":");
                    if (networkService.getMyPlayerId() != null && userId == networkService.getMyPlayerId()) {
                        playerManager.getLocalPlayer().setHealth(hp);
                        System.out.println("Local player health updated to: " + hp);
                    }
                }
                case "supply_spawn" -> {
                    long dropId = extractLong(json, "\"dropId\":");
                    String dropType = extractString(json, "\"dropType\":\"");
                    double x = extractDouble(json, "\"x\":");
                    double y = extractDouble(json, "\"y\":");
                    spawnSupplyDrop(dropId, dropType, x, y);
                }
                case "supply_removed" -> {
                    long dropId = extractLong(json, "\"dropId\":");
                    // 查找并移除对应的实体
                    getGameWorld().getEntitiesByType(GameType.SUPPLY_DROP).stream()
                            .filter(e -> e.getComponent(SupplyDropComponent.class).getDropId() == dropId)
                            .findFirst()
                            .ifPresent(Entity::removeFromWorld);
                }
                case "weapon_equip" -> {
                    int userId = extractInt(json, "\"userId\":");
                    String weaponType = extractString(json, "\"weaponType\":\"");

                    if (networkService.getMyPlayerId() != null && userId == networkService.getMyPlayerId()) {
                        // 如果是本地玩家，调用切换武器的方法
                        playerManager.getLocalPlayer().getShootingSys().equipWeapon(weaponType);
                        DialogManager.showInGameNotification("装备了 " + weaponType, "rgba(52, 152, 219, 0.8)");
                    }
                    // 对于远程玩家，目前我们不需要做任何视觉上的改变，
                    // 但未来可以在这里更新他们手中的武器模型。
                }
                case "player_bombed" -> {
                    int userId = extractInt(json, "\"userId\":");
                    // 找到被炸的玩家实体
                    Entity targetEntity = null;
                    if (networkService.getMyPlayerId() != null && userId == networkService.getMyPlayerId()) {
                        targetEntity = playerManager.getLocalPlayer().getEntity();
                    } else {
                        RemotePlayer rp = playerManager.getRemotePlayers().get(userId);
                        if (rp != null) {
                            targetEntity = rp.entity;
                        }
                    }
                    // 如果找到了实体，就播放屏幕震动效果
                    if (targetEntity != null) {
                        getGameScene().getViewport().shakeTranslational(7.0);
                    }
                }
                case "pickup_notification" -> {
                    String pickerNickname = extractString(json, "\"pickerNickname\":\"");
                    String itemType = extractString(json, "\"itemType\":\"");
                    DialogManager.showInGameNotification(pickerNickname + " 拾取了 " + itemType + "!", "rgba(39, 174, 96, 0.8)");
                }
                case "player_poisoned" -> {
                    int userId = extractInt(json, "\"userId\":");
                    double duration = extractLong(json, "\"duration\":") / 1000.0; // 毫秒转秒

                    if (networkService.getMyPlayerId() != null && userId == networkService.getMyPlayerId()) {
                        // 为本地玩家添加中毒组件
                        playerManager.getLocalPlayer().getEntity().addComponent(new PoisonedComponent(duration));
                    }

                }
                case "scoreboard_update" -> {
                    JsonObject scoreboardMsg = JsonParser.parseString(json).getAsJsonObject();
                    if (scoreboardMsg.has("gameTimeRemainingSeconds")) {
                        this.gameTimeRemainingSeconds = scoreboardMsg.get("gameTimeRemainingSeconds").getAsInt();
                    }
                    JsonArray scoresArray = scoreboardMsg.getAsJsonArray("scores");
                    // 使用Gson将JsonArray解析为List<Map<String, Object>>
                    Type listType = new TypeToken<List<Map<String, Object>>>() {}.getType();
                    currentScoreboardData = new Gson().fromJson(scoresArray, listType);
                    updateScoreboardUI(); // 收到新数据后立即更新UI
                }
            }
        } catch (Exception e) {
            System.err.println("handleServerMessage error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void updateScoreboardUI() {
        Platform.runLater(() -> {
            if (scoreboardGrid == null) return;
            scoreboardGrid.getChildren().clear(); // 清空旧的战绩

            // 创建表头
            Text headerNickname = new Text("玩家");
            Text headerKills = new Text("击杀");
            Text headerDeaths = new Text("死亡");

            for (Text header : List.of(headerNickname, headerKills, headerDeaths)) {
                header.setFill(Color.GOLD);
                header.setStyle("-fx-font-family: 'Press Start 2P'; -fx-font-size: 14px;");
            }
            scoreboardGrid.add(headerNickname, 0, 0);
            scoreboardGrid.add(headerKills, 1, 0);
            scoreboardGrid.add(headerDeaths, 2, 0);

            // 根据新数据创建每一行
            int rowIndex = 1;
            for (Map<String, Object> playerData : currentScoreboardData) {
                String nickname = (String) playerData.get("nickname");
                int kills = ((Number) playerData.get("kills")).intValue();
                int deaths = ((Number) playerData.get("deaths")).intValue();

                Text nicknameText = new Text(nickname);
                Text killsText = new Text(String.valueOf(kills));
                Text deathsText = new Text(String.valueOf(deaths));

                // 统一设置样式
                for (Text text : List.of(nicknameText, killsText, deathsText)) {
                    text.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 16px;");
                    // 如果是当前玩家，则高亮显示
                    if (networkService.getMyPlayerId() != null && ((Number)playerData.get("id")).intValue() == networkService.getMyPlayerId()) {
                        text.setFill(Color.AQUAMARINE);
                    } else {
                        text.setFill(Color.WHITE);
                    }
                }

                scoreboardGrid.add(nicknameText, 0, rowIndex);
                scoreboardGrid.add(killsText, 1, rowIndex);
                scoreboardGrid.add(deathsText, 2, rowIndex);
                rowIndex++;
            }
        });
    }

    private void spawnSupplyDrop(long dropId, String dropType, double x, double y) {

        // 1. 加载你的 supply_drop.png 贴图
        Texture view = getAssetLoader().loadTexture("chest.png");
        view.setFitWidth(60);
        view.setFitHeight(60);

        // 2. 使用 DropShadow 效果来创建 "微微发光" 的边框
        //    一个模糊半径较大、颜色明亮的阴影可以很好地模拟光晕
        view.setEffect(new javafx.scene.effect.DropShadow(20, Color.YELLOW));

        // 3. 创建实体，并添加我们新的 FloatingComponent
        entityBuilder()
                .type(GameType.SUPPLY_DROP)
                .at(x, y - 30)
                .viewWithBBox(view)
                .with(new CollidableComponent(true))
                .with(new SupplyDropComponent(dropId, dropType))
                .with(new FloatingComponent(10, 2.5)) // <-- ★ 在此添加浮动组件
                .buildAndAttach();
    }

    private void updateRemotePlayers(double tpf) {
        long now = System.currentTimeMillis();
        playerManager.getRemotePlayers().entrySet().removeIf(entry -> {
            RemotePlayer rp = entry.getValue();
            if (now - rp.lastUpdate > 3000) {
                if (rp.entity != null) rp.entity.removeFromWorld();
                return true;
            }
            if (rp.entity == null) return false;

            double curX = rp.entity.getX();
            double curY = rp.entity.getY();
            double dx = rp.targetX - curX;
            double dy = rp.targetY - curY;

            if (Math.abs(dx) < 0.8) curX = rp.targetX;
            else curX = curX + dx * 10.0 * tpf;

            if (Math.abs(dy) < 0.8) curY = rp.targetY;
            else curY = curY + dy * 10.0 * tpf;

            rp.entity.setPosition(curX, curY);

            if (rp.avatar != null) {
                rp.avatar.setFacingRight(rp.targetFacing);
                rp.avatar.playState(rp.anim, rp.phase, rp.lastVX, rp.onGround);
            }
            return false;
        });
    }

    private void setupCollisionHandlers() {
        // 这个辅助方法用于设置玩家是否在地面上
        java.util.function.BiConsumer<Entity, Boolean> setGround = (playerEntity, on) -> {
            if (playerEntity.getProperties().exists("playerRef")) {
                Object ref = playerEntity.getProperties().getObject("playerRef");
                if (ref instanceof Player p) {
                    p.setOnGround(on);
                }
            }
        };

        // 1. 玩家与地面的碰撞
        getPhysicsWorld().addCollisionHandler(new CollisionHandler(GameType.PLAYER, GameType.GROUND) {
            @Override protected void onCollisionBegin(Entity a, Entity b) { setGround.accept(a, true);  }
            @Override protected void onCollisionEnd(Entity a, Entity b)   { setGround.accept(a, false); }
        });

        // 2. 【已修正】玩家与平台的碰撞（包含特殊平台逻辑）
        getPhysicsWorld().addCollisionHandler(new CollisionHandler(GameType.PLAYER, GameType.PLATFORM) {
            @Override
            protected void onCollisionBegin(Entity playerEntity, Entity platform) {
                // 默认的落地逻辑
                setGround.accept(playerEntity, true);

                // 检查平台是否有特殊功能
                if (platform.hasComponent(BouncyComponent.class)) {
                    Object ref = playerEntity.getProperties().getObject("playerRef");
                    if (ref instanceof Player p) {
                        double bounceVel = platform.getComponent(BouncyComponent.class).getBounceVelocity();
                        p.getPhysics().setVelocityY(-bounceVel);
                        play("bouncy_platform.wav");
                    }
                }
                if (platform.hasComponent(FragileComponent.class)) {
                    platform.getComponent(FragileComponent.class).trigger();
                }
            }

            @Override
            protected void onCollisionEnd(Entity playerEntity, Entity platform) {
                setGround.accept(playerEntity, false);
            }
        });

        // 3. 子弹与世界的碰撞 (保持不变)
        getPhysicsWorld().addCollisionHandler(new CollisionHandler(GameType.BULLET, GameType.GROUND) {
            @Override
            protected void onCollisionBegin(Entity bullet, Entity ground) {
                bullet.removeFromWorld();
            }
        });
        getPhysicsWorld().addCollisionHandler(new CollisionHandler(GameType.BULLET, GameType.PLATFORM) {
            @Override
            protected void onCollisionBegin(Entity bullet, Entity platform) {
                bullet.removeFromWorld();
            }
        });
        getPhysicsWorld().addCollisionHandler(new CollisionHandler(GameType.BULLET, GameType.PLAYER) {
            @Override
            protected void onCollisionBegin(Entity bullet, Entity playerEntity) {
                BulletComponent bulletData = bullet.getComponent(BulletComponent.class);
                if (bulletData.getShooter().equals(playerEntity)) {
                    return;
                }
                bullet.removeFromWorld();
            }
        });

        // 4. 【已修正】玩家与物资箱的碰撞 (恢复了正确的拾取逻辑)
        getPhysicsWorld().addCollisionHandler(new CollisionHandler(GameType.PLAYER, GameType.SUPPLY_DROP) {
            @Override
            protected void onCollisionBegin(Entity playerEntity, Entity dropEntity) {
                // 确保只有本地玩家的碰撞才会触发拾取
                if (playerManager.getLocalPlayer() != null && playerEntity == playerManager.getLocalPlayer().getEntity()) {
                    SupplyDropComponent dropData = dropEntity.getComponent(SupplyDropComponent.class);
                    // 向服务器发送拾取请求
                    networkService.sendSupplyPickup(dropData.getDropId());
                    // 客户端立即将物品从世界上移除，以提供即时反馈
                    dropEntity.removeFromWorld();
                }
            }
        });
    }

    private void spawnRemoteShotgunBlast(int shooterId, double ox, double oy) {
        final int PELLETS_COUNT = 8;
        final double SPREAD_ARC_DEG = 15.0;

        boolean facingRight = true;
        RemotePlayer remoteShooter = playerManager.getRemotePlayers().get(shooterId);
        if (remoteShooter != null) {
            facingRight = remoteShooter.targetFacing;
        }

        for (int i = 0; i < PELLETS_COUNT; i++) {
            double baseDeg = facingRight ? 0.0 : 180.0;
            double spread = ThreadLocalRandom.current().nextDouble(-SPREAD_ARC_DEG / 2, SPREAD_ARC_DEG / 2);
            double finalDeg = baseDeg + spread;
            double rad = Math.toRadians(finalDeg);
            Point2D direction = new Point2D(Math.cos(rad), Math.sin(rad)).normalize();
            // 调用新的、特定的方法
            spawnRemoteShotgunPellet(shooterId, ox, oy, direction);
        }
    }

    // 远程霰弹枪效果
    private void spawnRemoteShotgunPellet(int shooterId, double ox, double oy, Point2D direction) {
        final double BULLET_SPEED = 2000.0; // 与 Shotgun.java 一致
        Entity shooterEntity = null;
        RemotePlayer remoteShooter = playerManager.getRemotePlayers().get(shooterId);
        if (remoteShooter != null) {
            shooterEntity = remoteShooter.entity;
        }

        entityBuilder()
                .type(GameType.BULLET)
                .at(ox, oy)
                // 使用霰弹枪的颜色和大小
                .viewWithBBox(new Rectangle(6, 6, Color.DARKSLATEGRAY))
                .with(new CollidableComponent(true))
                .with(new ProjectileComponent(direction, BULLET_SPEED))
                .with(new BulletComponent(shooterEntity))
                .buildAndAttach();
    }

    // 生成远程射线枪效果
    private void spawnRemoteRailgunBeam(int shooterId, double ox, double oy, Point2D direction) {
        final double SHOOT_RANGE = 4000.0;
        Point2D end = new Point2D(ox, oy).add(direction.multiply(SHOOT_RANGE));

        Line laserBeam = new Line(ox, oy, end.getX(), end.getY());
        laserBeam.setStroke(Color.ORANGERED); // 远程射线用不同颜色
        laserBeam.setStrokeWidth(4);

        Entity laserEffect = FXGL.entityBuilder()
                .at(0, 0)
                .view(laserBeam)
                .buildAndAttach();

        FXGL.getGameTimer().runOnceAfter(laserEffect::removeFromWorld, Duration.seconds(0.1));
    }


    private void spawnRemotePistolBullet(int shooterId, double ox, double oy, Point2D direction) {
        final double BULLET_SPEED = 1500.0; // 与 Pistol.java 一致
        Entity shooterEntity = null;
        RemotePlayer remoteShooter = playerManager.getRemotePlayers().get(shooterId);
        if (remoteShooter != null) {
            shooterEntity = remoteShooter.entity;
        }

        entityBuilder()
                .type(GameType.BULLET)
                .at(ox, oy)
                // 使用手枪的颜色和大小
                .viewWithBBox(new Rectangle(10, 4, Color.ORANGERED))
                .with(new CollidableComponent(true))
                .with(new ProjectileComponent(direction, BULLET_SPEED))
                .with(new BulletComponent(shooterEntity))
                .buildAndAttach();
    }

    // 远程机枪效果
    private void spawnRemoteMachineGunBullet(int shooterId, double ox, double oy, Point2D direction) {
        final double BULLET_SPEED = 1800.0; // 与 MachineGun.java 一致
        Entity shooterEntity = null;
        RemotePlayer remoteShooter = playerManager.getRemotePlayers().get(shooterId);
        if (remoteShooter != null) {
            shooterEntity = remoteShooter.entity;
        }

        entityBuilder()
                .type(GameType.BULLET)
                .at(ox, oy)
                // 使用机枪的颜色和大小
                .viewWithBBox(new Rectangle(12, 3, Color.YELLOW))
                .with(new CollidableComponent(true))
                .with(new ProjectileComponent(direction, BULLET_SPEED))
                .with(new BulletComponent(shooterEntity))
                .buildAndAttach();
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

    private long readSrvTS(String json) {
        long t = extractLong(json, "\"serverTime\":");
        if (t == 0L) t = extractLong(json, "\"srvTS\":");
        return t;
    }

    private void centerText(Text text, double x, double y) {
        text.setX(x - text.getLayoutBounds().getWidth() / 2);
        text.setY(y);
    }

    public static void main(String[] args) {
        launch(args);
    }
}