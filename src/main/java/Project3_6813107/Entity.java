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

public abstract class Entity implements GameMap.Drawable {

    protected double x, y;
    protected int width, height;
    protected int hp, maxHp;
    protected double speed;
    protected boolean alive = true;

    protected int animTimer;
    protected boolean facingLeft;
    protected GameMap map;

    protected Entity(double x, double y, int width, int height, int maxHp, double speed) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.maxHp = maxHp;
        this.hp = maxHp;
        this.speed = speed;
    }

    public abstract void update();
    @Override public abstract void draw(Graphics2D g2);

    public Rectangle getBounds() {
        return new Rectangle((int) Math.round(x - width / 2.0), (int) Math.round(y - height / 2.0), width, height);
    }

    public Rectangle getFeetBounds() {
        int feetH = Math.min(8, height);
        return new Rectangle((int) Math.round(x - width / 2.0), (int) Math.round(getFeetY()) - feetH, width, feetH);
    }

    public double getFeetY() { return y + height / 2.0; }
    @Override public double getSortY() { return getFeetY(); }
    public double getX() { return x; }
    public double getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }
    public boolean isAlive() { return alive; }
    public void setMap(GameMap map) { this.map = map; }
    public void setPosition(double x, double y) { this.x = x; this.y = y; }
    public double distanceTo(Entity other) { return Math.hypot(other.x - x, other.y - y); }
    public double distanceTo(double px, double py) { return Math.hypot(px - x, py - y); }
    public double angleTo(Entity other) { return Math.atan2(other.y - y, other.x - x); }
    public boolean isInsideWall() { return map != null && map.isBlocked(getFeetBounds()); }

    public int takeDamage(int amount) {
        if (!alive || amount <= 0) return 0;
        int dealt = Math.min(hp, amount);
        hp -= dealt;
        if (hp <= 0) alive = false;
        return dealt;
    }

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

    public boolean pushOutOfWalls() {
        if (!isInsideWall()) return true;
        double startX = x, startY = y;
        for (int ring = 1; ring <= 12; ring++) {
            for (int i = 0; i < 16; i++) {
                double a = i * Math.PI / 8;
                x = startX + Math.cos(a) * ring * 8;
                y = startY + Math.sin(a) * ring * 8;
                if (!isInsideWall()) return true;
            }
        }
        x = startX;
        y = startY;
        return false;
    }

    protected void drawFrame(Graphics2D g2, BufferedImage frame, double scale, boolean mirror) {
        int w = (int) Math.round(frame.getWidth() * scale);
        int h = (int) Math.round(frame.getHeight() * scale);
        int left = (int) Math.round(x - w / 2.0);
        int top = (int) Math.round(getFeetY()) - h;
        g2.drawImage(frame, mirror ? left + w : left, top, mirror ? -w : w, h, null);
    }

    protected void drawFlash(Graphics2D g2, BufferedImage frame, double scale, boolean mirror, float strength) {
        Composite old = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, strength))));
        drawFrame(g2, whiteSilhouette(frame), scale, mirror);
        g2.setComposite(old);
    }

    protected void drawShadow(Graphics2D g2, int shadowW) {
        g2.setColor(new Color(0, 0, 0, 60));
        g2.fillOval((int) Math.round(x - shadowW / 2.0), (int) Math.round(getFeetY()) - 3, shadowW, 5);
    }

    public void drawHitbox(Graphics2D g2) {
        g2.setColor(new Color(255, 230, 0, 200));
        Rectangle b = getBounds();
        g2.drawRect(b.x, b.y, b.width, b.height);
        g2.setColor(new Color(0, 230, 255, 200));
        Rectangle f = getFeetBounds();
        g2.drawRect(f.x, f.y, f.width, f.height);
    }

    private static final Map<BufferedImage, BufferedImage> WHITE_CACHE = new HashMap<>();

    private static BufferedImage whiteSilhouette(BufferedImage frame) {
        synchronized (WHITE_CACHE) {
            BufferedImage cached = WHITE_CACHE.get(frame);
            if (cached != null) return cached;
            BufferedImage out = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int py = 0; py < frame.getHeight(); py++) {
                for (int px = 0; px < frame.getWidth(); px++) {
                    out.setRGB(px, py, (frame.getRGB(px, py) & 0xFF000000) | 0xFFFFFF);
                }
            }
            WHITE_CACHE.put(frame, out);
            return out;
        }
    }
}
