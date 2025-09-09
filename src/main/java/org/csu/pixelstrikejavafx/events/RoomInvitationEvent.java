package org.csu.pixelstrikejavafx.events;

import javafx.event.Event;
import javafx.event.EventType;

public class RoomInvitationEvent extends Event {
    public static final EventType<RoomInvitationEvent> ANY = new EventType<>(Event.ANY, "ROOM_INVITATION");

    private final String roomId;
    private final String inviterNickname;
    private final long inviterId;

    public RoomInvitationEvent(String roomId, String inviterNickname, long inviterId) {
        super(ANY);
        this.roomId = roomId;
        this.inviterNickname = inviterNickname;
        this.inviterId = inviterId;
    }

    public String getRoomId() { return roomId; }
    public String getInviterNickname() { return inviterNickname; }
    public long getInviterId() { return inviterId; }
}