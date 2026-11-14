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
    private static final int HUD_REFRESH_TICKS = 15;              // about 4 HUD updates per second
    private static final int SPAWN_TICKS_WITHOUT_THREAD = 48;     // fast ~0.8 s cadence when tests drive updateGame()
    private static final int MAX_SPAWNS_PER_TICK = 4;             // batches enter rapidly for Hades pacing
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
    private transient GameMap map;
    private final transient Player player;
    private final transient List<Monster> monsters = new CopyOnWriteArrayList<>();
    private final transient List<Bullet> bullets = new CopyOnWriteArrayList<>();
    private final transient List<DroppedItem> drops = new CopyOnWriteArrayList<>();
    private final transient List<AllyNpc> allies = new CopyOnWriteArrayList<>();
    private final transient List<HazardTrap> traps = new CopyOnWriteArrayList<>();
    private final transient List<ExitDoor> exitDoors = new CopyOnWriteArrayList<>();
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
    private volatile int elapsedTicks;         // active chamber run time
    private volatile int wave = 1;
    private volatile int chamber;              // 0 = Safe Camp Hub, 1..15 = Act 1-3, 16+ = Endless
    private volatile int highestChamberReached;
    private volatile int runNumber = 1;
    private long runSeed = 1001L;
    private volatile GameData.ChamberType currentChamberType = GameData.ChamberType.SAFE_CAMP;
    private volatile int chamberMonstersTotal;
    private volatile int chamberMonstersRemaining;
    private volatile boolean chamberCleared = true;
    private volatile boolean pendingPerkSelection;
    private volatile int kills;
    private volatile int killScore;
    private int gameOverTimer;
    private boolean raidActive;
    private float nightAlpha;                  // atmospheric darkness by Act / Boss depth
    private transient BufferedImage nightMask; // world-scale darkness layer punched with light pools
    private int idlePlayerTicks;
    private double lastPlayerX, lastPlayerY;
    private transient volatile Banner banner;

    // ------------------------------------------------------------------ input (set on the Swing thread, read by the game thread)
    private volatile boolean keyUp, keyDown, keyLeft, keyRight;
    private volatile boolean mouseFiring;
    private volatile boolean mouseInside;
    private volatile int mouseX = DEFAULT_VIEW_W / 2 + 80, mouseY = DEFAULT_VIEW_H / 2;
    private volatile boolean grenadeRequested, medkitRequested, interactRequested, dashRequested;
    private volatile int weaponSlotRequested = -1;
    private volatile int weaponCycleRequested;
    private int lastWheelTick = -100;          // Swing thread only
    private volatile boolean autoFire, showHitbox;

    public static final class FieldShopOffer {
        public enum OfferKind { HEAL, TEMP_WEAPON, PERK }
        public final OfferKind kind;
        public final String title;
        public final String detail;
        public final int costScraps;
        public final int healAmount;
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

    private final transient List<FieldShopOffer> fieldShopOffers = new ArrayList<>();

    // ------------------------------------------------------------------ camera and screen shake (world pixels)
    private volatile double camX, camY;
    private volatile double shakeX, shakeY;
    private int shakeTicks;
    private double shakePower;
    private int lastPickupTick, pickupStack;   // stacking of pickup labels

    public List<FieldShopOffer> getFieldShopOffers() {
        return new ArrayList<>(fieldShopOffers);
    }

    private int scaleMerchantCost(int rawCost) {
        return Math.max(3, (int) Math.round(rawCost * player.getPlayerClass().costMult));
    }

    private void rollFieldShopOffers(int ch) {
        fieldShopOffers.clear();
        if (ch <= 0) {
            return;
        }
        // Offer 1: field healing tonic (randomized strength & price)
        int healRoll = rng.nextInt(3);
        String healName = healRoll == 0 ? "Combat Stimulant" : (healRoll == 1 ? "Field Blood Bag" : "Chthonic Elixir");
        int healAmt = healRoll == 0 ? 18 : (healRoll == 1 ? 26 : 36);
        int healCost = scaleMerchantCost((healRoll == 0 ? 3 : (healRoll == 1 ? 5 : 7)) + ch / 2);
        fieldShopOffers.add(new FieldShopOffer(
                FieldShopOffer.OfferKind.HEAL,
                healName,
                "Instant +" + healAmt + " HP recovery for this run",
                healCost, healAmt, null, null));

        // Offer 2: temporary 1-chamber weapon rental (+30 bonus ammo, vanishes next chamber)
        GameData.WeaponType[] pool = ch <= 3
                ? new GameData.WeaponType[]{
                        GameData.WeaponType.PISTOL, GameData.WeaponType.REVOLVER,
                        GameData.WeaponType.SHOTGUN, GameData.WeaponType.RIFLE}
                : new GameData.WeaponType[]{
                        GameData.WeaponType.REVOLVER, GameData.WeaponType.SHOTGUN,
                        GameData.WeaponType.RIFLE, GameData.WeaponType.SNIPER,
                        GameData.WeaponType.ROCKET_LAUNCHER};
        GameData.WeaponType primary = player.getEquippedPrimaryGun();
        GameData.WeaponType pickWeapon = pool[rng.nextInt(pool.length)];
        for (int tries = 0; tries < 6 && pickWeapon == primary; tries++) {
            pickWeapon = pool[rng.nextInt(pool.length)];
        }
        int tierCost = 4 + pickWeapon.ordinal() + ch / 2;
        fieldShopOffers.add(new FieldShopOffer(
                FieldShopOffer.OfferKind.TEMP_WEAPON,
                pickWeapon.displayName + " (1-Chamber)",
                "Temporary gun +30 Ammo for Chamber " + ch + " only (DMG " + pickWeapon.damage + ")",
                scaleMerchantCost(tierCost), 0, pickWeapon, null));

        // Offer 3: random tactical perk
        GameData.TacticalPerk[] perks = GameData.TacticalPerk.values();
        GameData.TacticalPerk pickPerk = perks[rng.nextInt(perks.length)];
        fieldShopOffers.add(new FieldShopOffer(
                FieldShopOffer.OfferKind.PERK,
                pickPerk.displayName,
                pickPerk.description,
                scaleMerchantCost(6 + ch), 0, null, pickPerk));

        for (int i = fieldShopOffers.size() - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            FieldShopOffer tmp = fieldShopOffers.get(i);
            fieldShopOffers.set(i, fieldShopOffers.get(j));
            fieldShopOffers.set(j, tmp);
        }
    }

    public String buyFieldShopOffer(int index) {
        if (chamber <= 0) {
            return "Field Merchant only operates inside Chambers.";
        }
        if (index < 0 || index >= fieldShopOffers.size()) {
            return "Invalid offer.";
        }
        FieldShopOffer offer = fieldShopOffers.get(index);
        if (offer.sold) {
            return offer.title + " is already sold out.";
        }
        int scraps = player.getItemCount(GameData.ItemType.SCRAPS);
        if (scraps < offer.costScraps) {
            return "Need " + offer.costScraps + " Scraps (you have " + scraps + ").";
        }
        if (offer.kind == FieldShopOffer.OfferKind.HEAL && player.getHp() >= player.getMaxHp()) {
            return "Health is already full.";
        }
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

    public int getShakeTicks() {
        return shakeTicks;
    }

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

    /** Environmental chamber traps (damage both player and monsters). */
    private static final class HazardTrap implements GameMap.Drawable {
        enum TrapKind { SPIKE, TOXIC, SHOCK, MINE, FLAME_JET }

        final TrapKind kind;
        final double x, y;
        int timer;
        int armTicks = -1;     // landmine fuse countdown
        int hitCooldown;
        boolean triggered;     // for MINE once detonated

        HazardTrap(TrapKind kind, double x, double y, int phaseOffset) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.timer = Math.floorMod(phaseOffset, 180);
        }

        void update() {
            timer++;
            if (hitCooldown > 0) {
                hitCooldown--;
            }
            if (armTicks > 0) {
                armTicks--;
            }
        }

        boolean isTriggeringDamage() {
            if (triggered) {
                return false;
            }
            if (kind == TrapKind.SPIKE) {
                int m = timer % 105;
                return m >= 68 && m <= 100;
            }
            if (kind == TrapKind.SHOCK) {
                int m = timer % 110;
                return m >= 72 && m <= 106;
            }
            if (kind == TrapKind.FLAME_JET) {
                int m = timer % 100;
                return m >= 68 && m <= 96;
            }
            if (kind == TrapKind.TOXIC) {
                return timer % 20 == 0;
            }
            return false;
        }

        boolean isWarningPhase() {
            if (kind == TrapKind.SPIKE) {
                int m = timer % 105;
                return m >= 42 && m < 68;
            }
            if (kind == TrapKind.SHOCK) {
                int m = timer % 110;
                return m >= 46 && m < 72;
            }
            if (kind == TrapKind.FLAME_JET) {
                int m = timer % 100;
                return m >= 44 && m < 68;
            }
            return kind == TrapKind.MINE && armTicks > 0;
        }

        @Override
        public double getSortY() {
            return y - 10;
        }

        @Override
        public void draw(Graphics2D g2) {
            if (triggered) {
                return;
            }
            int ix = (int) Math.round(x), iy = (int) Math.round(y);
            boolean active = isTriggeringDamage();
            boolean warn = isWarningPhase();
            Stroke oldStroke = g2.getStroke();
            if (kind == TrapKind.SPIKE) {
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
            } else if (kind == TrapKind.TOXIC) {
                g2.setColor(new Color(48, 128, 28, 175));
                g2.fillOval(ix - 20, iy - 13, 40, 26);
                g2.setColor(new Color(95, 195, 45, 130));
                g2.fillOval(ix - 12, iy - 8, 24, 15);
                g2.setColor(active ? new Color(185, 255, 85, 235) : new Color(135, 225, 55, 190));
                g2.drawOval(ix - 20, iy - 13, 40, 26);
                int b1 = (timer / 4) % 10;
                int b2 = ((timer + 9) / 5) % 9;
                g2.fillOval(ix - 7 + (timer / 11) % 7, iy + 2 - b1, 5, 5);
                g2.fillOval(ix + 5 - (timer / 13) % 6, iy + 3 - b2, 4, 4);
            } else if (kind == TrapKind.SHOCK) {
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
            } else if (kind == TrapKind.MINE) {
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
            } else if (kind == TrapKind.FLAME_JET) {
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
            g2.setStroke(oldStroke);
        }
    }

    /** Hades-style Exit Portal / Gate leading to the next chosen chamber or Safe Camp Hub. */
    private static final class ExitDoor implements GameMap.Drawable {
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
            switch (type) {
                case SAFE_CAMP:
                case VICTORY_EXTRACT:
                    return new Color(110, 225, 115);
                case TACTICAL_PERK:
                    return new Color(90, 210, 255);
                case SALVAGE_VAULT:
                    return Theme.GOLD;
                case FIELD_MEDIC:
                    return new Color(130, 230, 120);
                case ELITE_HAZARD:
                    return new Color(240, 110, 60);
                case ENDLESS_ABYSS:
                    return new Color(195, 125, 255);
                default:
                    return Theme.RED;
            }
        }

        @Override
        public double getSortY() {
            return y + 10;
        }

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
            Theme.drawPixelTextShadow(g2, type.displayName.toUpperCase(),
                    ix - Theme.pixelWidth(type.displayName, 1) / 2, iy - 16, 1, c);
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
                        int overdrive = player.getPerkLevel(GameData.TacticalPerk.SQUAD_OVERDRIVE);
                        GameData.WeaponType w = GameData.WeaponType.RIFLE;
                        int npcDmg = (int) Math.round((12 + squadLv * 4) * (1.0 + 0.35 * overdrive));
                        Bullet b = Bullet.shot(x + Math.cos(aimAngle) * 10, y + Math.sin(aimAngle) * 10,
                                aimAngle, w, npcDmg);
                        b.setMap(map);
                        bullets.add(b);
                        addEffect(new Effect(Effect.Kind.IMPACT,
                                x + Math.cos(aimAngle) * 12, y + Math.sin(aimAngle) * 12, 10, null, null, 12));
                        double cdMult = Math.max(0.45, 1.0 - 0.25 * overdrive);
                        fireCooldown = Math.max(9, (int) Math.round(Math.max(14, 26 - squadLv * 2) * cdMult));
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
        runSeed = mapType.seed + System.currentTimeMillis() % 100000L;
        map = new GameMap(mapType, mapType.seed, true);
        solidRects = new ArrayList<>(map.getSolidRects());
        Point spawn = map.getPlayerSpawn();
        player = new Player(this.settings, spawn.x, spawn.y);
        player.setMap(map);
        player.pushOutOfWalls();
        player.prepareForSafeCamp(false);
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
        preloadSprites();
        if (frame == null) {
            enterChamber(1, GameData.ChamberType.SALVAGE_VAULT);
        } else {
            initSafeCamp(false);
        }
    }

    /** Enters or returns to the Safe Camp Hub (chamber 0). Keeps 100% of permanent salvage materials & unlocked gear. */
    private void initSafeCamp(boolean returningFromRun) {
        GameData.MapType hubType = settings.map == null ? GameData.MapType.WASTELAND_HIGHWAY : settings.map;
        chamber = 0;
        currentChamberType = GameData.ChamberType.SAFE_CAMP;
        chamberCleared = true;
        chamberMonstersTotal = 0;
        chamberMonstersRemaining = 0;
        pendingSpawns.set(0);
        pendingPerkSelection = false;
        fieldShopOffers.clear();
        raidActive = false;
        nightAlpha = 0f;

        map = new GameMap(hubType, hubType.seed, true);
        solidRects.clear();
        solidRects.addAll(map.getSolidRects());
        Point spawn = map.getPlayerSpawn();
        player.setMap(map);
        player.setPosition(spawn.x, spawn.y);
        player.pushOutOfWalls();
        player.prepareForSafeCamp(false);

        monsters.clear();
        bullets.clear();
        drops.clear();
        traps.clear();
        exitDoors.clear();
        effects.clear();
        allies.clear();
        int allyLv = player.getLevel(GameData.RecipeKind.BASE_TURRET);
        for (int i = 0; i < Math.min(4, allyLv); i++) {
            allies.add(new AllyNpc(i, spawn.x - 24 + i * 16, spawn.y + 12));
        }

        Point gate = map.getGatePosition();
        exitDoors.add(new ExitDoor(GameData.ChamberType.TACTICAL_PERK, 1, gate.x, gate.y,
                "ENTER CHAMBER 1 (RUN #" + runNumber + ")"));
        lastPlayerX = player.getX();
        lastPlayerY = player.getY();
        updateCamera(1.0);
        showBanner("SAFE CAMP HUB - RUN #" + runNumber,
                returningFromRun ? "All permanent salvage saved! Upgrade at NPC [B] then press [F] at Gate"
                        : "Talk to Camp NPC [B], equip gear, then press [F] at the Gate below",
                BANNER_WAVE);
    }

    /** Public hook called on Swing thread after the death/victory summary returns the player to camp. */
    public void returnToSafeCamp() {
        synchronized (worldLock) {
            runNumber++;
            kills = 0;
            killScore = 0;
            elapsedTicks = 0;
            runSeed += 7919L;
            gameOver = false;
            won = false;
            paused = false;
            initSafeCamp(true);
            AssetLoader.playMusic("bgm");
        }
        requestHudRefresh();
        requestFocusInWindow();
    }

    private GameData.MapType biomeForChamber(int c) {
        if (c <= 5) {
            return (c % 2 == 1) ? GameData.MapType.WASTELAND_HIGHWAY : GameData.MapType.GAS_STATION;
        }
        if (c <= 10) {
            return (c % 2 == 0) ? GameData.MapType.QUARANTINE_ZONE : GameData.MapType.SURVIVOR_CAMP;
        }
        if (c <= 15) {
            return GameData.MapType.ABANDONED_BUNKER;
        }
        GameData.MapType[] all = GameData.MapType.values();
        return all[(c - 1) % all.length];
    }

    public String getActLabel(int c) {
        if (c <= 0) {
            return "SAFE CAMP HUB";
        }
        if (c <= 5) {
            return "ACT 1: OUTSKIRTS";
        }
        if (c <= 10) {
            return "ACT 2: QUARANTINE";
        }
        if (c <= 15) {
            return "ACT 3: BUNKER";
        }
        return "ENDLESS ABYSS";
    }

    /** Generates and loads a procedural combat chamber with traps, obstacles, and wave quota. */
    private void enterChamber(int nextChamber, GameData.ChamberType type) {
        if (nextChamber == 1 && chamber == 0) {
            elapsedTicks = 0;
            kills = 0;
            killScore = 0;
            runSeed += 313L;
        }
        chamber = nextChamber;
        wave = nextChamber;
        highestChamberReached = Math.max(highestChamberReached, chamber);
        currentChamberType = type;
        chamberCleared = false;
        pendingPerkSelection = false;
        pendingSpawns.set(0);
        raidActive = (type == GameData.ChamberType.BOSS_CHAMBER || type == GameData.ChamberType.ELITE_HAZARD);
        nightAlpha = chamber >= 11 ? 0.42f : (chamber >= 6 ? 0.24f : (type == GameData.ChamberType.BOSS_CHAMBER ? 0.35f : 0f));

        GameData.MapType biome = biomeForChamber(chamber);
        map = new GameMap(biome, runSeed + chamber * 7919L + runNumber * 131L, false);
        solidRects.clear();
        solidRects.addAll(map.getSolidRects());

        Point spawn = map.getPlayerSpawn();
        player.setMap(map);
        player.setPosition(spawn.x, spawn.y);
        player.pushOutOfWalls();
        player.clearTempChamberWeapon();
        rollFieldShopOffers(chamber);
        if (!player.canUseWeaponInRun(player.getCurrentWeapon(), false)) {
            GameData.WeaponType gun = player.getEquippedPrimaryGun();
            player.switchWeapon(gun != null ? gun : GameData.WeaponType.KNIFE);
        }

        monsters.clear();
        bullets.clear();
        drops.clear();
        traps.clear();
        exitDoors.clear();
        effects.clear();

        allies.clear();
        int allyLv = player.getLevel(GameData.RecipeKind.BASE_TURRET);
        for (int i = 0; i < Math.min(4, allyLv); i++) {
            allies.add(new AllyNpc(i, spawn.x - 22 + i * 14, spawn.y + 12));
        }

        spawnTrapsForChamber(chamber, type, spawn);

        if (type == GameData.ChamberType.SALVAGE_VAULT) {
            Point cp = map.randomOuterLootPoint(rng);
            DroppedItem chest = new DroppedItem(GameData.ItemType.CHEST, 1, cp.x, cp.y, player);
            chest.setMap(map);
            drops.add(chest);
        }

        boolean bossChamber = (type == GameData.ChamberType.BOSS_CHAMBER)
                || (chamber % GameData.BOSS_EVERY_CHAMBERS == 0);
        chamberMonstersTotal = GameData.chamberQuota(chamber, type, getDifficulty(), settings.doubleSpawn);
        int initialBatch = Math.min(chamberMonstersTotal, 3 + chamber / 2);
        chamberMonstersRemaining = chamberMonstersTotal - initialBatch;
        for (int i = 0; i < initialBatch; i++) {
            spawnMonster(GameData.MonsterType.randomForWave(chamber, rng));
        }
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

    private void spawnTrapsForChamber(int c, GameData.ChamberType kind, Point spawn) {
        int targetCount = Math.min(28, 4 + c * 3 / 2 + (kind == GameData.ChamberType.ELITE_HAZARD ? 5 : 0));
        HazardTrap.TrapKind[] allKinds = HazardTrap.TrapKind.values();
        Point gate = map.getGatePosition();
        double dx = gate.x - spawn.x;
        double dy = gate.y - spawn.y;
        double len = Math.hypot(dx, dy);
        double fx = len >= 40.0 ? dx / len : 1.0;
        double fy = len >= 40.0 ? dy / len : 0.0;
        double px = -fy;
        double py = fx;

        // 1. Tactical gauntlet lines across the main advance axis from entrance to exit gate
        double[] lineFractions = targetCount >= 18
                ? new double[]{0.30, 0.54, 0.77}
                : new double[]{0.34, 0.64};
        HazardTrap.TrapKind[] linePool = {
                HazardTrap.TrapKind.SPIKE, HazardTrap.TrapKind.FLAME_JET,
                HazardTrap.TrapKind.SHOCK, HazardTrap.TrapKind.TOXIC
        };
        for (int g = 0; g < lineFractions.length && traps.size() < targetCount; g++) {
            double cx = spawn.x + dx * lineFractions[g];
            double cy = spawn.y + dy * lineFractions[g];
            HazardTrap.TrapKind lineKind = linePool[(c + g + rng.nextInt(linePool.length)) % linePool.length];
            int halfSpan = (g == 1 && targetCount >= 18) ? 2 : 1;
            int step = 0;
            for (int k = -halfSpan; k <= halfSpan && traps.size() < targetCount; k++) {
                double tx = cx + px * (k * 32.0) + fx * ((step % 2 == 0) ? 0.0 : 8.0);
                double ty = cy + py * (k * 32.0) + fy * ((step % 2 == 0) ? 0.0 : 8.0);
                addTrapIfValid(lineKind, tx, ty, step * 16 + g * 23, spawn, 24.0);
                step++;
            }
        }

        // 2. Chain-detonating minefield clusters (spaced <= 65 px so blast chains trigger)
        int mineClusters = (c >= 4 || kind == GameData.ChamberType.ELITE_HAZARD) ? 2 : 1;
        for (int cl = 0; cl < mineClusters && traps.size() + 2 <= targetCount; cl++) {
            Point center = map.randomWalkablePoint(rng, spawn.x, spawn.y, 135.0);
            int placed = 0;
            double baseAngle = rng.nextDouble() * Math.PI * 2.0;
            for (int m = 0; m < 3 && traps.size() < targetCount; m++) {
                double ang = baseAngle + m * (Math.PI * 2.0 / 3.0);
                double r = 24.0 + rng.nextInt(8);
                if (addTrapIfValid(HazardTrap.TrapKind.MINE,
                        center.x + Math.cos(ang) * r, center.y + Math.sin(ang) * r,
                        m * 9, spawn, 26.0)) {
                    placed++;
                }
            }
            if (placed == 0) {
                addTrapIfValid(HazardTrap.TrapKind.MINE, center.x, center.y, cl * 11, spawn, 26.0);
            }
        }

        // 3. Ensure every trap archetype gets representation & fill chokepoint pairs
        for (HazardTrap.TrapKind needed : allKinds) {
            if (traps.size() >= targetCount) {
                break;
            }
            boolean present = false;
            for (HazardTrap existing : traps) {
                if (existing.kind == needed) {
                    present = true;
                    break;
                }
            }
            if (!present) {
                for (int tries = 0; tries < 10; tries++) {
                    Point p = map.randomWalkablePoint(rng, spawn.x, spawn.y, 110.0);
                    if (addTrapIfValid(needed, p.x, p.y, tries * 19, spawn, 28.0)) {
                        break;
                    }
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
                boolean horizontal = rng.nextBoolean();
                double sx = p.x + (horizontal ? 34.0 : 0.0);
                double sy = p.y + (horizontal ? 0.0 : 34.0);
                addTrapIfValid(k, sx, sy, phase + 18, spawn, 24.0);
            }
        }
    }

    private boolean addTrapIfValid(HazardTrap.TrapKind kind, double x, double y, int phase,
            Point spawn, double minTrapGap) {
        if (x < 44 || y < 44 || x > map.getWidthPx() - 44 || y > map.getHeightPx() - 44) {
            return false;
        }
        if (Math.hypot(x - spawn.x, y - spawn.y) < 88.0) {
            return false;
        }
        Rectangle probe = new Rectangle((int) Math.round(x) - 10, (int) Math.round(y) - 8, 20, 16);
        if (map.isBlocked(probe)) {
            return false;
        }
        for (HazardTrap existing : traps) {
            if (Math.hypot(x - existing.x, y - existing.y) < minTrapGap) {
                return false;
            }
        }
        traps.add(new HazardTrap(kind, x, y, phase));
        return true;
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

    /** The second thread: wakes periodically and asks for the next reinforcement batch in combat chambers. */
    private void runWaveSpawner() {
        Random timing = new Random();
        while (running) {
            try {
                int baseDelay = Math.max(700, 1400 - Math.max(0, chamber) * 50);
                int delay = (raidActive ? (baseDelay * 3 / 4) : baseDelay) + timing.nextInt(351);
                Thread.sleep(delay);
            } catch (InterruptedException ex) {
                return;   // stopGame() interrupts us
            }
            if (!paused && !gameOver && player.isAlive()
                    && chamber > 0 && !chamberCleared && chamberMonstersRemaining > 0) {
                requestSpawns();
            }
        }
    }

    /**
     * Only adds to a counter: the game thread creates the monsters itself, so the spawner never
     * touches the world while it is being updated or drawn.
     */
    private void requestSpawns() {
        if (chamber <= 0 || chamberCleared || chamberMonstersRemaining <= 0) {
            return;
        }
        int batch = Math.min(chamberMonstersRemaining,
                GameData.spawnBatchSize(chamber, getDifficulty(), settings.doubleSpawn));
        pendingSpawns.addAndGet(batch);
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
            updateChamberClock();
            if (gameOver) {
                return;
            }
            if (!running && tickCount % SPAWN_TICKS_WITHOUT_THREAD == 0 && player.isAlive()
                    && chamber > 0 && !chamberCleared && chamberMonstersRemaining > 0) {
                requestSpawns();   // no spawner thread (tests): driven by ticks
            }
            spawnPendingMonsters();
            updatePlayer();
            updateBaseSystems();
            updateTraps();
            map.update();
            updateMonsters();
            updateBullets();
            updateDrops();
            updateChamberProgression();
            updateEffects();
            updateCamera(0.28);
            if (!player.isAlive() && player.isDeathAnimationDone()) {
                finishGame(false);
            }
        }
    }

    /** Tracks elapsed run time inside combat chambers (Safe Camp pauses the expedition clock). */
    private void updateChamberClock() {
        if (!player.isAlive() || chamber <= 0 || chamberCleared) {
            return;
        }
        elapsedTicks++;
        wave = chamber;
    }

    /** Updates environmental hazards; active traps injure both survivors (unless dashing) and monsters. */
    private void updateTraps() {
        if (chamber <= 0 || traps.isEmpty()) {
            return;
        }
        int trapMasterLv = player.getPerkLevel(GameData.TacticalPerk.TRAP_MASTER);
        double monsterMult = 1.0 + 0.85 * trapMasterLv;
        for (HazardTrap t : traps) {
            t.update();
            if (chamberCleared || t.triggered) {
                continue;
            }
            if (t.kind == HazardTrap.TrapKind.MINE) {
                if (t.armTicks == 0) {
                    detonateMine(t);
                    continue;
                }
                if (t.armTicks < 0 && hasEntityNear(t.x, t.y, 24.0)) {
                    t.armTicks = 18;
                    addText(t.x, t.y - 12, "ARMED!", Theme.RED);
                }
                continue;
            }
            if (!t.isTriggeringDamage()) {
                continue;
            }
            double pRad = (t.kind == HazardTrap.TrapKind.TOXIC) ? 20.0
                    : (t.kind == HazardTrap.TrapKind.SHOCK ? 19.0 : 18.0);
            if (player.isAlive() && !player.isDashing() && player.distanceTo(t.x, t.y) < pRad) {
                int baseDmg;
                if (t.kind == HazardTrap.TrapKind.TOXIC) {
                    baseDmg = 9 + chamber / 2;
                } else if (t.kind == HazardTrap.TrapKind.SHOCK) {
                    baseDmg = 20 + chamber;
                } else if (t.kind == HazardTrap.TrapKind.FLAME_JET) {
                    baseDmg = 22 + chamber;
                } else {
                    baseDmg = 18 + chamber;
                }
                boolean wasSlowed = player.isTrapSlowed();
                if (hurtPlayerTrap(baseDmg) > 0) {
                    if (t.kind == HazardTrap.TrapKind.TOXIC) {
                        player.applyPoison(110);
                        player.applyTrapSlow(50);
                    } else if (t.kind == HazardTrap.TrapKind.SHOCK) {
                        player.applyTrapSlow(85);
                    } else if (t.kind == HazardTrap.TrapKind.SPIKE) {
                        player.applyTrapSlow(65);
                    } else if (t.kind == HazardTrap.TrapKind.FLAME_JET) {
                        player.applyTrapSlow(30);
                    }
                    if (!wasSlowed && player.isTrapSlowed()) {
                        addText(player.getX(), player.getY() - 32, "SLOWED!", Theme.GOLD);
                    }
                }
            }
            if (t.hitCooldown <= 0) {
                boolean hitAny = false;
                int baseMonDmg;
                double hitRad = 22.0;
                if (t.kind == HazardTrap.TrapKind.SHOCK) {
                    baseMonDmg = 24;
                } else if (t.kind == HazardTrap.TrapKind.FLAME_JET) {
                    baseMonDmg = 26;
                    hitRad = 24.0;
                } else if (t.kind == HazardTrap.TrapKind.SPIKE) {
                    baseMonDmg = 22;
                } else {
                    baseMonDmg = 12;
                    hitRad = 24.0;
                }
                int dmg = (int) Math.round(baseMonDmg * monsterMult);
                for (Monster m : monsters) {
                    if (!m.isAlive() || m.distanceTo(t.x, t.y) > hitRad) {
                        continue;
                    }
                    hitAny = true;
                    boolean killed = m.takeHit(dmg, Math.atan2(m.getY() - t.y, m.getX() - t.x), 3.0);
                    if (t.kind == HazardTrap.TrapKind.SHOCK) {
                        m.applySlow(85);
                    } else if (t.kind == HazardTrap.TrapKind.TOXIC) {
                        m.applySlow(60);
                    }
                    addText(m.getX(), m.getY() - m.getHeight(), String.valueOf(dmg), killed ? TEXT_KILL : TEXT_DAMAGE);
                    if (killed) {
                        onMonsterKilled(m);
                    }
                }
                if (hitAny) {
                    t.hitCooldown = 20;
                }
            }
        }
    }

    private boolean hasEntityNear(double tx, double ty, double radius) {
        if (player.isAlive() && !player.isDashing() && player.distanceTo(tx, ty) < radius) {
            return true;
        }
        for (Monster m : monsters) {
            if (m.isAlive() && m.distanceTo(tx, ty) < radius) {
                return true;
            }
        }
        return false;
    }

    private void detonateMine(HazardTrap mine) {
        if (mine.triggered) {
            return;
        }
        mine.triggered = true;
        addEffect(new Effect(Effect.Kind.EXPLOSION, mine.x, mine.y, 26, null, null, 86));
        AssetLoader.playSound("explosion");
        shake(5.5, 16);
        int trapMasterLv = player.getPerkLevel(GameData.TacticalPerk.TRAP_MASTER);
        int monsterDmg = (int) Math.round(56 * (1.0 + 0.85 * trapMasterLv));
        for (Monster m : monsters) {
            if (!m.isAlive() || m.distanceTo(mine.x, mine.y) > 60.0) {
                continue;
            }
            boolean killed = m.takeHit(monsterDmg, Math.atan2(m.getY() - mine.y, m.getX() - mine.x), 6.0);
            addText(m.getX(), m.getY() - m.getHeight(), String.valueOf(monsterDmg), killed ? TEXT_KILL : TEXT_DAMAGE);
            if (killed) {
                onMonsterKilled(m);
            }
        }
        if (player.isAlive() && !player.isDashing() && player.distanceTo(mine.x, mine.y) < 52.0) {
            if (hurtPlayerTrap(32 + chamber) > 0) {
                player.applyTrapSlow(45);
            }
        }
        for (HazardTrap other : traps) {
            if (!other.triggered && other != mine && other.kind == HazardTrap.TrapKind.MINE
                    && Math.hypot(other.x - mine.x, other.y - mine.y) <= 65.0) {
                other.armTicks = other.armTicks < 0 ? 8 : Math.min(other.armTicks, 8);
            }
        }
    }

    /** Checks whether all queued and live monsters in the chamber are defeated. */
    private void updateChamberProgression() {
        if (chamber <= 0 || chamberCleared || !player.isAlive()) {
            return;
        }
        if (chamberMonstersRemaining <= 0 && getLiveMonsterCount() == 0) {
            onChamberCleared();
        }
    }

    private void onChamberCleared() {
        chamberCleared = true;
        pendingSpawns.set(0);
        AssetLoader.playSound("win");

        // Hades-style Chthonic Vitality clear healing (+3 HP per upgrade level, +9 at Vitality Shrine)
        int healAmount = player.getChamberClearHeal()
                + (currentChamberType == GameData.ChamberType.FIELD_MEDIC ? 9 : 0);
        if (healAmount > 0) {
            int before = player.getHp();
            player.heal(healAmount);
            if (currentChamberType == GameData.ChamberType.FIELD_MEDIC) {
                player.curePoison();
            }
            int gained = player.getHp() - before;
            if (gained > 0) {
                addText(player.getX(), player.getY() - 26, "+" + gained + " HP CLEAR", TEXT_LOOT);
            }
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
                SwingUtilities.invokeLater(() -> {
                    frame.showPerkSelectionDialog(choices);
                });
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
        if (currentChamberType == GameData.ChamberType.SALVAGE_VAULT) {
            spawnBonusItem(GameData.ItemType.STEEL, 1, cx);
            spawnBonusItem(GameData.ItemType.SCRAPS, 1 + chamber / 5, cx);
            if (rng.nextBoolean()) {
                spawnBonusItem(GameData.ItemType.ELECTRONICS, 1, cx);
            }
        } else if (currentChamberType == GameData.ChamberType.ELITE_HAZARD) {
            spawnBonusItem(GameData.ItemType.STEEL, 1, cx);
            spawnBonusItem(GameData.ItemType.SCRAPS, 1, cx);
            if (rng.nextInt(100) < 35) {
                spawnBonusItem(GameData.ItemType.BATTERY, 1, cx);
            }
        } else if (currentChamberType == GameData.ChamberType.FIELD_MEDIC) {
            spawnBonusItem(GameData.ItemType.SCRAPS, 2, cx);
            spawnBonusItem(GameData.ItemType.AMMO, 8 + chamber, cx);
        } else if (currentChamberType == GameData.ChamberType.BOSS_CHAMBER) {
            spawnBonusItem(GameData.ItemType.AMMO, 8 + chamber, cx);
            spawnBonusItem(GameData.ItemType.SCRAPS, 2, cx);
        } else {
            spawnBonusItem(GameData.ItemType.AMMO, 5 + chamber / 2, cx);
            if (rng.nextInt(100) < 35) {
                spawnBonusItem(GameData.ItemType.SCRAPS, 1, cx);
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
            if (player.getPerkLevel(p) < 3) {
                pool.add(p);
            }
        }
        for (int i = pool.size() - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            GameData.TacticalPerk tmp = pool.get(i);
            pool.set(i, pool.get(j));
            pool.set(j, tmp);
        }
        return pool.subList(0, Math.min(count, pool.size()));
    }

    /** Applies the perk selected in GameFrame's Tactical Perk modal and clears the pending flag. */
    public void applyChosenPerk(GameData.TacticalPerk perk) {
        synchronized (worldLock) {
            if (perk != null) {
                player.addPerk(perk);
                addText(player.getX(), player.getY() - 28,
                        perk.displayName + " Lv." + player.getPerkLevel(perk), Theme.GOLD);
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
            Point p1 = findDoorSpotAround(gate.x, gate.y, -48, -28);
            Point p2 = findDoorSpotAround(gate.x, gate.y, 48, -28);
            exitDoors.add(new ExitDoor(GameData.ChamberType.VICTORY_EXTRACT, 0, p1.x, p1.y,
                    "EXTRACT TO CAMP (VICTORY)"));
            exitDoors.add(new ExitDoor(GameData.ChamberType.ENDLESS_ABYSS, 16, p2.x, p2.y,
                    "ENTER ENDLESS ABYSS (CH 16)"));
            return;
        }
        int next = chamber + 1;
        if (next % GameData.BOSS_EVERY_CHAMBERS == 0) {
            Point p = findDoorSpotAround(gate.x, gate.y, 0, 0);
            exitDoors.add(new ExitDoor(GameData.ChamberType.BOSS_CHAMBER, next, p.x, p.y,
                    "ENTER BOSS LAIR (CH " + next + ")"));
            if (chamber > 15) {
                Point ext = findDoorSpotAround(gate.x, gate.y, 56, 20);
                exitDoors.add(new ExitDoor(GameData.ChamberType.VICTORY_EXTRACT, 0, ext.x, ext.y,
                        "EXTRACT TO SAFE CAMP"));
            }
            return;
        }
        List<GameData.ChamberType> options = new ArrayList<>();
        options.add(GameData.ChamberType.TACTICAL_PERK);
        options.add(GameData.ChamberType.SALVAGE_VAULT);
        options.add(GameData.ChamberType.FIELD_MEDIC);
        if (next >= 3) {
            options.add(GameData.ChamberType.ELITE_HAZARD);
        }
        for (int i = options.size() - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            GameData.ChamberType tmp = options.get(i);
            options.set(i, options.get(j));
            options.set(j, tmp);
        }
        int doorCount = (chamber % 3 == 0) ? Math.min(3, options.size()) : 2;
        int[][] offsets = doorCount == 3
                ? new int[][]{{-64, -24}, {0, 26}, {64, -24}}
                : new int[][]{{-44, -18}, {44, 18}};
        for (int i = 0; i < doorCount; i++) {
            GameData.ChamberType ct = options.get(i);
            Point spot = findDoorSpotAround(gate.x, gate.y, offsets[i][0], offsets[i][1]);
            exitDoors.add(new ExitDoor(ct, next, spot.x, spot.y,
                    ct.displayName.toUpperCase() + " (CH " + next + ")"));
        }
    }

    private Point findDoorSpotAround(int anchorX, int anchorY, int dx, int dy) {
        double[] scales = {1.0, 0.7, 1.35, -0.7};
        for (double s : scales) {
            int cx = (int) Math.round(anchorX + dx * s);
            int cy = (int) Math.round(anchorY + dy * s);
            if (isValidDoorSpot(cx, cy)) {
                return new Point(cx, cy);
            }
        }
        for (int rad = 38; rad <= 66; rad += 24) {
            for (int step = 0; step < 16; step++) {
                double ang = step * (Math.PI / 8.0);
                int cx = (int) Math.round(anchorX + Math.cos(ang) * rad);
                int cy = (int) Math.round(anchorY + Math.sin(ang) * rad);
                if (isValidDoorSpot(cx, cy)) {
                    return new Point(cx, cy);
                }
            }
        }
        return map.randomWalkablePoint(rng, anchorX, anchorY, 24.0);
    }

    private boolean isValidDoorSpot(int px, int py) {
        if (px < 40 || py < 40 || px > map.getWidthPx() - 40 || py > map.getHeightPx() - 40) {
            return false;
        }
        Rectangle probe = new Rectangle(px - 14, py - 8, 28, 16);
        if (map.isBlocked(probe)) {
            return false;
        }
        for (ExitDoor other : exitDoors) {
            if (Math.hypot(px - other.x, py - other.y) < 36.0) {
                return false;
            }
        }
        return true;
    }

    private void interactNearestDoor() {
        if (gameOver || !player.isAlive()) {
            return;
        }
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
        if (exitDoors.isEmpty()) {
            return;
        }
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
        GameData.ChamberType nextType = best.type == GameData.ChamberType.ENDLESS_ABYSS
                ? GameData.ChamberType.ELITE_HAZARD : best.type;
        enterChamber(best.nextChamber, nextType);
    }

    /** Active squadmates (in Safe Camp & chambers) plus Safe Camp static structures/supply when at hub. */
    private void updateBaseSystems() {
        int turretLv = player.getLevel(GameData.RecipeKind.BASE_TURRET);
        int wallLv = player.getLevel(GameData.RecipeKind.BASE_WALL);
        int medicLv = player.getLevel(GameData.RecipeKind.BASE_MEDIC);
        int farmLv = player.getLevel(GameData.RecipeKind.BASE_FARM);
        if (map.hasSafeCamp() && map.applyBaseUpgrades(turretLv, wallLv, medicLv, farmLv)) {
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

        int desiredAllies = Math.min(4, turretLv);
        while (allies.size() < desiredAllies) {
            allies.add(new AllyNpc(allies.size(), player.getX(), player.getY()));
        }
        boolean allowNpcFire = frame != null || idlePlayerTicks < 180;
        for (AllyNpc a : allies) {
            a.update(turretLv, allowNpcFire);
        }

        if (chamber != 0 || !map.hasSafeCamp()) {
            return;
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
    }

    // ------------------------------------------------------------------ spawning
    private void spawnPendingMonsters() {
        if (chamber <= 0 || chamberCleared || chamberMonstersRemaining <= 0) {
            pendingSpawns.set(0);
            return;
        }
        if (getLiveMonsterCount() < 3 && pendingSpawns.get() == 0) {
            requestSpawns();
        }
        int maxAllowed = settings.doubleSpawn
                ? GameData.MAX_LIVE_MONSTERS
                : Math.min(32, 5 + chamber + getDifficulty());
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

    /** New chamber hostiles enter from outer reachable tiles away from the player. */
    private Monster spawnMonster(GameData.MonsterType type) {
        boolean boss = type == GameData.MonsterType.ZOMBIE_BOSS;
        Point p = boss || rng.nextInt(2) == 0
                ? map.randomSpawnPoint(rng, getCameraView())
                : map.randomWalkablePoint(rng, player.getX(), player.getY(), 170.0);
        Monster m = new Monster(type, p.x, p.y, map, player, GameData.monsterHpScale(chamber, getDifficulty()));
        m.applyChamberScaling(GameData.monsterSpeedScale(chamber), GameData.monsterDamageScale(chamber, getDifficulty()));
        m.setRaiding(true);
        monsters.add(m);
        return m;
    }

    private void spawnBoss() {
        spawnMonster(GameData.MonsterType.ZOMBIE_BOSS);
        AssetLoader.playSound("boss_roar");
        showBanner(getActLabel(chamber) + " BOSS", "Defeat the Brute Boss to earn a Blueprint & Chest!", BANNER_BOSS);
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
                if (!player.ownsWeapon(w)) {
                    player.showHint("Unlock " + w.displayName + " at Camp NPC [B]");
                } else if (chamber == 0) {
                    player.selectPrimaryGun(w);
                    if (!w.melee) {
                        player.showHint("Run Loadout: Survival Knife + " + w.displayName);
                    }
                } else if (player.canUseWeaponInRun(w, false)) {
                    player.switchWeapon(w);
                } else {
                    player.showHint("Carry Knife + 1 Gun per run! Swap at Safe Camp");
                }
            }
        }
        int cycle = weaponCycleRequested;
        if (cycle != 0) {
            weaponCycleRequested = 0;
            player.cycleWeapon(cycle, chamber == 0);
        }
        if (dashRequested) {
            dashRequested = false;
            if (player.triggerDash()) {
                addEffect(new Effect(Effect.Kind.IMPACT, player.getX(), player.getY(), 12, null, null, 20));
            }
        }
        if (interactRequested) {
            interactRequested = false;
            interactNearestDoor();
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
        player.showHint("No manual heal! Clear chambers (+HP/lv) or visit Field Merchant [B]");
        AssetLoader.playSound("click");
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

    /** Damages the player (armour and mercy time apply) with a red number, camera shake, and Death Defiance VFX. */
    private int hurtPlayer(int damage) {
        int ddBefore = player.getDeathDefianceLeft();
        int dealt = player.takeDamage(damage);
        if (dealt > 0) {
            addText(player.getX(), player.getY() - 22, "-" + dealt, TEXT_HURT);
            shake(4.2, 12);
            if (player.getDeathDefianceLeft() < ddBefore) {
                triggerDeathDefianceEffect();
            }
        }
        return dealt;
    }

    /** Damages the player from a chamber environmental trap (reduced by Hazard Suit and Hazard Engineer perk). */
    private int hurtPlayerTrap(int damage) {
        int ddBefore = player.getDeathDefianceLeft();
        int dealt = player.takeTrapDamage(damage);
        if (dealt > 0) {
            addText(player.getX(), player.getY() - 22, "-" + dealt + " TRAP", TEXT_HURT);
            shake(3.5, 10);
            if (player.getDeathDefianceLeft() < ddBefore) {
                triggerDeathDefianceEffect();
            }
        }
        return dealt;
    }

    private void triggerDeathDefianceEffect() {
        addEffect(new Effect(Effect.Kind.EXPLOSION, player.getX(), player.getY(), 24, null, null, 72));
        showBanner("DEATH DEFIANCE", "Second wind at 45% HP! (" + player.getDeathDefianceLeft() + " revives left)", BANNER_WAVE);
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
            Bullet shot;
            while ((shot = m.takeProjectile()) != null) {
                bullets.add(shot);
            }
        }
        Monster.separate(monsters);
        monsters.removeIf(Monster::isRemovable);
    }

    /** Score, loot, Volatile Contagion detonation, combat camera shake, and boss drop banner. */
    private void onMonsterKilled(Monster m) {
        kills++;
        killScore += m.getType().score;
        drops.addAll(m.rollDrops(rng, player.getLootBonus()));
        if (currentChamberType == GameData.ChamberType.ELITE_HAZARD && rng.nextInt(100) < 18) {
            drops.addAll(m.rollDrops(rng, player.getLootBonus()));
        }
        int blastLv = player.getPerkLevel(GameData.TacticalPerk.EXPLOSIVE_KILL);
        if (blastLv > 0) {
            explode(m.getX(), m.getY(), 50, 26 * blastLv);
        }
        if (m.getType() == GameData.MonsterType.ZOMBIE_BOSS) {
            showBanner("ACT BOSS DEFEATED!", "Blueprint & Supply Chest dropped!", BANNER_WAVE);
            shake(7.5, 32);
        } else if (m.getType() == GameData.MonsterType.ZOMBIE_TOXIC
                || m.getType() == GameData.MonsterType.BANDIT_SHOOTER) {
            shake(3.4, 10);
        } else {
            shake(2.6, 8);
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
        if (!b.isMelee() && chamber > 0) {
            for (HazardTrap t : traps) {
                if (!t.triggered && t.kind == HazardTrap.TrapKind.MINE
                        && Math.hypot(t.x - b.getX(), t.y - b.getY()) <= 14.0) {
                    detonateMine(t);
                    addImpact(b);
                    return;
                }
            }
        }
        int venomLv = player.getPerkLevel(GameData.TacticalPerk.VENOM_COATING);
        for (Monster m : monsters) {
            if (!b.isAlive()) {
                return;
            }
            // cheap distance test first: most monsters are nowhere near this bullet
            if (!m.isAlive() || Math.abs(m.getX() - b.getX()) > 64 || Math.abs(m.getY() - b.getY()) > 64) {
                continue;
            }
            if (b.tryHit(m)) {
                int dmg = b.getDamage() + (venomLv > 0 ? 6 * venomLv : 0);
                boolean killed = m.takeHit(dmg, b.getAngle(), b.getKnockback());
                if (venomLv > 0 && !killed) {
                    m.applySlow(85);
                }
                addText(m.getX(), m.getY() - m.getHeight(), String.valueOf(dmg), killed ? TEXT_KILL : TEXT_DAMAGE);
                if (!b.isMelee()) {
                    addImpact(b);
                }
                shake(b.isMelee() ? 1.8 : 1.2, 4);
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
            if (chamberCleared) {
                d.forceMagnet();
            }
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
        return Math.max(1, chamber);
    }

    public int getChamber() {
        return chamber;
    }

    public boolean isInSafeCamp() {
        return chamber == 0;
    }

    public boolean isChamberCleared() {
        return chamberCleared;
    }

    public int getHostilesRemaining() {
        return Math.max(0, chamberMonstersRemaining + getLiveMonsterCount());
    }

    public int getChamberClearPercent() {
        if (chamber <= 0 || chamberMonstersTotal <= 0) {
            return 100;
        }
        int cleared = Math.max(0, chamberMonstersTotal - getHostilesRemaining());
        return Math.min(100, cleared * 100 / chamberMonstersTotal);
    }

    public int getHighestChamberReached() {
        return highestChamberReached;
    }

    public int getRunNumber() {
        return runNumber;
    }

    public GameData.ChamberType getCurrentChamberType() {
        return currentChamberType;
    }

    public double getDashReadyFraction() {
        return player.getDashReadyFraction();
    }

    public int getAllyCount() {
        return allies.size();
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

    /** Kill points + chamber progression bonus + 1000 for extracting, scaled by difficulty. */
    public int getScore() {
        double base = killScore + highestChamberReached * 150 + getElapsedSeconds() + (won ? 1000 : 0);
        return (int) Math.round(base * (0.5 + 0.25 * getDifficulty()));
    }

    public int getElapsedSeconds() {
        return elapsedTicks * GameData.TICK_MS / 1000;
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

    /** True only inside the Safe Camp Hub (chamber 0). */
    public boolean isPlayerInBase() {
        return chamber == 0 && map.hasSafeCamp();
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
            case KeyEvent.VK_SPACE:
            case KeyEvent.VK_SHIFT:
                dashRequested = true;
                break;
            case KeyEvent.VK_F:
                interactRequested = true;
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
            if (chamber > 0) {
                // let this key event finish first; the modal Field Merchant runs its own event loop
                SwingUtilities.invokeLater(frame::showFieldShopDialog);
                return;
            }
            if (!isPlayerInBase()) {
                player.showHint("Camp NPCs are only available at the Safe Camp Hub!");
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

    /** Subtle perimeter outline around Safe Camp Hub (only drawn at chamber 0). */
    private void drawCampPerimeter(Graphics2D g2, Rectangle view) {
        if (!map.hasSafeCamp()) {
            return;
        }
        Rectangle camp = map.getCampArea();
        if (!camp.intersects(view)) {
            return;
        }
        g2.setColor(new Color(110, 210, 120, 95));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRect(camp.x, camp.y, camp.width, camp.height);
    }

    /** Only things near the camera go into the sorted draw list. */
    private void collectVisible(Rectangle view) {
        Rectangle near = new Rectangle(view);
        near.grow(48, 64);   // sprites stick out of their hitbox, so look a bit further
        drawList.clear();
        drawList.addAll(map.getPropsInView(view));
        for (HazardTrap t : traps) {
            if (!t.triggered && near.contains(t.x, t.y)) {
                drawList.add(t);
            }
        }
        for (ExitDoor ed : exitDoors) {
            if (near.contains(ed.x, ed.y)) {
                drawList.add(ed);
            }
        }
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

    /** Damage numbers, pickup labels, Safe Camp NPC prompt, and nearest Exit Portal [F] prompt. */
    private void drawFloatingTexts(Graphics2D g2) {
        if (!gameOver && chamber == 0 && map.hasSafeCamp()) {
            Point trader = map.getTraderPosition();
            if (player.distanceTo(trader.x, trader.y) < 125) {
                drawWorldPrompt(g2, trader.x, trader.y - 26, "PRESS B - CAMP NPC", Theme.GOLD);
            }
        } else if (!gameOver && chamber > 0) {
            Point trader = map.getTraderPosition();
            if (player.distanceTo(trader.x, trader.y) < 140) {
                drawWorldPrompt(g2, trader.x, trader.y - 46, "PRESS B - FIELD MERCHANT (3 OFFERS)", Theme.GOLD);
            }
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
            if (nearest != null) {
                drawWorldPrompt(g2, nearest.x, nearest.y - 30, "PRESS F - " + nearest.label, nearest.portalColor());
            }
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

    private void drawWorldPrompt(Graphics2D g2, double wx, double wy, String prompt, Color color) {
        int pw = Theme.pixelWidth(prompt, 2) + 16;
        int ph = 20;
        int tx = (int) Math.round(wx * SCALE) - originX - pw / 2;
        int ty = Math.max(8, (int) Math.round(wy * SCALE) - originY);
        Theme.paintPanel(g2, Theme.Panel.GLASS, tx, ty, pw, ph, 1);
        Theme.drawPixelTextShadow(g2, prompt, tx + 8, ty + 5, 2, color);
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
        mg.setColor(new Color(8, 12, 26, Math.round(nightAlpha * 130)));
        mg.fillRect(0, 0, mw, mh);

        mg.setComposite(AlphaComposite.DstOut);
        punchLightPool(mg, (float) (player.getX() - camX), (float) (player.getY() - camY), 120f);
        if (map.hasSafeCamp()) {
            Rectangle camp = map.getCampArea();
            punchLightPool(mg, (float) (camp.getCenterX() - camX), (float) (camp.getCenterY() - camY), 145f);
        }
        for (ExitDoor ed : exitDoors) {
            punchLightPool(mg, (float) (ed.x - camX), (float) (ed.y - camY), 74f);
        }
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
        if (map.hasSafeCamp()) {
            Rectangle camp = map.getCampArea();
            drawMarker(g, "map_marker_safe", left + (int) (camp.getCenterX() * perPx), top + (int) (camp.getCenterY() * perPx));
        } else if (chamber > 0) {
            Point tp = map.getTraderPosition();
            int tx = left + (int) (tp.x * perPx), ty = top + (int) (tp.y * perPx);
            g.setColor(new Color(0xF5, 0xC4, 0x51));
            g.fillRect(tx - 3, ty - 3, 6, 6);
            g.setColor(new Color(40, 28, 12));
            g.drawRect(tx - 3, ty - 3, 6, 6);
        }
        for (HazardTrap t : traps) {
            if (t.triggered) {
                continue;
            }
            int tx = left + (int) (t.x * perPx), ty = top + (int) (t.y * perPx);
            if (t.kind == HazardTrap.TrapKind.TOXIC) {
                g.setColor(new Color(145, 235, 60));
            } else if (t.kind == HazardTrap.TrapKind.SHOCK) {
                g.setColor(new Color(95, 225, 255));
            } else if (t.kind == HazardTrap.TrapKind.FLAME_JET) {
                g.setColor(new Color(255, 145, 45));
            } else if (t.kind == HazardTrap.TrapKind.MINE) {
                g.setColor(new Color(255, 70, 70));
            } else {
                g.setColor(new Color(225, 175, 75));
            }
            g.fillRect(tx - 1, ty - 1, 2, 2);
        }
        for (ExitDoor ed : exitDoors) {
            int dx = left + (int) (ed.x * perPx), dy = top + (int) (ed.y * perPx);
            g.setColor(ed.portalColor());
            g.fillRect(dx - 2, dy - 2, 5, 5);
        }
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

        // Roguelite chamber telemetry box right beneath the minimap
        int boxY = top + h + 10;
        Theme.paintPanel(g, Theme.Panel.GLASS, left - 6, boxY, w + 12, 42, 1);
        String zoneText;
        Color zoneColor;
        String intel;
        if (chamber == 0) {
            zoneText = "SAFE CAMP HUB";
            zoneColor = new Color(0x88D470);
            intel = "NPC [B]   GATE [F]";
        } else if (chamberCleared) {
            zoneText = "CH " + chamber + " CLEARED";
            zoneColor = new Color(0x88D470);
            intel = "SHOP [B]  PORTAL [F]";
        } else {
            zoneText = "CHAMBER " + chamber + " / 15";
            zoneColor = Theme.GOLD;
            intel = "FOES " + getHostilesRemaining() + "   SHOP [B]";
        }
        Theme.drawPixelTextShadow(g, zoneText, left + 2, boxY + 8, 2, zoneColor);
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
            {"Dash (Dodge)", "space", "shift"}, {"Gate / Portal", "f"},
            {"Grenade", "mouse_right", "g"}, {"Medkit", "e"},
            {"Switch weapon (1-7)", "mouse_mid"}, {"Camp NPC [Hub]", "b"},
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
        String stats = "CHAMBER " + highestChamberReached + "   INFECTED KILLED " + kills + "   PERKS " + getTotalPerkLevels() + "   TIME " + formatTime(getElapsedSeconds());
        Theme.drawPixelTextShadow(g2, stats, (w - Theme.pixelWidth(stats, 3)) / 2, h / 2 + 16, 3, Theme.TEXT);
    }

    public int getTotalPerkLevels() {
        int total = 0;
        for (int v : player.getRunPerks().values()) {
            total += v;
        }
        return total;
    }
}
