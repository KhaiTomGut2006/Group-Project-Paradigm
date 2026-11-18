// Tapatsawin Chaieua          6813105
// Thanapon Thanadunpremdet    6813106
// Tanawat Wongwad             6813107
// Natthanon Achachan          6813241
package Project3_6813107;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.zip.CRC32;

final class GameRegression {
    private static final long SEED = 6813107L;

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.US);
        AssetLoader.setSoundEnabled(false);
        AssetLoader.setMusicEnabled(false);
        for (GameData.PlayerClass profession : GameData.PlayerClass.values()) {
            compareRecipes(profession);
        }
        for (GameData.MapType region : GameData.MapType.values()) {
            printMap("hub", new GameMap(region, region.seed, true));
            for (int i = 0; i < 4; i++) {
                printMap("seed-" + i, new GameMap(region, SEED + i, false));
            }
            playChamber(region);
        }
        progressThroughPortals(false);
        progressThroughPortals(true);
        compareAssets();
        compareSavedScores();
    }

    private static GameData.GameSettings settings(GameData.MapType region) {
        GameData.GameSettings settings = new GameData.GameSettings();
        settings.map = region;
        settings.soundOn = false;
        settings.musicOn = false;
        return settings;
    }

    private static void compareRecipes(GameData.PlayerClass profession) {
        GameData.GameSettings settings = settings(GameData.MapType.WASTELAND_HIGHWAY);
        settings.playerClass = profession;
        Player player = new Player(settings, 0, 0);
        GameData.UpgradeRecipe pistol = GameData.RECIPES.stream()
                .filter(recipe -> recipe.weapon == GameData.WeaponType.PISTOL)
                .findFirst().orElseThrow();
        var initialInventory = player.getInventory();
        check(!player.canCraft(null) && player.craft(null) == null, "No selection must not craft");
        check(player.craft(pistol) == null && initialInventory.equals(player.getInventory()),
                "Unaffordable pistol must not spend items");
        var cost = player.getCost(pistol);
        cost.forEach(player::addItem);
        check(player.canCraft(pistol) && player.craft(pistol) != null, "Funded pistol must craft");
        check(player.getCurrentWeapon() == GameData.WeaponType.PISTOL && player.craft(pistol) == null,
                "Pistol must be equipped and craftable only once");
        check(initialInventory.equals(player.getInventory()), "Pistol must charge exactly its listed cost");

        for (GameData.ItemType item : GameData.ItemType.values()) {
            player.addItem(item, 1000);
        }
        for (GameData.UpgradeRecipe recipe : GameData.RECIPES) {
            if (recipe.kind == GameData.RecipeKind.HEAL_NOW) {
                check(!player.canCraft(recipe), "Full HP must block treatment");
                player.applyPoison(110);
            }
            for (int attempt = 0; attempt < Math.max(2, recipe.maxLevel + 1); attempt++) {
                System.out.printf("craft %s %s %d %s %s %s %s%n", profession, recipe.name, attempt,
                        player.getCost(recipe), player.getRecipeStatus(recipe), player.canCraft(recipe), player.craft(recipe));
            }
        }
        System.out.printf("craft state %s %s %s %s %d/%d %d%n", profession, player.getInventory(),
                player.getOwnedWeapons(), player.getEquippedPrimaryGun(),
                player.getHp(), player.getMaxHp(), player.getDeathDefianceLeft());
    }

    private static void printMap(String label, GameMap map) {
        CRC32 collision = new CRC32();
        for (int row = 0; row < GameMap.ROWS; row++) {
            for (int col = 0; col < GameMap.COLS; col++) {
                collision.update(map.isWalkableTile(col, row) ? 1 : 0);
            }
        }
        Point spawn = map.getPlayerSpawn();
        Point gate = map.getGatePosition();
        int solidHash = 1;
        for (Rectangle rectangle : map.getSolidRects()) {
            solidHash = 31 * solidHash + rectangle.hashCode();
        }
        System.out.printf("map %s %s %s %s %s %s %s %08x %d %s %s %.12f%n", label, map.getType(),
                map.getChamberLayoutName(), spawn, gate, map.getTraderPosition(), map.getSpawnPoints().size(),
                collision.getValue(), solidHash, pixels(map.getGroundImage()), pixels(map.getMinimap()),
                map.getChaseAngle(gate.x, gate.y, spawn.x, spawn.y));
    }

    private static GamePanel fixedRun(GameData.MapType region) throws Exception {
        GameData.GameSettings settings = settings(region);
        settings.playerClass = GameData.PlayerClass.ENGINEER;
        settings.difficulty = 4;
        settings.doubleSpawn = true;
        settings.showHitbox = true;
        GamePanel panel = new GamePanel(settings, null);
        panel.setSize(1100, 540);
        ((Random) read(panel, "rng")).setSeed(SEED);
        field(GamePanel.class, "runSeed").setLong(panel, SEED);
        // Remove nondeterministic constructor chamber setup by restarting from the hub.
        panel.returnToSafeCamp();
        for (GameData.ItemType item : GameData.ItemType.values()) {
            panel.getPlayer().addItem(item, 90);
        }
        for (GameData.UpgradeRecipe recipe : GameData.RECIPES) {
            if (recipe.weapon == GameData.WeaponType.PISTOL || recipe.kind == GameData.RecipeKind.BASE_TURRET) {
                check(panel.getPlayer().craft(recipe) != null, "Run loadout and ally upgrade");
            }
        }
        return panel;
    }

    private static void playChamber(GameData.MapType region) throws Exception {
        GamePanel panel = fixedRun(region);
        check(panel.isInSafeCamp(), "Run starts in the hub");
        printFrame("hub-" + region, panel);
        usePortal(panel, "TACTICAL_PERK");
        check(panel.getChamber() == 1, "F at camp gate must open chamber 1");
        panel.applyChosenPerk(GameData.TacticalPerk.values()[0]);
        printFrame("entry-" + region, panel);
        compareMerchant(panel, region);
        for (int tick = 0; tick < 240; tick++) {
            if (tick == 0) {
                key(panel, KeyEvent.VK_2, true);
                key(panel, KeyEvent.VK_D, true);
                mouse(panel, MouseEvent.BUTTON1, true);
            } else if (tick == 24) {
                key(panel, KeyEvent.VK_SPACE, true);
            } else if (tick == 40) {
                key(panel, KeyEvent.VK_D, false);
                key(panel, KeyEvent.VK_W, true);
            } else if (tick == 64) {
                mouse(panel, MouseEvent.BUTTON3, true);
            } else if (tick == 80) {
                key(panel, KeyEvent.VK_W, false);
                key(panel, KeyEvent.VK_P, true);
                int elapsed = panel.getElapsedSeconds();
                for (int pausedTick = 0; pausedTick < 70; pausedTick++) panel.updateGame();
                check(panel.getElapsedSeconds() == elapsed && panel.isPaused(), "Pause freezes chamber time");
                printFrame("paused-" + region, panel);
                key(panel, KeyEvent.VK_P, true);
                mouse(panel, MouseEvent.BUTTON1, true);
            } else if (tick == 100) {
                Player player = panel.getPlayer();
                player.takeDamage(player.getMaxHp() - 20);
                player.applyPoison(110);
            } else if (tick == 160) {
                mouse(panel, MouseEvent.BUTTON1, false);
            }
            panel.updateGame();
            if (tick % 20 == 19) printFrame("tick-" + region + "-" + tick, panel);
        }
    }

    private static void compareMerchant(GamePanel panel, GameData.MapType region) {
        List<GamePanel.FieldShopOffer> offers = panel.getFieldShopOffers();
        check(offers.size() == 3, "Merchant should have three offers");
        Player player = panel.getPlayer();
        for (int index = 0; index < offers.size(); index++) {
            GamePanel.FieldShopOffer offer = offers.get(index);
            int scraps = player.getItemCount(GameData.ItemType.SCRAPS);
            if (offer.kind == GamePanel.FieldShopOffer.OfferKind.HEAL) {
                check(!offer.sold && panel.buyFieldShopOffer(index).equals("Health is already full."),
                        "Merchant must reject healing at full HP");
                player.takeDamage(21);
            }
            String feedback = panel.buyFieldShopOffer(index);
            check(offer.sold && player.getItemCount(GameData.ItemType.SCRAPS) == scraps - offer.costScraps,
                    "Successful merchant purchase must charge once");
            String repeat = panel.buyFieldShopOffer(index);
            check(player.getItemCount(GameData.ItemType.SCRAPS) == scraps - offer.costScraps,
                    "Sold offers cannot charge again");
            System.out.printf("merchant %s %s %d %s %s%n", region, offer.kind, offer.costScraps, feedback, repeat);
        }
    }

    private static void progressThroughPortals(boolean endless) throws Exception {
        GamePanel panel = fixedRun(GameData.MapType.ABANDONED_BUNKER);
        usePortal(panel, "TACTICAL_PERK");
        for (int chamber = 1; chamber <= 15; chamber++) {
            check(panel.getChamber() == chamber, "Expected chamber " + chamber);
            if (chamber % 5 == 0) {
                check(monsters(panel).stream().anyMatch(monster -> monster.getType() == GameData.MonsterType.ZOMBIE_BOSS),
                        "Expected boss at chamber " + chamber);
                printFrame("boss-" + chamber, panel);
            }
            completeCombat(panel);
            printFrame("cleared-" + chamber, panel);
            if (chamber < 15) usePortal(panel, null);
        }
        usePortal(panel, endless ? "ENDLESS_ABYSS" : "VICTORY_EXTRACT");
        if (endless) {
            check(panel.getChamber() == 16 && !panel.isGameOver(), "Endless portal must open chamber 16");
            printFrame("endless", panel);
            panel.getPlayer().takeDamage(10000);
            for (int tick = 0; tick < 300 && !panel.isGameOver(); tick++) panel.updateGame();
            check(panel.isGameOver(), "Player death must end run");
            printFrame("death", panel);
        } else {
            check(panel.isGameOver() && (boolean) read(panel, "won"), "Extraction must win the run");
            printFrame("victory", panel);
        }
        int salvage = panel.getPlayer().getItemCount(GameData.ItemType.SCRAPS);
        panel.returnToSafeCamp();
        check(panel.isInSafeCamp() && panel.getRunNumber() == 3 && !panel.isGameOver()
                && panel.getPlayer().getItemCount(GameData.ItemType.SCRAPS) == salvage
                && panel.getPlayer().ownsWeapon(GameData.WeaponType.PISTOL), "Camp reset keeps permanent salvage and unlocks");
        printFrame("return-" + endless, panel);
    }

    private static void completeCombat(GamePanel panel) throws Exception {
        // Skip fighting in this scenario so both end routes exercise the real clear and portal rules.
        field(GamePanel.class, "chamberMonstersRemaining").setInt(panel, 0);
        for (Monster monster : monsters(panel)) monster.takeHit(10000, 0, 0);
        panel.updateGame();
        check(panel.isChamberCleared(), "Killing all hostiles must clear the chamber");
    }

    private static void usePortal(GamePanel panel, String type) throws Exception {
        List<?> doors = (List<?>) read(panel, "exitDoors");
        for (Object door : doors) {
            if (type == null || read(door, "type").toString().equals(type)) {
                panel.getPlayer().setPosition((double) read(door, "x"), (double) read(door, "y"));
                key(panel, KeyEvent.VK_F, true);
                panel.updateGame();
                return;
            }
        }
        throw new AssertionError("Portal missing: " + type);
    }

    private static void printFrame(String label, GamePanel panel) throws Exception {
        Player player = panel.getPlayer();
        BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        panel.paint(graphics);
        graphics.dispose();
        List<String> doors = new ArrayList<>();
        for (Object door : (List<?>) read(panel, "exitDoors")) {
            doors.add(read(door, "label") + "@" + read(door, "x") + "," + read(door, "y"));
        }
        System.out.printf("frame %s %d %s %d/%d %d %d %s %d %s %.6f %.6f %s %s %s %d %d %s %s%n", label,
                panel.getChamber(), panel.getCurrentChamberType(), player.getHp(), player.getMaxHp(),
                panel.getScore(), panel.getKills(), player.getCurrentWeapon(), panel.getHostilesRemaining(),
                player.getInventory(), player.getX(), player.getY(), player.isPoisoned(), player.isDashing(),
                player.getRunPerks(), ((List<?>) read(panel, "bullets")).size(), ((List<?>) read(panel, "drops")).size(),
                doors, pixels(image));
    }

    private static void compareAssets() {
        var strip = AssetLoader.loadStrip("characters/player_run.png", 6);
        var grid = AssetLoader.loadGrid("weapons/weapons_sheet.png", 32, 32);
        check(AssetLoader.canLoadSound("craft") && AssetLoader.canLoadSound("bgm"), "WAV files must decode");
        System.out.printf("assets %s %s %s %s%n", pixels(strip[0]), pixels(strip[strip.length - 1]),
                pixels(grid[0]), pixels(grid[grid.length - 1]));
    }

    private static void compareSavedScores() throws Exception {
        java.nio.file.Files.writeString(java.nio.file.Path.of("highscores.txt"),
                "Old Save\tEngineer\tWasteland\t120\t8\t2\t51\ninvalid row\n");
        GameData.ScoreEntry recent = new GameData.ScoreEntry();
        recent.name = "New\tSave";
        recent.playerClass = "Medic";
        recent.map = "Bunker";
        recent.score = 150;
        GameData.saveScore(recent);
        List<GameData.ScoreEntry> scores = GameData.loadScores();
        check(scores.size() == 2 && scores.get(0).name.equals("New Save"), "Old scores must load and sort with new entries");
        for (GameData.ScoreEntry score : scores) {
            System.out.printf("score %s %s %s %d %d %d %d%n", score.name, score.playerClass, score.map,
                    score.score, score.kills, score.wave, score.seconds);
        }
    }

    private static String pixels(BufferedImage image) {
        CRC32 crc = new CRC32();
        int[] row = new int[image.getWidth()];
        for (int y = 0; y < image.getHeight(); y++) {
            image.getRGB(0, y, row.length, 1, row, 0, row.length);
            for (int pixel : row) {
                crc.update(pixel >>> 24);
                crc.update(pixel >>> 16);
                crc.update(pixel >>> 8);
                crc.update(pixel);
            }
        }
        return Long.toHexString(crc.getValue());
    }

    private static void key(GamePanel panel, int code, boolean down) {
        KeyEvent event = new KeyEvent(panel, down ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED,
                0, 0, code, KeyEvent.CHAR_UNDEFINED);
        if (down) panel.keyPressed(event);
        else panel.keyReleased(event);
    }

    private static void mouse(GamePanel panel, int button, boolean down) {
        MouseEvent event = new MouseEvent(panel, down ? MouseEvent.MOUSE_PRESSED : MouseEvent.MOUSE_RELEASED,
                0, 0, 580, 265, 1, false, button);
        if (down) panel.mousePressed(event);
        else panel.mouseReleased(event);
    }

    @SuppressWarnings("unchecked")
    private static List<Monster> monsters(GamePanel panel) throws Exception {
        return (List<Monster>) read(panel, "monsters");
    }

    static Object read(Object object, String name) throws Exception {
        return field(object.getClass(), name).get(object);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
