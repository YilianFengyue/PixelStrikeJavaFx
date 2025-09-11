package org.csu.pixelstrikejavafx.lobby.network;

import com.almasb.fxgl.dsl.FXGL;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.websocket.ClientEndpoint;
import org.csu.pixelstrikejavafx.core.MatchSuccessEvent;
import org.csu.pixelstrikejavafx.lobby.events.*;
import org.csu.pixelstrikejavafx.core.GlobalState;

import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.websocket.ContainerProvider;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;
import jakarta.websocket.CloseReason;


/**
 * NetworkManager (WebSocket 客户端)
 * 负责管理与服务器的全局 WebSocket 连接，处理实时消息的接收和分发。
 * 采用线程安全的懒汉式单例模式。
 */
@ClientEndpoint
public class NetworkManager {

    private static volatile NetworkManager instance;
    private Session session;
    private final Gson gson = new Gson();
    private boolean isConnecting = false;
    private boolean intentionalDisconnect = false;
    private volatile JsonObject cachedRoomUpdate = null;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private NetworkManager() {
        // 私有构造函数，防止外部实例化
    }

    /**
     * 获取 NetworkManager 的唯一实例。
     */
    public static NetworkManager getInstance() {
        if (instance == null) {
            synchronized (NetworkManager.class) {
                if (instance == null) {
                    instance = new NetworkManager();
                }
            }
        }
        return instance;
    }

    /**
     * 根据全局状态中的 token 连接到 WebSocket 服务器。
     */
    public synchronized void connect() {
        if (session != null && session.isOpen() || isConnecting) {
            System.out.println("WebSocket is already connected or connecting.");
            return;
        }

        if (GlobalState.authToken == null) {
            System.err.println("Cannot connect WebSocket: Auth token is null.");
            return;
        }

        isConnecting = true;
        intentionalDisconnect = false;
        String url = "ws://localhost:8080/ws?token=" + GlobalState.authToken;

        try {
            System.out.println("Connecting to WebSocket: " + url);
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.connectToServer(this, new URI(url));
        } catch (Exception e) {
            System.err.println("WebSocket connection failed: " + e.getMessage());
            isConnecting = false;
            handleDisconnection(); // 连接失败，尝试重连
        }
    }

    /**
     * 主动断开连接。
     */
    public void disconnect() {
        intentionalDisconnect = true;
        if (session != null && session.isOpen()) {
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "User action"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        this.isConnecting = false;
        System.out.println("Global WebSocket connection established! Session ID: " + session.getId());
    }

    @OnMessage
    public void onMessage(String message) {
        System.out.println("Received WebSocket message: " + message);
        try {
            JsonObject msgJson = gson.fromJson(message, JsonObject.class);
            String type = msgJson.get("type").getAsString();

            String roomId;
            String inviterNickname;
            long inviterId;

            switch (type) {
                case "status_update":
                    FXGL.getEventBus().fireEvent(new FriendStatusEvent(msgJson));
                    break;
                case "match_success":
                    String serverAddress = msgJson.get("serverAddress").getAsString();
                    long gameId = msgJson.get("gameId").getAsLong();
                    FXGL.getEventBus().fireEvent(new MatchSuccessEvent(serverAddress, gameId));
                    break;
                case "new_friend_request":
                    FXGL.getEventBus().fireEvent(new NewFriendRequestEvent(msgJson));
                    break;
                case "friend_request_accepted":
                    FXGL.getEventBus().fireEvent(new FriendRequestAcceptedEvent(msgJson));
                    break;
                case "room_update":
                    this.cachedRoomUpdate = msgJson;
                    FXGL.getEventBus().fireEvent(new RoomUpdateEvent(msgJson));
                    break;
                case "room_invitation":
                    roomId = msgJson.get("roomId").getAsString();
                    inviterNickname = msgJson.get("inviterNickname").getAsString();
                    inviterId = msgJson.get("inviterId").getAsLong();
                    FXGL.getEventBus().fireEvent(new RoomInvitationEvent(roomId, inviterNickname, inviterId));
                    break;
                case "invitation_rejected":
                    long rejectorId = msgJson.get("rejectorId").getAsLong();
                    FXGL.getEventBus().fireEvent(new InvitationRejectedEvent(rejectorId));
                    break;
                case "kicked_from_room":
                    roomId = msgJson.get("roomId").getAsString();
                    FXGL.getEventBus().fireEvent(new KickedFromRoomEvent(roomId));
                    break;
            }

        } catch (Exception e) {
            System.err.println("Failed to parse WebSocket message: " + message);
            e.printStackTrace();
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        System.out.println("WebSocket session closed. Reason: " + reason.getReasonPhrase());
        this.session = null;
        this.isConnecting = false;
        if (!intentionalDisconnect) {
            handleDisconnection(); // 如果不是用户主动断开，则尝试重连
        }
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        System.err.println("WebSocket error occurred:");
        throwable.printStackTrace();
    }

    /**
     * 处理连接断开，并安排一个延迟的重连任务。
     */
    private void handleDisconnection() {
        System.out.println("Will attempt to reconnect in 5 seconds...");
        scheduler.schedule(this::connect, 5, TimeUnit.SECONDS);
    }

    /**
     * 获取并清除缓存的 room_update 消息。
     * “检查信箱”这个动作本身就会把信取走，防止下次再读到旧信。
     * @return 如果有缓存的消息则返回该消息，否则返回 null。
     */
    public JsonObject getAndClearCachedRoomUpdate() {
        JsonObject message = this.cachedRoomUpdate;
        if (message != null) {
            this.cachedRoomUpdate = null; // 取走后清空信箱
        }
        return message;
    }
}