// Tapatsawin Chaieua          6813105
// Thanapon Thanadunpremdet    6813106
// Tanawat Wongwad             6813107
// Natthanon Achachan          6813241
package Project3_6813107;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.Toolkit;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public final class GamePanel extends JPanel
        implements Runnable, KeyListener, MouseListener, MouseMotionListener, MouseWheelListener {

    private static final long serialVersionUID = 1L;
    private static final int SCALE = GameData.SCALE, DEFAULT_VIEW_W = 1100, DEFAULT_VIEW_H = 540;
    private static final long TICK_NANOS = GameData.TICK_MS * 1_000_000L;
    private static final int HUD_REFRESH_TICKS = 15, SPAWN_TICKS_WITHOUT_THREAD = 48;
    private static final int MAX_SPAWNS_PER_TICK = 4, GAME_OVER_DELAY_TICKS = 110, MAX_EFFECTS = 250, WHEEL_DELAY_TICKS = 8;

    private static final Color BACKGROUND = new Color(18, 16, 20);
    private static final Color TEXT_DAMAGE = Theme.TEXT, TEXT_KILL = Theme.GOLD, TEXT_HURT = Theme.RED;
    private static final Color TEXT_LOOT = new Color(0xB4DC70), BANNER_WAVE = Theme.GOLD, BANNER_BOSS = Theme.RED;

    private final transient GameData.GameSettings settings;
    private final GameFrame frame;
    private transient GameMap map;
    private final transient Player player;
    private final transient List<Monster> monsters = new CopyOnWriteArrayList<>();
    private final transient List<Bullet> bullets = new CopyOnWriteArrayList<>();
    private final transient List<DroppedItem> drops = new CopyOnWriteArrayList<>();
    private final transient List<AllyNpc> allies = new CopyOnWriteArrayList<>();
    private final transient List<HazardTrap> traps = new CopyOnWriteArrayList<>();
    private final transient List<ExitDoor> exitDoors = new CopyOnWriteArrayList<>();
    private final transient List<Effect> effects = new CopyOnWriteArrayList<>();
    private final transient List<FieldShopOffer> fieldShopOffers = new ArrayList<>();
    private final transient List<Rectangle> solidRects;
    private final transient List<GameMap.Drawable> drawList = new ArrayList<>();
    private final transient Object worldLock = new Object();
    private final Random rng = new Random();
    private final AtomicInteger pendingSpawns = new AtomicInteger();

    private transient Thread gameThread, waveSpawnerThread;
    private volatile boolean running, paused, gameOver, won, chamberCleared = true, pendingPerkSelection;
    private volatile int tickCount, elapsedTicks, chamber, highestChamberReached, runNumber = 1;
    private volatile int chamberMonstersTotal, chamberMonstersRemaining, kills, killScore;
    private volatile GameData.ChamberType currentChamberType = GameData.ChamberType.SAFE_CAMP;
    private long runSeed = 1001L;
    private int gameOverTimer, idlePlayerTicks, shakeTicks, lastPickupTick, pickupStack;
    private boolean raidActive;
    private float nightAlpha;
    private double lastPlayerX, lastPlayerY, shakePower;
    private volatile double camX, camY, shakeX, shakeY;
    private transient volatile Banner banner;

    private volatile boolean keyUp, keyDown, keyLeft, keyRight, mouseFiring, mouseInside;
    private volatile int mouseX = DEFAULT_VIEW_W / 2 + 80, mouseY = DEFAULT_VIEW_H / 2;
    private volatile boolean grenadeRequested, medkitRequested, interactRequested, dashRequested;
    private volatile int weaponSlotRequested = -1, weaponCycleRequested;
    private int lastWheelTick = -100;
    private volatile boolean autoFire, showHitbox;

    private final transient BufferedImage[] explosionFrames = AssetLoader.getExplosionFrames();
    private final transient BufferedImage[] impactFrames = AssetLoader.getImpactFrames();
    private final transient GameOverlay overlay;
    private int originX, originY;

    public GamePanel(GameData.GameSettings settings, GameFrame frame) {
        this.settings = settings == null ? new GameData.GameSettings() : settings;
        this.frame = frame;
        GameData.MapType mapType = this.settings.map == null ? GameData.MapType.WASTELAND_HIGHWAY : this.settings.map;
        runSeed = mapType.seed + System.currentTimeMillis() % 100000L;
        map = new GameMap(mapType, mapType.seed, true);
        solidRects = new ArrayList<>(map.getSolidRects());
        Point spawn = map.getPlayerSpawn();
        player = new Player(this.settings, spawn.x, spawn.y);
        overlay = new GameOverlay(player, monsters, drops, allies, traps, exitDoors, this::getHostilesRemaining);
        player.setMap(map);
        player.pushOutOfWalls();
        player.prepareForSafeCamp();
        autoFire = this.settings.autoFire;
        showHitbox = this.settings.showHitbox;
        AssetLoader.setSoundEnabled(this.settings.soundOn);
        AssetLoader.setMusicEnabled(this.settings.musicOn);
        AssetLoader.setVolume(this.settings.volume);

        setBackground(BACKGROUND);
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        addKeyListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);
        addMouseWheelListener(this);
        addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { releaseAllInput(); }
        });
        if (!GraphicsEnvironment.isHeadless()) {
            BufferedImage blank = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            setCursor(Toolkit.getDefaultToolkit().createCustomCursor(blank, new Point(0, 0), "blank"));
        }
        preloadSprites();
        if (frame == null) enterChamber(1, GameData.ChamberType.SALVAGE_VAULT);
        else initSafeCamp(false);
    }

    private static void preloadSprites() {
        for (GameData.MonsterType t : GameData.MonsterType.values()) {
            AssetLoader.loadStrip(t.runStrip(), 8);
            AssetLoader.loadStrip(t.hitStrip(), 3);
            AssetLoader.loadStrip(t.deathStrip(), 8);
        }
        AssetLoader.loadStrip("characters/bandit_idle.png", 6);
        AssetLoader.loadStrip("characters/npc_idle.png", 6);
        AssetLoader.loadStrip("characters/npc2_idle.png", 6);
        for (GameData.WeaponType w : GameData.WeaponType.values()) {
            AssetLoader.loadStrip(w.flickerStrip(), 7);
            if (w.shootStrip != null) AssetLoader.loadStrip(w.shootStrip, w.shootFrames);
        }
        for (GameData.ItemType t : GameData.ItemType.values()) AssetLoader.getItemFrames(t);
        AssetLoader.getAlertFrames();
    }

    public void startGame() {
        if (running) return;
        running = true;
        AssetLoader.playMusic("bgm");
        gameThread = new Thread(this, "gameThread");
        waveSpawnerThread = new Thread(this::runWaveSpawner, "waveSpawnerThread");
        gameThread.setDaemon(true);
        waveSpawnerThread.setDaemon(true);
        gameThread.start();
        waveSpawnerThread.start();
        requestFocusInWindow();
    }

    @Override
    public void run() {
        long nextTick = System.nanoTime();
        int ticksSinceHud = 0;
        while (running) {
            try {
                updateGame();
            } catch (RuntimeException ex) {
                System.err.println("Game step failed: " + ex);
                ex.printStackTrace();
            }
            repaint();
            if (++ticksSinceHud >= HUD_REFRESH_TICKS) {
                ticksSinceHud = 0;
                requestHudRefresh();
            }
            nextTick += TICK_NANOS;
            long sleepNanos = nextTick - System.nanoTime();
            if (sleepNanos < -5 * TICK_NANOS) {
                nextTick = System.nanoTime();
                sleepNanos = 0;
            }
            try {
                if (sleepNanos > 0) Thread.sleep(sleepNanos / 1_000_000, (int) (sleepNanos % 1_000_000));
            } catch (InterruptedException ex) {
                return;
            }
        }
    }

    private void runWaveSpawner() {
        Random timing = new Random();
        while (running) {
            try {
                int baseDelay = Math.max(700, 1400 - Math.max(0, chamber) * 50);
                Thread.sleep((raidActive ? baseDelay * 3 / 4 : baseDelay) + timing.nextInt(351));
            } catch (InterruptedException ex) {
                return;
            }
            if (!paused && !gameOver && player.isAlive() && chamber > 0 && !chamberCleared && chamberMonstersRemaining > 0) {
                requestSpawns();
            }
        }
    }

    public void pauseGame() {
        if (gameOver) return;
        paused = true;
        releaseAllInput();
        requestHudRefresh();
    }

    public void resumeGame() {
        paused = false;
        requestFocusInWindow();
        requestHudRefresh();
    }

    public void stopGame() {
        running = false;
        AssetLoader.stopMusic();
        stopThread(waveSpawnerThread);
        stopThread(gameThread);
    }

    private static void stopThread(Thread t) {
        if (t == null || t == Thread.currentThread()) return;
        t.interrupt();
        try {
            t.join(1000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void requestHudRefresh() {
        if (frame != null) SwingUtilities.invokeLater(frame::refreshHud);
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        if (code >= KeyEvent.VK_1 && code <= KeyEvent.VK_8) {
            weaponSlotRequested = code - KeyEvent.VK_1;
            return;
        }
        if (setMoveKey(code, true)) return;
        switch (code) {
            case KeyEvent.VK_SPACE, KeyEvent.VK_SHIFT -> dashRequested = true;
            case KeyEvent.VK_F -> interactRequested = true;
            case KeyEvent.VK_G -> grenadeRequested = true;
            case KeyEvent.VK_E -> medkitRequested = true;
            case KeyEvent.VK_B -> openWorkshop();
            case KeyEvent.VK_P, KeyEvent.VK_ESCAPE -> togglePause();
            default -> {}
        }
    }

    @Override public void keyReleased(KeyEvent e) { setMoveKey(e.getKeyCode(), false); }
    @Override public void keyTyped(KeyEvent e) {}

    private boolean setMoveKey(int code, boolean down) {
        if (code == KeyEvent.VK_W || code == KeyEvent.VK_UP) keyUp = down;
        else if (code == KeyEvent.VK_S || code == KeyEvent.VK_DOWN) keyDown = down;
        else if (code == KeyEvent.VK_A || code == KeyEvent.VK_LEFT) keyLeft = down;
        else if (code == KeyEvent.VK_D || code == KeyEvent.VK_RIGHT) keyRight = down;
        else return false;
        return true;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        requestFocusInWindow();
        trackMouse(e);
        if (paused || gameOver) return;
        if (SwingUtilities.isRightMouseButton(e) || e.isControlDown()) grenadeRequested = true;
        else if (SwingUtilities.isLeftMouseButton(e)) mouseFiring = true;
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e)) mouseFiring = false;
    }

    @Override public void mouseMoved(MouseEvent e) { trackMouse(e); }
    @Override public void mouseDragged(MouseEvent e) { trackMouse(e); }
    @Override public void mouseEntered(MouseEvent e) { trackMouse(e); }
    @Override public void mouseExited(MouseEvent e) { mouseInside = false; }
    @Override public void mouseClicked(MouseEvent e) {}

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        double rotation = e.getPreciseWheelRotation();
        if (rotation == 0 || tickCount - lastWheelTick < WHEEL_DELAY_TICKS) return;
        lastWheelTick = tickCount;
        weaponCycleRequested = rotation > 0 ? 1 : -1;
    }

    private void trackMouse(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();
        mouseInside = true;
    }

    private void togglePause() {
        if (paused) resumeGame();
        else pauseGame();
        repaint();
    }

    private void openWorkshop() {
        if (frame == null || gameOver || !player.isAlive()) return;
        if (chamber > 0) {
            SwingUtilities.invokeLater(frame::showFieldShopDialog);
        } else if (!isPlayerInBase()) {
            player.showHint("Camp NPCs are only available at the Safe Camp Hub!");
            AssetLoader.playSound("click");
        } else {
            SwingUtilities.invokeLater(frame::openUpgradeDialog);
        }
    }

    private void releaseAllInput() {
        keyUp = keyDown = keyLeft = keyRight = mouseFiring = false;
    }

    public void updateGame() {
        if (paused) return;
        synchronized (worldLock) {
            tickCount++;
            if (gameOver) {
                updateEffects();
                updateGameOverDelay();
                return;
            }
            if (player.isAlive() && chamber > 0 && !chamberCleared) elapsedTicks++;
            if (!running && tickCount % SPAWN_TICKS_WITHOUT_THREAD == 0 && player.isAlive()
                    && chamber > 0 && !chamberCleared && chamberMonstersRemaining > 0) {
                requestSpawns();
            }
            spawnPendingMonsters();
            updatePlayer();
            updateBaseSystems();
            updateTraps();
            map.update();
            updateMonsters();
            updateBullets();
            updateDrops();
            if (chamber > 0 && !chamberCleared && player.isAlive() && chamberMonstersRemaining <= 0 && getLiveMonsterCount() == 0) {
                onChamberCleared();
            }
            updateEffects();
            updateCamera(0.28);
            if (!player.isAlive() && player.isDeathAnimationDone()) finishGame(false);
        }
    }

    private void initSafeCamp(boolean returningFromRun) {
        GameData.MapType hubType = settings.map == null ? GameData.MapType.WASTELAND_HIGHWAY : settings.map;
        chamber = 0;
        currentChamberType = GameData.ChamberType.SAFE_CAMP;
        chamberCleared = true;
        chamberMonstersTotal = chamberMonstersRemaining = 0;
        pendingSpawns.set(0);
        pendingPerkSelection = false;
        fieldShopOffers.clear();
        raidActive = false;
        nightAlpha = 0f;

        Point spawn = placePlayerOnMap(new GameMap(hubType, hubType.seed, true));
        player.prepareForSafeCamp();
        clearRoomEntities();
        int allyLv = player.getLevel(GameData.RecipeKind.BASE_TURRET);
        for (int i = 0; i < Math.min(4, allyLv); i++) allies.add(new AllyNpc(i, spawn.x - 24 + i * 16, spawn.y + 12));

        Point gate = map.getGatePosition();
        exitDoors.add(new ExitDoor(GameData.ChamberType.TACTICAL_PERK, 1, gate.x, gate.y, "ENTER CHAMBER 1 (RUN #" + runNumber + ")"));
        lastPlayerX = player.getX();
        lastPlayerY = player.getY();
        updateCamera(1.0);
        showBanner("SAFE CAMP HUB - RUN #" + runNumber,
                returningFromRun ? "All permanent salvage saved! Upgrade at NPC [B] then press [F] at Gate"
                        : "Talk to Camp NPC [B], equip gear, then press [F] at the Gate below",
                BANNER_WAVE);
    }

    public void returnToSafeCamp() {
        synchronized (worldLock) {
            runNumber++;
            kills = killScore = elapsedTicks = 0;
            runSeed += 7919L;
            gameOver = won = paused = false;
            initSafeCamp(true);
            AssetLoader.playMusic("bgm");
        }
        requestHudRefresh();
        requestFocusInWindow();
    }

    private GameData.MapType biomeForChamber(int c) {
        if (c <= 5) return (c % 2 == 1) ? GameData.MapType.WASTELAND_HIGHWAY : GameData.MapType.GAS_STATION;
        if (c <= 10) return (c % 2 == 0) ? GameData.MapType.QUARANTINE_ZONE : GameData.MapType.SURVIVOR_CAMP;
        if (c <= 15) return GameData.MapType.ABANDONED_BUNKER;
        GameData.MapType[] all = GameData.MapType.values();
        return all[(c - 1) % all.length];
    }

    public String getActLabel(int c) {
        if (c <= 0) return "SAFE CAMP HUB";
        if (c <= 5) return "ACT 1: OUTSKIRTS";
        if (c <= 10) return "ACT 2: QUARANTINE";
        if (c <= 15) return "ACT 3: BUNKER";
        return "ENDLESS ABYSS";
    }

    private void enterChamber(int nextChamber, GameData.ChamberType type) {
        if (nextChamber == 1 && chamber == 0) {
            elapsedTicks = kills = killScore = 0;
            runSeed += 313L;
        }
        chamber = nextChamber;
        highestChamberReached = Math.max(highestChamberReached, chamber);
        currentChamberType = type;
        chamberCleared = pendingPerkSelection = false;
        pendingSpawns.set(0);
        raidActive = type == GameData.ChamberType.BOSS_CHAMBER || type == GameData.ChamberType.ELITE_HAZARD;
        nightAlpha = chamber >= 11 ? 0.42f : (chamber >= 6 ? 0.24f : (type == GameData.ChamberType.BOSS_CHAMBER ? 0.35f : 0f));

        Point spawn = placePlayerOnMap(new GameMap(biomeForChamber(chamber), runSeed + chamber * 7919L + runNumber * 131L, false));
        player.clearTempChamberWeapon();
        rollFieldShopOffers(chamber);
        if (!player.canUseWeaponInRun(player.getCurrentWeapon(), false)) {
            GameData.WeaponType gun = player.getEquippedPrimaryGun();
            player.switchWeapon(gun != null ? gun : GameData.WeaponType.KNIFE);
        }

        clearRoomEntities();
        int allyLv = player.getLevel(GameData.RecipeKind.BASE_TURRET);
        for (int i = 0; i < Math.min(4, allyLv); i++) allies.add(new AllyNpc(i, spawn.x - 22 + i * 14, spawn.y + 12));
        spawnTrapsForChamber(chamber, type, spawn);

        if (type == GameData.ChamberType.SALVAGE_VAULT) {
            Point cp = map.randomOuterLootPoint(rng);
            DroppedItem chest = new DroppedItem(GameData.ItemType.CHEST, 1, cp.x, cp.y, player);
            chest.setMap(map);
            drops.add(chest);
        }

        boolean bossChamber = type == GameData.ChamberType.BOSS_CHAMBER || chamber % GameData.BOSS_EVERY_CHAMBERS == 0;
        chamberMonstersTotal = GameData.chamberQuota(chamber, type, getDifficulty(), settings.doubleSpawn);
        int initialBatch = Math.min(chamberMonstersTotal, 3 + chamber / 2);
        chamberMonstersRemaining = chamberMonstersTotal - initialBatch;
        for (int i = 0; i < initialBatch; i++) spawnMonster(GameData.MonsterType.randomForWave(chamber, rng));
        if (bossChamber) {
            spawnBoss();
        } else {
            AssetLoader.playSound("wave");
            showBanner(getActLabel(chamber) + " - CHAMBER " + chamber,
                    type.displayName.toUpperCase() + "  |  " + map.getChamberLayoutName() + "  |  " + chamberMonstersTotal + " HOSTILES",
                    type == GameData.ChamberType.ELITE_HAZARD ? BANNER_BOSS : BANNER_WAVE);
        }
        lastPlayerX = player.getX();
        lastPlayerY = player.getY();
        updateCamera(1.0);
        requestHudRefresh();
    }

    private Point placePlayerOnMap(GameMap nextMap) {
        map = nextMap;
        solidRects.clear();
        solidRects.addAll(map.getSolidRects());
        Point spawn = map.getPlayerSpawn();
        player.setMap(map);
        player.setPosition(spawn.x, spawn.y);
        player.pushOutOfWalls();
        return spawn;
    }

    private void clearRoomEntities() {
        monsters.clear();
        bullets.clear();
        drops.clear();
        traps.clear();
        exitDoors.clear();
        effects.clear();
        allies.clear();
    }

    private void onChamberCleared() {
        chamberCleared = true;
        pendingSpawns.set(0);
        AssetLoader.playSound("win");
        int healAmount = player.getChamberClearHeal() + (currentChamberType == GameData.ChamberType.FIELD_MEDIC ? 9 : 0);
        if (healAmount > 0) {
            int before = player.getHp();
            player.heal(healAmount);
            if (currentChamberType == GameData.ChamberType.FIELD_MEDIC) player.curePoison();
            int gained = player.getHp() - before;
            if (gained > 0) addText(player.getX(), player.getY() - 26, "+" + gained + " HP CLEAR", TEXT_LOOT);
        }
        spawnChamberClearRewards();
        spawnExitDoors();

        boolean offersPerk = currentChamberType == GameData.ChamberType.TACTICAL_PERK
                || currentChamberType == GameData.ChamberType.ELITE_HAZARD
                || currentChamberType == GameData.ChamberType.BOSS_CHAMBER;
        if (offersPerk && frame != null && !pendingPerkSelection) {
            List<GameData.TacticalPerk> choices = rollPerkChoices(3);
            if (!choices.isEmpty()) {
                pendingPerkSelection = true;
                SwingUtilities.invokeLater(() -> frame.showPerkSelectionDialog(choices));
            }
        }
        showBanner("CHAMBER " + chamber + " CLEARED",
                chamber == 15 ? "Victory! Head to Exit Gate (minimap) & press [F]"
                        : "Grab salvage & head to Exit Gate (minimap) [F]",
                TEXT_LOOT);
        requestHudRefresh();
    }

    private void spawnChamberClearRewards() {
        Point cx = new Point((int) player.getX(), (int) player.getY());
        switch (currentChamberType) {
            case SALVAGE_VAULT -> {
                spawnBonusItem(GameData.ItemType.STEEL, 1, cx);
                spawnBonusItem(GameData.ItemType.SCRAPS, 1 + chamber / 5, cx);
                if (rng.nextBoolean()) spawnBonusItem(GameData.ItemType.ELECTRONICS, 1, cx);
            }
            case ELITE_HAZARD -> {
                spawnBonusItem(GameData.ItemType.STEEL, 1, cx);
                spawnBonusItem(GameData.ItemType.SCRAPS, 1, cx);
                if (rng.nextInt(100) < 35) spawnBonusItem(GameData.ItemType.BATTERY, 1, cx);
            }
            case FIELD_MEDIC, BOSS_CHAMBER -> {
                if (currentChamberType == GameData.ChamberType.FIELD_MEDIC) {
                    spawnBonusItem(GameData.ItemType.SCRAPS, 2, cx);
                    spawnBonusItem(GameData.ItemType.AMMO, 8 + chamber, cx);
                } else {
                    spawnBonusItem(GameData.ItemType.AMMO, 8 + chamber, cx);
                    spawnBonusItem(GameData.ItemType.SCRAPS, 2, cx);
                }
            }
            default -> {
                spawnBonusItem(GameData.ItemType.AMMO, 5 + chamber / 2, cx);
                if (rng.nextInt(100) < 35) spawnBonusItem(GameData.ItemType.SCRAPS, 1, cx);
            }
        }
    }

    private void spawnBonusItem(GameData.ItemType itemType, int amount, Point center) {
        double angle = rng.nextDouble() * Math.PI * 2;
        double sx = Math.max(48, Math.min(map.getWidthPx() - 48, center.x + Math.cos(angle) * 28));
        double sy = Math.max(48, Math.min(map.getHeightPx() - 48, center.y + Math.sin(angle) * 24));
        DroppedItem d = new DroppedItem(itemType, amount, sx, sy, player);
        d.setMap(map);
        d.scatter(rng);
        drops.add(d);
    }

    private List<GameData.TacticalPerk> rollPerkChoices(int count) {
        List<GameData.TacticalPerk> pool = new ArrayList<>();
        for (GameData.TacticalPerk p : GameData.TacticalPerk.values()) {
            if (player.getPerkLevel(p) < 3) pool.add(p);
        }
        Collections.shuffle(pool, rng);
        return pool.subList(0, Math.min(count, pool.size()));
    }

    public void applyChosenPerk(GameData.TacticalPerk perk) {
        synchronized (worldLock) {
            if (perk != null) {
                player.addPerk(perk);
                addText(player.getX(), player.getY() - 28, perk.displayName + " Lv." + player.getPerkLevel(perk), Theme.GOLD);
                AssetLoader.playSound("craft");
            }
            pendingPerkSelection = false;
        }
        requestHudRefresh();
    }

    private void spawnExitDoors() {
        exitDoors.clear();
        Point gate = map.getGatePosition();
        if (chamber == 15) {
            Point p1 = findDoorSpotAround(gate.x, gate.y, -48, -28), p2 = findDoorSpotAround(gate.x, gate.y, 48, -28);
            exitDoors.add(new ExitDoor(GameData.ChamberType.VICTORY_EXTRACT, 0, p1.x, p1.y, "EXTRACT TO CAMP (VICTORY)"));
            exitDoors.add(new ExitDoor(GameData.ChamberType.ENDLESS_ABYSS, 16, p2.x, p2.y, "ENTER ENDLESS ABYSS (CH 16)"));
            return;
        }
        int next = chamber + 1;
        if (next % GameData.BOSS_EVERY_CHAMBERS == 0) {
            Point p = findDoorSpotAround(gate.x, gate.y, 0, 0);
            exitDoors.add(new ExitDoor(GameData.ChamberType.BOSS_CHAMBER, next, p.x, p.y, "ENTER BOSS LAIR (CH " + next + ")"));
            if (chamber > 15) {
                Point ext = findDoorSpotAround(gate.x, gate.y, 56, 20);
                exitDoors.add(new ExitDoor(GameData.ChamberType.VICTORY_EXTRACT, 0, ext.x, ext.y, "EXTRACT TO SAFE CAMP"));
            }
            return;
        }
        List<GameData.ChamberType> options = new ArrayList<>(List.of(
                GameData.ChamberType.TACTICAL_PERK, GameData.ChamberType.SALVAGE_VAULT, GameData.ChamberType.FIELD_MEDIC));
        if (next >= 3) options.add(GameData.ChamberType.ELITE_HAZARD);
        Collections.shuffle(options, rng);
        int doorCount = (chamber % 3 == 0) ? Math.min(3, options.size()) : 2;
        int[][] offsets = doorCount == 3 ? new int[][]{{-64, -24}, {0, 26}, {64, -24}} : new int[][]{{-44, -18}, {44, 18}};
        for (int i = 0; i < doorCount; i++) {
            GameData.ChamberType ct = options.get(i);
            Point spot = findDoorSpotAround(gate.x, gate.y, offsets[i][0], offsets[i][1]);
            exitDoors.add(new ExitDoor(ct, next, spot.x, spot.y, ct.displayName.toUpperCase() + " (CH " + next + ")"));
        }
    }

    private Point findDoorSpotAround(int anchorX, int anchorY, int dx, int dy) {
        for (double s : new double[]{1.0, 0.7, 1.35, -0.7}) {
            int cx = (int) Math.round(anchorX + dx * s), cy = (int) Math.round(anchorY + dy * s);
            if (isValidDoorSpot(cx, cy)) return new Point(cx, cy);
        }
        for (int rad = 38; rad <= 66; rad += 24) {
            for (int step = 0; step < 16; step++) {
                double ang = step * (Math.PI / 8.0);
                int cx = (int) Math.round(anchorX + Math.cos(ang) * rad), cy = (int) Math.round(anchorY + Math.sin(ang) * rad);
                if (isValidDoorSpot(cx, cy)) return new Point(cx, cy);
            }
        }
        return map.randomWalkablePoint(rng, anchorX, anchorY, 24.0);
    }

    private boolean isValidDoorSpot(int px, int py) {
        if (px < 40 || py < 40 || px > map.getWidthPx() - 40 || py > map.getHeightPx() - 40) return false;
        if (map.isBlocked(new Rectangle(px - 14, py - 8, 28, 16))) return false;
        for (ExitDoor other : exitDoors) {
            if (Math.hypot(px - other.x, py - other.y) < 36.0) return false;
        }
        return true;
    }

    private void interactNearestDoor() {
        if (gameOver || !player.isAlive()) return;
        Point trader = map.getTraderPosition();
        boolean nearTrader = chamber > 0 && player.distanceTo(trader.x, trader.y) < 68.0;
        ExitDoor best = null;
        double bestDist = 72.0;
        if (chamber == 0 || chamberCleared) {
            for (ExitDoor d : exitDoors) {
                double dist = player.distanceTo(d.x, d.y);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = d;
                }
            }
        }
        if (nearTrader && (best == null || bestDist > 52.0)) {
            openWorkshop();
            return;
        }
        if (exitDoors.isEmpty()) return;
        if (chamber > 0 && !chamberCleared) {
            player.showHint("Clear remaining hostiles first! Press [B] for Field Merchant");
            return;
        }
        if (best == null) {
            player.showHint(chamber == 0 ? "Walk down to the Expedition Gate & press [F]" : "Stand near a Portal & press [F]");
            AssetLoader.playSound("click");
            return;
        }
        if (best.type == GameData.ChamberType.VICTORY_EXTRACT) {
            finishGame(true);
            return;
        }
        enterChamber(best.nextChamber, best.type == GameData.ChamberType.ENDLESS_ABYSS ? GameData.ChamberType.ELITE_HAZARD : best.type);
    }

    private void spawnTrapsForChamber(int c, GameData.ChamberType kind, Point spawn) {
        int targetCount = Math.min(28, 4 + c * 3 / 2 + (kind == GameData.ChamberType.ELITE_HAZARD ? 5 : 0));
        HazardTrap.TrapKind[] allKinds = HazardTrap.TrapKind.values();
        Point gate = map.getGatePosition();
        double dx = gate.x - spawn.x, dy = gate.y - spawn.y, len = Math.hypot(dx, dy);
        double fx = len >= 40.0 ? dx / len : 1.0, fy = len >= 40.0 ? dy / len : 0.0;
        double px = -fy, py = fx;

        double[] lineFractions = targetCount >= 18 ? new double[]{0.30, 0.54, 0.77} : new double[]{0.34, 0.64};
        HazardTrap.TrapKind[] linePool = {
            HazardTrap.TrapKind.SPIKE, HazardTrap.TrapKind.FLAME_JET, HazardTrap.TrapKind.SHOCK, HazardTrap.TrapKind.TOXIC
        };
        for (int g = 0; g < lineFractions.length && traps.size() < targetCount; g++) {
            double cx = spawn.x + dx * lineFractions[g], cy = spawn.y + dy * lineFractions[g];
            HazardTrap.TrapKind lineKind = linePool[(c + g + rng.nextInt(linePool.length)) % linePool.length];
            int halfSpan = (g == 1 && targetCount >= 18) ? 2 : 1, step = 0;
            for (int k = -halfSpan; k <= halfSpan && traps.size() < targetCount; k++) {
                double tx = cx + px * (k * 32.0) + fx * ((step % 2 == 0) ? 0.0 : 8.0);
                double ty = cy + py * (k * 32.0) + fy * ((step % 2 == 0) ? 0.0 : 8.0);
                addTrapIfValid(lineKind, tx, ty, step * 16 + g * 23, spawn, 24.0);
                step++;
            }
        }

        int mineClusters = (c >= 4 || kind == GameData.ChamberType.ELITE_HAZARD) ? 2 : 1;
        for (int cl = 0; cl < mineClusters && traps.size() + 2 <= targetCount; cl++) {
            Point center = map.randomWalkablePoint(rng, spawn.x, spawn.y, 135.0);
            int placed = 0;
            double baseAngle = rng.nextDouble() * Math.PI * 2.0;
            for (int m = 0; m < 3 && traps.size() < targetCount; m++) {
                double ang = baseAngle + m * (Math.PI * 2.0 / 3.0), r = 24.0 + rng.nextInt(8);
                if (addTrapIfValid(HazardTrap.TrapKind.MINE, center.x + Math.cos(ang) * r, center.y + Math.sin(ang) * r, m * 9, spawn, 26.0)) placed++;
            }
            if (placed == 0) addTrapIfValid(HazardTrap.TrapKind.MINE, center.x, center.y, cl * 11, spawn, 26.0);
        }

        for (HazardTrap.TrapKind needed : allKinds) {
            if (traps.size() >= targetCount) break;
            if (traps.stream().noneMatch(t -> t.kind == needed)) {
                for (int tries = 0; tries < 10; tries++) {
                    Point p = map.randomWalkablePoint(rng, spawn.x, spawn.y, 110.0);
                    if (addTrapIfValid(needed, p.x, p.y, tries * 19, spawn, 28.0)) break;
                }
            }
        }

        int tries = 0;
        while (traps.size() < targetCount && tries < targetCount * 6) {
            tries++;
            Point p = map.randomWalkablePoint(rng, spawn.x, spawn.y, 110.0);
            HazardTrap.TrapKind k = allKinds[rng.nextInt(allKinds.length)];
            int phase = tries * 17 + rng.nextInt(25);
            if (addTrapIfValid(k, p.x, p.y, phase, spawn, 30.0)
                    && k != HazardTrap.TrapKind.MINE && traps.size() < targetCount && rng.nextInt(100) < 60) {
                boolean horiz = rng.nextBoolean();
                addTrapIfValid(k, p.x + (horiz ? 34.0 : 0.0), p.y + (horiz ? 0.0 : 34.0), phase + 18, spawn, 24.0);
            }
        }
    }

    private boolean addTrapIfValid(HazardTrap.TrapKind kind, double x, double y, int phase, Point spawn, double minTrapGap) {
        if (x < 44 || y < 44 || x > map.getWidthPx() - 44 || y > map.getHeightPx() - 44) return false;
        if (Math.hypot(x - spawn.x, y - spawn.y) < 88.0) return false;
        if (map.isBlocked(new Rectangle((int) Math.round(x) - 10, (int) Math.round(y) - 8, 20, 16))) return false;
        for (HazardTrap existing : traps) {
            if (Math.hypot(x - existing.x, y - existing.y) < minTrapGap) return false;
        }
        traps.add(new HazardTrap(kind, x, y, phase));
        return true;
    }

    private void updateTraps() {
        if (chamber <= 0 || traps.isEmpty()) return;
        double monsterMult = 1.0 + 0.85 * player.getPerkLevel(GameData.TacticalPerk.TRAP_MASTER);
        for (HazardTrap t : traps) {
            t.update();
            if (chamberCleared || t.triggered) continue;
            HazardTrap.TrapKind k = t.kind;
            if (k == HazardTrap.TrapKind.MINE) {
                if (t.armTicks == 0) detonateMine(t);
                else if (t.armTicks < 0 && hasEntityNear(t.x, t.y, 24.0)) {
                    t.armTicks = 18;
                    addText(t.x, t.y - 12, "ARMED!", Theme.RED);
                }
                continue;
            }
            if (!t.isTriggeringDamage()) continue;
            if (player.isAlive() && !player.isDashing() && player.distanceTo(t.x, t.y) < k.playerRad) {
                boolean wasSlowed = player.isTrapSlowed();
                if (hurtPlayerTrap(k.playerBaseDmg + chamber / k.playerChamberDiv) > 0) {
                    if (k == HazardTrap.TrapKind.TOXIC) player.applyPoison(110);
                    player.applyTrapSlow(k.playerSlow);
                    if (!wasSlowed && player.isTrapSlowed()) addText(player.getX(), player.getY() - 32, "SLOWED!", Theme.GOLD);
                }
            }
            if (t.hitCooldown <= 0) {
                boolean hitAny = false;
                int dmg = (int) Math.round(k.monsterDmg * monsterMult);
                for (Monster m : monsters) {
                    if (!m.isAlive() || m.distanceTo(t.x, t.y) > k.monsterRad) continue;
                    hitAny = true;
                    boolean killed = m.takeHit(dmg, Math.atan2(m.getY() - t.y, m.getX() - t.x), 3.0);
                    if (k.monsterSlow > 0) m.applySlow(k.monsterSlow);
                    addText(m.getX(), m.getY() - m.getHeight(), String.valueOf(dmg), killed ? TEXT_KILL : TEXT_DAMAGE);
                    if (killed) onMonsterKilled(m);
                }
                if (hitAny) t.hitCooldown = 20;
            }
        }
    }

    private boolean hasEntityNear(double tx, double ty, double radius) {
        if (player.isAlive() && !player.isDashing() && player.distanceTo(tx, ty) < radius) return true;
        for (Monster m : monsters) if (m.isAlive() && m.distanceTo(tx, ty) < radius) return true;
        return false;
    }

    private void detonateMine(HazardTrap mine) {
        if (mine.triggered) return;
        mine.triggered = true;
        addEffect(new Effect(Effect.Kind.EXPLOSION, mine.x, mine.y, 26, null, null, 86));
        AssetLoader.playSound("explosion");
        shake(5.5, 16);
        int monsterDmg = (int) Math.round(56 * (1.0 + 0.85 * player.getPerkLevel(GameData.TacticalPerk.TRAP_MASTER)));
        for (Monster m : monsters) {
            if (!m.isAlive() || m.distanceTo(mine.x, mine.y) > 60.0) continue;
            boolean killed = m.takeHit(monsterDmg, Math.atan2(m.getY() - mine.y, m.getX() - mine.x), 6.0);
            addText(m.getX(), m.getY() - m.getHeight(), String.valueOf(monsterDmg), killed ? TEXT_KILL : TEXT_DAMAGE);
            if (killed) onMonsterKilled(m);
        }
        if (player.isAlive() && !player.isDashing() && player.distanceTo(mine.x, mine.y) < 52.0 && hurtPlayerTrap(32 + chamber) > 0) {
            player.applyTrapSlow(45);
        }
        for (HazardTrap other : traps) {
            if (!other.triggered && other != mine && other.kind == HazardTrap.TrapKind.MINE
                    && Math.hypot(other.x - mine.x, other.y - mine.y) <= 65.0) {
                other.armTicks = other.armTicks < 0 ? 8 : Math.min(other.armTicks, 8);
            }
        }
    }

    private void updateBaseSystems() {
        int turretLv = player.getLevel(GameData.RecipeKind.BASE_TURRET);
        int wallLv = player.getLevel(GameData.RecipeKind.BASE_WALL);
        int medicLv = player.getLevel(GameData.RecipeKind.BASE_MEDIC);
        int farmLv = player.getLevel(GameData.RecipeKind.BASE_FARM);
        if (map.hasSafeCamp() && map.applyBaseUpgrades(turretLv, wallLv, medicLv, farmLv)) {
            solidRects.clear();
            solidRects.addAll(map.getSolidRects());
        }
        if (Math.hypot(player.getX() - lastPlayerX, player.getY() - lastPlayerY) > 0.5 || mouseFiring || autoFire) idlePlayerTicks = 0;
        else idlePlayerTicks++;
        lastPlayerX = player.getX();
        lastPlayerY = player.getY();

        int desiredAllies = Math.min(4, turretLv);
        while (allies.size() < desiredAllies) allies.add(new AllyNpc(allies.size(), player.getX(), player.getY()));
        boolean allowNpcFire = frame != null || idlePlayerTicks < 180;
        for (AllyNpc a : allies) a.update(turretLv, allowNpcFire);

        if (chamber == 0 && map.hasSafeCamp() && medicLv > 0 && player.isAlive() && tickCount % 75 == 0
                && map.getCampArea().contains((int) player.getX(), (int) player.getY())) {
            boolean wasPoisoned = player.isPoisoned();
            int before = player.getHp();
            player.heal(2 + medicLv * 2);
            player.curePoison();
            int gained = player.getHp() - before;
            if (gained > 0 || wasPoisoned) addText(player.getX(), player.getY() - 24, gained > 0 ? "+" + gained + " Med Tent" : "Cured!", TEXT_LOOT);
        }
    }

    private void requestSpawns() {
        if (chamber <= 0 || chamberCleared || chamberMonstersRemaining <= 0) return;
        pendingSpawns.addAndGet(Math.min(chamberMonstersRemaining, GameData.spawnBatchSize(chamber, getDifficulty(), settings.doubleSpawn)));
    }

    private void spawnPendingMonsters() {
        if (chamber <= 0 || chamberCleared || chamberMonstersRemaining <= 0) {
            pendingSpawns.set(0);
            return;
        }
        if (getLiveMonsterCount() < 3 && pendingSpawns.get() == 0) requestSpawns();
        int maxAllowed = settings.doubleSpawn ? GameData.MAX_LIVE_MONSTERS : Math.min(32, 5 + chamber + getDifficulty());
        int room = Math.min(chamberMonstersRemaining, maxAllowed - getLiveMonsterCount());
        if (room <= 0) {
            pendingSpawns.set(0);
            return;
        }
        int count = Math.min(MAX_SPAWNS_PER_TICK, Math.min(room, pendingSpawns.get()));
        for (int i = 0; i < count; i++) {
            spawnMonster(GameData.MonsterType.randomForWave(chamber, rng));
            chamberMonstersRemaining--;
        }
        pendingSpawns.addAndGet(-count);
    }

    private void spawnMonster(GameData.MonsterType type) {
        Point p = type == GameData.MonsterType.ZOMBIE_BOSS || rng.nextInt(2) == 0
                ? map.randomSpawnPoint(rng, getCameraView())
                : map.randomWalkablePoint(rng, player.getX(), player.getY(), 170.0);
        Monster m = new Monster(type, p.x, p.y, map, player, GameData.monsterHpScale(chamber, getDifficulty()));
        m.applyChamberScaling(GameData.monsterSpeedScale(chamber), GameData.monsterDamageScale(chamber, getDifficulty()));
        m.setRaiding(true);
        monsters.add(m);
    }

    private void spawnBoss() {
        spawnMonster(GameData.MonsterType.ZOMBIE_BOSS);
        AssetLoader.playSound("boss_roar");
        showBanner(getActLabel(chamber) + " BOSS", "Defeat the Brute Boss to earn a Blueprint & Chest!", BANNER_BOSS);
        shake(5, 30);
    }

    private void updatePlayer() {
        if (!player.isAlive()) {
            player.update();
            return;
        }
        double aimX = getMouseWorldX(), aimY = getMouseWorldY();
        handleActionRequests(aimX, aimY);
        player.setMoveInput(keyUp, keyDown, keyLeft, keyRight);
        Monster target = autoFire && !mouseFiring ? findAutoFireTarget() : null;
        player.setAim(target != null ? target.getX() : aimX, target != null ? target.getY() : aimY);
        player.update();
        if (mouseFiring) fireAt(aimX, aimY);
        else if (target != null) fireAt(target.getX(), target.getY());
    }

    private void handleActionRequests(double aimX, double aimY) {
        int slot = weaponSlotRequested;
        if (slot >= 0) {
            weaponSlotRequested = -1;
            if (slot < GameData.HOTBAR_WEAPONS.length) {
                GameData.WeaponType w = GameData.HOTBAR_WEAPONS[slot];
                if (!player.ownsWeapon(w)) player.showHint("Unlock " + w.displayName + " at Camp NPC [B]");
                else if (chamber == 0) {
                    player.selectPrimaryGun(w);
                    if (!w.melee) player.showHint("Run Loadout: Survival Knife + " + w.displayName);
                } else if (player.canUseWeaponInRun(w, false)) player.switchWeapon(w);
                else player.showHint("Carry Knife + 1 Gun per run! Swap at Safe Camp");
            }
        }
        if (weaponCycleRequested != 0) {
            player.cycleWeapon(weaponCycleRequested, chamber == 0);
            weaponCycleRequested = 0;
        }
        if (dashRequested) {
            dashRequested = false;
            if (player.triggerDash()) addEffect(new Effect(Effect.Kind.IMPACT, player.getX(), player.getY(), 12, null, null, 20));
        }
        if (interactRequested) {
            interactRequested = false;
            interactNearestDoor();
        }
        if (grenadeRequested) {
            grenadeRequested = false;
            Bullet grenade = player.throwGrenade(aimX, aimY);
            if (grenade != null) bullets.add(grenade);
        }
        if (medkitRequested) {
            medkitRequested = false;
            player.showHint("No manual heal! Clear chambers (+HP/lv) or visit Field Merchant [B]");
            AssetLoader.playSound("click");
        }
    }

    private void fireAt(double targetX, double targetY) {
        List<Bullet> shots = player.shoot(targetX, targetY);
        if (shots.isEmpty()) return;
        bullets.addAll(shots);
        GameData.WeaponType w = player.getCurrentWeapon();
        if (w.damage >= 30 && !w.melee) shake(1.5, 6);
    }

    private Monster findAutoFireTarget() {
        GameData.WeaponType w = player.getCurrentWeapon();
        double bestDist = w.melee ? w.range + 14 : w.range * 0.9;
        Monster best = null;
        for (Monster m : monsters) {
            double d = m.isAlive() ? player.distanceTo(m) : bestDist;
            if (d < bestDist) {
                bestDist = d;
                best = m;
            }
        }
        if (best != null && !w.melee && !map.hasClearPath(player.getX(), player.getY(), best.getX(), best.getY(), 4)) return null;
        return best;
    }

    private int hurtPlayer(int damage) {
        int ddBefore = player.getDeathDefianceLeft();
        int dealt = player.takeDamage(damage);
        return onPlayerDamaged(dealt, ddBefore, "", 4.2, 12);
    }

    private int hurtPlayerTrap(int damage) {
        int ddBefore = player.getDeathDefianceLeft();
        int dealt = player.takeTrapDamage(damage);
        return onPlayerDamaged(dealt, ddBefore, " TRAP", 3.5, 10);
    }

    private int onPlayerDamaged(int dealt, int ddBefore, String suffix, double shakePower, int shakeTicks) {
        if (dealt > 0) {
            addText(player.getX(), player.getY() - 22, "-" + dealt + suffix, TEXT_HURT);
            shake(shakePower, shakeTicks);
            if (player.getDeathDefianceLeft() < ddBefore) {
                addEffect(new Effect(Effect.Kind.EXPLOSION, player.getX(), player.getY(), 24, null, null, 72));
                showBanner("DEATH DEFIANCE", "Second wind at 45% HP! (" + player.getDeathDefianceLeft() + " revives left)", BANNER_WAVE);
            }
        }
        return dealt;
    }

    private void updateMonsters() {
        for (Monster m : monsters) {
            m.update();
            if (!m.isAlive()) continue;
            int bite = m.tryContactAttack();
            if (bite > 0) hurtPlayer(bite);
            Bullet shot;
            while ((shot = m.takeProjectile()) != null) bullets.add(shot);
        }
        Monster.separate(monsters);
        monsters.removeIf(Monster::isRemovable);
    }

    private void onMonsterKilled(Monster m) {
        kills++;
        killScore += m.getType().score;
        drops.addAll(m.rollDrops(rng, player.getLootBonus()));
        if (currentChamberType == GameData.ChamberType.ELITE_HAZARD && rng.nextInt(100) < 18) {
            drops.addAll(m.rollDrops(rng, player.getLootBonus()));
        }
        int blastLv = player.getPerkLevel(GameData.TacticalPerk.EXPLOSIVE_KILL);
        if (blastLv > 0) explode(m.getX(), m.getY(), 50, 26 * blastLv);
        if (m.getType() == GameData.MonsterType.ZOMBIE_BOSS) {
            showBanner("ACT BOSS DEFEATED!", "Blueprint & Supply Chest dropped!", BANNER_WAVE);
            shake(7.5, 32);
        } else if (m.getType() == GameData.MonsterType.ZOMBIE_TOXIC || m.getType() == GameData.MonsterType.BANDIT_SHOOTER) {
            shake(3.4, 10);
        } else {
            shake(2.6, 8);
        }
    }

    private void updateBullets() {
        for (Bullet b : bullets) {
            b.update();
            if (b.isHostile()) {
                if (b.tryHit(player)) {
                    addImpact(b);
                    if (hurtPlayer(b.getDamage()) > 0 && b.getPoisonTicks() > 0) player.applyPoison(b.getPoisonTicks());
                }
            } else {
                checkPlayerBulletHits(b);
            }
            if (b.consumeExplosion()) explode(b.getX(), b.getY(), b.getBlastRadius(), b.getDamage());
            if (b.consumeWallHit()) addImpact(b);
        }
        bullets.removeIf(b -> !b.isAlive());
    }

    private void checkPlayerBulletHits(Bullet b) {
        if (!b.isMelee() && chamber > 0) {
            for (HazardTrap t : traps) {
                if (!t.triggered && t.kind == HazardTrap.TrapKind.MINE && Math.hypot(t.x - b.getX(), t.y - b.getY()) <= 14.0) {
                    detonateMine(t);
                    addImpact(b);
                    return;
                }
            }
        }
        int venomLv = player.getPerkLevel(GameData.TacticalPerk.VENOM_COATING);
        for (Monster m : monsters) {
            if (!b.isAlive()) return;
            if (!m.isAlive() || Math.abs(m.getX() - b.getX()) > 64 || Math.abs(m.getY() - b.getY()) > 64) continue;
            if (b.tryHit(m)) {
                int dmg = b.getDamage() + (venomLv > 0 ? 6 * venomLv : 0);
                boolean killed = m.takeHit(dmg, b.getAngle(), b.getKnockback());
                if (venomLv > 0 && !killed) m.applySlow(85);
                addText(m.getX(), m.getY() - m.getHeight(), String.valueOf(dmg), killed ? TEXT_KILL : TEXT_DAMAGE);
                if (!b.isMelee()) addImpact(b);
                shake(b.isMelee() ? 1.8 : 1.2, 4);
                if (killed) onMonsterKilled(m);
            }
        }
    }

    private void explode(double x, double y, int radius, int damage) {
        addEffect(new Effect(Effect.Kind.EXPLOSION, x, y, 28, null, null, Math.max(48, radius * 2)));
        AssetLoader.playSound("explosion");
        shake(6, 18);
        for (Monster m : monsters) {
            if (!m.isAlive() || m.distanceTo(x, y) > radius + m.getWidth() / 2.0) continue;
            boolean killed = m.takeHit(damage, Math.atan2(m.getY() - y, m.getX() - x), 7);
            addText(m.getX(), m.getY() - m.getHeight(), String.valueOf(damage), killed ? TEXT_KILL : TEXT_DAMAGE);
            if (killed) onMonsterKilled(m);
        }
    }

    private void updateDrops() {
        for (DroppedItem d : drops) {
            if (chamberCleared) d.forceMagnet();
            d.update();
            if (d.isReadyToCollect()) {
                String label = player.pickUp(d);
                if (!label.isEmpty()) addPickupText(label);
            }
            if (d.isChestReadyToBurst()) {
                drops.addAll(d.burstChest(rng));
                addText(d.getX(), d.getY() - 20, "Supply chest!", TEXT_KILL);
            }
        }
        drops.removeIf(d -> !d.isAlive());
    }

    private void updateEffects() {
        for (Effect e : effects) {
            e.age++;
            if (e.kind == Effect.Kind.TEXT) e.y -= 0.45;
        }
        effects.removeIf(e -> e.age >= e.life);
        if (shakeTicks > 0) {
            shakeTicks--;
            shakeX = (rng.nextDouble() * 2 - 1) * shakePower;
            shakeY = (rng.nextDouble() * 2 - 1) * shakePower;
            shakePower *= 0.9;
        } else {
            shakeX = shakeY = 0;
        }
    }

    private void addImpact(Bullet b) { addEffect(new Effect(Effect.Kind.IMPACT, b.getX(), b.getY(), 15, null, null, 20)); }

    private void addEffect(Effect e) {
        if (effects.size() < MAX_EFFECTS || e.kind == Effect.Kind.EXPLOSION) effects.add(e);
    }

    private void addText(double x, double y, String text, Color color) {
        addEffect(new Effect(Effect.Kind.TEXT, x + (rng.nextDouble() - 0.5) * 14, y + (rng.nextDouble() - 0.5) * 6, 50, text, color, 0));
    }

    private void addPickupText(String label) {
        pickupStack = tickCount - lastPickupTick < 15 ? (pickupStack + 1) % 5 : 0;
        lastPickupTick = tickCount;
        addEffect(new Effect(Effect.Kind.TEXT, player.getX(), player.getY() - 26 - pickupStack * 8, 55, label, TEXT_LOOT, 0));
    }

    private void shake(double power, int ticks) {
        shakePower = Math.max(shakePower, power);
        shakeTicks = Math.max(shakeTicks, ticks);
    }

    private void showBanner(String title, String subtitle, Color color) {
        banner = new Banner(title, subtitle, color, tickCount, 115);
    }

    private void updateCamera(double follow) {
        double viewW = getViewWidth() / (double) SCALE, viewH = getViewHeight() / (double) SCALE;
        camX += (clamp(player.getX() - viewW / 2, map.getWidthPx() - viewW) - camX) * follow;
        camY += (clamp(player.getY() - viewH / 2, map.getHeightPx() - viewH) - camY) * follow;
    }

    private static double clamp(double v, double max) { return Math.max(0, Math.min(Math.max(0, max), v)); }

    private void finishGame(boolean survived) {
        if (gameOver) return;
        won = survived;
        gameOver = true;
        gameOverTimer = GAME_OVER_DELAY_TICKS;
        releaseAllInput();
        AssetLoader.stopMusic();
        if (survived) AssetLoader.playSound("win");
        requestHudRefresh();
    }

    private void updateGameOverDelay() {
        if (gameOverTimer > 0 && --gameOverTimer == 0 && frame != null) {
            boolean survived = won;
            SwingUtilities.invokeLater(() -> frame.onGameOver(survived));
        }
    }

    public List<FieldShopOffer> getFieldShopOffers() { return new ArrayList<>(fieldShopOffers); }

    private int scaleMerchantCost(int rawCost) {
        return Math.max(3, (int) Math.round(rawCost * player.getPlayerClass().costMult));
    }

    private void rollFieldShopOffers(int ch) {
        fieldShopOffers.clear();
        if (ch <= 0) return;
        int healRoll = rng.nextInt(3);
        String healName = healRoll == 0 ? "Combat Stimulant" : (healRoll == 1 ? "Field Blood Bag" : "Chthonic Elixir");
        int healAmt = healRoll == 0 ? 18 : (healRoll == 1 ? 26 : 36);
        int healCost = scaleMerchantCost((healRoll == 0 ? 3 : (healRoll == 1 ? 5 : 7)) + ch / 2);
        fieldShopOffers.add(new FieldShopOffer(FieldShopOffer.OfferKind.HEAL, healName,
                "Instant +" + healAmt + " HP recovery for this run", healCost, healAmt, null, null));

        GameData.WeaponType[] pool = ch <= 3
                ? new GameData.WeaponType[]{GameData.WeaponType.PISTOL, GameData.WeaponType.REVOLVER,
                        GameData.WeaponType.SHOTGUN, GameData.WeaponType.RIFLE}
                : new GameData.WeaponType[]{GameData.WeaponType.REVOLVER, GameData.WeaponType.SHOTGUN,
                        GameData.WeaponType.RIFLE, GameData.WeaponType.SNIPER, GameData.WeaponType.ROCKET_LAUNCHER};
        GameData.WeaponType primary = player.getEquippedPrimaryGun(), pickWeapon = pool[rng.nextInt(pool.length)];
        for (int tries = 0; tries < 6 && pickWeapon == primary; tries++) pickWeapon = pool[rng.nextInt(pool.length)];
        fieldShopOffers.add(new FieldShopOffer(FieldShopOffer.OfferKind.TEMP_WEAPON,
                pickWeapon.displayName + " (1-Chamber)",
                "Temporary gun +30 Ammo for Chamber " + ch + " only (DMG " + pickWeapon.damage + ")",
                scaleMerchantCost(4 + pickWeapon.ordinal() + ch / 2), 0, pickWeapon, null));

        GameData.TacticalPerk[] perks = GameData.TacticalPerk.values();
        GameData.TacticalPerk pickPerk = perks[rng.nextInt(perks.length)];
        fieldShopOffers.add(new FieldShopOffer(FieldShopOffer.OfferKind.PERK,
                pickPerk.displayName, pickPerk.description, scaleMerchantCost(6 + ch), 0, null, pickPerk));
        Collections.shuffle(fieldShopOffers, rng);
    }

    public String buyFieldShopOffer(int index) {
        if (chamber <= 0) return "Field Merchant only operates inside Chambers.";
        if (index < 0 || index >= fieldShopOffers.size()) return "Invalid offer.";
        FieldShopOffer offer = fieldShopOffers.get(index);
        if (offer.sold) return offer.title + " is already sold out.";
        int scraps = player.getItemCount(GameData.ItemType.SCRAPS);
        if (scraps < offer.costScraps) return "Need " + offer.costScraps + " Scraps (you have " + scraps + ").";
        if (offer.kind == FieldShopOffer.OfferKind.HEAL && player.getHp() >= player.getMaxHp()) return "Health is already full.";
        player.addItem(GameData.ItemType.SCRAPS, -offer.costScraps);
        offer.sold = true;
        String feedback;
        if (offer.kind == FieldShopOffer.OfferKind.HEAL) {
            player.heal(offer.healAmount);
            feedback = "Restored +" + offer.healAmount + " HP";
        } else if (offer.kind == FieldShopOffer.OfferKind.TEMP_WEAPON) {
            player.grantTempChamberWeapon(offer.weapon, 30);
            player.switchWeapon(offer.weapon);
            feedback = "Equipped Temp " + offer.weapon.displayName + " (+30 Ammo, expires next chamber)";
        } else {
            player.addPerk(offer.perk);
            feedback = "Gained Perk: " + offer.perk.displayName;
        }
        AssetLoader.playSound("craft");
        addText(player.getX(), player.getY() - 34, offer.title.toUpperCase(), new Color(0xF5, 0xC4, 0x51));
        return feedback;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = Theme.smooth(g);
        try {
            synchronized (worldLock) {
                drawWorld(g2);
                drawFloatingTexts(g2);
            }
            drawOverlay(g2);
        } finally {
            g2.dispose();
        }
    }

    private void drawWorld(Graphics2D g2) {
        double viewX = camX + shakeX, viewY = camY + shakeY;
        originX = (int) Math.round(viewX * SCALE);
        originY = (int) Math.round(viewY * SCALE);
        Rectangle view = new Rectangle((int) Math.floor(viewX), (int) Math.floor(viewY),
                getViewWidth() / SCALE + 2, getViewHeight() / SCALE + 2);
        AffineTransform old = g2.getTransform();
        g2.translate(-originX, -originY);
        g2.scale(SCALE, SCALE);

        map.drawGround(g2, view);
        if (map.hasSafeCamp() && map.getCampArea().intersects(view)) {
            Rectangle camp = map.getCampArea();
            g2.setColor(new Color(110, 210, 120, 95));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRect(camp.x, camp.y, camp.width, camp.height);
        }
        collectVisible(view);
        drawList.sort(GameMap.DEPTH_ORDER);
        for (GameMap.Drawable d : drawList) d.draw(g2);
        if (showHitbox) {
            g2.setColor(new Color(255, 60, 60, 160));
            for (Rectangle r : solidRects) if (r.intersects(view)) g2.drawRect(r.x, r.y, r.width, r.height);
            for (GameMap.Drawable d : drawList) if (d instanceof Entity entity) entity.drawHitbox(g2);
        }
        for (Effect e : effects) {
            BufferedImage[] frames = e.kind == Effect.Kind.EXPLOSION ? explosionFrames
                    : e.kind == Effect.Kind.IMPACT ? impactFrames : null;
            if (frames == null) continue;
            int half = e.size / 2;
            g2.drawImage(frames[Math.min(frames.length - 1, e.age * frames.length / e.life)],
                    (int) e.x - half, (int) e.y - half, e.size, e.size, null);
        }
        g2.setTransform(old);
    }

    private void collectVisible(Rectangle view) {
        Rectangle near = new Rectangle(view);
        near.grow(48, 64);
        drawList.clear();
        drawList.addAll(map.getPropsInView(view));
        for (HazardTrap t : traps) if (!t.triggered && near.contains(t.x, t.y)) drawList.add(t);
        for (ExitDoor ed : exitDoors) if (near.contains(ed.x, ed.y)) drawList.add(ed);
        for (DroppedItem d : drops) if (near.contains(d.getX(), d.getY())) drawList.add(d);
        for (Monster m : monsters) if (near.contains(m.getX(), m.getY())) drawList.add(m);
        for (Bullet b : bullets) if (near.contains(b.getX(), b.getY())) drawList.add(b);
        for (AllyNpc a : allies) if (near.contains(a.x, a.y)) drawList.add(a);
        drawList.add(player);
    }

    private void drawFloatingTexts(Graphics2D g2) {
        if (!gameOver && chamber == 0 && map.hasSafeCamp()) {
            Point trader = map.getTraderPosition();
            if (player.distanceTo(trader.x, trader.y) < 125) drawWorldPrompt(g2, trader.x, trader.y - 26, "PRESS B - CAMP NPC", Theme.GOLD);
        } else if (!gameOver && chamber > 0) {
            Point trader = map.getTraderPosition();
            if (player.distanceTo(trader.x, trader.y) < 140) drawWorldPrompt(g2, trader.x, trader.y - 46, "PRESS B - FIELD MERCHANT (3 OFFERS)", Theme.GOLD);
        }
        if (!gameOver && chamberCleared && !exitDoors.isEmpty()) {
            ExitDoor nearest = null;
            double bestDist = 78.0;
            for (ExitDoor ed : exitDoors) {
                double d = player.distanceTo(ed.x, ed.y);
                if (d < bestDist) {
                    bestDist = d;
                    nearest = ed;
                }
            }
            if (nearest != null) drawWorldPrompt(g2, nearest.x, nearest.y - 30, "PRESS F - " + nearest.label, nearest.portalColor());
        }
        Composite old = g2.getComposite();
        for (Effect e : effects) {
            if (e.kind != Effect.Kind.TEXT) continue;
            float left = 1f - e.age / (float) e.life;
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, left * 2.5f)));
            int sx = (int) Math.round(e.x * SCALE) - originX - Theme.pixelWidth(e.text, 2) / 2;
            int sy = (int) Math.round(e.y * SCALE) - originY - 10;
            Theme.drawPixelTextShadow(g2, e.text, sx, sy, 2, e.color);
        }
        g2.setComposite(old);
    }

    private void drawWorldPrompt(Graphics2D g2, double wx, double wy, String prompt, Color color) {
        int pw = Theme.pixelWidth(prompt, 2) + 16, ph = 20;
        int tx = (int) Math.round(wx * SCALE) - originX - pw / 2;
        int ty = Math.max(8, (int) Math.round(wy * SCALE) - originY);
        Theme.paintPanel(g2, Theme.Panel.GLASS, tx, ty, pw, ph, 1);
        Theme.drawPixelTextShadow(g2, prompt, tx + 8, ty + 5, 2, color);
    }

    private void drawOverlay(Graphics2D g2) {
        GameOverlay.Viewport view = new GameOverlay.Viewport(getViewWidth(), getViewHeight(), camX, camY);
        overlay.drawNightLighting(g2, view, map, nightAlpha);
        overlay.drawDangerTint(g2, view, tickCount);
        overlay.drawBanner(g2, view, banner, tickCount);
        overlay.drawMiniMap(g2, view, map, chamber, chamberCleared);
        if (gameOver) {
            String stats = "CHAMBER " + highestChamberReached + "   INFECTED KILLED " + kills
                    + "   PERKS " + getTotalPerkLevels() + "   TIME " + GameData.formatRunTime(getElapsedSeconds());
            overlay.drawGameOverScreen(g2, view, won, stats);
            return;
        }
        overlay.drawBossBar(g2, view);
        overlay.drawHint(g2, view);
        if (paused) overlay.drawPauseScreen(g2, view);
        else if (mouseInside) overlay.drawCrosshair(g2, mouseX, mouseY);
    }

    public Player getPlayer() { return player; }
    public int getChamber() { return chamber; }
    public boolean isInSafeCamp() { return chamber == 0; }
    public boolean isChamberCleared() { return chamberCleared; }
    public int getHostilesRemaining() { return Math.max(0, chamberMonstersRemaining + getLiveMonsterCount()); }
    public int getHighestChamberReached() { return highestChamberReached; }
    public int getRunNumber() { return runNumber; }
    public GameData.ChamberType getCurrentChamberType() { return currentChamberType; }
    public int getAllyCount() { return allies.size(); }
    public int getKills() { return kills; }
    public int getElapsedSeconds() { return elapsedTicks * GameData.TICK_MS / 1000; }
    public boolean isPaused() { return paused; }
    public boolean isGameOver() { return gameOver; }
    public boolean isPlayerInBase() { return chamber == 0 && map.hasSafeCamp(); }
    public void setAutoFire(boolean b) { autoFire = b; }

    public int getChamberClearPercent() {
        if (chamber <= 0 || chamberMonstersTotal <= 0) return 100;
        return Math.min(100, Math.max(0, chamberMonstersTotal - getHostilesRemaining()) * 100 / chamberMonstersTotal);
    }

    public int getScore() {
        double base = killScore + highestChamberReached * 150 + getElapsedSeconds() + (won ? 1000 : 0);
        return (int) Math.round(base * (0.5 + 0.25 * getDifficulty()));
    }

    public int getLiveMonsterCount() {
        int n = 0;
        for (Monster m : monsters) if (m.isAlive()) n++;
        return n;
    }

    public int getTotalPerkLevels() {
        int total = 0;
        for (int v : player.getRunPerks().values()) total += v;
        return total;
    }

    private int getDifficulty() { return Math.max(1, Math.min(5, settings.difficulty)); }
    private int getViewWidth() { return getWidth() > 0 ? getWidth() : DEFAULT_VIEW_W; }
    private int getViewHeight() { return getHeight() > 0 ? getHeight() : DEFAULT_VIEW_H; }
    private double getMouseWorldX() { return camX + mouseX / (double) SCALE; }
    private double getMouseWorldY() { return camY + mouseY / (double) SCALE; }
    private Rectangle getCameraView() { return new Rectangle((int) camX, (int) camY, getViewWidth() / SCALE + 1, getViewHeight() / SCALE + 1); }

    public static final class FieldShopOffer {
        public enum OfferKind { HEAL, TEMP_WEAPON, PERK }
        public final OfferKind kind;
        public final String title, detail;
        public final int costScraps, healAmount;
        public final GameData.WeaponType weapon;
        public final GameData.TacticalPerk perk;
        public boolean sold;

        FieldShopOffer(OfferKind kind, String title, String detail, int costScraps,
                       int healAmount, GameData.WeaponType weapon, GameData.TacticalPerk perk) {
            this.kind = kind;
            this.title = title;
            this.detail = detail;
            this.costScraps = costScraps;
            this.healAmount = healAmount;
            this.weapon = weapon;
            this.perk = perk;
        }
    }

    static final class Effect {
        enum Kind { TEXT, IMPACT, EXPLOSION }
        final Kind kind;
        final double x;
        double y;
        final int life, size;
        int age;
        final String text;
        final Color color;

        Effect(Kind kind, double x, double y, int life, String text, Color color, int size) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.life = life;
            this.text = text;
            this.color = color;
            this.size = size;
        }
    }

    record Banner(String title, String subtitle, Color color, int startTick, int length) {}

    static final class HazardTrap implements GameMap.Drawable {
        enum TrapKind {
            SPIKE(105, 42, 68, 100, 18.0, 18, 1, 65, 22.0, 22, 0, new Color(225, 175, 75)),
            TOXIC(20, -1, 0, 0, 20.0, 9, 2, 50, 24.0, 12, 60, new Color(145, 235, 60)),
            SHOCK(110, 46, 72, 106, 19.0, 20, 1, 85, 22.0, 24, 85, new Color(95, 225, 255)),
            MINE(1, -1, -1, -1, 0, 0, 1, 0, 0, 0, 0, new Color(255, 70, 70)),
            FLAME_JET(100, 44, 68, 96, 18.0, 22, 1, 30, 24.0, 26, 0, new Color(255, 145, 45));

            final int period, warnStart, activeStart, activeEnd;
            final double playerRad, monsterRad;
            final int playerBaseDmg, playerChamberDiv, playerSlow, monsterDmg, monsterSlow;
            final Color mapColor;

            TrapKind(int period, int warnStart, int activeStart, int activeEnd, double playerRad, int playerBaseDmg,
                     int playerChamberDiv, int playerSlow, double monsterRad, int monsterDmg, int monsterSlow, Color mapColor) {
                this.period = period;
                this.warnStart = warnStart;
                this.activeStart = activeStart;
                this.activeEnd = activeEnd;
                this.playerRad = playerRad;
                this.playerBaseDmg = playerBaseDmg;
                this.playerChamberDiv = playerChamberDiv;
                this.playerSlow = playerSlow;
                this.monsterRad = monsterRad;
                this.monsterDmg = monsterDmg;
                this.monsterSlow = monsterSlow;
                this.mapColor = mapColor;
            }
        }

        final TrapKind kind;
        final double x, y;
        int timer, armTicks = -1, hitCooldown;
        boolean triggered;

        HazardTrap(TrapKind kind, double x, double y, int phaseOffset) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.timer = Math.floorMod(phaseOffset, 180);
        }

        void update() {
            timer++;
            if (hitCooldown > 0) hitCooldown--;
            if (armTicks > 0) armTicks--;
        }

        boolean isTriggeringDamage() {
            if (triggered || kind.activeStart < 0) return false;
            int m = timer % kind.period;
            return m >= kind.activeStart && m <= kind.activeEnd;
        }

        boolean isWarningPhase() {
            if (kind == TrapKind.MINE) return armTicks > 0;
            if (kind.warnStart < 0) return false;
            int m = timer % kind.period;
            return m >= kind.warnStart && m < kind.activeStart;
        }

        @Override public double getSortY() { return y - 10; }

        @Override
        public void draw(Graphics2D g2) {
            if (triggered) return;
            int ix = (int) Math.round(x), iy = (int) Math.round(y);
            boolean active = isTriggeringDamage(), warn = isWarningPhase();
            Stroke oldStroke = g2.getStroke();
            switch (kind) {
                case SPIKE -> {
                    g2.setColor(new Color(40, 38, 44));
                    g2.fillRect(ix - 15, iy - 11, 30, 22);
                    g2.setColor(active ? new Color(220, 75, 65) : (warn ? new Color(245, 165, 50) : new Color(78, 76, 86)));
                    g2.drawRect(ix - 15, iy - 11, 30, 22);
                    for (int sx = -10; sx <= 10; sx += 10) {
                        for (int sy = -6; sy <= 6; sy += 6) {
                            if (active) {
                                g2.setColor(new Color(235, 232, 240));
                                g2.fillPolygon(new int[]{ix + sx - 4, ix + sx, ix + sx + 4},
                                        new int[]{iy + sy + 4, iy + sy - 10, iy + sy + 4}, 3);
                                g2.setColor(new Color(185, 55, 55));
                                g2.fillRect(ix + sx - 1, iy + sy - 9, 2, 4);
                            } else {
                                g2.setColor(warn ? new Color(250, 120, 50) : new Color(20, 20, 24));
                                g2.fillOval(ix + sx - 2, iy + sy - 2, 5, 5);
                            }
                        }
                    }
                }
                case TOXIC -> {
                    g2.setColor(new Color(48, 128, 28, 175));
                    g2.fillOval(ix - 20, iy - 13, 40, 26);
                    g2.setColor(new Color(95, 195, 45, 130));
                    g2.fillOval(ix - 12, iy - 8, 24, 15);
                    g2.setColor(active ? new Color(185, 255, 85, 235) : new Color(135, 225, 55, 190));
                    g2.drawOval(ix - 20, iy - 13, 40, 26);
                    g2.fillOval(ix - 7 + (timer / 11) % 7, iy + 2 - (timer / 4) % 10, 5, 5);
                    g2.fillOval(ix + 5 - (timer / 13) % 6, iy + 3 - ((timer + 9) / 5) % 9, 4, 4);
                }
                case SHOCK -> {
                    if (warn || active) {
                        int pulse = (timer % 24) / 6;
                        g2.setColor(active ? new Color(115, 240, 255, 165) : new Color(90, 180, 245, 110));
                        g2.drawOval(ix - 20 - pulse, iy - 13 - pulse / 2, 40 + pulse * 2, 26 + pulse);
                    }
                    g2.setColor(new Color(32, 48, 72, 195));
                    g2.fillOval(ix - 17, iy - 11, 34, 22);
                    g2.setColor(active ? new Color(135, 245, 255) : (warn ? new Color(95, 185, 240) : new Color(68, 100, 140)));
                    g2.drawOval(ix - 17, iy - 11, 34, 22);
                    g2.fillRect(ix - 4, iy - 10, 8, 12);
                    g2.setColor(active ? new Color(220, 255, 255) : new Color(110, 185, 225));
                    g2.fillOval(ix - 3, iy - 12, 6, 5);
                    if (active) {
                        g2.setStroke(new BasicStroke(1.8f));
                        g2.drawLine(ix - 15, iy - 1, ix - 4, iy + 3);
                        g2.drawLine(ix - 4, iy + 3, ix + 5, iy - 4);
                        g2.drawLine(ix + 5, iy - 4, ix + 15, iy + 1);
                        g2.drawLine(ix, iy - 10, ix + 3, iy + 10);
                    }
                }
                case MINE -> {
                    g2.setColor(armTicks > 0 ? new Color(255, 70, 55, 180) : new Color(175, 55, 45, 70));
                    int ringR = armTicks > 0 ? 18 + (timer % 6) : 14;
                    g2.drawOval(ix - ringR, iy - ringR * 2 / 3, ringR * 2, ringR * 4 / 3);
                    g2.setColor(new Color(58, 56, 52));
                    g2.fillOval(ix - 9, iy - 6, 18, 12);
                    g2.setColor(new Color(26, 26, 28));
                    g2.drawOval(ix - 9, iy - 6, 18, 12);
                    boolean blink = armTicks > 0 ? (armTicks / 2) % 2 == 0 : (timer / 14) % 2 == 0;
                    g2.setColor(blink ? new Color(255, 60, 45) : new Color(125, 25, 25));
                    g2.fillOval(ix - 3, iy - 3, 6, 6);
                }
                case FLAME_JET -> {
                    g2.setColor(new Color(46, 36, 32));
                    g2.fillOval(ix - 14, iy - 9, 28, 18);
                    g2.setColor(active ? new Color(255, 155, 55) : (warn ? new Color(235, 120, 45) : new Color(86, 62, 52)));
                    g2.drawOval(ix - 14, iy - 9, 28, 18);
                    g2.setColor(new Color(22, 18, 18));
                    g2.fillOval(ix - 8, iy - 5, 16, 10);
                    if (warn) {
                        g2.setColor(new Color(255, 120, 35, 190));
                        g2.fillOval(ix - 5, iy - 3, 10, 6);
                    } else if (active) {
                        int flicker = (timer % 4) * 2;
                        g2.setColor(new Color(255, 105, 25, 200));
                        g2.fillOval(ix - 16, iy - 14 - flicker / 2, 32, 24);
                        g2.setColor(new Color(255, 195, 55, 225));
                        g2.fillOval(ix - 10, iy - 16 - flicker, 20, 22);
                        g2.setColor(new Color(255, 248, 195, 235));
                        g2.fillOval(ix - 5, iy - 11 - flicker / 2, 10, 12);
                    }
                }
            }
            g2.setStroke(oldStroke);
        }
    }

    static final class ExitDoor implements GameMap.Drawable {
        final GameData.ChamberType type;
        final int nextChamber;
        final double x, y;
        final String label;

        ExitDoor(GameData.ChamberType type, int nextChamber, double x, double y, String label) {
            this.type = type;
            this.nextChamber = nextChamber;
            this.x = x;
            this.y = y;
            this.label = label;
        }

        Color portalColor() {
            return switch (type) {
                case SAFE_CAMP, VICTORY_EXTRACT -> new Color(110, 225, 115);
                case TACTICAL_PERK -> new Color(90, 210, 255);
                case SALVAGE_VAULT -> Theme.GOLD;
                case FIELD_MEDIC -> new Color(130, 230, 120);
                case ELITE_HAZARD -> new Color(240, 110, 60);
                case ENDLESS_ABYSS -> new Color(195, 125, 255);
                default -> Theme.RED;
            };
        }

        @Override public double getSortY() { return y + 10; }

        @Override
        public void draw(Graphics2D g2) {
            int ix = (int) Math.round(x), iy = (int) Math.round(y);
            Color c = portalColor();
            g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 55));
            g2.fillOval(ix - 18, iy - 10, 36, 20);
            g2.setColor(new Color(24, 24, 30, 230));
            g2.fillOval(ix - 13, iy - 7, 26, 14);
            g2.setColor(c);
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(ix - 14, iy - 8, 28, 16);
            Theme.drawPixelTextShadow(g2, type.displayName.toUpperCase(), ix - Theme.pixelWidth(type.displayName, 1) / 2, iy - 16, 1, c);
        }
    }

    final class AllyNpc implements GameMap.Drawable {
        final int slot;
        double x, y, aimAngle;
        boolean facingLeft, moving;
        int animTick, fireCooldown;

        AllyNpc(int slot, double x, double y) {
            this.slot = slot;
            this.x = x;
            this.y = y;
        }

        void update(int squadLv, boolean allowFire) {
            animTick++;
            if (fireCooldown > 0) fireCooldown--;
            if (Math.hypot(player.getX() - x, player.getY() - y) > 250) {
                x = player.getX();
                y = player.getY();
                moving = false;
            } else {
                double formAngle = slot * (Math.PI / 2.0) + 0.75, formDist = 38.0 + (slot % 2) * 14.0;
                double goalX = player.getX() + Math.cos(formAngle) * formDist, goalY = player.getY() + Math.sin(formAngle) * formDist;
                double d = Math.hypot(goalX - x, goalY - y);
                if (d > 10) {
                    double step = player.getMoveSpeed() * 1.05, moveAngle = map.getChaseAngle(x, y + 8, goalX, goalY + 8, 14);
                    double dx = Math.cos(moveAngle) * Math.min(step, d), dy = Math.sin(moveAngle) * Math.min(step, d);
                    double oldX = x, oldY = y;
                    if (dx != 0 && !map.isBlocked(new Rectangle((int) Math.round(x + dx) - 7, (int) Math.round(y), 14, 12))) x += dx;
                    if (dy != 0 && !map.isBlocked(new Rectangle((int) Math.round(x) - 7, (int) Math.round(y + dy), 14, 12))) y += dy;
                    moving = Math.hypot(x - oldX, y - oldY) > 0.1;
                    facingLeft = dx < -0.05 || (Math.abs(dx) <= 0.05 && facingLeft);
                } else {
                    moving = false;
                }
            }

            if (!allowFire || !player.isAlive()) return;
            Monster best = null;
            double bestDist = 185.0;
            for (Monster m : monsters) {
                if (!m.isAlive()) continue;
                double d = Math.hypot(m.getX() - x, m.getY() - y);
                if (d < bestDist && map.hasClearPath(x, y, m.getX(), m.getY(), 4)) {
                    bestDist = d;
                    best = m;
                }
            }
            if (best != null) {
                aimAngle = Math.atan2(best.getY() - y, best.getX() - x);
                facingLeft = Math.cos(aimAngle) < 0;
                if (fireCooldown <= 0 && bullets.size() < 320) {
                    int overdrive = player.getPerkLevel(GameData.TacticalPerk.SQUAD_OVERDRIVE);
                    int npcDmg = (int) Math.round((12 + squadLv * 4) * (1.0 + 0.35 * overdrive));
                    Bullet b = Bullet.shot(x + Math.cos(aimAngle) * 10, y + Math.sin(aimAngle) * 10, aimAngle, GameData.WeaponType.RIFLE, npcDmg);
                    b.setMap(map);
                    bullets.add(b);
                    addEffect(new Effect(Effect.Kind.IMPACT, x + Math.cos(aimAngle) * 12, y + Math.sin(aimAngle) * 12, 10, null, null, 12));
                    fireCooldown = Math.max(9, (int) Math.round(Math.max(14, 26 - squadLv * 2) * Math.max(0.45, 1.0 - 0.25 * overdrive)));
                }
            } else if (!moving) {
                aimAngle = facingLeft ? Math.PI : 0;
            }
        }

        @Override public double getSortY() { return y + 10; }

        @Override
        public void draw(Graphics2D g2) {
            g2.setColor(new Color(0, 0, 0, 55));
            g2.fillOval((int) Math.round(x - 7), (int) Math.round(y + 8), 14, 5);
            g2.setColor(new Color(100, 225, 115, 125));
            g2.setStroke(new BasicStroke(1f));
            g2.drawOval((int) Math.round(x - 8), (int) Math.round(y + 7), 16, 6);

            BufferedImage[] frames = AssetLoader.loadStrip(slot % 2 == 0 ? "characters/npc2_idle.png" : "characters/npc_idle.png", 6);
            BufferedImage frame = frames[(animTick / 8) % frames.length];
            int bob = moving && (animTick / 4) % 2 == 1 ? -1 : 0, w = frame.getWidth(), h = frame.getHeight();
            int left = (int) Math.round(x - w / 2.0), top = (int) Math.round(y + 19 - h) + bob;
            g2.drawImage(frame, facingLeft ? left + w : left, top, facingLeft ? -w : w, h, null);

            GameData.WeaponType weapon = slot == 0 && player.getLevel(GameData.RecipeKind.BASE_TURRET) == 0
                    ? GameData.WeaponType.PISTOL : GameData.WeaponType.RIFLE;
            BufferedImage gunImg = AssetLoader.loadStrip(weapon.flickerStrip(), 7)[6];
            AffineTransform old = g2.getTransform();
            g2.translate(x + (facingLeft ? -4 : 4), y + 7 + bob);
            g2.rotate(aimAngle);
            if (Math.cos(aimAngle) < 0) g2.scale(1, -1);
            g2.drawImage(gunImg, -12, -17, null);
            g2.setTransform(old);
        }
    }
}
