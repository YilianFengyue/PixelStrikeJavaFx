package org.csu.pixelstrikejavafx.lobby.ui;

import com.almasb.fxgl.dsl.FXGL;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import org.csu.pixelstrikejavafx.lobby.network.ApiClient;
import org.csu.pixelstrikejavafx.core.GlobalState;
import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class HistoryController implements Initializable {

    @FXML private ListView<Map<String, Object>> historyListView;
    private final ApiClient apiClient = new ApiClient();
    private static final DateTimeFormatter INPUT_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter OUTPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /*@Override
    public void initialize(URL location, ResourceBundle resources) {
        // 设置单元格的显示格式
        historyListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Map<String, Object> match, boolean empty) {
                super.updateItem(match, empty);
                if (empty || match == null) {
                    setText(null);
                } else {
                    // 从 map 中安全地获取 K/D 数据，如果不存在则默认为 0
                    Object killsObj = match.get("myKills");
                    Object deathsObj = match.get("myDeaths");
                    int kills = (killsObj instanceof Number) ? ((Number) killsObj).intValue() : 0;
                    int deaths = (deathsObj instanceof Number) ? ((Number) deathsObj).intValue() : 0;

                    // ★ 核心修复：安全地获取排名数据
                    Object rankingObj = match.get("ranking");
                    String rankingText = "N/A"; // 默认显示 "N/A"
                    if (rankingObj instanceof Number) {
                        rankingText = String.valueOf(((Number) rankingObj).intValue());
                    }

                    // 设置最终显示的文本格式
                    setText(String.format("模式: %s  |  K/D: %d/%d  |  地图: %s  |  排名: %s",
                            match.get("gameMode"),
                            kills,
                            deaths,
                            match.get("mapName"),
                            rankingText)); // 使用我们安全获取的排名文本
                }
            }
        });

        loadHistoryWithDetails();
    }
*/

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 【修改】简化 CellFactory，只负责显示摘要信息
        historyListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Map<String, Object> match, boolean empty) {
                super.updateItem(match, empty);
                if (empty || match == null) {
                    setText(null);
                } else {
                    // 安全地获取排名
                    Object rankingObj = match.get("ranking");
                    String rankingText = "N/A";
                    if (rankingObj instanceof Number) {
                        rankingText = String.valueOf(((Number) rankingObj).intValue());
                    }

                    // 解析和格式化时间
                    String startTimeStr = (String) match.get("startTime");
                    String formattedTime = "未知时间";
                    if (startTimeStr != null) {
                        try {
                            LocalDateTime ldt = LocalDateTime.parse(startTimeStr);
                            formattedTime = ldt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                        } catch (Exception e) {
                            // 时间格式解析失败，保持默认值
                        }
                    }

                    // 设置最终显示的文本，只使用API返回的字段
                    setText(String.format("【%s】 %s | 地图: %s | 排名: %s",
                            match.get("gameMode"),
                            formattedTime,
                            match.get("mapName"),
                            rankingText
                    ));
                }
            }
        });

        // 【新增】为ListView添加点击事件监听器
        historyListView.setOnMouseClicked(event -> {
            Map<String, Object> selectedMatch = historyListView.getSelectionModel().getSelectedItem();
            if (selectedMatch != null) {
                long matchId = ((Number) selectedMatch.get("matchId")).longValue();
                showMatchDetails(matchId);
            }
        });

        // 【修改】调用简化的加载方法
        loadHistory();
    }

    /**
     * 【修改】简化加载逻辑，只获取列表
     */
    private void loadHistory() {
        new Thread(() -> {
            try {
                List<Map<String, Object>> matches = apiClient.getHistory();
                Platform.runLater(() -> historyListView.getItems().setAll(matches));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * 【修改】获取并显示指定对局的详细信息
     */
    /*private void showMatchDetails(long matchId) {
        new Thread(() -> {
            try {
                // 1. 调用API获取战绩详情
                JsonObject details = apiClient.getHistoryDetails(matchId);

                // 2. 在UI线程上构建并显示弹窗
                Platform.runLater(() -> {
                    // 创建一个垂直布局作为弹窗内容
                    VBox content = new VBox(15);
                    content.setPadding(new Insets(10));

                    // 创建一个网格布局来显示玩家数据
                    GridPane grid = new GridPane();
                    grid.setHgap(20); // 水平间距
                    grid.setVgap(10);  // 垂直间距

                    // 添加表头
                    addGridRow(grid, 0, true, "排名", "玩家昵称", "击杀", "死亡");

                    // 遍历参与者列表并添加到网格中
                    JsonArray participants = details.getAsJsonArray("participants");
                    int rowIndex = 1;
                    for (JsonElement pElement : participants) {
                        JsonObject p = pElement.getAsJsonObject();
                        addGridRow(grid, rowIndex++, false,
                                p.get("ranking").getAsString(),
                                p.get("nickname").getAsString(),
                                p.get("kills").getAsString(),
                                p.get("deaths").getAsString()
                        );
                    }

                    // 将标题和网格表格添加到VBox中
                    content.getChildren().add(new Text("对局详情 (ID: " + matchId + ")"));
                    content.getChildren().add(grid);

                    // 使用FXGL的对话框服务显示弹窗
                    FXGL.getDialogService().showBox("战绩详情", content, new Button("关闭"));
                });

            } catch (Exception e) {
                Platform.runLater(() -> FXGL.getDialogService().showMessageBox("获取详情失败: " + e.getMessage()));
                e.printStackTrace();
                System.out.println("发生错误？？？？？？？？？");
            }
        }).start();
    }
*/

    /**
     * 【新调试版】获取并显示指定对局的详细信息
     */
    private void showMatchDetails(long matchId) {
        new Thread(() -> {
            try {
                JsonObject details = apiClient.getHistoryDetails(matchId);

                Platform.runLater(() -> {
                    try {
                        // 主容器
                        VBox content = new VBox(15);
                        content.setPadding(new Insets(20));
                        content.setAlignment(Pos.TOP_CENTER);
                        content.setPrefWidth(550); // 调整宽度以容纳更多信息

                        // 新增：顶部的对局概要信息
                        VBox matchInfoBox = new VBox(5);
                        matchInfoBox.setAlignment(Pos.CENTER_LEFT);
                        matchInfoBox.setStyle("-fx-padding: 10; -fx-background-color: #f1f5f9; -fx-background-radius: 8;");

                        String gameMode = details.get("gameMode").getAsString();
                        String mapName = details.get("mapName").getAsString();
                        String startTime = formatDateTime(details.get("startTime").getAsString());
                        String endTime = formatDateTime(details.get("endTime").getAsString());

                        matchInfoBox.getChildren().addAll(
                                new Text("模式: " + gameMode),
                                new Text("地图: " + mapName),
                                new Text("开始时间: " + startTime),
                                new Text("结束时间: " + endTime)
                        );


                        // 玩家战绩表格
                        GridPane grid = new GridPane();
                        grid.setHgap(15);
                        grid.setVgap(8);
                        grid.setAlignment(Pos.CENTER);
                        grid.setPadding(new Insets(10));

                        // 修改：调整列宽以适应新列
                        grid.getColumnConstraints().addAll(
                                createColumn(60, HPos.CENTER),  // 排名
                                createColumn(180, HPos.LEFT),   // 玩家
                                createColumn(80, HPos.CENTER),  // 角色ID (新增)
                                createColumn(80, HPos.CENTER),  // 击杀
                                createColumn(80, HPos.CENTER)   // 死亡
                        );

                        // 修改：增加“角色ID”表头
                        addGridHeader(grid, 0, "排名", "玩家", "角色", "击杀", "死亡");
                        grid.add(new Separator(), 0, 1, 5, 1); // 分隔线跨5列

                        // 新增：按排名排序
                        JsonArray participantsArray = details.getAsJsonArray("participants");
                        List<JsonElement> participants = new ArrayList<>();
                        participantsArray.forEach(participants::add);
                        participants.sort(Comparator.comparingInt(p -> p.getAsJsonObject().get("ranking").getAsInt()));

                        // 填充数据行
                        int rowIndex = 2;
                        for (JsonElement pElement : participants) {
                            JsonObject p = pElement.getAsJsonObject();
                            // 新增：判断是否为当前玩家以实现高亮
                            boolean isCurrentUser = GlobalState.userId != null && p.get("userId").getAsLong() == GlobalState.userId;
                            addGridDataRow(grid, rowIndex++, isCurrentUser,
                                    p.get("ranking").getAsString(),
                                    p.get("nickname").getAsString(),
                                    p.get("characterName").getAsString(), // 新增数据
                                    p.get("kills").getAsString(),
                                    p.get("deaths").getAsString()
                            );
                        }

                        // 将所有组件添加到主容器
                        content.getChildren().addAll(matchInfoBox, grid);

                        // 显示弹窗
                        FXGL.getDialogService().showBox("战绩详情 (ID: " + matchId + ")", content, new Button("关闭"));

                    } catch (Exception uiException) {
                        uiException.printStackTrace();
                        FXGL.getDialogService().showMessageBox("显示详情失败: " + uiException.getMessage());
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() ->
                        FXGL.getDialogService().showMessageBox("获取详情失败: " + e.getMessage())
                );
            }
        }).start();
    }
    // 新增：一个用于创建列约束的辅助方法
    private ColumnConstraints createColumn(double width, HPos alignment) {
        ColumnConstraints col = new ColumnConstraints(width);
        col.setHalignment(alignment);
        return col;
    }

    // 修改：更新表头方法以匹配新的列数
    private void addGridHeader(GridPane grid, int row, String... headers) {
        for (int col = 0; col < headers.length; col++) {
            Label label = new Label(headers[col]);
            // 新增了 -fx-text-fill: #d1d5db; (一个柔和的浅灰色)
            label.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #d1d5db;");
            grid.add(label, col, row);
        }
    }

    // 修改：更新数据行方法以支持高亮
    private void addGridDataRow(GridPane grid, int row, boolean isHighlight, String... values) {
        for (int col = 0; col < values.length; col++) {
            Label label = new Label(values[col]);
            String style;
            if (isHighlight) {
                // 高亮行：浅蓝背景，深色文字
                style = "-fx-background-color: #e0f2fe; -fx-font-weight: bold; -fx-padding: 2 5; -fx-font-size: 13px; -fx-text-fill: #1e293b;";
            } else {
                // 普通行：透明背景，白色文字
                style = "-fx-font-size: 13px; -fx-text-fill: white;";
            }
            label.setStyle(style);
            grid.add(label, col, row);
        }
    }

    // 新增：格式化时间的辅助方法
    private String formatDateTime(String isoDateTime) {
        try {
            return LocalDateTime.parse(isoDateTime, INPUT_FORMATTER).format(OUTPUT_FORMATTER);
        } catch (Exception e) {
            return isoDateTime; // 如果解析失败，返回原始字符串
        }
    }
    /**
     * 【新增】一个辅助方法，用于向GridPane添加一行数据
     */
    private void addGridRow(GridPane grid, int row, boolean isHeader, String... values) {
        for (int i = 0; i < values.length; i++) {
            Text text = new Text(values[i]);
            if (isHeader) {
                text.setFont(Font.font("System", FontWeight.BOLD, 14));
            }
            grid.add(text, i, row);
        }
    }

    /**
     * 加载历史战绩，并为每一条记录补充 K/D 详情
     */
    private void loadHistoryWithDetails() {
        new Thread(() -> {
            try {
                // 1. 先获取基础的战绩列表
                List<Map<String, Object>> matches = apiClient.getHistory();

                // 2. 遍历每一条战绩，去请求它的详细信息
                for (Map<String, Object> match : matches) {
                    long matchId = ((Number) match.get("matchId")).longValue();
                    try {
                        JsonObject details = apiClient.getHistoryDetails(matchId);
                        JsonArray participants = details.getAsJsonArray("participants");

                        // 3. 在参与者列表中找到自己
                        for (JsonElement pElement : participants) {
                            JsonObject p = pElement.getAsJsonObject();
                            long userId = p.get("userId").getAsLong();

                            if (GlobalState.userId != null && GlobalState.userId == userId) {
                                // 4. 找到后，将 K/D 数据补充到原始的 match Map 中
                                match.put("myKills", p.get("kills").getAsInt());
                                match.put("myDeaths", p.get("deaths").getAsInt());
                                break; // 找到自己后就跳出内部循环
                            }
                        }
                    } catch (IOException e) {
                        // 如果某一条详情获取失败，可以设置默认值或忽略
                        System.err.println("获取对局 " + matchId + " 的详情失败: " + e.getMessage());
                    }
                }

                // 5. 当所有详情都获取并补充完毕后，在UI线程上更新整个列表
                Platform.runLater(() -> historyListView.getItems().setAll(matches));

            } catch (Exception e) {
                // 如果获取列表本身就失败了
                e.printStackTrace();
            }
        }).start();
    }

    @FXML
    private void handleBackToLobby() {
        UIManager.load("lobby-view.fxml");
    }
}