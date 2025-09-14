package org.csu.pixelstrikejavafx.lobby.events;

import com.google.gson.JsonObject;
import javafx.event.Event;
import javafx.event.EventType;

public class FriendProfileUpdateEvent extends Event {
    public static final EventType<FriendProfileUpdateEvent> ANY = new EventType<>(Event.ANY, "FRIEND_PROFILE_UPDATE");
    private final JsonObject data;

    public FriendProfileUpdateEvent(JsonObject data) {
        super(ANY);
        this.data = data;
    }

    public JsonObject getData() {
        return data;
    }
}