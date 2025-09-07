package org.csu.pixelstrikejavafx.events;

import com.google.gson.JsonObject;
import javafx.event.Event;
import javafx.event.EventType;

// 当收到新的好友申请时，发送此事件
public class NewFriendRequestEvent extends Event {
    public static final EventType<NewFriendRequestEvent> ANY = new EventType<>(Event.ANY, "NEW_FRIEND_REQUEST");
    private final JsonObject data;

    public NewFriendRequestEvent(JsonObject data) {
        super(ANY);
        this.data = data;
    }

    public JsonObject getData() {
        return data;
    }
}