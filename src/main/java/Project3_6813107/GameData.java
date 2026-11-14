// Tapatsawin Chaieua          6813105
// Thanapon Thanadunpremdet    6813106
// Tanawat Wongwad             6813107
// Natthanon Achachan          6813241
package Project3_6813107;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * All game data in one place: constants, enums that carry balance numbers, the crafting
 * recipes and the high-score file. No Swing code lives here, so every other class can use it.
 * To re-balance the game you only edit this file.
 */
public final class GameData {

    private GameData() {
        // only static members
    }

    // ------------------------------------------------------------------ constants
    public static final int TILE = 32;              // world tile size in pixels
    public static final int SCALE = 2;              // world is drawn at 2x (pixel-art, nearest neighbour)
    public static final int GAME_W = 1100, GAME_H = 660, LAUNCHER_W = 900, LAUNCHER_H = 620;
    public static final int TICK_MS = 16;           // one simulation step = 16 ms (about 60 updates per second)

    public static final String[] GROUP_MEMBERS = {
        "Tapatsawin Chaieua          6813105",
        "Thanapon Thanadunpremdet    6813106",
        "Tanawat Wongwad             6813107",
        "Natthanon Achachan          6813241"
    };

    // Player base stats (class bonuses and upgrades are added on top of these)
    public static final int PLAYER_BASE_HP = 100;
    public static final double PLAYER_BASE_SPEED = 2.25;    // fast Hades-style movement in world pixels per tick
    public static final int PLAYER_PICKUP_RADIUS = 52;      // world pixels; items inside fly to the player
    public static final int PLAYER_START_AMMO = 36;
    public static final int PLAYER_INVULNERABLE_MS = 300;   // short mercy time after being hit
    public static final int MEDKIT_HEAL = 30;
    public static final int CHAMBER_CLEAR_HEAL_PER_LEVEL = 3; // Hades-style HP restored per chamber clear
    public static final int FOOD_HEAL = 6;                  // legacy constant (manual heal disabled)
    public static final int GRENADE_DAMAGE = 70;
    public static final int GRENADE_BLAST_RADIUS = 52;
    public static final int GRENADE_COOLDOWN_MS = 420;
    public static final int POISON_TICK_DAMAGE = 3;         // damage per poison pulse from toxic spit
    public static final int POISON_PULSE_MS = 500;

    // Roguelite run & chamber rules (used by GamePanel)
    public static final int TOTAL_CHAMBERS = 15;            // 3 Acts x 5 chambers (chambers 5, 10, 15 are Bosses)
    public static final int ACT_CHAMBERS = 5;
    public static final int BOSS_EVERY_CHAMBERS = 5;
    public static final int TIME_LIMIT_SECONDS = 300;       // kept for legacy references
    public static final int WAVE_SECONDS = 30;
    public static final int DAY_CYCLE_SECONDS = 100;
    public static final int DAYLIGHT_SECONDS = 62;
    public static final int BOSS_EVERY_WAVES = 3;
    public static final int MAX_LIVE_MONSTERS = 120;        // cap so the frame rate holds
    public static final int ITEM_LIFETIME_SECONDS = 90;     // dropped loot lasts long enough to clear the chamber

    /** Monster health multiplier: approachable in Chamber 1, scaling progressively per chamber and act. */
    public static double monsterHpScale(int chamber, int difficulty) {
        double diffBase = 0.46 + 0.12 * difficulty;
        double actBoost = (chamber >= 6 ? 0.20 : 0.0) + (chamber >= 11 ? 0.25 : 0.0);
        return diffBase * (1.0 + 0.18 * Math.max(0, chamber - 1) + actBoost);
    }

    /** Monster damage multiplier: reduced in early chambers so new runs can learn patterns, fierce in deep acts. */
    public static double monsterDamageScale(int chamber, int difficulty) {
        return (0.52 + 0.08 * difficulty) * (1.0 + 0.09 * Math.max(0, chamber - 1));
    }

    /** Monster movement speed multiplier: calmer in Chambers 1-3, reaching full speed by Act 2 and frenzy in Act 3+. */
    public static double monsterSpeedScale(int chamber) {
        return Math.min(1.18, 0.74 + 0.032 * Math.max(0, chamber - 1));
    }

    /** Total hostile quota for a procedural chamber. Starts small in Chamber 1 and scales up per chamber. */
    public static int chamberQuota(int chamber, ChamberType kind, int difficulty, boolean doubleSpawn) {
        int base = 5 + chamber * 2 + difficulty;
        if (kind == ChamberType.BOSS_CHAMBER) {
            base = 4 + chamber / 2 + difficulty;
        } else if (kind == ChamberType.ELITE_HAZARD) {
            base += 3 + chamber / 2;
        } else if (kind == ChamberType.FIELD_MEDIC) {
            base = Math.max(5, base - 1);
        }
        return doubleSpawn ? Math.min(80, base * 2) : Math.min(54, base);
    }

