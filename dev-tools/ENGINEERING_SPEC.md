# Last Camp Survivor — Engineering Spec (orchestrator contract)

Read `/Volumes/M.2/GitHub/Group Project Paradigm/PROJECT_STRUCTURE.md` first: it is the product
design. This file adds the engineering decisions and the cross-class API contract. Where the two
disagree, this file wins. Everything not fixed here is your call as the developer.

## 0. Environment
- ROOT  = `/Volumes/M.2/GitHub/Group Project Paradigm`
- SRC   = `ROOT/src/main/java/Project3_XXX`   (package `Project3_XXX`, flat, no sub-packages)
- RES   = `SRC/resources/{characters,weapons,items,tiles,ui,sounds}`
- Raw art = `ROOT/UPDATE 2_ZombiePack` (read-only; never modify, never reference at runtime)
- JDK 26 (the only JDK on this machine; `java`/`javac` are NOT on PATH):
  `JAVAC="/Applications/Apache NetBeans.app/Contents/Home/bin/javac"`,
  `JAVA="/Applications/Apache NetBeans.app/Contents/Home/bin/java"`
- Compile: `"$JAVAC" -Xlint:all -d "$ROOT/target/classes" "$ROOT"/src/main/java/Project3_XXX/*.java`
- Run:     `cd "$ROOT" && "$JAVA" -cp target/classes:src/main/java Project3_XXX.MainApplication`
- No python PIL, no brew, no maven on PATH. python3 stdlib is available (e.g. `wave`).
- Throwaway tools / test harnesses / previews go in the scratchpad dir
  `/private/tmp/claude-501/-Users-tanawatgut--claude-spares-spare-79cmOx/96a86cfa-b7a6-4475-ba1d-800397290095/scratchpad/`
  (use a subfolder), NEVER inside ROOT. Only game code + resources live in ROOT.
- The working tree is not a git repo. Do not init git.

## 1. Hard constraints (course rules — losing any of these loses marks)
- `javax.swing` + `java.awt` + `javax.sound.sampled` + `javax.imageio` only. No JavaFX, no external libs.
- Must compile with zero errors and zero warnings under `-Xlint:all` on Java 26.
- Entry point `Project3_XXX.MainApplication.main`. Frames: launcher 900x620, game 1100x660
  (never above 1200x700), components kept away from frame borders.
- First lines of EVERY .java file: the member header comment block (section 7).
- All files read via relative lookups inside `Project3_XXX` (section 3 AssetLoader). No absolute paths.
- Source must be pure ASCII (no Thai, no emoji, no fancy quotes) so it compiles on any PC/encoding.
- Required components and the 5 named event handlers of PROJECT_STRUCTURE.md section 2 must all
  exist and each do substantial work.
- Code is presented in class by students: favour clear, short methods, descriptive names and
  English comments explaining *why*. No clever tricks, no lambdas-inside-lambdas, no streams
  gymnastics, no reflection, no generics wizardry. Plain OOP: inheritance from `Entity`,
  overriding `update()`/`draw()`, enums carrying data.

## 2. Files (PROJECT_STRUCTURE.md list + ONE approved addition)
`MainApplication, GameFrame, GamePanel, UpgradeDialog, Entity, Player, Monster, Bullet,
DroppedItem, GameData, AssetLoader` + **`GameMap.java`** (approved addition: tile map, props,
collision, spawn points — keeps GamePanel explainable). Small helper types go in as static nested
classes (e.g. `GamePanel.Effect`, `GameMap.Prop`, `GameData.UpgradeRecipe`), not new files.
Also: `SRC/readme.txt`, `ROOT/pom.xml` (minimal, `maven.compiler.release` 26, and a `<resources>`
entry that copies non-.java files from `src/main/java` so NetBeans "Run" finds the resources).

## 3. Cross-class API contract (the parts other developers code against — keep these exact)

