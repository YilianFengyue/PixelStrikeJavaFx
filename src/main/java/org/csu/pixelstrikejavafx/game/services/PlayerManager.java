package org.csu.pixelstrikejavafx.game.services;

import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.Entity;
import javafx.scene.effect.ColorAdjust;
import org.csu.pixelstrikejavafx.PixelGameApp;
import org.csu.pixelstrikejavafx.game.core.GameConfig;
import org.csu.pixelstrikejavafx.game.player.Player;
import org.csu.pixelstrikejavafx.game.player.RemoteAvatar;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerManager {

    private Player localPlayer;
    private final Map<Integer, PixelGameApp.RemotePlayer> remotePlayers = new ConcurrentHashMap<>();

    public Player createLocalPlayer(NetworkService networkService) {
        localPlayer = new Player(500, GameConfig.MAP_H - 211 - 128);
        localPlayer.getShootingSys().setShotReporter(
                (ox, oy, dx, dy, range, dmg, ts) -> networkService.sendShot(ox, oy, dx, dy, range, dmg, ts)
        );
        return localPlayer;
    }

    public void updateRemotePlayer(int id, double x, double y, boolean facing, String anim, String phase, double vx, double vy, boolean onGround, long seq) {
        PixelGameApp.RemotePlayer rp = remotePlayers.computeIfAbsent(id, key -> {
            System.out.println("Spawning new remote player with id: " + id);
            return createRemotePlayer(key, x, y, facing);
        });

        if (seq > 0 && seq <= rp.lastSeq) return; // 序列过滤
        rp.lastSeq = seq;

        rp.targetX = x;
        rp.targetY = y;
        rp.targetFacing = facing;
        rp.anim = anim;
        rp.phase = phase;
        rp.lastVX = vx;
        rp.lastVY = vy; // 修正：之前遗漏了vy的赋值
        rp.onGround = onGround;
        rp.lastUpdate = System.currentTimeMillis();
    }


    private PixelGameApp.RemotePlayer createRemotePlayer(int id, double x, double y, boolean facing) {
        RemoteAvatar avatar = new RemoteAvatar();
        Entity entity = FXGL.entityBuilder()
                .at(x, y)
                .view(avatar.view())
                .zIndex(999)
                .buildAndAttach();
        avatar.setFacingRight(facing);

        double hue = ((id * 57) % 360) / 360.0;
        var adj = new ColorAdjust(hue * 2 - 1, 0.2, 0, 0);
        avatar.view().setEffect(adj);

        return new PixelGameApp.RemotePlayer(entity, avatar);
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