// Tapatsawin Chaieua          6813105
// Thanapon Thanadunpremdet    6813106
// Tanawat Wongwad             6813107
// Natthanon Achachan          6813241
package Project3_6813107;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Projectiles and melee arcs: bullets, rockets, arcing grenades, enemy shots, and melee slashes.
 */
public final class Bullet extends Entity {

    private final GameData.Projectile kind;
    private final BufferedImage sprite;
    private final boolean hostile, explosive;
    private final int damage, blastRadius, knockback, maxLife;
    private final double angle, vx, vy, maxRange, arcRad, targetX, targetY;
    private final Entity owner;
    private final List<Entity> alreadyHit = new ArrayList<>();

    private int pierceLeft, lifeTicks;
    private double travelled, height;
    private boolean explosionPending, wallHitPending;

    private Bullet(GameData.Projectile kind, double x, double y, double angle, double speed, int damage,
                   double maxRange, boolean hostile, boolean explosive, int blastRadius, int pierce,
                   Entity owner, double arcRad, int maxLife, double targetX, double targetY) {
        super(x, y, kind.radius * 2, kind.radius * 2, 1, speed);
        this.kind = kind;
        this.sprite = AssetLoader.getProjectileImage(kind);
        this.angle = angle;
        this.vx = Math.cos(angle) * speed;
        this.vy = Math.sin(angle) * speed;
        this.damage = damage;
        this.maxRange = maxRange;
        this.hostile = hostile;
        this.explosive = explosive;
        this.blastRadius = blastRadius;
        this.pierceLeft = pierce;
        this.owner = owner;
        this.arcRad = arcRad;
        this.maxLife = maxLife;
        this.targetX = targetX;
        this.targetY = targetY;
        this.knockback = owner != null ? 1 : Math.min(6, 1 + damage / 12);
    }

    // ------------------------------------------------------------------ factories
    public static Bullet shot(double x, double y, double angle, GameData.WeaponType w, int damage) {
        return shot(x, y, angle, w, damage, 1.0, 0);
    }

    public static Bullet shot(double x, double y, double angle, GameData.WeaponType w, int damage,
                              double speedMult, int bonusPierce) {
        return new Bullet(w.projectile(), x, y, angle, w.bulletSpeed * speedMult, damage, w.range, false,
                w.explosive, w.blastRadius, w.pierce + bonusPierce, null, 0, 0, 0, 0);
    }

    public static Bullet melee(Entity owner, double angle, GameData.WeaponType w, int damage) {
        return new Bullet(GameData.Projectile.SLASH, owner.getX(), owner.getY(), angle, 0, damage, w.range, false,
                false, 0, 99, owner, Math.toRadians(w.spreadDeg), 6, 0, 0);
    }

    public static Bullet grenade(double x, double y, double targetX, double targetY) {
        double dist = Math.min(200, Math.hypot(targetX - x, targetY - y));
        double a = Math.atan2(targetY - y, targetX - x);
        double tx = x + Math.cos(a) * dist, ty = y + Math.sin(a) * dist;
        int flight = Math.max(12, (int) (dist / 6.5));
        return new Bullet(GameData.Projectile.GRENADE, x, y, a, dist / flight, GameData.GRENADE_DAMAGE, dist, false,
                true, GameData.GRENADE_BLAST_RADIUS, 0, null, 0, flight, tx, ty);
    }

    public static Bullet enemyShot(double x, double y, double angle, GameData.Projectile kind, int damage, double range) {
        double speed = kind == GameData.Projectile.TOXIC_SPIT ? 5.4 : 6.8;
        return new Bullet(kind, x, y, angle, speed, damage, range, true, false, 0, 0, null, 0, 0, 0, 0);
    }

    // ------------------------------------------------------------------ simulation
    @Override
    public void update() {
        if (!alive) return;
        animTimer++;
        switch (kind) {
            case SLASH -> {
                x = owner.getX();
                y = owner.getY();
                if (++lifeTicks >= maxLife) alive = false;
            }
            case GRENADE -> {
                lifeTicks++;
                x += vx;
                y += vy;
                height = Math.sin(Math.PI * Math.min(1.0, lifeTicks / (double) maxLife)) * 34;
                if (lifeTicks >= maxLife) {
                    x = targetX;
                    y = targetY;
                    height = 0;
                    end(true);
                }
            }
            default -> {
                x += vx;
                y += vy;
                travelled += speed;
                if (map != null && map.isBlocked(new Rectangle((int) x - 2, (int) y - 2, 4, 4))) {
                    wallHitPending = true;
                    end(explosive);
                } else if (travelled >= maxRange) {
                    end(explosive);
                }
            }
        }
    }

