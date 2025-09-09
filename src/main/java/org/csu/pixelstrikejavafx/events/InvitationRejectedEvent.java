package org.csu.pixelstrikejavafx.events;

import javafx.event.Event;
import javafx.event.EventType;

// 当房间邀请被拒绝时，发送此事件
public class InvitationRejectedEvent extends Event {
    public static final EventType<InvitationRejectedEvent> ANY = new EventType<>(Event.ANY, "INVITATION_REJECTED");

    private final long rejectorId;

    public InvitationRejectedEvent(long rejectorId) {
        super(ANY);
        this.rejectorId = rejectorId;
    }

    public long getRejectorId() {
        return rejectorId;
    }
}