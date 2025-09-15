package org.csu.pixelstrikejavafx.lobby.ui;

import com.almasb.fxgl.dsl.FXGL;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ListCell;
import javafx.scene.image.Image;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.geometry.Pos;

import javafx.util.Duration;
import javafx.scene.layout.VBox;
import org.csu.pixelstrikejavafx.lobby.events.KickedFromRoomEvent;
import org.csu.pixelstrikejavafx.lobby.events.RoomUpdateEvent;
import org.csu.pixelstrikejavafx.lobby.network.ApiClient;
import org.csu.pixelstrikejavafx.lobby.network.NetworkManager;
import org.csu.pixelstrikejavafx.core.GlobalState;
import org.csu.pixelstrikejavafx.lobby.ui.dialog.DialogManager;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;


public class RoomController implements Initializable {
    @FXML private ImageView backgroundImageView;
    @FXML private Label roomIdLabel;
    @FXML private GridPane playersGridPane; // <-- 已从 ListView 修改为 GridPane
    @FXML private Button leaveRoomButton;
    @FXML private Button inviteButton;
    @FXML private Button startGameButton;
    @FXML private HBox mainContentBox;
    private final ApiClient apiClient = new ApiClient();
    private final Gson gson = new Gson();
    private VBox invitePane;
    private InviteFriendController inviteController;
    private boolean isInvitePanelVisible = false;
    @FXML
    private Button changeCharacterButton;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupUI();
        setupEventHandlers();
        loadInvitePanel(); // <-- 新增：在初始化时就提前加载好邀请面板
        checkForPendingNotifications();
        initializeRoomState();
    }

    private void setupUI() {
        try {
            Image bg = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/assets/textures/background.png")));
            backgroundImageView.setImage(bg);
        } catch (Exception e) {
            System.err.println("房间背景图加载失败: " + e.getMessage());
        }
    }
    private void setupEventHandlers() {
        FXGL.getEventBus().addEventHandler(RoomUpdateEvent.ANY, this::onRoomUpdate);
        FXGL.getEventBus().addEventHandler(KickedFromRoomEvent.ANY, this::onKickedFromRoom);
    }
    private void loadInvitePanel() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/invite-friend-view.fxml"));
            invitePane = loader.load();
            inviteController = loader.getController();

            // 设置关闭处理逻辑：点击面板内的关闭箭头时，也执行隐藏面板的动画
            inviteController.setCloseHandler(() -> toggleInvitePanel(false));

            // 设置初始状态
            invitePane.setVisible(false);
            invitePane.setManaged(false); // 不参与布局计算

            // 将加载好的面板添加到右侧
            mainContentBox.getChildren().add(invitePane);
        } catch (IOException e) {
            e.printStackTrace();
            // 如果加载失败，可以禁用邀请按钮
            inviteButton.setDisable(true);
        }
    }
    @FXML
    private void handleInviteFriend() {
        if (invitePane == null) return;
        if (!isInvitePanelVisible && inviteController != null) {
            inviteController.resetState();
            updateInvitePanelWithCurrentPlayers();
        }

        toggleInvitePanel(!isInvitePanelVisible);
    }
    private void toggleInvitePanel(boolean show) {
        if (isInvitePanelVisible == show) return; // 如果已经是目标状态，则不执行操作

        isInvitePanelVisible = show;

        if (show) {
            invitePane.setManaged(true);

            // 刷新好友列表，确保数据最新
            if(inviteController != null) {
                // 这里需要调用InviteFriendController中的一个公共方法来刷新列表
                // 我们假设 InviteFriendController 中有一个 public void refreshList()
                // 如果没有，您需要在 InviteFriendController 中添加
                // inviteController.loadAllFriends();
            }
        }

        // 创建动画
        FadeTransition ft = new FadeTransition(Duration.millis(300), invitePane);
        TranslateTransition tt = new TranslateTransition(Duration.millis(300), invitePane);

        if (show) {
            invitePane.setOpacity(0);
            invitePane.setTranslateX(50); // 从右侧50像素的位置开始
            invitePane.setVisible(true);
            ft.setToValue(1.0);
            tt.setToX(0);
        } else {
            ft.setToValue(0);
            tt.setToX(50);
            ft.setOnFinished(e -> {
                invitePane.setVisible(false);
                invitePane.setManaged(false); // 动画结束后再从布局中移除
            });
        }

        ParallelTransition transition = new ParallelTransition(ft, tt);
        transition.play();
    }
    private void checkForPendingNotifications() {
        Platform.runLater(() -> {
            String message = UIManager.getAndClearMessageForNextScreen();
            if (message != null) {
                DialogManager.showNotification(message);
            }
        });
    }
    private void initializeRoomState() {
        JsonObject cachedMessage = NetworkManager.getInstance().getAndClearCachedRoomUpdate();
        if (cachedMessage != null) {
            System.out.println("RoomController: 在'信箱'中找到了初始房间信息！");
            JsonObject roomData = cachedMessage.getAsJsonObject("room");

            GlobalState.currentRoomInfo = roomData;
            updateRoomUI(roomData);
        } else {
            System.out.println("RoomController: '信箱'是空的，等待WebSocket事件...");
            roomIdLabel.setText("房间ID: 等待中...");
            // 显示一个空的网格
            updatePlayersGrid(List.of());
        }
    }
    private void updatePlayersGrid(List<Map<String, Object>> players) {
        playersGridPane.getChildren().clear();

        // 1. 确定当前客户端的玩家是不是房主
        long hostId = -1;
        boolean amIHost = false;
        for (Map<String, Object> player : players) {
            if ((Boolean) player.getOrDefault("host", false)) {
                hostId = ((Number) player.get("userId")).longValue();
                break;
            }
        }
        if (GlobalState.userId != null && GlobalState.userId == hostId) {
            amIHost = true;
        }

        // 2. 循环创建8个槽位
        for (int i = 0; i < 8; i++) {
            VBox slot;
            int rowIndex = i / 4;
            int colIndex = i % 4;

            if (i < players.size()) {
                Map<String, Object> player = players.get(i);
                slot = new VBox();
                slot.getStyleClass().add("player-card");

                long currentUserId = ((Number) player.get("userId")).longValue();
                if (currentUserId == hostId) {
                    slot.getStyleClass().add("host");
                }

                ImageView avatar = new ImageView();
                avatar.setFitHeight(80);
                avatar.setFitWidth(80);
                avatar.getStyleClass().add("player-avatar");
                JsonElement avatarUrlElement = (JsonElement) player.get("avatarUrl");
                String avatarUrl = (avatarUrlElement != null && !avatarUrlElement.isJsonNull()) ? avatarUrlElement.getAsString() : null;
                avatar.setImage(UIManager.loadAvatar(avatarUrl));

                Label nickname = new Label((String) player.get("nickname"));
                nickname.getStyleClass().add("player-nickname");

                slot.getChildren().addAll(avatar, nickname);

                if (currentUserId == hostId) {
                    Label role = new Label("(房主)");
                    role.getStyleClass().add("player-role");
                    slot.getChildren().add(role);
                }

                // --- ↓↓↓【关键新增代码】创建并添加“踢出”按钮 ↓↓↓ ---
                if (amIHost && currentUserId != hostId) {
                    // 如果“我”是房主，并且这张卡片不是房主自己的
                    Button kickButton = new Button("踢出");
                    kickButton.getStyleClass().add("kick-button");

                    // 为按钮添加点击事件
                    kickButton.setOnAction(event -> {
                        DialogManager.showConfirmation("确认操作", "确定要将 " + player.get("nickname") + " 踢出房间吗？", () -> {
                            new Thread(() -> {
                                try {
                                    apiClient.kickPlayer(currentUserId);
                                    // 成功后无需做任何事，等待后端推送 room_update 消息自动刷新界面
                                } catch (Exception e) {
                                    Platform.runLater(() -> DialogManager.showMessage("操作失败", e.getMessage()));
                                }
                            }).start();
                        });
                    });

                    // 将按钮添加到卡片中
                    slot.getChildren().add(kickButton);
                }
                // --- ↑↑↑ 新增代码结束 ↑↑↑ ---

            } else {
                slot = new VBox();
                slot.getStyleClass().add("empty-slot");
                slot.getChildren().add(new Label("等待加入..."));
            }
            playersGridPane.add(slot, colIndex, rowIndex);
        }
    }
    // 当收到 WebSocket 推送的房间更新消息时，此方法被调用
    private void onRoomUpdate(RoomUpdateEvent event) {
        Platform.runLater(() -> {
            System.out.println("UI received: Room Update!");
            JsonObject roomData = event.getData().getAsJsonObject("room");
            GlobalState.currentRoomInfo = roomData;
            updateRoomUI(roomData);

            // 【新增】如果邀请面板当前是打开的，就实时更新它
            if (isInvitePanelVisible && inviteController != null) {
                updateInvitePanelWithCurrentPlayers();
            }
        });
    }

    private void updateInvitePanelWithCurrentPlayers() {
        if (GlobalState.currentRoomInfo == null) return;

        Set<Long> playerIds = new HashSet<>();
        JsonArray playersArray = GlobalState.currentRoomInfo.getAsJsonArray("players");
        for (JsonElement playerElement : playersArray) {
            playerIds.add(playerElement.getAsJsonObject().get("userId").getAsLong());
        }

        inviteController.updateRoomPlayers(playerIds);
    }

    /**
     * 当监听到自己被踢出房间的事件时，此方法被调用
     */
    private void onKickedFromRoom(KickedFromRoomEvent event) {
        Platform.runLater(() -> {
            GlobalState.currentRoomInfo = null;
            DialogManager.showMessage("通知", "您已被房主踢出房间");
            UIManager.load("lobby-view.fxml");
        });
    }

    private void updateRoomUI(JsonObject roomData) {
        if (roomData == null) return;
        String roomId = roomData.get("roomId").getAsString();
        long hostId = roomData.get("hostId").getAsLong();
        roomIdLabel.setText("房间ID: " + roomId);
        // 解析玩家列表
        JsonArray playersArray = roomData.getAsJsonArray("players");
        Type listType = new TypeToken<List<Map<String, Object>>>() {}.getType();
        List<Map<String, Object>> playersList = gson.fromJson(playersArray, listType);

        // 调用新方法来刷新玩家网格
        updatePlayersGrid(playersList);

        // 根据自己是否是房主，来决定“开始游戏”按钮的可见性
        startGameButton.setVisible(GlobalState.userId != null && GlobalState.userId == hostId);
    }

    @FXML
    private void handleLeaveRoom() {
        new Thread(() -> {
            try {
                apiClient.leaveRoom();
                Platform.runLater(() -> {
                    GlobalState.currentRoomInfo = null; // 清理本地房间状态
                    UIManager.showMessageOnNextScreen("已离开房间");
                    UIManager.load("lobby-view.fxml");
                });
            } catch (Exception e) {
                Platform.runLater(() -> DialogManager.showMessage("操作失败", e.getMessage()));
            }
        }).start();
    }

    @FXML
    private void handleChangeCharacter() {
        new Thread(() -> {
            try {
                List<Map<String, Object>> characters = apiClient.getCharacters();
                Platform.runLater(() -> {
                    DialogManager.showCharacterSelection("更换角色", characters, selectedCharacter -> {
                        if (selectedCharacter == null) return; // 用户取消
                        long characterId = ((Number) selectedCharacter.get("id")).longValue();

                        new Thread(() -> {
                            try {
                                apiClient.changeCharacterInRoom(characterId);
                            } catch (Exception e) {
                                Platform.runLater(() -> FXGL.getDialogService().showMessageBox("更换角色失败: " + e.getMessage()));
                            }
                        }).start();
                    });
                });
            } catch (Exception e) {
                Platform.runLater(() -> FXGL.getDialogService().showMessageBox("获取角色列表失败: " + e.getMessage()));
            }
        }).start();
    }

    // 房间内专用的选择对话框辅助方法
    private void showCharacterSelectionDialog(List<Map<String, Object>> items, java.util.function.Consumer<Map<String, Object>> onItemSelected) {
        Dialog<Map<String, Object>> dialog = new Dialog<>();
        dialog.setTitle("更换角色");

        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ListView<Map<String, Object>> listView = new ListView<>();
        listView.getItems().setAll(items);
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Map<String, Object> item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setText(null);
                } else {
                    // 可以显示更详细的信息
                    setText(String.format("%s (生命: %s, 速度: %s)",
                            item.get("name"), item.get("health"), item.get("speed")));
                }
            }
        });
        listView.setPrefHeight(200);

        VBox content = new VBox(10, listView);
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                return listView.getSelectionModel().getSelectedItem();
            }
            return null;
        });

        dialog.showAndWait().ifPresent(onItemSelected);
    }

    /**
     * 设置玩家列表 ListView 的单元格如何显示
     */


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
                Platform.runLater(() -> {
                    FXGL.getDialogService().showMessageBox("开始游戏失败: " + e.getMessage());
                    startGameButton.setDisable(false);
                });
                e.printStackTrace();
            }
        }).start();
    }
}