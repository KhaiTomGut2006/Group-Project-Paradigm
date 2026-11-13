// Member1 Firstname Lastname   6XXXXXX1
// Member2 Firstname Lastname   6XXXXXX2
// Member3 Firstname Lastname   6XXXXXX3
// Member4 Firstname Lastname   6XXXXXX4
// Member5 Firstname Lastname   6XXXXXX5
package Project3_XXX;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
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
    private static final Color TEXT_SHADOW = new Color(0, 0, 0, 200);
    private static final Color TEXT_DAMAGE = new Color(255, 245, 225);
    private static final Color TEXT_KILL = new Color(255, 200, 60);
    private static final Color TEXT_HURT = new Color(255, 70, 60);
    private static final Color TEXT_LOOT = new Color(140, 235, 120);
    private static final Color BANNER_WAVE = new Color(255, 196, 70);
    private static final Color BANNER_BOSS = new Color(255, 80, 60);
    private static final Font FONT_FLOAT = new Font(Font.SANS_SERIF, Font.BOLD, 13);
    private static final Font FONT_HINT = new Font(Font.SANS_SERIF, Font.BOLD, 15);
    private static final Font FONT_SMALL = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    private static final Font FONT_BANNER = new Font(Font.SANS_SERIF, Font.BOLD, 42);
    private static final Font FONT_BANNER_SUB = new Font(Font.SANS_SERIF, Font.BOLD, 18);
    private static final Font FONT_HELP = new Font(Font.MONOSPACED, Font.BOLD, 15);

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
    private int bossTimer;                     // > 0: a boss walks in when it reaches 0
    private int gameOverTimer;
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
    private final transient BufferedImage crosshair = AssetLoader.getCrosshair();
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
        showBanner("WAVE 1", "Survive " + formatTime(GameData.TIME_LIMIT_SECONDS) + "  -  B opens the workshop", BANNER_WAVE);
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

    /** Survival time, wave changes, the boss timer and the win. */
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
        if (bossTimer > 0 && --bossTimer == 0) {
            spawnBoss();
        }
    }

    private void startWave(int number) {
        wave = number;
        AssetLoader.playSound("wave");
        if (number % GameData.BOSS_EVERY_WAVES == 0) {
            showBanner("WAVE " + number, "WARNING: a boss is coming!", BANNER_BOSS);
            bossTimer = BOSS_WARNING_TICKS;
        } else if (number == GameData.TIME_LIMIT_SECONDS / GameData.WAVE_SECONDS) {
            showBanner("FINAL WAVE", "Hold on for 30 more seconds!", BANNER_WAVE);
        } else {
            showBanner("WAVE " + number, "The horde grows stronger", BANNER_WAVE);
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

    /** New monsters appear at a free spot near the map edge that the camera cannot see. */
    private Monster spawnMonster(GameData.MonsterType type) {
        Point p = map.randomSpawnPoint(rng, getCameraView());
        Monster m = new Monster(type, p.x, p.y, map, player, GameData.monsterHpScale(wave, getDifficulty()));
        monsters.add(m);
        return m;
    }

    private void spawnBoss() {
        spawnMonster(GameData.MonsterType.ZOMBIE_BOSS);
        AssetLoader.playSound("boss_roar");
        showBanner("BRUTE BOSS", "has entered the arena", BANNER_BOSS);
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
            GameData.WeaponType w = GameData.WeaponType.values()[slot];
            if (player.ownsWeapon(w)) {
                player.switchWeapon(w);
            } else {
                player.showHint("You do not have the " + w.displayName + " yet");
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

    /** Damage numbers and pickup labels, drawn in screen pixels so the text stays sharp. */
    private void drawFloatingTexts(Graphics2D g2) {
        g2.setFont(FONT_FLOAT);
        FontMetrics fm = g2.getFontMetrics();
        Composite old = g2.getComposite();
        for (Effect e : effects) {
            if (e.kind != Effect.Kind.TEXT) {
                continue;
            }
            float left = 1f - e.age / (float) e.life;
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, left * 2.5f)));
            int sx = (int) Math.round(e.x * SCALE) - originX - fm.stringWidth(e.text) / 2;
            int sy = (int) Math.round(e.y * SCALE) - originY;
            drawShadowedText(g2, e.text, sx, sy, e.color);
        }
        g2.setComposite(old);
    }

    /** Everything that sits on the glass in front of the world. */
    private void drawOverlay(Graphics2D g2) {
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

    /** A wide health bar at the top while a boss is alive. */
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
        int barW = 360, x = (getViewWidth() - barW) / 2, y = 26;
        g2.setColor(new Color(10, 8, 10, 190));
        g2.fillRect(x - 3, y - 3, barW + 6, 16);
        g2.setColor(new Color(90, 20, 15));
        g2.fillRect(x, y, barW, 10);
        g2.setColor(new Color(230, 110, 25));
        g2.fillRect(x, y, barW * boss.getHp() / boss.getMaxHp(), 10);
        g2.setFont(FONT_SMALL);
        String name = boss.getType().displayName.toUpperCase();
        drawShadowedText(g2, name, (getViewWidth() - g2.getFontMetrics().stringWidth(name)) / 2, y - 6, BANNER_BOSS);
    }

    /** The 56x36 map picture drawn 3x in the top-right corner, with dots for everything alive. */
    private void drawMiniMap(Graphics2D g2) {
        BufferedImage mini = map.getMinimap();
        int s = 3, w = mini.getWidth() * s, h = mini.getHeight() * s;
        int left = getViewWidth() - w - 12, top = 12;
        double perPx = s / (double) GameData.TILE;   // mini-map pixels per world pixel
        g2.setColor(new Color(0, 0, 0, 170));
        g2.fillRect(left - 3, top - 3, w + 6, h + 6);
        Composite old = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f));
        g2.drawImage(mini, left, top, w, h, null);
        g2.setComposite(old);
        for (DroppedItem d : drops) {
            if (d.getType() == GameData.ItemType.CHEST) {
                g2.setColor(new Color(255, 220, 60));
                g2.fillRect(left + (int) (d.getX() * perPx) - 2, top + (int) (d.getY() * perPx) - 2, 5, 5);
            }
        }
        for (Monster m : monsters) {
            if (!m.isAlive()) {
                continue;
            }
            boolean boss = m.getType() == GameData.MonsterType.ZOMBIE_BOSS;
            int size = boss ? 5 : 2;
            g2.setColor(boss ? new Color(255, 140, 30) : new Color(235, 50, 45));
            g2.fillRect(left + (int) (m.getX() * perPx) - size / 2, top + (int) (m.getY() * perPx) - size / 2, size, size);
        }
        g2.setColor(new Color(255, 255, 255, 140));
        g2.drawRect(left + (int) (camX * perPx), top + (int) (camY * perPx),
                (int) (getViewWidth() / (double) SCALE * perPx), (int) (getViewHeight() / (double) SCALE * perPx));
        g2.setColor(Color.WHITE);
        g2.fillRect(left + (int) (player.getX() * perPx) - 2, top + (int) (player.getY() * perPx) - 2, 4, 4);
        g2.setColor(new Color(120, 110, 100));
        g2.drawRect(left - 3, top - 3, w + 5, h + 5);
    }

    /** Fades in, stays, fades out. */
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
        Composite old = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, alpha)));
        int cy = getViewHeight() / 4 + 10;
        g2.setColor(new Color(0, 0, 0, 120));
        g2.fillRect(0, cy - 48, getViewWidth(), 82);
        drawCenteredText(g2, b.title, cy, FONT_BANNER, b.color);
        drawCenteredText(g2, b.subtitle, cy + 26, FONT_BANNER_SUB, Color.WHITE);
        g2.setComposite(old);
    }

    /** Player messages such as "Out of ammo - switched to Pistol". */
    private void drawHint(Graphics2D g2) {
        String hint = player.getHint();
        if (hint != null) {
            drawCenteredText(g2, hint, getViewHeight() - 28, FONT_HINT, new Color(255, 225, 120));
        }
    }

    /** The crosshair sprite follows the mouse; a ring around it fills up while the weapon reloads. */
    private void drawCrosshair(Graphics2D g2) {
        int size = crosshair.getWidth() * 3 / 2;
        g2.drawImage(crosshair, mouseX - size / 2, mouseY - size / 2, size, size, null);
        double reload = player.getReloadProgress();
        if (reload < 1.0 && player.getCooldownTicks(player.getCurrentWeapon()) > 20) {
            g2.setColor(new Color(255, 255, 255, 170));
            g2.setStroke(new BasicStroke(2f));
            g2.draw(new Arc2D.Double(mouseX - 20, mouseY - 20, 40, 40, 90, -360 * reload, Arc2D.OPEN));
        }
    }

    private void drawPauseScreen(Graphics2D g2) {
        int w = getViewWidth(), h = getViewHeight();
        g2.setColor(new Color(0, 0, 0, 160));
        g2.fillRect(0, 0, w, h);
        int cy = h / 2 - 120;
        drawCenteredText(g2, "PAUSED", cy, FONT_BANNER, Color.WHITE);
        drawCenteredText(g2, "Press P or Esc to resume", cy + 30, FONT_BANNER_SUB, new Color(255, 210, 110));
        String[] help = {
            "W A S D ....... move",
            "Mouse ......... aim, hold left button to fire",
            "Right click/G . throw a grenade",
            "E ............. use a medkit",
            "1-8 / wheel ... switch weapon",
            "B ............. open the workshop"
        };
        g2.setFont(FONT_HELP);
        int blockW = g2.getFontMetrics().stringWidth(help[1]);
        int x = (w - blockW) / 2;
        g2.setColor(new Color(30, 26, 32, 220));
        g2.fillRect(x - 18, cy + 50, blockW + 36, help.length * 24 + 16);
        for (int i = 0; i < help.length; i++) {
            drawShadowedText(g2, help[i], x, cy + 74 + i * 24, new Color(225, 225, 225));
        }
    }

    private void drawGameOverScreen(Graphics2D g2) {
        int w = getViewWidth(), h = getViewHeight();
        g2.setColor(new Color(0, 0, 0, won ? 120 : 150));
        g2.fillRect(0, 0, w, h);
        String title = won ? "YOU SURVIVED!" : "YOU DIED";
        drawCenteredText(g2, title, h / 2 - 10, FONT_BANNER, won ? new Color(120, 235, 110) : BANNER_BOSS);
        String stats = "Score " + getScore() + "    Kills " + kills + "    Wave " + wave
                + "    Time " + formatTime(getElapsedSeconds());
        drawCenteredText(g2, stats, h / 2 + 24, FONT_BANNER_SUB, Color.WHITE);
    }

    private void drawCenteredText(Graphics2D g2, String text, int baselineY, Font font, Color color) {
        g2.setFont(font);
        int x = (getViewWidth() - g2.getFontMetrics().stringWidth(text)) / 2;
        drawShadowedText(g2, text, x, baselineY, color);
    }

    /** Text with a dark shadow so it can be read on any ground. */
    private static void drawShadowedText(Graphics2D g2, String text, int x, int y, Color color) {
        g2.setColor(TEXT_SHADOW);
        g2.drawString(text, x + 2, y + 2);
        g2.setColor(color);
        g2.drawString(text, x, y);
    }
}
