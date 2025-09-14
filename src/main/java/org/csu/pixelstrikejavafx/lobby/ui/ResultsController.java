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
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import org.csu.pixelstrikejavafx.core.GlobalState;

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

    private static final DateTimeFormatter INPUT_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter OUTPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 从全局状态加载数据并渲染
        JsonObject resultsData = GlobalState.lastMatchResults;
        if (resultsData != null) {
            renderResults(resultsData);
        } else {
            // 如果没有数据，显示错误信息
            mainContainer.getChildren().add(new Label("未能加载战绩信息。"));
        }

        // 清理全局状态，防止下次误用
        GlobalState.lastMatchResults = null;
    }

    private void renderResults(JsonObject details) {
        // 这部分代码几乎完全从 HistoryController 的 showMatchDetails 方法复制而来
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
        grid.setHgap(15); grid.setVgap(8); grid.setAlignment(Pos.CENTER);

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
        mainContainer.getChildren().add(content); // 将内容添加到主容器
    }

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
        } catch (Exception e) { return isoDateTime; }
    }

    @FXML
    private void handleBackToLobby() {
        UIManager.load("lobby-view.fxml");
    }
}
