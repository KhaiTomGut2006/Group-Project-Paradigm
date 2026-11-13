// Tapatsawin Chaieua          6813105
// Thanapon Thanadunpremdet    6813106
// Tanawat Wongwad             6813107
// Natthanon Achachan          6813241
package Project3_6813107;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Loot lying on the ground. It pops out of the monster, bobs up and down with a glint, flies to
 * the player once the player is inside the pickup radius ("magnet"), and blinks before it
 * disappears. A CHEST is different: walking onto it plays the opening animation and then it
 * bursts into several items (GamePanel asks isChestReadyToBurst() and adds burstChest(...)).
 *
 * GamePanel each tick:  item.update();  if (item.isReadyToCollect()) player.pickUp(item);
 */
public final class DroppedItem extends Entity {

    private static final int OPEN_TICKS = 6 * 5;    // chest opening animation length

    private final GameData.ItemType type;
    private final int amount;
    private final Player collector;
    private final BufferedImage[] frames;
    private double vx, vy;           // pop-out motion when dropped
    private int age;
    private final int lifetime;
    private boolean collected;
    private boolean flying;          // pulled by the magnet
    private int flyTicks;            // speeds up the longer it flies
    private int openTick = -1;       // chest: -1 = closed, otherwise ticks since it started opening
    private boolean burst;

    public DroppedItem(GameData.ItemType type, int amount, double x, double y, Player collector) {
        super(x, y, type == GameData.ItemType.CHEST ? 26 : 12, type == GameData.ItemType.CHEST ? 12 : 10, 1, 0);
        this.type = type;
        this.amount = Math.max(1, amount);
        this.collector = collector;
        this.frames = AssetLoader.getItemFrames(type);
        this.lifetime = type == GameData.ItemType.CHEST ? Integer.MAX_VALUE
                : GameData.ITEM_LIFETIME_SECONDS * 1000 / GameData.TICK_MS;
        this.animTimer = (int) Math.floorMod((long) (x * 31 + y * 17), 60L);
    }

    /** Gives the item a small random pop so several drops fan out instead of stacking. */
    public void scatter(Random rng) {
        double a = rng.nextDouble() * Math.PI * 2;
        double s = 0.8 + rng.nextDouble() * 1.2;
        vx = Math.cos(a) * s;
        vy = Math.sin(a) * s;
    }

    public GameData.ItemType getType() {
        return type;
    }

    public int getAmount() {
        return amount;
    }

    /** "+3 Scraps" for the floating pickup text. */
    public String getLabel() {
        return "+" + amount + " " + type.displayName;
    }

    // ------------------------------------------------------------------ simulation
    @Override
    public void update() {
        if (!alive) {
            return;
        }
        animTimer++;
        age++;
        if (Math.abs(vx) + Math.abs(vy) > 0.02) {
            moveWithCollision(vx, vy);
            vx *= 0.85;
            vy *= 0.85;
        }
        if (type == GameData.ItemType.CHEST) {
            updateChest();
            return;
        }
        if (age > lifetime) {
            alive = false;
            return;
        }
        if (collector == null || !collector.isAlive()) {
            return;
        }
        double dist = distanceTo(collector);
        if (flying || (age > 20 && dist < collector.getPickupRadius())) {
            // magnet: accelerate towards the player; ignores walls so items never get stuck
            flying = true;
            flyTicks++;
            double pull = Math.min(dist, Math.min(7.0, 1.5 + flyTicks * 0.2));
            x += (collector.getX() - x) / Math.max(1, dist) * pull;
            y += (collector.getY() - y) / Math.max(1, dist) * pull;
        }
    }

    private void updateChest() {
        if (openTick >= 0) {
            openTick++;
            if (openTick >= OPEN_TICKS && !burst) {
                burst = true;
            }
            if (openTick > OPEN_TICKS + 90) {
                alive = false;   // the empty chest fades away
            }
            return;
        }
        if (collector != null && collector.isAlive() && getBounds().intersects(collector.getBounds())) {
            openTick = 0;
            AssetLoader.playSound("chest_open");
        }
    }

