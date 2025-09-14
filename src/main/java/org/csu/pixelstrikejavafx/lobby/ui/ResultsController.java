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
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Separator;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
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
        // 确保所有UI操作都在JavaFX应用线程上执行
        Platform.runLater(() -> {
            // 在渲染新内容前，先清空旧的UI元素
            resultsContent.getChildren().clear();

            // --- 以下是您之前编写的、完整的UI构建逻辑 ---

            VBox content = new VBox(15);
            content.setPadding(new Insets(20));
            content.setAlignment(Pos.TOP_CENTER);

            VBox matchInfoBox = new VBox(5);
            matchInfoBox.setAlignment(Pos.CENTER_LEFT);
            matchInfoBox.setStyle("-fx-padding: 10; -fx-background-color: #f1f5f9; -fx-background-radius: 8;");

            String gameMode = details.get("gameMode").getAsString();
            String mapName = details.get("mapName").getAsString();
            String startTime = formatDateTime(details.get("startTime").getAsString());
            String endTime = formatDateTime(details.get("endTime").getAsString());

            matchInfoBox.getChildren().addAll(
                    new Text("模式: " + gameMode), new Text("地图: " + mapName),
                    new Text("开始时间: " + startTime), new Text("结束时间: " + endTime)
            );

            GridPane grid = new GridPane();
            grid.setHgap(15);
            grid.setVgap(8);
            grid.setAlignment(Pos.CENTER);

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

            content.getChildren().addAll(matchInfoBox, grid);

            // 将构建好的完整内容添加到resultsContent容器中
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
            label.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
            grid.add(label, col, row);
        }
    }

    private void addGridDataRow(GridPane grid, int row, boolean isHighlight, String... values) {
        for (int col = 0; col < values.length; col++) {
            Label label = new Label(values[col]);
            String style = "-fx-font-size: 13px;";
            if (isHighlight) {
                style += "-fx-background-color: #e0f2fe; -fx-font-weight: bold; -fx-padding: 2 5; -fx-text-fill: #1e293b;";
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