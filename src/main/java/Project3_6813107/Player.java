// Tapatsawin Chaieua          6813105
// Thanapon Thanadunpremdet    6813106
// Tanawat Wongwad             6813107
// Natthanon Achachan          6813241
package Project3_6813107;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The survivor. Holds the inventory (EnumMap item -> count), owned weapons, upgrade levels,
 * and draws itself with the held weapon rotating towards the mouse.
 *
 * Input: GamePanel keeps the key/mouse state and every tick calls
 *   setMoveInput(up, down, left, right); setAim(mouseWorldX, mouseWorldY); update();
 * and, when the fire button is held (or auto-fire is on), shoot(targetX, targetY).
 *
 * Methods used by both the game thread and the Swing thread (inventory, crafting, weapons)
 * are synchronized so the workshop dialog never sees half-finished changes.
 */
public final class Player extends Entity {

    private static final int HURT_TICKS = 9;
    private static final int SWING_TICKS = 10;
    private static final int FLICKER_TICKS = 7 * 4;
    private static final int HINT_TICKS = 150;

    private final GameData.GameSettings settings;
    private final GameData.PlayerClass playerClass;
    private final EnumMap<GameData.ItemType, Integer> inventory = new EnumMap<>(GameData.ItemType.class);
    private final EnumSet<GameData.WeaponType> ownedWeapons = EnumSet.noneOf(GameData.WeaponType.class);
    private final EnumMap<GameData.RecipeKind, Integer> levels = new EnumMap<>(GameData.RecipeKind.class);
    private final Random random = new Random(213);
    private GameData.WeaponType currentWeapon;

    // input
    private boolean up, down, left, right;
    private double aimX, aimY;
    private boolean moving;

    // timers (in ticks)
    private int fireCooldown;
    private int grenadeCooldown;
    private int shootAnimTick = -1;   // -1 = not playing the shoot strip
    private int swingTick;            // > 0 while a melee swing is shown
    private double swingAngle;
    private int flickerTick;          // weapon glint after switching/crafting
    private int hurtTick;
    private int invulnerableTick;
    private int poisonTick;
    private int deathTick;
    private String hint;
    private int hintTick;

    // sprites
    private final BufferedImage[] idleFrames = AssetLoader.loadStrip("characters/player_idle.png", 6);
    private final BufferedImage[] runFrames = AssetLoader.loadStrip("characters/player_run.png", 8);
    private final BufferedImage[] hitFrames = AssetLoader.loadStrip("characters/player_hit.png", 3);
    private final BufferedImage[] deathFrames = AssetLoader.loadStrip("characters/player_death.png", 8);

    public Player(GameData.GameSettings settings, double startX, double startY) {
        super(startX, startY, 12, 20, GameData.PLAYER_BASE_HP, GameData.PLAYER_BASE_SPEED);
        this.settings = settings == null ? new GameData.GameSettings() : settings;
        this.playerClass = this.settings.playerClass == null ? GameData.PlayerClass.SCAVENGER : this.settings.playerClass;
        GameData.WeaponType start = this.settings.startWeapon == null ? GameData.WeaponType.PISTOL : this.settings.startWeapon;
        for (GameData.ItemType t : GameData.ItemType.values()) {
            inventory.put(t, 0);
        }
        for (GameData.RecipeKind k : GameData.RecipeKind.values()) {
            levels.put(k, 0);
        }
        inventory.put(GameData.ItemType.AMMO, GameData.PLAYER_START_AMMO + playerClass.bonusAmmo);
        inventory.put(GameData.ItemType.MEDKIT, playerClass.startMedkits);
        inventory.put(GameData.ItemType.GRENADE, playerClass.startGrenades);
        inventory.put(GameData.ItemType.SCRAPS, playerClass.startScraps);
        ownedWeapons.add(GameData.WeaponType.PISTOL);   // the sidearm with infinite ammo is always there
        ownedWeapons.add(start);
        currentWeapon = start;
        maxHp = GameData.PLAYER_BASE_HP + playerClass.bonusMaxHp;
        hp = maxHp;
        aimX = startX + 50;
        aimY = startY;
    }

    // ------------------------------------------------------------------ input
    /** Which movement keys are held (W, S, A, D). */
    public void setMoveInput(boolean up, boolean down, boolean left, boolean right) {
        this.up = up;
        this.down = down;
        this.left = left;
        this.right = right;
    }

    /** Where the mouse points, in world pixels. */
    public void setAim(double worldX, double worldY) {
        this.aimX = worldX;
        this.aimY = worldY;
    }

