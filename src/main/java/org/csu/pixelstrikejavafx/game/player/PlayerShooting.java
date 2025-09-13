package org.csu.pixelstrikejavafx.game.player;

import org.csu.pixelstrikejavafx.game.core.GameConfig;
import org.csu.pixelstrikejavafx.game.weapon.*;

public class PlayerShooting {

    private final Player player;
    private int currentWeaponIndex = 0;
    private Weapon currentWeapon;
    private ShotReporter reporter;

    public interface ShotReporter {
        void onShot(double ox, double oy, double dx, double dy, double range, int damage, long ts, String weaponType);
    }

    public PlayerShooting(Player player) {
        this.player = player;
        // ★ 默认只装备手枪
        this.currentWeapon = new Pistol(GameConfig.Weapons.PISTOL);
        this.currentWeapon.onEquip(player);
    }

    public void equipWeapon(String weaponName) {
        System.out.println("Player equipping weapon: " + weaponName);
        switch (weaponName) {
            case "MachineGun":
                currentWeapon = new MachineGun(GameConfig.Weapons.MACHINE_GUN);
                break;
            case "Shotgun":
                currentWeapon = new Shotgun(GameConfig.Weapons.SHOTGUN);
                break;
            case "Railgun":
                currentWeapon = new Railgun(GameConfig.Weapons.RAILGUN);
                break;
            case "GrenadeLauncher":
                currentWeapon = new GrenadeLauncher(GameConfig.Weapons.GRENADE_LAUNCHER);
                break;
            default: // 包括 "Pistol" 或者任何未知类型，都切换回手枪
                currentWeapon = new Pistol(GameConfig.Weapons.PISTOL);
                break;
        }
        currentWeapon.onEquip(player);
    }

    public void update(double tpf) {
        if (currentWeapon != null) {
            currentWeapon.update(tpf);
        }
    }

    public void startShooting() {
        if (currentWeapon != null) {
            currentWeapon.onFireStart();
            currentWeapon.shoot(player, player);
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