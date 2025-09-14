package org.csu.pixelstrikejavafx.game.services;

import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.components.CollidableComponent;
import javafx.geometry.Point2D;
import javafx.scene.effect.ColorAdjust;
import org.csu.pixelstrikejavafx.PixelGameApp;
import org.csu.pixelstrikejavafx.content.CharacterRegistry;
import org.csu.pixelstrikejavafx.game.core.GameConfig;
import org.csu.pixelstrikejavafx.game.core.GameType;
import org.csu.pixelstrikejavafx.game.player.Player;
import org.csu.pixelstrikejavafx.game.player.RemoteAvatar;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerManager {

    private Player localPlayer;
    private final Map<Integer, PixelGameApp.RemotePlayer> remotePlayers = new ConcurrentHashMap<>();

    // ★ 修正 #1: 添加 charId 参数
    public Player createLocalPlayer(NetworkService networkService, String charId) {
        // 使用 CharacterRegistry 来获取角色定义
        localPlayer = new Player(500, GameConfig.MAP_H - 211 - 128, CharacterRegistry.get(charId));
        localPlayer.getShootingSys().setShotReporter(
                // ShotReporter 的签名保持不变，因为 weaponType 是从武器自身获取的
                (ox, oy, dx, dy, range, dmg, ts, weaponType) -> networkService.sendShot(ox, oy, dx, dy, range, dmg, ts, weaponType)
        );
        return localPlayer;
    }

    // ★ 修正 #2: 添加 charId 参数
    public void updateRemotePlayer(int id, double x, double y, boolean facing, String anim, String phase, double vx, double vy, boolean onGround, long seq, String charId) {
        PixelGameApp.RemotePlayer rp = remotePlayers.computeIfAbsent(id, key -> {
            System.out.println("Spawning new remote player with id: " + id + ", charId: " + charId);
            // 创建远程玩家时也传入 charId
            return createRemotePlayer(key, x, y, facing, charId);
        });

        // 序列过滤
        if (seq > 0 && seq <= rp.lastSeq) return;
        rp.lastSeq = seq;

        // 如果charId有变化，重建avatar
        if (charId != null && !charId.equals(rp.charId)) {
            rp.charId = charId;
            rp.avatar.rebuild(CharacterRegistry.get(charId));
        }

        rp.targetX = x;
        rp.targetY = y;
        rp.targetFacing = facing;
        rp.anim = anim;
        rp.phase = phase;
        rp.lastVX = vx;
        rp.lastVY = vy;
        rp.onGround = onGround;
        rp.lastUpdate = System.currentTimeMillis();
    }


    // ★ 修正 #2.1: createRemotePlayer 也接收 charId
    private PixelGameApp.RemotePlayer createRemotePlayer(int id, double x, double y, boolean facing, String charId) {
        // 使用 charId 来创建对应的 RemoteAvatar
        RemoteAvatar avatar = new RemoteAvatar(CharacterRegistry.get(charId));
        Entity entity = FXGL.entityBuilder()
                .type(GameType.PLAYER)
                .at(x, y)
                .view(avatar.view())
                .bbox(new com.almasb.fxgl.physics.HitBox(
                        new Point2D(Player.HB_OFF_X, Player.HB_OFF_Y),
                        com.almasb.fxgl.physics.BoundingShape.box(Player.HB_W, Player.HB_H)
                ))
                .with(new CollidableComponent(true))
                .zIndex(999)
                .buildAndAttach();
        avatar.setFacingRight(facing);

        double hue = ((id * 57) % 360) / 360.0;
        var adj = new ColorAdjust(hue * 2 - 1, 0.2, 0, 0);
        avatar.view().setEffect(adj);

        var remotePlayer = new PixelGameApp.RemotePlayer(entity, avatar);
        remotePlayer.charId = charId; // 记录 charId

        return remotePlayer;
    }

    public void removeRemotePlayer(int id) {
        PixelGameApp.RemotePlayer rp = remotePlayers.remove(id);
        if (rp != null && rp.entity != null) {
            rp.entity.removeFromWorld();
        }
    }

    public void clearAllRemotePlayers() {
        remotePlayers.values().forEach(rp -> {
            if (rp.entity != null) rp.entity.removeFromWorld();
        });
        remotePlayers.clear();
    }

    public Player getLocalPlayer() {
        return localPlayer;
    }

    public Map<Integer, PixelGameApp.RemotePlayer> getRemotePlayers() {
        return remotePlayers;
    }
}