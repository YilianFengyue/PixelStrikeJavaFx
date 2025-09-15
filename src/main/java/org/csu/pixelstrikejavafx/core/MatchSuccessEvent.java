package org.csu.pixelstrikejavafx.core;

import com.google.gson.JsonObject; // 新增导入
import javafx.event.Event;
import javafx.event.EventType;

/**
 * 当匹配成功时，发送此事件。
 */
public class MatchSuccessEvent extends Event {

    public static final EventType<MatchSuccessEvent> ANY = new EventType<>(Event.ANY, "MATCH_SUCCESS");

    private final String serverAddress;
    private final long gameId;
    private final JsonObject characterSelections;

    public MatchSuccessEvent(String serverAddress, long gameId, JsonObject characterSelections) { // <-- 修改构造函数
        super(ANY);
        this.serverAddress = serverAddress;
        this.gameId = gameId;
        this.characterSelections = characterSelections;
    }

    public String getServerAddress() {
        return serverAddress;
    }

    public long getGameId() {
        return gameId;
    }

    public JsonObject getCharacterSelections() {
        return characterSelections;
    }
}