    /** How many monsters one spawner wake-up adds. */
    public static int spawnBatchSize(int chamber, int difficulty, boolean doubleSpawn) {
        int n = 2 + chamber / 2 + difficulty / 3;
        if (doubleSpawn) {
            return Math.min(16, n * 2);
        }
        return Math.min(9, n);
    }

    /** Converts milliseconds to whole simulation ticks (at least 1). */
    public static int msToTicks(int ms) {
        return Math.max(1, Math.round(ms / (float) TICK_MS));
    }

    // ------------------------------------------------------------------ items
    /** Loot that can be picked up. CHEST is never stored: it opens and bursts into other items. */
    public enum ItemType {
        SCRAPS("Scraps", "items/scraps.png", 16, 16),
        WOOD("Wood", "items/wood.png", 16, 16),
        STEEL("Steel", "items/steel.png", 16, 16),
        ELECTRONICS("Electronics", "items/electronics.png", 16, 16),
        BATTERY("Battery", "items/battery.png", 16, 16),
        BLUEPRINT("Blueprint", "items/blueprint.png", 16, 16),
        MEDKIT("Medkit", "items/medkit.png", 16, 16),
        AMMO("Ammo", "items/ammo.png", 16, 16),
        FOOD("Food", "items/food.png", 16, 16),
        GRENADE("Grenade", "items/grenade.png", 16, 16),
        ARMOR_VEST("Armor Vest", "items/armor_vest.png", 16, 16),
        CHEST("Supply Chest", "items/chest_open.png", 32, 16);

        public final String displayName;
        public final String spritePath;   // horizontal animation strip
        public final int frameW, frameH;  // size of one frame in that strip

        ItemType(String displayName, String spritePath, int frameW, int frameH) {
            this.displayName = displayName;
            this.spritePath = spritePath;
            this.frameW = frameW;
            this.frameH = frameH;
        }

        /** Permanent salvage materials retained 100% when the player dies and returns to Safe Camp Hub. */
        public boolean isPermanentSalvage() {
            return this == SCRAPS || this == WOOD || this == STEEL
                    || this == ELECTRONICS || this == BATTERY || this == BLUEPRINT
                    || this == ARMOR_VEST;
        }
    }

    // ------------------------------------------------------------------ projectiles
    /** How a projectile looks: a 16x16 cell of weapons/projectiles.png. SLASH is the invisible melee hitbox. */
    public enum Projectile {
        SMALL_BULLET(48, 80, 3),
        BIG_BULLET(16, 80, 3),
        SNIPER_ROUND(64, 80, 3),
        ROCKET(0, 80, 4),
        GRENADE(48, 96, 4),
        ENEMY_BULLET(32, 80, 3),
        TOXIC_SPIT(64, 96, 5),
        SLASH(-1, -1, 0);

        public final int sheetX, sheetY;  // top-left of the 16x16 cell, -1 = no sprite
        public final int radius;          // collision radius in world pixels

        Projectile(int sheetX, int sheetY, int radius) {
            this.sheetX = sheetX;
            this.sheetY = sheetY;
            this.radius = radius;
        }
    }

    // ------------------------------------------------------------------ weapons
    /**
     * The 8 weapons. Speeds and ranges are in world pixels (per tick for speed).
     * pierce = how many EXTRA monsters one bullet may pass through (0 = stops at the first).
     * Melee weapons use range as reach and spreadDeg as the width of the swing arc.
     */
    public enum WeaponType {
        //          name              dmg  cdMs  speed pel spread ammo range melee pierce expl blast icon   strip                          frames sound
        KNIFE("Survival Knife",        14,  230,  0.0, 1,  85,  0,   32,  true,  99, false, 0, 3, 1, null,                      0, "swing"),
        AXE("Axe",                     26,  340,  0.0, 1, 110,  0,   36,  true,  99, false, 0, 0, 1, null,                      0, "swing"),
        PISTOL("Pistol",               18,  210,  8.8, 1,   4,  1,  270,  false,  0, false, 0, 1, 0, "weapons/pistol_shoot.png",   5, "shoot_pistol"),
        REVOLVER("Revolver",           34,  330,  9.6, 1,   2,  1,  300,  false,  1, false, 0, 2, 0, "weapons/revolver_shoot.png", 9, "shoot_heavy"),
        SHOTGUN("Pump Shotgun",        12,  480,  8.4, 6,  28,  1,  160,  false,  0, false, 0, 3, 0, "weapons/shotgun_shoot.png", 10, "shotgun"),
        RIFLE("Assault Rifle",         14,   85, 10.5, 1,   6,  1,  320,  false,  0, false, 0, 1, 1, "weapons/rifle_shoot.png",    4, "shoot_rifle"),
        SNIPER("Sniper",               75,  680, 14.5, 1,   0,  2,  560,  false,  5, false, 0, 0, 0, "weapons/sniper_shoot.png",  13, "shoot_heavy"),
        ROCKET_LAUNCHER("Rocket Launcher", 60,  820,  6.8, 1, 0, 4,  380,  false,  0, true, 56, 2, 1, "weapons/rocket_shoot.png",  12, "rocket");

