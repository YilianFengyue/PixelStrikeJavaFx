// main/java/org/csu/pixelstrikejavafx/app/PixelGameApp.java

package org.csu.pixelstrikejavafx.app;

import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.input.UserAction;
import com.almasb.fxgl.physics.CollisionHandler;
import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import javafx.util.Duration;
import org.csu.pixelstrikejavafx.camera.CameraFollow;
import org.csu.pixelstrikejavafx.core.GameConfig;
import org.csu.pixelstrikejavafx.core.GameType;
import org.csu.pixelstrikejavafx.map.MapBuilder;
import org.csu.pixelstrikejavafx.net.NetworkManager;
import org.csu.pixelstrikejavafx.net.dto.GameStateSnapshot;
import org.csu.pixelstrikejavafx.net.dto.PlayerState;
import org.csu.pixelstrikejavafx.net.dto.UserCommand;
import org.csu.pixelstrikejavafx.player.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.almasb.fxgl.dsl.FXGL.*;

public class PixelGameApp extends GameApplication {

    private NetworkManager networkManager;
    private final Map<String, Player> players = new ConcurrentHashMap<>();
    private final AtomicInteger commandSequence = new AtomicInteger(0);
    private CameraFollow cameraFollow;

    private boolean isMovingLeft = false;
    private boolean isMovingRight = false;
    private boolean isShooting = false;
    private Player myPlayer = null;

    @Override
    protected void initSettings(GameSettings s) {
        s.setWidth(GameConfig.WINDOW_W);
        s.setHeight(GameConfig.WINDOW_H);
        s.setTitle("PixelStrike - Online Client (Final)");
        s.setMainMenuEnabled(false);
        s.setGameMenuEnabled(false);
        s.setScaleAffectedOnResize(false);
        s.setDeveloperMenuEnabled(true);
    }

    @Override
    protected void initGame() {
        getPhysicsWorld().setGravity(0, 1100);
        networkManager = new NetworkManager(this::onGameStateReceived, this::onMatchSuccess);
        MapBuilder.buildLevel();
        setupCamera();
//        setupCollisionHandlers();

        run(() -> {
            if (networkManager.getMyPlayerId() != null) {
                sendContinuousCommands();
            }
        }, Duration.millis(50));

        startGameFlow();
    }
//    private void setupCollisionHandlers() {
//        // 这个碰撞处理器在网络版中可能不是必须的，
//        // 因为玩家是否在地面上的最终判断权在服务器。
//        // 但为了本地视觉表现的平滑，可以保留。
//        java.util.function.BiConsumer<Entity, Boolean> setGround = (playerEntity, on) -> {
//            Object ref = playerEntity.getProperties().getObject("playerRef");
//            if (ref instanceof Player p) {
//                // 注意：在网络版中，这里可能需要调整，
//                // 也许只是更新一个本地的 isGrounded 标志用于动画，
//                // 而不是直接影响跳跃逻辑。
//                // p.setOnGround(on);
//            }
//        };
//
//        getPhysicsWorld().addCollisionHandler(new CollisionHandler(GameType.PLAYER, GameType.GROUND) {
//            @Override protected void onCollisionBegin(Entity a, Entity b) { setGround.accept(a, true);  }
//            @Override protected void onCollisionEnd(Entity a, Entity b)   { setGround.accept(a, false); }
//        });
//
//        getPhysicsWorld().addCollisionHandler(new CollisionHandler(GameType.PLAYER, GameType.PLATFORM) {
//            @Override protected void onCollisionBegin(Entity a, Entity b) { setGround.accept(a, true);  }
//            @Override protected void onCollisionEnd(Entity a, Entity b)   { setGround.accept(a, false); }
//        });
//    }


    private void setupCamera() {
        var vp = getGameScene().getViewport();
        vp.setBounds(0, 0, (int) GameConfig.MAP_W, (int) GameConfig.MAP_H);
        cameraFollow = new CameraFollow(vp, GameConfig.MAP_W, GameConfig.MAP_H, getAppWidth(), getAppHeight());
    }

