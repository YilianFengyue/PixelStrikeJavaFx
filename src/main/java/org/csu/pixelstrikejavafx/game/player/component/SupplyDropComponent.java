package org.csu.pixelstrikejavafx.game.player.component;

import com.almasb.fxgl.entity.component.Component;

public class SupplyDropComponent extends Component {

    private final long dropId;
    private final String dropType;

    public SupplyDropComponent(long dropId, String dropType) {
        this.dropId = dropId;
        this.dropType = dropType;
    }

    public long getDropId() {
        return dropId;
    }
}