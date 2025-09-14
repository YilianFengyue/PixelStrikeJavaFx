package org.csu.pixelstrikejavafx.game.player;

import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.components.CollidableComponent;
import com.almasb.fxgl.physics.PhysicsComponent;
import com.almasb.fxgl.physics.box2d.dynamics.BodyType;
import com.almasb.fxgl.physics.box2d.dynamics.FixtureDef;
import com.almasb.fxgl.texture.Texture;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.csu.pixelstrikejavafx.content.CharacterDef;
import org.csu.pixelstrikejavafx.content.CharacterRegistry;
import org.csu.pixelstrikejavafx.content.WeaponDef;
import org.csu.pixelstrikejavafx.content.WeaponRegistry;
import org.csu.pixelstrikejavafx.game.core.GameType;

import static com.almasb.fxgl.dsl.FXGL.*;

public class Player implements OnFireCallback {

    public enum State {
        IDLE, WALK, RUN, JUMP, FALL, DOUBLE_JUMP, SHOOTING, DIE
    }

    public enum AttackPhase {
        BEGIN, IDLE, END
    }

    public static final double PLAYER_W = 200;
    public static final double PLAYER_H = 200;
    public static final double HB_OFF_X = 80;
    public static final double HB_OFF_Y = 20;
    public static final double HB_W = 86;
    public static final double HB_H = 160;

    private static final double WALK_SPEED = 550.0;
    private static final double RUN_SPEED = 850.0;
    private static final double GROUND_ACCEL = 12000.0;
    private static final double AIR_ACCEL = 2500.0;
    private static final double JUMP_VY = 1200.0;
    private static final double DJUMP_VY = 1000.0;
    private static final long DOUBLE_TAP_MS = 200;
    private static final int ATTACK_BEGIN_MS = 200;
    private static final int ATTACK_END_MS = 400;
    private static final int HOLSTER_DELAY_MS = 120;
    private static final int RAISE_SKIP_THRESHOLD_MS = 220;

    private Entity entity;
    private PhysicsComponent physics;
    private PlayerAnimator animator;
    private final PlayerHealth health = new PlayerHealth(this);
    private final PlayerShooting shootingSys;
    private final CharacterDef ch;
    private WeaponDef currentWeapon;

    private Group weaponGroup = null;
    private Texture weaponTex = null;
    private double skinPivotX = 0, skinPivotY = 0;
    private double skinOffRX = 0, skinOffRY = 0;
    private double skinOffLX = 0, skinOffLY = 0;
    private double skinScale = 1.0;

    private State state = State.IDLE;
    private double vxTarget = 0.0;
    private double vxCurrent = 0.0;
    private double knockVX = 0.0;
    private boolean movingLeft = false;
    private boolean movingRight = false;
    private boolean running = false;
    private boolean onGround = false;
    private int jumpsUsed = 0;
    private boolean facingRight = true;
    private boolean shooting = false;
    private boolean dead = false;
    private AttackPhase attackPhase = AttackPhase.BEGIN;
    private long attackPhaseStartTime = 0;
    private boolean stopQueued = false;
    private long lastShootUpTime = 0;
    private long lastShootEndTime = 0;
    private long lastLeftTap = 0;
    private long lastRightTap = 0;

    public Player(double spawnX, double spawnY) {
        this(spawnX, spawnY, CharacterRegistry.get("ash"));
    }

    public Player(double spawnX, double spawnY, CharacterDef def) {
        this.ch = (def != null ? def : CharacterRegistry.get("ash"));
        createEntity(spawnX, spawnY);
        initAnimator(this.ch);
        shootingSys = new PlayerShooting(this);
        setWeapon(WeaponRegistry.get("pistol"));
    }

