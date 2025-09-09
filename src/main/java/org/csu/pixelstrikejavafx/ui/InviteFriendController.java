package org.csu.pixelstrikejavafx.ui;

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
import org.csu.pixelstrikejavafx.events.InvitationRejectedEvent;
import org.csu.pixelstrikejavafx.http.ApiClient;

import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;

public class InviteFriendController implements Initializable {

    @FXML private ListView<Map<String, Object>> onlineFriendsListView;
    private final ApiClient apiClient = new ApiClient();

    // 用于跟踪已经邀请过的玩家ID，防止重复邀请
    private final Set<Long> invitedPlayerIds = new HashSet<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupOnlineFriendsCellFactory();
        loadOnlineFriends();
        FXGL.getEventBus().addEventHandler(InvitationRejectedEvent.ANY, this::onInvitationRejected);
    }

    private void loadOnlineFriends() {
        new Thread(() -> {
            try {
                List<Map<String, Object>> allFriends = apiClient.getFriends();

                // 筛选出在线的好友 (状态不是 null 或 OFFLINE)
                List<Map<String, Object>> onlineFriends = allFriends.stream()
                        .filter(friend -> friend.get("onlineStatus") != null && !friend.get("onlineStatus").equals("OFFLINE"))
                        .collect(Collectors.toList());

                Platform.runLater(() -> onlineFriendsListView.getItems().setAll(onlineFriends));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void setupOnlineFriendsCellFactory() {
        onlineFriendsListView.setCellFactory(lv -> new ListCell<Map<String, Object>>() {
            private final HBox hbox = new HBox(10);
            private final ImageView avatarView = new ImageView();
            private final Label friendInfoLabel = new Label();
            private final Region spacer = new Region();
            private final Button inviteButton = new Button("邀请");

            {
                avatarView.setFitHeight(40);
                avatarView.setFitWidth(40);
                HBox.setHgrow(spacer, Priority.ALWAYS);
                hbox.setAlignment(Pos.CENTER_LEFT);
                inviteButton.setMinWidth(60);
                hbox.getChildren().addAll(avatarView, friendInfoLabel, spacer, inviteButton);
            }

            @Override
            protected void updateItem(Map<String, Object> friend, boolean empty) {
                super.updateItem(friend, empty);
                if (empty || friend == null) {
                    setGraphic(null);
                } else {
                    String nickname = (String) friend.get("nickname");
                    String status = (String) friend.get("onlineStatus");
                    friendInfoLabel.setText(String.format("%s [%s]", nickname, status));

                    long friendId = ((Number) friend.get("userId")).longValue();

                    // 检查该好友是否已被邀请
                    if (invitedPlayerIds.contains(friendId)) {
                        inviteButton.setDisable(true);
                        inviteButton.setText("已邀请");
                    } else {
                        inviteButton.setDisable(false);
                        inviteButton.setText("邀请");
                    }

                    inviteButton.setOnAction(event -> {
                        inviteButton.setDisable(true);
                        inviteButton.setText("已邀请");
                        invitedPlayerIds.add(friendId); // 记录已邀请

                        new Thread(() -> {
                            try {
                                System.out.println("DEBUG: 准备向后端API发送邀请, 目标好友ID: " + friendId);
                                apiClient.inviteFriend(friendId);
                                Platform.runLater(() -> FXGL.getNotificationService().pushNotification("已向 " + nickname + " 发送邀请"));
                            } catch (Exception e) {
                                Platform.runLater(() -> {
                                    FXGL.getDialogService().showMessageBox("邀请失败: " + e.getMessage());
                                    inviteButton.setDisable(false); // 失败后恢复按钮
                                    inviteButton.setText("邀请");
                                    invitedPlayerIds.remove(friendId); // 失败后移除记录
                                });
                            }
                        }).start();
                    });
                    avatarView.setImage(UIManager.loadAvatar((String) friend.get("avatarUrl")));

                    setGraphic(hbox);
                }
            }
        });
    }

    // **新增：处理邀请被拒绝的逻辑**
    private void onInvitationRejected(InvitationRejectedEvent event) {
        Platform.runLater(() -> {
            long rejectorId = event.getRejectorId();
            // 从已邀请集合中移除，这样下次刷新列表时按钮就会恢复
            invitedPlayerIds.remove(rejectorId);
            // 强制刷新 ListView 来更新按钮状态
            onlineFriendsListView.refresh();
        });
    }



}