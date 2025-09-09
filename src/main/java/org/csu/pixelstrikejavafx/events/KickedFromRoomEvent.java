package org.csu.pixelstrikejavafx.events;

import javafx.event.Event;
import javafx.event.EventType;

// 当玩家被踢出房间时，发送此事件
public class KickedFromRoomEvent extends Event {
    public static final EventType<KickedFromRoomEvent> ANY = new EventType<>(Event.ANY, "KICKED_FROM_ROOM");

    private final String roomId;

    public KickedFromRoomEvent(String roomId) {
        super(ANY);
        this.roomId = roomId;
    }

    public String getRoomId() {
        return roomId;
    }
}