    // ------------------------------------------------------------------ simulation
    @Override
    public void update() {
        animTimer++;
        if (fireCooldown > 0) fireCooldown--;
        if (grenadeCooldown > 0) grenadeCooldown--;
        if (swingTick > 0) swingTick--;
        if (flickerTick > 0) flickerTick--;
        if (hurtTick > 0) hurtTick--;
        if (invulnerableTick > 0) invulnerableTick--;
        if (hintTick > 0 && --hintTick == 0) hint = null;
        if (shootAnimTick >= 0) {
            shootAnimTick++;
            if (currentWeapon.shootStrip == null || shootAnimTick >= currentWeapon.shootFrames * shootFrameTicks()) {
                shootAnimTick = -1;
            }
        }
        if (!alive) {
            deathTick++;
            return;
        }
        updatePoison();
        int dx = (right ? 1 : 0) - (left ? 1 : 0);
        int dy = (down ? 1 : 0) - (up ? 1 : 0);
        moving = dx != 0 || dy != 0;
        if (moving) {
            double len = Math.hypot(dx, dy);
            double s = getMoveSpeed();
            moveWithCollision(dx / len * s, dy / len * s);
        }
        facingLeft = aimX < x;
    }

    private void updatePoison() {
        if (poisonTick <= 0) {
            return;
        }
        poisonTick--;
        if (poisonTick % GameData.msToTicks(GameData.POISON_PULSE_MS) == 0) {
            hp -= GameData.POISON_TICK_DAMAGE;
            if (hp <= 0) {
                hp = 0;
                die();
            }
        }
    }

    /** Ticks per frame of the shoot strip: fast weapons play it faster so it fits the cooldown. */
    private int shootFrameTicks() {
        if (currentWeapon.shootFrames == 0) {
            return 1;
        }
        return Math.max(1, Math.min(3, getCooldownTicks(currentWeapon) / currentWeapon.shootFrames));
    }

    // ------------------------------------------------------------------ combat
    /**
     * Fires the current weapon at the target point. Returns the new bullets (a melee swing gives one
     * invisible slash). Empty list while on cooldown, dead, or out of ammo (then it falls back to the
     * pistol and shows a hint).
     */
    public synchronized List<Bullet> shoot(double targetX, double targetY) {
        List<Bullet> out = new ArrayList<>();
        if (!alive || fireCooldown > 0) {
            return out;
        }
        GameData.WeaponType w = currentWeapon;
        if (w.ammoCost > inventory.get(GameData.ItemType.AMMO)) {
            fallBackFromEmptyWeapon();
            return out;
        }
        double hx = getHandX(), hy = getHandY();
        double angle = Math.atan2(targetY - hy, targetX - hx);
        int dmg = getWeaponDamage(w);
        if (w.melee) {
            out.add(Bullet.melee(this, angle, w, dmg));
            swingTick = SWING_TICKS;
            swingAngle = angle;
        } else {
            double mx = hx + Math.cos(angle) * 14, my = hy + Math.sin(angle) * 14;
            for (int i = 0; i < w.pellets; i++) {
                double spread = Math.toRadians(w.spreadDeg);
                double offset;
                if (w.pellets > 1) {
                    // pellets fan out evenly across the cone with a little jitter
                    offset = -spread / 2 + spread * i / (w.pellets - 1) + (random.nextDouble() - 0.5) * spread * 0.15;
                } else {
                    offset = (random.nextDouble() - 0.5) * spread;
                }
                Bullet b = Bullet.shot(mx, my, angle + offset, w, dmg);
                b.setMap(map);
                out.add(b);
            }
            inventory.put(GameData.ItemType.AMMO, inventory.get(GameData.ItemType.AMMO) - w.ammoCost);
            shootAnimTick = 0;
        }
        swingAngle = angle;
        fireCooldown = getCooldownTicks(w);
        AssetLoader.playSound(w.shootSound);
        return out;
    }

    private void fallBackFromEmptyWeapon() {
        GameData.WeaponType fallback = GameData.WeaponType.PISTOL;
        currentWeapon = fallback;
        flickerTick = FLICKER_TICKS;
        showHint("Out of ammo - switched to " + fallback.displayName);
    }

