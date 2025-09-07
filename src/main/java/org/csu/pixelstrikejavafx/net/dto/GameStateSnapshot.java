package org.csu.pixelstrikejavafx.net.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class GameStateSnapshot {

    /**
     * 服务器生成此快照时的Tick编号。
     * 客户端可以用它来管理快照的顺序和插值。
     */
    private long tickNumber;

    /**
     * 所有玩家的状态。
     * Key是playerId, Value是该玩家的完整状态。
     */
    private Map<String, PlayerState> players;

    /**
     * 所有在空中飞行的子弹/抛射物。
     * 服务器负责计算它们的轨迹，客户端只负责渲染。
     */
    private List<ProjectileState> projectiles;

    /**
     * 地图上所有可拾取物品的状态。
     * 对应Gun Mayhem中的武器箱。
     */
    private List<PickupState> pickups;

    /**
     * 在这个Tick内发生的、需要客户端播放特效或音效的“一次性事件”。
     * 比如：某处发生了爆炸、某个玩家被击中。
     * 这比让客户端通过状态变化去“猜测”发生了什么要高效和准确得多。
     */
    private List<GameEvent> events;
    private Integer countdownSeconds;
    private Integer gameTimeRemainingSeconds;

    // --- 嵌套的子DTOs ---

    @Data
    public static class ProjectileState {
        public int id;
        public int typeId; // 子弹类型ID
        public double x, y;
    }

    @Data
    public static class PickupState {
        public int id;
        public int weaponId; // 箱子里的武器ID
        public double x, y;
    }

    @Data
    public static class GameEvent {
        public EventType type;
        public double x, y; // 事件发生的位置
        public String relatedPlayerId; // 与事件相关的玩家ID
        public enum EventType {
            BULLET_IMPACT, EXPLOSION, PLAYER_HIT, PLAYER_DIED
        }
    }

}