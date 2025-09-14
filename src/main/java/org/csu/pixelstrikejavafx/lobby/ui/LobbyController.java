package org.csu.pixelstrikejavafx.lobby.ui;

import com.almasb.fxgl.dsl.FXGL;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.geometry.Pos;


import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import org.csu.pixelstrikejavafx.core.MatchResultsModel;
import org.csu.pixelstrikejavafx.core.MatchSuccessEvent;
import org.csu.pixelstrikejavafx.lobby.events.*;
import org.csu.pixelstrikejavafx.lobby.network.ApiClient;
import org.csu.pixelstrikejavafx.lobby.network.NetworkManager;
import org.csu.pixelstrikejavafx.core.GlobalState;
import org.csu.pixelstrikejavafx.lobby.ui.dialog.DialogManager;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.text.Text;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.layout.BorderPane;
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
    private Button cancelMatchButton;

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
    @FXML private ImageView backgroundImageView;
    @FXML private VBox friendsPanel;
    @FXML private StackPane toggleFriendsButton;
    @FXML private Button changeNicknameButton;
    private final Set<Long> friendIds = new HashSet<>();

    private final ApiClient apiClient = new ApiClient();

    /**
     * FXML 界面加载完成时，该方法会自动被调用。
     * 我们在这里注册所有需要的事件监听器。
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {

        URL cssUrl = getClass().getResource("/assets/css/lobby-style.css");
        System.out.println("DEBUG: 尝试查找 lobby-style.css, 找到的路径是 -> " + cssUrl);
        try {
            Image bg = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/assets/textures/background.png")));
            backgroundImageView.setImage(bg);
        } catch (Exception e) {
            System.err.println("大厅背景图加载失败: " + e.getMessage());
        }

        System.out.println("LobbyController initialized. Setting up event handlers.");

        // 【新增】重置UI到初始状态
        if (startMatchButton != null) {
            startMatchButton.setDisable(false);
        }
        if (cancelMatchButton != null) {
            cancelMatchButton.setDisable(true); // 初始时禁用
        }
        if (matchStatusLabel != null) {
            matchStatusLabel.setText(""); // 清空状态文本
        }
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

                MatchResultsModel.reset();
                // 立即切换到战绩页面，它会一直显示“加载中”，直到游戏结束
                UIManager.load("results-view.fxml");
                System.out.println("Switched to results view in loading state.");

                //UIManager.load("lobby-view.fxml");
                // 【您的方案】在进入游戏前，立即恢复按钮状态，为返回做准备
                if (startMatchButton != null) {
                    startMatchButton.setDisable(false);
                }
                if (cancelMatchButton != null) {
                    cancelMatchButton.setDisable(true);
                }
                // 2. 断开当前的全局(大厅) WebSocket
                //NetworkManager.getInstance().disconnect();
                //System.out.println("Lobby WebSocket disconnected.");

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

        try {
            FontAwesomeIconView icon = new FontAwesomeIconView(FontAwesomeIcon.USER);
            icon.setSize("22px");
            icon.getStyleClass().add("icon-style");

            // 修改后的代码:
            toggleFriendsButton.getChildren().add(icon); // 将图标作为子元素添加到 StackPane 中

        } catch (Exception e) {
            System.err.println("FontAwesomeFX icon could not be loaded. Check dependencies.");
        }

        try {
            FontAwesomeIconView icon = new FontAwesomeIconView(FontAwesomeIcon.PENCIL); // 创建一个铅笔图标
            icon.setSize("16px"); // 设置一个合适的大小
            icon.getStyleClass().add("icon-style"); // 复用之前定义好的白色图标样式

            changeNicknameButton.setGraphic(icon); // 将图标设置为按钮的图形内容
            changeNicknameButton.setText(""); // 确保按钮没有文字

        } catch (Exception e) {
            System.err.println("Pencil icon could not be loaded. Check dependencies.");
            // 如果图标加载失败，按钮会显示文字作为后备
            changeNicknameButton.setText("修改");
        }
    }

    // 在 LobbyController.java 的任何地方添加这个新方法
    @FXML
    private void logButtonPositionOnPressed() {
        if (toggleFriendsButton == null) return;

        // 使用 Platform.runLater 确保我们在获取当前帧的最终位置
        Platform.runLater(() -> {
            // 获取按钮左上角在整个屏幕上的坐标
            javafx.geometry.Point2D screenCoords = toggleFriendsButton.localToScreen(0, 0);
            System.out.println(String.format("--- 点击时 --- 按钮在屏幕上的坐标: X=%.2f, Y=%.2f", screenCoords.getX(), screenCoords.getY()));
        });
    }
    /**
     * 处理点击“取消匹配”按钮的事件
     */
    @FXML
    private void handleCancelMatch() {
        if (matchStatusLabel != null) {
            matchStatusLabel.setText("正在取消匹配...");
        }
        cancelMatchButton.setDisable(true); // 防止重复点击

        new Thread(() -> {
            try {
                apiClient.cancelMatchmaking();
                Platform.runLater(() -> {
                    if (matchStatusLabel != null) {
                        matchStatusLabel.setText("已取消匹配");
                    }
                    startMatchButton.setDisable(false); // 恢复“开始匹配”按钮
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (matchStatusLabel != null) {
                        matchStatusLabel.setText("取消失败: " + e.getMessage());
                    }
                    // 即便取消失败，也最好让用户能重点，所以这里依然恢复按钮
                    startMatchButton.setDisable(false);
                    cancelMatchButton.setDisable(true);
                });
                e.printStackTrace();
            }
        }).start();
    }
    /**
     * 处理点击“开始匹配”按钮的事件
     */

    /*@FXML
    private void handleStartMatch() {
        // 匹配流程: 选地图 -> 选角色 -> 开始匹配
        new Thread(() -> {
            try {
                // 1. 获取地图列表
                List<Map<String, Object>> maps = apiClient.getMaps();
                // 2. 弹出地图选择框 (在UI线程)
                Platform.runLater(() -> showSelectionDialog("选择地图", maps, selectedMap -> {
                    if (selectedMap == null) return; // 用户取消
                    long mapId = ((Number) selectedMap.get("id")).longValue();

                    // 3. 地图选好后，获取角色列表
                    new Thread(() -> {
                        try {
                            List<Map<String, Object>> characters = apiClient.getCharacters();
                            // 4. 弹出角色选择框 (在UI线程)
                            Platform.runLater(() -> showSelectionDialog("选择角色", characters, selectedCharacter -> {
                                if (selectedCharacter == null) return; // 用户取消
                                long characterId = ((Number) selectedCharacter.get("id")).longValue();

                                // 5. 一切就绪，调用新的开始匹配接口
                                startMatchmakingWithSelection(mapId, characterId);
                            }));
                        } catch (Exception e) {
                            Platform.runLater(() -> FXGL.getDialogService().showMessageBox("获取角色列表失败: " + e.getMessage()));
                        }
                    }).start();
                }));
            } catch (Exception e) {
                Platform.runLater(() -> FXGL.getDialogService().showMessageBox("获取地图列表失败: " + e.getMessage()));
            }
        }).start();
    }
    @FXML
    private void handleCreateRoom() {
        // 开房流程: 选地图 -> 创建房间
        new Thread(() -> {
            try {
                List<Map<String, Object>> maps = apiClient.getMaps();
                Platform.runLater(() -> showSelectionDialog("选择地图创建房间", maps, selectedMap -> {
                    if (selectedMap == null) return; // 用户取消
                    long mapId = ((Number) selectedMap.get("id")).longValue();

                    new Thread(() -> {
                        try {
                            // 调用带 mapId 的 createRoom 接口
                            apiClient.createRoom(String.valueOf(mapId));
                            Platform.runLater(() -> {
                                System.out.println("房间创建成功，正在进入...");
                                UIManager.load("room-view.fxml");
                            });
                        } catch (Exception e) {
                            Platform.runLater(() -> FXGL.getDialogService().showMessageBox("创建房间失败: " + e.getMessage()));
                        }
                    }).start();
                }));
            } catch (Exception e) {
                Platform.runLater(() -> FXGL.getDialogService().showMessageBox("获取地图列表失败: " + e.getMessage()));
            }
        }).start();
    }
*/

    @FXML
    private void handleStartMatch() {
        // 匹配流程: 选地图 -> 选角色 -> 开始匹配
        new Thread(() -> {
            try {
                List<Map<String, Object>> maps = apiClient.getMaps();
                Platform.runLater(() -> showMapSelectionDialog("选择地图", maps, selectedMap -> {
                    if (selectedMap == null) return; // 用户取消
                    long mapId = ((Number) selectedMap.get("id")).longValue();

                    // 后续的角色选择流程不变
                    new Thread(() -> {
                        try {
                            List<Map<String, Object>> characters = apiClient.getCharacters();
                            Platform.runLater(() -> showSelectionDialog("选择角色", characters, selectedCharacter -> {
                                if (selectedCharacter == null) return;
                                long characterId = ((Number) selectedCharacter.get("id")).longValue();
                                startMatchmakingWithSelection(mapId, characterId);
                            }));
                        } catch (Exception e) {
                            Platform.runLater(() -> FXGL.getDialogService().showMessageBox("获取角色列表失败: " + e.getMessage()));
                        }
                    }).start();
                }));
            } catch (Exception e) {
                Platform.runLater(() -> FXGL.getDialogService().showMessageBox("获取地图列表失败: " + e.getMessage()));
            }
        }).start();
    }

    @FXML
    private void handleCreateRoom() {
        // 开房流程: 选地图 -> 创建房间
        new Thread(() -> {
            try {
                List<Map<String, Object>> maps = apiClient.getMaps();
                Platform.runLater(() -> showMapSelectionDialog("选择地图创建房间", maps, selectedMap -> {
                    if (selectedMap == null) return; // 用户取消
                    long mapId = ((Number) selectedMap.get("id")).longValue();

                    new Thread(() -> {
                        try {
                            apiClient.createRoom(String.valueOf(mapId));
                            Platform.runLater(() -> {
                                System.out.println("房间创建成功，正在进入...");
                                UIManager.load("room-view.fxml");
                            });
                        } catch (Exception e) {
                            Platform.runLater(() -> FXGL.getDialogService().showMessageBox("创建房间失败: " + e.getMessage()));
                        }
                    }).start();
                }));
            } catch (Exception e) {
                Platform.runLater(() -> FXGL.getDialogService().showMessageBox("获取地图列表失败: " + e.getMessage()));
            }
        }).start();
    }

    /**
     * 新增：显示一个图片轮播式的地图选择对话框
     */
    private void showMapSelectionDialog(String title, List<Map<String, Object>> maps, java.util.function.Consumer<Map<String, Object>> onItemSelected) {
        if (maps == null || maps.isEmpty()) {
            FXGL.getDialogService().showMessageBox("没有可用的地图！");
            return;
        }

        Dialog<Map<String, Object>> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // --- UI 组件 ---
        BorderPane root = new BorderPane();
        root.setPrefSize(400, 300);

        ImageView mapView = new ImageView();
        mapView.setFitHeight(200);
        mapView.setPreserveRatio(true);

        Text mapName = new Text();
        mapName.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        Text mapDescription = new Text();
        VBox mapInfoBox = new VBox(5, mapName, mapDescription);
        mapInfoBox.setAlignment(Pos.CENTER);

        Button leftButton = new Button("<");
        Button rightButton = new Button(">");

        root.setCenter(mapView);
        root.setBottom(mapInfoBox);
        root.setLeft(leftButton);
        root.setRight(rightButton);
        BorderPane.setAlignment(leftButton, Pos.CENTER_LEFT);
        BorderPane.setAlignment(rightButton, Pos.CENTER_RIGHT);
        BorderPane.setMargin(mapInfoBox, new Insets(10));

        // --- 逻辑 ---
        SimpleObjectProperty<Map<String, Object>> currentMap = new SimpleObjectProperty<>(maps.get(0));
        final int[] currentIndex = {0};

        // 更新显示内容的方法
        Runnable updateDisplay = () -> {
            Map<String, Object> map = maps.get(currentIndex[0]);
            currentMap.set(map);
            mapName.setText((String) map.get("name"));
            mapDescription.setText((String) map.get("description"));

            Object urlObj = map.get("thumbnailUrl");
            Image image;
            if (urlObj != null && !urlObj.toString().isEmpty()) {
                image = new Image(urlObj.toString(), true); // true表示后台加载
            } else {
                image = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/assets/textures/background.png"))); // 默认图
            }
            mapView.setImage(image);
        };

        // 按钮事件
        leftButton.setOnAction(e -> {
            currentIndex[0] = (currentIndex[0] - 1 + maps.size()) % maps.size();
            updateDisplay.run();
        });
        rightButton.setOnAction(e -> {
            currentIndex[0] = (currentIndex[0] + 1) % maps.size();
            updateDisplay.run();
        });

        // 初始显示
        updateDisplay.run();

        dialog.getDialogPane().setContent(root);
        dialog.setResultConverter(dialogButton -> (dialogButton == ButtonType.OK) ? currentMap.get() : null);
        dialog.showAndWait().ifPresent(onItemSelected);
    }


    // 新增一个私有方法来处理最终的API调用
    private void startMatchmakingWithSelection(long mapId, long characterId) {
        if (matchStatusLabel != null) {
            matchStatusLabel.setText("正在发送匹配请求...");
        }
        startMatchButton.setDisable(true);
        cancelMatchButton.setDisable(false);

        new Thread(() -> {
            try {
                // 调用带参数的 startMatchmaking 方法
                apiClient.startMatchmaking(String.valueOf(mapId), String.valueOf(characterId));
                Platform.runLater(() -> {
                    if (matchStatusLabel != null) {
                        matchStatusLabel.setText("已进入匹配队列，等待服务器通知...");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (matchStatusLabel != null) {
                        matchStatusLabel.setText("开始匹配失败: " + e.getMessage());
                    }
                    startMatchButton.setDisable(false);
                    cancelMatchButton.setDisable(true);
                });
                e.printStackTrace();
            }
        }).start();
    }



    /**
     * 新增：一个通用的选择对话框
     * @param title 对话框标题
     * @param items 要显示的项目列表 (每个Map应包含 "id" 和 "name")
     * @param onItemSelected 用户选择后的回调函数
     */
    private void showSelectionDialog(String title, List<Map<String, Object>> items, java.util.function.Consumer<Map<String, Object>> onItemSelected) {
        Dialog<Map<String, Object>> dialog = new Dialog<>();
        dialog.setTitle(title);

        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ListView<Map<String, Object>> listView = new ListView<>();
        listView.getItems().setAll(items);
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Map<String, Object> item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : (String) item.get("name"));
            }
        });
        // --- 新增逻辑：在这里设置默认选项 ---
        if ("选择角色".equals(title) && !items.isEmpty()) {
            // 遍历列表，找到ID为1的角色
            for (Map<String, Object> character : items) {
                // GSON解析JSON数字时默认为Double类型，所以用 1.0 比较
                if (character.get("id") instanceof Number && ((Number) character.get("id")).doubleValue() == 1.0) {
                    // 找到后，设置为默认选中项
                    listView.getSelectionModel().select(character);
                    break; // 找到后即可退出循环
                }
            }
            // 如果没找到ID为1的，默认会选中第一项
            if (listView.getSelectionModel().isEmpty()){
                listView.getSelectionModel().selectFirst();
            }
        }
        VBox content = new VBox(10, new Label("请选择一项:"), listView);
        content.setPadding(new Insets(20));
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                // 如果用户没有手动选择，确保返回默认选中的项
                if (listView.getSelectionModel().getSelectedItem() == null) {
                    // 如果是角色选择，且没有手动选择，可以强制返回ID为1的角色
                    if ("选择角色".equals(title)) {
                        for (Map<String, Object> character : items) {
                            if (character.get("id") instanceof Number && ((Number) character.get("id")).doubleValue() == 1.0) {
                                return character;
                            }
                        }
                    }
                }
                return listView.getSelectionModel().getSelectedItem();
            }
            return null;
        });

        dialog.showAndWait().ifPresent(onItemSelected);
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
        new Thread(() -> {
            try {
                apiClient.logout();
                Platform.runLater(() -> {
                    NetworkManager.getInstance().disconnect();
                    GlobalState.authToken = null;
                    UIManager.load("login-view.fxml");
                });
            } catch (Exception e) {
                Platform.runLater(() -> DialogManager.showMessage("登出失败", e.getMessage()));
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

        new Thread(() -> {
            try {
                List<Map<String, Object>> users = apiClient.searchUsers(nickname);
                Platform.runLater(() -> {
                    // 这里不再需要清空，因为在发起请求前已经清空了
                    if (!users.isEmpty()) {
                        searchResultListView.getItems().addAll(users);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    // 确保即使出错也清空列表
                    searchResultListView.getItems().clear();
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
                actionButton.getStyleClass().add("add-friend-button");
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


        new Thread(() -> {
            try {
                List<Map<String, Object>> requests = apiClient.getFriendRequests();
                Platform.runLater(() -> {
                    // 这里不再需要清空
                    if (!requests.isEmpty()) {
                        requestsListView.getItems().addAll(requests);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    // 确保即使出错也清空列表
                    requestsListView.getItems().clear();
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
                acceptButton.getStyleClass().add("accept-request-button");
                rejectButton.getStyleClass().add("reject-request-button");
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

       /*// 监听匹配成功
        FXGL.getEventBus().addEventHandler(MatchSuccessEvent.ANY, this::onMatchSuccess);*/

        // 监听“收到新好友申请”事件
        FXGL.getEventBus().addEventHandler(NewFriendRequestEvent.ANY, this::onNewFriendRequest);
        // 【新增】监听好友个人资料更新事件
        FXGL.getEventBus().addEventHandler(FriendProfileUpdateEvent.ANY, this::onFriendProfileUpdate);
        // 监听“好友申请被接受”事件
        FXGL.getEventBus().addEventHandler(FriendRequestAcceptedEvent.ANY, this::onFriendRequestAccepted);
        // 【新增】监听被好友删除的事件
        FXGL.getEventBus().addEventHandler(FriendRemovedEvent.ANY, this::onFriendRemoved);
        FXGL.getEventBus().addEventHandler(RoomInvitationEvent.ANY, this::onRoomInvitation);
    }

    /**
     * 当被好友删除时，此方法被调用
     */
    private void onFriendRemoved(FriendRemovedEvent event) {
        Platform.runLater(() -> {
            // 弹出一个通知告诉用户
            FXGL.getNotificationService().pushNotification("您已被对方从好友列表中移除。");

            // 重新加载好友列表，被删除的好友会从列表中消失
            loadFriendsList();
        });
    }

    /**
     * 当收到好友昵称或头像更新时，此方法被调用
     */
    private void onFriendProfileUpdate(FriendProfileUpdateEvent event) {
        Platform.runLater(() -> {
            JsonObject data = event.getData();
            long userId = data.get("userId").getAsLong();
            String newNickname = data.get("newNickname").getAsString();

            // 安全地获取 newAvatarUrl，可能为 null
            JsonElement avatarElement = data.get("newAvatarUrl");
            String newAvatarUrl = (avatarElement != null && !avatarElement.isJsonNull()) ? avatarElement.getAsString() : null;

            // 遍历当前好友列表
            for (Map<String, Object> friend : friendsListView.getItems()) {
                long currentFriendId = ((Number) friend.get("userId")).longValue();
                if (currentFriendId == userId) {
                    // 找到对应的朋友，更新他的信息
                    friend.put("nickname", newNickname);
                    friend.put("avatarUrl", newAvatarUrl);
                    break; // 找到后即可退出循环
                }
            }

            // 强制刷新ListView，让CellFactory重新渲染更新后的数据
            friendsListView.refresh();
        });
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
            String senderNickname = event.getData().get("senderNickname").getAsString();
            DialogManager.showNotification("收到来自 " + senderNickname + " 的好友申请");
            loadFriendRequests();
        });
    }


    private void onFriendRequestAccepted(FriendRequestAcceptedEvent event) {
        Platform.runLater(() -> {
            String acceptorNickname = event.getData().get("acceptorNickname").getAsString();
            DialogManager.showNotification(acceptorNickname + " 已同意你的好友申请");
            loadFriendsList();
        });
    }



    @FXML
    private void handleJoinRoom() {
        String roomId = roomIdField.getText().trim();
        if (roomId.isEmpty()) {
            DialogManager.showMessage("提示", "请输入房间ID！");
            return;
        }
        new Thread(() -> {
            try {
                apiClient.joinRoom(roomId);
                Platform.runLater(() -> {
                    System.out.println("成功加入房间: " + roomId);

                    // ↓↓↓ 修改点：设置下一页的消息 ↓↓↓
                    UIManager.showMessageOnNextScreen("成功加入房间！");

                    UIManager.load("room-view.fxml");
                });
            } catch (Exception e) {
                Platform.runLater(() -> DialogManager.showMessage("加入房间失败", e.getMessage()));
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * 当收到房间邀请时，动态创建一个通知栏并显示在屏幕上 (最终调试版)
     */
    private void onRoomInvitation(RoomInvitationEvent event) {
        Platform.runLater(() -> {
            // 定义“同意”按钮的逻辑
            Runnable acceptAction = () -> {
                new Thread(() -> {
                    try {
                        apiClient.acceptInvite(event.getRoomId());
                        Platform.runLater(() -> {
                            UIManager.showMessageOnNextScreen("成功加入房间！");
                            UIManager.load("room-view.fxml");
                        });
                    } catch (Exception ex) {
                        Platform.runLater(() -> DialogManager.showMessage("加入失败", ex.getMessage()));
                    }
                }).start();
            };

            // 定义“拒绝”按钮的逻辑
            Runnable rejectAction = () -> {
                new Thread(() -> {
                    try {
                        apiClient.rejectInvite(event.getInviterId());
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }).start();
            };

            // 用一行代码调用我们强大的新通知栏
            DialogManager.showActionableNotification(
                    event.getInviterNickname() + " 邀请你加入房间",
                    "同意", acceptAction,
                    "拒绝", rejectAction
            );
        });
    }

    @FXML
    private void handleChangeNickname() {
        DialogManager.showCancellableInput("修改昵称", "请输入新的昵称 (2-7位):", newNickname -> {
            if (newNickname == null) return; // 用户点击了取消
            String trimmedNickname = newNickname.trim();
            if (trimmedNickname.length() < 2 || trimmedNickname.length() > 7) {
                DialogManager.showMessage("输入无效", "昵称长度必须在 2-7 位之间！");
                return;
            }
            new Thread(() -> {
                try {
                    JsonObject updatedProfile = apiClient.updateNickname(trimmedNickname);
                    String confirmedNickname = updatedProfile.get("nickname").getAsString();
                    Platform.runLater(() -> {
                        GlobalState.nickname = confirmedNickname;
                        nicknameLabel.setText("昵称: " + confirmedNickname);
                        DialogManager.showNotification("昵称已更新！");
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> DialogManager.showMessage("修改失败", e.getMessage()));
                }
            }).start();
        }, null); // onCancel回调为null，表示取消时只关闭对话框
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

    @FXML
    private void handleToggleFriendsList() {
        boolean isVisible = friendsPanel.isVisible();
        friendsPanel.setVisible(!isVisible);
        friendsPanel.setManaged(!isVisible); // setManaged(false)会使布局忽略该节点
    }
    /**
     * 处理点击头像上传的事件
     */
    @FXML
    private void handleUploadAvatar() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择头像图片");
        fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"));
        File selectedFile = fileChooser.showOpenDialog(FXGL.getPrimaryStage());

        if (selectedFile != null) {
            new Thread(() -> {
                try {
                    JsonObject updatedProfile = apiClient.uploadAvatar(selectedFile);
                    Platform.runLater(() -> {
                        JsonElement newAvatarUrlElement = updatedProfile.get("avatarUrl");
                        if (newAvatarUrlElement != null && !newAvatarUrlElement.isJsonNull()) {
                            GlobalState.avatarUrl = newAvatarUrlElement.getAsString();
                        }
                        loadAvatar();
                        DialogManager.showNotification("头像更新成功！");
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> DialogManager.showMessage("上传失败", e.getMessage()));
                }
            }).start();
        }
    }

    private void showCustomConfirm(String message, Runnable onConfirm) {
        try {
            // 1. 加载 FXML 模板
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/custom-confirm-pane.fxml"));
            VBox confirmPane = loader.load();

            // 2. 将面板添加到大厅的根布局中
            Pane rootPane = (Pane) nicknameLabel.getScene().getRoot();
            rootPane.getChildren().add(confirmPane);
            StackPane.setAlignment(confirmPane, Pos.CENTER); // 居中显示

            // 3. 获取面板中的控件并设置内容
            Label messageLabel = (Label) loader.getNamespace().get("messageLabel");
            Button confirmButton = (Button) loader.getNamespace().get("confirmButton");
            Button cancelButton = (Button) loader.getNamespace().get("cancelButton");

            messageLabel.setText(message);

            // 4. 为按钮绑定事件
            confirmButton.setOnAction(e -> {
                // 先关闭面板
                rootPane.getChildren().remove(confirmPane);
                // 然后执行传入的操作
                if (onConfirm != null) {
                    onConfirm.run();
                }
            });

            cancelButton.setOnAction(e -> {
                // 直接关闭面板
                rootPane.getChildren().remove(confirmPane);
            });

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setupFriendsCellFactory() {
        friendsListView.setCellFactory(lv -> new ListCell<Map<String, Object>>() {
            private final HBox hbox = new HBox(15);
            private final Circle statusIndicator = new Circle(6);
            private final ImageView avatarView = new ImageView();
            private final VBox userInfoVBox = new VBox(2);
            private final Label nicknameLabel = new Label();
            private final Label statusLabel = new Label();
            private final Region spacer = new Region();
            private final Button deleteButton = new Button("删除");

            {
                avatarView.setFitHeight(40);
                avatarView.setFitWidth(40);
                HBox.setHgrow(spacer, Priority.ALWAYS);
                userInfoVBox.getChildren().addAll(nicknameLabel, statusLabel);
                hbox.setAlignment(Pos.CENTER_LEFT);
                hbox.getChildren().addAll(statusIndicator, avatarView, userInfoVBox, spacer, deleteButton);
                hbox.getStyleClass().add("friend-cell-container");
                statusIndicator.getStyleClass().add("status-indicator");
                nicknameLabel.getStyleClass().add("friend-nickname-label");
                statusLabel.getStyleClass().add("friend-status-label");
                deleteButton.getStyleClass().add("delete-friend-button");
            }

            @Override
            protected void updateItem(Map<String, Object> friend, boolean empty) {
                super.updateItem(friend, empty);
                if (empty || friend == null || friend.get("nickname") == null) {
                    setGraphic(null);
                } else {
                    nicknameLabel.setText((String) friend.get("nickname"));

                    // 根据新API文档更新状态逻辑
                    String statusFromServer = (String) friend.get("onlineStatus");
                    String displayStatus;

                    // 先移除所有可能的状态样式，确保每次都是干净的
                    statusIndicator.getStyleClass().removeAll("status-online", "status-offline", "status-ingame", "status-matching", "status-in-room");

                    // API文档指出，通过HTTP接口获取时，离线状态为 null
                    if (statusFromServer == null) {
                        statusFromServer = "OFFLINE";
                    }

                    switch (statusFromServer.toUpperCase()) { // 使用 toUpperCase() 来统一处理大小写
                        case "ONLINE":
                            statusIndicator.getStyleClass().add("status-online");
                            displayStatus = "在线";
                            break;
                        case "IN_GAME":
                            statusIndicator.getStyleClass().add("status-ingame");
                            displayStatus = "游戏中";
                            break;
                        case "MATCHING": // 新增状态
                            statusIndicator.getStyleClass().add("status-matching");
                            displayStatus = "匹配中";
                            break;
                        case "IN_ROOM": // 新增状态
                            statusIndicator.getStyleClass().add("status-in-room");
                            displayStatus = "房间中";
                            break;
                        default: // OFFLINE 或其他未知状态
                            statusIndicator.getStyleClass().add("status-offline");
                            displayStatus = "离线";
                            break;
                    }

                    statusLabel.setText(displayStatus);
                    avatarView.setImage(UIManager.loadAvatar((String) friend.get("avatarUrl")));

                    deleteButton.setOnAction(event -> {
                        long friendId = ((Number) friend.get("userId")).longValue();
                        String nickname = (String) friend.get("nickname");
                        // 使用新的DialogManager来显示确认框
                        DialogManager.showConfirmation("确认删除", "确定要删除好友 " + nickname + " 吗？", () -> {
                            new Thread(() -> {
                                try {
                                    apiClient.deleteFriend(friendId);
                                    Platform.runLater(() -> loadFriendsList());
                                } catch (Exception e) {
                                    Platform.runLater(() -> DialogManager.showMessage("删除失败", e.getMessage()));
                                }
                            }).start();
                        });
                    });

                    setGraphic(hbox);
                }
            }
        });
    }




}