        public final String displayName;
        public final int damage;
        public final int cooldownMs;
        public final double bulletSpeed;
        public final int pellets;
        public final int spreadDeg;
        public final int ammoCost;        // AMMO items used per shot (0 = infinite)
        public final int range;
        public final boolean melee;
        public final int pierce;
        public final boolean explosive;
        public final int blastRadius;
        public final int iconCol, iconRow;  // cell in weapons/weapons_sheet.png (32x32 cells)
        public final String shootStrip;     // recoil + muzzle flash animation, null for melee
        public final int shootFrames;
        public final String shootSound;

        WeaponType(String displayName, int damage, int cooldownMs, double bulletSpeed, int pellets, int spreadDeg,
                   int ammoCost, int range, boolean melee, int pierce, boolean explosive, int blastRadius,
                   int iconCol, int iconRow, String shootStrip, int shootFrames, String shootSound) {
            this.displayName = displayName;
            this.damage = damage;
            this.cooldownMs = cooldownMs;
            this.bulletSpeed = bulletSpeed;
            this.pellets = pellets;
            this.spreadDeg = spreadDeg;
            this.ammoCost = ammoCost;
            this.range = range;
            this.melee = melee;
            this.pierce = pierce;
            this.explosive = explosive;
            this.blastRadius = blastRadius;
            this.iconCol = iconCol;
            this.iconRow = iconRow;
            this.shootStrip = shootStrip;
            this.shootFrames = shootFrames;
            this.shootSound = shootSound;
        }

        /** The 7-frame glint strip shown when the weapon is drawn or crafted (also used as the idle pose). */
        public String flickerStrip() {
            switch (this) {
                case KNIFE: return "weapons/knife_flicker.png";
                case AXE: return "weapons/axe_flicker.png";
                case PISTOL: return "weapons/pistol_flicker.png";
                case REVOLVER: return "weapons/revolver_flicker.png";
                case SHOTGUN: return "weapons/shotgun_flicker.png";
                case RIFLE: return "weapons/rifle_flicker.png";
                case SNIPER: return "weapons/sniper_flicker.png";
                default: return "weapons/rocket_flicker.png";
            }
        }

        /** Which projectile sprite this gun fires. */
        public Projectile projectile() {
            switch (this) {
                case KNIFE:
                case AXE: return Projectile.SLASH;
                case REVOLVER: return Projectile.BIG_BULLET;
                case SNIPER: return Projectile.SNIPER_ROUND;
                case ROCKET_LAUNCHER: return Projectile.ROCKET;
                default: return Projectile.SMALL_BULLET;
            }
        }
    }

    /** The 7 survival weapons shown on the HUD hotbar (1 melee + 6 craftable firearms). */
    public static final WeaponType[] HOTBAR_WEAPONS = {
        WeaponType.KNIFE, WeaponType.PISTOL, WeaponType.REVOLVER,
        WeaponType.SHOTGUN, WeaponType.RIFLE, WeaponType.SNIPER, WeaponType.ROCKET_LAUNCHER
    };

    /** Legacy list kept for test harnesses. */
    public static final WeaponType[] START_WEAPONS = {
        WeaponType.KNIFE, WeaponType.AXE, WeaponType.PISTOL, WeaponType.REVOLVER, WeaponType.SHOTGUN, WeaponType.RIFLE
    };

    // ------------------------------------------------------------------ monsters
    /** One line of a drop table: chancePercent to drop between min and max of an item. */
    public static final class Drop {
        public final ItemType item;
        public final int chancePercent;
        public final int min, max;

        public Drop(ItemType item, int chancePercent, int min, int max) {
            this.item = item;
            this.chancePercent = chancePercent;
            this.min = min;
            this.max = max;
        }
    }

