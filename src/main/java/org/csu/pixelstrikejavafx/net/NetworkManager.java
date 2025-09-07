package org.csu.pixelstrikejavafx.net;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import lombok.Getter;
import org.csu.pixelstrikejavafx.net.dto.GameStateSnapshot;
import org.csu.pixelstrikejavafx.net.dto.LoginResponseDTO;
import org.csu.pixelstrikejavafx.net.dto.UserCommand;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public class NetworkManager {

    private final String API_BASE_URL = "http://localhost:8080";
    private final String WS_BASE_URL = "ws://localhost:8080";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    private String authToken;
    private WebSocket lobbySocket;
    private WebSocket gameSocket;

    private final Consumer<GameStateSnapshot> onGameStateReceived;
    private final Consumer<Long> onMatchSuccess;

    @Getter
    private String myPlayerId;

    private final StringBuilder gameReceiveBuffer = new StringBuilder();

    public NetworkManager(Consumer<GameStateSnapshot> onGameStateReceived, Consumer<Long> onMatchSuccess) {
        this.onGameStateReceived = onGameStateReceived;
        this.onMatchSuccess = onMatchSuccess;
    }

    // 1. 登录 (已修复)
    public CompletableFuture<Boolean> login(String username, String password) {
        String jsonPayload = gson.toJson(Map.of("username", username, "password", password));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE_URL + "/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        // 【修复】使用 CommonResponse 解析
                        CommonResponse<LoginResponseDTO> commonResponse = gson.fromJson(
                                response.body(),
                                CommonResponse.getTypeToken(LoginResponseDTO.class)
                        );
                        if (commonResponse.getStatus() == 0) {
                            this.authToken = commonResponse.getData().getToken();
                            System.out.println("Login successful, token received.");
                            return true;
                        }
                    }
                    System.err.println("Login failed: " + response.body());
                    return false;
                });
    }

    // 2. 连接到大厅 (已修复)
    public void connectToLobby() {
        if (authToken == null) {
            System.err.println("Cannot connect to lobby, not logged in.");
            return;
        }
        httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(WS_BASE_URL + "/ws?token=" + authToken), new WebSocket.Listener() {
                    @Override
                    public void onOpen(WebSocket webSocket) {
                        lobbySocket = webSocket;
                        System.out.println("Lobby WebSocket connected.");
                        webSocket.request(1); // 请求第一条消息
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        String message = data.toString();
                        System.out.println("[Lobby WS Received] " + message);
                        try {
                            JsonObject jsonObject = gson.fromJson(message, JsonObject.class);
                            String type = jsonObject.get("type").getAsString();
                            if ("match_success".equals(type) || "game_start".equals(type)) {
                                long gameId = jsonObject.get("gameId").getAsLong();
                                if (onMatchSuccess != null) {
                                    onMatchSuccess.accept(gameId);
                                }
                            }
                            // 可以在这里处理其他大厅消息，如好友状态更新等
                        } catch (Exception e) {
                            System.err.println("Failed to parse lobby message: " + message);
                        }
                        webSocket.request(1); // 处理完后请求下一条
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        System.out.println("Lobby WebSocket closed: " + reason);
                        return null;
                    }
                });
    }

    // 3. 开始匹配
    public void startMatchmaking() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE_URL + "/matchmaking/start"))
                .header("Authorization", "Bearer " + authToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> System.out.println("Matchmaking request sent, server response: " + response.body()));
    }

    // 4. 连接到游戏服务器 (已修复)
    public void connectToGame(long gameId) {
        if (lobbySocket != null) {
            lobbySocket.sendClose(WebSocket.NORMAL_CLOSURE, "Joining game");
            lobbySocket = null;
        }

        httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(WS_BASE_URL + "/game?gameId=" + gameId + "&token=" + authToken), new WebSocket.Listener() {
                    @Override
                    public void onOpen(WebSocket webSocket) {
                        gameSocket = webSocket;
                        gameReceiveBuffer.setLength(0); // 清空缓冲区
                        System.out.println("Game WebSocket connected.");
                        webSocket.request(1);
                    }

                    /**
                     * 【核心修正2】: 重写 onText 方法以处理消息分片
                     */
                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        // 1. 将收到的数据块追加到缓冲区
                        gameReceiveBuffer.append(data);

                        // 2. 循环处理缓冲区，尝试提取一个或多个完整的JSON对象
                        while (true) {
                            String bufferString = gameReceiveBuffer.toString();
                            int startIndex = bufferString.indexOf('{');

                            // 如果缓冲区里连一个 '{' 都没有，说明没有有效的JSON开始了，可以清空并退出
                            if (startIndex == -1) {
                                gameReceiveBuffer.setLength(0);
                                break;
                            }

                            // 通过括号匹配来找到JSON对象的边界
                            int braceCount = 0;
                            int endIndex = -1;
                            for (int i = startIndex; i < bufferString.length(); i++) {
                                char c = bufferString.charAt(i);
                                if (c == '{') braceCount++;
                                else if (c == '}') braceCount--;

                                if (braceCount == 0) {
                                    endIndex = i;
                                    break;
                                }
                            }

                            // 3. 如果在缓冲区中找到了一个完整的JSON对象
                            if (endIndex != -1) {
                                // 提取这个完整的JSON字符串
                                String message = bufferString.substring(startIndex, endIndex + 1);

                                // --- 使用我们之前的解析逻辑处理这个完整的消息 ---
                                try {
                                    JsonObject jsonObject = gson.fromJson(message, JsonObject.class);
                                    if (jsonObject.has("type") && "welcome".equals(jsonObject.get("type").getAsString())) {
                                        myPlayerId = jsonObject.get("playerId").getAsString();
                                        System.out.println("<<<<< SUCCESS: Assigned player ID: " + myPlayerId + " >>>>>");
                                    } else {
                                        GameStateSnapshot snapshot = gson.fromJson(message, GameStateSnapshot.class);
                                        if (onGameStateReceived != null) {
                                            onGameStateReceived.accept(snapshot);
                                        }
                                    }
                                } catch (JsonSyntaxException e) {
                                    System.err.println("!!! FAILED TO PARSE ASSEMBLED JSON: " + message);
                                }

                                // 4. 【关键】从缓冲区中移除已经处理过的这部分数据
                                gameReceiveBuffer.delete(0, endIndex + 1);

                            } else {
                                // 缓冲区里有 '{' 但没有找到匹配的 '}'，说明JSON不完整，退出循环等待下一个数据块
                                break;
                            }
                        }

                        // 请求下一块数据
                        webSocket.request(1);
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        System.out.println("Game WebSocket closed: " + reason);
                        gameSocket = null;
                        return null;
                    }
                });
    }

    // 5. 发送玩家指令
    public void sendCommand(UserCommand command) {
        if (gameSocket != null && !gameSocket.isOutputClosed()) {
            String jsonCommand = gson.toJson(command);
            gameSocket.sendText(jsonCommand, true);
        }
    }
}