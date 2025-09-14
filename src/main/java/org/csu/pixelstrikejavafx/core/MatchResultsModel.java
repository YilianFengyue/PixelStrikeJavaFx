// 文件: src/main/java/org/csu/pixelstrikejavafx/core/MatchResultsModel.java

package org.csu.pixelstrikejavafx.core;

import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * 一个全局的、响应式的战绩数据模型。
 * 用于解耦游戏结束逻辑与战绩UI显示。
 */
public class MatchResultsModel {

    // 使用JavaFX的Property，以便UI组件可以监听其变化
    private static final ObjectProperty<JsonObject> matchResults = new SimpleObjectProperty<>(null);
    private static final BooleanProperty isLoading = new SimpleBooleanProperty(true);

    // --- Public Getters for Properties ---

    public static ObjectProperty<JsonObject> matchResultsProperty() {
        return matchResults;
    }

    public static BooleanProperty isLoadingProperty() {
        return isLoading;
    }

    // --- Public Methods to Modify State ---

    /**
     * 当游戏结束，从服务器收到战绩时调用此方法。
     * @param results 从后端WebSocket消息中解析出的战绩JsonObject。
     */
    public static void setMatchResults(JsonObject results) {
        // 确保在JavaFX应用线程上更新Property
        Platform.runLater(() -> {
            matchResults.set(results);
            isLoading.set(false); // 数据已到达，结束加载状态
        });
    }

    /**
     * 在开始一场新游戏前调用此方法。
     * 重置状态，让战绩页面显示“加载中”。
     */
    public static void reset() {
        Platform.runLater(() -> {
            matchResults.set(null);
            isLoading.set(true); // 开始加载新数据
        });
    }
}