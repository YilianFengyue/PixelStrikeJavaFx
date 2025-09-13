package org.csu.pixelstrikejavafx;

import com.almasb.fxgl.app.ApplicationMode;
import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.dsl.components.ProjectileComponent;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.components.CollidableComponent;
import com.almasb.fxgl.input.UserAction;
import com.almasb.fxgl.physics.CollisionHandler;
import javafx.geometry.Point2D;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.csu.pixelstrikejavafx.game.player.component.GrenadeComponent;
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
        s.setPixelsPerMeter(GameConfig.PPM);
        s.setMainMenuEnabled(true);
        s.setGameMenuEnabled(false);
        s.setSceneFactory(new PixelStrikeSceneFactory());
        s.setScaleAffectedOnResize(false);
        s.setApplicationMode(ApplicationMode.DEVELOPER);
        s.setDeveloperMenuEnabled(true);
    }

    @Override
    protected void initGame() {
        // --- 核心修改：初始化所有 Service ---
        playerManager = new PlayerManager();
        networkService = new NetworkService(this::handleServerMessage);
        getGameWorld().getEntities().forEach(Entity::removeFromWorld);
        getGameScene().clearGameViews();
        getPhysicsWorld().clear();

        getPhysicsWorld().setGravity(0, 1100);
        MapBuilder.buildLevel();

        Player localPlayer = playerManager.createLocalPlayer(networkService);

        var vp = getGameScene().getViewport();
        vp.setBounds(0, 0, (int)(GameConfig.MAP_W - getAppWidth()), (int)(GameConfig.MAP_H - getAppHeight()));
        cameraFollow = new CameraFollow(vp, GameConfig.MAP_W, GameConfig.MAP_H, getAppWidth(), getAppHeight());
        cameraFollow.setTarget(localPlayer.getEntity());

        setupCollisionHandlers();

        // 连接到服务器
        networkService.connect();
    }

    @Override
    protected void initInput() {

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

        getInput().addAction(new UserAction("Next Weapon") {
            @Override
            protected void onActionBegin() {
                Player localPlayer = playerManager.getLocalPlayer();
                if (localPlayer != null) {
                    localPlayer.getShootingSys().nextWeapon();
                }
            }
        }, KeyCode.Q); // 按 Q 切换武器
    }

    @Override
    protected void initUI() {
        getGameScene().clearUINodes();

        hud = new PlayerHUD( UIManager.loadAvatar(GlobalState.avatarUrl), null,
                () -> { if (playerManager.getLocalPlayer() != null) playerManager.getLocalPlayer().die(); },
                () -> { if (playerManager.getLocalPlayer() != null) playerManager.getLocalPlayer().revive(); },
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
            if (type == null) return;

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
                                spawnRemotePistolBullet(attackerId, ox, oy, new Point2D(dx, dy));
                                break;
                            case "MachineGun":
                                spawnRemoteMachineGunBullet(attackerId, ox, oy, new Point2D(dx, dy));
                                break;
                            case "Shotgun":
                                spawnRemoteShotgunBlast(attackerId, ox, oy);
                                break;
                            case "Railgun":
                                spawnRemoteRailgunBeam(attackerId, ox, oy, new Point2D(dx, dy));
                                break;
                            case "GrenadeLauncher":
                                spawnRemoteGrenade(attackerId, ox, oy, new Point2D(dx, dy));
                                break;
                            default:
                                // 保留一个默认行为以防万一，或者留空
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
                        // 是其他玩家被击中了
                        RemotePlayer remotePlayer = playerManager.getRemotePlayers().get(victimId);
                        if (remotePlayer != null && remotePlayer.entity != null) {
                            // 如果伤害消息表明该玩家已死亡，则隐藏其模型
                            if (isDead) {
                                System.out.println("Hiding remote player " + victimId + " because they died.");
                                remotePlayer.entity.setVisible(false);
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
                    // 服务器会统一处理所有玩家的游戏结束流程。
                    // 客户端只需等待，然后返回主菜单即可。
                    getGameScene().getViewport().fade(() -> getDialogService().showMessageBox("游戏结束!", () -> {
                        // networkService.sendLeaveMessage(); // 不再需要发送 leave 消息
                        getGameController().gotoMainMenu();  // 直接返回主菜单
                    }));
                }

                case "leave" -> playerManager.removeRemotePlayer(extractInt(json, "\"id\":"));
            }
        } catch (Exception e) {
            System.err.println("handleServerMessage error: " + e.getMessage());
            e.printStackTrace();
        }
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
        java.util.function.BiConsumer<Entity, Boolean> setGround = (playerEntity, on) -> {
            // 首先，使用正确的方法 .exists() 检查 'playerRef' 属性是否存在
            if (playerEntity.getProperties().exists("playerRef")) {
                // 只有当这个属性存在时（意味着这是本地玩家），才继续执行
                Object ref = playerEntity.getProperties().getObject("playerRef");
                if (ref instanceof Player p) {
                    p.setOnGround(on);
                }
            }
            // 如果属性不存在（意味着这是远程玩家），则什么也不做，优雅地跳过。
        };

        getPhysicsWorld().addCollisionHandler(new CollisionHandler(GameType.PLAYER, GameType.GROUND) {
            @Override protected void onCollisionBegin(Entity a, Entity b) { setGround.accept(a, true);  }
            @Override protected void onCollisionEnd(Entity a, Entity b)   { setGround.accept(a, false); }
        });

        getPhysicsWorld().addCollisionHandler(new CollisionHandler(GameType.PLAYER, GameType.PLATFORM) {
            @Override protected void onCollisionBegin(Entity a, Entity b) { setGround.accept(a, true);  }
            @Override protected void onCollisionEnd(Entity a, Entity b)   { setGround.accept(a, false); }
        });

        // --- 子弹的碰撞处理器 ---
        // 子弹碰到地面或平台就消失
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
        // 子弹碰到玩家
        getPhysicsWorld().addCollisionHandler(new CollisionHandler(GameType.BULLET, GameType.PLAYER) {
            @Override
            protected void onCollisionBegin(Entity bullet, Entity playerEntity) {
                // 从子弹实体中获取 BulletComponent
                BulletComponent bulletData = bullet.getComponent(BulletComponent.class);

                // 确保子弹不能伤害射手自己
                if (bulletData.getShooter().equals(playerEntity)) {
                    return;
                }
                // 无论如何，子弹在碰撞后都应该消失
                bullet.removeFromWorld();
            }
        });

        // 处理榴弹（PROJECTILE）的碰撞
        getPhysicsWorld().addCollisionHandler(new CollisionHandler(GameType.PROJECTILE, GameType.GROUND) {
            @Override
            protected void onCollisionBegin(Entity projectile, Entity ground) {
                // 可以在这里添加爆炸效果
                projectile.removeFromWorld();
            }
        });

        getPhysicsWorld().addCollisionHandler(new CollisionHandler(GameType.PROJECTILE, GameType.PLATFORM) {
            @Override
            protected void onCollisionBegin(Entity projectile, Entity platform) {
                projectile.removeFromWorld();
            }
        });

        getPhysicsWorld().addCollisionHandler(new CollisionHandler(GameType.PROJECTILE, GameType.PLAYER) {
            @Override
            protected void onCollisionBegin(Entity projectile, Entity playerEntity) {
                GrenadeComponent grenadeData = projectile.getComponent(GrenadeComponent.class);
                if (grenadeData.getShooter().equals(playerEntity)) {
                    return; // 不伤害自己
                }
                projectile.removeFromWorld();
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


    // 远程炮弹效果
    private void spawnRemoteGrenade(int shooterId, double ox, double oy, Point2D direction) {
        // 获取射手实体，虽然可能不是必须的，但保持组件完整性是好习惯
        Entity shooterEntity = null;
        RemotePlayer remoteShooter = playerManager.getRemotePlayers().get(shooterId);
        if (remoteShooter != null) {
            shooterEntity = remoteShooter.entity;
        }

        // 与 GrenadeLauncher.java 中的 spawnGrenade 逻辑几乎一样
        com.almasb.fxgl.physics.PhysicsComponent physics = new com.almasb.fxgl.physics.PhysicsComponent();
        physics.setBodyType(com.almasb.fxgl.physics.box2d.dynamics.BodyType.DYNAMIC);
        physics.setFixtureDef(new com.almasb.fxgl.physics.box2d.dynamics.FixtureDef().density(0.5f).restitution(0.4f));

        final double LAUNCH_VELOCITY = 800.0; // 与 GrenadeLauncher.java 保持一致

        entityBuilder()
                .type(GameType.PROJECTILE) // 类型正确
                .at(ox, oy)
                .viewWithBBox(new javafx.scene.shape.Circle(8, Color.DARKOLIVEGREEN)) // 视觉效果一致
                .with(new CollidableComponent(true))
                .with(physics)
                .with(new GrenadeComponent(shooterEntity)) // 记录发射者
                .buildAndAttach();

        // 施加初速度
        physics.setLinearVelocity(direction.multiply(LAUNCH_VELOCITY));
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

    public static void main(String[] args) {
        launch(args);
    }
}