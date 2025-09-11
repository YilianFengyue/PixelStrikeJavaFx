// main/java/org/csu/pixelstrikejavafx/game/player/PlayerShooting.java
package org.csu.pixelstrikejavafx.game.player;

import org.csu.pixelstrikejavafx.game.weapon.Pistol;
import org.csu.pixelstrikejavafx.game.weapon.Weapon;

public class PlayerShooting {

    private final Player player;
    private Weapon currentWeapon; // 持有当前武器的引用
    private ShotReporter reporter;

    public interface ShotReporter {
        void onShot(double ox, double oy, double dx, double dy, double range, int damage, long ts);
    }

    public PlayerShooting(Player player) {
        this.player = player;
        // 默认装备手枪
        equipWeapon(new Pistol());
    }

    public void equipWeapon(Weapon weapon) {
        this.currentWeapon = weapon;
    }

    public void update(double tpf) {
        if (currentWeapon != null) {
            currentWeapon.update(tpf);
        }
    }

    public void startShooting() {
        if (currentWeapon != null) {
            currentWeapon.onFireStart();
            // 对于半自动武器，在按下时就尝试开火
            currentWeapon.shoot(player);
        }
    }

    public void stopShooting() {
        if (currentWeapon != null) {
            currentWeapon.onFireStop();
        }
    }

    public void setShotReporter(ShotReporter r) {
        this.reporter = r;
    }

    public ShotReporter getReporter() {
        return reporter;
    }
}