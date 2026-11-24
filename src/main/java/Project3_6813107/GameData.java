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
 * Game balance constants, enums (items, weapons, monsters, classes, maps, perks, recipes),
 * crafting recipes, and high-score persistence. Contains no Swing code.
 */
public final class GameData {

    private GameData() {}

    // ------------------------------------------------------------------ constants
    public static final int TILE = 32;
    public static final int SCALE = 2;
    public static final int GAME_W = 1100, GAME_H = 660, LAUNCHER_W = 900, LAUNCHER_H = 620;
    public static final int TICK_MS = 16;

    public static final String[] GROUP_MEMBERS = {
        "Tapatsawin Chaieua          6813105",
        "Thanapon Thanadunpremdet    6813106",
        "Tanawat Wongwad             6813107",
        "Natthanon Achachan          6813241"
    };

    public static final int PLAYER_BASE_HP = 100;
    public static final double PLAYER_BASE_SPEED = 2.25;
    public static final int PLAYER_PICKUP_RADIUS = 52;
    public static final int PLAYER_START_AMMO = 36;
    public static final int PLAYER_INVULNERABLE_MS = 300;
    public static final int CHAMBER_CLEAR_HEAL_PER_LEVEL = 3;
    public static final int GRENADE_DAMAGE = 70;
    public static final int GRENADE_BLAST_RADIUS = 52;
    public static final int GRENADE_COOLDOWN_MS = 420;
    public static final int POISON_TICK_DAMAGE = 3;
    public static final int POISON_PULSE_MS = 500;

    public static final int TOTAL_CHAMBERS = 15;
    public static final int BOSS_EVERY_CHAMBERS = 5;
    public static final int MAX_LIVE_MONSTERS = 120;
    public static final int ITEM_LIFETIME_SECONDS = 90;

    public static double monsterHpScale(int chamber, int difficulty) {
        double diffBase = 0.46 + 0.12 * difficulty;
        double actBoost = (chamber >= 6 ? 0.20 : 0.0) + (chamber >= 11 ? 0.25 : 0.0);
        return diffBase * (1.0 + 0.18 * Math.max(0, chamber - 1) + actBoost);
    }

    public static double monsterDamageScale(int chamber, int difficulty) {
        return (0.52 + 0.08 * difficulty) * (1.0 + 0.09 * Math.max(0, chamber - 1));
    }

    public static double monsterSpeedScale(int chamber) {
        return Math.min(1.18, 0.74 + 0.032 * Math.max(0, chamber - 1));
    }

    public static int chamberQuota(int chamber, ChamberType kind, int difficulty, boolean doubleSpawn) {
        int base = 5 + chamber * 2 + difficulty;
        if (kind == ChamberType.BOSS_CHAMBER) base = 4 + chamber / 2 + difficulty;
        else if (kind == ChamberType.ELITE_HAZARD) base += 3 + chamber / 2;
        else if (kind == ChamberType.FIELD_MEDIC) base = Math.max(5, base - 1);
        return doubleSpawn ? Math.min(80, base * 2) : Math.min(54, base);
    }

    public static int spawnBatchSize(int chamber, int difficulty, boolean doubleSpawn) {
        int n = 2 + chamber / 2 + difficulty / 3;
        return doubleSpawn ? Math.min(16, n * 2) : Math.min(9, n);
    }

    public static int msToTicks(int ms) {
        return Math.max(1, Math.round(ms / (float) TICK_MS));
    }

    // ------------------------------------------------------------------ items
    public enum ItemType {
        SCRAPS("Scraps", "items/scraps.png"),
        WOOD("Wood", "items/wood.png"),
        STEEL("Steel", "items/steel.png"),
        ELECTRONICS("Electronics", "items/electronics.png"),
        BATTERY("Battery", "items/battery.png"),
        BLUEPRINT("Blueprint", "items/blueprint.png"),
        MEDKIT("Medkit", "items/medkit.png"),
        AMMO("Ammo", "items/ammo.png"),
        FOOD("Food", "items/food.png"),
        GRENADE("Grenade", "items/grenade.png"),
        ARMOR_VEST("Armor Vest", "items/armor_vest.png"),
        CHEST("Supply Chest", "items/chest_open.png", 32);

        public final String displayName;
        public final String spritePath;
        public final int frameW;

        ItemType(String displayName, String spritePath) {
            this(displayName, spritePath, 16);
        }

        ItemType(String displayName, String spritePath, int frameW) {
            this.displayName = displayName;
            this.spritePath = spritePath;
            this.frameW = frameW;
        }
    }

