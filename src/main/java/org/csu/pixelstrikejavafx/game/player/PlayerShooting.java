package org.csu.pixelstrikejavafx.game.player;

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
        void onShot(double ox, double oy, double dx, double dy, double range, int damage, long ts);
    }

    public PlayerShooting(Player player) {
        this.player = player;

        // 初始化所有可用武器
        availableWeapons.add(new Pistol());
        availableWeapons.add(new MachineGun());
        availableWeapons.add(new Shotgun());
        availableWeapons.add(new GrenadeLauncher());
        availableWeapons.add(new Railgun()); // 添加射线枪

        // 默认装备第一把武器
        equipWeapon(0);
    }

    public void equipWeapon(int weaponIndex) {
        if (weaponIndex < 0 || weaponIndex >= availableWeapons.size()) {
            return;
        }
        this.currentWeaponIndex = weaponIndex;
        this.currentWeapon = availableWeapons.get(weaponIndex);
        this.currentWeapon.onEquip(player); // **调用 onEquip**
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
            if (!(currentWeapon instanceof MachineGun)) {
                currentWeapon.shoot(player);
            }
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