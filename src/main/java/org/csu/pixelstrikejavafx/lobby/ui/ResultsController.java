package org.csu.pixelstrikejavafx.lobby.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Separator;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import org.csu.pixelstrikejavafx.core.GlobalState;
import org.csu.pixelstrikejavafx.core.MatchResultsModel;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ResourceBundle;

public class ResultsController implements Initializable {

    @FXML private VBox mainContainer;
    @FXML private Button backToLobbyButton;
    @FXML private ProgressIndicator loadingIndicator; // 用于显示加载状态
    @FXML private VBox resultsContent; // 用于包裹实际的战绩信息

    private static final DateTimeFormatter INPUT_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter OUTPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 1. 将UI组件的可见性绑定到数据模型的加载状态上
        // 当isLoading为true时，显示加载圈，隐藏战绩内容
        if (loadingIndicator != null && resultsContent != null) {
            loadingIndicator.visibleProperty().bind(MatchResultsModel.isLoadingProperty());
            resultsContent.visibleProperty().bind(MatchResultsModel.isLoadingProperty().not());
        }

        // 2. 添加一个监听器，当战绩数据发生变化时，自动调用渲染方法
        MatchResultsModel.matchResultsProperty().addListener((obs, oldResults, newResults) -> {
            if (newResults != null) {
                // 当数据从null变为非null时，渲染UI
                System.out.println("Results data received, rendering UI.");
                renderResults(newResults);
            } else {
                // 当数据从有变为null时 (通过reset调用)，清空UI
                clearResults();
            }
        });

        // 3. 初始化时检查：如果数据已经存在（例如，在UI切换前数据就已到达），立即渲染
        JsonObject initialResults = MatchResultsModel.matchResultsProperty().get();
        if (initialResults != null) {
            System.out.println("Initial results data found, rendering immediately.");
            renderResults(initialResults);
        } else {
            System.out.println("ResultsController initialized, waiting for data...");
        }
    }

    /**
     * 使用从数据模型接收到的战绩数据来构建和显示UI。
     * @param details 包含完整战绩信息的JsonObject。
     */
    private void renderResults(JsonObject details) {
        Platform.runLater(() -> {
            resultsContent.getChildren().clear();

            VBox content = new VBox(25); // 增加间距
            content.setPadding(new Insets(20));
            content.setAlignment(Pos.TOP_CENTER);

            // 概要信息框
            VBox matchInfoBox = new VBox(8);
            matchInfoBox.setAlignment(Pos.CENTER_LEFT);
            matchInfoBox.getStyleClass().add("summary-box");

            matchInfoBox.getChildren().addAll(
                    new Text("模式: " + details.get("gameMode").getAsString()),
                    new Text("地图: " + details.get("mapName").getAsString()),
                    new Text("开始时间: " + formatDateTime(details.get("startTime").getAsString())),
                    new Text("结束时间: " + formatDateTime(details.get("endTime").getAsString()))
            );

            // 玩家战绩表格
            GridPane grid = new GridPane();
            grid.setHgap(15);
            grid.setVgap(10); // 增加行间距
            grid.setAlignment(Pos.CENTER);
            grid.getStyleClass().add("results-grid");

            // 【修改】调整列宽，为长名字留出空间
            grid.getColumnConstraints().addAll(
                    createColumn(80, HPos.CENTER),
                    createColumn(220, HPos.LEFT),
                    createColumn(180, HPos.CENTER),
                    createColumn(80, HPos.CENTER),
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

                Node[] rowNodes = createGridRow(isCurrentUser,
                        p.get("ranking").getAsString(),
                        p.get("nickname").getAsString(),
                        p.get("characterName").getAsString(),
                        p.get("kills").getAsString(),
                        p.get("deaths").getAsString()
                );

                grid.addRow(rowIndex++, rowNodes);
                if(isCurrentUser) {
                    // 如果需要单独的背景，可以添加一个透明的Pane
                    Region highlightBg = new Region();
                    highlightBg.getStyleClass().add("highlighted-row");
                    grid.add(highlightBg, 0, rowIndex - 1, 5, 1);
                    // 将节点提到最前，以免遮挡文字
                    for (Node node : rowNodes) {
                        node.toFront();
                    }
                }
            }

            content.getChildren().addAll(matchInfoBox, grid);
            resultsContent.getChildren().add(content);
        });
    }

    /**
     * 当数据被重置时，清空UI。
     */
    private void clearResults() {
        Platform.runLater(() -> {
            resultsContent.getChildren().clear();
        });
    }

    // --- 以下是您之前编写的、无需修改的辅助方法 ---

    private ColumnConstraints createColumn(double width, HPos alignment) {
        ColumnConstraints col = new ColumnConstraints(width);
        col.setHalignment(alignment);
        return col;
    }

    private void addGridHeader(GridPane grid, int row, String... headers) {
        for (int col = 0; col < headers.length; col++) {
            Label label = new Label(headers[col]);
            label.getStyleClass().add("grid-header");
            grid.add(label, col, row);
        }
    }
    private Node[] createGridRow(boolean isHighlight, String... values) {
        Node[] nodes = new Node[values.length];
        for (int col = 0; col < values.length; col++) {
            Label label = new Label(values[col]);
            label.setWrapText(true); // 允许自动换行
            if (col == 1) label.setMaxWidth(210);
            if (col == 2) label.setMaxWidth(170);

            nodes[col] = label;
        }
        return nodes;
    }

    private void addGridDataRow(GridPane grid, int row, boolean isHighlight, String... values) {
        for (int col = 0; col < values.length; col++) {
            Label label = new Label(values[col]);
            String style;
            if (isHighlight) {
                // 高亮行样式保持不变，因为浅蓝色背景配深色文字是清晰的
                style = "-fx-background-color: #e0f2fe; -fx-font-weight: bold; -fx-padding: 2 5; -fx-font-size: 13px; -fx-text-fill: #1e293b;";
            } else {
                // 【UI修正】为普通行的文字添加亮色
                style = "-fx-font-size: 13px; -fx-text-fill: white;"; // 直接使用白色
            }
            label.setStyle(style);
            grid.add(label, col, row);
        }
    }

    private String formatDateTime(String isoDateTime) {
        try {
            return LocalDateTime.parse(isoDateTime, INPUT_FORMATTER).format(OUTPUT_FORMATTER);
        } catch (Exception e) {
            return isoDateTime;
        }
    }

    @FXML
    private void handleBackToLobby() {
        UIManager.load("lobby-view.fxml");
    }
}