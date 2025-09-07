package org.csu.pixelstrikejavafx.net.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponseDTO {
    private String token;
    private Long activeGameId; // 玩家当前所在的游戏ID，如果不在游戏中则为null
}