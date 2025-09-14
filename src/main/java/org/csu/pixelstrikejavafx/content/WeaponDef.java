package org.csu.pixelstrikejavafx.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

/** 最小武器定义：稳定字段 + 可扩展 props（未知字段自动忽略） */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class WeaponDef {

    public String id;
    public String name;

    /** 秒/发（开火间隔） */
    public double shootInterval = 0.15;

    /** 每发伤害（服务器仍会权威裁决，这里用于客户端表现与上报） */
    public int damage = 10;

    /** 连发累积上抬角（度/发） */
    public double recoilKickDeg = 0.5;

    /** 枪口增量（叠加到角色 sockets） */
    public MuzzleDelta muzzleOffsetDelta = new MuzzleDelta();

    /** 预留扩展字段：散布/摆动/反冲/特效/弹药等 */
    public Map<String, Object> props;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class MuzzleDelta {
        public double rightX = 0;
        public double leftX  = 0;
        public double y      = 0;
    }
}