    private void createEntity(double x, double y) {
        physics = new PhysicsComponent();
        com.almasb.fxgl.physics.box2d.dynamics.BodyDef bd = new com.almasb.fxgl.physics.box2d.dynamics.BodyDef();
        bd.setType(BodyType.DYNAMIC);
        bd.setFixedRotation(true);
        physics.setBodyDef(bd);
        physics.setFixtureDef(new FixtureDef().friction(0.1f).restitution(0f).density(1.0f));

        entity = entityBuilder()
                .type(GameType.PLAYER)
                .at(x, y)
                .view(new Rectangle(PLAYER_W, PLAYER_H, Color.CRIMSON)) // Placeholder view
                .bbox(new com.almasb.fxgl.physics.HitBox(new Point2D(HB_OFF_X, HB_OFF_Y), com.almasb.fxgl.physics.BoundingShape.box(HB_W, HB_H)))
                .with(new CollidableComponent(true))
                .with(physics)
                .zIndex(1000)
                .buildAndAttach();
        entity.setProperty("playerRef", this);
    }

    private void initAnimator(CharacterDef def) {
        animator = new PlayerAnimator(this, def);
        if (animator.isAnimationLoaded()) {
            entity.getViewComponent().clearChildren();
            entity.getViewComponent().addChild(animator.getAnimatedTexture());
        }
    }

    public void update(double tpf) {
        if (movingLeft && !movingRight) {
            vxTarget = running ? -RUN_SPEED : -WALK_SPEED;
        } else if (movingRight && !movingLeft) {
            vxTarget = running ? RUN_SPEED : WALK_SPEED;
        } else {
            vxTarget = 0;
        }
        double accel = onGround ? GROUND_ACCEL : AIR_ACCEL;
        double diff = vxTarget - vxCurrent;
        double step = accel * tpf;
        if (Math.abs(diff) > step) {
            vxCurrent += Math.signum(diff) * step;
        } else {
            vxCurrent = vxTarget;
        }
        if (onGround && vxTarget == 0 && Math.abs(vxCurrent) > 0) {
            vxCurrent *= 0.75;
            if (Math.abs(vxCurrent) < 10) vxCurrent = 0;
        }

        double decel = 600 * tpf;
        if (Math.abs(knockVX) <= decel) {
            knockVX = 0;
        } else {
            knockVX -= Math.signum(knockVX) * decel;
        }
        physics.setVelocityX(vxCurrent + knockVX);

        if (dead) {
            state = State.DIE;
        } else if (shooting) {
            state = State.SHOOTING;
        } else {
            if (!onGround) {
                state = (physics.getVelocityY() < -50) ? (jumpsUsed >= 2 ? State.DOUBLE_JUMP : State.JUMP) : State.FALL;
            } else {
                state = (Math.abs(vxCurrent) < 1) ? State.IDLE : (running ? State.RUN : State.WALK);
            }
        }

        if (movingRight && !movingLeft) facingRight = true;
        else if (movingLeft && !movingRight) facingRight = false;

        updateAttackPhase();
        if (animator != null) animator.update();
        if (shootingSys != null) shootingSys.update(tpf);
        updateWeaponSkinTransform();
    }

    private void updateAttackPhase() {
        if (!shooting) return;
        long now = System.currentTimeMillis();
        long elapsed = now - attackPhaseStartTime;

        switch (attackPhase) {
            case BEGIN:
                if (elapsed >= ATTACK_BEGIN_MS) {
                    attackPhase = AttackPhase.IDLE;
                    attackPhaseStartTime = now;
                }
                break;
            case IDLE:
                if (stopQueued && (now - lastShootUpTime) >= HOLSTER_DELAY_MS) {
                    attackPhase = AttackPhase.END;
                    attackPhaseStartTime = now;
                }
                break;
            case END:
                if (elapsed >= ATTACK_END_MS) {
                    shooting = false;
                    stopQueued = false;
                    lastShootEndTime = now;
                    attackPhase = AttackPhase.BEGIN;
                }
                break;
        }
    }

