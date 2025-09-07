package org.csu.pixelstrikejavafx.ui;

import com.almasb.fxgl.dsl.FXGL;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.text.Text;
import javafx.geometry.Pos;
import javafx.scene.layout.Region;


import org.csu.pixelstrikejavafx.events.FriendRequestAcceptedEvent;
import org.csu.pixelstrikejavafx.events.FriendStatusEvent;
import org.csu.pixelstrikejavafx.events.MatchSuccessEvent;
import org.csu.pixelstrikejavafx.events.NewFriendRequestEvent;
import org.csu.pixelstrikejavafx.http.ApiClient;
import org.csu.pixelstrikejavafx.network.NetworkManager;
import org.csu.pixelstrikejavafx.state.GlobalState;

import java.net.URL;
import java.util.Map;
import java.util.List;
import java.util.ResourceBundle;


/**
 * LobbyController 负责处理 lobby-view.fxml 的所有用户交互。
 * 例如：显示好友列表、处理匹配按钮点击、监听服务器事件等。
 */
public class LobbyController implements Initializable {

    @FXML
    private ListView<String> friendsListView; // 假设你的 FXML 中有一个 ListView 来显示好友

    @FXML
    private Button startMatchButton;

    @FXML
    private Label matchStatusLabel;

    @FXML
    private Label nicknameLabel;

    @FXML private TextField searchUserField;
    @FXML private ListView<Map<String, Object>> searchResultListView;
    @FXML private ListView<Map<String, Object>> requestsListView;

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
                System.out.println("UI received: Match Success!");
                matchStatusLabel.setText("匹配成功！准备进入游戏...");

                // 断开当前的全局 WebSocket
                NetworkManager.getInstance().disconnect();

                // 准备连接到游戏服务器...
                String gameServerUrl = event.getServerAddress();
                System.out.println("Game Server URL: " + gameServerUrl);