    /**
     * The 5 enemies. Sprites are characters/{spriteName}_run|_hit|_death.png (8, 3 and 8 frames of 32x32).
     * Ranged monsters fire their projectile every rangedCooldownMs when the player is within rangedRange;
     * preferredDistance > 0 means the monster tries to keep that distance instead of hugging the player.
     */
    public enum MonsterType {
        ZOMBIE_NORMAL("Zombie", 62, 1.68, 19, 12, "zombie1", 1.0, 12, 18, 310,
                null, 0, 0, 0, 1, 12,
                new Drop[]{new Drop(ItemType.SCRAPS, 18, 1, 1), new Drop(ItemType.WOOD, 9, 1, 1),
                    new Drop(ItemType.AMMO, 16, 3, 5)}),
        ZOMBIE_RUNNER("Runner", 44, 2.38, 17, 16, "zombie2", 1.0, 12, 18, 240,
                null, 0, 0, 0, 2, 8,
                new Drop[]{new Drop(ItemType.SCRAPS, 20, 1, 1), new Drop(ItemType.WOOD, 10, 1, 1),
                    new Drop(ItemType.AMMO, 18, 3, 5)}),
        ZOMBIE_TOXIC("Toxic Zombie", 88, 1.52, 22, 24, "zombie3", 1.0, 13, 19, 360,
                Projectile.TOXIC_SPIT, 980, 250, 0, 3, 6,
                new Drop[]{new Drop(ItemType.SCRAPS, 24, 1, 2), new Drop(ItemType.ELECTRONICS, 12, 1, 1),
                    new Drop(ItemType.BATTERY, 8, 1, 1)}),
        BANDIT_SHOOTER("Bandit", 82, 1.72, 23, 30, "bandit", 1.0, 13, 19, 380,
                Projectile.ENEMY_BULLET, 860, 290, 110, 5, 5,
                new Drop[]{new Drop(ItemType.SCRAPS, 28, 1, 2), new Drop(ItemType.STEEL, 14, 1, 1),
                    new Drop(ItemType.GRENADE, 9, 1, 1), new Drop(ItemType.ARMOR_VEST, 5, 1, 1),
                    new Drop(ItemType.AMMO, 22, 4, 7)}),
        ZOMBIE_BOSS("Brute Boss", 860, 1.75, 38, 350, "zombie4", 1.6, 20, 30, 320,
                Projectile.TOXIC_SPIT, 920, 320, 0, BOSS_EVERY_CHAMBERS, 0,
                new Drop[]{new Drop(ItemType.BLUEPRINT, 100, 1, 1), new Drop(ItemType.CHEST, 100, 1, 1),
                    new Drop(ItemType.STEEL, 65, 1, 2), new Drop(ItemType.ELECTRONICS, 50, 1, 1)});

        public final String displayName;
        public final int maxHp;
        public final double speed;        // world pixels per tick
        public final int damage;          // contact damage per bite
        public final int score;
        public final String spriteName;   // file prefix in resources/characters
        public final double drawScale;    // the boss is drawn larger than the 32x32 art
        public final int hitboxW, hitboxH;
        public final int attackCooldownMs;
        public final Projectile rangedProjectile; // null = melee only
        public final int rangedCooldownMs;
        public final int rangedRange;
        public final int preferredDistance;
        public final int firstWave;       // first wave this monster can appear in
        public final int spawnWeight;     // relative chance in the normal spawn mix (boss = 0, spawned on its own)
        private final Drop[] drops;

        MonsterType(String displayName, int maxHp, double speed, int damage, int score, String spriteName,
                    double drawScale, int hitboxW, int hitboxH, int attackCooldownMs,
                    Projectile rangedProjectile, int rangedCooldownMs, int rangedRange, int preferredDistance,
                    int firstWave, int spawnWeight, Drop[] drops) {
            this.displayName = displayName;
            this.maxHp = maxHp;
            this.speed = speed;
            this.damage = damage;
            this.score = score;
            this.spriteName = spriteName;
            this.drawScale = drawScale;
            this.hitboxW = hitboxW;
            this.hitboxH = hitboxH;
            this.attackCooldownMs = attackCooldownMs;
            this.rangedProjectile = rangedProjectile;
            this.rangedCooldownMs = rangedCooldownMs;
            this.rangedRange = rangedRange;
            this.preferredDistance = preferredDistance;
            this.firstWave = firstWave;
            this.spawnWeight = spawnWeight;
            this.drops = drops;
        }

        /**
         * Picks a random non-boss monster that is allowed in this wave, weighted by spawnWeight
         * (bosses are spawned separately every BOSS_EVERY_WAVES waves).
         */
        public static MonsterType randomForWave(int wave, Random rng) {
            int total = 0;
            for (MonsterType t : values()) {
                if (t.spawnWeight > 0 && wave >= t.firstWave) {
                    total += t.spawnWeight;
                }
            }
            int roll = rng.nextInt(Math.max(1, total));
            for (MonsterType t : values()) {
                if (t.spawnWeight > 0 && wave >= t.firstWave) {
                    roll -= t.spawnWeight;
                    if (roll < 0) {
                        return t;
                    }
                }
            }
            return ZOMBIE_NORMAL;
        }

        /** The drop table (a copy, so nobody can change the balance by accident). */
        public Drop[] getDrops() {
            return drops.clone();
        }