    /** Throws a grenade (right click / G). Returns null if there is none or it is too soon. */
    public synchronized Bullet throwGrenade(double targetX, double targetY) {
        if (!alive || grenadeCooldown > 0) {
            return null;
        }
        if (inventory.get(GameData.ItemType.GRENADE) <= 0) {
            showHint("No grenades - bandits drop them");
            grenadeCooldown = GameData.msToTicks(GameData.GRENADE_COOLDOWN_MS);
            return null;
        }
        inventory.put(GameData.ItemType.GRENADE, inventory.get(GameData.ItemType.GRENADE) - 1);
        grenadeCooldown = GameData.msToTicks(GameData.GRENADE_COOLDOWN_MS);
        Bullet b = Bullet.grenade(getHandX(), getHandY(), targetX, targetY);
        b.setMap(map);
        AssetLoader.playSound("swing");
        return b;
    }

    /** Uses one medkit (E). Returns false if none or already at full health. */
    public synchronized boolean useMedkit() {
        if (!alive || hp >= maxHp) {
            return false;
        }
        if (inventory.get(GameData.ItemType.MEDKIT) <= 0) {
            showHint("No medkits - craft one in the workshop (B)");
            return false;
        }
        inventory.put(GameData.ItemType.MEDKIT, inventory.get(GameData.ItemType.MEDKIT) - 1);
        heal((int) Math.round(GameData.MEDKIT_HEAL * playerClass.healMult));
        poisonTick = 0;
        AssetLoader.playSound("pickup");
        return true;
    }

    public void heal(int amount) {
        if (alive) {
            hp = Math.min(maxHp, hp + Math.max(0, amount));
        }
    }

    /** Armour (and fortified base walls inside camp) reduces damage; right after a hit the player is briefly invulnerable. Returns damage taken. */
    @Override
    public int takeDamage(int amount) {
        if (!alive || amount <= 0 || invulnerableTick > 0) {
            return 0;
        }
        double reduction = getArmorFraction();
        if (map != null && map.getCampArea().contains((int) x, (int) y)) {
            int wallLv = getLevel(GameData.RecipeKind.BASE_WALL);
            if (wallLv > 0) {
                reduction = Math.min(0.85, reduction + wallLv * GameData.RecipeKind.BASE_WALL.perLevel);
            }
        }
        int dealt = Math.max(1, (int) Math.round(amount * (1.0 - reduction)));
        hp -= dealt;
        hurtTick = HURT_TICKS;
        invulnerableTick = GameData.msToTicks(GameData.PLAYER_INVULNERABLE_MS);
        AssetLoader.playSound("hurt");
        if (hp <= 0) {
            hp = 0;
            die();
        }
        return dealt;
    }

    /** Toxic spit: a few damage pulses over time (shown as green bubbles). */
    public void applyPoison(int ticks) {
        poisonTick = Math.max(poisonTick, ticks);
    }

    /** Removes active poison (Medical Tent or Camp Doctor treatment). */
    public void curePoison() {
        poisonTick = 0;
    }

    private void die() {
        alive = false;
        deathTick = 0;
        poisonTick = 0;
        AssetLoader.playSound("gameover");
    }

    /** True when the death animation has finished (GamePanel then ends the run). */
    public boolean isDeathAnimationDone() {
        return !alive && deathTick >= deathFrames.length * 7 + 30;
    }

    // ------------------------------------------------------------------ loot and inventory
    /** Takes a dropped item: adds it to the inventory (food also heals). Returns the "+2 Wood" label. */
    public synchronized String pickUp(DroppedItem item) {
        GameData.ItemType t = item.getType();
        item.markCollected();
        if (t == GameData.ItemType.CHEST) {
            return "";
        }
        if (t == GameData.ItemType.FOOD) {
            heal(GameData.FOOD_HEAL * item.getAmount());
        }
        inventory.put(t, inventory.get(t) + item.getAmount());
        AssetLoader.playSound("pickup");
        return item.getLabel();
    }

    public synchronized int getItemCount(GameData.ItemType t) {
        return inventory.get(t);
    }

    public synchronized void addItem(GameData.ItemType t, int amount) {
        if (t == GameData.ItemType.CHEST) {
            return;   // chests are opened in the world, never stored
        }
        inventory.put(t, Math.max(0, inventory.get(t) + amount));
    }

    /** A copy of the whole inventory (for the workshop's item list). */
    public synchronized Map<GameData.ItemType, Integer> getInventory() {
        return new EnumMap<>(inventory);
    }

    // ------------------------------------------------------------------ weapons
    public synchronized GameData.WeaponType getCurrentWeapon() {
        return currentWeapon;
    }

    public synchronized boolean ownsWeapon(GameData.WeaponType w) {
        return ownedWeapons.contains(w);
    }