                // 在这里，你需要编写切换到游戏场景的逻辑
                // 例如：FXGL.getGameController().startNewGame();
                // 并且在游戏场景的 initGame() 中，使用 gameServerUrl 建立新的游戏内 WebSocket 连接
            });
        });

        // 初始加载好友列表 (可以在这里或用一个刷新按钮来触发)
        loadFriendsList();
        loadFriendRequests();
        setupSearchResultCellFactory();
        setupRequestsCellFactory();
        setupEventHandlers();

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
        matchStatusLabel.setText("正在寻找对局...");
        startMatchButton.setDisable(true); // 防止重复点击

        // 后台线程调用匹配 API
        new Thread(() -> {
            try {
                // 假设你的 ApiClient 有一个 startMatchmaking 方法
                // apiClient.startMatchmaking();
                Platform.runLater(() -> matchStatusLabel.setText("已进入匹配队列，等待服务器通知..."));
            } catch (Exception e) {
                Platform.runLater(() -> {
                    matchStatusLabel.setText("开始匹配失败: " + e.getMessage());
                    startMatchButton.setDisable(false);
                });
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
        friendsListView.getItems().clear(); // 先清空旧数据
        friendsListView.getItems().add("正在加载好友..."); // 提示用户

        new Thread(() -> {
            try {
                // 调用API获取好友数据
                List<Map<String, Object>> friends = apiClient.getFriends();

                // 在UI线程更新 ListView
                Platform.runLater(() -> {
                    friendsListView.getItems().clear(); // 清空“加载中”提示
                    if (friends.isEmpty()) {
                        friendsListView.getItems().add("好友列表为空");
                    } else {
                        for (Map<String, Object> friend : friends) {
                            String nickname = (String) friend.get("nickname");
                            String status = (String) friend.get("onlineStatus");

                            // API文档中 null 代表离线，我们做一下转换
                            if (status == null) {
                                status = "离线";
                            }

                            friendsListView.getItems().add(String.format("%s [%s]", nickname, status));
                        }
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    friendsListView.getItems().clear();
                    friendsListView.getItems().add("加载好友失败: " + e.getMessage());
                });
                e.printStackTrace();
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
            private HBox hbox = new HBox(10);
            private Text userInfo = new Text();
            private Button addButton = new Button("添加");

            {
                // 布局单元格内的控件
                HBox.setHgrow(userInfo, Priority.ALWAYS);
                hbox.setAlignment(Pos.CENTER_LEFT);
                hbox.getChildren().addAll(userInfo, addButton);
            }

            @Override
            protected void updateItem(Map<String, Object> user, boolean empty) {
                super.updateItem(user, empty);
                if (empty || user == null) {
                    setText(null);
                    setGraphic(null);
                } else if (user.containsKey("loading")) {
                    setText("正在搜索...");
                    setGraphic(null);
                }
                else {
                    // 从 Map 中获取数据并显示
                    String nickname = (String) user.get("nickname");
                    String status = (String) user.get("onlineStatus");
                    status = (status == null) ? "离线" : status;
                    userInfo.setText(String.format("%s [%s]", nickname, status));

                    // 为“添加”按钮设置点击事件
                    addButton.setOnAction(event -> {
                        addButton.setDisable(true);
                        addButton.setText("已申请");

                        // 从 user Map 中获取 userId
                        // 注意：JSON数字可能被解析为Double，需要安全转换
                        long userId = ((Number) user.get("userId")).longValue();

                        new Thread(() -> {
                            try {
                                apiClient.sendFriendRequest(userId);
                            } catch (Exception e) {
                                Platform.runLater(() -> {
                                    addButton.setDisable(false);
                                    addButton.setText("添加");
                                    // 在这里可以弹出一个提示框显示错误信息，e.getMessage()
                                    FXGL.getDialogService().showMessageBox(e.getMessage());
                                });
                                e.printStackTrace();
                            }
                        }).start();
                    });

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

            // 2. 使用 Label 显示用户信息，它比 Text 更适合布局
            private final Label requestInfoLabel = new Label();

            // 3. 创建一个看不见的、可伸缩的“弹簧”
            private final Region spacer = new Region();

            // 4. 创建按钮
            private final Button acceptButton = new Button("同意");
            private final Button rejectButton = new Button("拒绝"); // 顺便加上拒绝按钮

            {
                // 5. 设置“弹簧”：让它占据所有可用的水平空间
                HBox.setHgrow(spacer, Priority.ALWAYS);

                // 设置按钮的最小宽度，防止被过度压缩
                acceptButton.setMinWidth(60);
                rejectButton.setMinWidth(60);

                // 6. 将所有控件按顺序放入 HBox
                hbox.setAlignment(Pos.CENTER_LEFT);
                hbox.getChildren().addAll(requestInfoLabel, spacer, acceptButton, rejectButton);
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
    }

    private void onFriendStatusUpdate(FriendStatusEvent event) {
        // 确保所有UI操作都在JavaFX应用线程中执行
        Platform.runLater(() -> {
            JsonObject data = event.getData();
            // 从事件中解析出是哪个好友，以及他的新状态
            String nickname = data.get("nickname").getAsString();
            String status = data.get("status").getAsString();

            System.out.println(String.format("UI Handling: Friend '%s' status changed to '%s'", nickname, status));

            // 1. 遍历当前好友列表 (ListView) 中的每一项
            for (int i = 0; i < friendsListView.getItems().size(); i++) {
                String itemText = friendsListView.getItems().get(i);

                // 2. 检查这一行是不是我们要找的那个好友
                // 我们假设列表项的格式是 "昵称 [状态]"
                if (itemText.startsWith(nickname + " [") || itemText.equals(nickname)) {

                    // 3. 创建新的显示文本
                    String newStatusText = String.format("%s [%s]", nickname, status);

                    // 4. 使用 set 方法更新 ListView 中特定行的内容
                    friendsListView.getItems().set(i, newStatusText);

                    // 5. 找到并更新后，就可以退出循环了
                    break;
                }
            }
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


}