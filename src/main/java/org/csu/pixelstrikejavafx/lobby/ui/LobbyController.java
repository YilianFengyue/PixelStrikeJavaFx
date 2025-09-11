package org.csu.pixelstrikejavafx.lobby.ui;

import com.almasb.fxgl.dsl.FXGL;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.geometry.Pos;


import javafx.stage.FileChooser;
import org.csu.pixelstrikejavafx.core.MatchSuccessEvent;
import org.csu.pixelstrikejavafx.lobby.events.*;
import org.csu.pixelstrikejavafx.lobby.network.ApiClient;
import org.csu.pixelstrikejavafx.lobby.network.NetworkManager;
import org.csu.pixelstrikejavafx.core.GlobalState;

import java.io.File;
import java.net.URL;
import java.util.*;


/**
 * LobbyController 负责处理 lobby-view.fxml 的所有用户交互。
 * 例如：显示好友列表、处理匹配按钮点击、监听服务器事件等。
 */
public class LobbyController implements Initializable {

    @FXML
    private ListView<Map<String, Object>> friendsListView;

    @FXML
    private Button startMatchButton;

    @FXML
    private Label matchStatusLabel;

    @FXML
    private TextField roomIdField;

    @FXML
    private Label nicknameLabel;

    @FXML private TextField searchUserField;
    @FXML private ListView<Map<String, Object>> searchResultListView;
    @FXML private ListView<Map<String, Object>> requestsListView;
    @FXML private ImageView avatarImageView;
    private final Set<Long> friendIds = new HashSet<>();

    private final ApiClient apiClient = new ApiClient();

    /**
     * FXML 界面加载完成时，该方法会自动被调用。
     * 我们在这里注册所有需要的事件监听器。
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("LobbyController initialized. Setting up event handlers.");


        // 1. 监听好友状态更新事件
        FXGL.getEventBus().addEventHandler(FriendStatusEvent.ANY, event -> {
            // 这个回调可能不在JavaFX应用线程，所以更新UI需要用 Platform.runLater
            Platform.runLater(() -> {
                JsonObject data = event.getData();
                String nickname = data.get("nickname").getAsString();
                String status = data.get("status").getAsString();
                System.out.println(String.format("UI received: Friend %s is now %s", nickname, status));

                // 在这里编写更新好友列表UI的逻辑
                // 例如：遍历 friendsListView 找到对应的项并更新其状态文本
            });
        });

        // 2. 监听匹配成功事件
        FXGL.getEventBus().addEventHandler(MatchSuccessEvent.ANY, event -> {
            Platform.runLater(() -> {
                System.out.println("UI received: Match Success! Preparing to start game...");
                if(matchStatusLabel != null) {
                    matchStatusLabel.setText("匹配成功！正在进入游戏...");
                }

                // 1. 将游戏服务器地址存入全局状态，以便游戏场景启动时读取
                GlobalState.currentGameServerUrl = event.getServerAddress();
                System.out.println("Game Server URL saved to GlobalState: " + GlobalState.currentGameServerUrl);
                GlobalState.currentGameId = event.getGameId();

                // 2. 断开当前的全局(大厅) WebSocket
                NetworkManager.getInstance().disconnect();
                System.out.println("Lobby WebSocket disconnected.");

                // 3. 【关键修复】启动游戏场景
                System.out.println("Starting new game scene...");
                FXGL.getGameController().startNewGame();
            });
        });


        // 1. 立即为所有 ListView 设置它们的“美化”方法
        setupFriendsCellFactory();
        setupSearchResultCellFactory();
        setupRequestsCellFactory();

        // 2. 设置事件监听器
        setupEventHandlers();

        // 3. 加载初始数据
        loadFriendsList();
        loadFriendRequests();
        loadAvatar();

        if (GlobalState.nickname != null) {
            nicknameLabel.setText("昵称: " + GlobalState.nickname);
        } else {
            nicknameLabel.setText("昵称: 未知");
        }
    }

    /**
     * 处理点击“开始匹配”按钮的事件
     */
    @FXML
    private void handleStartMatch() {
        // 确保 matchStatusLabel 不为 null 后再使用
        if (matchStatusLabel != null) {
            matchStatusLabel.setText("正在发送匹配请求...");
        }
        startMatchButton.setDisable(true); // 禁用按钮，防止重复点击

        // 必须在后台线程中执行网络请求，否则UI会卡死
        new Thread(() -> {
            try {
                // 调用我们刚刚在 ApiClient 中添加的方法
                apiClient.startMatchmaking();

                // 网络请求成功后，在UI线程上更新界面
                Platform.runLater(() -> {
                    if (matchStatusLabel != null) {
                        matchStatusLabel.setText("已进入匹配队列，等待服务器通知...");
                    }
                    // 可以在这里启用“取消匹配”按钮（如果需要）
                });

            } catch (Exception e) {
                // 如果请求失败（例如网络问题或后端返回错误），在UI线程上显示错误信息
                Platform.runLater(() -> {
                    if (matchStatusLabel != null) {
                        matchStatusLabel.setText("开始匹配失败: " + e.getMessage());
                    }
                    startMatchButton.setDisable(false); // 恢复按钮的可点击状态
                });
                e.printStackTrace(); // 在控制台打印详细错误，便于调试
            }
        }).start();
    }