### GameData (constants + enums + small data classes; no Swing code)
```java
public static final int TILE = 32;              // world tile size in pixels
public static final int SCALE = 2;              // world is drawn at 2x (pixel-art, nearest neighbour)
public static final int GAME_W = 1100, GAME_H = 660, LAUNCHER_W = 900, LAUNCHER_H = 620;
public static final String[] GROUP_MEMBERS;     // section 7 placeholders, shown in the launcher

public enum ItemType { SCRAPS, WOOD, STEEL, ELECTRONICS, BATTERY, BLUEPRINT, MEDKIT, AMMO, FOOD,
                       GRENADE, ARMOR_VEST, CHEST }   // + displayName, sprite file; CHEST is never stored
public enum WeaponType { KNIFE, AXE, PISTOL, REVOLVER, SHOTGUN, RIFLE, SNIPER, ROCKET_LAUNCHER }
     // displayName, damage, cooldownMs, bulletSpeed, pellets, spreadDeg, ammoCost, range, melee, pierce, explosive
public enum MonsterType { ZOMBIE_NORMAL, ZOMBIE_RUNNER, ZOMBIE_TOXIC, BANDIT_SHOOTER, ZOMBIE_BOSS }
     // displayName, maxHp, speed, damage, score, sprite set, drop table
public enum PlayerClass { SCAVENGER, SOLDIER, MEDIC, SCOUT, ENGINEER }   // displayName, description, bonuses
public enum MapType { ... exactly 5 ... }       // displayName, description, tileset/theme
public static class GameSettings {              // launcher -> game. Plain public fields, sane defaults.
    public String playerName = "Survivor";
    public PlayerClass playerClass; public WeaponType startWeapon; public MapType map;
    public int difficulty = 3;                  // 1..5 from JSlider
    public int volume = 70;                     // 0..100 from JSlider
    public boolean autoFire, showHitbox, soundOn = true, musicOn = true, doubleSpawn;
}
public static class UpgradeRecipe { ... }       // name, description, base cost (EnumMap<ItemType,Integer>), kind, maxLevel
public static final List<UpgradeRecipe> RECIPES;   // 10-12 entries: weapon crafts + stat upgrades + consumables
public static class ScoreEntry { public String name, playerClass, map; public int score, kills, wave, seconds; }
public static List<ScoreEntry> loadScores();    // sorted desc, never throws (returns empty list on any error)
public static void saveScore(ScoreEntry e);     // appends to a text file next to the sources/classes; never throws
```
Balance numbers live ONLY in these enums (one place to tune).

### AssetLoader (all static; every loader must be crash-proof and return a visible fallback)
```java
public static BufferedImage loadImage(String relPath);            // e.g. "characters/player_run.png"
public static BufferedImage[] loadStrip(String relPath, int frameCount);
public static BufferedImage[] loadGrid(String relPath, int cellW, int cellH);  // row-major
public static BufferedImage subImage(String relPath, int x, int y, int w, int h);
public static void playSound(String name);       // short SFX, non-blocking, respects enabled flag + volume
public static void playMusic(String name);       // looping BGM;  stopMusic();
public static void setSoundEnabled(boolean b); setMusicEnabled(boolean b); setVolume(int percent0to100);
```
Lookup order for a resource `p`: (1) classpath `/Project3_XXX/resources/p`, (2) file
`src/main/java/Project3_XXX/resources/p`, (3) file `Project3_XXX/resources/p`, relative to the
working directory. Cache images. Missing image -> magenta/black checker fallback of a sensible
size + one `System.err` line; missing sound -> silently skip. Sound must never throw to callers
(no audio device, unsupported format, etc. are all swallowed with a log line).

### Entity
Exactly the fields/methods in PROJECT_STRUCTURE.md section 4 (`x, y` = world-pixel CENTER of the
entity, `width/height` = hitbox size), plus what you need (e.g. `animTimer`, `facingLeft`,
`distanceTo(Entity)`, `takeDamage(int)`). `getBounds()` returns the hitbox Rectangle in world px.

### Player (extends Entity) — methods UpgradeDialog / HUD rely on
```java
public Player(GameData.GameSettings settings, double startX, double startY);
public int getItemCount(GameData.ItemType t);
public void addItem(GameData.ItemType t, int amount);
public Map<GameData.ItemType,Integer> getCost(GameData.UpgradeRecipe r);  // real cost now (level scaling, Engineer discount)
public boolean canCraft(GameData.UpgradeRecipe r);       // affordable AND not maxed AND prerequisites met
public String getRecipeStatus(GameData.UpgradeRecipe r); // "Lv 2/5", "Owned", "MAX", "Need Blueprint" ...
public String craft(GameData.UpgradeRecipe r);           // deducts items, applies effect; returns log line, or null if it failed
public GameData.WeaponType getCurrentWeapon(); public boolean ownsWeapon(GameData.WeaponType w);
public void switchWeapon(GameData.WeaponType w);          // ignored if not owned
public List<Bullet> shoot(double targetX, double targetY); // empty list if on cooldown / no ammo
public boolean useMedkit();
// plus getters for HUD: getHp(), getMaxHp(), getArmorPercent(), getMoveSpeed(), getPickupRadius(), ...
```
Player methods touched from both the game thread and the Swing EDT (inventory, crafting) must be
`synchronized` (short blocks).

