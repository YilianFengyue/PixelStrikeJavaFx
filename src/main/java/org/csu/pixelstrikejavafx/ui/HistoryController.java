package org.csu.pixelstrikejavafx.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import org.csu.pixelstrikejavafx.http.ApiClient;
import org.csu.pixelstrikejavafx.state.GlobalState;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

public class HistoryController implements Initializable {

    @FXML private ListView<Map<String, Object>> historyListView;
    private final ApiClient apiClient = new ApiClient();

    @Override
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