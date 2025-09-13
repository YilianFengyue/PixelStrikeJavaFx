package org.csu.pixelstrikejavafx.game.player;

import org.csu.pixelstrikejavafx.game.core.GameConfig; // ★ 1. 确保导入 GameConfig
import org.csu.pixelstrikejavafx.game.weapon.*;
import java.util.ArrayList;
import java.util.List;

public class PlayerShooting {

    private final Player player;
    private final List<Weapon> availableWeapons = new ArrayList<>();
    private int currentWeaponIndex = 0;
    private Weapon currentWeapon;
    private ShotReporter reporter;

    public interface ShotReporter {
        void onShot(double ox, double oy, double dx, double dy, double range, int damage, long ts, String weaponType);
    }

    public PlayerShooting(Player player) {
        this.player = player;

        // ★ 2. 核心修改：初始化武器时，传入对应的配置对象 ★
        availableWeapons.add(new Pistol(GameConfig.Weapons.PISTOL));
        availableWeapons.add(new MachineGun(GameConfig.Weapons.MACHINE_GUN));
        availableWeapons.add(new Shotgun(GameConfig.Weapons.SHOTGUN));
        availableWeapons.add(new GrenadeLauncher(GameConfig.Weapons.GRENADE_LAUNCHER));
        availableWeapons.add(new Railgun(GameConfig.Weapons.RAILGUN));

        // 默认装备第一把武器
        equipWeapon(0);
    }

    public void equipWeapon(int weaponIndex) {
        if (weaponIndex < 0 || weaponIndex >= availableWeapons.size()) {
            return;
        }
        this.currentWeaponIndex = weaponIndex;
        this.currentWeapon = availableWeapons.get(weaponIndex);
        this.currentWeapon.onEquip(player);
        System.out.println("Equipped: " + this.currentWeapon.getClass().getSimpleName());
    }

    public void nextWeapon() {
        int nextIndex = (currentWeaponIndex + 1) % availableWeapons.size();
        equipWeapon(nextIndex);
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