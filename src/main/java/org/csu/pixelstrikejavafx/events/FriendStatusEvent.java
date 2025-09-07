package org.csu.pixelstrikejavafx.events;

import com.google.gson.JsonObject;
import javafx.event.Event;
import javafx.event.EventType;

public class FriendStatusEvent extends Event {

    public static final EventType<FriendStatusEvent> ANY = new EventType<>(Event.ANY, "FRIEND_STATUS_UPDATE");

    private final JsonObject data;

    public FriendStatusEvent(JsonObject data) {
        // 构造函数需要调用父类的构造函数，并传入我们定义的事件类型
        super(ANY);
        this.data = data;
    }

    public JsonObject getData() {
        return data;
    }
}