        public String runStrip() {
            return "characters/" + spriteName + "_run.png";
        }

        public String hitStrip() {
            return "characters/" + spriteName + "_hit.png";
        }

        public String deathStrip() {
            return "characters/" + spriteName + "_death.png";
        }
    }

    // ------------------------------------------------------------------ player classes
    /** The 5 starting professions (JRadioButtons in the launcher). Multipliers are 1.0 = no change. */
    public enum PlayerClass {
        //                                                                         maxHp dmg   speed cd   pickup cost loot ammo med gren parts heal
        SCAVENGER("Scavenger", "Bigger loot magnet and +20% drop chance.",              0, 1.0,  1.0,  1.0, 1.4,  1.0,  20,  0,  0,  0,  0, 1.0),
        SOLDIER("Soldier", "+15% damage, starts with extra ammo and a grenade.",        0, 1.15, 1.0,  1.0, 1.0,  1.0,   0, 40,  0,  1,  0, 1.0),
        MEDIC("Medic", "+25 max HP and restores +3 HP after clearing every chamber.",        25, 1.0,  1.0,  1.0, 1.0,  1.0,   0,  0,  0,  0,  0, 1.5),
        SCOUT("Scout", "+15% move speed and +10% fire rate.",                           0, 1.0,  1.15, 0.9, 1.0,  1.0,   0,  0,  0,  0,  0, 1.0),
        ENGINEER("Engineer", "All crafting costs 25% less, starts with spare parts.",   0, 1.0,  1.0,  1.0, 1.0,  0.75,  0,  0,  0,  0,  4, 1.0);

        public final String displayName;
        public final String description;
        public final int bonusMaxHp;
        public final double damageMult;
        public final double speedMult;
        public final double cooldownMult;   // < 1 = shoots faster
        public final double pickupMult;
        public final double costMult;       // < 1 = cheaper crafting
        public final int lootBonusPercent;  // added to every drop chance
        public final int bonusAmmo;
        public final int startMedkits;
        public final int startGrenades;
        public final int startScraps;
        public final double healMult;       // medkit healing multiplier