    /** True when the player touches a normal item: call player.pickUp(this). */
    public boolean isReadyToCollect() {
        if (!alive || collected || type == GameData.ItemType.CHEST || collector == null) {
            return false;
        }
        return distanceTo(collector) < 10 || getBounds().intersects(collector.getBounds());
    }

    /** Called by Player.pickUp so the item disappears. */
    public void markCollected() {
        collected = true;
        alive = false;
    }

    /** True once, when an opened chest is ready to spill its loot. */
    public boolean isChestReadyToBurst() {
        return burst && openTick >= OPEN_TICKS && !collected;
    }

    /** The chest's loot (call once when isChestReadyToBurst()). */
    public List<DroppedItem> burstChest(Random rng) {
        collected = true;
        List<DroppedItem> loot = new ArrayList<>();
        GameData.ItemType[] pool = {GameData.ItemType.AMMO, GameData.ItemType.STEEL, GameData.ItemType.ELECTRONICS,
            GameData.ItemType.BATTERY, GameData.ItemType.MEDKIT, GameData.ItemType.GRENADE,
            GameData.ItemType.ARMOR_VEST, GameData.ItemType.SCRAPS, GameData.ItemType.FOOD};
        int count = 5 + rng.nextInt(3);
        for (int i = 0; i < count; i++) {
            GameData.ItemType t = pool[rng.nextInt(pool.length)];
            int n = t == GameData.ItemType.AMMO ? 15 + rng.nextInt(16) : 1 + rng.nextInt(2);
            DroppedItem d = new DroppedItem(t, n, x, y, collector);
            d.setMap(map);
            d.scatter(rng);
            loot.add(d);
        }
        return loot;
    }

    // ------------------------------------------------------------------ drawing
    @Override
    public void draw(Graphics2D g2) {
        if (!alive) {
            return;
        }
        if (type == GameData.ItemType.CHEST) {
            drawChest(g2);
            return;
        }
        int left = lifetime - age;
        if (left < 300 && (left / 8) % 2 == 0) {
            return;   // blink during the last ~5 seconds
        }
        double bob = flying ? 0 : Math.sin(animTimer * 0.12) * 2.0;
        // shadow shrinks while the item bobs up
        g2.setColor(new Color(0, 0, 0, 55));
        int sw = (int) (10 - bob);
        g2.fillOval((int) x - sw / 2, (int) getFeetY() - 2, sw, 3);
        // the glint sweeps over the item (frames 0-6), then the plain last frame rests for a while
        int step = (animTimer / 6) % 18;
        BufferedImage frame = frames[Math.min(step, frames.length - 1)];
        g2.drawImage(frame, (int) Math.round(x - 8), (int) Math.round(getFeetY() - 16 - 2 + bob), null);
    }

    private void drawChest(Graphics2D g2) {
        int f = openTick < 0 ? 0 : Math.min(frames.length - 1, openTick / 5);
        Composite old = g2.getComposite();
        if (openTick > OPEN_TICKS + 50) {
            float a = Math.max(0f, 1f - (openTick - OPEN_TICKS - 50) / 40f);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
        }
        g2.setColor(new Color(0, 0, 0, 60));
        g2.fillOval((int) x - 15, (int) getFeetY() - 3, 30, 5);
        BufferedImage frame = frames[f];
        int bounce = openTick < 0 ? (int) Math.round(Math.abs(Math.sin(animTimer * 0.08)) * -2) : 0;
        g2.drawImage(frame, (int) Math.round(x - 16), (int) Math.round(getFeetY() - 16 + bounce), null);
        g2.setComposite(old);
    }

    /** Items lie on the ground, so they are drawn behind characters standing at the same y. */
    @Override
    public double getSortY() {
        return getFeetY() - 6;
    }
}
