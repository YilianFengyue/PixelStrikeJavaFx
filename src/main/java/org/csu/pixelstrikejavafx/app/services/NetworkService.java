package org.csu.pixelstrikejavafx.app.services;

import com.almasb.fxgl.dsl.FXGL;
import javafx.application.Platform;
import org.csu.pixelstrikejavafx.net.NetClient;
import org.csu.pixelstrikejavafx.state.GlobalState;

import java.util.function.Consumer;

public class NetworkService {

    private NetClient netClient;
    private long seq = 1;
    private boolean joinedAck = false;
    private long welcomeSrvTS = 0L;
    private Integer myPlayerId = null;

    private final Consumer<String> onMessage;

    public NetworkService(Consumer<String> onMessage) {
        this.onMessage = onMessage;
    }

    public void connect() {
        String baseUrl = GlobalState.currentGameServerUrl;
        if (baseUrl == null || baseUrl.isBlank()) {
            FXGL.getDialogService().showMessageBox("错误：找不到游戏服务器地址！", () -> FXGL.getGameController().gotoMainMenu());
            return;
        }

        String token = GlobalState.authToken;
        Long gameId = GlobalState.currentGameId;
        if (token == null || gameId == null) {
            FXGL.getDialogService().showMessageBox("错误：无法连接游戏服务器，认证信息不完整！", () -> FXGL.getGameController().gotoMainMenu());
            return;
        }

        String finalUrl = baseUrl + "?gameId=" + gameId + "&token=" + token;
        System.out.println("=== Connecting to game server with final URL: " + finalUrl);

        netClient = new NetClient();
        netClient.connect(finalUrl,
                () -> System.out.println("[WS] >> Connection opened. Waiting for 'welcome' message..."),
                msg -> Platform.runLater(() -> onMessage.accept(msg))
        );
    }

    public void sendState(double x, double y, double vx, double vy, boolean facing, boolean onGround, String anim, String phase) {
        if (netClient != null && joinedAck) {
            netClient.sendState(x, y, vx, vy, facing, onGround, anim, phase, System.currentTimeMillis(), seq++);
        }
    }

    public void sendShot(double ox, double oy, double dx, double dy, double range, int dmg, long ts) {
        if (netClient != null && joinedAck) {
            netClient.sendShot(ox, oy, dx, dy, range, dmg, ts, seq++);
        }
    }

    public void sendLeaveMessage() {
        if (netClient != null) {
            netClient.send("{\"type\":\"leave\"}");
        }
    }

    // Getters and Setters
    public boolean isJoinedAck() { return joinedAck; }
    public void setJoinedAck(boolean joinedAck) { this.joinedAck = joinedAck; }
    public long getWelcomeSrvTS() { return welcomeSrvTS; }
    public void setWelcomeSrvTS(long welcomeSrvTS) { this.welcomeSrvTS = welcomeSrvTS; }
    public Integer getMyPlayerId() { return myPlayerId; }
    public void setMyPlayerId(Integer myPlayerId) { this.myPlayerId = myPlayerId; }
}