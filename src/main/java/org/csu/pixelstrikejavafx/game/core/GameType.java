package org.csu.pixelstrikejavafx.game.core;

/**
 * 游戏对象类型枚举
 * 用于区分不同类型的游戏实体，便于碰撞检测和逻辑处理
 */
public enum GameType {
    PLAYER,      // 玩家角色
    GROUND,      // 地面
    PLATFORM,    // 平台
    WALL,        // 墙壁
    ITEM,        // 道具
    ENEMY,       // 敌人 (预留)
    DECORATION,  // 装饰物 (预留)
    BULLET,      // 子弹
    SUPPLY_DROP  // 地图刷新的物资
}