    private void end(boolean explode) {
        alive = false;
        if (explode && explosive) explosionPending = true;
    }

    public boolean tryHit(Entity target) {
        if (!alive || target == null || !target.isAlive() || kind == GameData.Projectile.GRENADE || alreadyHit.contains(target)) {
            return false;
        }
        int r = kind.radius;
        boolean hit = isMelee() ? isInsideArc(target) : new Rectangle((int) (x - r), (int) (y - r), r * 2, r * 2).intersects(target.getBounds());
        if (!hit) return false;
        alreadyHit.add(target);
        if (isMelee()) return true;
        if (explosive) end(true);
        else if (pierceLeft > 0) pierceLeft--;
        else alive = false;
        return true;
    }

    private boolean isInsideArc(Entity target) {
        double reach = maxRange + Math.max(target.getWidth(), target.getHeight()) / 2.0;
        double dist = Math.hypot(target.getX() - x, target.getY() - y);
        if (dist > reach) return false;
        double toTarget = Math.atan2(target.getY() - y, target.getX() - x);
        double diff = Math.abs(Math.atan2(Math.sin(toTarget - angle), Math.cos(toTarget - angle)));
        return diff <= arcRad / 2 || dist < 10;
    }

    public boolean consumeExplosion() {
        boolean e = explosionPending;
        explosionPending = false;
        return e;
    }

    public boolean consumeWallHit() {
        boolean w = wallHitPending;
        wallHitPending = false;
        return w;
    }

    // ------------------------------------------------------------------ getters
    public int getDamage() { return damage; }
    public double getAngle() { return angle; }
    public int getKnockback() { return knockback; }
    public boolean isHostile() { return hostile; }
    public int getBlastRadius() { return blastRadius; }
    public boolean isMelee() { return kind == GameData.Projectile.SLASH; }
    public int getPoisonTicks() { return kind == GameData.Projectile.TOXIC_SPIT ? GameData.msToTicks(3000) : 0; }
    @Override public double getSortY() { return y + (isMelee() ? 20 : 0); }

    // ------------------------------------------------------------------ drawing
    @Override
    public void draw(Graphics2D g2) {
        if (!alive) return;
        if (isMelee()) {
            drawSlash(g2);
            return;
        }
        if (kind == GameData.Projectile.GRENADE) {
            g2.setColor(new Color(0, 0, 0, 70));
            g2.fillOval((int) x - 4, (int) y - 1, 8, 4);
        }
        if (kind == GameData.Projectile.SNIPER_ROUND) {
            Stroke old = g2.getStroke();
            g2.setStroke(new BasicStroke(1.5f));
            g2.setColor(new Color(255, 240, 200, 110));
            g2.drawLine((int) (x - vx * 3), (int) (y - vy * 3), (int) x, (int) y);
            g2.setStroke(old);
        }
        AffineTransform old = g2.getTransform();
        g2.translate(x, y - height);
        double spin = kind == GameData.Projectile.GRENADE ? animTimer * 0.35 : angle;
        if (kind == GameData.Projectile.TOXIC_SPIT) {
            double pulse = 1.0 + 0.12 * Math.sin(animTimer * 0.5);
            g2.scale(pulse, pulse);
            spin = 0;
        }
        g2.rotate(spin);
        g2.drawImage(sprite, -8, -8, null);
        g2.setTransform(old);
    }

    private void drawSlash(Graphics2D g2) {
        float life = 1f - lifeTicks / (float) maxLife;
        Composite oldC = g2.getComposite();
        Stroke oldS = g2.getStroke();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, 0.75f * life)));
        g2.setColor(Color.WHITE);
        double sweep = Math.toDegrees(arcRad), progress = Math.min(1.0, lifeTicks / 4.0);
        double start = -Math.toDegrees(angle) - sweep / 2;
        g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(new Arc2D.Double(x - maxRange * 0.8, y - maxRange * 0.8, maxRange * 1.6, maxRange * 1.6, start, sweep * progress, Arc2D.OPEN));
        g2.setStroke(new BasicStroke(1.5f));
        g2.draw(new Arc2D.Double(x - maxRange * 0.55, y - maxRange * 0.55, maxRange * 1.1, maxRange * 1.1, start, sweep * progress, Arc2D.OPEN));
        g2.setComposite(oldC);
        g2.setStroke(oldS);
    }
}