    @FXML
    private void handleStartGame() {
        System.out.println("开始游戏！正在加载游戏世界...");
        // 调用 FXGL 核心方法来启动游戏
        // 这会触发 PixelGameApp.java 中的 initGame() 方法
        FXGL.getGameController().startNewGame();
    }

    @FXML
    private void handleLogout() {
        System.out.println("用户请求登出...");

        // 因为登出需要进行网络请求，所以必须放在后台线程
        new Thread(() -> {
            try {
                // 1. 先调用后端的登出接口
                apiClient.logout();
                // 2. 后端成功登出后，再执行客户端的清理 (必须在UI线程)
                Platform.runLater(() -> {
                    // 主动断开全局 WebSocket 连接
                    NetworkManager.getInstance().disconnect();
                    // 清除本地存储的用户凭证 (token)
                    GlobalState.authToken = null;
                    // 使用 UIManager 切换回登录界面
                    UIManager.load("login-view.fxml");
                });
            } catch (Exception e) {
                // 如果登出失败，在UI上给出提示
                Platform.runLater(() -> {
                    System.err.println("登出失败: " + e.getMessage());
                    // 你可以在大厅界面添加一个 Label 来显示这个错误
                });
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * 从后端加载好友列表并更新UI
     */

    @FXML
    private void loadFriendsList() {
        friendsListView.getItems().clear();

        new Thread(() -> {
            try {
                // 1. 调用API获取好友数据
                List<Map<String, Object>> friends = apiClient.getFriends();

                // 2. 更新好友ID缓存 (这部分逻辑不变)
                friendIds.clear();
                for (Map<String, Object> friend : friends) {
                    friendIds.add(((Number) friend.get("userId")).longValue());
                }

                // 3. 在UI线程更新 ListView
                Platform.runLater(() -> {
                    if (friends.isEmpty()) {
                    } else {
                        friendsListView.getItems().setAll(friends);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    e.printStackTrace();
                });
            }
        }).start();
    }
    /**
     * 处理点击“搜索”按钮的事件
     */
    @FXML
    private void handleSearchUser() {
        String nickname = searchUserField.getText().trim();
        if (nickname.isEmpty()) {
            // 可以在这里加一个提示标签
            return;
        }

        searchResultListView.getItems().clear();
        searchResultListView.getItems().add(Map.of("loading", true)); // 显示加载提示

        new Thread(() -> {
            try {
                List<Map<String, Object>> users = apiClient.searchUsers(nickname);
                Platform.runLater(() -> {
                    searchResultListView.getItems().clear();
                    if (users.isEmpty()) {
                        // 如果需要，可以显示“未找到用户”
                    } else {
                        searchResultListView.getItems().addAll(users);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    searchResultListView.getItems().clear();
                    // 显示错误
                });
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * 设置搜索结果 ListView 的单元格如何显示
     */
    private void setupSearchResultCellFactory() {
        searchResultListView.setCellFactory(lv -> new ListCell<Map<String, Object>>() {
            private final HBox hbox = new HBox(10);
            private final ImageView avatarView = new ImageView();
            private final Label userInfoLabel = new Label();
            private final Region spacer = new Region();
            private final Button actionButton = new Button();

            {
                avatarView.setFitHeight(40);
                avatarView.setFitWidth(40);
                HBox.setHgrow(spacer, Priority.ALWAYS);
                hbox.setAlignment(Pos.CENTER_LEFT);
                hbox.getChildren().addAll(avatarView, userInfoLabel, spacer, actionButton);
            }

            @Override
            protected void updateItem(Map<String, Object> user, boolean empty) {
                super.updateItem(user, empty);
                if (empty || user == null) {
                    setGraphic(null);
                } else if (user.containsKey("loading")) {
                    setText("正在搜索...");
                    setGraphic(null);
                }
                else {
                    setText(null);
                    String nickname = (String) user.get("nickname");
                    String status = (String) user.get("onlineStatus");
                    status = (status == null) ? "离线" : status;
                    userInfoLabel.setText(String.format("%s [%s]", nickname, status));
                    avatarView.setImage(UIManager.loadAvatar((String) user.get("avatarUrl")));
                    long userId = ((Number) user.get("userId")).longValue();



                    // 1. 判断是不是自己
                    if (GlobalState.userId != null && GlobalState.userId == userId) {
                        actionButton.setDisable(true);
                        actionButton.setText("自己");
                    }
                    // 2. 判断是不是已经是好友
                    else if (friendIds.contains(userId)) {
                        actionButton.setDisable(true);
                        actionButton.setText("已是好友");
                    }
                    // 3. 否则就是陌生人，可以添加
                    else {
                        actionButton.setDisable(false);
                        actionButton.setText("添加");
                        actionButton.setOnAction(event -> {
                            actionButton.setDisable(true);
                            actionButton.setText("已申请");

                            new Thread(() -> {
                                try {
                                    apiClient.sendFriendRequest(userId);
                                } catch (Exception e) {
                                    Platform.runLater(() -> {
                                        FXGL.getDialogService().showMessageBox("申请失败: " + e.getMessage());
                                        // 失败后可以恢复按钮状态，但为了防止刷屏，暂时不恢复
                                    });
                                }
                            }).start();
                        });
                    }

                    setGraphic(hbox);
                }
            }
        });
    }
    /**
     * 加载待处理的好友申请并更新UI
     */
    private void loadFriendRequests() {
        requestsListView.getItems().clear();
        requestsListView.getItems().add(Map.of("loading", true)); // 显示加载提示

        new Thread(() -> {
            try {
                List<Map<String, Object>> requests = apiClient.getFriendRequests();
                System.out.println("DEBUG (LobbyController): 从ApiClient收到了 " + requests.size() + " 条申请。");
                Platform.runLater(() -> {
                    System.out.println("DEBUG (LobbyController): 即将更新UI界面，显示 " + requests.size() + " 条申请。");
                    requestsListView.getItems().clear();
                    if (requests.isEmpty()) {
                        // 可以显示“没有待处理的申请”
                    } else {
                        requestsListView.getItems().addAll(requests);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    requestsListView.getItems().clear();
                    // 显示错误
                });
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * 设置好友申请 ListView 的单元格如何显示
     */
    private void setupRequestsCellFactory() {
        requestsListView.setCellFactory(lv -> new ListCell<Map<String, Object>>() {
            private final HBox hbox = new HBox(10);
            private final ImageView avatarView = new ImageView();

            // 2. 使用 Label 显示用户信息，它比 Text 更适合布局
            private final Label requestInfoLabel = new Label();

            // 3. 创建一个看不见的、可伸缩的“弹簧”
            private final Region spacer = new Region();

            // 4. 创建按钮
            private final Button acceptButton = new Button("同意");
            private final Button rejectButton = new Button("拒绝"); // 顺便加上拒绝按钮

            {
                avatarView.setFitHeight(40);
                avatarView.setFitWidth(40);
                // 5. 设置“弹簧”：让它占据所有可用的水平空间
                HBox.setHgrow(spacer, Priority.ALWAYS);

                // 设置按钮的最小宽度，防止被过度压缩
                acceptButton.setMinWidth(60);
                rejectButton.setMinWidth(60);

                // 6. 将所有控件按顺序放入 HBox
                hbox.setAlignment(Pos.CENTER_LEFT);
                hbox.getChildren().addAll(avatarView, requestInfoLabel, spacer, acceptButton, rejectButton);
            }

            @Override
            protected void updateItem(Map<String, Object> request, boolean empty) {
                super.updateItem(request, empty);
                if (empty || request == null) {
                    setGraphic(null);
                } else if (request.containsKey("loading")) {
                    setText("正在加载申请...");
                    setGraphic(null);
                } else {
                    String nickname = (String) request.get("nickname");
                    String status = (String) request.get("onlineStatus");

                    // 2. 根据后端API文档，null 代表离线，我们做一下转换
                    if (status == null) {
                        status = "离线";
                    }

                    // 3. 将昵称和状态格式化后，设置给 Label
                    requestInfoLabel.setText(String.format("%s [%s]", nickname, status));
                    avatarView.setImage(UIManager.loadAvatar((String) request.get("avatarUrl")));

                    acceptButton.setDisable(false);
                    acceptButton.setText("同意");

                    acceptButton.setOnAction(event -> {
                        acceptButton.setDisable(true);
                        acceptButton.setText("已同意");

                        long userId = ((Number) request.get("userId")).longValue();

                        new Thread(() -> {
                            try {
                                apiClient.acceptFriendRequest(userId);
                                // 同意成功后，刷新好友列表和申请列表
                                Platform.runLater(() -> {
                                    loadFriendRequests(); // 重新加载申请列表，这一条会消失
                                    loadFriendsList();    // 重新加载好友列表，新好友会出现在那里
                                });
                            } catch (Exception e) {
                                Platform.runLater(() -> {
                                    acceptButton.setDisable(false);
                                    acceptButton.setText("同意");
                                    FXGL.getDialogService().showMessageBox("操作失败: " + e.getMessage());
                                });
                            }
                        }).start();
                    });
                    rejectButton.setOnAction(event -> {
                        // 在这里可以实现拒绝好友申请的逻辑
                        // 然后从列表中移除这一项
                        this.getListView().getItems().remove(request);
                    });


                    setGraphic(hbox);
                }
            }
        });
    }

    private void setupEventHandlers() {
        // 监听好友状态更新
        FXGL.getEventBus().addEventHandler(FriendStatusEvent.ANY, this::onFriendStatusUpdate);

       /* // 监听匹配成功
        FXGL.getEventBus().addEventHandler(MatchSuccessEvent.ANY, this::onMatchSuccess);*/

        // 监听“收到新好友申请”事件
        FXGL.getEventBus().addEventHandler(NewFriendRequestEvent.ANY, this::onNewFriendRequest);

        // 监听“好友申请被接受”事件
        FXGL.getEventBus().addEventHandler(FriendRequestAcceptedEvent.ANY, this::onFriendRequestAccepted);

        FXGL.getEventBus().addEventHandler(RoomInvitationEvent.ANY, this::onRoomInvitation);
    }

    private void onFriendStatusUpdate(FriendStatusEvent event) {
        // 确保所有UI操作都在JavaFX应用线程中执行
        Platform.runLater(() -> {
            JsonObject data = event.getData();
            long userId = data.get("userId").getAsLong();
            String status = data.get("status").getAsString();
            String nickname = data.get("nickname").getAsString(); // 用于打印日志

            System.out.println(String.format("UI Handling: Friend '%s' (ID: %d) status changed to '%s'", nickname, userId, status));
            for (Map<String, Object> friend : friendsListView.getItems()) {

                long currentFriendId = ((Number) friend.get("userId")).longValue();
                if (currentFriendId == userId) {
                    friend.put("onlineStatus", status);
                    break;
                }
            }
            friendsListView.refresh();
        });
    }

    private void onNewFriendRequest(NewFriendRequestEvent event) {
        Platform.runLater(() -> {
            JsonObject data = event.getData();
            String senderNickname = data.get("senderNickname").getAsString();

            // 1. 弹出一个通知，告诉玩家收到了申请
            FXGL.getNotificationService().pushNotification("收到来自 " + senderNickname + " 的好友申请");

            // 2. 自动刷新“申请”标签页的内容
            loadFriendRequests();
        });
    }

    private void onFriendRequestAccepted(FriendRequestAcceptedEvent event) {
        Platform.runLater(() -> {
            JsonObject data = event.getData();
            String acceptorNickname = data.get("acceptorNickname").getAsString();

            // 1. 弹出一个通知
            FXGL.getNotificationService().pushNotification(acceptorNickname + " 已同意你的好友申请");

            // 2. 自动刷新好友列表，新好友会出现在里面
            loadFriendsList();
        });
    }

    @FXML
    private void handleCreateRoom() {
        // 可以在这里禁用按钮，防止重复点击
        // createRoomButton.setDisable(true);

        // 因为需要进行网络请求，所以必须在后台线程中执行
        new Thread(() -> {
            try {
                // 1. 调用 ApiClient 的 createRoom 方法
                String roomId = apiClient.createRoom();

                // 2. 房间创建成功后，后端会通过 WebSocket 推送一个 room_update 消息。
                //    我们的 RoomController 会监听到这个消息并用它来更新房间的初始状态。
                //    因此，客户端在这里只需要做一件事：切换到房间界面。
                Platform.runLater(() -> {
                    System.out.println("房间创建成功，ID: " + roomId + "，正在进入房间...");
                    UIManager.load("room-view.fxml");
                });

            } catch (Exception e) {
                // 如果创建失败（例如，玩家已在另一个房间），在UI上显示错误
                Platform.runLater(() -> {
                    FXGL.getDialogService().showMessageBox("创建房间失败: " + e.getMessage());
                    // createRoomButton.setDisable(false); // 恢复按钮
                });
                e.printStackTrace();
            }
        }).start();
    }

    @FXML
    private void handleJoinRoom() {
        String roomId = roomIdField.getText().trim(); // 获取输入并去除首尾空格

        if (roomId.isEmpty()) {
            FXGL.getDialogService().showMessageBox("请输入房间ID！");
            return;
        }

        // 在后台线程执行网络请求
        new Thread(() -> {
            try {
                // 调用我们之前在 ApiClient 中写好的 joinRoom 方法
                apiClient.joinRoom(roomId);

                // 加入成功后，后端会通过 WebSocket 推送 room_update 消息，
                // 我们的 RoomController 会监听到并更新UI。
                // 客户端只需要切换到房间界面即可。
                Platform.runLater(() -> {
                    System.out.println("成功加入房间: " + roomId);
                    UIManager.load("room-view.fxml");
                });

            } catch (Exception e) {
                // 如果加入失败（例如房间不存在、已满员），在UI上显示错误弹窗
                Platform.runLater(() -> {
                    FXGL.getDialogService().showMessageBox("加入房间失败: "+e.getMessage());
                });
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * 当收到房间邀请时，动态创建一个通知栏并显示在屏幕上 (最终调试版)
     */
    private void onRoomInvitation(RoomInvitationEvent event) {
        Platform.runLater(() -> {
            try {
                // 1. 创建UI控件 (这部分不变)
                HBox notificationPane = new HBox(20);
                notificationPane.setAlignment(Pos.CENTER);
                notificationPane.setPadding(new Insets(10));
                notificationPane.setStyle("-fx-background-color: rgba(0, 0, 0, 0.7); -fx-background-radius: 10;");

                Label infoLabel = new Label(event.getInviterNickname() + " 邀请你加入房间");
                infoLabel.setStyle("-fx-text-fill: white; -fx-font-size: 18px;");

                Button acceptButton = new Button("同意");
                Button rejectButton = new Button("拒绝");

                notificationPane.getChildren().addAll(infoLabel, acceptButton, rejectButton);

                // 2. 为按钮添加逻辑 (移除通知栏的逻辑也需要修改)
                acceptButton.setOnAction(e -> {
                    acceptButton.setDisable(true);
                    rejectButton.setDisable(true);
                    new Thread(() -> {
                        try {
                            apiClient.acceptInvite(event.getRoomId());
                            Platform.runLater(() -> UIManager.load("room-view.fxml"));
                        } catch (Exception ex) {
                            Platform.runLater(() -> FXGL.getDialogService().showMessageBox("加入失败: " + ex.getMessage()));
                        } finally {
                            // 从我们自己的 UI 容器中移除
                            Platform.runLater(() -> UIManager.getRoot().getChildren().remove(notificationPane));
                        }
                    }).start();
                });

                rejectButton.setOnAction(e -> {
                    acceptButton.setDisable(true);
                    rejectButton.setDisable(true);
                    new Thread(() -> {
                        try {
                            apiClient.rejectInvite(event.getInviterId());
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        } finally {
                            // 从我们自己的 UI 容器中移除
                            Platform.runLater(() -> UIManager.getRoot().getChildren().remove(notificationPane));
                        }
                    }).start();
                });

                // 3. 将通知栏添加到我们自己管理的、当前可见的 UI 根容器中
                Pane root = UIManager.getRoot();
                if (root != null) {
                    root.getChildren().add(notificationPane);

                    // 4. 因为我们的根容器是 StackPane，所以用 StackPane 的方式来定位
                    StackPane.setAlignment(notificationPane, Pos.BOTTOM_CENTER);
                    StackPane.setMargin(notificationPane, new Insets(0, 0, 50, 0)); // 距离底部50像素
                }

            } catch (Exception e) {
                System.err.println("创建或显示通知栏时发生严重错误！");
                e.printStackTrace();
            }
        });
    }

    @FXML
    private void handleChangeNickname() {
        // 使用 FXGL 的输入对话框，让用户输入新昵称
        FXGL.getDialogService().showInputBox("请输入新的昵称:", newNickname -> {
            if (newNickname.isEmpty()) {
                return; // 用户没输入，则不做任何事
            }

            // 在后台线程执行网络请求
            new Thread(() -> {
                try {
                    JsonObject updatedProfile = apiClient.updateNickname(newNickname);
                    String confirmedNickname = updatedProfile.get("nickname").getAsString();

                    // 成功后，在UI线程更新全局状态和界面显示
                    Platform.runLater(() -> {
                        GlobalState.nickname = confirmedNickname;
                        nicknameLabel.setText("昵称: " + confirmedNickname);
                        FXGL.getNotificationService().pushNotification("昵称已更新！");
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> FXGL.getDialogService().showMessageBox("修改失败: " + e.getMessage()));
                }
            }).start();
        });
    }

    @FXML
    private void handleShowHistory() {
        UIManager.load("history-view.fxml");
    }

    /**
     * 加载并显示当前用户的头像
     */
    private void loadAvatar() {
        String avatarUrl = GlobalState.avatarUrl;
        Image avatarImage;
        try {
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                // 如果用户有头像URL，则尝试从网络加载
                avatarImage = new Image(avatarUrl, true); // true 表示后台加载
            } else {
                // 否则，加载本地的默认头像
                avatarImage = FXGL.getAssetLoader().loadImage("default_avatar.png");
            }
        } catch (Exception e) {
            // 如果加载失败（比如URL无效），也加载默认头像
            System.err.println("加载头像失败: " + e.getMessage());
            avatarImage = FXGL.getAssetLoader().loadImage("default_avatar.png");
        }
        avatarImageView.setImage(avatarImage);
    }

    /**
     * 处理点击头像上传的事件
     */
    @FXML
    private void handleUploadAvatar() {
        // 1. 创建文件选择器
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择头像图片");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg")
        );

        // 2. 显示文件选择对话框
        // getPrimaryStage() 可以在 FXGL 的任何地方获取主窗口
        File selectedFile = fileChooser.showOpenDialog(FXGL.getPrimaryStage());

        if (selectedFile != null) {
            // 3. 在后台线程执行上传操作
            new Thread(() -> {
                try {
                    JsonObject updatedProfile = apiClient.uploadAvatar(selectedFile);

                    // 4. 上传成功后，在UI线程更新全局状态和界面显示
                    Platform.runLater(() -> {
                        JsonElement newAvatarUrlElement = updatedProfile.get("avatarUrl");
                        if (newAvatarUrlElement != null && !newAvatarUrlElement.isJsonNull()) {
                            GlobalState.avatarUrl = newAvatarUrlElement.getAsString();
                        }
                        // 重新加载头像以显示最新版本
                        loadAvatar();
                        FXGL.getNotificationService().pushNotification("头像更新成功！");
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> FXGL.getDialogService().showMessageBox("上传失败: " + e.getMessage()));
                }
            }).start();
        }
    }

    private void setupFriendsCellFactory() {
        friendsListView.setCellFactory(lv -> new ListCell<Map<String, Object>>() {
            // HBox 作为根容器
            private final HBox hbox = new HBox(10);
            // 用于显示头像
            private final ImageView avatarView = new ImageView();
            // 用于显示昵称和状态
            private final Label infoLabel = new Label();
            private final Region spacer = new Region();
            private final Button deleteButton = new Button("刪除");

            {
                // 初始化单元格布局
                avatarView.setFitHeight(40);
                avatarView.setFitWidth(40);
                HBox.setHgrow(spacer, Priority.ALWAYS);
                hbox.setAlignment(Pos.CENTER_LEFT);
                hbox.getChildren().addAll(avatarView, infoLabel, spacer, deleteButton);
            }

            @Override
            protected void updateItem(Map<String, Object> friend, boolean empty) {
                super.updateItem(friend, empty);
                if (empty || friend == null || friend.get("nickname") == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    setText(null);
                    infoLabel.setText(String.format("%s [%s]", friend.get("nickname"), friend.get("onlineStatus") == null ? "离线" : friend.get("onlineStatus")));
                    avatarView.setImage(UIManager.loadAvatar((String) friend.get("avatarUrl")));

                    // 為刪除按鈕設定點擊事件
                    deleteButton.setOnAction(event -> {
                        long friendId = ((Number) friend.get("userId")).longValue();
                        String nickname = (String) friend.get("nickname");

                        // 彈出確認對話方塊，防止誤刪
                        FXGL.getDialogService().showConfirmationBox("確定要刪除好友 " + nickname + " 吗？", (yes) -> {
                            if (yes) {
                                new Thread(() -> {
                                    try {
                                        apiClient.deleteFriend(friendId);
                                        // 成功後，在UI執行緒重新載入好友列表
                                        Platform.runLater(() -> loadFriendsList());
                                    } catch (Exception e) {
                                        Platform.runLater(() -> FXGL.getDialogService().showMessageBox("刪除失敗: " + e.getMessage()));
                                    }
                                }).start();
                            }
                        });
                    });

                    setGraphic(hbox);
                }
            }
        });
    }




}