### GameMap
```java
public GameMap(GameData.MapType type, long seed);
public int getWidthPx(); public int getHeightPx();        // world size, roughly 56x36 tiles, bigger than the view
public boolean isBlocked(Rectangle worldRect);            // world edge OR solid prop footprint
public Point getPlayerSpawn();                            // inside the central camp clearing
public Point randomSpawnPoint(Random rng, Rectangle cameraView); // free tile near map edge and off-screen
public void drawGround(Graphics2D g2, Rectangle cameraView);     // pre-rendered ground image, only the visible part
public List<Prop> getProps();                             // Prop: image/animation frames, draw x,y, solid Rectangle (may be null), sortY
```

### GamePanel (extends JPanel implements Runnable, KeyListener, MouseListener, MouseMotionListener)
```java
public GamePanel(GameData.GameSettings settings, GameFrame frame);  // frame may be null (headless tests)
public void startGame(); public void pauseGame(); public void resumeGame(); public void stopGame();
public boolean isPaused(); public boolean isGameOver();
public void updateGame();            // one fixed 16 ms simulation step; public so tests can drive it
public Player getPlayer(); public int getWave(); public int getKills(); public int getScore();
public int getElapsedSeconds(); public int getTimeLimitSeconds(); public int getWaveProgressPercent();
public void setAutoFire(boolean b); public void setShowHitbox(boolean b);
```
Must work without ever being added to a frame: `new GamePanel(settings, null)`, call
`setSize(1100, 540)`, call `updateGame()` N times and `paint(g)` into a BufferedImage under
`-Djava.awt.headless=true`. Therefore: no `getGraphics()`, no dependence on real time inside
`updateGame()` (count ticks; 1 tick = 16 ms), null-check `frame`, and input state lives in plain
fields that tests may set through the normal listener methods.

### GameFrame (extends JFrame)
```java
public GameFrame(GameData.GameSettings settings, MainApplication launcher);  // launcher may be null
public void openUpgradeDialog();           // pauses panel, shows modal UpgradeDialog, resumes after
public void refreshHud();                  // called by GamePanel (via SwingUtilities.invokeLater) a few times/second
public void onGameOver(boolean survived);  // called once by GamePanel: summary dialog, saveScore, back to launcher
```
All HUD buttons/checkboxes `setFocusable(false)` so the GamePanel keeps keyboard focus.

### UpgradeDialog (extends JDialog)
`public UpgradeDialog(JFrame owner, Player player)` — modal. `refreshTable()`, `craftSelectedUpgrade()`.

### MainApplication (extends JFrame implements ActionListener, ChangeListener)
`public void onGameFinished()` — called by GameFrame when a run ends/closes: show launcher again,
reload the high-score JTable.

## 4. Game design decisions
- **View**: GamePanel is ~1100x540 (HUD above and below in GameFrame). World drawn at SCALE 2 with
  nearest-neighbour, camera centred on the player and clamped to the map. Mouse position is
  converted screen -> world for aiming.
- **Depth**: props, monsters, drops and the player are drawn sorted by their feet Y so you can walk
  "behind" a tent or car. Solid footprint of a prop = its lower part only.
- **Movement/collision**: axis-separated moves against `GameMap.isBlocked` so everything slides
  along obstacles. Monsters chase with `atan2`; when blocked they slide and get a short sideways
  nudge so they do not get stuck forever on a prop. Monsters softly push apart so the horde does
  not collapse into one sprite.
- **Weapons** (8): Knife, Axe = melee arc swing (no ammo). Pistol = infinite ammo sidearm.
  Revolver, Pump Shotgun (pellet spread), Rifle (fast), Sniper (pierces), Rocket Launcher
  (AoE explosion + screen shake) consume the shared AMMO item. Number keys switch owned weapons,
  mouse wheel cycles. Out of ammo -> auto-fallback to pistol/melee with a HUD hint.
