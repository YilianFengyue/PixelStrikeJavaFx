package org.csu.pixelstrikejavafx.lobby.events;

import com.google.gson.JsonObject;
import javafx.event.Event;
import javafx.event.EventType;

public class FriendRemovedEvent extends Event {
    public static final EventType<FriendRemovedEvent> ANY = new EventType<>(Event.ANY, "FRIEND_REMOVED");
    private final JsonObject data;

    public FriendRemovedEvent(JsonObject data) {
        super(ANY);
        this.data = data;
    }

    public JsonObject getData() {
        return data;
    }
}