    /** Owned weapons in hotkey order (1 = Knife ... 8 = Rocket Launcher). */
    public synchronized List<GameData.WeaponType> getOwnedWeapons() {
        return Collections.unmodifiableList(new ArrayList<>(ownedWeapons));
    }

    /** Ignored if the weapon is not owned or already in hand. */
    public synchronized void switchWeapon(GameData.WeaponType w) {
        if (w == null || !ownedWeapons.contains(w) || w == currentWeapon) {
            return;
        }
        currentWeapon = w;
        shootAnimTick = -1;
        swingTick = 0;
        flickerTick = FLICKER_TICKS;
        fireCooldown = Math.max(fireCooldown, 8);   // a short draw time
        AssetLoader.playSound("click");
    }

    /** Mouse wheel: next (+1) or previous (-1) owned weapon. */
    public synchronized void cycleWeapon(int direction) {
        List<GameData.WeaponType> list = new ArrayList<>(ownedWeapons);
        int i = list.indexOf(currentWeapon);
        int next = Math.floorMod(i + (direction >= 0 ? 1 : -1), list.size());
        switchWeapon(list.get(next));
    }

    // ------------------------------------------------------------------ crafting
    public synchronized int getLevel(GameData.RecipeKind kind) {
        return levels.get(kind);
    }

    /** Real cost now: base cost grows 50% per level already bought; the Engineer pays 25% less. Blueprints stay 1. */
    public synchronized Map<GameData.ItemType, Integer> getCost(GameData.UpgradeRecipe r) {
        Map<GameData.ItemType, Integer> cost = new EnumMap<>(GameData.ItemType.class);
        int level = r.kind == GameData.RecipeKind.WEAPON || r.maxLevel == 0 ? 0 : levels.get(r.kind);
        for (Map.Entry<GameData.ItemType, Integer> e : r.baseCost.entrySet()) {
            int n;
            if (e.getKey() == GameData.ItemType.BLUEPRINT) {
                n = e.getValue();
            } else {
                n = (int) Math.ceil(e.getValue() * (1.0 + 0.5 * level) * playerClass.costMult);
            }
            cost.put(e.getKey(), Math.max(1, n));
        }
        return cost;
    }

    private boolean isMaxed(GameData.UpgradeRecipe r) {
        if (r.kind == GameData.RecipeKind.WEAPON) {
            return ownedWeapons.contains(r.weapon);
        }
        return r.maxLevel > 0 && levels.get(r.kind) >= r.maxLevel;
    }

    private boolean hasItems(Map<GameData.ItemType, Integer> cost) {
        for (Map.Entry<GameData.ItemType, Integer> e : cost.entrySet()) {
            if (inventory.get(e.getKey()) < e.getValue()) {
                return false;
            }
        }
        return true;
    }

    /** Affordable, not maxed/owned, and the required weapon is owned. */
    public synchronized boolean canCraft(GameData.UpgradeRecipe r) {
        if (r == null || isMaxed(r)) {
            return false;
        }
        if (r.kind == GameData.RecipeKind.HEAL_NOW && hp >= maxHp && !isPoisoned()) {
            return false;
        }
        if (r.requiredWeapon != null && !ownedWeapons.contains(r.requiredWeapon)) {
            return false;
        }
        return hasItems(getCost(r));
    }

    /** Short text for the workshop table: "Ready", "Owned", "Lv 2/5", "MAX", "Need Blueprint", "Have 3" ... */
    public synchronized String getRecipeStatus(GameData.UpgradeRecipe r) {
        Map<GameData.ItemType, Integer> cost = getCost(r);
        if (r.kind == GameData.RecipeKind.WEAPON) {
            if (ownedWeapons.contains(r.weapon)) {
                return "Owned";
            }
            if (r.requiredWeapon != null && !ownedWeapons.contains(r.requiredWeapon)) {
                return "Need " + r.requiredWeapon.displayName;
            }
            Integer bp = cost.get(GameData.ItemType.BLUEPRINT);
            if (bp != null && inventory.get(GameData.ItemType.BLUEPRINT) < bp) {
                return "Need Blueprint";
            }
            return hasItems(cost) ? "Ready" : "Missing items";
        }
        if (r.kind == GameData.RecipeKind.HEAL_NOW) {
            if (hp >= maxHp && !isPoisoned()) {
                return "Full HP";
            }
            return hasItems(cost) ? "Ready" : "Missing items";
        }
        if (r.maxLevel == 0) {
            return "Have " + inventory.get(GameData.ItemType.MEDKIT);
        }
        int level = levels.get(r.kind);
        if (level >= r.maxLevel) {
            return "MAX";
        }
        return "Lv " + level + "/" + r.maxLevel;
    }

