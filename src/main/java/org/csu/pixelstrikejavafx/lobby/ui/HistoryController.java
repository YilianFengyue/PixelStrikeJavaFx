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
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.csu.pixelstrikejavafx.lobby.network.ApiClient;
import org.csu.pixelstrikejavafx.core.GlobalState;
import org.csu.pixelstrikejavafx.lobby.ui.dialog.DialogManager;

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

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        historyListView.setCellFactory(lv -> new ListCell<>() {
            // 为每个单元格创建一个布局
            private final BorderPane layout = new BorderPane();
            private final VBox leftBox = new VBox(5);
            private final VBox rightBox = new VBox(5);
            private final Label gameModeLabel = new Label();
            private final Label mapLabel = new Label();
            private final Label timeLabel = new Label();
            private final Label rankingLabel = new Label("排名");
            private final Label rankingValue = new Label();
            private final Region spacer = new Region();

            {
                // 初始化布局
                gameModeLabel.getStyleClass().add("history-cell-gamemode");
                mapLabel.getStyleClass().add("history-cell-map");
                timeLabel.getStyleClass().add("history-cell-time");
                rankingLabel.getStyleClass().add("history-cell-ranking-label");
                rankingValue.getStyleClass().add("history-cell-ranking-value");

                leftBox.getChildren().addAll(gameModeLabel, mapLabel, timeLabel);
                rightBox.getChildren().addAll(rankingLabel, rankingValue);
                rightBox.setAlignment(Pos.CENTER);

                layout.setLeft(leftBox);
                layout.setRight(rightBox);
                layout.getStyleClass().add("history-cell-container");
            }

            @Override
            protected void updateItem(Map<String, Object> match, boolean empty) {
                super.updateItem(match, empty);
                if (empty || match == null) {
                    setGraphic(null);
                } else {
                    gameModeLabel.setText((String) match.get("gameMode"));
                    mapLabel.setText("地图: " + match.get("mapName"));

                    String startTimeStr = (String) match.get("startTime");
                    try {
                        LocalDateTime ldt = LocalDateTime.parse(startTimeStr);
                        timeLabel.setText(ldt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                    } catch (Exception e) {
                        timeLabel.setText("未知时间");
                    }

                    Object rankingObj = match.get("ranking");
                    rankingValue.setText(rankingObj instanceof Number ? String.valueOf(((Number) rankingObj).intValue()) : "N/A");

                    setGraphic(layout);
                }
            }
        });

        historyListView.setOnMouseClicked(event -> {
            Map<String, Object> selectedMatch = historyListView.getSelectionModel().getSelectedItem();
            if (selectedMatch != null) {
                long matchId = ((Number) selectedMatch.get("matchId")).longValue();
                showMatchDetails(matchId);
            }
        });

        loadHistory();
    }

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

    private void showMatchDetails(long matchId) {
        new Thread(() -> {
            try {
                JsonObject details = apiClient.getHistoryDetails(matchId);

                Platform.runLater(() -> {
                    try {
                        // --- 1. 创建UI组件 ---
                        VBox rootPane = new VBox();
                        rootPane.setPrefWidth(550);
                        rootPane.setStyle("-fx-background-color: black; -fx-border-color: #4b5563; -fx-border-width: 1; -fx-background-radius: 8; -fx-border-radius: 8;");

                        // a) 创建可拖动的标题栏
                        Label titleLabel = new Label("战绩详情 (ID: " + matchId + ")");
                        titleLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10; -fx-cursor: move;");
                        titleLabel.setMaxWidth(Double.MAX_VALUE);
                        titleLabel.setAlignment(Pos.CENTER);

                        // b) 对局概要信息 (不变)
                        VBox matchInfoBox = new VBox(5);
                        matchInfoBox.setAlignment(Pos.CENTER_LEFT);
                        matchInfoBox.setStyle("-fx-padding: 10; -fx-background-color: #1f2937; -fx-background-radius: 8; -fx-border-color: #374151;");
                        String gameMode = details.get("gameMode").getAsString();
                        String mapName = details.get("mapName").getAsString();
                        String startTime = formatDateTime(details.get("startTime").getAsString());
                        String endTime = formatDateTime(details.get("endTime").getAsString());
                        matchInfoBox.getChildren().addAll(
                                new Text("模式: " + gameMode), new Text("地图: " + mapName),
                                new Text("开始时间: " + startTime), new Text("结束时间: " + endTime)
                        );
                        // 为概要信息里的文字设置白色
                        matchInfoBox.getChildren().forEach(node -> node.setStyle("-fx-fill: white;"));


                        // c) 战绩表格 (不变)
                        GridPane grid = new GridPane();
                        grid.setHgap(15); grid.setVgap(8); grid.setAlignment(Pos.CENTER); grid.setPadding(new Insets(10));
                        grid.getColumnConstraints().addAll(
                                createColumn(60, HPos.CENTER), createColumn(180, HPos.LEFT),
                                createColumn(80, HPos.CENTER), createColumn(80, HPos.CENTER),
                                createColumn(80, HPos.CENTER)
                        );
                        addGridHeader(grid, 0, "排名", "玩家", "角色", "击杀", "死亡");
                        grid.add(new Separator(), 0, 1, 5, 1);
                        JsonArray participantsArray = details.getAsJsonArray("participants");
                        List<JsonElement> participants = new ArrayList<>();
                        participantsArray.forEach(participants::add);
                        participants.sort(Comparator.comparingInt(p -> p.getAsJsonObject().get("ranking").getAsInt()));
                        int rowIndex = 2;
                        for (JsonElement pElement : participants) {
                            JsonObject p = pElement.getAsJsonObject();
                            boolean isCurrentUser = GlobalState.userId != null && p.get("userId").getAsLong() == GlobalState.userId;
                            addGridDataRow(grid, rowIndex++, isCurrentUser,
                                    p.get("ranking").getAsString(), p.get("nickname").getAsString(),
                                    p.get("characterName").getAsString(), p.get("kills").getAsString(),
                                    p.get("deaths").getAsString()
                            );
                        }
                        VBox contentPane = new VBox(10, matchInfoBox, grid);
                        contentPane.setPadding(new Insets(10));

                        // d) 关闭按钮
                        Button closeButton = new Button("关闭");
                        String buttonStyle = "-fx-background-color: #4b5563; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5; -fx-cursor: hand;";
                        closeButton.setStyle(buttonStyle);
                        HBox buttonBar = new HBox(closeButton);
                        buttonBar.setAlignment(Pos.CENTER);
                        buttonBar.setPadding(new Insets(10));

                        // e) 组装所有部分
                        rootPane.getChildren().addAll(titleLabel, new Separator(), contentPane, buttonBar);
                        VBox.setVgrow(contentPane, Priority.ALWAYS);

                        // --- 2. 创建和配置独立的窗口 (Stage) ---
                        Stage stage = new Stage();
                        stage.initOwner(FXGL.getPrimaryStage());
                        stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
                        stage.initStyle(javafx.stage.StageStyle.TRANSPARENT);
                        Scene scene = new Scene(rootPane);
                        scene.setFill(Color.TRANSPARENT);
                        stage.setScene(scene);

                        // --- 3. 实现窗口拖动 ---
                        final double[] xOffset = {0}, yOffset = {0};
                        titleLabel.setOnMousePressed(event -> {
                            xOffset[0] = event.getSceneX();
                            yOffset[0] = event.getSceneY();
                        });
                        titleLabel.setOnMouseDragged(event -> {
                            stage.setX(event.getScreenX() - xOffset[0]);
                            stage.setY(event.getScreenY() - yOffset[0]);
                        });

                        // --- 4. 关闭逻辑 ---
                        closeButton.setOnAction(e -> stage.close());

                        // --- 5. 显示窗口 ---
                        stage.showAndWait();

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

    // 新的辅助方法，用于构建和显示美化后的详情弹窗
    private void buildAndShowDetailsDialog(JsonObject details, long matchId) {
        try {
            VBox content = new VBox(15);
            content.setPadding(new Insets(20));
            content.setAlignment(Pos.TOP_CENTER);
            content.setPrefWidth(600);

            // 概要信息
            VBox matchInfoBox = new VBox(5);
            matchInfoBox.getStyleClass().add("details-summary-box");
            matchInfoBox.getChildren().addAll(
                    new Text("模式: " + details.get("gameMode").getAsString()),
                    new Text("地图: " + details.get("mapName").getAsString()),
                    new Text("开始时间: " + formatDateTime(details.get("startTime").getAsString())),
                    new Text("结束时间: " + formatDateTime(details.get("endTime").getAsString()))
            );

            // 玩家战绩表格
            GridPane grid = new GridPane();
            grid.setHgap(15);
            grid.setVgap(8);
            grid.setAlignment(Pos.CENTER);

            // ... (这部分代码与您原来的版本相同，无需修改)
            grid.getColumnConstraints().addAll(
                    createColumn(60, HPos.CENTER), createColumn(180, HPos.LEFT),
                    createColumn(80, HPos.CENTER), createColumn(80, HPos.CENTER),
                    createColumn(80, HPos.CENTER)
            );
            addGridHeader(grid, 0, "排名", "玩家", "角色", "击杀", "死亡");
            grid.add(new Separator(), 0, 1, 5, 1);

            JsonArray participantsArray = details.getAsJsonArray("participants");
            List<JsonElement> participants = new ArrayList<>();
            participantsArray.forEach(participants::add);
            participants.sort(Comparator.comparingInt(p -> p.getAsJsonObject().get("ranking").getAsInt()));

            int rowIndex = 2;
            for (JsonElement pElement : participants) {
                JsonObject p = pElement.getAsJsonObject();
                boolean isCurrentUser = GlobalState.userId != null && p.get("userId").getAsLong() == GlobalState.userId;
                addGridDataRow(grid, rowIndex++, isCurrentUser,
                        p.get("ranking").getAsString(), p.get("nickname").getAsString(),
                        p.get("characterName").getAsString(), p.get("kills").getAsString(),
                        p.get("deaths").getAsString());
            }

            content.getChildren().addAll(matchInfoBox, grid);
            FXGL.getDialogService().showBox("战绩详情 (ID: " + matchId + ")", content, new Button("关闭"));

        } catch (Exception uiException) {
            uiException.printStackTrace();
            FXGL.getDialogService().showMessageBox("显示详情失败: " + uiException.getMessage());
        }
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

    private void addGridDataRow(GridPane grid, int row, boolean isHighlight, String... values) {
        for (int col = 0; col < values.length; col++) {
            Label label = new Label(values[col]);
            label.setWrapText(true);
            if (col == 1) { // 玩家列
                label.setMaxWidth(210);
            } else if (col == 2) { // 角色列
                label.setMaxWidth(170);
            }
            String style;
            if (isHighlight) {
                style = "-fx-background-color: rgba(144, 164, 174, 0.3); -fx-background-radius: 5; -fx-font-weight: bold; -fx-padding: 3 6; -fx-font-size: 14px; -fx-text-fill: #ecf0f1;";
            } else {
                style = "-fx-font-size: 14px; -fx-text-fill: #ecf0f1;";
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
/*    *//**
     * 【新增】一个辅助方法，用于向GridPane添加一行数据
     *//*
    private void addGridRow(GridPane grid, int row, boolean isHeader, String... values) {
        for (int i = 0; i < values.length; i++) {
            Text text = new Text(values[i]);
            if (isHeader) {
                text.setFont(Font.font("System", FontWeight.BOLD, 14));
            }
            grid.add(text, i, row);
        }
    }*/

  /*  *//**
     * 加载历史战绩，并为每一条记录补充 K/D 详情
     *//*
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
    }*/

    @FXML
    private void handleBackToLobby() {
        UIManager.load("lobby-view.fxml");
    }
}