    // ------------------------------------------------------------------ projectiles
    public enum Projectile {
        SMALL_BULLET(48, 80, 3),
        BIG_BULLET(16, 80, 3),
        SNIPER_ROUND(64, 80, 3),
        ROCKET(0, 80, 4),
        GRENADE(48, 96, 4),
        ENEMY_BULLET(32, 80, 3),
        TOXIC_SPIT(64, 96, 5),
        SLASH(-1, -1, 0);

        public final int sheetX, sheetY, radius;

        Projectile(int sheetX, int sheetY, int radius) {
            this.sheetX = sheetX;
            this.sheetY = sheetY;
            this.radius = radius;
        }
    }

    // ------------------------------------------------------------------ weapons
    public enum WeaponType {
        KNIFE("Survival Knife",          14, 230,  0.0, 1,  85, 0,  32, true,  99, false,  0, 3, 1, null,                         0, "swing"),
        AXE("Axe",                       26, 340,  0.0, 1, 110, 0,  36, true,  99, false,  0, 0, 1, null,                         0, "swing"),
        PISTOL("Pistol",                 18, 210,  8.8, 1,   4, 1, 270, false,  0, false,  0, 1, 0, "weapons/pistol_shoot.png",   5, "shoot_pistol"),
        REVOLVER("Revolver",             34, 330,  9.6, 1,   2, 1, 300, false,  1, false,  0, 2, 0, "weapons/revolver_shoot.png", 9, "shoot_heavy"),
        SHOTGUN("Pump Shotgun",          12, 480,  8.4, 6,  28, 1, 160, false,  0, false,  0, 3, 0, "weapons/shotgun_shoot.png", 10, "shotgun"),
        RIFLE("Assault Rifle",           14,  85, 10.5, 1,   6, 1, 320, false,  0, false,  0, 1, 1, "weapons/rifle_shoot.png",    4, "shoot_rifle"),
        SNIPER("Sniper",                 75, 680, 14.5, 1,   0, 2, 560, false,  5, false,  0, 0, 0, "weapons/sniper_shoot.png",  13, "shoot_heavy"),
        ROCKET_LAUNCHER("Rocket Launcher", 60, 820, 6.8, 1, 0, 4, 380, false,  0, true,  56, 2, 1, "weapons/rocket_shoot.png",  12, "rocket");

        public final String displayName, shootStrip, shootSound;
        public final int damage, cooldownMs, pellets, spreadDeg, ammoCost, range, pierce, blastRadius, iconCol, iconRow, shootFrames;
        public final double bulletSpeed;
        public final boolean melee, explosive;

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

        public String flickerStrip() {
            return switch (this) {
                case KNIFE -> "weapons/knife_flicker.png";
                case AXE -> "weapons/axe_flicker.png";
                case PISTOL -> "weapons/pistol_flicker.png";
                case REVOLVER -> "weapons/revolver_flicker.png";
                case SHOTGUN -> "weapons/shotgun_flicker.png";
                case RIFLE -> "weapons/rifle_flicker.png";
                case SNIPER -> "weapons/sniper_flicker.png";
                default -> "weapons/rocket_flicker.png";
            };
        }

        public Projectile projectile() {
            return switch (this) {
                case KNIFE, AXE -> Projectile.SLASH;
                case REVOLVER -> Projectile.BIG_BULLET;
                case SNIPER -> Projectile.SNIPER_ROUND;
                case ROCKET_LAUNCHER -> Projectile.ROCKET;
                default -> Projectile.SMALL_BULLET;
            };
        }
    }

    public static final WeaponType[] HOTBAR_WEAPONS = {
        WeaponType.KNIFE, WeaponType.PISTOL, WeaponType.REVOLVER,
        WeaponType.SHOTGUN, WeaponType.RIFLE, WeaponType.SNIPER, WeaponType.ROCKET_LAUNCHER
    };

    // ------------------------------------------------------------------ monsters
    public static final class Drop {
        public final ItemType item;
        public final int chancePercent, min, max;

        public Drop(ItemType item, int chancePercent, int min, int max) {
            this.item = item;
            this.chancePercent = chancePercent;
            this.min = min;
            this.max = max;
        }
    }

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

        public final String displayName, spriteName;
        public final int maxHp, damage, score, hitboxW, hitboxH, attackCooldownMs;
        public final int rangedCooldownMs, rangedRange, preferredDistance, firstWave, spawnWeight;
        public final double speed, drawScale;
        public final Projectile rangedProjectile;
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

