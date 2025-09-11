package org.csu.pixelstrikejavafx.game.ui;

import com.google.gson.*;
import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXPasswordField;
import io.github.palexdev.materialfx.controls.MFXTextField;
import io.github.palexdev.materialfx.enums.ButtonType;
import io.github.palexdev.materialfx.enums.FloatMode;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import org.csu.pixelstrikejavafx.game.network.ApiClient;
import org.csu.pixelstrikejavafx.game.network.WsClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MultiplayerDock {

    // ===== 基本配置 =====
    private String API_BASE = "http://localhost:8080";
    private String WS_BASE  = "ws://localhost:8080";

    // ===== 网络工具 =====
    private final ApiClient api = new ApiClient();
    private final WsClient lobbyWS = new WsClient();
    private final WsClient gameWS  = new WsClient();

    // ===== 状态 =====
    private String authToken;
    private String myPlayerId;
    private String lastGameId;

    private Consumer<String> onGameMatched;   // 回调：匹配成功（gameId）
    private Consumer<Void>   onGameClosed;    // 回调：游戏 WS 关闭

    // ===== UI 根节点 =====
    private final BorderPane root = new BorderPane();
    private final VBox content = new VBox(12);
    private final StackPane header = new StackPane();
    private boolean collapsed = true;

    // ===== 顶栏徽章 =====
    private final Label wsBadge = badge("未连接", Color.web("#dc3545"));

    // ===== 登录区 =====
    private final MFXTextField tfUser = new MFXTextField();
    private final MFXPasswordField tfPass = new MFXPasswordField();
    private final MFXButton btnLogin = new MFXButton("登录");

    // ===== 折叠按钮（改用 MFXButton） =====
    private final MFXButton btnCollapse = new MFXButton("›"); // 初始为展开方向

    // ===== 匹配区 =====
    private final MFXButton btnStartMatch = new MFXButton("开始匹配");
    private final MFXButton btnCancelMatch = new MFXButton("取消匹配");

    // ===== 好友/搜索/房间/历史 =====
    private final ListView<String> friendsList = new ListView<>();
    private final MFXTextField tfSearch = new MFXTextField();
    private final MFXButton btnSearch = new MFXButton("搜索");
    private final ListView<String> searchList = new ListView<>();
    private final MFXButton btnAddFriend = new MFXButton("给所选发送好友请求");

    private final MFXButton btnCreateRoom = new MFXButton("创建自定义房间");
    private final MFXTextField tfCreatedRoom = new MFXTextField();
    private final MFXButton btnCopyRoom = new MFXButton("复制");
    private final MFXButton btnStartCustom = new MFXButton("开始游戏");
    private final MFXTextField tfJoinRoom = new MFXTextField();
    private final MFXButton btnJoinRoom = new MFXButton("加入");
    private final MFXButton btnLeaveRoom = new MFXButton("退出房间");

    private final ListView<String> historyList = new ListView<>();
    private final VBox resultsBox = new VBox(8);

    // 日志
    private final VBox logBox = new VBox(6);

    public MultiplayerDock() {
        buildUI();
        wireEvents();
        setCollapsed(false);
        setAuthEnabled(false); // FIX: 未登录前禁用所有需要鉴权的功能
    }

    // ==== 外部接口 ====
    public Node getRoot() { return root; }
    public void setBaseUrls(String apiBase, String wsBase) { this.API_BASE = apiBase; this.WS_BASE = wsBase; }
    public void onGameMatched(Consumer<String> cb) { this.onGameMatched = cb; }
    public void onGameClosed(Consumer<Void> cb) { this.onGameClosed = cb; }

    // ==== UI 构建 ====
    private void buildUI() {
        // FIX: 折叠时也能点到（整个面板区域可点）
        root.setPickOnBounds(true);
        root.setMouseTransparent(false);

        root.setPrefWidth(360);
        root.setMaxWidth(360);
        root.setStyle("""
            -fx-background-color: white;
            -fx-background-radius: 14 0 0 14;
            -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.16), 16, 0.2, 0, 2);
            -fx-border-color: #E5E7EB;
            -fx-border-width: 1 0 1 1;
            -fx-border-radius: 14 0 0 14;
        """);

        // 顶栏
        var title = new Label("联机控制");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        // FIX: 用 MFXButton 做圆形折叠按钮
        btnCollapse.setButtonType(ButtonType.RAISED);
        btnCollapse.setCursor(Cursor.HAND);
        btnCollapse.setPrefSize(28, 28);
        btnCollapse.setStyle("""
            -fx-background-color:#111827;
            -fx-text-fill:white;
            -fx-background-radius:999;
            -fx-padding:0;
        """);
        btnCollapse.setOnAction(e -> toggle());

        StackPane.setAlignment(btnCollapse, Pos.CENTER_LEFT);
        StackPane.setMargin(btnCollapse, new Insets(0,0,0,8));
        var right = new HBox(6, new Label("大厅"), wsBadge);
        right.setAlignment(Pos.CENTER_RIGHT);

        var titleBox = new HBox();
        titleBox.setAlignment(Pos.CENTER);
        titleBox.getChildren().add(title);

        header.getChildren().setAll(btnCollapse, titleBox, right);
        header.setPadding(new Insets(10, 12, 10, 12));
        header.setStyle("-fx-background-color:#F9FAFB; -fx-background-radius:14 0 0 0; -fx-border-color:#E5E7EB; -fx-border-width:0 0 1 0;");
        // FIX: 折叠时点击标题栏也能展开
        header.setOnMouseClicked(e -> { if (collapsed) toggle(); });

        // 内容
        content.setPadding(new Insets(12));
        content.getChildren().addAll(
                group("1. 登录", buildLoginBox()),
                group("2. 匹配与游戏", buildMatchBox()),
                group("好友列表", friendsList),
                group("社交与自定义房间", buildSocialBox()),
                group("对局详情 / 历史战绩", buildResultsBox()),
                group("日志", buildLogArea())
        );

        root.setTop(header);
        root.setCenter(content);
    }

    private VBox buildLoginBox() {
        tfUser.setPromptText("用户名"); tfUser.setFloatMode(FloatMode.BORDER);
        tfPass.setPromptText("密码");   tfPass.setFloatMode(FloatMode.BORDER);
        btnLogin.setButtonType(ButtonType.RAISED); btnLogin.setPrefHeight(36);
        btnLogin.setStyle("-fx-background-color:#3B82F6; -fx-text-fill:white;"); // 统一风格
        return vbox(8, tfUser, tfPass, btnLogin);
    }

    private VBox buildMatchBox() {
        btnStartMatch.setButtonType(ButtonType.RAISED);
        btnStartMatch.setStyle("-fx-background-color:#2563EB; -fx-text-fill:white;");
        btnCancelMatch.setDisable(true);
        btnCancelMatch.setButtonType(ButtonType.RAISED);
        btnCancelMatch.setStyle("-fx-background-color:#EF4444; -fx-text-fill:white;");
        var tip = new Label("A/D 左右 | W/空格 跳跃 | J 开火");
        tip.setTextFill(Color.web("#6B7280"));
        return vbox(8, btnStartMatch, btnCancelMatch, tip);
    }

    private VBox buildSocialBox() {
        tfSearch.setPromptText("按昵称搜索");
        btnSearch.setText("搜索"); btnSearch.setButtonType(ButtonType.RAISED);
        searchList.setPrefHeight(120);
        btnAddFriend.setButtonType(ButtonType.RAISED);
        btnAddFriend.setStyle("-fx-background-color:#10B981; -fx-text-fill:white;");
        btnAddFriend.setOnAction(e -> {
            String sel = searchList.getSelectionModel().getSelectedItem();
            if (sel != null) sendFriendRequest(extractId(sel));
        });

        tfCreatedRoom.setPromptText("创建成功后显示房号"); tfCreatedRoom.setEditable(false);
        btnCreateRoom.setButtonType(ButtonType.RAISED);
        btnCreateRoom.setStyle("-fx-background-color:#8B5CF6; -fx-text-fill:white;");
        btnCopyRoom.setOnAction(e -> copyToClipboard(tfCreatedRoom.getText()));
        btnStartCustom.setVisible(false);
        btnStartCustom.setButtonType(ButtonType.RAISED);
        btnStartCustom.setStyle("-fx-background-color:#22C55E; -fx-text-fill:white;");
        btnLeaveRoom.setVisible(false);
        btnLeaveRoom.setButtonType(ButtonType.RAISED);
        btnLeaveRoom.setStyle("-fx-background-color:#DC2626; -fx-text-fill:white;");

        tfJoinRoom.setPromptText("输入房号加入");

        var searchRow  = hbox(8, tfSearch, btnSearch);
        var roomBtns   = hbox(8, btnCreateRoom, btnStartCustom, btnLeaveRoom);
        var createdRow = hbox(8, tfCreatedRoom, btnCopyRoom);
        var joinRow    = hbox(8, tfJoinRoom, btnJoinRoom);

        return vbox(10,
                new Label("搜索玩家"), searchRow, searchList, btnAddFriend,
                new Separator(),
                new Label("自定义房间"), roomBtns, createdRow, joinRow
        );
    }

    private VBox buildResultsBox() {
        var tip = new Label("点击历史战绩查看详情");
        tip.setTextFill(Color.web("#6B7280"));
        historyList.setPrefHeight(160);
        return vbox(8, resultsBox, new Separator(), tip, historyList);
    }

    private ScrollPane buildLogArea() {
        var sp = new ScrollPane(logBox);
        sp.setFitToWidth(true);
        sp.setPrefHeight(160);
        sp.setStyle("-fx-background-color:transparent;");
        return sp;
    }

    private Node group(String title, Node content) {
        var card = new VBox(8);
        var head = new Label(title);
        head.setStyle("-fx-font-weight:bold; -fx-text-fill:#111827;");
        card.getChildren().addAll(head, content);
        card.setPadding(new Insets(10));
        card.setStyle("""
            -fx-background-color:white;
            -fx-background-radius:10;
            -fx-border-color:#E5E7EB;
            -fx-border-radius:10;
        """);
        return card;
    }

    private Label badge(String text, Color bg) {
        var l = new Label(text);
        l.setStyle("-fx-text-fill:white; -fx-font-size:11px; -fx-font-weight:bold;");
        l.setAlignment(Pos.CENTER);
        l.setPadding(new Insets(2,8,2,8));
        l.setBackground(new Background(new BackgroundFill(bg, new CornerRadii(999), Insets.EMPTY)));
        return l;
    }

    private VBox vbox(double gap, Node... nodes) { var v = new VBox(gap, nodes); v.setFillWidth(true); return v; }
    private HBox hbox(double gap, Node... nodes) { var h = new HBox(gap, nodes); h.setAlignment(Pos.CENTER_LEFT); HBox.setHgrow(nodes[0], Priority.ALWAYS); return h; }

    private void toggle() { setCollapsed(!collapsed); }

    public void setCollapsed(boolean c) {
        collapsed = c;
        content.setVisible(!c);
        // FIX: 用 Labeled 统一处理按钮文本（兼容 MFXButton / Button）
        Labeled lbl = (Labeled) ((StackPane)root.getTop()).getChildren().get(0);
        lbl.setText(c ? "›" : "‹");
        root.setPrefWidth(c ? 48 : 360);
        root.setMaxWidth(c ? 48 : 360);
    }

    private void setWsStatus(boolean connected) {
        wsBadge.setText(connected ? "已连接" : "未连接");
        wsBadge.setBackground(new Background(new BackgroundFill(
                connected ? Color.web("#28a745") : Color.web("#dc3545"),
                new CornerRadii(999), Insets.EMPTY)));
    }

    // ===== 事件 & 网络 =====
    private void wireEvents() {
        btnLogin.setOnAction(e -> doLogin());
        btnStartMatch.setOnAction(e -> startMatchmaking());
        btnCancelMatch.setOnAction(e -> cancelMatchmaking());

        btnSearch.setOnAction(e -> searchUser());
        btnCreateRoom.setOnAction(e -> createCustomRoom());
        btnStartCustom.setOnAction(e -> startCustomGame());
        btnJoinRoom.setOnAction(e -> joinCustomRoom());
        btnLeaveRoom.setOnAction(e -> leaveCustomRoom());

        historyList.setOnMouseClicked(e -> {
            String item = historyList.getSelectionModel().getSelectedItem();
            if (item != null) fetchMatchResults(extractId(item));
        });

        // 大厅 WS
        lobbyWS.onOpen(() -> { log("大厅连接成功","success"); Platform.runLater(() -> setWsStatus(true)); });
        lobbyWS.onClose(() -> { log("大厅连接断开","error"); Platform.runLater(() -> setWsStatus(false)); });
        lobbyWS.onMessage(this::handleLobbyMessage);

        // 游戏 WS
        gameWS.onOpen(() -> log("游戏连接成功","success"));
        gameWS.onClose(() -> {
            log("游戏连接关闭","error");
            if (onGameClosed != null) onGameClosed.accept(null);
        });
        gameWS.onMessage(this::handleGameMessage);
    }

    private void doLogin() {
        String u = tfUser.getText().trim(), p = tfPass.getText();
        if (u.isEmpty() || p.isEmpty()) { toast("请输入用户名和密码"); return; }
        log("登录中: " + u, "ws");

        JsonObject body = new JsonObject();
        body.addProperty("username", u);
        body.addProperty("password", p);

        api.postJson(API_BASE + "/auth/login", body, null)
                .thenAccept(json -> {
                    try {
                        int status = jsInt(json, "status", -1); // FIX: 防御式解析
                        if (status == 0 && json.has("data") && json.get("data").isJsonObject()) {
                            JsonObject data = json.getAsJsonObject("data");
                            authToken = jsStr(data, "token", null);
                            if (authToken == null || authToken.isBlank()) {
                                toast("登录响应缺少 token"); return;
                            }
                            log("登录成功，Token 已保存","success");

                            Platform.runLater(() -> setAuthEnabled(true)); // FIX: 开启鉴权区块

                            connectLobbyWS();
                            fetchMyProfile();
                            fetchFriends();
                            fetchHistory();

                            if (jsHas(data, "activeGameId")) {
                                lastGameId = jsStr(data, "activeGameId", null);
                                if (lastGameId != null && !lastGameId.isBlank()) {
                                    log("检测到未完成对局，尝试重连 " + lastGameId, "success");
                                    connectGameWS(lastGameId);
                                }
                            }
                        } else {
                            toast(jsStr(json, "message", "登录失败"));
                        }
                    } catch (Exception ex) {
                        toast("登录解析异常: " + ex.getMessage());
                    }
                })
                .exceptionally(ex -> { log("登录异常: " + ex.getMessage(),"error"); return null; });
    }

    private void setAuthEnabled(boolean enabled) {
        btnStartMatch.setDisable(!enabled);
        btnCancelMatch.setDisable(true);
        tfSearch.setDisable(!enabled);
        btnSearch.setDisable(!enabled);
        btnAddFriend.setDisable(!enabled);
        btnCreateRoom.setDisable(!enabled);
        btnStartCustom.setDisable(!enabled);
        btnJoinRoom.setDisable(!enabled);
        btnLeaveRoom.setDisable(!enabled);
    }

    private void connectLobbyWS() {
        String url = WS_BASE + "/ws?token=" + urlenc(authToken);
        lobbyWS.connect(url);
    }

    private void handleLobbyMessage(String text) {
        log("大厅消息: " + text, "ws");
        JsonObject msg = JsonParser.parseString(text).getAsJsonObject();
        String type = (msg.has("type") && !msg.get("type").isJsonNull())
                ? msg.get("type").getAsString() : "";

        switch (type) {
            case "match_success":
            case "game_start": {
                lastGameId = jsStr(msg, "gameId", null);
                if (lastGameId != null) {
                    log("匹配成功，gameId=" + lastGameId, "success");
                    btnStartMatch.setDisable(false);
                    btnCancelMatch.setDisable(true);
                    resultsBox.getChildren().clear();
                    connectGameWS(lastGameId);
                    if (onGameMatched != null) onGameMatched.accept(lastGameId);
                }
                break;
            }
            case "status_update": {
                fetchFriends();
                break;
            }
            case "new_friend_request": {
                toast("收到好友请求：" + jsStr(msg, "senderNickname", "?"));
                break;
            }
            case "friend_request_accepted":
            case "friend_request_rejected": {
                fetchFriends();
                break;
            }
            case "room_disbanded": {
                toast("房间已解散：" + jsStr(msg, "reason", ""));
                Platform.runLater(() -> {
                    btnStartCustom.setVisible(false);
                    btnLeaveRoom.setVisible(false);
                });
                break;
            }
            default: {
                // ignore
                break;
            }
        }
    }


    private void connectGameWS(String gameId) {
        String url = WS_BASE + "/game?gameId=" + urlenc(gameId) + "&token=" + urlenc(authToken);
        gameWS.connect(url);
    }

    private void handleGameMessage(String text) {
        try {
            JsonObject msg = JsonParser.parseString(text).getAsJsonObject();
            if (msg.has("type")) {
                String t = msg.get("type").getAsString();
                if ("welcome".equals(t)) {
                    myPlayerId = jsStr(msg, "playerId", null);
                    if (myPlayerId != null) log("识别我的玩家ID: " + myPlayerId, "success");
                }
                // pong 可在这里计算 ping
            } else {
                // 世界状态：players / countdownSeconds / gameTimeRemainingSeconds
                // 下一步：把这块喂给你的同步层
            }
        } catch (Exception ignore) { }
    }

    // ===== 业务：匹配 / 好友 / 房间 / 历史 =====
    private void startMatchmaking() {
        btnStartMatch.setDisable(true);
        btnCancelMatch.setDisable(false);
        api.postJson(API_BASE + "/matchmaking/start", null, authToken)
                .thenAccept(j -> log("开始匹配: " + j, "info"))
                .exceptionally(ex -> { log("开始匹配异常: "+ex.getMessage(), "error"); return null; });
    }

    private void cancelMatchmaking() {
        btnStartMatch.setDisable(false);
        btnCancelMatch.setDisable(true);
        api.postJson(API_BASE + "/matchmaking/cancel", null, authToken)
                .thenAccept(j -> log("取消匹配: " + j, "info"))
                .exceptionally(ex -> { log("取消匹配异常: "+ex.getMessage(), "error"); return null; });
    }

    private void fetchMyProfile() {
        api.getJson(API_BASE + "/users/me", authToken)
                .thenAccept(j -> {
                    try {
                        if (jsHas(j,"status") && j.get("status").getAsInt()==0 && jsHas(j,"data")) {
                            String nick = jsStr(j.getAsJsonObject("data"), "nickname", "?");
                            log("你好，" + nick, "success");
                        } else {
                            log("获取 Profile 失败: " + j, "error");
                        }
                    } catch (Exception ex) {
                        log("Profile 解析异常: " + ex.getMessage(), "error");
                    }
                });
    }

    private void fetchFriends() {
        api.getJson(API_BASE + "/friends", authToken)
                .thenAccept(j -> Platform.runLater(() -> {
                    friendsList.getItems().clear();
                    try {
                        if (jsHas(j,"status") && j.get("status").getAsInt()==0 && j.get("data").isJsonArray()) {
                            j.getAsJsonArray("data").forEach(e -> {
                                var f = e.getAsJsonObject();
                                String line = jsStr(f,"nickname","?") +
                                        " (ID:" + jsStr(f,"userId","?") + ") " +
                                        "[" + (jsHas(f,"onlineStatus") ? jsStr(f,"onlineStatus","") : "OFFLINE") + "]";
                                friendsList.getItems().add(line);
                            });
                            if (friendsList.getItems().isEmpty()) friendsList.getItems().add("没有好友");
                        } else {
                            friendsList.getItems().add("没有好友");
                        }
                    } catch (Exception ex) {
                        friendsList.getItems().add("数据格式异常");
                        log("好友列表解析异常: " + ex.getMessage(), "error");
                    }
                }));
    }

    private void searchUser() {
        String name = tfSearch.getText().trim(); if (name.isEmpty()) return;
        api.getJson(API_BASE + "/friends/search?nickname=" + urlenc(name), authToken)
                .thenAccept(j -> Platform.runLater(() -> {
                    searchList.getItems().clear();
                    try {
                        if (j.has("data") && j.get("data").isJsonArray() && j.getAsJsonArray("data").size()>0) {
                            j.getAsJsonArray("data").forEach(e -> {
                                var u = e.getAsJsonObject();
                                searchList.getItems().add(jsStr(u,"nickname","?") + " (ID:" + jsStr(u,"userId","?") + ")");
                            });
                        } else searchList.getItems().add("未找到玩家");
                    } catch (Exception ex) {
                        searchList.getItems().add("数据格式异常");
                        log("搜索解析异常: " + ex.getMessage(), "error");
                    }
                }));
    }

    private void sendFriendRequest(String userId) {
        if (userId == null || userId.isEmpty()) { toast("未选中有效的用户"); return; }
        api.postJson(API_BASE + "/friends/requests/" + urlenc(userId), null, authToken)
                .thenAccept(j -> log("好友请求结果: " + j, "info"));
    }

    private void createCustomRoom() {
        api.postJson(API_BASE + "/custom-room/create", null, authToken)
                .thenAccept(j -> Platform.runLater(() -> {
                    try {
                        if (jsHas(j,"status") && j.get("status").getAsInt()==0) {
                            String roomId = jsStr(j, "data", "");
                            tfCreatedRoom.setText(roomId);
                            btnStartCustom.setVisible(true);
                            btnLeaveRoom.setVisible(true);
                            log("房间创建成功: " + roomId, "success");
                        } else toast(jsStr(j,"message","创建失败"));
                    } catch (Exception ex) {
                        toast("创建房间解析异常: " + ex.getMessage());
                    }
                }));
    }

    private void startCustomGame() {
        api.postJson(API_BASE + "/custom-room/start-game", null, authToken)
                .thenAccept(j -> log("开局: "+j, "info"));
    }

    private void joinCustomRoom() {
        String roomId = tfJoinRoom.getText().trim();
        if (roomId.isEmpty()) { toast("请输入房号"); return; }
        api.postJson(API_BASE + "/custom-room/join?roomId=" + urlenc(roomId), null, authToken)
                .thenAccept(j -> Platform.runLater(() -> {
                    try {
                        if (jsHas(j,"status") && j.get("status").getAsInt()==0) {
                            btnLeaveRoom.setVisible(true);
                            log("加入房间成功", "success");
                        } else toast(jsStr(j,"message","加入失败"));
                    } catch (Exception ex) {
                        toast("加入房间解析异常: " + ex.getMessage());
                    }
                }));
    }

    private void leaveCustomRoom() {
        api.postJson(API_BASE + "/custom-room/leave", null, authToken)
                .thenAccept(j -> Platform.runLater(() -> {
                    btnStartCustom.setVisible(false);
                    btnLeaveRoom.setVisible(false);
                    tfCreatedRoom.setText("");
                    log("已退出房间", "success");
                }));
    }

    private void fetchHistory() {
        api.getJson(API_BASE + "/matches/me", authToken)
                .thenAccept(j -> Platform.runLater(() -> {
                    historyList.getItems().clear();
                    try {
                        if (jsHas(j,"status") && j.get("status").getAsInt()==0 && j.get("data").isJsonArray()) {
                            for (var e : j.getAsJsonArray("data")) {
                                var m = e.getAsJsonObject();
                                String id   = jsStr(m, "matchId", jsStr(m, "id", "?"));
                                String mode = jsStr(m, "gameMode", jsStr(m, "mode", "?"));
                                int kills   = jsInt(m, "kills", jsInt(m, "killCount", 0));
                                int deaths  = jsInt(m, "deaths", jsInt(m, "deathCount", 0));
                                String time = jsStr(m, "startTime", jsStr(m, "startedAt", ""));
                                historyList.getItems().add("[" + id + "] " + mode + "  K/D " + kills + "/" + deaths + "  " + time);
                            }
                            if (historyList.getItems().isEmpty()) historyList.getItems().add("暂无历史战绩");
                        } else historyList.getItems().add("暂无历史战绩");
                    } catch (Exception ex) {
                        historyList.getItems().add("数据格式异常");
                        log("解析历史战绩异常: " + ex.getMessage() + " 原始: " + j, "error");
                    }
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> historyList.getItems().add("请求异常: " + ex.getMessage()));
                    log("获取历史战绩异常: " + ex.getMessage(), "error");
                    return null;
                });
    }

    private void fetchMatchResults(String matchId) {
        if (matchId == null || matchId.isEmpty()) { toast("无效的对局ID"); return; }
        api.getJson(API_BASE + "/matches/" + urlenc(matchId) + "/results", authToken)
                .thenAccept(j -> Platform.runLater(() -> {
                    resultsBox.getChildren().clear();
                    try {
                        if (jsHas(j,"status") && j.get("status").getAsInt()==0 && j.get("data").isJsonArray()) {
                            var title = new Label("对局 [" + matchId + "] 详情");
                            title.setStyle("-fx-font-weight:bold;");
                            var grid = new GridPane(); grid.setHgap(8); grid.setVgap(6);
                            addRow(grid, 0, "#", "玩家", "击杀", "死亡", true);
                            int r = 1;
                            for (var e : j.getAsJsonArray("data")) {
                                var row = e.getAsJsonObject();
                                int rk   = jsInt(row, "ranking", jsInt(row,"rank", r));
                                String n = jsStr(row, "nickname", jsStr(row,"name","?"));
                                int k    = jsInt(row, "kills", jsInt(row,"killCount", 0));
                                int d    = jsInt(row, "deaths", jsInt(row,"deathCount", 0));
                                addRow(grid, r++, String.valueOf(rk), n, String.valueOf(k), String.valueOf(d), false);
                            }
                            resultsBox.getChildren().addAll(title, grid);
                        } else {
                            resultsBox.getChildren().add(new Label("查询失败：" + jsStr(j,"message","")));
                            log("战绩查询失败: " + j, "error");
                        }
                    } catch (Exception ex) {
                        resultsBox.getChildren().add(new Label("解析失败: " + ex.getMessage()));
                        log("战绩解析异常: " + ex.getMessage() + " 原始: " + j, "error");
                    }
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> resultsBox.getChildren().add(new Label("请求异常: " + ex.getMessage())));
                    log("战绩请求异常: " + ex.getMessage(), "error");
                    return null;
                });
    }

    private void addRow(GridPane grid, int r, String a, String b, String c, String d, boolean header) {
        Label A = new Label(a), B = new Label(b), C = new Label(c), D = new Label(d);
        if (header) { A.setStyle("-fx-font-weight:bold;"); B.setStyle("-fx-font-weight:bold;"); C.setStyle("-fx-font-weight:bold;"); D.setStyle("-fx-font-weight:bold;"); }
        grid.addRow(r, A,B,C,D);
    }

    // ===== Json 安全取值工具（FIX: 解决后端字段缺失导致 NPE） =====
    private static boolean jsHas(JsonObject o, String k) {
        return o != null && o.has(k) && !o.get(k).isJsonNull();
    }
    private static String jsStr(JsonObject o, String k, String def) {
        try { return jsHas(o,k) ? o.get(k).getAsString() : def; } catch (Exception e) { return def; }
    }
    private static int jsInt(JsonObject o, String k, int def) {
        try { return jsHas(o,k) ? o.get(k).getAsInt() : def; } catch (Exception e) { return def; }
    }

    // ===== 小工具 =====
    private void log(String msg, String level) {
        Platform.runLater(() -> {
            var line = new HBox(6);
            var time = new Label("[" + Instant.now().toString().substring(11,19) + "]");
            time.setTextFill(Color.web("#9CA3AF"));
            var text = new Label(msg);
            switch (level) {
                case "error" -> text.setTextFill(Color.web("#DC2626"));
                case "success" -> text.setTextFill(Color.web("#059669"));
                case "ws" -> text.setTextFill(Color.web("#6D28D9"));
                default -> text.setTextFill(Color.web("#111827"));
            }
            line.getChildren().addAll(time, text);
            logBox.getChildren().add(0, line);
            if (logBox.getChildren().size() > 200) logBox.getChildren().remove(200, logBox.getChildren().size());
        });
    }
    private void toast(String text) { log(text, "error"); }
    private String urlenc(String s) { return s==null? "" : URLEncoder.encode(s, StandardCharsets.UTF_8); }
    private void copyToClipboard(String s) {
        if (s==null || s.isEmpty()) return;
        ClipboardContent c = new ClipboardContent(); c.putString(s); Clipboard.getSystemClipboard().setContent(c);
        log("已复制: " + s, "success");
    }
    // 提取 “(ID:xxx)” 或 “[xxx] …” 中的 ID
    private String extractId(String text) {
        if (text == null) return "";
        Pattern p = Pattern.compile("ID[:：]\\s*([\\w-]+)");
        Matcher m = p.matcher(text);
        if (m.find()) return m.group(1);
        // 备选：形如 "[matchId] ..." 取中括号内容
        Pattern p2 = Pattern.compile("\\[([^\\]]+)]");
        Matcher m2 = p2.matcher(text);
        if (m2.find()) return m2.group(1);
        return text.trim();
    }
}