        PlayerClass(String displayName, String description, int bonusMaxHp, double damageMult, double speedMult,
                    double cooldownMult, double pickupMult, double costMult, int lootBonusPercent,
                    int bonusAmmo, int startMedkits, int startGrenades, int startScraps, double healMult) {
            this.displayName = displayName;
            this.description = description;
            this.bonusMaxHp = bonusMaxHp;
            this.damageMult = damageMult;
            this.speedMult = speedMult;
            this.cooldownMult = cooldownMult;
            this.pickupMult = pickupMult;
            this.costMult = costMult;
            this.lootBonusPercent = lootBonusPercent;
            this.bonusAmmo = bonusAmmo;
            this.startMedkits = startMedkits;
            this.startGrenades = startGrenades;
            this.startScraps = startScraps;
            this.healMult = healMult;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    // ------------------------------------------------------------------ maps
    /** The 5 arenas. Each layout is generated from a fixed seed, so a map always looks the same. */
    public enum MapType {
        WASTELAND_HIGHWAY("Wasteland Highway", "A broken desert highway full of wrecked cars.", 1001L),
        SURVIVOR_CAMP("Survivor Camp", "A fenced tent town with market stalls and farm plots.", 2002L),
        ABANDONED_BUNKER("Abandoned Bunker", "Concrete rooms: dorms, lab, storage and a canteen.", 3003L),
        GAS_STATION("Gas Station", "A roadside fuel stop with pumps, crates and parked cars.", 4004L),
        QUARANTINE_ZONE("Quarantine Zone", "Chain-link compounds, warning signs and field tents.", 5005L);

        public final String displayName;
        public final String description;
        public final long seed;

        MapType(String displayName, String description, long seed) {
            this.displayName = displayName;
            this.description = description;
            this.seed = seed;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    // ------------------------------------------------------------------ settings
    /** Everything the launcher hands to the game. Plain public fields with sane defaults. */
    public static class GameSettings {
        public String playerName = "Survivor";
        public PlayerClass playerClass = PlayerClass.SCAVENGER;
        public WeaponType startWeapon = WeaponType.KNIFE;
        public MapType map = MapType.WASTELAND_HIGHWAY;
        public int difficulty = 3;                  // 1..5 from JSlider
        public int volume = 70;                     // 0..100 from JSlider
        public boolean autoFire, showHitbox, soundOn = true, musicOn = true, doubleSpawn;
    }

    // ------------------------------------------------------------------ roguelite chambers and perks
    /** Procedural room reward/encounter type previewed above Hades-style exit doors. */
    public enum ChamberType {
        SAFE_CAMP("Safe Camp Hub", "Prepare gear, talk to NPCs [B], and enter the Gate [F]"),
        TACTICAL_PERK("Tactical Perk", "Clear hostiles to choose 1 of 3 temporary Tactical Perks"),
        SALVAGE_VAULT("Salvage Vault", "Rich cache of permanent Scrap, Steel, and Electronics"),
        FIELD_MEDIC("Vitality Shrine", "Restores bonus HP on clear and drops extra Scraps & Ammo"),
        ELITE_HAZARD("Elite Hazard", "Dense traps & tough infected, 2x permanent Salvage + Perk"),
        BOSS_CHAMBER("Act Boss Lair", "Defeat the region Boss for Blueprint + Supply Chest"),
        VICTORY_EXTRACT("Extract to Camp", "Complete the expedition in triumph with bonus Salvage"),
        ENDLESS_ABYSS("Endless Abyss", "Continue deeper into Chamber 16+ with all current Perks");

        public final String displayName;
        public final String description;

        ChamberType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
    }

    /** Temporary in-run boons chosen after clearing chambers (reset on death / new run). */
    public enum TacticalPerk {
        VAMPIRIC_BLADE("Chthonic Vitality", "Restores +3 HP per level after clearing every chamber and +10 Max HP."),
        HIGH_CALIBER("Hollow-Point Rounds", "+20% weapon damage and stronger knockback per level."),
        RAPID_TRIGGER("Hair Trigger", "+16% attack speed and faster weapon recovery per level."),
        RICOCHET_PIERCE("Piercing Rounds", "Bullets pierce +1 extra enemy and travel 15% faster per level."),
        EXPLOSIVE_KILL("Volatile Contagion", "Killed infected detonate for 26 blast damage per level."),
        TRAP_MASTER("Hazard Engineer", "-55% trap damage taken; traps deal +85% damage to monsters per level."),
        ADRENALINE_DASH("Tactical Dash", "SPACE/SHIFT dash cools down 20% faster and dashes farther per level."),
        VENOM_COATING("Bio-Toxin Edge", "Hits scorch enemies with bonus toxin damage (+6 per level)."),
        SQUAD_OVERDRIVE("Squad Overdrive", "NPC companions fire 30% faster and deal +35% damage per level."),
        SALVAGE_JACKPOT("Salvage Finder", "+25% multiplicative drop bonus on salvage materials & ammo per level.");

        public final String displayName;
        public final String description;

        TacticalPerk(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
    }

    // ------------------------------------------------------------------ crafting
    /** What a recipe does when crafted. perLevel is the bonus added for each level bought. */
    public enum RecipeKind {
        WEAPON(0),          // unlocks UpgradeRecipe.weapon permanently for the Camp Arsenal
        DAMAGE(0.12),       // +12% weapon damage per level
        FIRE_RATE(0.08),    // -8% weapon cooldown per level
        MAX_HP(20),         // +20 max HP per level (and heals that much)
        SPEED(0.08),        // +8% move speed per level
        ARMOR(0.08),        // 8% less damage taken per level
        MAGNET(0.25),       // +25% pickup radius per level
        DEATH_DEFIANCE(1),  // revives once with 45% HP per level when fatally hit
        BASE_TURRET(1),     // +1 permanent armed NPC squad ally accompanying runs
        BASE_WALL(0.15),    // -15% trap and boss damage taken per level + fortified camp perimeter
        BASE_MEDIC(3),      // +3 HP healed on every chamber clear per level (Hades style)
        BASE_FARM(1),       // +25 Starting Ammo & extra Grenades per run + periodic camp supplies
        HEAL_NOW(65),       // instant NPC doctor treatment (+65 HP & cures poison)
        MEDKIT(1);          // +1 medkit for the next run

        public final double perLevel;

        RecipeKind(double perLevel) {
            this.perLevel = perLevel;
        }
    }

    /** One row of the workshop JTable. The real cost (level scaling, class discount) comes from Player.getCost. */
    public static class UpgradeRecipe {
        public final String name;
        public final String description;
        public final Map<ItemType, Integer> baseCost;  // unmodifiable
        public final RecipeKind kind;
        public final int maxLevel;                     // 0 = can be crafted any number of times
        public final WeaponType weapon;                // only for kind == WEAPON
        public final WeaponType requiredWeapon;        // must be owned first (null = none)

        public UpgradeRecipe(String name, String description, RecipeKind kind, int maxLevel,
                             WeaponType weapon, WeaponType requiredWeapon, Object... costPairs) {
            this.name = name;
            this.description = description;
            this.kind = kind;
            this.maxLevel = maxLevel;
            this.weapon = weapon;
            this.requiredWeapon = requiredWeapon;
            EnumMap<ItemType, Integer> cost = new EnumMap<>(ItemType.class);
            for (int i = 0; i + 1 < costPairs.length; i += 2) {
                cost.put((ItemType) costPairs[i], (Integer) costPairs[i + 1]);
            }
            this.baseCost = Collections.unmodifiableMap(cost);
        }

        /** "3 Scraps + 1 Steel" style text for tables and logs. */
        public static String costToText(Map<ItemType, Integer> cost) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<ItemType, Integer> e : cost.entrySet()) {
                if (sb.length() > 0) {
                    sb.append(" + ");
                }
                sb.append(e.getValue()).append(' ').append(e.getKey().displayName);
            }
            return sb.toString();
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /** The workshop recipes: weapon crafts, stat upgrades, base buildings, and NPC medical services. */
    public static final List<UpgradeRecipe> RECIPES = Collections.unmodifiableList(createRecipes());

    private static List<UpgradeRecipe> createRecipes() {
        List<UpgradeRecipe> list = new ArrayList<>();
        list.add(new UpgradeRecipe("Craft Pistol", "Unlock 9mm sidearm permanently for your Safe Camp Arsenal (uses Ammo).",
                RecipeKind.WEAPON, 1, WeaponType.PISTOL, WeaponType.KNIFE,
                ItemType.SCRAPS, 3, ItemType.WOOD, 1));
        list.add(new UpgradeRecipe("Craft Revolver", "Unlock heavy sidearm permanently; hits hard and pierces 1 enemy.",
                RecipeKind.WEAPON, 1, WeaponType.REVOLVER, WeaponType.PISTOL,
                ItemType.SCRAPS, 5, ItemType.WOOD, 2, ItemType.STEEL, 2));
        list.add(new UpgradeRecipe("Craft Pump Shotgun", "Unlock 6-pellet shotgun permanently. Devastating at close quarters.",
                RecipeKind.WEAPON, 1, WeaponType.SHOTGUN, WeaponType.REVOLVER,
                ItemType.SCRAPS, 8, ItemType.STEEL, 3, ItemType.ELECTRONICS, 1));
        list.add(new UpgradeRecipe("Craft Assault Rifle", "Unlock rapid-fire automatic rifle permanently.",
                RecipeKind.WEAPON, 1, WeaponType.RIFLE, WeaponType.SHOTGUN,
                ItemType.SCRAPS, 10, ItemType.STEEL, 5, ItemType.ELECTRONICS, 3, ItemType.BATTERY, 1));
        list.add(new UpgradeRecipe("Craft Sniper", "Unlock high-velocity piercing sniper permanently (pierces 5).",
                RecipeKind.WEAPON, 1, WeaponType.SNIPER, WeaponType.RIFLE,
                ItemType.STEEL, 7, ItemType.ELECTRONICS, 4, ItemType.BATTERY, 2, ItemType.BLUEPRINT, 1));
        list.add(new UpgradeRecipe("Craft Rocket Launcher", "Unlock explosive rocket launcher permanently.",
                RecipeKind.WEAPON, 1, WeaponType.ROCKET_LAUNCHER, WeaponType.SNIPER,
                ItemType.STEEL, 10, ItemType.ELECTRONICS, 6, ItemType.BATTERY, 3, ItemType.BLUEPRINT, 2));
        list.add(new UpgradeRecipe("Upgrade Weapon Damage", "Permanent +12% damage for all weapons across every run.",
                RecipeKind.DAMAGE, 5, null, null, ItemType.SCRAPS, 5, ItemType.STEEL, 2));
        list.add(new UpgradeRecipe("Upgrade Fire Rate", "Permanent +8% attack & reload speed for all weapons.",
                RecipeKind.FIRE_RATE, 5, null, null, ItemType.SCRAPS, 4, ItemType.ELECTRONICS, 2));
        list.add(new UpgradeRecipe("Reinforce Body (Max HP)", "Permanent +20 max HP for every expedition.",
                RecipeKind.MAX_HP, 5, null, null, ItemType.WOOD, 4, ItemType.SCRAPS, 4, ItemType.STEEL, 1));
        list.add(new UpgradeRecipe("Upgrade Boots Speed", "Permanent +8% movement speed inside chambers.",
                RecipeKind.SPEED, 4, null, null, ItemType.SCRAPS, 4, ItemType.BATTERY, 1));
        list.add(new UpgradeRecipe("Craft Armor Plating", "Permanent 8% damage reduction per level.",
                RecipeKind.ARMOR, 5, null, null, ItemType.STEEL, 3, ItemType.ARMOR_VEST, 1));
        list.add(new UpgradeRecipe("Awaken Death Defiance", "Revive inside a chamber with 45% HP when fatally hit (+1 charge/lv).",
                RecipeKind.DEATH_DEFIANCE, 2, null, null,
                ItemType.STEEL, 6, ItemType.ELECTRONICS, 4, ItemType.BATTERY, 2, ItemType.BLUEPRINT, 1));
        list.add(new UpgradeRecipe("Upgrade Loot Magnet", "Permanent +25% item pickup radius.",
                RecipeKind.MAGNET, 4, null, null, ItemType.ELECTRONICS, 2, ItemType.BATTERY, 1));
        list.add(new UpgradeRecipe("Recruit NPC Ally", "Permanent armed NPC companion who accompanies your runs (+1 NPC/lv).",
                RecipeKind.BASE_TURRET, 4, null, null, ItemType.SCRAPS, 6, ItemType.WOOD, 4, ItemType.STEEL, 3));
        list.add(new UpgradeRecipe("Fortify Hazard Suit", "Build camp defenses & reduce trap/boss damage taken by 15% per level.",
                RecipeKind.BASE_WALL, 3, null, null, ItemType.WOOD, 5, ItemType.SCRAPS, 4, ItemType.STEEL, 2));
        list.add(new UpgradeRecipe("Upgrade Vitality Siphon",
                "Hades-style recovery: heal +3 HP per level every time you clear a chamber.",
                RecipeKind.BASE_MEDIC, 5, null, null,
                ItemType.SCRAPS, 3, ItemType.WOOD, 2, ItemType.ELECTRONICS, 1));
        list.add(new UpgradeRecipe("Build Supply Depot", "+25 Starting Ammo & extra Grenades on every expedition.",
                RecipeKind.BASE_FARM, 3, null, null, ItemType.WOOD, 4, ItemType.SCRAPS, 5, ItemType.STEEL, 2));
        return list;
    }

    // ------------------------------------------------------------------ high scores
    /** One finished run, as shown in the launcher's high-score JTable. */
    public static class ScoreEntry {
        public String name, playerClass, map;
        public int score, kills, wave, seconds;
    }

    private static final String SCORE_FILE_NAME = "highscores.txt";

    /** The score file sits next to the sources when run from the project, in cwd when writable, else ~/.last_camp_survivor. */
    private static File scoreFile() {
        File srcDir = new File("src/main/java/Project3_6813107");
        if (srcDir.isDirectory() && srcDir.canWrite()) {
            return new File(srcDir, SCORE_FILE_NAME);
        }
        File pkgDir = new File("Project3_6813107");
        if (pkgDir.isDirectory() && pkgDir.canWrite()) {
            return new File(pkgDir, SCORE_FILE_NAME);
        }
        File cwd = new File(System.getProperty("user.dir", "."));
        if (cwd.isDirectory() && cwd.canWrite()) {
            return new File(cwd, SCORE_FILE_NAME);
        }
        File homeDir = new File(System.getProperty("user.home", "."), ".last_camp_survivor");
        if (!homeDir.exists()) {
            homeDir.mkdirs();
        }
        return new File(homeDir, SCORE_FILE_NAME);
    }

    /** All saved scores, best first. Never throws: any problem gives an empty (or partial) list. */
    public static List<ScoreEntry> loadScores() {
        List<ScoreEntry> list = new ArrayList<>();
        File file = scoreFile();
        if (!file.isFile()) {
            return list;
        }
        try (BufferedReader in = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = in.readLine()) != null) {
                String[] parts = line.split("\t");
                if (parts.length != 7) {
                    continue;  // skip broken lines instead of failing the whole table
                }
                try {
                    ScoreEntry e = new ScoreEntry();
                    e.name = parts[0];
                    e.playerClass = parts[1];
                    e.map = parts[2];
                    e.score = Integer.parseInt(parts[3]);
                    e.kills = Integer.parseInt(parts[4]);
                    e.wave = Integer.parseInt(parts[5]);
                    e.seconds = Integer.parseInt(parts[6]);
                    list.add(e);
                } catch (NumberFormatException ex) {
                    System.err.println("Skipping bad score line: " + line);
                }
            }
        } catch (IOException ex) {
            System.err.println("Could not read scores: " + ex.getMessage());
        }
        list.sort((a, b) -> Integer.compare(b.score, a.score));
        return list;
    }

    /** Appends one run to the score file. Never throws. */
    public static void saveScore(ScoreEntry e) {
        if (e == null) {
            return;
        }
        try (PrintWriter out = new PrintWriter(new FileWriter(scoreFile(), true))) {
            out.println(clean(e.name) + "\t" + clean(e.playerClass) + "\t" + clean(e.map) + "\t"
                    + e.score + "\t" + e.kills + "\t" + e.wave + "\t" + e.seconds);
        } catch (IOException ex) {
            System.err.println("Could not save score: " + ex.getMessage());
        }
    }

    /** Tabs and line breaks would break the file format, so they become spaces. */
    private static String clean(String s) {
        if (s == null || s.isBlank()) {
            return "-";
        }
        return s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim();
    }
}