    private void startGameFlow() {
        String username = "qwe";
        String password = "123456";
        getExecutor().startAsync(() -> {
            networkManager.login(username, password).thenAccept(success -> {
                Platform.runLater(() -> {
                    if (success) {
                        networkManager.connectToLobby();
                        runOnce(() -> networkManager.startMatchmaking(), Duration.seconds(1));
                    }
                });
            });
        });
    }

    private void onMatchSuccess(long gameId) {
        System.out.println("Match Success! Connecting to game server...");
        players.values().forEach(p -> p.getEntity().removeFromWorld());
        players.clear();
        myPlayer = null;

        Platform.runLater(() -> networkManager.connectToGame(gameId));
    }

    private void onGameStateReceived(GameStateSnapshot snapshot) {
        Platform.runLater(() -> {
            players.keySet().removeIf(playerId -> {
                if (!snapshot.getPlayers().containsKey(playerId)) {
                    Player playerToRemove = players.get(playerId);
                    if (playerToRemove != null) playerToRemove.getEntity().removeFromWorld();
                    return true;
                }
                return false;
            });

            for (Map.Entry<String, PlayerState> entry : snapshot.getPlayers().entrySet()) {
                String playerId = entry.getKey();
                PlayerState serverState = entry.getValue();
                Player localPlayer = players.get(playerId);

                if (localPlayer == null) {
                    localPlayer = new Player(serverState.getX(), serverState.getY());
                    players.put(playerId, localPlayer);
                    if (playerId.equals(networkManager.getMyPlayerId())) {
                        myPlayer = localPlayer;
                        cameraFollow.setTarget(localPlayer.getEntity());
                    }
                }

                localPlayer.networkUpdate(serverState);
            }
        });
    }

    @Override
    protected void onUpdate(double tpf) {
        // 【核心修正】: 使用统一的状态更新方法
        if(myPlayer != null) {
            myPlayer.updateLocalState(isMovingLeft || isMovingRight, isShooting);
        }

        for (Player player : players.values()) {
            player.clientUpdate(tpf);
        }

        if (cameraFollow != null) {
            cameraFollow.update();
        }
    }

    @Override
    protected void initInput() {
        getInput().addAction(new UserAction("Move Left") {
            @Override
            protected void onActionBegin() { isMovingLeft = true; }
            @Override
            protected void onActionEnd() { isMovingLeft = false; }
        }, KeyCode.A);

        getInput().addAction(new UserAction("Move Right") {
            @Override
            protected void onActionBegin() { isMovingRight = true; }
            @Override
            protected void onActionEnd() { isMovingRight = false; }
        }, KeyCode.D);

        getInput().addAction(new UserAction("Jump with W") {
            @Override
            protected void onActionBegin() { sendJumpCommand(); }
        }, KeyCode.W);
        getInput().addAction(new UserAction("Jump with Space") {
            @Override
            protected void onActionBegin() { sendJumpCommand(); }
        }, KeyCode.SPACE);

        getInput().addAction(new UserAction("Shoot") {
            @Override
            protected void onActionBegin() { isShooting = true; }
            @Override
            protected void onActionEnd() { isShooting = false; }
        }, KeyCode.J);
    }

    private void sendContinuousCommands() {
        float moveInput = 0;
        if (isMovingLeft) moveInput = -1.0f;
        if (isMovingRight) moveInput = 1.0f;
        byte actions = 0;
        if (isShooting) actions |= 2;

        UserCommand command = new UserCommand();
        command.setMoveInput(moveInput);
        command.setActions(actions);
        sendCommand(command);
    }

    private void sendJumpCommand() {
        UserCommand command = new UserCommand();
        command.setMoveInput(0);
        command.setActions((byte) 1);
        sendCommand(command);
    }

    private void sendCommand(UserCommand command) {
        if (networkManager.getMyPlayerId() == null) return;
        command.setPlayerId(networkManager.getMyPlayerId());
        command.setCommandSequence(commandSequence.getAndIncrement());
        command.setTimestamp(System.currentTimeMillis());
        command.setAimAngle(0);
        networkManager.sendCommand(command);
    }

    public static void main(String[] args) {
        launch(args);
    }
}