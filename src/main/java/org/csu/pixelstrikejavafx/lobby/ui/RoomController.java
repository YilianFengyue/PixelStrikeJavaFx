package org.csu.pixelstrikejavafx.lobby.ui;

import com.almasb.fxgl.dsl.FXGL;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ListCell;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.geometry.Pos;

import org.csu.pixelstrikejavafx.lobby.events.KickedFromRoomEvent;
import org.csu.pixelstrikejavafx.lobby.events.RoomUpdateEvent;
import org.csu.pixelstrikejavafx.lobby.network.ApiClient;
import org.csu.pixelstrikejavafx.lobby.network.NetworkManager;
import org.csu.pixelstrikejavafx.core.GlobalState;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;


public class RoomController implements Initializable {
    @FXML
    private Label roomIdLabel;
    @FXML
    private ListView<JsonElement> playersListView;
    @FXML
    private Button leaveRoomButton;
    @FXML
    private Button startGameButton;

    private final ApiClient apiClient = new ApiClient();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 监听来自 NetworkManager 的房间状态更新事件
        FXGL.getEventBus().addEventHandler(RoomUpdateEvent.ANY, this::onRoomUpdate);
        FXGL.getEventBus().addEventHandler(KickedFromRoomEvent.ANY, this::onKickedFromRoom);

        setupPlayersCellFactory();

