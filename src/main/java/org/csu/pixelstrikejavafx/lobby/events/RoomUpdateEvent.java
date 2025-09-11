package org.csu.pixelstrikejavafx.lobby.events;

import com.google.gson.JsonObject;
import javafx.event.Event;
import javafx.event.EventType;

public class RoomUpdateEvent extends Event {
    public static final EventType<RoomUpdateEvent> ANY = new EventType<>(Event.ANY, "ROOM_UPDATE");
    private final JsonObject data;

    public RoomUpdateEvent(JsonObject data) {
        super(ANY);
        this.data = data;
    }

    public JsonObject getData() {
        return data;
    }
}