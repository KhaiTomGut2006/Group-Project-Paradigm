// Member1 Firstname Lastname   6XXXXXX1
// Member2 Firstname Lastname   6XXXXXX2
// Member3 Firstname Lastname   6XXXXXX3
// Member4 Firstname Lastname   6XXXXXX4
// Member5 Firstname Lastname   6XXXXXX5
package Project3_XXX;

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
        "Member1 Firstname Lastname   6XXXXXX1",
        "Member2 Firstname Lastname   6XXXXXX2",
        "Member3 Firstname Lastname   6XXXXXX3",
        "Member4 Firstname Lastname   6XXXXXX4",
        "Member5 Firstname Lastname   6XXXXXX5"
    };

    // Player base stats (class bonuses and upgrades are added on top of these)
    public static final int PLAYER_BASE_HP = 100;
    public static final double PLAYER_BASE_SPEED = 1.5;     // world pixels per tick
    public static final int PLAYER_PICKUP_RADIUS = 40;      // world pixels; items inside fly to the player
    public static final int PLAYER_START_AMMO = 40;
    public static final int PLAYER_INVULNERABLE_MS = 450;   // short mercy time after being hit
    public static final int MEDKIT_HEAL = 40;
    public static final int FOOD_HEAL = 6;                  // instant heal when a FOOD item is picked up
    public static final int GRENADE_DAMAGE = 70;
    public static final int GRENADE_BLAST_RADIUS = 52;
    public static final int GRENADE_COOLDOWN_MS = 600;
    public static final int POISON_TICK_DAMAGE = 2;         // damage per poison pulse from toxic spit
    public static final int POISON_PULSE_MS = 500;

    // Run / wave rules (used by GamePanel)
    public static final int TIME_LIMIT_SECONDS = 300;       // survive 5:00 to win
    public static final int WAVE_SECONDS = 30;              // the wave number goes up every 30 s
    public static final int BOSS_EVERY_WAVES = 3;           // a boss arrives on wave 3, 6, 9 ...
    public static final int MAX_LIVE_MONSTERS = 120;        // cap so the frame rate holds
    public static final int ITEM_LIFETIME_SECONDS = 45;     // dropped loot disappears after this

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
        KNIFE("Knife",                 16,  280,  0.0, 1, 110,  0,   30,  true,  99, false, 0, 3, 1, null,                      0, "swing"),
        AXE("Axe",                     32,  520,  0.0, 1, 150,  0,   36,  true,  99, false, 0, 0, 1, null,                      0, "swing"),
        PISTOL("Pistol",               12,  330,  6.0, 1,   4,  0,  260,  false,  0, false, 0, 1, 0, "weapons/pistol_shoot.png",   5, "shoot_pistol"),
        REVOLVER("Revolver",           30,  520,  7.0, 1,   2,  1,  300,  false,  1, false, 0, 2, 0, "weapons/revolver_shoot.png", 9, "shoot_heavy"),
        SHOTGUN("Pump Shotgun",        11,  780,  6.0, 6,  28,  1,  160,  false,  0, false, 0, 3, 0, "weapons/shotgun_shoot.png", 10, "shotgun"),
        RIFLE("Assault Rifle",         12,  115,  8.0, 1,   6,  1,  320,  false,  0, false, 0, 1, 1, "weapons/rifle_shoot.png",    4, "shoot_rifle"),
        SNIPER("Sniper",               75, 1000, 11.0, 1,   0,  2,  560,  false,  5, false, 0, 0, 0, "weapons/sniper_shoot.png",  13, "shoot_heavy"),
        ROCKET_LAUNCHER("Rocket Launcher", 60, 1250, 4.5, 1, 0, 4,  380,  false,  0, true, 56, 2, 1, "weapons/rocket_shoot.png",  12, "rocket");

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

    /** The six weapons offered in the launcher's "starting weapon" JComboBox. */
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
        ZOMBIE_NORMAL("Zombie", 30, 0.62, 8, 10, "zombie1", 1.0, 12, 18, 700,
                null, 0, 0, 0, 1, 10,
                new Drop[]{new Drop(ItemType.SCRAPS, 55, 1, 2), new Drop(ItemType.WOOD, 40, 1, 2),
                    new Drop(ItemType.AMMO, 30, 4, 8), new Drop(ItemType.FOOD, 15, 1, 1)}),
        ZOMBIE_RUNNER("Runner", 18, 1.20, 6, 12, "zombie2", 1.0, 12, 18, 600,
                null, 0, 0, 0, 2, 6,
                new Drop[]{new Drop(ItemType.SCRAPS, 45, 1, 1), new Drop(ItemType.WOOD, 35, 1, 2),
                    new Drop(ItemType.AMMO, 35, 3, 6), new Drop(ItemType.FOOD, 20, 1, 1)}),
        ZOMBIE_TOXIC("Toxic Zombie", 42, 0.55, 9, 18, "zombie3", 1.0, 13, 19, 800,
                Projectile.TOXIC_SPIT, 3800, 170, 0, 2, 4,
                new Drop[]{new Drop(ItemType.ELECTRONICS, 45, 1, 1), new Drop(ItemType.BATTERY, 35, 1, 1),
                    new Drop(ItemType.MEDKIT, 12, 1, 1), new Drop(ItemType.SCRAPS, 30, 1, 1)}),
        BANDIT_SHOOTER("Bandit", 38, 0.85, 7, 25, "bandit", 1.0, 13, 19, 900,
                Projectile.ENEMY_BULLET, 3000, 230, 120, 3, 3,
                new Drop[]{new Drop(ItemType.STEEL, 60, 1, 2), new Drop(ItemType.GRENADE, 30, 1, 1),
                    new Drop(ItemType.ARMOR_VEST, 20, 1, 1), new Drop(ItemType.AMMO, 50, 6, 12)}),
        ZOMBIE_BOSS("Brute Boss", 650, 0.50, 22, 250, "zombie4", 1.6, 20, 30, 900,
                null, 0, 0, 0, BOSS_EVERY_WAVES, 0,
                new Drop[]{new Drop(ItemType.BLUEPRINT, 100, 1, 1), new Drop(ItemType.CHEST, 100, 1, 1),
                    new Drop(ItemType.STEEL, 100, 2, 3), new Drop(ItemType.ELECTRONICS, 60, 1, 2)});

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
        MEDIC("Medic", "+25 max HP, starts with 2 medkits that heal 50% more.",        25, 1.0,  1.0,  1.0, 1.0,  1.0,   0,  0,  2,  0,  0, 1.5),
        SCOUT("Scout", "+15% move speed and +10% fire rate.",                           0, 1.0,  1.15, 0.9, 1.0,  1.0,   0,  0,  0,  0,  0, 1.0),
        ENGINEER("Engineer", "All crafting costs 25% less, starts with spare parts.",   0, 1.0,  1.0,  1.0, 1.0,  0.75,  0,  0,  0,  0,  8, 1.0);

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
        public WeaponType startWeapon = WeaponType.PISTOL;
        public MapType map = MapType.WASTELAND_HIGHWAY;
        public int difficulty = 3;                  // 1..5 from JSlider
        public int volume = 70;                     // 0..100 from JSlider
        public boolean autoFire, showHitbox, soundOn = true, musicOn = true, doubleSpawn;
    }

    // ------------------------------------------------------------------ crafting
    /** What a recipe does when crafted. perLevel is the bonus added for each level bought. */
    public enum RecipeKind {
        WEAPON(0),          // unlocks UpgradeRecipe.weapon
        DAMAGE(0.12),       // +12% weapon damage per level
        FIRE_RATE(0.08),    // -8% weapon cooldown per level
        MAX_HP(20),         // +20 max HP per level (and heals that much)
        SPEED(0.08),        // +8% move speed per level
        ARMOR(0.08),        // 8% less damage taken per level
        MAGNET(0.25),       // +25% pickup radius per level
        MEDKIT(1);          // +1 medkit per craft

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

    /** The 12 workshop recipes: 5 weapon crafts, 6 stat upgrades and 1 consumable. */
    public static final List<UpgradeRecipe> RECIPES = Collections.unmodifiableList(createRecipes());

    private static List<UpgradeRecipe> createRecipes() {
        List<UpgradeRecipe> list = new ArrayList<>();
        list.add(new UpgradeRecipe("Craft Revolver", "Heavy sidearm, hits hard and pierces one enemy.",
                RecipeKind.WEAPON, 1, WeaponType.REVOLVER, WeaponType.PISTOL,
                ItemType.SCRAPS, 5, ItemType.WOOD, 2));
        list.add(new UpgradeRecipe("Craft Pump Shotgun", "Six pellets in a wide cone. Great at close range.",
                RecipeKind.WEAPON, 1, WeaponType.SHOTGUN, WeaponType.REVOLVER,
                ItemType.SCRAPS, 6, ItemType.WOOD, 4, ItemType.STEEL, 2));
        list.add(new UpgradeRecipe("Craft Assault Rifle", "Very fast automatic fire.",
                RecipeKind.WEAPON, 1, WeaponType.RIFLE, WeaponType.SHOTGUN,
                ItemType.SCRAPS, 8, ItemType.STEEL, 3, ItemType.ELECTRONICS, 2));
        list.add(new UpgradeRecipe("Craft Sniper", "Long range round that pierces 5 enemies.",
                RecipeKind.WEAPON, 1, WeaponType.SNIPER, WeaponType.RIFLE,
                ItemType.STEEL, 4, ItemType.ELECTRONICS, 3, ItemType.BLUEPRINT, 1));
        list.add(new UpgradeRecipe("Craft Rocket Launcher", "Explosive rockets with area damage.",
                RecipeKind.WEAPON, 1, WeaponType.ROCKET_LAUNCHER, WeaponType.SNIPER,
                ItemType.STEEL, 6, ItemType.ELECTRONICS, 4, ItemType.BATTERY, 2, ItemType.BLUEPRINT, 1));
        list.add(new UpgradeRecipe("Upgrade Weapon Damage", "+12% damage for every weapon.",
                RecipeKind.DAMAGE, 5, null, null, ItemType.SCRAPS, 4, ItemType.STEEL, 1));
        list.add(new UpgradeRecipe("Upgrade Fire Rate", "Weapons recover 8% faster.",
                RecipeKind.FIRE_RATE, 5, null, null, ItemType.SCRAPS, 3, ItemType.ELECTRONICS, 1));
        list.add(new UpgradeRecipe("Reinforce Body (Max HP)", "+20 max HP and heal 20.",
                RecipeKind.MAX_HP, 5, null, null, ItemType.WOOD, 3, ItemType.FOOD, 2));
        list.add(new UpgradeRecipe("Upgrade Boots Speed", "+8% movement speed.",
                RecipeKind.SPEED, 4, null, null, ItemType.SCRAPS, 3, ItemType.BATTERY, 1));
        list.add(new UpgradeRecipe("Craft Armor Plating", "Take 8% less damage.",
                RecipeKind.ARMOR, 5, null, null, ItemType.STEEL, 2, ItemType.ARMOR_VEST, 1));
        list.add(new UpgradeRecipe("Upgrade Loot Magnet", "+25% item pickup radius.",
                RecipeKind.MAGNET, 4, null, null, ItemType.ELECTRONICS, 2, ItemType.BATTERY, 1));
        list.add(new UpgradeRecipe("Craft Medkit", "One medkit (press E to heal).",
                RecipeKind.MEDKIT, 0, null, null, ItemType.FOOD, 2, ItemType.SCRAPS, 1));
        return list;
    }

    // ------------------------------------------------------------------ high scores
    /** One finished run, as shown in the launcher's high-score JTable. */
    public static class ScoreEntry {
        public String name, playerClass, map;
        public int score, kills, wave, seconds;
    }

    private static final String SCORE_FILE_NAME = "highscores.txt";

    /** The score file sits next to the sources when run from the project, otherwise next to the classes. */
    private static File scoreFile() {
        File srcDir = new File("src/main/java/Project3_XXX");
        if (srcDir.isDirectory()) {
            return new File(srcDir, SCORE_FILE_NAME);
        }
        File pkgDir = new File("Project3_XXX");
        if (pkgDir.isDirectory()) {
            return new File(pkgDir, SCORE_FILE_NAME);
        }
        return new File(SCORE_FILE_NAME);
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
