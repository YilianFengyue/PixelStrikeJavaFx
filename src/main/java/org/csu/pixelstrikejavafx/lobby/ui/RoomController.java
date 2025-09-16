package org.csu.pixelstrikejavafx.lobby.ui;

import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.texture.AnimatedTexture;
import com.almasb.fxgl.texture.AnimationChannel;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ListCell;
import javafx.scene.image.Image;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.geometry.Pos;

import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Stage;
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
    @FXML
    private ImageView bg1;
    @FXML
    private ImageView bg2;

    private AnimationTimer backgroundScroller;

    // --- 新增：角色ID到动画文件名的映射 ---
    private static final Map<Integer, String> CHARACTER_ANIMATION_MAP = Map.of(
            1, "ash/ash_attack.png",
            2, "shu/shu_attack.png",
            3, "angel_neng/angel_neng_attack.png",
            4,"bluep_marthe/bluep_marthe_attack.png"

    );
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
            Image bg = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/assets/textures/Forest_background_11.png")));
            bg1.setImage(bg);
            bg2.setImage(bg);
            startBackgroundAnimation();
        } catch (Exception e) {
            System.err.println("房间背景图加载失败: " + e.getMessage());
        }
        changeCharacterButton.setStyle("-fx-text-fill: black;");
    }
    private void startBackgroundAnimation() {
        double speed = 0.5; // 控制滚动的速度，可以调整
        double sceneWidth = 1920.0; // 您的场景宽度

        // 初始时，让第二张图紧跟在第一张图的右边
        bg2.setTranslateX(sceneWidth);

        // 创建一个动画计时器，它会在每一帧被调用
        backgroundScroller = new AnimationTimer() {
            @Override
            public void handle(long now) {
                // 两张图都向左移动
                bg1.setTranslateX(bg1.getTranslateX() - speed);
                bg2.setTranslateX(bg2.getTranslateX() - speed);

                // 检查第一张图是否完全移出左边界
                if (bg1.getTranslateX() <= -sceneWidth) {
                    // 把它“传送”到第二张图的右边
                    bg1.setTranslateX(bg2.getTranslateX() + sceneWidth);
                }

                // 检查第二张图是否完全移出左边界
                if (bg2.getTranslateX() <= -sceneWidth) {
                    // 把它“传送”到第一张图的右边
                    bg2.setTranslateX(bg1.getTranslateX() + sceneWidth);
                }
            }
        };
        backgroundScroller.start();
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
                boolean isMe = GlobalState.userId != null && currentUserId == GlobalState.userId;

                // --- 1. 高亮自己和房主 ---

                if(currentUserId == hostId) {
                    slot.getStyleClass().add("host"); // 房主卡片特殊样式
                }else if (isMe) {
                    slot.setStyle("-fx-border-color: #3498db; -fx-border-width: 2px;");
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

                // 【新增】显示角色名
                String characterName = (String) player.get("characterName");
                Label characterLabel = new Label(characterName);
                characterLabel.getStyleClass().add("player-character");
                characterLabel.setStyle("-fx-text-fill: #f5f5f5;");
                // --- 核心修复 2：将角色名Label添加到VBox中 ---
                slot.getChildren().addAll(avatar, nickname, characterLabel);


                if (currentUserId == hostId) {
                    Label role = new Label("(房主)");
                    role.getStyleClass().add("player-role");
                    slot.getChildren().add(role);
                }

                // --- 2. 添加踢出和移交房主逻辑 ---
                if (amIHost && !isMe) {
                    // 如果我是房主，且这张卡片不是我
                    HBox buttonBox = new HBox(5);
                    buttonBox.setAlignment(Pos.CENTER);

                    Button kickButton = new Button("踢出");
                    kickButton.getStyleClass().add("kick-button");
                    kickButton.setOnAction(event -> handleKickPlayer(currentUserId, (String) player.get("nickname")));

                    Button transferButton = new Button("移交房主");
                    transferButton.getStyleClass().add("transfer-button");
                    transferButton.setOnAction(event -> handleTransferHost(currentUserId, (String) player.get("nickname")));

                    buttonBox.getChildren().addAll(kickButton, transferButton);
                    slot.getChildren().add(buttonBox);
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

    // --- 新增：将踢人和移交房主的逻辑提取为独立方法，使代码更清晰 ---
    private void handleKickPlayer(long targetId, String targetNickname) {
        DialogManager.showConfirmation("确认操作", "确定要将 " + targetNickname + " 踢出房间吗？", () -> {
            new Thread(() -> {
                try {
                    apiClient.kickPlayer(targetId);
                } catch (Exception e) {
                    Platform.runLater(() -> DialogManager.showMessage("操作失败", e.getMessage()));
                }
            }).start();
        });
    }

    private void handleTransferHost(long targetId, String targetNickname) {
        DialogManager.showConfirmation("确认操作", "确定要将房主移交给 " + targetNickname + " 吗？", () -> {
            new Thread(() -> {
                try {
                    apiClient.transferHost(targetId);
                } catch (Exception e) {
                    Platform.runLater(() -> DialogManager.showMessage("操作失败", e.getMessage()));
                }
            }).start();
        });
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

    /*@FXML
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
    }*/

    @FXML
    private void handleChangeCharacter() {
        // 这个方法现在调用新的、带动画的弹窗
        new Thread(() -> {
            try {
                List<Map<String, Object>> characters = apiClient.getCharacters();
                Platform.runLater(() -> {
                    showAnimatedCharacterSelectionDialog("更换角色", characters, selectedCharacter -> {
                        if (selectedCharacter == null) return; // 用户取消
                        long characterId = ((Number) selectedCharacter.get("id")).longValue();

                        new Thread(() -> {
                            try {
                                apiClient.changeCharacterInRoom(characterId);
                                // 成功后，等待后端广播 room_update 自动刷新
                            } catch (Exception e) {
                                Platform.runLater(() -> DialogManager.showMessage("更换失败", e.getMessage()));
                            }
                        }).start();
                    });
                });
            } catch (Exception e) {
                Platform.runLater(() -> DialogManager.showMessage("获取角色列表失败", e.getMessage()));
            }
        }).start();
    }

    /**
     * 新增：显示一个独立的、可拖动的、带动画的角色选择窗口
     * (逻辑从 LobbyController 移植而来)
     */
    private void showAnimatedCharacterSelectionDialog(String title, List<Map<String, Object>> characters, java.util.function.Consumer<Map<String, Object>> onItemSelected) {
        if (characters == null || characters.isEmpty()) {
            DialogManager.showMessage("错误", "没有可用的角色！");
            return;
        }

        // --- UI 组件 ---
        VBox rootPane = new VBox();
        rootPane.setPrefSize(400, 380);
        rootPane.setStyle("-fx-background-color: black; -fx-border-color: #4b5563; -fx-border-width: 1; -fx-background-radius: 8; -fx-border-radius: 8;");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10; -fx-cursor: move;");
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel.setAlignment(Pos.CENTER);

        Pane animationContainer = new Pane();
        animationContainer.setPrefSize(200, 200);
        Text characterName = new Text();
        characterName.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-fill: white;");
        Text characterDescription = new Text();
        characterDescription.setStyle("-fx-fill: #d1d5db;");
        VBox characterInfoBox = new VBox(5, characterName, characterDescription);
        characterInfoBox.setAlignment(Pos.CENTER);
        characterInfoBox.setPadding(new Insets(0, 10, 10, 10));

        Button leftButton = new Button("<");
        Button rightButton = new Button(">");
        VBox centerContent = new VBox(10, animationContainer, characterInfoBox);
        centerContent.setAlignment(Pos.CENTER);
        BorderPane displayArea = new BorderPane();
        displayArea.setCenter(centerContent);
        displayArea.setLeft(leftButton);
        displayArea.setRight(rightButton);
        BorderPane.setAlignment(leftButton, Pos.CENTER_LEFT);
        BorderPane.setAlignment(rightButton, Pos.CENTER_RIGHT);
        displayArea.setPadding(new Insets(5));

        Button btnOK = new Button("确定");
        Button btnCancel = new Button("取消");
        String buttonStyle = "-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5; -fx-cursor: hand;";
        String buttonHoverStyle = "-fx-background-color: #1d4ed8;";
        btnOK.setStyle(buttonStyle);
        btnCancel.setStyle(buttonStyle.replace("#2563eb", "#4b5563"));
        btnOK.setOnMouseEntered(e -> btnOK.setStyle(buttonStyle + buttonHoverStyle));
        btnOK.setOnMouseExited(e -> btnOK.setStyle(buttonStyle));
        HBox buttonBar = new HBox(10, btnOK, btnCancel);
        buttonBar.setAlignment(Pos.CENTER);
        buttonBar.setPadding(new Insets(10));

        rootPane.getChildren().addAll(titleLabel, new Separator(), displayArea, buttonBar);
        VBox.setVgrow(displayArea, Priority.ALWAYS);

        // --- 动画与逻辑 ---
        SimpleObjectProperty<Map<String, Object>> currentCharacter = new SimpleObjectProperty<>(characters.get(0));
        final int[] currentIndex = {0};
        final AnimatedTexture[] animatedTexture = {null};
        AnimationTimer timer = new AnimationTimer() {
            private long lastUpdate = 0;
            public void handle(long now) {
                if (lastUpdate == 0) { lastUpdate = now; return; }
                double tpf = (now - lastUpdate) / 1_000_000_000.0;
                if (animatedTexture[0] != null) animatedTexture[0].onUpdate(tpf);
                lastUpdate = now;
            }
        };

        Runnable updateDisplay = () -> {
            Map<String, Object> character = characters.get(currentIndex[0]);
            currentCharacter.set(character);
            characterName.setText((String) character.get("name"));
            characterDescription.setText((String) character.get("description"));
            int characterId = ((Number) character.get("id")).intValue();
            String animationFile = CHARACTER_ANIMATION_MAP.getOrDefault(characterId, "ash_idle.png");
            try {
                AnimationChannel animChannel = new AnimationChannel(FXGL.image("characters/" + animationFile), 15, 200, 200, Duration.seconds(0.8), 0, 14);
                animatedTexture[0] = new AnimatedTexture(animChannel);
                animatedTexture[0].loop();
                animationContainer.getChildren().setAll(animatedTexture[0]);
            } catch (Exception e) {
                System.err.println("加载动画失败: " + animationFile);
            }
        };

        ((Button)displayArea.getLeft()).setOnAction(e -> {
            currentIndex[0] = (currentIndex[0] - 1 + characters.size()) % characters.size();
            updateDisplay.run();
        });
        ((Button)displayArea.getRight()).setOnAction(e -> {
            currentIndex[0] = (currentIndex[0] + 1) % characters.size();
            updateDisplay.run();
        });
        updateDisplay.run();

        // --- 创建和显示窗口 ---
        Stage stage = new Stage();
        stage.initOwner(FXGL.getPrimaryStage());
        stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        stage.initStyle(javafx.stage.StageStyle.TRANSPARENT);
        Scene scene = new Scene(rootPane);
        scene.setFill(Color.TRANSPARENT);
        stage.setScene(scene);

        final double[] xOffset = {0}, yOffset = {0};
        titleLabel.setOnMousePressed(event -> {
            xOffset[0] = event.getSceneX();
            yOffset[0] = event.getSceneY();
        });
        titleLabel.setOnMouseDragged(event -> {
            stage.setX(event.getScreenX() - xOffset[0]);
            stage.setY(event.getScreenY() - yOffset[0]);
        });

        btnOK.setOnAction(e -> {
            stage.close();
            onItemSelected.accept(currentCharacter.get());
        });
        btnCancel.setOnAction(e -> {
            stage.close();
            onItemSelected.accept(null);
        });
        stage.setOnHidden(e -> timer.stop());

        timer.start();
        stage.showAndWait();
    }

    // 房间内专用的选择对话框辅助方法
    /*private void showCharacterSelectionDialog(List<Map<String, Object>> items, java.util.function.Consumer<Map<String, Object>> onItemSelected) {
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
    }*/

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
                    DialogManager.showMessage("开始游戏失败", e.getMessage());
                    startGameButton.setDisable(false);
                });
                e.printStackTrace();
            }
        }).start();
    }
}