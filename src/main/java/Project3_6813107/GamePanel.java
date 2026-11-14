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
    private static final int MAX_EFFECTS = 600;
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
    private final transient List<AllyNpc> allies = new CopyOnWriteArrayList<>();
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
    private volatile int currentDay = 1;
    private volatile int kills;
    private volatile int killScore;
    private int bossTimer;                     // > 0: an apex mutant walks in when it reaches 0
    private int gameOverTimer;
    private boolean raidActive;
    private float nightAlpha;                  // 0 = daylight, 1 = nightfall
    private transient BufferedImage nightMask; // world-scale darkness layer punched with light pools
    private int idlePlayerTicks;
    private double lastPlayerX, lastPlayerY;
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

    // ------------------------------------------------------------------ rhythm & auto-runner state
    private int waypointIndex = 1;
    private int stuckTicks;
    private int lapsCompleted;
    private int combo;
    private int maxCombo;
    private int feverCharge;
    private int feverTicks;
    private int rhythmBonusScore;
    private int perfectHits, greatHits, goodHits, offBeatHits;
    private GameData.RhythmJudge lastJudge;
    private int lastJudgeTick = -999;
    private int lastRhythmBeatScored = -1;
    private int lastAutoBeatFired = -1;
    private volatile boolean beatShotRequested;
    private volatile boolean spaceAimTarget;
    private double zoomKick;
    private double recoilX, recoilY;
    private Color screenFlashColor;
    private int screenFlashTicks, screenFlashMax;

    // ------------------------------------------------------------------ drawing helpers (Swing thread only)
    private final transient List<GameMap.Drawable> drawList = new ArrayList<>();
    private final transient BufferedImage[] explosionFrames = AssetLoader.getExplosionFrames();
    private final transient BufferedImage[] impactFrames = AssetLoader.getImpactFrames();
    private transient BufferedImage vignette;            // red edge for low health, rebuilt only when the size changes
    private int originX, originY;              // screen pixel of world (0, 0) in the frame being painted
    private double currentZoom = 1.0;

    // ------------------------------------------------------------------ small helper types
    /** A short-lived visual: floating text, bullet impact, explosion, shockwave, beam, spark, or afterimage. */
    static final class Effect {
        enum Kind { TEXT, IMPACT, EXPLOSION, SHOCKWAVE, BEAM, SPARK, AFTERIMAGE }

        final Kind kind;
        double x, y;
        final double x2, y2;
        double vx, vy;
        final int life;
        int age;
        final String text;
        final Color color;
        final int size;

        Effect(Kind kind, double x, double y, int life, String text, Color color, int size) {
            this(kind, x, y, 0, 0, 0, 0, life, text, color, size);
        }

        Effect(Kind kind, double x, double y, double x2, double y2, double vx, double vy,
                int life, String text, Color color, int size) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.x2 = x2;
            this.y2 = y2;
            this.vx = vx;
            this.vy = vy;
            this.life = Math.max(1, life);
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

    /** Armed NPC survivor who accompanies the player, keeps formation, and shoots nearby zombies. */
    private final class AllyNpc implements GameMap.Drawable {
        final int slot;
        double x, y;
        double aimAngle;
        boolean facingLeft;
        boolean moving;
        int animTick;
        int fireCooldown;

        AllyNpc(int slot, double x, double y) {
            this.slot = slot;
            this.x = x;
            this.y = y;
        }

        void update(int squadLv, boolean allowFire) {
            animTick++;
            if (fireCooldown > 0) {
                fireCooldown--;
            }
            double distToPlayer = Math.hypot(player.getX() - x, player.getY() - y);
            if (distToPlayer > 250) {
                x = player.getX();
                y = player.getY();
                moving = false;
            } else {
                double formAngle = slot * (Math.PI / 2.0) + 0.75;
                double formDist = 38.0 + (slot % 2) * 14.0;
                double goalX = player.getX() + Math.cos(formAngle) * formDist;
                double goalY = player.getY() + Math.sin(formAngle) * formDist;
                double d = Math.hypot(goalX - x, goalY - y);
                if (d > 10) {
                    double step = player.getMoveSpeed() * 1.05;
                    double moveAngle = map.getChaseAngle(x, y + 8, goalX, goalY + 8, 14);
                    double dx = Math.cos(moveAngle) * Math.min(step, d);
                    double dy = Math.sin(moveAngle) * Math.min(step, d);
                    double oldX = x, oldY = y;
                    if (dx != 0 && !map.isBlocked(new Rectangle((int) Math.round(x + dx) - 7, (int) Math.round(y), 14, 12))) {
                        x += dx;
                    }
                    if (dy != 0 && !map.isBlocked(new Rectangle((int) Math.round(x) - 7, (int) Math.round(y + dy), 14, 12))) {
                        y += dy;
                    }
                    moving = Math.hypot(x - oldX, y - oldY) > 0.1;
                    facingLeft = dx < -0.05 || (Math.abs(dx) <= 0.05 && facingLeft);
                } else {
                    moving = false;
                }
            }

            if (allowFire && player.isAlive()) {
                Monster best = null;
                double bestDist = 185.0;
                for (Monster m : monsters) {
                    if (!m.isAlive()) {
                        continue;
                    }
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
                        GameData.WeaponType w = (slot == 0 && squadLv == 0)
                                ? GameData.WeaponType.PISTOL : GameData.WeaponType.RIFLE;
                        Bullet b = Bullet.shot(x + Math.cos(aimAngle) * 10, y + Math.sin(aimAngle) * 10,
                                aimAngle, w, 11 + squadLv * 3);
                        b.setMap(map);
                        bullets.add(b);
                        addEffect(new Effect(Effect.Kind.IMPACT,
                                x + Math.cos(aimAngle) * 12, y + Math.sin(aimAngle) * 12, 10, null, null, 12));
                        fireCooldown = Math.max(14, 26 - squadLv * 2);
                    }
                } else if (!moving) {
                    aimAngle = facingLeft ? Math.PI : 0;
                }
            }
        }

        @Override
        public double getSortY() {
            return y + 10;
        }

        @Override
        public void draw(Graphics2D g2) {
            g2.setColor(new Color(0, 0, 0, 55));
            g2.fillOval((int) Math.round(x - 7), (int) Math.round(y + 8), 14, 5);
            g2.setColor(new Color(100, 225, 115, 125));
            g2.setStroke(new BasicStroke(1f));
            g2.drawOval((int) Math.round(x - 8), (int) Math.round(y + 7), 16, 6);

            String strip = slot % 2 == 0 ? "characters/npc2_idle.png" : "characters/npc_idle.png";
            BufferedImage[] frames = AssetLoader.loadStrip(strip, 6);
            BufferedImage frame = frames[(animTick / 8) % frames.length];
            int bob = moving && (animTick / 4) % 2 == 1 ? -1 : 0;
            int w = frame.getWidth(), h = frame.getHeight();
            int left = (int) Math.round(x - w / 2.0);
            int top = (int) Math.round(y + 19 - h) + bob;
            if (facingLeft) {
                g2.drawImage(frame, left + w, top, -w, h, null);
            } else {
                g2.drawImage(frame, left, top, w, h, null);
            }

            GameData.WeaponType weapon = slot == 0 && player.getLevel(GameData.RecipeKind.BASE_TURRET) == 0
                    ? GameData.WeaponType.PISTOL : GameData.WeaponType.RIFLE;
            BufferedImage gunImg = AssetLoader.loadStrip(weapon.flickerStrip(), 7)[6];
            AffineTransform old = g2.getTransform();
            g2.translate(x + (facingLeft ? -4 : 4), y + 7 + bob);
            g2.rotate(aimAngle);
            if (Math.cos(aimAngle) < 0) {
                g2.scale(1, -1);
            }
            g2.drawImage(gunImg, -12, -17, null);
            g2.setTransform(old);
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
        showBanner("BEAT CAMP RUNNER", "125 BPM AUTO-RUN / SHOOT ON BEAT / SPACE OR CLICK", BANNER_WAVE);
    }

    /** Populates routes and outer zones with zombies, supply chests, and 1 starting NPC companion. */
    private void spawnInitialScavengeWorld() {
        for (int i = 0; i < 4; i++) {
            Point p = map.randomOuterLootPoint(rng);
            DroppedItem chest = new DroppedItem(GameData.ItemType.CHEST, 1, p.x, p.y, player);
            chest.setMap(map);
            drops.add(chest);
        }
        int wpCount = Math.max(1, map.getWaypointCount());
        for (int i = 0; i < 6; i++) {
            Point wp = map.getWaypoint((i + 1) % wpCount);
            double px = wp.x + (rng.nextDouble() - 0.5) * 90;
            double py = wp.y + (rng.nextDouble() - 0.5) * 90;
            Point p = map.isBlocked(new Rectangle((int) px - 8, (int) py - 8, 16, 16))
                    ? map.randomOuterLootPoint(rng) : new Point((int) px, (int) py);
            Monster m = new Monster(GameData.MonsterType.ZOMBIE_NORMAL, p.x, p.y, map, player, GameData.monsterHpScale(1, getDifficulty()));
            m.setRaiding(true);
            monsters.add(m);
        }
        allies.add(new AllyNpc(0, player.getX() - 26, player.getY() + 12));
        lastPlayerX = player.getX();
        lastPlayerY = player.getY();
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
        AssetLoader.loadStrip("characters/npc_idle.png", 6);
        AssetLoader.loadStrip("characters/npc2_idle.png", 6);
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

    /** The second thread: wakes periodically and asks for a batch of monsters. */
    private void runWaveSpawner() {
        Random timing = new Random();
        while (running) {
            try {
                int delay = raidActive ? (2200 + timing.nextInt(901)) : (3200 + timing.nextInt(1101));
                Thread.sleep(delay);
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

    /** Survival time, 3-Day Day/Night transitions, boss timer and win. */
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
            wave = newWave;
        }
        int elapsedSec = getElapsedSeconds();
        int newDay = 1 + Math.min(2, elapsedSec / GameData.DAY_CYCLE_SECONDS);
        if (newDay != currentDay) {
            startDay(newDay);
        }
        int secInDay = elapsedSec % GameData.DAY_CYCLE_SECONDS;
        if (!raidActive && secInDay >= GameData.DAYLIGHT_SECONDS) {
            startRaidPhase();
        }
        if (raidActive) {
            nightAlpha = Math.min(1f, nightAlpha + 0.005f);
        } else {
            nightAlpha = Math.max(0f, nightAlpha - 0.006f);
        }
        if (bossTimer > 0 && --bossTimer == 0) {
            spawnBoss();
        }
        if (elapsedTicks % GameData.BEAT_TICKS == 0) {
            boolean fever = isFeverActive();
            zoomKick = Math.max(zoomKick, fever ? 0.045 : 0.022);
            addEffect(new Effect(Effect.Kind.SHOCKWAVE, player.getX(), player.getFeetY() - 2,
                    0, 0, 0, 0, 14, null,
                    fever ? new Color(255, 220, 60, 170) : new Color(70, 225, 255, 130),
                    fever ? 32 : 20));
        }
        if (feverTicks > 0) {
            feverTicks--;
            if (feverTicks % 30 == 0 && player.getHp() < player.getMaxHp()) {
                player.heal(1);
            }
            if (feverTicks % 4 == 0) {
                double ang = rng.nextDouble() * Math.PI * 2;
                double spd = 0.8 + rng.nextDouble() * 1.4;
                addEffect(new Effect(Effect.Kind.SPARK, player.getX(), player.getY(),
                        0, 0, Math.cos(ang) * spd, Math.sin(ang) * spd, 12, null,
                        new Color(255, 225, 70, 220), 3));
            }
            if (feverTicks == 0) {
                addText(player.getX(), player.getY() - 28, "FEVER END", Theme.GOLD);
            }
        }
    }

    private void startDay(int dayNumber) {
        currentDay = dayNumber;
        raidActive = false;
        spawnOuterSupplyChests(3);
        AssetLoader.playSound("wave");
        if (dayNumber >= 3) {
            showBanner("FINAL DAY " + dayNumber + " - DAWN", "Last Daylight: prepare your camp & squad for the final night!", BANNER_WAVE);
        } else {
            showBanner("DAY " + dayNumber + " - DAWN", "Daylight (62s): scavenge chests & upgrade at camp [B]", BANNER_WAVE);
        }
    }

    private void startRaidPhase() {
        raidActive = true;
        bossTimer = BOSS_WARNING_TICKS;
        AssetLoader.playSound("wave");
        showBanner("DAY " + currentDay + " - NIGHTFALL", "Night raid & Apex Mutant incoming - hold out for 38s!", BANNER_BOSS);
        int raiders = 0;
        int limit = 5 + currentDay * 3;
        for (Monster m : monsters) {
            if (m.isAlive()) {
                m.setRaiding(true);
                if (++raiders >= limit) {
                    break;
                }
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

    /** Active camp defenses & facilities: mobile NPC allies, guard posts, medical tent regen, and supply depot. */
    private void updateBaseSystems() {
        int turretLv = player.getLevel(GameData.RecipeKind.BASE_TURRET);
        int wallLv = player.getLevel(GameData.RecipeKind.BASE_WALL);
        int medicLv = player.getLevel(GameData.RecipeKind.BASE_MEDIC);
        int farmLv = player.getLevel(GameData.RecipeKind.BASE_FARM);
        if (map.applyBaseUpgrades(turretLv, wallLv, medicLv, farmLv)) {
            solidRects.clear();
            solidRects.addAll(map.getSolidRects());
        }

        if (Math.hypot(player.getX() - lastPlayerX, player.getY() - lastPlayerY) > 0.5 || mouseFiring || autoFire) {
            idlePlayerTicks = 0;
        } else {
            idlePlayerTicks++;
        }
        lastPlayerX = player.getX();
        lastPlayerY = player.getY();

        int desiredAllies = Math.min(5, 1 + turretLv);
        while (allies.size() < desiredAllies) {
            allies.add(new AllyNpc(allies.size(), player.getX(), player.getY()));
        }
        boolean allowNpcFire = frame != null || idlePlayerTicks < 180;
        for (AllyNpc a : allies) {
            a.update(turretLv, allowNpcFire);
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
        int maxAllowed = settings.doubleSpawn
                ? GameData.MAX_LIVE_MONSTERS
                : (raidActive ? (18 + currentDay * 5 + getDifficulty() * 3) : (12 + currentDay * 4 + getDifficulty() * 2));
        int room = maxAllowed - getLiveMonsterCount();
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

    /** New monsters spawn along upcoming patrol segments or map borders and aggressively chase the runner. */
    private Monster spawnMonster(GameData.MonsterType type) {
        boolean boss = type == GameData.MonsterType.ZOMBIE_BOSS;
        Point p = null;
        int wpCount = map.getWaypointCount();
        if (!boss && wpCount > 0 && rng.nextBoolean()) {
            Point ahead = map.getWaypoint((waypointIndex + 1 + rng.nextInt(2)) % wpCount);
            int cx = ahead.x + rng.nextInt(121) - 60;
            int cy = ahead.y + rng.nextInt(121) - 60;
            Rectangle feet = new Rectangle(cx - type.hitboxW / 2, cy, type.hitboxW, 8);
            if (!map.isBlocked(feet) && player.distanceTo(cx, cy) > 130.0) {
                p = new Point(cx, cy);
            }
        }
        if (p == null) {
            p = map.randomSpawnPoint(rng, getCameraView());
        }
        Monster m = new Monster(type, p.x, p.y, map, player, GameData.monsterHpScale(wave, getDifficulty()));
        m.setRaiding(true);
        monsters.add(m);
        return m;
    }

    private void spawnBoss() {
        spawnMonster(GameData.MonsterType.ZOMBIE_BOSS);
        AssetLoader.playSound("boss_roar");
        showBanner("APEX MUTANT", "GIANT MUTANT BREACHED - HIT ON BEAT TO CRUSH IT!", BANNER_BOSS);
        shake(8, 30);
        triggerScreenFlash(new Color(255, 60, 60, 80), 10);
    }

    // ------------------------------------------------------------------ player & rhythm auto-run
    private void updatePlayer() {
        if (!player.isAlive()) {
            player.update();   // only the death animation
            return;
        }
        double aimX = getMouseWorldX(), aimY = getMouseWorldY();
        handleActionRequests(aimX, aimY);

        // Auto-runner advances continuously along waypoints; WASD dodges laterally around hazards.
        player.setMoveInput(false, false, false, false);
        Monster autoTarget = (autoFire && !mouseFiring) ? findAutoFireTarget() : null;
        if (autoTarget != null) {
            player.setAim(autoTarget.getX(), autoTarget.getY());
        } else {
            player.setAim(aimX, aimY);
        }
        double prevX = player.getX(), prevY = player.getY();
        player.update();
        updateAutoRunMovement(prevX, prevY);

        int beatIdx = (elapsedTicks + GameData.HALF_BEAT_TICKS) / GameData.BEAT_TICKS;
        if (beatShotRequested) {
            beatShotRequested = false;
            boolean useAutoLock = spaceAimTarget;
            spaceAimTarget = false;
            double tx = aimX, ty = aimY;
            if (useAutoLock) {
                Monster lock = findRhythmLockTarget();
                if (lock != null) {
                    tx = lock.getX();
                    ty = lock.getY();
                }
            }
            player.setAim(tx, ty);
            fireRhythmAt(tx, ty, beatIdx);
        } else if (getTicksFromNearestBeat() <= 1 && lastAutoBeatFired != beatIdx) {
            if (mouseFiring) {
                lastAutoBeatFired = beatIdx;
                player.setAim(aimX, aimY);
                fireRhythmAt(aimX, aimY, beatIdx);
            } else if (autoTarget != null) {
                lastAutoBeatFired = beatIdx;
                fireRhythmAt(autoTarget.getX(), autoTarget.getY(), beatIdx);
            }
        }
    }

    private void updateAutoRunMovement(double prevX, double prevY) {
        int wpCount = Math.max(1, map.getWaypointCount());
        Point wp = map.getWaypoint(waypointIndex);
        if (Math.hypot(wp.x - player.getX(), wp.y - player.getY()) < 26.0) {
            int finished = waypointIndex;
            waypointIndex = (waypointIndex + 1) % wpCount;
            if (finished == wpCount - 1) {
                onLapCompleted();
            }
            wp = map.getWaypoint(waypointIndex);
        }
        double angle = map.hasClearPath(player.getX(), player.getY(), wp.x, wp.y, player.getWidth())
                ? Math.atan2(wp.y - player.getY(), wp.x - player.getX())
                : map.getChaseAngle(player.getX(), player.getY(), wp.x, wp.y, player.getWidth());

        int manualX = (keyRight ? 1 : 0) - (keyLeft ? 1 : 0);
        int manualY = (keyDown ? 1 : 0) - (keyUp ? 1 : 0);
        double vx = Math.cos(angle) + manualX * 0.72;
        double vy = Math.sin(angle) + manualY * 0.72;
        double len = Math.hypot(vx, vy);
        double speed = player.getMoveSpeed() * (isFeverActive() ? 1.25 : 1.0);
        if (len > 0.01) {
            player.moveAutoRun(vx / len * speed, vy / len * speed);
        }
        if (Math.hypot(player.getX() - prevX, player.getY() - prevY) < 0.25) {
            stuckTicks++;
            if (stuckTicks > 40) {
                waypointIndex = (waypointIndex + 1) % wpCount;
                stuckTicks = 0;
            }
        } else {
            stuckTicks = 0;
        }
        if (elapsedTicks % (isFeverActive() ? 4 : 8) == 0) {
            addEffect(new Effect(Effect.Kind.AFTERIMAGE, player.getX(), player.getFeetY() - 8,
                    0, 0, 0, 0, 12, null,
                    isFeverActive() ? new Color(255, 220, 60, 140) : new Color(60, 225, 255, 105),
                    isFeverActive() ? 12 : 9));
        }
    }

    private void onLapCompleted() {
        lapsCompleted++;
        int healBefore = player.getHp();
        player.heal(25);
        int gainedHp = player.getHp() - healBefore;
        player.addInventory(GameData.ItemType.AMMO, 25);
        player.addInventory(GameData.ItemType.SCRAPS, 3);
        rhythmBonusScore += 250 * (1 + lapsCompleted);
        AssetLoader.playSound("wave");
        shake(5.5, 16);
        zoomKick = Math.min(0.14, zoomKick + 0.06);
        triggerScreenFlash(new Color(80, 240, 180, 75), 10);
        spawnShockwaveAndSparks(player.getX(), player.getY(), new Color(90, 245, 180), 42, 16);
        String msg = "+" + gainedHp + " HP / +25 AMMO / +3 SCRAPS";
        showBanner("LAP " + lapsCompleted + " COMPLETE!", msg, BANNER_WAVE);
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

    private Monster findRhythmLockTarget() {
        Monster best = null;
        double bestDist = 300.0;
        for (Monster m : monsters) {
            if (!m.isAlive()) {
                continue;
            }
            double d = player.distanceTo(m);
            if (d < bestDist && map.hasClearPath(player.getX(), player.getY(), m.getX(), m.getY(), 4)) {
                bestDist = d;
                best = m;
            }
        }
        return best;
    }

    private void fireRhythmAt(double targetX, double targetY, int beatIdx) {
        GameData.RhythmJudge judge = judgeCurrentTiming();
        boolean fever = isFeverActive();
        List<Bullet> shots = player.shootRhythm(targetX, targetY, judge, fever);
        if (shots.isEmpty()) {
            return;
        }
        bullets.addAll(shots);
        GameData.WeaponType w = player.getCurrentWeapon();
        double hx = player.getHandX(), hy = player.getHandY();
        double angle = Math.atan2(targetY - hy, targetX - hx);
        boolean freshBeat = lastRhythmBeatScored != beatIdx;

        if (judge == GameData.RhythmJudge.OFF_BEAT) {
            combo = 0;
            offBeatHits++;
            lastJudge = judge;
            lastJudgeTick = tickCount;
            AssetLoader.playSound("off_beat");
            shake(1.2, 4);
            addText(player.getX(), player.getY() - 26, judge.label, new Color(165, 165, 175));
            return;
        }

        if (freshBeat) {
            lastRhythmBeatScored = beatIdx;
            combo++;
            maxCombo = Math.max(maxCombo, combo);
            if (judge == GameData.RhythmJudge.PERFECT) {
                perfectHits++;
            } else if (judge == GameData.RhythmJudge.GREAT) {
                greatHits++;
            } else {
                goodHits++;
            }
            int mult = getComboMultiplier();
            int gain = judge.basePoints * mult * (fever ? 2 : 1);
            rhythmBonusScore += gain;
            if (!fever) {
                feverCharge = Math.min(GameData.FEVER_MAX, feverCharge + judge.feverDelta);
            }
            lastJudge = judge;
            lastJudgeTick = tickCount;
            addText(player.getX(), player.getY() - 28, judge.label + " +" + gain, judgeColor(judge));
        }

        double mx = hx + Math.cos(angle) * 14, my = hy + Math.sin(angle) * 14;
        if (judge == GameData.RhythmJudge.PERFECT) {
            AssetLoader.playSound("perfect_hit");
            double kick = (w.damage >= 30 ? 7.5 : 5.5) * (fever ? 1.25 : 1.0);
            recoilX -= Math.cos(angle) * (fever ? 7.0 : 5.0);
            recoilY -= Math.sin(angle) * (fever ? 7.0 : 5.0);
            shake(kick, 11);
            zoomKick = Math.min(0.15, zoomKick + (fever ? 0.055 : 0.04));
            triggerScreenFlash(fever ? new Color(255, 220, 60, 62) : new Color(55, 240, 255, 48), 5);
            spawnShockwaveAndSparks(mx, my, fever ? new Color(255, 225, 65) : new Color(75, 240, 255),
                    fever ? 28 : 22, fever ? 12 : 9);
            if (!w.melee) {
                double beamDist = Math.min(w.range, Math.hypot(targetX - mx, targetY - my) + 60);
                addEffect(new Effect(Effect.Kind.BEAM, mx, my,
                        mx + Math.cos(angle) * beamDist, my + Math.sin(angle) * beamDist,
                        0, 0, 6, null, fever ? new Color(255, 230, 80) : new Color(90, 245, 255), fever ? 4 : 3));
            }
        } else if (judge == GameData.RhythmJudge.GREAT) {
            recoilX -= Math.cos(angle) * 3.0;
            recoilY -= Math.sin(angle) * 3.0;
            shake(3.5, 8);
            zoomKick = Math.min(0.15, zoomKick + 0.025);
            spawnShockwaveAndSparks(mx, my, new Color(80, 235, 255), 16, 6);
        } else {
            recoilX -= Math.cos(angle) * 1.5;
            recoilY -= Math.sin(angle) * 1.5;
            shake(2.0, 5);
            spawnDirectionalSparks(mx, my, angle, judgeColor(judge), 3);
        }

        if (!fever && feverCharge >= GameData.FEVER_MAX) {
            activateFeverOverdrive();
        }
    }

    private void activateFeverOverdrive() {
        feverCharge = 0;
        feverTicks = GameData.FEVER_DURATION_TICKS;
        AssetLoader.playSound("fever");
        showBanner("FEVER OVERDRIVE!", "INFINITE AMMO / 2X SCORE / TURBO BEAT!", new Color(255, 220, 55));
        shake(11.0, 24);
        zoomKick = 0.14;
        triggerScreenFlash(new Color(255, 220, 65, 115), 14);
        spawnShockwaveAndSparks(player.getX(), player.getY(), new Color(255, 225, 65), 60, 28);
        for (Monster m : monsters) {
            if (m.isAlive() && m.distanceTo(player) <= 95.0) {
                boolean killed = m.takeHit(25, Math.atan2(m.getY() - player.getY(), m.getX() - player.getX()), 9);
                addText(m.getX(), m.getY() - m.getHeight(), "25!", TEXT_KILL);
                if (killed) {
                    onMonsterKilled(m);
                }
            }
        }
    }

    private void triggerScreenFlash(Color color, int ticks) {
        screenFlashColor = color;
        screenFlashTicks = ticks;
        screenFlashMax = Math.max(1, ticks);
    }

    private void spawnShockwaveAndSparks(double x, double y, Color color, int radius, int sparkCount) {
        addEffect(new Effect(Effect.Kind.SHOCKWAVE, x, y, 0, 0, 0, 0, 14, null, color, radius));
        for (int i = 0; i < sparkCount; i++) {
            double ang = rng.nextDouble() * Math.PI * 2;
            double spd = 1.6 + rng.nextDouble() * 3.6;
            addEffect(new Effect(Effect.Kind.SPARK, x, y, 0, 0,
                    Math.cos(ang) * spd, Math.sin(ang) * spd,
                    10 + rng.nextInt(8), null, color, 2 + rng.nextInt(3)));
        }
    }

    private void spawnDirectionalSparks(double x, double y, double baseAngle, Color color, int sparkCount) {
        for (int i = 0; i < sparkCount; i++) {
            double ang = baseAngle + (rng.nextDouble() - 0.5) * 0.9;
            double spd = 2.0 + rng.nextDouble() * 3.5;
            addEffect(new Effect(Effect.Kind.SPARK, x, y, 0, 0,
                    Math.cos(ang) * spd, Math.sin(ang) * spd,
                    9 + rng.nextInt(6), null, color, 2));
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

    /** Damages the player (armour and mercy time apply) with a red number, combo break, and heavy shake. */
    private int hurtPlayer(int damage) {
        int dealt = player.takeDamage(damage);
        if (dealt > 0) {
            combo = 0;
            addText(player.getX(), player.getY() - 22, "-" + dealt, TEXT_HURT);
            shake(5.5, 14);
            triggerScreenFlash(new Color(220, 35, 35, 85), 8);
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

    /** Score, loot, shockwave, sparks, and heavy kill shake. */
    private void onMonsterKilled(Monster m) {
        kills++;
        int mult = getComboMultiplier() * (isFeverActive() ? 2 : 1);
        killScore += m.getType().score * mult;
        drops.addAll(m.rollDrops(rng, player.getLootBonus()));
        if (!isFeverActive()) {
            feverCharge = Math.min(GameData.FEVER_MAX, feverCharge + 6);
            if (feverCharge >= GameData.FEVER_MAX) {
                activateFeverOverdrive();
            }
        }
        boolean boss = m.getType() == GameData.MonsterType.ZOMBIE_BOSS;
        shake(boss ? 14.0 : (isFeverActive() ? 8.0 : 5.5), boss ? 32 : 13);
        zoomKick = Math.min(0.16, zoomKick + (boss ? 0.10 : 0.04));
        triggerScreenFlash(boss ? new Color(255, 220, 60, 120) : new Color(255, 150, 50, 42), boss ? 14 : 5);
        spawnShockwaveAndSparks(m.getX(), m.getY(), boss ? new Color(255, 220, 60) : new Color(255, 130, 55),
                boss ? 56 : 26, boss ? 30 : 14);
        if (boss) {
            showBanner("BOSS DOWN!", "MASSIVE BEAT BONUS + SUPPLY CHEST", BANNER_WAVE);
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
                int tier = b.getRhythmTier();
                Color sparkCol = tier >= 3 ? new Color(255, 225, 65)
                        : (tier == 2 ? new Color(75, 240, 255) : new Color(255, 190, 95));
                spawnDirectionalSparks(m.getX(), m.getY() - 4, b.getAngle() + Math.PI, sparkCol, tier >= 2 ? 10 : 5);
                if (killed) {
                    onMonsterKilled(m);
                }
            }
        }
    }

    /** Rockets and grenades: damage every monster inside the blast and push it away from the centre. */
    private void explode(double x, double y, int radius, int damage) {
        addEffect(new Effect(Effect.Kind.EXPLOSION, x, y, 28, null, null, Math.max(48, radius * 2)));
        spawnShockwaveAndSparks(x, y, new Color(255, 165, 50), Math.max(36, radius), 22);
        AssetLoader.playSound("explosion");
        shake(9.5, 20);
        zoomKick = Math.min(0.16, zoomKick + 0.08);
        triggerScreenFlash(new Color(255, 175, 60, 75), 8);
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
            } else if (e.kind == Effect.Kind.SPARK) {
                e.x += e.vx;
                e.y += e.vy;
                e.vx *= 0.88;
                e.vy *= 0.88;
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
        zoomKick *= 0.85;
        recoilX *= 0.80;
        recoilY *= 0.80;
        if (screenFlashTicks > 0) {
            screenFlashTicks--;
        }
    }

    /** The little spark where a bullet stopped. */
    private void addImpact(Bullet b) {
        addEffect(new Effect(Effect.Kind.IMPACT, b.getX(), b.getY(), 15, null, null, 20));
    }

    private void addEffect(Effect e) {
        if (effects.size() < MAX_EFFECTS || e.kind == Effect.Kind.EXPLOSION
                || e.kind == Effect.Kind.SHOCKWAVE || e.kind == Effect.Kind.TEXT) {
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
        banner = new Banner(title, subtitle, color, tickCount, 115);
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
        return currentDay;
    }

    public boolean isNight() {
        return raidActive;
    }

    public int getPhaseSecondsRemaining() {
        int secInDay = getElapsedSeconds() % GameData.DAY_CYCLE_SECONDS;
        return raidActive ? (GameData.DAY_CYCLE_SECONDS - secInDay) : (GameData.DAYLIGHT_SECONDS - secInDay);
    }

    public int getDayCycleProgressPercent() {
        int secInDay = getElapsedSeconds() % GameData.DAY_CYCLE_SECONDS;
        if (!raidActive) {
            return secInDay * 100 / GameData.DAYLIGHT_SECONDS;
        }
        int nightLen = GameData.DAY_CYCLE_SECONDS - GameData.DAYLIGHT_SECONDS;
        return (secInDay - GameData.DAYLIGHT_SECONDS) * 100 / Math.max(1, nightLen);
    }

    public int getAllyCount() {
        return allies.size() + map.getGuardPosts().size();
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

    /** Kill points + rhythm timing points + 2 per second survived + 1000 for surviving, scaled by difficulty. */
    public int getScore() {
        double base = killScore + rhythmBonusScore + getElapsedSeconds() * 2 + (won ? 1000 : 0);
        return (int) Math.round(base * (0.5 + 0.25 * getDifficulty()));
    }

    public double getBeatPhase() {
        return (elapsedTicks % GameData.BEAT_TICKS) / (double) GameData.BEAT_TICKS;
    }

    public int getTicksFromNearestBeat() {
        int mod = elapsedTicks % GameData.BEAT_TICKS;
        return Math.min(mod, GameData.BEAT_TICKS - mod);
    }

    public boolean isInPerfectWindow() {
        return getTicksFromNearestBeat() <= GameData.PERFECT_WINDOW_TICKS;
    }

    public GameData.RhythmJudge judgeCurrentTiming() {
        int d = getTicksFromNearestBeat();
        if (d <= GameData.PERFECT_WINDOW_TICKS) {
            return GameData.RhythmJudge.PERFECT;
        }
        if (d <= GameData.GREAT_WINDOW_TICKS) {
            return GameData.RhythmJudge.GREAT;
        }
        if (d <= GameData.GOOD_WINDOW_TICKS) {
            return GameData.RhythmJudge.GOOD;
        }
        return GameData.RhythmJudge.OFF_BEAT;
    }

    public boolean isFeverActive() {
        return feverTicks > 0;
    }

    public int getFeverPercent() {
        if (feverTicks > 0) {
            return feverTicks * 100 / GameData.FEVER_DURATION_TICKS;
        }
        return feverCharge * 100 / GameData.FEVER_MAX;
    }

    public int getCombo() {
        return combo;
    }

    public int getMaxCombo() {
        return maxCombo;
    }

    public int getComboMultiplier() {
        return Math.min(4, 1 + combo / 10);
    }

    public int getLapsCompleted() {
        return lapsCompleted;
    }

    public int getPerfectHits() {
        return perfectHits;
    }

    public int getGreatHits() {
        return greatHits;
    }

    public int getGoodHits() {
        return goodHits;
    }

    public int getOffBeatHits() {
        return offBeatHits;
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
        double unzoomed = (mouseX - getViewWidth() / 2.0) / Math.max(0.5, currentZoom) + getViewWidth() / 2.0;
        return camX + unzoomed / SCALE;
    }

    private double getMouseWorldY() {
        double unzoomed = (mouseY - getViewHeight() / 2.0) / Math.max(0.5, currentZoom) + getViewHeight() / 2.0;
        return camY + unzoomed / SCALE;
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
            case KeyEvent.VK_SPACE:
            case KeyEvent.VK_F:
                beatShotRequested = true;
                spaceAimTarget = true;
                break;
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
            beatShotRequested = true;
            spaceAimTarget = false;
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

    /** Ground, auto-run beat track, sorted props/entities, monsters' beat rings, and effects. */
    private void drawWorld(Graphics2D g2) {
        double viewX = camX + shakeX + recoilX, viewY = camY + shakeY + recoilY;
        originX = (int) Math.round(viewX * SCALE);
        originY = (int) Math.round(viewY * SCALE);
        double beatPulseZoom = (1.0 - getBeatPhase()) * (isFeverActive() ? 0.018 : 0.008);
        currentZoom = 1.0 + zoomKick + beatPulseZoom;
        int vw = getViewWidth(), vh = getViewHeight();
        Rectangle view = new Rectangle((int) Math.floor(viewX) - 24, (int) Math.floor(viewY) - 24,
                vw / SCALE + 52, vh / SCALE + 52);
        AffineTransform old = g2.getTransform();
        g2.translate(vw / 2.0, vh / 2.0);
        g2.scale(currentZoom, currentZoom);
        g2.translate(-vw / 2.0, -vh / 2.0);
        g2.translate(-originX, -originY);
        g2.scale(SCALE, SCALE);

        map.drawGround(g2, view);
        drawCampPerimeter(g2, view);
        drawAutoRunTrack(g2, view);
        double beatPhase = getBeatPhase();
        boolean perfectWin = isInPerfectWindow();
        for (Monster m : monsters) {
            if (m.isAlive() && view.contains(m.getX(), m.getY())) {
                m.drawBeatTargetRing(g2, beatPhase, perfectWin);
            }
        }
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

    /** Glowing neon waypoints & scrolling floor chevrons showing the runner's next turns. */
    private void drawAutoRunTrack(Graphics2D g2, Rectangle view) {
        int wpCount = map.getWaypointCount();
        if (wpCount <= 0) {
            return;
        }
        Stroke oldStroke = g2.getStroke();
        Color trackCol = isFeverActive() ? new Color(255, 220, 60, 110) : new Color(60, 225, 255, 95);
        g2.setColor(trackCol);
        g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                1f, new float[]{4f, 6f}, Math.max(0f, 10f - (float) ((elapsedTicks * 0.5) % 10.0))));
        double px = player.getX(), py = player.getFeetY() - 2;
        int preview = Math.min(4, wpCount);
        for (int i = 0; i < preview; i++) {
            Point wp = map.getWaypoint((waypointIndex + i) % wpCount);
            if (view.intersectsLine(px, py, wp.x, wp.y)) {
                g2.drawLine((int) px, (int) py, wp.x, wp.y);
            }
            px = wp.x;
            py = wp.y;
        }
        Point cur = map.getWaypoint(waypointIndex);
        if (view.contains(cur.x, cur.y)) {
            int pulse = 8 + (int) ((1.0 - getBeatPhase()) * 6);
            g2.setStroke(new BasicStroke(1.8f));
            g2.setColor(isFeverActive() ? new Color(255, 230, 80, 175) : new Color(80, 240, 255, 165));
            g2.drawOval(cur.x - pulse, cur.y - pulse / 2, pulse * 2, pulse);
        }
        g2.setStroke(oldStroke);
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
        for (AllyNpc a : allies) {
            if (near.contains(a.x, a.y)) {
                drawList.add(a);
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

    /** Explosions, impacts, neon beams, shockwaves, sparks, and runner afterimages in world space. */
    private void drawWorldEffects(Graphics2D g2) {
        Composite oldComp = g2.getComposite();
        Stroke oldStroke = g2.getStroke();
        for (Effect e : effects) {
            float progress = Math.min(1f, e.age / (float) e.life);
            float alpha = Math.max(0f, 1f - progress);
            if (e.kind == Effect.Kind.EXPLOSION || e.kind == Effect.Kind.IMPACT) {
                BufferedImage[] frames = (e.kind == Effect.Kind.EXPLOSION) ? explosionFrames : impactFrames;
                BufferedImage img = frames[Math.min(frames.length - 1, e.age * frames.length / e.life)];
                int half = e.size / 2;
                g2.drawImage(img, (int) e.x - half, (int) e.y - half, e.size, e.size, null);
            } else if (e.kind == Effect.Kind.SHOCKWAVE) {
                double r = e.size * (0.25 + 1.15 * progress);
                Color c = e.color != null ? e.color : Theme.GOLD;
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, alpha * 0.9f)));
                g2.setColor(c);
                g2.setStroke(new BasicStroke(Math.max(0.8f, 2.8f * alpha)));
                g2.drawOval((int) (e.x - r), (int) (e.y - r), (int) (r * 2), (int) (r * 2));
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(Math.max(0.4f, 1.1f * alpha)));
                g2.drawOval((int) (e.x - r * 0.85), (int) (e.y - r * 0.85), (int) (r * 1.7), (int) (r * 1.7));
            } else if (e.kind == Effect.Kind.BEAM) {
                Color c = e.color != null ? e.color : new Color(80, 240, 255);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                g2.setColor(c);
                g2.setStroke(new BasicStroke(Math.max(0.8f, (e.size * 1.4f) * alpha), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine((int) e.x, (int) e.y, (int) e.x2, (int) e.y2);
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(Math.max(0.4f, 1.3f * alpha), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine((int) e.x, (int) e.y, (int) e.x2, (int) e.y2);
            } else if (e.kind == Effect.Kind.SPARK) {
                Color c = e.color != null ? e.color : Theme.GOLD;
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                g2.setColor(c);
                g2.setStroke(new BasicStroke(Math.max(0.8f, e.size * alpha), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine((int) (e.x - e.vx * 1.8), (int) (e.y - e.vy * 1.8), (int) e.x, (int) e.y);
                g2.setColor(Color.WHITE);
                g2.fillOval((int) e.x - 1, (int) e.y - 1, 2, 2);
            } else if (e.kind == Effect.Kind.AFTERIMAGE) {
                Color c = e.color != null ? e.color : new Color(70, 225, 255);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * 0.45f));
                g2.setColor(c);
                g2.fillOval((int) e.x - e.size / 2, (int) e.y - e.size, e.size, e.size * 2);
            }
        }
        g2.setStroke(oldStroke);
        g2.setComposite(oldComp);
    }

    private int worldToScreenX(double wx) {
        double unzoomed = wx * SCALE - originX;
        return (int) Math.round((unzoomed - getViewWidth() / 2.0) * currentZoom + getViewWidth() / 2.0);
    }

    private int worldToScreenY(double wy) {
        double unzoomed = wy * SCALE - originY;
        return (int) Math.round((unzoomed - getViewHeight() / 2.0) * currentZoom + getViewHeight() / 2.0);
    }

    /** Damage numbers, rhythm judgments, pickup labels, and base NPC prompts in sharp screen pixels. */
    private void drawFloatingTexts(Graphics2D g2) {
        Point trader = map.getTraderPosition();
        if (!gameOver && player.distanceTo(trader.x, trader.y) < 125) {
            String prompt = "PRESS B - CAMP NPC";
            int pw = Theme.pixelWidth(prompt, 2) + 16;
            int ph = 20;
            int tx = worldToScreenX(trader.x) - pw / 2;
            int ty = Math.max(8, worldToScreenY(trader.y - 26));
            Theme.paintPanel(g2, Theme.Panel.GLASS, tx, ty, pw, ph, 1);
            Theme.drawPixelTextShadow(g2, prompt, tx + 8, ty + 5, 2, Theme.GOLD);
        }
        Composite old = g2.getComposite();
        for (Effect e : effects) {
            if (e.kind != Effect.Kind.TEXT) {
                continue;
            }
            float left = 1f - e.age / (float) e.life;
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, left * 2.5f)));
            String msg = e.text == null ? "" : e.text.toUpperCase();
            int sx = worldToScreenX(e.x) - Theme.pixelWidth(msg, 2) / 2;
            int sy = worldToScreenY(e.y) - 10;
            Theme.drawPixelTextShadow(g2, msg, sx, sy, 2, e.color);
        }
        g2.setComposite(old);
    }

    /** Everything that sits on the glass in front of the world. */
    private void drawOverlay(Graphics2D g2) {
        drawNightLighting(g2);
        drawDangerTint(g2);
        drawScreenFlash(g2);
        drawFeverBorder(g2);
        drawBanner(g2);
        drawMiniMap(g2);
        if (gameOver) {
            drawGameOverScreen(g2);
            return;
        }
        drawBossBar(g2);
        drawComboAndFeverHud(g2);
        drawBeatHighwayBar(g2);
        drawHint(g2);
        if (paused) {
            drawPauseScreen(g2);
        } else if (mouseInside) {
            drawCrosshair(g2);
        }
    }

    private void drawScreenFlash(Graphics2D g2) {
        if (screenFlashTicks <= 0 || screenFlashColor == null) {
            return;
        }
        float ratio = screenFlashTicks / (float) Math.max(1, screenFlashMax);
        int alpha = Math.max(0, Math.min(180, Math.round(screenFlashColor.getAlpha() * ratio)));
        g2.setColor(new Color(screenFlashColor.getRed(), screenFlashColor.getGreen(), screenFlashColor.getBlue(), alpha));
        g2.fillRect(0, 0, getViewWidth(), getViewHeight());
    }

    private void drawFeverBorder(Graphics2D g2) {
        int w = getViewWidth(), h = getViewHeight();
        boolean fever = isFeverActive();
        if (!fever && !isInPerfectWindow()) {
            return;
        }
        Stroke old = g2.getStroke();
        double pulse = 1.0 - getBeatPhase();
        if (fever) {
            int glowAlpha = 70 + (int) (pulse * 110);
            g2.setColor(new Color(255, 210, 45, glowAlpha));
            g2.setStroke(new BasicStroke(6f));
            g2.drawRect(3, 3, w - 6, h - 6);
            g2.setColor(new Color(255, 80, 195, glowAlpha / 2));
            g2.setStroke(new BasicStroke(12f));
            g2.drawRect(6, 6, w - 12, h - 12);
            // Animated corner beat equalizer bars during Fever Overdrive
            for (int i = 0; i < 8; i++) {
                double barPhase = (getBeatPhase() + i * 0.14) % 1.0;
                int bh = 10 + (int) ((1.0 - Math.abs(barPhase - 0.5) * 2.0) * 28);
                g2.setColor(i % 2 == 0 ? new Color(255, 220, 60, 190) : new Color(255, 95, 200, 180));
                g2.fillRect(14 + i * 8, h - 14 - bh, 5, bh);
                g2.fillRect(w - 19 - i * 8, h - 14 - bh, 5, bh);
            }
        } else {
            g2.setColor(new Color(65, 235, 255, 45));
            g2.setStroke(new BasicStroke(3f));
            g2.drawRect(2, 2, w - 4, h - 4);
        }
        g2.setStroke(old);
    }

    private void drawComboAndFeverHud(Graphics2D g2) {
        boolean fever = isFeverActive();
        int x = 14, y = 14, w = 228, h = 78;
        Theme.paintPanel(g2, Theme.Panel.GLASS, x, y, w, h, 1);

        Color comboCol = fever ? new Color(255, 225, 65) : (combo >= 10 ? new Color(80, 240, 255) : Theme.TEXT);
        String comboTxt = combo > 0 ? ("COMBO " + combo + "X") : "125 BPM RUN";
        Theme.drawPixelTextShadow(g2, comboTxt, x + 12, y + 10, 3, comboCol);

        String meta = "MULT " + getComboMultiplier() + "X   LAP " + (lapsCompleted + 1);
        Theme.drawPixelTextShadow(g2, meta, x + 12, y + 32, 2, Theme.TEXT_2);

        String feverLabel = fever ? "FEVER OVERDRIVE!" : ("FEVER " + getFeverPercent() + "%");
        Theme.drawPixelTextShadow(g2, feverLabel, x + 12, y + 48, 2, fever ? Theme.GOLD : new Color(255, 185, 65));

        int bx = x + 12, by = y + 63, bw = w - 24, bh = 8;
        g2.setColor(new Color(20, 22, 30, 220));
        g2.fillRect(bx, by, bw, bh);
        int fillW = bw * getFeverPercent() / 100;
        if (fillW > 0) {
            g2.setColor(fever ? new Color(255, 215, 50) : new Color(65, 225, 255));
            g2.fillRect(bx, by, fillW, bh);
        }
        g2.setColor(new Color(220, 230, 245, 90));
        g2.drawRect(bx, by, bw, bh);

        if (lastJudge != null && tickCount - lastJudgeTick < 36) {
            String j = lastJudge.label;
            int scale = lastJudge == GameData.RhythmJudge.PERFECT ? 3 : 2;
            Theme.drawPixelTextShadow(g2, j, x + 12, y + h + 8, scale, judgeColor(lastJudge));
        }
    }

    private static Color judgeColor(GameData.RhythmJudge j) {
        switch (j) {
            case PERFECT: return new Color(255, 225, 65);
            case GREAT: return new Color(75, 240, 255);
            case GOOD: return new Color(135, 230, 120);
            default: return new Color(175, 175, 185);
        }
    }

    private void drawBeatHighwayBar(Graphics2D g2) {
        int vw = getViewWidth(), vh = getViewHeight();
        int barW = 350, barH = 44;
        int x = (vw - barW) / 2;
        int y = vh - barH - 12;
        Theme.paintPanel(g2, Theme.Panel.GLASS, x, y, barW, barH, 1);

        int cx = x + barW / 2;
        int laneY = y + 14;
        int halfW = barW / 2 - 22;

        // Track horizontal guide line
        g2.setColor(new Color(95, 110, 130, 150));
        g2.fillRect(cx - halfW, laneY - 1, halfW * 2, 2);

        // Moving incoming beat bars gliding from left and right toward center target
        double phase = getBeatPhase();   // 0..1 between beats; 0 & 1 = exact beat
        boolean perfectWin = isInPerfectWindow();
        boolean fever = isFeverActive();
        for (int b = 0; b < 3; b++) {
            double frac = ((1.0 - phase) + b) / 3.0;
            int off = (int) Math.round(frac * halfW);
            int alpha = Math.max(45, (int) (255 * (1.0 - frac * 0.72)));
            Color barCol = fever ? new Color(255, 220, 60, alpha) : new Color(75, 235, 255, alpha);
            g2.setColor(barCol);
            g2.fillRect(cx - off - 3, laneY - 9, 6, 18);
            g2.fillRect(cx + off - 3, laneY - 9, 6, 18);
        }

        // Center beat bullseye reticle
        int coreR = perfectWin ? 12 : 9;
        g2.setColor(perfectWin ? (fever ? Theme.GOLD : new Color(75, 245, 255)) : new Color(210, 215, 225, 180));
        Stroke old = g2.getStroke();
        g2.setStroke(new BasicStroke(perfectWin ? 2.6f : 1.6f));
        g2.drawOval(cx - coreR, laneY - coreR, coreR * 2, coreR * 2);
        if (perfectWin) {
            g2.setColor(new Color(255, 255, 255, 150));
            g2.fillOval(cx - 4, laneY - 4, 8, 8);
        }
        g2.setStroke(old);

        String prompt = fever ? "FEVER OVERDRIVE!  SPACE / CLICK" : "SHOOT ON BEAT  SPACE / CLICK";
        int tw = Theme.pixelWidth(prompt, 2);
        Theme.drawPixelTextShadow(g2, prompt, cx - tw / 2, y + 29, 2, perfectWin ? Theme.GOLD : Theme.TEXT_2);
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
        mg.setColor(new Color(8, 12, 26, Math.round(nightAlpha * 130)));
        mg.fillRect(0, 0, mw, mh);

        mg.setComposite(AlphaComposite.DstOut);
        punchLightPool(mg, (float) (player.getX() - camX), (float) (player.getY() - camY), 115f);
        Rectangle camp = map.getCampArea();
        punchLightPool(mg, (float) (camp.getCenterX() - camX), (float) (camp.getCenterY() - camY), 145f);
        for (AllyNpc a : allies) {
            punchLightPool(mg, (float) (a.x - camX), (float) (a.y - camY), 68f);
        }
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
        g.setColor(new Color(0x88D470));
        for (AllyNpc a : allies) {
            int ax = left + (int) (a.x * perPx), ay = top + (int) (a.y * perPx);
            g.fillRect(ax - 1, ay - 1, 3, 3);
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
        String intel = "CHESTS " + getOuterChestCount() + "  ALLIES " + getAllyCount();
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
        int cx = getViewWidth() / 2, top = getViewHeight() / 7;
        Theme.drawPixelTextShadow(g, b.title, cx - Theme.pixelWidth(b.title, 5) / 2, top, 5, b.color);
        top += 28;
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
        int x = (getViewWidth() - w) / 2, y = getViewHeight() - h - 64;
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
            {"Dodge / Strafe", "w", "a", "s", "d"}, {"Shoot on Beat", "space", "mouse_left"},
            {"Grenade", "mouse_right", "g"}, {"Medkit", "e"},
            {"Switch weapon (1-7)", "mouse_mid"}, {"Upgrade Shop", "b"},
            {"Resume", "p", "esc"}
        };
        BufferedImage title = Theme.kit("titles/paused", 2);
        int rowH = 40, rows = (controls.length + 1) / 2;
        int cardW = 630, cardH = 34 + title.getHeight() + 44 + rows * rowH + 14;
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
        String stats = "LAP " + (lapsCompleted + 1) + "   KILLS " + kills + "   MAX COMBO " + maxCombo + "X   SCORE " + getScore() + "   TIME " + formatTime(getElapsedSeconds());
        Theme.drawPixelTextShadow(g2, stats, (w - Theme.pixelWidth(stats, 3)) / 2, h / 2 + 16, 3, Theme.TEXT);
    }
}
