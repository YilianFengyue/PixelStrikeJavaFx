package org.csu.pixelstrikejavafx.lobby.ui;

import com.almasb.fxgl.dsl.FXGL;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import org.csu.pixelstrikejavafx.lobby.events.FriendStatusEvent;
import org.csu.pixelstrikejavafx.lobby.events.InvitationRejectedEvent;
import org.csu.pixelstrikejavafx.lobby.network.ApiClient;
import org.csu.pixelstrikejavafx.lobby.ui.dialog.DialogManager;

import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;

public class InviteFriendController implements Initializable {

    @FXML private ListView<Map<String, Object>> onlineFriendsListView;
    @FXML private FontAwesomeIconView closeIcon;

    private final ApiClient apiClient = new ApiClient();
    private final Set<Long> invitedPlayerIds = new HashSet<>();
    private Set<Long> playerIdsInRoom = new HashSet<>();

    private final javafx.event.EventHandler<FriendStatusEvent> friendStatusUpdateHandler = this::onFriendStatusUpdate;

    private Runnable closeHandler;

    public void setCloseHandler(Runnable closeHandler) {
        this.closeHandler = closeHandler;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupOnlineFriendsCellFactory();
        loadAllFriends();

        FXGL.getEventBus().addEventHandler(FriendStatusEvent.ANY, friendStatusUpdateHandler);

        onlineFriendsListView.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                System.out.println("InviteFriend view is closing, removing event listeners.");
                FXGL.getEventBus().removeEventHandler(FriendStatusEvent.ANY, friendStatusUpdateHandler);
            }
        });

        FXGL.getEventBus().addEventHandler(InvitationRejectedEvent.ANY, this::onInvitationRejected);
    }

    /**
     * 【关键新增方法】这个方法用于响应 FXML 中 onMouseClicked="#handleClose" 事件。
     */
    @FXML
    private void handleClose() {
        if (closeHandler != null) {
            closeHandler.run(); // 调用外部 DialogManager 设置的关闭处理程序
        }
    }

    private void loadAllFriends() {
        new Thread(() -> {
            try {
                List<Map<String, Object>> allFriends = apiClient.getFriends();
                Platform.runLater(() -> onlineFriendsListView.getItems().setAll(allFriends));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void setupOnlineFriendsCellFactory() {
        onlineFriendsListView.setCellFactory(lv -> new ListCell<Map<String, Object>>() {
            private final HBox hbox = new HBox(15); // 【修改】增加间距
            private final ImageView avatarView = new ImageView();
            private final HBox friendInfoHBox = new HBox(8); // 【新增】用于放置昵称和状态的HBox
            private final Label nicknameLabel = new Label();
            private final Label statusLabel = new Label();
            private final Region spacer = new Region();
            private final Button inviteButton = new Button();

            {
                // 应用来自 lobby-style.css 的样式
                hbox.getStyleClass().add("friend-cell-container");
                friendInfoHBox.getStyleClass().add("friend-info-group"); // 【新增】一个样式类
                nicknameLabel.getStyleClass().add("friend-nickname-label");
                statusLabel.getStyleClass().add("friend-status-label");
                inviteButton.getStyleClass().add("invite-cell-button");

                avatarView.setFitHeight(40);
                avatarView.setFitWidth(40);
                HBox.setHgrow(spacer, Priority.ALWAYS);
                friendInfoHBox.setAlignment(Pos.CENTER_LEFT); // 【修改】对齐
                friendInfoHBox.getChildren().addAll(nicknameLabel, statusLabel); // 【修改】HBox内部元素
                hbox.getChildren().addAll(avatarView, friendInfoHBox, spacer, inviteButton); // 【修改】hbox内部元素
            }

            @Override
            protected void updateItem(Map<String, Object> friend, boolean empty) {
                super.updateItem(friend, empty);

                if (empty || friend == null) {
                    setGraphic(null); // 如果是空行，不显示任何东西
                } else {
                    // --- 1. 获取数据 ---
                    String nickname = (String) friend.get("nickname");
                    long friendId = ((Number) friend.get("userId")).longValue();
                    String status = (String) friend.get("onlineStatus");
                    status = (status == null) ? "OFFLINE" : status.toUpperCase();

                    // --- 2. 更新UI内容 ---
                    nicknameLabel.setText(nickname);
                    statusLabel.setText(convertStatusToChinese(status));
                    avatarView.setImage(UIManager.loadAvatar((String) friend.get("avatarUrl")));

                    // --- 3. 动态更新样式 ---
                    // 先移除所有旧样式，防止因单元格复用导致样式错乱
                    hbox.getStyleClass().remove("offline-player");
                    statusLabel.getStyleClass().removeAll("online", "offline", "ingame", "in_room", "matching");

                    // 根据当前状态添加新样式
                    if ("OFFLINE".equals(status)) {
                        hbox.getStyleClass().add("offline-player"); // 让整个卡片变暗
                        statusLabel.getStyleClass().add("offline");   // 状态文字变灰
                    } else {
                        statusLabel.getStyleClass().add(status.toLowerCase()); // online, ingame...
                    }

                    // --- 4. 核心：智能设置邀请按钮的状态和文本 ---
                    // 判断优先级: 在房间内 > 已邀请 > 在线 > 其他(离线/游戏中等)
                    if (playerIdsInRoom.contains(friendId)) {
                        inviteButton.setDisable(true);
                        inviteButton.setText("在房间内");
                    } else if (invitedPlayerIds.contains(friendId)) {
                        inviteButton.setDisable(true);
                        inviteButton.setText("已邀请");
                    } else if ("ONLINE".equals(status)) {
                        inviteButton.setDisable(false);
                        inviteButton.setText("邀请");
                    } else {
                        inviteButton.setDisable(true);
                        inviteButton.setText(convertStatusToChinese(status));
                    }

                    // --- 5. 设置按钮点击事件 ---
                    inviteButton.setOnAction(event -> {
                        inviteButton.setDisable(true);
                        inviteButton.setText("已邀请");
                        invitedPlayerIds.add(friendId);

                        new Thread(() -> {
                            try {
                                apiClient.inviteFriend(friendId);
                                Platform.runLater(() -> DialogManager.showNotification("已向 " + nickname + " 发送邀请"));
                            } catch (Exception e) {
                                Platform.runLater(() -> {
                                    DialogManager.showMessage("邀请失败", e.getMessage());
                                    // 失败后恢复按钮状态
                                    inviteButton.setDisable(false);
                                    inviteButton.setText("邀请");
                                    invitedPlayerIds.remove(friendId);
                                });
                            }
                        }).start();
                    });

                    // --- 6. 将最终组装好的HBox设置为该单元格的图形 ---
                    setGraphic(hbox);
                }
            }
        });
    }

    private void onFriendStatusUpdate(FriendStatusEvent event) {
        Platform.runLater(() -> {
            long userId = event.getData().get("userId").getAsLong();
            String newStatus = event.getData().get("status").getAsString();

            for (Map<String, Object> friend : onlineFriendsListView.getItems()) {
                if (((Number) friend.get("userId")).longValue() == userId) {
                    friend.put("onlineStatus", newStatus);
                    break;
                }
            }
            onlineFriendsListView.refresh();
        });
    }

    private void onInvitationRejected(InvitationRejectedEvent event) {
        Platform.runLater(() -> {
            long rejectorId = event.getRejectorId();
            invitedPlayerIds.remove(rejectorId);
            onlineFriendsListView.refresh();
        });
    }

    private String convertStatusToChinese(String status) {
        if (status == null) return "离线";
        switch (status.toUpperCase()) {
            case "ONLINE": return "在线";
            case "OFFLINE": return "离线";
            case "INGAME": return "游戏中";
            case "IN_ROOM": return "房间中";
            case "MATCHING": return "匹配中";
            default: return status;
        }
    }
    public void updateRoomPlayers(Set<Long> playerIdsInRoom) {
        this.playerIdsInRoom = playerIdsInRoom;
        onlineFriendsListView.refresh(); // 收到新列表后立即刷新UI
    }
    public void resetState() {
        this.invitedPlayerIds.clear();
        // (可选) 刷新列表以确保UI立即更新
        if (onlineFriendsListView != null) {
            onlineFriendsListView.refresh();
        }
    }
}