        public static MonsterType randomForWave(int wave, Random rng) {
            int total = 0;
            for (MonsterType t : values()) {
                if (t.spawnWeight > 0 && wave >= t.firstWave) total += t.spawnWeight;
            }
            int roll = rng.nextInt(Math.max(1, total));
            for (MonsterType t : values()) {
                if (t.spawnWeight > 0 && wave >= t.firstWave && (roll -= t.spawnWeight) < 0) {
                    return t;
                }
            }
            return ZOMBIE_NORMAL;
        }

        public Drop[] getDrops() { return drops.clone(); }
        public String runStrip() { return "characters/" + spriteName + "_run.png"; }
        public String hitStrip() { return "characters/" + spriteName + "_hit.png"; }
        public String deathStrip() { return "characters/" + spriteName + "_death.png"; }
    }

    // ------------------------------------------------------------------ player classes
    public enum PlayerClass {
        SCAVENGER("Scavenger", "Bigger loot magnet and +20% drop chance.",              0, 1.0,  1.0,  1.0, 1.4,  1.0,  20,  0, 0, 0),
        SOLDIER("Soldier", "+15% damage, starts with extra ammo and a grenade.",        0, 1.15, 1.0,  1.0, 1.0,  1.0,   0, 40, 1, 0),
        MEDIC("Medic", "+25 max HP and restores +3 HP after clearing every chamber.",  25, 1.0,  1.0,  1.0, 1.0,  1.0,   0,  0, 0, 0),
        SCOUT("Scout", "+15% move speed and +10% fire rate.",                           0, 1.0,  1.15, 0.9, 1.0,  1.0,   0,  0, 0, 0),
        ENGINEER("Engineer", "All crafting costs 25% less, starts with spare parts.",   0, 1.0,  1.0,  1.0, 1.0,  0.75,  0,  0, 0, 4);

        public final String displayName, description;
        public final int bonusMaxHp, lootBonusPercent, bonusAmmo, startGrenades, startScraps;
        public final double damageMult, speedMult, cooldownMult, pickupMult, costMult;

        PlayerClass(String displayName, String description, int bonusMaxHp, double damageMult, double speedMult,
                    double cooldownMult, double pickupMult, double costMult, int lootBonusPercent,
                    int bonusAmmo, int startGrenades, int startScraps) {
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
            this.startGrenades = startGrenades;
            this.startScraps = startScraps;
        }

        @Override public String toString() { return displayName; }
    }

    // ------------------------------------------------------------------ maps
    public enum MapType {
        WASTELAND_HIGHWAY("Wasteland Highway", "A broken desert highway full of wrecked cars.", 1001L),
        SURVIVOR_CAMP("Survivor Camp", "A fenced tent town with market stalls and farm plots.", 2002L),
        ABANDONED_BUNKER("Abandoned Bunker", "Concrete rooms: dorms, lab, storage and a canteen.", 3003L),
        GAS_STATION("Gas Station", "A roadside fuel stop with pumps, crates and parked cars.", 4004L),
        QUARANTINE_ZONE("Quarantine Zone", "Chain-link compounds, warning signs and field tents.", 5005L);

        public final String displayName, description;
        public final long seed;

        MapType(String displayName, String description, long seed) {
            this.displayName = displayName;
            this.description = description;
            this.seed = seed;
        }

        @Override public String toString() { return displayName; }
    }

    // ------------------------------------------------------------------ settings
    public static class GameSettings {
        public String playerName = "Survivor";
        public PlayerClass playerClass = PlayerClass.SCAVENGER;
        public WeaponType startWeapon = WeaponType.KNIFE;
        public MapType map = MapType.WASTELAND_HIGHWAY;
        public int difficulty = 3;
        public int volume = 70;
        public boolean autoFire, showHitbox, soundOn = true, musicOn = true, doubleSpawn;
    }

    // ------------------------------------------------------------------ roguelite chambers and perks
    public enum ChamberType {
        SAFE_CAMP("Safe Camp Hub", "Prepare gear, talk to NPCs [B], and enter the Gate [F]"),
        TACTICAL_PERK("Tactical Perk", "Clear hostiles to choose 1 of 3 temporary Tactical Perks"),
        SALVAGE_VAULT("Salvage Vault", "Rich cache of permanent Scrap, Steel, and Electronics"),
        FIELD_MEDIC("Vitality Shrine", "Restores bonus HP on clear and drops extra Scraps & Ammo"),
        ELITE_HAZARD("Elite Hazard", "Dense traps & tough infected, 2x permanent Salvage + Perk"),
        BOSS_CHAMBER("Act Boss Lair", "Defeat the region Boss for Blueprint + Supply Chest"),
        VICTORY_EXTRACT("Extract to Camp", "Complete the expedition in triumph with bonus Salvage"),
        ENDLESS_ABYSS("Endless Abyss", "Continue deeper into Chamber 16+ with all current Perks");

        public final String displayName, description;

        ChamberType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
    }

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

        public final String displayName, description;

        TacticalPerk(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
    }

    // ------------------------------------------------------------------ crafting
    public enum RecipeKind {
        WEAPON(0), DAMAGE(0.12), FIRE_RATE(0.08), MAX_HP(20), SPEED(0.08),
        ARMOR(0.08), MAGNET(0.25), DEATH_DEFIANCE(1), BASE_TURRET(1),
        BASE_WALL(0.15), BASE_MEDIC(3), BASE_FARM(1), HEAL_NOW(65), MEDKIT(1);

        public final double perLevel;

        RecipeKind(double perLevel) { this.perLevel = perLevel; }
    }

    public static class UpgradeRecipe {
        public final String name, description;
        public final Map<ItemType, Integer> baseCost;
        public final RecipeKind kind;
        public final int maxLevel;
        public final WeaponType weapon, requiredWeapon;

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

        public static String costToText(Map<ItemType, Integer> cost) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<ItemType, Integer> e : cost.entrySet()) {
                if (sb.length() > 0) sb.append(" + ");
                sb.append(e.getValue()).append(' ').append(e.getKey().displayName);
            }
            return sb.toString();
        }

        @Override public String toString() { return name; }
    }

    public static final List<UpgradeRecipe> RECIPES = List.of(
        new UpgradeRecipe("Craft Pistol", "Unlock 9mm sidearm permanently for your Safe Camp Arsenal (uses Ammo).",
                RecipeKind.WEAPON, 1, WeaponType.PISTOL, WeaponType.KNIFE, ItemType.SCRAPS, 3, ItemType.WOOD, 1),
        new UpgradeRecipe("Craft Revolver", "Unlock heavy sidearm permanently; hits hard and pierces 1 enemy.",
                RecipeKind.WEAPON, 1, WeaponType.REVOLVER, WeaponType.PISTOL, ItemType.SCRAPS, 5, ItemType.WOOD, 2, ItemType.STEEL, 2),
        new UpgradeRecipe("Craft Pump Shotgun", "Unlock 6-pellet shotgun permanently. Devastating at close quarters.",
                RecipeKind.WEAPON, 1, WeaponType.SHOTGUN, WeaponType.REVOLVER, ItemType.SCRAPS, 8, ItemType.STEEL, 3, ItemType.ELECTRONICS, 1),
        new UpgradeRecipe("Craft Assault Rifle", "Unlock rapid-fire automatic rifle permanently.",
                RecipeKind.WEAPON, 1, WeaponType.RIFLE, WeaponType.SHOTGUN, ItemType.SCRAPS, 10, ItemType.STEEL, 5, ItemType.ELECTRONICS, 3, ItemType.BATTERY, 1),
        new UpgradeRecipe("Craft Sniper", "Unlock high-velocity piercing sniper permanently (pierces 5).",
                RecipeKind.WEAPON, 1, WeaponType.SNIPER, WeaponType.RIFLE, ItemType.STEEL, 7, ItemType.ELECTRONICS, 4, ItemType.BATTERY, 2, ItemType.BLUEPRINT, 1),
        new UpgradeRecipe("Craft Rocket Launcher", "Unlock explosive rocket launcher permanently.",
                RecipeKind.WEAPON, 1, WeaponType.ROCKET_LAUNCHER, WeaponType.SNIPER, ItemType.STEEL, 10, ItemType.ELECTRONICS, 6, ItemType.BATTERY, 3, ItemType.BLUEPRINT, 2),
        new UpgradeRecipe("Upgrade Weapon Damage", "Permanent +12% damage for all weapons across every run.",
                RecipeKind.DAMAGE, 5, null, null, ItemType.SCRAPS, 5, ItemType.STEEL, 2),
        new UpgradeRecipe("Upgrade Fire Rate", "Permanent +8% attack & reload speed for all weapons.",
                RecipeKind.FIRE_RATE, 5, null, null, ItemType.SCRAPS, 4, ItemType.ELECTRONICS, 2),
        new UpgradeRecipe("Reinforce Body (Max HP)", "Permanent +20 max HP for every expedition.",
                RecipeKind.MAX_HP, 5, null, null, ItemType.WOOD, 4, ItemType.SCRAPS, 4, ItemType.STEEL, 1),
        new UpgradeRecipe("Upgrade Boots Speed", "Permanent +8% movement speed inside chambers.",
                RecipeKind.SPEED, 4, null, null, ItemType.SCRAPS, 4, ItemType.BATTERY, 1),
        new UpgradeRecipe("Craft Armor Plating", "Permanent 8% damage reduction per level.",
                RecipeKind.ARMOR, 5, null, null, ItemType.STEEL, 3, ItemType.ARMOR_VEST, 1),
        new UpgradeRecipe("Awaken Death Defiance", "Revive inside a chamber with 45% HP when fatally hit (+1 charge/lv).",
                RecipeKind.DEATH_DEFIANCE, 2, null, null, ItemType.STEEL, 6, ItemType.ELECTRONICS, 4, ItemType.BATTERY, 2, ItemType.BLUEPRINT, 1),
        new UpgradeRecipe("Upgrade Loot Magnet", "Permanent +25% item pickup radius.",
                RecipeKind.MAGNET, 4, null, null, ItemType.ELECTRONICS, 2, ItemType.BATTERY, 1),
        new UpgradeRecipe("Recruit NPC Ally", "Permanent armed NPC companion who accompanies your runs (+1 NPC/lv).",
                RecipeKind.BASE_TURRET, 4, null, null, ItemType.SCRAPS, 6, ItemType.WOOD, 4, ItemType.STEEL, 3),
        new UpgradeRecipe("Fortify Hazard Suit", "Build camp defenses & reduce trap/boss damage taken by 15% per level.",
                RecipeKind.BASE_WALL, 3, null, null, ItemType.WOOD, 5, ItemType.SCRAPS, 4, ItemType.STEEL, 2),
        new UpgradeRecipe("Upgrade Vitality Siphon", "Hades-style recovery: heal +3 HP per level every time you clear a chamber.",
                RecipeKind.BASE_MEDIC, 5, null, null, ItemType.SCRAPS, 3, ItemType.WOOD, 2, ItemType.ELECTRONICS, 1),
        new UpgradeRecipe("Build Supply Depot", "+25 Starting Ammo & extra Grenades on every expedition.",
                RecipeKind.BASE_FARM, 3, null, null, ItemType.WOOD, 4, ItemType.SCRAPS, 5, ItemType.STEEL, 2)
    );

    // ------------------------------------------------------------------ high scores
    static String formatRunTime(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    public static class ScoreEntry {
        public String name, playerClass, map;
        public int score, kills, wave, seconds;
    }

    private static final String SCORE_FILE_NAME = "highscores.txt";

    private static File scoreFile() {
        String[] dirs = {"src/main/java/Project3_6813107", "Project3_6813107", System.getProperty("user.dir", ".")};
        for (String path : dirs) {
            File dir = new File(path);
            if (dir.isDirectory() && dir.canWrite()) return new File(dir, SCORE_FILE_NAME);
        }
        File homeDir = new File(System.getProperty("user.home", "."), ".last_camp_survivor");
        if (!homeDir.exists()) homeDir.mkdirs();
        return new File(homeDir, SCORE_FILE_NAME);
    }

    public static List<ScoreEntry> loadScores() {
        List<ScoreEntry> list = new ArrayList<>();
        File file = scoreFile();
        if (!file.isFile()) return list;
        try (BufferedReader in = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = in.readLine()) != null) {
                ScoreEntry score = parseScore(line);
                if (score != null) list.add(score);
            }
        } catch (IOException ex) {
            System.err.println("Could not read scores: " + ex.getMessage());
        }
        list.sort((a, b) -> Integer.compare(b.score, a.score));
        return list;
    }

    private static ScoreEntry parseScore(String line) {
        String[] parts = line.split("\t");
        if (parts.length != 7) return null;
        try {
            ScoreEntry score = new ScoreEntry();
            score.name = parts[0];
            score.playerClass = parts[1];
            score.map = parts[2];
            score.score = Integer.parseInt(parts[3]);
            score.kills = Integer.parseInt(parts[4]);
            score.wave = Integer.parseInt(parts[5]);
            score.seconds = Integer.parseInt(parts[6]);
            return score;
        } catch (NumberFormatException ex) {
            System.err.println("Skipping bad score line: " + line);
            return null;
        }
    }

    public static void saveScore(ScoreEntry e) {
        if (e == null) return;
        try (PrintWriter out = new PrintWriter(new FileWriter(scoreFile(), true))) {
            out.println(clean(e.name) + "\t" + clean(e.playerClass) + "\t" + clean(e.map) + "\t"
                    + e.score + "\t" + e.kills + "\t" + e.wave + "\t" + e.seconds);
        } catch (IOException ex) {
            System.err.println("Could not save score: " + ex.getMessage());
        }
    }

    private static String clean(String s) {
        return (s == null || s.isBlank()) ? "-" : s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim();
    }
}
