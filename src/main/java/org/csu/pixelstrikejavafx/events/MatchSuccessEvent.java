package org.csu.pixelstrikejavafx.events;

import javafx.event.Event;
import javafx.event.EventType;

/**
 * 当匹配成功时，发送此事件。
 */
public class MatchSuccessEvent extends Event {

    public static final EventType<MatchSuccessEvent> ANY = new EventType<>(Event.ANY, "MATCH_SUCCESS");

    private final String serverAddress;
    private final int gameId;

    public MatchSuccessEvent(String serverAddress, int gameId) {
        super(ANY);
        this.serverAddress = serverAddress;
        this.gameId = gameId;
    }

    public String getServerAddress() {
        return serverAddress;
    }

    public int getGameId() {
        return gameId;
    }
}