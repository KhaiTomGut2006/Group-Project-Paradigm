// Tapatsawin Chaieua          6813105
// Thanapon Thanadunpremdet    6813106
// Tanawat Wongwad             6813107
// Natthanon Achachan          6813241
package Project3_6813107;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Superclass of everything that lives in the world: Player, Monster, Bullet and DroppedItem.
 *
 * Coordinates are world pixels (the GamePanel scales and scrolls the whole world when drawing).
 * (x, y) is the CENTER of the hitbox; width/height is the hitbox size. The bottom edge of the
 * hitbox is the "feet" line: sprites stand on it and the draw order is sorted by it.
 */
public abstract class Entity implements GameMap.Drawable {

    protected double x, y;
    protected int width, height;
    protected int hp, maxHp;
    protected double speed;
    protected boolean alive = true;

    protected int animTimer;          // counts ticks, used to pick animation frames
    protected boolean facingLeft;     // sprites are drawn mirrored when true
    protected GameMap map;            // for collision; null = no walls (e.g. in tests)

    protected Entity(double x, double y, int width, int height, int maxHp, double speed) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.maxHp = maxHp;
        this.hp = maxHp;
        this.speed = speed;
    }

    /** One simulation step (16 ms). */
    public abstract void update();

    /** Draws in world coordinates. */
    @Override
    public abstract void draw(Graphics2D g2);

    /** The hitbox in world pixels, for entityA.getBounds().intersects(entityB.getBounds()). */
    public Rectangle getBounds() {
        return new Rectangle((int) Math.round(x - width / 2.0), (int) Math.round(y - height / 2.0), width, height);
    }

    /** The small box at the feet used for walking against walls, so heads can overlap props "behind" them. */
    public Rectangle getFeetBounds() {
        int feetH = Math.min(8, height);
        return new Rectangle((int) Math.round(x - width / 2.0), (int) Math.round(getFeetY()) - feetH, width, feetH);
    }

    /** Y of the feet line (bottom of the hitbox). */
    public double getFeetY() {
        return y + height / 2.0;
    }

    /** Things further down the screen are drawn later (in front). */
    @Override
    public double getSortY() {
        return getFeetY();
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public void shiftY(double dy) {
        this.y += dy;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getHp() {
        return hp;
    }

    public int getMaxHp() {
        return maxHp;
    }

    public boolean isAlive() {
        return alive;
    }

    public void setMap(GameMap map) {
        this.map = map;
    }

    public double distanceTo(Entity other) {
        return Math.hypot(other.x - x, other.y - y);
    }

    public double distanceTo(double px, double py) {
        return Math.hypot(px - x, py - y);
    }

    /** Angle in radians from this entity to the other (0 = right, PI/2 = down). */
    public double angleTo(Entity other) {
        return Math.atan2(other.y - y, other.x - x);
    }

    /** Lowers hp; returns the damage really dealt. hp 0 means the entity is no longer alive. */
    public int takeDamage(int amount) {
        if (!alive || amount <= 0) {
            return 0;
        }
        int dealt = Math.min(hp, amount);
        hp -= dealt;
        if (hp <= 0) {
            alive = false;
        }
        return dealt;
    }

    /**
     * Moves by (dx, dy) one axis at a time so the entity slides along walls instead of sticking.
     * Returns false if any part of the move was blocked.
     */
    protected boolean moveWithCollision(double dx, double dy) {
        if (map == null) {
            x += dx;
            y += dy;
            return true;
        }
        boolean free = true;
        if (dx != 0) {
            x += dx;
            if (map.isBlocked(getFeetBounds())) {
                x -= dx;
                free = false;
            }
        }
        if (dy != 0) {
            y += dy;
            if (map.isBlocked(getFeetBounds())) {
                y -= dy;
                free = false;
            }
        }
        return free;
    }

    /**
     * If the feet ended up inside a wall or prop (e.g. spawned on a bad spot), moves the entity to the
     * nearest free spot, searching outwards in rings of 8 px. Returns false if nothing free was found.
     */
    public boolean pushOutOfWalls() {
        if (!isInsideWall()) {
            return true;
        }
        double startX = x, startY = y;
        for (int ring = 1; ring <= 12; ring++) {
            for (int i = 0; i < 16; i++) {
                double a = i * Math.PI / 8;
                x = startX + Math.cos(a) * ring * 8;
                y = startY + Math.sin(a) * ring * 8;
                if (!isInsideWall()) {
                    return true;
                }
            }
        }
        x = startX;
        y = startY;
        return false;
    }

    /** True if the feet would be inside a wall or prop at the current position. */
    public boolean isInsideWall() {
        return map != null && map.isBlocked(getFeetBounds());
    }

    /**
     * Draws a sprite frame standing on the feet line, centred on x, optionally mirrored and scaled.
     * Character frames are 32x32 with the feet on the bottom row, so this lines up for all of them.
     */
    protected void drawFrame(Graphics2D g2, BufferedImage frame, double scale, boolean mirror) {
        int w = (int) Math.round(frame.getWidth() * scale);
        int h = (int) Math.round(frame.getHeight() * scale);
        int left = (int) Math.round(x - w / 2.0);
        int top = (int) Math.round(getFeetY()) - h;
        if (mirror) {
            g2.drawImage(frame, left + w, top, -w, h, null);
        } else {
            g2.drawImage(frame, left, top, w, h, null);
        }
    }

    /** Same as drawFrame but painted as a solid white silhouette: the classic "I got hit" flash. */
    protected void drawFlash(Graphics2D g2, BufferedImage frame, double scale, boolean mirror, float strength) {
        BufferedImage white = whiteSilhouette(frame);
        Composite old = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, strength))));
        drawFrame(g2, white, scale, mirror);
        g2.setComposite(old);
    }

    /** Small soft shadow under the feet so sprites feel grounded. */
    protected void drawShadow(Graphics2D g2, int shadowW) {
        g2.setColor(new Color(0, 0, 0, 60));
        int feet = (int) Math.round(getFeetY());
        g2.fillOval((int) Math.round(x - shadowW / 2.0), feet - 3, shadowW, 5);
    }

    /** Outline of the hitbox (yellow) and feet box (cyan) for the "Show Hitbox" option. */
    public void drawHitbox(Graphics2D g2) {
        g2.setColor(new Color(255, 230, 0, 200));
        Rectangle b = getBounds();
        g2.drawRect(b.x, b.y, b.width, b.height);
        g2.setColor(new Color(0, 230, 255, 200));
        Rectangle f = getFeetBounds();
        g2.drawRect(f.x, f.y, f.width, f.height);
    }

    // ------------------------------------------------------------------ white silhouettes (cached per frame)
    private static final Map<BufferedImage, BufferedImage> WHITE_CACHE = new HashMap<>();

    private static BufferedImage whiteSilhouette(BufferedImage frame) {
        synchronized (WHITE_CACHE) {
            BufferedImage cached = WHITE_CACHE.get(frame);
            if (cached != null) {
                return cached;
            }
            BufferedImage out = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int py = 0; py < frame.getHeight(); py++) {
                for (int px = 0; px < frame.getWidth(); px++) {
                    int alpha = frame.getRGB(px, py) >>> 24;
                    out.setRGB(px, py, (alpha << 24) | 0xFFFFFF);
                }
            }
            WHITE_CACHE.put(frame, out);
            return out;
        }
    }
}