    /** Pays the cost and applies the recipe. Returns a line for the craft log, or null if it is not possible. */
    public synchronized String craft(GameData.UpgradeRecipe r) {
        if (!canCraft(r)) {
            return null;
        }
        Map<GameData.ItemType, Integer> cost = getCost(r);
        for (Map.Entry<GameData.ItemType, Integer> e : cost.entrySet()) {
            inventory.put(e.getKey(), inventory.get(e.getKey()) - e.getValue());
        }
        String paid = " (-" + GameData.UpgradeRecipe.costToText(cost).replace(" + ", ", -") + ")";
        AssetLoader.playSound("craft");
        switch (r.kind) {
            case WEAPON:
                ownedWeapons.add(r.weapon);
                currentWeapon = r.weapon;
                flickerTick = FLICKER_TICKS;
                return "Crafted " + r.weapon.displayName + paid;
            case MEDKIT:
                inventory.put(GameData.ItemType.MEDKIT, inventory.get(GameData.ItemType.MEDKIT) + 1);
                return "Crafted a Medkit" + paid;
            case HEAL_NOW:
                heal((int) GameData.RecipeKind.HEAL_NOW.perLevel);
                curePoison();
                return "Camp Doctor treated wounds (+65 HP)" + paid;
            case MAX_HP:
                levels.put(r.kind, levels.get(r.kind) + 1);
                int extra = (int) GameData.RecipeKind.MAX_HP.perLevel;
                maxHp += extra;
                heal(extra);
                return r.name + " -> Lv " + levels.get(r.kind) + paid;
            default:
                levels.put(r.kind, levels.get(r.kind) + 1);
                return r.name + " -> Lv " + levels.get(r.kind) + paid;
        }
    }

    // ------------------------------------------------------------------ derived stats (HUD getters)
    public GameData.PlayerClass getPlayerClass() {
        return playerClass;
    }

    public String getPlayerName() {
        return settings.playerName;
    }

    public synchronized int getAmmo() {
        return inventory.get(GameData.ItemType.AMMO);
    }

    public double getMoveSpeed() {
        return speed * playerClass.speedMult * (1.0 + GameData.RecipeKind.SPEED.perLevel * levels.get(GameData.RecipeKind.SPEED));
    }

    public double getPickupRadius() {
        return GameData.PLAYER_PICKUP_RADIUS * playerClass.pickupMult
                * (1.0 + GameData.RecipeKind.MAGNET.perLevel * levels.get(GameData.RecipeKind.MAGNET));
    }

    private double getArmorFraction() {
        return Math.min(0.6, GameData.RecipeKind.ARMOR.perLevel * levels.get(GameData.RecipeKind.ARMOR));
    }

    /** Damage reduction in percent (0..60). */
    public int getArmorPercent() {
        return (int) Math.round(getArmorFraction() * 100);
    }

    /** Damage of one hit/pellet of weapon w with all bonuses. */
    public int getWeaponDamage(GameData.WeaponType w) {
        double mult = playerClass.damageMult * (1.0 + GameData.RecipeKind.DAMAGE.perLevel * levels.get(GameData.RecipeKind.DAMAGE));
        return (int) Math.round(w.damage * mult);
    }

    /** Ticks between two shots of weapon w with all bonuses. */
    public int getCooldownTicks(GameData.WeaponType w) {
        double mult = playerClass.cooldownMult * (1.0 - GameData.RecipeKind.FIRE_RATE.perLevel * levels.get(GameData.RecipeKind.FIRE_RATE));
        return GameData.msToTicks((int) Math.round(w.cooldownMs * mult));
    }

    /** 0.0 = just fired, 1.0 = ready (for a reload bar). */
    public double getReloadProgress() {
        int total = getCooldownTicks(currentWeapon);
        return 1.0 - Math.min(1.0, fireCooldown / (double) total);
    }

    /** The class's bonus to every drop chance (pass to Monster.rollDrops). */
    public int getLootBonus() {
        return playerClass.lootBonusPercent;
    }

    public double getAimAngle() {
        return Math.atan2(aimY - getHandY(), aimX - getHandX());
    }

    public boolean isPoisoned() {
        return poisonTick > 0;
    }

    public boolean isMoving() {
        return moving;
    }

