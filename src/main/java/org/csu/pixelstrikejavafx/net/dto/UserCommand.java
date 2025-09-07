package org.csu.pixelstrikejavafx.net.dto;

import lombok.Data;

/**
 * 代表一个客户端在一个Tick内上报的所有输入指令。
 * 这是服务器进行Sub-tick精确模拟的数据基础。
 */
@Data
public class UserCommand {

    private String playerId;

    /**
     * 客户端本地的指令序列号。
     * 服务器可以用它来识别和丢弃过时或乱序的指令包。
     */
    private int commandSequence;

    /**
     * 客户端发出此指令时的精确时间戳 (System.currentTimeMillis())。
     * 这是实现Sub-tick命中判定的核心数据。
     */
    private long timestamp;

    /**
     * 水平移动输入。
     * -1 代表向左，1 代表向右，0 代表静止。
     * 使用浮点数可以支持手柄的模拟摇杆。
     */
    private float moveInput;

    /**
     * 瞄准角度（以度为单位）。
     * 0度代表正右方，90度代表正上方，180度代表正左方。
     * 这是服务器进行精确射线检测（hitScan）的依据。
     */
    private float aimAngle;

    /**
     * 一个用比特位表示的按键状态掩码，非常高效。
     * 每一位代表一个按键是否被按下。
     * 例如:
     * - 第0位: 跳跃键
     * - 第1位: 开火键
     * - 第2位: 投掷炸弹键
     * - 第3位: 丢弃武器键
     */
    private byte actions;
}