    @Override
    public void onSuccessfulShot() {
        long now = System.currentTimeMillis();
        shooting = true;
        stopQueued = false;

        boolean skipBegin = (now - lastShootEndTime) <= RAISE_SKIP_THRESHOLD_MS;

        switch (attackPhase) {
            case END:
                if (skipBegin || (now - attackPhaseStartTime) < ATTACK_END_MS) {
                    attackPhase = AttackPhase.IDLE;
                } else {
                    attackPhase = AttackPhase.BEGIN;
                }
                attackPhaseStartTime = now;
                break;
            case BEGIN:
            case IDLE:
                break;
            default:
                attackPhase = skipBegin ? AttackPhase.IDLE : AttackPhase.BEGIN;
                attackPhaseStartTime = now;
                break;
        }
    }

    public void startShooting() {
        if (dead) return;
        if (shootingSys != null) shootingSys.startShooting();
    }

    public void stopShooting() {
        stopQueued = true;
        lastShootUpTime = System.currentTimeMillis();
        if (shootingSys != null) shootingSys.stopShooting();
    }

    public Point2D getMuzzleWorld() {
        double rx = 150, lx = 0, my = 0;
        if (ch != null && ch.sockets != null) {
            rx = ch.sockets.muzzleRightX;
            lx = ch.sockets.muzzleLeftX;
            my = ch.sockets.muzzleY;
        }
        if (currentWeapon != null && currentWeapon.muzzleOffsetDelta != null) {
            rx += currentWeapon.muzzleOffsetDelta.rightX;
            lx += currentWeapon.muzzleOffsetDelta.leftX;
            my += currentWeapon.muzzleOffsetDelta.y;
        }
        double offX = getFacingRight() ? rx : -lx;
        return new Point2D(entity.getCenter().getX() + offX, entity.getCenter().getY() + my);
    }

    private void refreshWeaponSkin() {
        if (weaponGroup != null) {
            entity.getViewComponent().removeChild(weaponGroup);
            weaponGroup = null; weaponTex = null;
        }
        WeaponDef w = getWeapon();
        if (w == null || w.skin == null || w.skin.image == null) return;

        try {
            weaponTex = getAssetLoader().loadTexture("weapons/" + w.skin.image);
            weaponTex.setSmooth(false);

            double scale = 1.0;
            if (w.skin.h != null && w.skin.h > 0) scale = w.skin.h / weaponTex.getImage().getHeight();
            else if (w.skin.w != null && w.skin.w > 0) scale = w.skin.w / weaponTex.getImage().getWidth();
            else if (w.skin.scale != null) scale = w.skin.scale;
            weaponTex.setScaleX(scale); weaponTex.setScaleY(scale);

            skinScale = scale;
            skinPivotX = w.skin.pivotX; skinPivotY = w.skin.pivotY;
            skinOffRX = w.skin.offsetRight.x; skinOffRY = w.skin.offsetRight.y;
            skinOffLX = w.skin.offsetLeft.x; skinOffLY = w.skin.offsetLeft.y;

            weaponTex.setTranslateX(-skinPivotX * scale);
            weaponTex.setTranslateY(-skinPivotY * scale);

            weaponGroup = new Group(weaponTex);
            entity.getViewComponent().addChild(weaponGroup);
        } catch (Exception e) {
            weaponGroup = null; weaponTex = null;
        }
    }

    private void updateWeaponSkinTransform() {
        if (weaponGroup == null) return;
        Point2D muzzle = getMuzzleWorld();
        double lx = muzzle.getX() - entity.getX();
        double ly = muzzle.getY() - entity.getY();
        double ox, oy;
        if (getFacingRight()) {
            ox = skinOffRX; oy = skinOffRY;
            weaponGroup.setScaleX(1);
        } else {
            ox = skinOffLX; oy = skinOffLY;
            weaponGroup.setScaleX(-1);
        }
        weaponGroup.setScaleY(1);
        weaponGroup.setTranslateX(lx + ox);
        weaponGroup.setTranslateY(ly + oy);
    }