    /** True for one moment after being hurt (for a red screen flash). */
    public boolean isHurt() {
        return hurtTick > 0;
    }

    /** A short message for the HUD ("Out of ammo ..."), or null. */
    public String getHint() {
        return hint;
    }

    public void showHint(String text) {
        hint = text;
        hintTick = HINT_TICKS;
    }

    /** Where bullets leave the barrel (for muzzle effects). */
    public double getMuzzleX() {
        return getHandX() + Math.cos(getAimAngle()) * 14;
    }

    public double getMuzzleY() {
        return getHandY() + Math.sin(getAimAngle()) * 14;
    }

    private double getHandX() {
        return x + (facingLeft ? -2 : 2);
    }

    private double getHandY() {
        return y + 2;
    }

    // ------------------------------------------------------------------ drawing
    @Override
    public void draw(Graphics2D g2) {
        if (!alive) {
            BufferedImage frame = deathFrames[Math.min(deathFrames.length - 1, deathTick / 7)];
            drawFrame(g2, frame, 1.0, facingLeft);
            return;
        }
        // blink while invulnerable
        if (invulnerableTick > 0 && (invulnerableTick / 3) % 2 == 1) {
            return;
        }
        drawShadow(g2, 16);
        BufferedImage body;
        if (hurtTick > 0) {
            body = hitFrames[Math.min(hitFrames.length - 1, (HURT_TICKS - hurtTick) / 3)];
        } else if (moving) {
            body = runFrames[(animTimer / 4) % runFrames.length];
        } else {
            body = idleFrames[(animTimer / 8) % idleFrames.length];
        }
        double aim = swingTick > 0 ? currentSwingAngle() : getAimAngle();
        boolean weaponBehind = Math.sin(aim) < -0.35;   // aiming up: the gun is behind the body
        if (weaponBehind) {
            drawWeapon(g2, aim);
        }
        drawFrame(g2, body, 1.0, facingLeft);   // the hit strip itself starts with a white flash
        if (!weaponBehind) {
            drawWeapon(g2, aim);
        }
        if (poisonTick > 0) {
            drawPoisonBubbles(g2);
        }
    }

    /** During a melee swing the weapon sweeps across the arc. */
    private double currentSwingAngle() {
        double arc = Math.toRadians(currentWeapon.spreadDeg);
        double progress = 1.0 - swingTick / (double) SWING_TICKS;
        return swingAngle - arc / 2 + arc * progress;
    }

    /**
     * Rotates the weapon picture around its grip so it points at the aim angle. When aiming left it
     * is mirrored vertically, otherwise the gun would be upside down.
     */
    private void drawWeapon(Graphics2D g2, double aim) {
        GameData.WeaponType w = currentWeapon;
        BufferedImage img;
        if (shootAnimTick >= 0 && w.shootStrip != null) {
            BufferedImage[] strip = AssetLoader.loadStrip(w.shootStrip, w.shootFrames);
            img = strip[Math.min(strip.length - 1, shootAnimTick / shootFrameTicks())];
        } else {
            BufferedImage[] flicker = AssetLoader.loadStrip(w.flickerStrip(), 7);
            // frame 6 is the plain pose; frames 0-5 sweep a glint over the weapon after a switch
            img = flickerTick > 0 ? flicker[Math.min(6, (FLICKER_TICKS - flickerTick) / 4)] : flicker[6];
        }
        AffineTransform old = g2.getTransform();
        g2.translate(getHandX(), getHandY());
        if (w == GameData.WeaponType.AXE) {
            // the axe art stands upright (head on top): turn it a quarter so the head points at the aim
            g2.rotate(aim + Math.PI / 2);
            if (Math.cos(aim) < 0) {
                g2.scale(-1, 1);
            }
            g2.drawImage(img, -16, -26, null);
        } else {
            g2.rotate(aim);
            if (Math.cos(aim) < 0) {
                g2.scale(1, -1);
            }
            g2.drawImage(img, -12, -17, null);
        }
        g2.setTransform(old);
    }

    private void drawPoisonBubbles(Graphics2D g2) {
        Composite old = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f));
        g2.setColor(new Color(140, 220, 40));
        for (int i = 0; i < 3; i++) {
            double t = ((animTimer + i * 13) % 40) / 40.0;
            int bx = (int) (x - 6 + i * 6 + Math.sin(animTimer * 0.2 + i) * 2);
            int by = (int) (getFeetY() - 26 - t * 12);
            g2.fillOval(bx, by, 3, 3);
        }
        g2.setComposite(old);
    }
}