- **Grenade**: right click (or G) throws an arcing grenade using a GRENADE item; AoE on landing.
- **Medkit**: E heals. FOOD heals a little instantly on pickup.
- **Auto-Fire**: when enabled, fires on cooldown at the nearest monster in range (falls back to
  mouse direction if none). Manual: hold left mouse to fire.
- **Monsters** (5): Normal (slow), Runner (fast, fragile), Toxic (leaves/shoots poison, green),
  Bandit (keeps distance, shoots every ~3 s, bullets hurt the player), Boss (big, lots of HP,
  drops chest + blueprint; arrives every few waves with a warning banner). Each uses its own
  run / hit / death strips. Drop tables per PROJECT_STRUCTURE.md section 1.
- **Waves**: `waveSpawnerThread` wakes every 2-3 s and requests spawns sized by wave number,
  difficulty slider and Double Spawn; wave number rises with survival time. Cap live monsters
  (e.g. 120) so the frame rate holds. Spawner must do nothing while paused/game over.
- **Win/Lose**: survive the time limit (suggest 5:00; shown as a countdown + JProgressBar) = win;
  HP 0 = death animation then lose. Score = kills/score values + time + bonus, scaled by difficulty.
- **Chest**: boss drops a chest; walking onto it plays the open animation and bursts loot.
- **Camp**: every map has a clear central camp (NPC, animated flame barrel, animated flag,
  workbench) where the player spawns. Workshop opens with B anywhere (per the design doc).
- **Maps** (5, distinct look AND layout, generated from a fixed seed per map so each map is
  always the same): e.g. Wasteland Highway (road + wrecked cars + tires), Survivor Camp (tents,
  fences, crates, stalls), Abandoned Bunker (interior floor, lockers, beds, desks, vending
  machines, wall border), Gas Station (pumps, crate stacks, barrels), Quarantine Zone (signs,
  chain fences, barrels, sheds). Ground must not look like one repeated tile: mix base + detail
  variants; border the world so the edge reads as a wall/fence, not a void.
- **Juice (required, this is what makes it feel like a real game)**: run/idle/hit/death
  animations, held weapon rotating to the aim angle with its shoot strip + muzzle flash, bullet
  impact sprites, explosion strip, floating damage/pickup numbers, hit flash + small knockback on
  monsters, corpses that linger then fade, item bobbing + magnet pull, screen shake, wave/boss
  banners, low-HP red vignette, crosshair sprite replacing the cursor, mini-map in a corner.
- **Sounds**: generate small original .wav files (16-bit PCM mono, 22050 Hz) with a throwaway
  script in the scratchpad: shoot variants, shotgun, explosion, hit, hurt, pickup, craft, click,
  game over, win, and a 15-25 s seamless looping `bgm.wav`. Whole `resources/` folder <= ~3 MB.
- **Threads**: `gameThread` (fixed 16 ms step: `updateGame()` + `repaint()`), `waveSpawnerThread`.
  Shared lists are `CopyOnWriteArrayList`. Threads are stopped (flag + interrupt + join with
  timeout) when the frame closes — no thread may survive a finished game. Every `sleep` handles
  `InterruptedException` properly.

## 5. Quality bar
- A reviewer will run headless simulations (thousands of `updateGame()` ticks with random input)
  and look at rendered frames of every map; any exception, stuck entity, invisible sprite,
  misaligned sprite sheet slice, or frame rate collapse is a reject.
- Sprite slicing must be verified by looking at the pixels, not guessed from file names. Many
  sheets are irregular (camp sheet is 367 px wide; props are not on a 32 px grid).
- Never leave TODOs, dead code, debug prints, or unused fields.

## 6. Reporting
Finish with a short report: files written (with line counts), exact public API if it deviates
from section 3 (avoid deviating; if you must, say exactly what and why), how you verified
(commands + results), and known weak spots. Raw facts, no sales pitch.

## 7. Member header (placeholders — the students will fill these in later)
Every .java file starts with exactly:
```java
// Member1 Firstname Lastname   6XXXXXX1
// Member2 Firstname Lastname   6XXXXXX2
// Member3 Firstname Lastname   6XXXXXX3
// Member4 Firstname Lastname   6XXXXXX4
// Member5 Firstname Lastname   6XXXXXX5
package Project3_XXX;
```
`GameData.GROUP_MEMBERS` holds the same five strings; `readme.txt` lists the same five lines.