    public void startMoveLeft() { long now = System.currentTimeMillis(); if (now - lastLeftTap < DOUBLE_TAP_MS) running = true; lastLeftTap = now; movingLeft = true; }
    public void stopMoveLeft() { movingLeft = false; if (!movingRight) running = false; }
    public void startMoveRight() { long now = System.currentTimeMillis(); if (now - lastRightTap < DOUBLE_TAP_MS) running = true; lastRightTap = now; movingRight = true; }
    public void stopMoveRight() { movingRight = false; if (!movingLeft) running = false; }
    public void jump() { if (jumpsUsed == 0 && onGround) { physics.setVelocityY(-JUMP_VY); jumpsUsed = 1; onGround = false; } else if (jumpsUsed == 1) { physics.setVelocityY(-DJUMP_VY); jumpsUsed = 2; } }
    public void setOnGround(boolean onGround) { if(this.onGround != onGround) { this.onGround = onGround; if (onGround) jumpsUsed = 0; } }
    public void die() { onDeath(); }
    public void revive() { health.reviveFull(); }
    public void reset(double x, double y) { entity.setPosition(x, y); physics.setVelocityX(0); physics.setVelocityY(0); movingLeft = movingRight = running = false; onGround = false; jumpsUsed = 0; vxCurrent = vxTarget = knockVX = 0; state = State.IDLE; facingRight = true; }
    public void applyHit(int damage, double knockX, double knockY) { applyKnockback(knockX, knockY); health.takeDamage(damage); }
    public void applyKnockback(double kx, double ky) { knockVX += kx; if (physics != null) physics.setVelocityY(physics.getVelocityY() - ky); }
    public void onDamaged(int amount) { /* TODO: visual effects */ }

    public void onDeath() {
        dead = true;
        if (physics != null) {
            physics.setVelocityX(0);
            physics.setVelocityY(0);
            physics.getBody().setActive(false);
        }
        shooting = false;
        stopQueued = false;
        attackPhase = AttackPhase.BEGIN;
        if (entity != null) {
            entity.setVisible(false);
            entity.getComponentOptional(CollidableComponent.class).ifPresent(c -> c.setValue(false));
        }
    }

    public void onRevived() {
        dead = false;
        if (entity != null) {
            entity.setVisible(true);
            entity.getComponentOptional(CollidableComponent.class).ifPresent(c -> c.setValue(true));
        }
        if (physics != null) {
            physics.getBody().setActive(true);
            physics.setVelocityX(0);
            physics.setVelocityY(0);
        }
        shooting = false; stopQueued = false; attackPhase = AttackPhase.BEGIN; movingLeft = movingRight = running = false; vxCurrent = vxTarget = knockVX = 0; jumpsUsed = 0; onGround = true; state = State.IDLE; if (entity != null) entity.translateY(1); setWeapon(WeaponRegistry.get("pistol"));
    }

    // Getters
    public Entity getEntity() { return entity; }
    public PhysicsComponent getPhysics() { return physics; }
    public PlayerHealth getHealth() { return health; }
    public State getState() { return state; }
    public boolean getFacingRight() { return facingRight; }
    public boolean isOnGround() { return onGround; }
    public AttackPhase getAttackPhase() { return attackPhase; }
    public WeaponDef getWeapon() { return (currentWeapon != null) ? currentWeapon : WeaponRegistry.get("pistol"); }

    public boolean isDead() {
        return dead;
    }

    public PlayerShooting getShootingSys() {
        return shootingSys;
    }

    public void setWeapon(WeaponDef def) { this.currentWeapon = def != null ? def : WeaponRegistry.get("pistol"); if(shootingSys != null) shootingSys.setWeapon(this.currentWeapon); refreshWeaponSkin(); }
    public String getNetAnim() { if (dead) return "DIE"; if (shooting) return "SHOOT"; switch (state) { case RUN: return "RUN"; case WALK: return "WALK"; case JUMP: return "JUMP"; case FALL: return "FALL"; default: return "IDLE"; } }
    public String getNetPhase() { switch (attackPhase) { case BEGIN: return "BEGIN"; case END: return "END"; default: return "IDLE"; } }
    public void setHealth(int hp) { if (health != null) health.setHp(hp); }
}