        JsonObject cachedMessage = NetworkManager.getInstance().getAndClearCachedRoomUpdate();
        if (cachedMessage != null) {
            // 如果有，说明 WebSocket 消息已经提前到了
            System.out.println("RoomController: 在'信箱'中找到了初始房间信息！");
            // 直接用这份数据来更新UI
            JsonObject roomData = cachedMessage.getAsJsonObject("room");

            // 關鍵修復：同時更新全域狀態和UI
            GlobalState.currentRoomInfo = roomData;
            updateRoomUI(roomData);
        } else {
            // 如果没有，说明消息还没到，界面暂时显示等待中
            // 我们只需要和平常一样等待 onRoomUpdate 事件被触发即可
            System.out.println("RoomController: '信箱'是空的，等待WebSocket事件...");
            roomIdLabel.setText("房间ID: 等待中...");
        }
    }

    // 当收到 WebSocket 推送的房间更新消息时，此方法被调用
    private void onRoomUpdate(RoomUpdateEvent event) {
        Platform.runLater(() -> {
            System.out.println("UI received: Room Update!");
            JsonObject roomData = event.getData().getAsJsonObject("room");
            GlobalState.currentRoomInfo = roomData; // 更新全局的房间信息
            updateRoomUI(roomData);
        });
    }

    /**
     * 当监听到自己被踢出房间的事件时，此方法被调用
     */
    private void onKickedFromRoom(KickedFromRoomEvent event) {
        Platform.runLater(() -> {
            // 1. 给玩家一个明确的弹窗提示
            FXGL.getDialogService().showMessageBox("您已被房主踢出房间。", () -> {
                // 2. 当玩家点击弹窗的“确定”按钮后，执行返回大厅的逻辑

                // 清理本地的房间信息
                GlobalState.currentRoomInfo = null;

                // 切换回大厅界面
                UIManager.load("lobby-view.fxml");
            });
        });
    }

    private void updateRoomUI(JsonObject roomData) {
        if (roomData == null) return;

        String roomId = roomData.get("roomId").getAsString();
        long hostId = roomData.get("hostId").getAsLong();

        roomIdLabel.setText("房间ID: " + roomId);

        // 直接将新的玩家列表数据设置给 ListView
        // 具体的显示逻辑由 CellFactory 负责
        playersListView.getItems().setAll(roomData.getAsJsonArray("players").asList());

        // 只有房主才能看到并点击“开始游戏”按钮
        startGameButton.setVisible(GlobalState.userId != null && GlobalState.userId == hostId);
    }

    @FXML
    private void handleLeaveRoom() {
        new Thread(() -> {
            try {
                apiClient.leaveRoom();
                Platform.runLater(() -> UIManager.load("lobby-view.fxml"));
            } catch (Exception e) {
                Platform.runLater(() -> FXGL.getDialogService().showMessageBox("离开房间失败: " + e.getMessage()));
            }
        }).start();
    }

    /**
     * 设置玩家列表 ListView 的单元格如何显示
     */
    private void setupPlayersCellFactory() {
        playersListView.setCellFactory(lv -> new ListCell<JsonElement>() {
            private HBox hbox = new HBox(10);
            private final ImageView avatarView = new ImageView();
            private Label playerInfoLabel = new Label();
            private Region spacer = new Region();
            private Button kickButton = new Button("踢出");

            {
                avatarView.setFitHeight(40);
                avatarView.setFitWidth(40);
                HBox.setHgrow(spacer, Priority.ALWAYS);
                hbox.setAlignment(Pos.CENTER_LEFT);
                hbox.getChildren().addAll(avatarView, playerInfoLabel, spacer, kickButton);
            }

            @Override
            protected void updateItem(JsonElement item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    JsonObject player = item.getAsJsonObject();
                    String nickname = player.get("nickname").getAsString();
                    long userId = player.get("userId").getAsLong();
                    boolean isHost = player.get("host").getAsBoolean();

                    playerInfoLabel.setText(nickname + (isHost ? " (房主)" : ""));

                    // 在使用 GlobalState.currentRoomInfo 之前，必须检查它是否为 null
                    if (GlobalState.currentRoomInfo != null) {
                        long hostId = GlobalState.currentRoomInfo.get("hostId").getAsLong();
                        boolean amIHost = GlobalState.userId != null && GlobalState.userId == hostId;

                        // 只有当前玩家是房主，并且列表项不是房主自己时，才显示踢出按钮
                        kickButton.setVisible(amIHost && !isHost);
                    } else {
                        // 如果房间信息尚未加载，则隐藏按钮
                        kickButton.setVisible(false);
                    }
                    kickButton.setOnAction(event -> {
                        new Thread(() -> {
                            try {
                                apiClient.kickPlayer(userId);
                                // 成功后，后端会推送 room_update 消息，界面会自动刷新
                            } catch (Exception e) {
                                Platform.runLater(() -> FXGL.getDialogService().showMessageBox("操作失败: " + e.getMessage()));
                            }
                        }).start();
                    });
                    avatarView.setImage(UIManager.loadAvatar(player.has("avatarUrl") ? player.get("avatarUrl").getAsString() : null));

                    setGraphic(hbox);
                }
            }
        });
    }

    @FXML
    private void handleInviteFriend() {
        try {
            // 1. 获取我们为邀请好友专门创建的 FXML 文件的路径
            URL url = FXGL.getAssetLoader().getURL("/fxml/invite-friend-view.fxml");
            if (url == null) {
                // 如果找不到文件，抛出一个明确的错误
                throw new IOException("找不到 FXML 文件: invite-friend-view.fxml");
            }

            // 2. 创建一个 FXMLLoader 实例来加载这个文件
            FXMLLoader loader = new FXMLLoader(url);
            Parent inviteDialogContent = loader.load(); // 加载 FXML 并获取其根节点

            // 3. 创建一个“关闭”按钮，用于我们即将弹出的对话框
            Button closeButton = new Button("关闭");

            // 1. 直接调用 showBox，不再试图接收它的返回值
            FXGL.getDialogService().showBox("邀请好友加入房间", inviteDialogContent, closeButton);

        } catch (Exception e) {
            System.err.println("无法加载好友邀请界面");
            e.printStackTrace();
            FXGL.getDialogService().showMessageBox("无法打开邀请界面，发生错误。");
        }
    }

    @FXML
    private void handleStartGame() {
        // 禁用按钮防止重复点击
        startGameButton.setDisable(true);

        new Thread(() -> {
            try {
                // 调用API，请求开始游戏
                apiClient.startGame();
                // 请求成功后，我们什么都不用做，只需等待 NetworkManager 接收 WebSocket 广播即可
            } catch (Exception e) {
                // 如果API调用失败（例如房间未满），在UI线程显示错误并恢复按钮
                Platform.runLater(() -> {
                    FXGL.getDialogService().showMessageBox("开始游戏失败: " + e.getMessage());
                    startGameButton.setDisable(false);
                });
                e.printStackTrace();
            }
        }).start();
    }
}