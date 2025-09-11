package org.csu.pixelstrikejavafx.lobby.events;

import com.google.gson.JsonObject;
import javafx.event.Event;
import javafx.event.EventType;

// 当你的好友申请被别人接受时，发送此事件
public class FriendRequestAcceptedEvent extends Event {
    public static final EventType<FriendRequestAcceptedEvent> ANY = new EventType<>(Event.ANY, "FRIEND_REQUEST_ACCEPTED");
    private final JsonObject data;

    public FriendRequestAcceptedEvent(JsonObject data) {
        super(ANY);
        this.data = data;
    }

    public JsonObject getData() {
        return data;
    }
}