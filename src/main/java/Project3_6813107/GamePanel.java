// Tapatsawin Chaieua          6813105
// Thanapon Thanadunpremdet    6813106
// Tanawat Wongwad             6813107
// Natthanon Achachan          6813241
package Project3_6813107;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.MultipleGradientPaint;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
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
import java.awt.geom.Arc2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * The game screen. It owns the world (map, player, monsters, bullets, loot), runs it with two
 * threads and draws it:
 *
 *   gameThread         every 16 ms: updateGame() (one simulation step) and repaint()
 *   waveSpawnerThread  every 2-3 s: asks for a new batch of monsters (the game thread creates them)
 *
 * The Swing thread only sets input fields (keys, mouse) and paints. The lists are
 * CopyOnWriteArrayLists so painting and the HUD can loop over them while the game thread changes
 * them, and worldLock makes painting and one update step take turns, so a frame never shows a
 * half-finished step.
 *
 * The panel also works without a window (frame == null) so tests can call updateGame() and
 * paint() directly; then the spawner timer runs inside updateGame() instead of in its thread.
 */
public final class GamePanel extends JPanel
        implements Runnable, KeyListener, MouseListener, MouseMotionListener, MouseWheelListener {

    private static final long serialVersionUID = 1L;

    private static final int SCALE = GameData.SCALE;
    private static final int DEFAULT_VIEW_W = 1100, DEFAULT_VIEW_H = 540;   // used before the panel is laid out
    private static final long TICK_NANOS = GameData.TICK_MS * 1_000_000L;
    private static final int TIME_LIMIT_TICKS = GameData.TIME_LIMIT_SECONDS * 1000 / GameData.TICK_MS;
    private static final int WAVE_TICKS = GameData.WAVE_SECONDS * 1000 / GameData.TICK_MS;
    private static final int HUD_REFRESH_TICKS = 15;              // about 4 HUD updates per second
    private static final int SPAWN_TICKS_WITHOUT_THREAD = 156;    // 2.5 s, when tests drive updateGame() directly
    private static final int MAX_SPAWNS_PER_TICK = 2;             // a big batch trickles in over a few frames
    private static final int BOSS_WARNING_TICKS = 150;            // warning banner before the boss walks in
    private static final int GAME_OVER_DELAY_TICKS = 110;         // show "YOU DIED"/"YOU SURVIVED" before the summary
    private static final int MAX_EFFECTS = 250;
    private static final int WHEEL_DELAY_TICKS = 8;               // trackpads send many wheel events per swipe

    private static final Color BACKGROUND = new Color(18, 16, 20);
    private static final Color TEXT_DAMAGE = Theme.TEXT;
    private static final Color TEXT_KILL = Theme.GOLD;
    private static final Color TEXT_HURT = Theme.RED;
    private static final Color TEXT_LOOT = new Color(0xB4DC70);   // light green from the kit's infection bar
    private static final Color BANNER_WAVE = Theme.GOLD;
    private static final Color BANNER_BOSS = Theme.RED;
    private static final Color GLASS = new Color(14, 16, 20, 215);   // dark see-through panels over the world
    private static final Color SCRIM = new Color(8, 9, 12, 165);     // darkens the world behind pause / end screens
    // overlay text uses the kit's pixel font (Theme.drawPixelText); this font is for the pause card's sentences
    private static final Font FONT_HELP = Theme.font(Font.PLAIN, 14);

    // ------------------------------------------------------------------ world
    // transient: Swing panels are Serializable, but a running game is never written to a stream
    private final transient GameData.GameSettings settings;
    private final GameFrame frame;              // null in headless tests
    private final transient GameMap map;
    private final transient Player player;
    private final transient List<Monster> monsters = new CopyOnWriteArrayList<>();
    private final transient List<Bullet> bullets = new CopyOnWriteArrayList<>();
    private final transient List<DroppedItem> drops = new CopyOnWriteArrayList<>();
    private final transient List<Effect> effects = new CopyOnWriteArrayList<>();
    private final transient Object worldLock = new Object();
    private final Random rng = new Random();   // used by the game thread only
    private final AtomicInteger pendingSpawns = new AtomicInteger();   // written by the spawner, read by the game thread
    private final transient List<Rectangle> solidRects;  // for "Show Hitbox"

    // ------------------------------------------------------------------ threads and run state
    private transient Thread gameThread;
    private transient Thread waveSpawnerThread;
    private volatile boolean running;
    private volatile boolean paused;
    private volatile boolean gameOver;
    private volatile boolean won;
    private volatile int tickCount;            // every update step, also after game over (animations)
    private volatile int elapsedTicks;         // survival time; stops while the player dies
    private volatile int wave = 1;
    private volatile int kills;
    private volatile int killScore;
    private int bossTimer;                     // > 0: an apex mutant walks in when it reaches 0
    private int gameOverTimer;
    private boolean raidActive;
    private float nightAlpha;                  // 0 = daylight, 1 = nightfall
    private transient BufferedImage nightMask; // world-scale darkness layer punched with light pools
    private transient volatile Banner banner;

    // ------------------------------------------------------------------ input (set on the Swing thread, read by the game thread)
    private volatile boolean keyUp, keyDown, keyLeft, keyRight;
    private volatile boolean mouseFiring;
    private volatile boolean mouseInside;
    private volatile int mouseX = DEFAULT_VIEW_W / 2 + 80, mouseY = DEFAULT_VIEW_H / 2;
    private volatile boolean grenadeRequested, medkitRequested;
    private volatile int weaponSlotRequested = -1;
    private volatile int weaponCycleRequested;
    private int lastWheelTick = -100;          // Swing thread only
    private volatile boolean autoFire, showHitbox;

    // ------------------------------------------------------------------ camera and screen shake (world pixels)
    private volatile double camX, camY;
    private volatile double shakeX, shakeY;
    private int shakeTicks;
    private double shakePower;
    private int lastPickupTick, pickupStack;   // stacking of pickup labels

    // ------------------------------------------------------------------ drawing helpers (Swing thread only)
    private final transient List<GameMap.Drawable> drawList = new ArrayList<>();
    private final transient BufferedImage[] explosionFrames = AssetLoader.getExplosionFrames();
    private final transient BufferedImage[] impactFrames = AssetLoader.getImpactFrames();
    private transient BufferedImage vignette;            // red edge for low health, rebuilt only when the size changes
    private int originX, originY;              // screen pixel of world (0, 0) in the frame being painted

    // ------------------------------------------------------------------ small helper types
    /** A short-lived visual: floating text, bullet impact or explosion (world coordinates). */
    static final class Effect {
        enum Kind { TEXT, IMPACT, EXPLOSION }

        final Kind kind;
        final double x;
        double y;
        final int life;
        int age;
        final String text;
        final Color color;
        final int size;

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

    /** The big centred message ("WAVE 3", "BOSS INCOMING"). Immutable, so it is swapped in one step. */
    static final class Banner {
        final String title, subtitle;
        final Color color;
        final int startTick, length;

        Banner(String title, String subtitle, Color color, int startTick, int length) {
            this.title = title;
            this.subtitle = subtitle;
            this.color = color;
            this.startTick = startTick;
            this.length = length;
        }
    }

    // ------------------------------------------------------------------ construction
    public GamePanel(GameData.GameSettings settings, GameFrame frame) {
        this.settings = settings == null ? new GameData.GameSettings() : settings;
        this.frame = frame;
        GameData.MapType mapType = this.settings.map == null ? GameData.MapType.WASTELAND_HIGHWAY : this.settings.map;
        map = new GameMap(mapType, mapType.seed);
        solidRects = map.getSolidRects();
        Point spawn = map.getPlayerSpawn();
        player = new Player(this.settings, spawn.x, spawn.y);
        player.setMap(map);              // without this the player walks through walls
        player.pushOutOfWalls();
        autoFire = this.settings.autoFire;
        showHitbox = this.settings.showHitbox;
        AssetLoader.setSoundEnabled(this.settings.soundOn);
        AssetLoader.setMusicEnabled(this.settings.musicOn);
        AssetLoader.setVolume(this.settings.volume);

        setBackground(BACKGROUND);
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);   // Tab must not move the keyboard focus away from the game
        addKeyListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);
        addMouseWheelListener(this);
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                releaseAllInput();   // otherwise a key held while switching windows stays "down"
            }
        });
        if (!GraphicsEnvironment.isHeadless()) {
            // hide the system cursor: the crosshair sprite is drawn at the mouse instead
            BufferedImage blank = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            setCursor(Toolkit.getDefaultToolkit().createCustomCursor(blank, new Point(0, 0), "blank"));
        }
        updateCamera(1.0);   // start centred on the player
        preloadSprites();
        spawnInitialScavengeWorld();
        showBanner("DAY 1 - DAWN", "Knife equipped: scavenge outside & build camp [B]", BANNER_WAVE);
    }

    /** Populates outer map zones with roaming zombies and supply chests right at run start. */
    private void spawnInitialScavengeWorld() {
        for (int i = 0; i < 3; i++) {
            Point p = map.randomOuterLootPoint(rng);
            DroppedItem chest = new DroppedItem(GameData.ItemType.CHEST, 1, p.x, p.y, player);
            chest.setMap(map);
            drops.add(chest);
        }
        for (int i = 0; i < 6; i++) {
            Point p = map.randomOuterLootPoint(rng);
            Monster m = new Monster(GameData.MonsterType.ZOMBIE_NORMAL, p.x, p.y, map, player, GameData.monsterHpScale(1, getDifficulty()));
            m.setRaiding(false);
            monsters.add(m);
        }
    }

    /**
     * Loads every monster and weapon animation now, so the first runner or boss does not make the
     * game stutter while its pictures are read from disk (the loader caches them).
     */
    private static void preloadSprites() {
        for (GameData.MonsterType t : GameData.MonsterType.values()) {
            AssetLoader.loadStrip(t.runStrip(), 8);
            AssetLoader.loadStrip(t.hitStrip(), 3);
            AssetLoader.loadStrip(t.deathStrip(), 8);
        }
        AssetLoader.loadStrip("characters/bandit_idle.png", 6);
        for (GameData.WeaponType w : GameData.WeaponType.values()) {
            AssetLoader.loadStrip(w.flickerStrip(), 7);
            if (w.shootStrip != null) {
                AssetLoader.loadStrip(w.shootStrip, w.shootFrames);
            }
        }
        for (GameData.ItemType t : GameData.ItemType.values()) {
            AssetLoader.getItemFrames(t);
        }
        AssetLoader.getAlertFrames();
    }

    // ------------------------------------------------------------------ threads
    /** Starts the game loop and the wave spawner (and the music). Safe to call once. */
    public void startGame() {
        if (running) {
            return;
        }
        running = true;
        AssetLoader.playMusic("bgm");
        gameThread = new Thread(this, "gameThread");
        waveSpawnerThread = new Thread(this::runWaveSpawner, "waveSpawnerThread");
        gameThread.setDaemon(true);          // a safety net only: stopGame() ends both threads properly
        waveSpawnerThread.setDaemon(true);
        gameThread.start();
        waveSpawnerThread.start();
        requestFocusInWindow();
    }

    /** The game loop: a fixed 16 ms step. If the computer falls behind, it catches up a few steps. */
    @Override
    public void run() {
        long nextTick = System.nanoTime();
        int ticksSinceHud = 0;
        while (running) {
            try {
                updateGame();
            } catch (RuntimeException ex) {
                // one broken step must not kill the whole game; log it and keep going
                System.err.println("Game step failed: " + ex);
                ex.printStackTrace();
            }
            repaint();   // also while paused, so the pause screen is drawn
            if (++ticksSinceHud >= HUD_REFRESH_TICKS) {
                ticksSinceHud = 0;
                requestHudRefresh();
            }
            nextTick += TICK_NANOS;
            long sleepNanos = nextTick - System.nanoTime();
            if (sleepNanos < -5 * TICK_NANOS) {
                nextTick = System.nanoTime();   // far behind (e.g. the PC was busy): do not race to catch up
                sleepNanos = 0;
            }
            try {
                if (sleepNanos > 0) {
                    Thread.sleep(sleepNanos / 1_000_000, (int) (sleepNanos % 1_000_000));
                }
            } catch (InterruptedException ex) {
                return;   // stopGame() interrupts us
            }
        }
    }

    /** The second thread: wakes every 2-3 seconds and asks for a batch of monsters. */
    private void runWaveSpawner() {
        Random timing = new Random();
        while (running) {
            try {
                Thread.sleep(2000 + timing.nextInt(1001));
            } catch (InterruptedException ex) {
                return;   // stopGame() interrupts us
            }
            if (!paused && !gameOver && player.isAlive()) {
                requestSpawns();
            }
        }
    }

    /**
     * Only adds to a counter: the game thread creates the monsters itself, so the spawner never
     * touches the world while it is being updated or drawn.
     */
    private void requestSpawns() {
        pendingSpawns.addAndGet(GameData.spawnBatchSize(wave, getDifficulty(), settings.doubleSpawn));
    }

    public void pauseGame() {
        if (gameOver) {
            return;
        }
        paused = true;
        releaseAllInput();
        requestHudRefresh();
    }

    public void resumeGame() {
        paused = false;
        requestFocusInWindow();
        requestHudRefresh();
    }

    /** Ends both threads (flag + interrupt + join) and stops the music. Safe to call more than once. */
    public void stopGame() {
        running = false;
        AssetLoader.stopMusic();
        stopThread(waveSpawnerThread);
        stopThread(gameThread);
    }

    private static void stopThread(Thread t) {
        if (t == null || t == Thread.currentThread()) {
            return;
        }
        t.interrupt();
        try {
            t.join(1000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void requestHudRefresh() {
        if (frame != null) {
            SwingUtilities.invokeLater(frame::refreshHud);
        }
    }

    // ------------------------------------------------------------------ one simulation step
    /** One fixed 16 ms step of the whole game (nothing happens while paused). Public so tests can drive it. */
    public void updateGame() {
        if (paused) {
            return;
        }
        synchronized (worldLock) {
            tickCount++;
            if (gameOver) {
                updateEffects();
                updateGameOverDelay();
                return;
            }
            updateClock();
            if (gameOver) {
                return;
            }
            if (!running && tickCount % SPAWN_TICKS_WITHOUT_THREAD == 0 && player.isAlive()) {
                requestSpawns();   // no spawner thread (tests): same timer, driven by the ticks
            }
            spawnPendingMonsters();
            updatePlayer();
            updateBaseSystems();
            map.update();
            updateMonsters();
            updateBullets();
            updateDrops();
            updateEffects();
            updateCamera(0.15);
            if (player.isDeathAnimationDone()) {
                finishGame(false);
            }
        }
    }

    /** Survival time, wave changes, Scavenge/Raid phase transitions, boss timer and win. */
    private void updateClock() {
        if (!player.isAlive()) {
            return;   // the clock stops while the death animation plays
        }
        elapsedTicks++;
        if (elapsedTicks >= TIME_LIMIT_TICKS) {
            finishGame(true);
            return;
        }
        int newWave = 1 + elapsedTicks / WAVE_TICKS;
        if (newWave != wave) {
            startWave(newWave);
        }
        int secInWave = getElapsedSeconds() % GameData.WAVE_SECONDS;
        int raidStartSec = wave == 1 ? 16 : 14;
        if (!raidActive && secInWave >= raidStartSec) {
            startRaidPhase();
        }
        if (raidActive) {
            nightAlpha = Math.min(1f, nightAlpha + 0.012f);
        } else {
            nightAlpha = Math.max(0f, nightAlpha - 0.015f);
        }
        if (bossTimer > 0 && --bossTimer == 0) {
            spawnBoss();
        }
    }

    private void startWave(int number) {
        wave = number;
        raidActive = false;
        spawnOuterSupplyChests(2);
        AssetLoader.playSound("wave");
        if (number % GameData.BOSS_EVERY_WAVES == 0) {
            showBanner("DAY " + number + " - BLOOD MOON", "Warning: an Apex Mutant is tracking your camp!", BANNER_BOSS);
            bossTimer = BOSS_WARNING_TICKS;
        } else if (number == GameData.TIME_LIMIT_SECONDS / GameData.WAVE_SECONDS) {
            showBanner("FINAL DAY " + number, "Hold out until extraction arrives!", BANNER_WAVE);
        } else {
            showBanner("DAY " + number + " - DAWN", "Daylight: scavenge outer zones & fortify camp [B]", BANNER_WAVE);
        }
    }

    private void startRaidPhase() {
        raidActive = true;
        AssetLoader.playSound("wave");
        showBanner("NIGHTFALL", "Darkness falls - the infected march on your camp!", BANNER_BOSS);
        for (Monster m : monsters) {
            if (m.isAlive()) {
                m.setRaiding(true);
            }
        }
    }

    private void spawnOuterSupplyChests(int desiredAdd) {
        int currentChests = 0;
        for (DroppedItem d : drops) {
            if (d.getType() == GameData.ItemType.CHEST) {
                currentChests++;
            }
        }
        int toSpawn = Math.min(desiredAdd, Math.max(0, 5 - currentChests));
        for (int i = 0; i < toSpawn; i++) {
            Point p = map.randomOuterLootPoint(rng);
            DroppedItem chest = new DroppedItem(GameData.ItemType.CHEST, 1, p.x, p.y, player);
            chest.setMap(map);
            drops.add(chest);
        }
    }

    /** Active camp defenses & facilities: guard posts, medical tent regen, and supply depot production. */
    private void updateBaseSystems() {
        int turretLv = player.getLevel(GameData.RecipeKind.BASE_TURRET);
        int wallLv = player.getLevel(GameData.RecipeKind.BASE_WALL);
        int medicLv = player.getLevel(GameData.RecipeKind.BASE_MEDIC);
        int farmLv = player.getLevel(GameData.RecipeKind.BASE_FARM);
        if (map.applyBaseUpgrades(turretLv, wallLv, medicLv, farmLv)) {
            solidRects.clear();
            solidRects.addAll(map.getSolidRects());
        }

        if (turretLv > 0 && tickCount % Math.max(12, 32 - turretLv * 5) == 0 && bullets.size() < 320) {
            double maxRange = 220.0 + turretLv * 20.0;
            for (Point gp : map.getGuardPosts()) {
                Monster best = null;
                double bestDist = maxRange;
                for (Monster m : monsters) {
                    if (!m.isAlive()) {
                        continue;
                    }
                    double d = Math.hypot(m.getX() - gp.x, m.getY() - gp.y);
                    if (d < bestDist && map.hasClearPath(gp.x, gp.y, m.getX(), m.getY(), 4)) {
                        bestDist = d;
                        best = m;
                    }
                }
                if (best != null) {
                    double angle = Math.atan2(best.getY() - gp.y, best.getX() - gp.x);
                    Bullet shot = Bullet.shot(gp.x, gp.y - 6, angle, GameData.WeaponType.RIFLE, 10 + turretLv * 5);
                    shot.setMap(map);
                    bullets.add(shot);
                    addEffect(new Effect(Effect.Kind.IMPACT, gp.x + Math.cos(angle) * 10, gp.y - 6 + Math.sin(angle) * 10, 10, null, null, 14));
                }
            }
        }

        if (medicLv > 0 && player.isAlive() && tickCount % 75 == 0
                && map.getCampArea().contains((int) player.getX(), (int) player.getY())) {
            boolean wasPoisoned = player.isPoisoned();
            int before = player.getHp();
            player.heal(2 + medicLv * 2);
            player.curePoison();
            int gained = player.getHp() - before;
            if (gained > 0 || wasPoisoned) {
                addText(player.getX(), player.getY() - 24, gained > 0 ? "+" + gained + " Med Tent" : "Cured!", TEXT_LOOT);
            }
        }

        if (farmLv > 0 && tickCount % Math.max(540, 1080 - farmLv * 180) == 0 && drops.size() < 200) {
            Rectangle camp = map.getCampArea();
            double sx = camp.x + camp.width - 72;
            double sy = camp.y + 84;
            int count = farmLv >= 3 ? 2 : 1;
            for (int i = 0; i < count; i++) {
                int roll = rng.nextInt(10);
                GameData.ItemType itemType = roll < 5 ? GameData.ItemType.AMMO
                        : (roll < 8 ? GameData.ItemType.FOOD : GameData.ItemType.MEDKIT);
                int amount = itemType == GameData.ItemType.AMMO ? 12 + farmLv * 4 : 1;
                DroppedItem sup = new DroppedItem(itemType, amount, sx, sy, player);
                sup.setMap(map);
                sup.scatter(rng);
                drops.add(sup);
            }
            addText(sx, sy - 14, "+Base Supply", TEXT_LOOT);
        }
    }

    // ------------------------------------------------------------------ spawning
    private void spawnPendingMonsters() {
        int room = GameData.MAX_LIVE_MONSTERS - getLiveMonsterCount();
        if (room <= 0) {
            pendingSpawns.set(0);   // the arena is full: forget extra requests so they do not pour in later
            return;
        }
        int count = Math.min(MAX_SPAWNS_PER_TICK, Math.min(room, pendingSpawns.get()));
        for (int i = 0; i < count; i++) {
            spawnMonster(GameData.MonsterType.randomForWave(wave, rng));
        }
        pendingSpawns.addAndGet(-count);
    }

    /** New monsters appear outside camera view during raids, or in outer zones during scavenge phase. */
    private Monster spawnMonster(GameData.MonsterType type) {
        boolean boss = type == GameData.MonsterType.ZOMBIE_BOSS;
        boolean raid = raidActive || boss;
        Point p = raid ? map.randomSpawnPoint(rng, getCameraView()) : map.randomOuterLootPoint(rng);
        Monster m = new Monster(type, p.x, p.y, map, player, GameData.monsterHpScale(wave, getDifficulty()));
        m.setRaiding(raid);
        monsters.add(m);
        return m;
    }

    private void spawnBoss() {
        spawnMonster(GameData.MonsterType.ZOMBIE_BOSS);
        AssetLoader.playSound("boss_roar");
        showBanner("APEX MUTANT", "A giant mutant has breached the perimeter!", BANNER_BOSS);
        shake(5, 30);
    }

    // ------------------------------------------------------------------ player
    private void updatePlayer() {
        if (!player.isAlive()) {
            player.update();   // only the death animation
            return;
        }
        double aimX = getMouseWorldX(), aimY = getMouseWorldY();
        handleActionRequests(aimX, aimY);
        player.setMoveInput(keyUp, keyDown, keyLeft, keyRight);
        Monster target = autoFire && !mouseFiring ? findAutoFireTarget() : null;
        if (target != null) {
            player.setAim(target.getX(), target.getY());
        } else {
            player.setAim(aimX, aimY);
        }
        player.update();
        if (mouseFiring) {
            fireAt(aimX, aimY);
        } else if (target != null) {
            fireAt(target.getX(), target.getY());
        }
    }

    /** Key presses that change the world are only remembered by the Swing thread and done here. */
    private void handleActionRequests(double aimX, double aimY) {
        int slot = weaponSlotRequested;
        if (slot >= 0) {
            weaponSlotRequested = -1;
            if (slot < GameData.HOTBAR_WEAPONS.length) {
                GameData.WeaponType w = GameData.HOTBAR_WEAPONS[slot];
                if (player.ownsWeapon(w)) {
                    player.switchWeapon(w);
                } else {
                    player.showHint("Craft the " + w.displayName + " at Base Camp [B]");
                }
            }
        }
        int cycle = weaponCycleRequested;
        if (cycle != 0) {
            weaponCycleRequested = 0;
            player.cycleWeapon(cycle);
        }
        if (grenadeRequested) {
            grenadeRequested = false;
            Bullet grenade = player.throwGrenade(aimX, aimY);
            if (grenade != null) {
                bullets.add(grenade);
            }
        }
        if (medkitRequested) {
            medkitRequested = false;
            useMedkit();
        }
    }

    private void useMedkit() {
        int before = player.getHp();
        if (player.useMedkit()) {
            addText(player.getX(), player.getY() - 22, "+" + (player.getHp() - before) + " HP", TEXT_LOOT);
        } else if (player.getHp() >= player.getMaxHp()) {
            player.showHint("Health is already full");
        }
    }

    private void fireAt(double targetX, double targetY) {
        List<Bullet> shots = player.shoot(targetX, targetY);
        if (shots.isEmpty()) {
            return;
        }
        bullets.addAll(shots);
        GameData.WeaponType w = player.getCurrentWeapon();
        if (w.damage >= 30 && !w.melee) {
            shake(1.5, 6);   // heavy guns kick the camera a little
        }
    }

    /** Auto-Fire: the nearest living monster in range that is not behind a wall, or null. */
    private Monster findAutoFireTarget() {
        GameData.WeaponType w = player.getCurrentWeapon();
        double range = w.melee ? w.range + 14 : w.range * 0.9;
        Monster best = null;
        double bestDist = range;
        for (Monster m : monsters) {
            if (!m.isAlive()) {
                continue;
            }
            double d = player.distanceTo(m);
            if (d < bestDist) {
                bestDist = d;
                best = m;
            }
        }
        if (best != null && !w.melee && !map.hasClearPath(player.getX(), player.getY(), best.getX(), best.getY(), 4)) {
            return null;   // do not waste ammo into a wall
        }
        return best;
    }

    /** Damages the player (armour and mercy time apply) with a red number and a camera shake. */
    private int hurtPlayer(int damage) {
        int dealt = player.takeDamage(damage);
        if (dealt > 0) {
            addText(player.getX(), player.getY() - 22, "-" + dealt, TEXT_HURT);
            shake(3, 10);
        }
        return dealt;
    }

    // ------------------------------------------------------------------ monsters
    private void updateMonsters() {
        for (Monster m : monsters) {
            m.update();
            if (!m.isAlive()) {
                continue;
            }
            int bite = m.tryContactAttack();
            if (bite > 0) {
                hurtPlayer(bite);
            }
            Bullet shot = m.takeProjectile();
            if (shot != null) {
                bullets.add(shot);
            }
        }
        Monster.separate(monsters);
        monsters.removeIf(Monster::isRemovable);
    }

    /** Score, loot and (for the boss) a big moment. */
    private void onMonsterKilled(Monster m) {
        kills++;
        killScore += m.getType().score;
        drops.addAll(m.rollDrops(rng, player.getLootBonus()));
        if (m.getType() == GameData.MonsterType.ZOMBIE_BOSS) {
            showBanner("BOSS DOWN!", "It dropped a supply chest", BANNER_WAVE);
            shake(6, 30);
        }
    }

    // ------------------------------------------------------------------ bullets
    private void updateBullets() {
        for (Bullet b : bullets) {
            b.update();
            if (b.isHostile()) {
                checkHostileHit(b);
            } else {
                checkPlayerBulletHits(b);
            }
            if (b.consumeExplosion()) {
                explode(b.getX(), b.getY(), b.getBlastRadius(), b.getDamage());
            }
            if (b.consumeWallHit()) {
                addImpact(b);
            }
        }
        bullets.removeIf(b -> !b.isAlive());
    }

    private void checkHostileHit(Bullet b) {
        if (!b.tryHit(player)) {
            return;
        }
        addImpact(b);
        if (hurtPlayer(b.getDamage()) > 0 && b.getPoisonTicks() > 0) {
            player.applyPoison(b.getPoisonTicks());
        }
    }

    private void checkPlayerBulletHits(Bullet b) {
        for (Monster m : monsters) {
            if (!b.isAlive()) {
                return;
            }
            // cheap distance test first: most monsters are nowhere near this bullet
            if (!m.isAlive() || Math.abs(m.getX() - b.getX()) > 64 || Math.abs(m.getY() - b.getY()) > 64) {
                continue;
            }
            if (b.tryHit(m)) {
                boolean killed = m.takeHit(b.getDamage(), b.getAngle(), b.getKnockback());
                addText(m.getX(), m.getY() - m.getHeight(), String.valueOf(b.getDamage()), killed ? TEXT_KILL : TEXT_DAMAGE);
                if (!b.isMelee()) {
                    addImpact(b);
                }
                if (killed) {
                    onMonsterKilled(m);
                }
            }
        }
    }

    /** Rockets and grenades: damage every monster inside the blast and push it away from the centre. */
    private void explode(double x, double y, int radius, int damage) {
        addEffect(new Effect(Effect.Kind.EXPLOSION, x, y, 28, null, null, Math.max(48, radius * 2)));
        AssetLoader.playSound("explosion");
        shake(6, 18);
        for (Monster m : monsters) {
            if (!m.isAlive() || m.distanceTo(x, y) > radius + m.getWidth() / 2.0) {
                continue;
            }
            boolean killed = m.takeHit(damage, Math.atan2(m.getY() - y, m.getX() - x), 7);
            addText(m.getX(), m.getY() - m.getHeight(), String.valueOf(damage), killed ? TEXT_KILL : TEXT_DAMAGE);
            if (killed) {
                onMonsterKilled(m);
            }
        }
    }

    // ------------------------------------------------------------------ loot
    private void updateDrops() {
        for (DroppedItem d : drops) {
            d.update();
            if (d.isReadyToCollect()) {
                String label = player.pickUp(d);
                if (!label.isEmpty()) {
                    addPickupText(label);
                }
            }
            if (d.isChestReadyToBurst()) {
                drops.addAll(d.burstChest(rng));
                addText(d.getX(), d.getY() - 20, "Supply chest!", TEXT_KILL);
            }
        }
        drops.removeIf(d -> !d.isAlive());
    }

    // ------------------------------------------------------------------ effects, camera, banners
    private void updateEffects() {
        for (Effect e : effects) {
            e.age++;
            if (e.kind == Effect.Kind.TEXT) {
                e.y -= 0.45;   // floating numbers drift upwards
            }
        }
        effects.removeIf(e -> e.age >= e.life);
        if (shakeTicks > 0) {
            shakeTicks--;
            shakeX = (rng.nextDouble() * 2 - 1) * shakePower;
            shakeY = (rng.nextDouble() * 2 - 1) * shakePower;
            shakePower *= 0.9;
        } else {
            shakeX = 0;
            shakeY = 0;
        }
    }

    /** The little spark where a bullet stopped. */
    private void addImpact(Bullet b) {
        addEffect(new Effect(Effect.Kind.IMPACT, b.getX(), b.getY(), 15, null, null, 20));
    }

    private void addEffect(Effect e) {
        if (effects.size() < MAX_EFFECTS || e.kind == Effect.Kind.EXPLOSION) {
            effects.add(e);
        }
    }

    /** Floating text with a small random offset so several numbers at once do not cover each other. */
    private void addText(double x, double y, String text, Color color) {
        double jitterX = (rng.nextDouble() - 0.5) * 14;
        double jitterY = (rng.nextDouble() - 0.5) * 6;
        addEffect(new Effect(Effect.Kind.TEXT, x + jitterX, y + jitterY, 50, text, color, 0));
    }

    /** Pickups that arrive together are stacked upwards so every label can be read. */
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
        banner = new Banner(title, subtitle, color, tickCount, 180);
    }

    /**
     * The camera glides after the player (follow = share of the distance covered per tick, 1 = jump
     * there) and never shows anything outside the map.
     */
    private void updateCamera(double follow) {
        double viewW = getViewWidth() / (double) SCALE, viewH = getViewHeight() / (double) SCALE;
        double targetX = clamp(player.getX() - viewW / 2, map.getWidthPx() - viewW);
        double targetY = clamp(player.getY() - viewH / 2, map.getHeightPx() - viewH);
        camX += (targetX - camX) * follow;
        camY += (targetY - camY) * follow;
    }

    /** Keeps v between 0 and max (or 0 when the view is bigger than the map). */
    private static double clamp(double v, double max) {
        return Math.max(0, Math.min(Math.max(0, max), v));
    }

    // ------------------------------------------------------------------ end of the run
    private void finishGame(boolean survived) {
        if (gameOver) {
            return;
        }
        won = survived;
        gameOver = true;
        gameOverTimer = GAME_OVER_DELAY_TICKS;
        releaseAllInput();
        AssetLoader.stopMusic();
        if (survived) {
            AssetLoader.playSound("win");
        }
        requestHudRefresh();
    }

    /** After the end screen has been visible for a moment, tell the window (exactly once). */
    private void updateGameOverDelay() {
        if (gameOverTimer <= 0) {
            return;
        }
        gameOverTimer--;
        if (gameOverTimer == 0 && frame != null) {
            boolean survived = won;
            SwingUtilities.invokeLater(() -> frame.onGameOver(survived));
        }
    }

    // ------------------------------------------------------------------ getters used by GameFrame and tests
    public Player getPlayer() {
        return player;
    }

    public int getWave() {
        return wave;
    }

    public int getDay() {
        return wave;
    }

    public boolean isNight() {
        return raidActive;
    }

    public int getOuterChestCount() {
        int n = 0;
        for (DroppedItem d : drops) {
            if (d.getType() == GameData.ItemType.CHEST) {
                n++;
            }
        }
        return n;
    }

    public int getDistanceFromCampMeters() {
        Rectangle camp = map.getCampArea();
        return (int) Math.round(Math.hypot(player.getX() - camp.getCenterX(), player.getY() - camp.getCenterY()) / 12.0);
    }

    public String getCompassDirectionToCamp() {
        Rectangle camp = map.getCampArea();
        double dx = camp.getCenterX() - player.getX();
        double dy = camp.getCenterY() - player.getY();
        String ns = Math.abs(dy) < 24 ? "" : (dy < 0 ? "N" : "S");
        String ew = Math.abs(dx) < 24 ? "" : (dx < 0 ? "W" : "E");
        String dir = ns + ew;
        return dir.isEmpty() ? "HERE" : dir;
    }

    public int getTotalBaseLevel() {
        return player.getLevel(GameData.RecipeKind.BASE_TURRET)
                + player.getLevel(GameData.RecipeKind.BASE_WALL)
                + player.getLevel(GameData.RecipeKind.BASE_MEDIC)
                + player.getLevel(GameData.RecipeKind.BASE_FARM);
    }

    public int getKills() {
        return kills;
    }

    /** Kill points + 2 per second survived + 1000 for surviving, scaled by difficulty (1: x0.75 ... 5: x1.75). */
    public int getScore() {
        double base = killScore + getElapsedSeconds() * 2 + (won ? 1000 : 0);
        return (int) Math.round(base * (0.5 + 0.25 * getDifficulty()));
    }

    public int getElapsedSeconds() {
        return elapsedTicks * GameData.TICK_MS / 1000;
    }

    public int getTimeLimitSeconds() {
        return GameData.TIME_LIMIT_SECONDS;
    }

    /** How far the current wave is, 0..100 (the next wave starts at 100). */
    public int getWaveProgressPercent() {
        return (elapsedTicks % WAVE_TICKS) * 100 / WAVE_TICKS;
    }

    public int getLiveMonsterCount() {
        int n = 0;
        for (Monster m : monsters) {
            if (m.isAlive()) {
                n++;
            }
        }
        return n;
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public boolean isRaidPhase() {
        return raidActive;
    }

    /** True when the player is inside or right at the entrance of the central Base Camp. */
    public boolean isPlayerInBase() {
        Rectangle baseZone = map.getCampArea();
        baseZone.grow(28, 28);
        return baseZone.contains((int) player.getX(), (int) player.getY());
    }

    public void setAutoFire(boolean b) {
        autoFire = b;
    }

    public void setShowHitbox(boolean b) {
        showHitbox = b;
        repaint();
    }

    private int getDifficulty() {
        return Math.max(1, Math.min(5, settings.difficulty));
    }

    private int getViewWidth() {
        return getWidth() > 0 ? getWidth() : DEFAULT_VIEW_W;
    }

    private int getViewHeight() {
        return getHeight() > 0 ? getHeight() : DEFAULT_VIEW_H;
    }

    private double getMouseWorldX() {
        return camX + mouseX / (double) SCALE;
    }

    private double getMouseWorldY() {
        return camY + mouseY / (double) SCALE;
    }

    /** The part of the world the camera shows, in world pixels. */
    private Rectangle getCameraView() {
        return new Rectangle((int) camX, (int) camY, getViewWidth() / SCALE + 1, getViewHeight() / SCALE + 1);
    }

    private static String formatTime(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    // ------------------------------------------------------------------ input handlers
    /** WASD/arrows move, 1-8 pick a weapon, G grenade, E medkit, B workshop, P/Esc pause. */
    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        if (code >= KeyEvent.VK_1 && code <= KeyEvent.VK_8) {
            weaponSlotRequested = code - KeyEvent.VK_1;   // 1 = Knife ... 8 = Rocket Launcher
            return;
        }
        if (setMoveKey(code, true)) {
            return;
        }
        switch (code) {
            case KeyEvent.VK_G:
                grenadeRequested = true;
                break;
            case KeyEvent.VK_E:
                medkitRequested = true;
                break;
            case KeyEvent.VK_B:
                openWorkshop();
                break;
            case KeyEvent.VK_P:
            case KeyEvent.VK_ESCAPE:
                togglePause();
                break;
            default:
                break;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        setMoveKey(e.getKeyCode(), false);
    }

    /** Remembers whether a movement key (W A S D or an arrow) is held. Returns false for other keys. */
    private boolean setMoveKey(int code, boolean down) {
        if (code == KeyEvent.VK_W || code == KeyEvent.VK_UP) {
            keyUp = down;
        } else if (code == KeyEvent.VK_S || code == KeyEvent.VK_DOWN) {
            keyDown = down;
        } else if (code == KeyEvent.VK_A || code == KeyEvent.VK_LEFT) {
            keyLeft = down;
        } else if (code == KeyEvent.VK_D || code == KeyEvent.VK_RIGHT) {
            keyRight = down;
        } else {
            return false;
        }
        return true;
    }

    @Override
    public void keyTyped(KeyEvent e) {
        // keyPressed handles every key we use
    }

    /** Left button: start firing at the mouse. Right button (or Ctrl+click on a Mac): throw a grenade. */
    @Override
    public void mousePressed(MouseEvent e) {
        requestFocusInWindow();
        trackMouse(e);
        if (paused || gameOver) {
            return;
        }
        if (SwingUtilities.isRightMouseButton(e) || e.isControlDown()) {
            grenadeRequested = true;
        } else if (SwingUtilities.isLeftMouseButton(e)) {
            mouseFiring = true;
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e)) {
            mouseFiring = false;
        }
    }

    /** Aiming: the game thread turns the mouse position into a world position every tick. */
    @Override
    public void mouseMoved(MouseEvent e) {
        trackMouse(e);
    }

    /** mouseMoved does not fire while a button is held, so aiming while shooting comes through here. */
    @Override
    public void mouseDragged(MouseEvent e) {
        trackMouse(e);
    }

    /** The wheel cycles through the owned weapons. */
    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        double rotation = e.getPreciseWheelRotation();
        if (rotation == 0 || tickCount - lastWheelTick < WHEEL_DELAY_TICKS) {
            return;
        }
        lastWheelTick = tickCount;
        weaponCycleRequested = rotation > 0 ? 1 : -1;
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        trackMouse(e);
    }

    @Override
    public void mouseExited(MouseEvent e) {
        mouseInside = false;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        // mousePressed/mouseReleased already handle the buttons
    }

    private void trackMouse(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();
        mouseInside = true;
    }

    private void togglePause() {
        if (paused) {
            resumeGame();
        } else {
            pauseGame();
        }
        repaint();
    }

    private void openWorkshop() {
        if (frame != null && !gameOver && player.isAlive()) {
            if (!isPlayerInBase()) {
                player.showHint("Return to Base Camp (green icon) to talk to NPCs!");
                AssetLoader.playSound("click");
                return;
            }
            // let this key event finish first; the modal dialog then runs its own event loop
            SwingUtilities.invokeLater(frame::openUpgradeDialog);
        }
    }

    private void releaseAllInput() {
        keyUp = false;
        keyDown = false;
        keyLeft = false;
        keyRight = false;
        mouseFiring = false;
    }

    // ------------------------------------------------------------------ drawing
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            synchronized (worldLock) {
                drawWorld(g2);
                drawFloatingTexts(g2);
            }
            drawOverlay(g2);
        } finally {
            g2.dispose();
        }
    }

    /** Ground, then props and entities sorted by their feet (so you can walk behind a tent), then effects. */
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
        drawCampPerimeter(g2, view);
        collectVisible(view);
        drawList.sort(GameMap.DEPTH_ORDER);
        for (GameMap.Drawable d : drawList) {
            d.draw(g2);
        }
        if (showHitbox) {
            drawHitboxes(g2, view);
        }
        drawWorldEffects(g2);
        g2.setTransform(old);
    }

    /** Subtle perimeter outline around Base Camp (green during Scavenge safe phase, red/orange during Raid). */
    private void drawCampPerimeter(Graphics2D g2, Rectangle view) {
        Rectangle camp = map.getCampArea();
        if (!camp.intersects(view)) {
            return;
        }
        g2.setColor(raidActive ? new Color(220, 75, 55, 110) : new Color(110, 210, 120, 95));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRect(camp.x, camp.y, camp.width, camp.height);
    }

    /** Only things near the camera go into the sorted draw list. */
    private void collectVisible(Rectangle view) {
        Rectangle near = new Rectangle(view);
        near.grow(48, 64);   // sprites stick out of their hitbox, so look a bit further
        drawList.clear();
        drawList.addAll(map.getPropsInView(view));
        for (DroppedItem d : drops) {
            if (near.contains(d.getX(), d.getY())) {
                drawList.add(d);
            }
        }
        for (Monster m : monsters) {
            if (near.contains(m.getX(), m.getY())) {
                drawList.add(m);
            }
        }
        for (Bullet b : bullets) {
            if (near.contains(b.getX(), b.getY())) {
                drawList.add(b);
            }
        }
        drawList.add(player);
    }

    private void drawHitboxes(Graphics2D g2, Rectangle view) {
        g2.setColor(new Color(255, 60, 60, 160));
        for (Rectangle r : solidRects) {
            if (r.intersects(view)) {
                g2.drawRect(r.x, r.y, r.width, r.height);
            }
        }
        for (GameMap.Drawable d : drawList) {
            if (d instanceof Entity) {
                ((Entity) d).drawHitbox(g2);
            }
        }
    }

    /** Explosions and bullet impacts, in world coordinates. */
    private void drawWorldEffects(Graphics2D g2) {
        for (Effect e : effects) {
            BufferedImage[] frames;
            if (e.kind == Effect.Kind.EXPLOSION) {
                frames = explosionFrames;
            } else if (e.kind == Effect.Kind.IMPACT) {
                frames = impactFrames;
            } else {
                continue;
            }
            BufferedImage img = frames[Math.min(frames.length - 1, e.age * frames.length / e.life)];
            int half = e.size / 2;
            g2.drawImage(img, (int) e.x - half, (int) e.y - half, e.size, e.size, null);
        }
    }

    /** Damage numbers, pickup labels, and base NPC interaction prompts in sharp screen pixels. */
    private void drawFloatingTexts(Graphics2D g2) {
        Point trader = map.getTraderPosition();
        if (!gameOver && getCameraView().contains(trader)) {
            String prompt = isPlayerInBase() ? "PRESS B: CAMP NPCS - CRAFT / HEAL / BUILD" : "BASE CAMP NPCS";
            int tx = trader.x * SCALE - originX - Theme.pixelWidth(prompt, 2) / 2;
            int ty = Math.max(12, (trader.y - 20) * SCALE - originY);
            Theme.drawPixelTextShadow(g2, prompt, tx, ty, 2, isPlayerInBase() ? Theme.GOLD : Theme.TEXT_2);
        }
        Composite old = g2.getComposite();
        for (Effect e : effects) {
            if (e.kind != Effect.Kind.TEXT) {
                continue;
            }
            float left = 1f - e.age / (float) e.life;
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, left * 2.5f)));
            int sx = (int) Math.round(e.x * SCALE) - originX - Theme.pixelWidth(e.text, 2) / 2;
            int sy = (int) Math.round(e.y * SCALE) - originY - 10;
            Theme.drawPixelTextShadow(g2, e.text, sx, sy, 2, e.color);   // the kit's pixel font
        }
        g2.setComposite(old);
    }

    /** Everything that sits on the glass in front of the world. */
    private void drawOverlay(Graphics2D g2) {
        drawNightLighting(g2);
        drawDangerTint(g2);
        drawBanner(g2);
        drawMiniMap(g2);
        if (gameOver) {
            drawGameOverScreen(g2);
            return;
        }
        drawBossBar(g2);
        drawHint(g2);
        if (paused) {
            drawPauseScreen(g2);
        } else if (mouseInside) {
            drawCrosshair(g2);
        }
    }

    /** Atmospheric Nightfall darkness with warm light pools around the player, campfire, and guard posts. */
    private void drawNightLighting(Graphics2D g2) {
        if (nightAlpha <= 0.01f) {
            return;
        }
        int vw = getViewWidth(), vh = getViewHeight();
        int mw = vw / SCALE + 2, mh = vh / SCALE + 2;
        if (nightMask == null || nightMask.getWidth() != mw || nightMask.getHeight() != mh) {
            nightMask = new BufferedImage(mw, mh, BufferedImage.TYPE_INT_ARGB);
        }
        Graphics2D mg = nightMask.createGraphics();
        mg.setComposite(AlphaComposite.Src);
        mg.setColor(new Color(8, 12, 26, Math.round(nightAlpha * 165)));
        mg.fillRect(0, 0, mw, mh);

        mg.setComposite(AlphaComposite.DstOut);
        punchLightPool(mg, (float) (player.getX() - camX), (float) (player.getY() - camY), 90f);
        Rectangle camp = map.getCampArea();
        punchLightPool(mg, (float) (camp.getCenterX() - camX), (float) (camp.getCenterY() - camY), 130f);
        for (Point gp : map.getGuardPosts()) {
            punchLightPool(mg, (float) (gp.x - camX), (float) (gp.y - camY), 68f);
        }
        mg.dispose();
        g2.drawImage(nightMask, 0, 0, vw, vh, null);
    }

    private static void punchLightPool(Graphics2D mg, float cx, float cy, float radius) {
        if (cx + radius < 0 || cy + radius < 0 || cx - radius > 1200 || cy - radius > 900) {
            return;
        }
        mg.setPaint(new RadialGradientPaint(cx, cy, radius, new float[]{0f, 0.55f, 1f},
                new Color[]{new Color(0, 0, 0, 255), new Color(0, 0, 0, 150), new Color(0, 0, 0, 0)}));
        mg.fillOval(Math.round(cx - radius), Math.round(cy - radius), Math.round(radius * 2), Math.round(radius * 2));
    }

    /** Red flash when hurt, green tint when poisoned, pulsing red edge when health is low. */
    private void drawDangerTint(Graphics2D g2) {
        int w = getViewWidth(), h = getViewHeight();
        if (player.isHurt()) {
            g2.setColor(new Color(200, 0, 0, 55));
            g2.fillRect(0, 0, w, h);
        } else if (player.isPoisoned()) {
            g2.setColor(new Color(80, 200, 40, 30));
            g2.fillRect(0, 0, w, h);
        }
        boolean lowHp = player.getHp() * 100 < player.getMaxHp() * 30;
        if (lowHp || !player.isAlive()) {
            if (vignette == null || vignette.getWidth() != w || vignette.getHeight() != h) {
                vignette = createVignette(w, h);
            }
            float pulse = player.isAlive() ? 0.55f + 0.45f * (float) Math.abs(Math.sin(tickCount * 0.08)) : 1f;
            Composite old = g2.getComposite();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, pulse));
            g2.drawImage(vignette, 0, 0, null);
            g2.setComposite(old);
        }
    }

    /** Transparent in the middle, dark red at the edges. Made once per panel size. */
    private static BufferedImage createVignette(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        float radius = (float) Math.hypot(w / 2.0, h / 2.0);
        g.setPaint(new RadialGradientPaint(w / 2f, h / 2f, radius, new float[]{0f, 0.55f, 1f},
                new Color[]{new Color(120, 0, 0, 0), new Color(140, 0, 0, 40), new Color(150, 0, 0, 200)},
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    /** The boss's health on a glass panel at the top, with the kit's zombie portrait. */
    private void drawBossBar(Graphics2D g2) {
        Monster boss = null;
        for (Monster m : monsters) {
            if (m.isAlive() && m.getType() == GameData.MonsterType.ZOMBIE_BOSS) {
                boss = m;
                break;
            }
        }
        if (boss == null) {
            return;
        }
        Graphics2D g = (Graphics2D) g2.create();
        BufferedImage portrait = Theme.kit("hud/portrait_zombie", 1);
        int barW = 300, panelW = barW + portrait.getWidth() + 44, panelH = 52;
        int x = (getViewWidth() - panelW) / 2, y = 12;
        Theme.paintPanel(g, Theme.Panel.GLASS, x, y, panelW, panelH, 1);
        g.drawImage(portrait, x + 12, y + 10, null);
        int bx = x + portrait.getWidth() + 24;
        Theme.drawPixelTextShadow(g, boss.getType().displayName.toUpperCase(), bx, y + 10, 2, BANNER_BOSS);
        String hp = boss.getHp() + "/" + boss.getMaxHp();
        Theme.drawPixelTextShadow(g, hp, bx + barW - Theme.pixelWidth(hp, 2), y + 10, 2, Theme.TEXT_2);
        Theme.drawBar(g, Theme.Fill.HEALTH, bx, y + 26, barW, boss.getHp() / (double) boss.getMaxHp(), 2);
        g.dispose();
    }

    /** The 56x36 map picture drawn 3x in the kit's minimap frame, with the kit's map markers. */
    private void drawMiniMap(Graphics2D g2) {
        BufferedImage mini = map.getMinimap();
        int s = 3, w = mini.getWidth() * s, h = mini.getHeight() * s;
        int left = getViewWidth() - w - 22, top = 22;
        double perPx = s / (double) GameData.TILE;   // mini-map pixels per world pixel
        Graphics2D g = (Graphics2D) g2.create();
        g.setColor(GLASS);
        g.fillRect(left - 4, top - 4, w + 8, h + 8);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f));
        g.drawImage(mini, left, top, w, h, null);
        g.setComposite(AlphaComposite.SrcOver);
        Rectangle camp = map.getCampArea();
        drawMarker(g, "map_marker_safe", left + (int) (camp.getCenterX() * perPx), top + (int) (camp.getCenterY() * perPx));
        for (DroppedItem d : drops) {
            if (d.getType() == GameData.ItemType.CHEST) {
                drawMarker(g, "map_marker_loot", left + (int) (d.getX() * perPx), top + (int) (d.getY() * perPx));
            }
        }
        g.setColor(Theme.RED);
        for (Monster m : monsters) {
            if (!m.isAlive()) {
                continue;
            }
            int mx = left + (int) (m.getX() * perPx), my = top + (int) (m.getY() * perPx);
            if (m.getType() == GameData.MonsterType.ZOMBIE_BOSS) {
                drawMarker(g, "map_marker_zombie", mx, my);
            } else {
                g.fillRect(mx - 1, my - 1, 3, 3);   // ordinary monsters as small dots, so a horde stays readable
            }
        }
        g.setColor(new Color(240, 230, 192, 110));
        g.drawRect(left + (int) (camX * perPx), top + (int) (camY * perPx),
                (int) (getViewWidth() / (double) SCALE * perPx), (int) (getViewHeight() / (double) SCALE * perPx));
        drawMarker(g, "map_marker_player", left + (int) (player.getX() * perPx), top + (int) (player.getY() * perPx));
        Theme.drawMinimapFrame(g, left - 6, top - 6, w + 12, h + 12, 2);

        // Survival telemetry & compass box right beneath the minimap
        int boxY = top + h + 10;
        Theme.paintPanel(g, Theme.Panel.GLASS, left - 6, boxY, w + 12, 42, 1);
        String zoneText = isPlayerInBase()
                ? "ZONE: SAFE CAMP"
                : "CAMP: " + getDistanceFromCampMeters() + "M " + getCompassDirectionToCamp();
        Theme.drawPixelTextShadow(g, zoneText, left + 2, boxY + 8, 2,
                isPlayerInBase() ? new Color(0x88D470) : Theme.GOLD);
        String intel = "CHESTS " + getOuterChestCount() + "  BASE LV " + getTotalBaseLevel();
        Theme.drawPixelTextShadow(g, intel, left + 2, boxY + 24, 2, Theme.TEXT_2);
        g.dispose();
    }

    /** A kit map marker (8 x 8) centred on a mini-map point. */
    private static void drawMarker(Graphics2D g, String name, int cx, int cy) {
        g.drawImage(Theme.kit("hud/" + name, 1), cx - 4, cy - 4, null);
    }

    /** Title in the kit style with a red banner underneath; fades in, stays, fades out. */
    private void drawBanner(Graphics2D g2) {
        Banner b = banner;
        if (b == null) {
            return;
        }
        int age = tickCount - b.startTick;
        if (age < 0 || age > b.length) {
            return;
        }
        float alpha = Math.min(1f, Math.min(age / 15f, (b.length - age) / 30f));
        Graphics2D g = (Graphics2D) g2.create();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, alpha)));
        int cx = getViewWidth() / 2, top = getViewHeight() / 5;
        Theme.drawPixelTextShadow(g, b.title, cx - Theme.pixelWidth(b.title, 6) / 2, top, 6, b.color);
        top += 34;
        Theme.drawBanner(g, b.subtitle.toUpperCase(), cx, top + 12, 2);
        g.dispose();
    }

    /** Player messages such as "Out of ammo - switched to Pistol", on a glass panel near the bottom. */
    private void drawHint(Graphics2D g2) {
        String hint = player.getHint();
        if (hint == null) {
            return;
        }
        Graphics2D g = (Graphics2D) g2.create();
        int w = Theme.pixelWidth(hint, 2) + 40, h = 34;
        int x = (getViewWidth() - w) / 2, y = getViewHeight() - h - 18;
        Theme.paintPanel(g, Theme.Panel.GLASS, x, y, w, h, 1);
        Theme.drawPixelTextShadow(g, hint, x + 20, y + 12, 2, Theme.GOLD);
        g.dispose();
    }

    /** A kit crosshair that suits the weapon; a ring around it fills up while the weapon reloads. */
    private void drawCrosshair(Graphics2D g2) {
        GameData.WeaponType w = player.getCurrentWeapon();
        String name;
        if (w.melee) {
            name = "cursor_attack";
        } else if (w.pellets > 1 || w.explosive) {
            name = "crosshair_2";
        } else if (w.pierce > 1) {
            name = "crosshair_4";
        } else {
            name = "crosshair_1";
        }
        BufferedImage cross = Theme.kit("cursors/" + name, 2);
        g2.drawImage(cross, mouseX - cross.getWidth() / 2, mouseY - cross.getHeight() / 2, null);
        double reload = player.getReloadProgress();
        if (reload < 1.0 && player.getCooldownTicks(w) > 20) {
            g2.setColor(new Color(240, 230, 192, 170));
            g2.setStroke(new BasicStroke(2f));
            g2.draw(new Arc2D.Double(mouseX - 20, mouseY - 20, 40, 40, 90, -360 * reload, Arc2D.OPEN));
        }
    }

    /** Darkens the game and shows the kit's PAUSED title and the controls as key caps on a glass panel. */
    private void drawPauseScreen(Graphics2D g2) {
        int w = getViewWidth(), h = getViewHeight();
        Graphics2D g = Theme.smooth(g2);
        g.setColor(SCRIM);
        g.fillRect(0, 0, w, h);
        String[][] controls = {
            {"Move", "w", "a", "s", "d"}, {"Aim, hold to fire", "mouse_left"},
            {"Grenade", "mouse_right", "g"}, {"Medkit", "e"},
            {"Switch weapon (1-7)", "mouse_mid"}, {"Base NPC", "b"},
            {"Resume", "p", "esc"}
        };
        BufferedImage title = Theme.kit("titles/paused", 2);
        int rowH = 40, rows = (controls.length + 1) / 2;
        int cardW = 560, cardH = 34 + title.getHeight() + 44 + rows * rowH + 14;
        int x0 = (w - cardW) / 2, y0 = (h - cardH) / 2;
        Theme.paintPanel(g, Theme.Panel.GLASS, x0, y0, cardW, cardH, 2);
        g.drawImage(title, x0 + 34, y0 + 30, null);
        g.setFont(FONT_HELP);
        g.setColor(Theme.TEXT_2);
        int textY = y0 + 30 + title.getHeight() + 24;
        g.drawString("Press P or Esc to keep playing.", x0 + 36, textY);
        int colW = (cardW - 68) / 2;
        for (int i = 0; i < controls.length; i++) {
            int x = x0 + 34 + (i % 2) * colW;
            int y = textY + 16 + (i / 2) * rowH;
            for (int k = 1; k < controls[i].length; k++) {
                BufferedImage key = Theme.keySprite(controls[i][k], 2);
                g.drawImage(key, x, y, null);
                x += key.getWidth() + 4;
            }
            g.setColor(Theme.TEXT);
            g.drawString(controls[i][0], x + 8, y + 21);
        }
        g.dispose();
    }

    /** The kit's "SURVIVED" or "YOU DIED" title with the run's numbers, shown for a moment before the summary. */
    private void drawGameOverScreen(Graphics2D g2) {
        int w = getViewWidth(), h = getViewHeight();
        g2.setColor(SCRIM);
        g2.fillRect(0, 0, w, h);
        BufferedImage title = Theme.kit(won ? "titles/survived" : "titles/you_died", 3);
        g2.drawImage(title, (w - title.getWidth()) / 2, h / 2 - title.getHeight() - 10, null);
        String stats = "DAY " + wave + "   INFECTED KILLED " + kills + "   BASE LV " + getTotalBaseLevel() + "   TIME " + formatTime(getElapsedSeconds());
        Theme.drawPixelTextShadow(g2, stats, (w - Theme.pixelWidth(stats, 3)) / 2, h / 2 + 16, 3, Theme.TEXT);
    }
}
