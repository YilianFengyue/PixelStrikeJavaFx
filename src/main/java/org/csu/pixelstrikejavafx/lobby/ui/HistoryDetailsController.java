package org.csu.pixelstrikejavafx.lobby.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import org.csu.pixelstrikejavafx.core.GlobalState;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class HistoryDetailsController {

    @FXML private Label titleLabel;
    @FXML private VBox matchInfoBox;
    @FXML private GridPane grid;
    @FXML private Button closeButton;

    private Runnable onClose;

    private static final DateTimeFormatter INPUT_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter OUTPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    @FXML
    private void handleClose() {
        if (onClose != null) {
            onClose.run();
        }
    }

    public void populateDetails(JsonObject details, long matchId) {
        titleLabel.setText("战绩详情 (ID: " + matchId + ")");

        // 填充概要信息
        matchInfoBox.getChildren().addAll(
                new Text("模式: " + details.get("gameMode").getAsString()),
                new Text("地图: " + details.get("mapName").getAsString()),
                new Text("开始时间: " + formatDateTime(details.get("startTime").getAsString())),
                new Text("结束时间: " + formatDateTime(details.get("endTime").getAsString()))
        );

        // 填充表格
        setupGrid(details);
    }

    private void setupGrid(JsonObject details) {
        grid.getColumnConstraints().addAll(
                createColumn(80, HPos.CENTER),  // 排名: 稍微宽一点
                createColumn(200, HPos.LEFT), // 玩家: 略宽
                createColumn(150, HPos.CENTER), // 【修改】角色: 更宽，用于显示长名字
                createColumn(80, HPos.CENTER),  // 击杀
                createColumn(80, HPos.CENTER)   // 死亡
        );
        addGridHeader(grid, 0, "排名", "玩家", "角色", "击杀", "死亡");
        grid.add(new Separator(), 0, 1, 5, 1); // 5列

        JsonArray participantsArray = details.getAsJsonArray("participants");
        List<JsonElement> participants = new ArrayList<>();
        participantsArray.forEach(participants::add);
        participants.sort(Comparator.comparingInt(p -> p.getAsJsonObject().get("ranking").getAsInt()));

        int rowIndex = 2;
        for (JsonElement pElement : participants) {
            JsonObject p = pElement.getAsJsonObject();
            boolean isCurrentUser = GlobalState.userId != null && p.get("userId").getAsLong() == GlobalState.userId;
            addGridDataRow(grid, rowIndex++, isCurrentUser,
                    p.get("ranking").getAsString(),
                    p.get("nickname").getAsString(),
                    p.get("characterName").getAsString(), // 角色名
                    p.get("kills").getAsString(),
                    p.get("deaths").getAsString());
        }
    }


    private ColumnConstraints createColumn(double width, HPos alignment) {
        ColumnConstraints col = new ColumnConstraints(width);
        col.setHalignment(alignment);
        return col;
    }

    private void addGridHeader(GridPane grid, int row, String... headers) {
        for (int col = 0; col < headers.length; col++) {
            Label label = new Label(headers[col]);
            label.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #bdc3c7;");
            grid.add(label, col, row);
        }
    }

    private void addGridDataRow(GridPane grid, int row, boolean isHighlight, String... values) {
        for (int col = 0; col < values.length; col++) {
            Label label = new Label(values[col]);
            label.setWrapText(true); // 【新增】允许文本自动换行
            label.setMaxWidth(col == 1 ? 190 : (col == 2 ? 140 : 70));
            String style = isHighlight
                    ? "-fx-background-color: #e0f2fe; -fx-font-weight: bold; -fx-padding: 2 5; -fx-font-size: 13px; -fx-text-fill: #1e293b;"
                    : "-fx-font-size: 13px; -fx-text-fill: white;";
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
}