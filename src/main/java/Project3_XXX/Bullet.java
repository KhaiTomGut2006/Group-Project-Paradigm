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
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything that flies and hurts: player bullets, rockets, grenades, enemy bullets, toxic spit,
 * and the invisible "slash" of a melee swing (a very short-lived hitbox in front of the player).
 *
 * How GamePanel uses a bullet each tick:
 *   b.update();
 *   for each monster m: if (b.tryHit(m)) m.takeHit(b.getDamage(), b.getAngle(), b.getKnockback());
 *   if (b.consumeExplosion()) ... area damage around b with radius b.getBlastRadius()
 *   if (b.consumeWallHit()) ... impact sparks at b
 *   remove b when !b.isAlive()
 * tryHit remembers who was already hit, so a piercing bullet or a slash damages each monster once.
 */
public final class Bullet extends Entity {

    private final GameData.Projectile kind;
    private final BufferedImage sprite;
    private final boolean hostile;          // true = fired by a monster, hurts the player
    private final int damage;
    private final double angle;
    private final double vx, vy;
    private final double maxRange;
    private final boolean explosive;
    private final int blastRadius;
    private final int knockback;
    private int pierceLeft;
    private double travelled;
    private final List<Entity> alreadyHit = new ArrayList<>();
    private boolean explosionPending;
    private boolean wallHitPending;

    // melee slash: follows its owner for a few ticks and hits everything inside the arc
    private final Entity owner;
    private final double arcRad;
    private int lifeTicks;
    private final int maxLife;

    // grenade: flies in an arc to a target point and explodes there
    private final double targetX, targetY;
    private double height;

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
        this.knockback = Math.min(7, 1 + damage / 10);
    }

    // ------------------------------------------------------------------ factories
    /** A bullet/rocket fired by the player with weapon w (damage already includes upgrades). */
    public static Bullet shot(double x, double y, double angle, GameData.WeaponType w, int damage) {
        return new Bullet(w.projectile(), x, y, angle, w.bulletSpeed, damage, w.range, false,
                w.explosive, w.blastRadius, w.pierce, null, 0, 0, 0, 0);
    }

    /** The invisible hitbox of a knife/axe swing in front of the owner. */
    public static Bullet melee(Entity owner, double angle, GameData.WeaponType w, int damage) {
        return new Bullet(GameData.Projectile.SLASH, owner.getX(), owner.getY(), angle, 0, damage, w.range, false,
                false, 0, 99, owner, Math.toRadians(w.spreadDeg), 9, 0, 0);
    }

    /** A thrown grenade that lands on (targetX, targetY) - at most 200 px away - and explodes. */
    public static Bullet grenade(double x, double y, double targetX, double targetY) {
        double dist = Math.min(200, Math.hypot(targetX - x, targetY - y));
        double a = Math.atan2(targetY - y, targetX - x);
        double tx = x + Math.cos(a) * dist, ty = y + Math.sin(a) * dist;
        int flight = Math.max(20, (int) (dist / 4.0));
        return new Bullet(GameData.Projectile.GRENADE, x, y, a, dist / flight, GameData.GRENADE_DAMAGE, dist, false,
                true, GameData.GRENADE_BLAST_RADIUS, 0, null, 0, flight, tx, ty);
    }

    /** A projectile fired by a monster (bandit bullet, toxic spit). */
    public static Bullet enemyShot(double x, double y, double angle, GameData.Projectile kind, int damage, double range) {
        double speed = kind == GameData.Projectile.TOXIC_SPIT ? 2.6 : 3.6;
        return new Bullet(kind, x, y, angle, speed, damage, range, true, false, 0, 0, null, 0, 0, 0, 0);
    }

    // ------------------------------------------------------------------ simulation
    @Override
    public void update() {
        if (!alive) {
            return;
        }
        animTimer++;
        switch (kind) {
            case SLASH:
                updateSlash();
                break;
            case GRENADE:
                updateGrenade();
                break;
            default:
                updateFlying();
                break;
        }
    }

    private void updateSlash() {
        // stick to the owner so the swing moves with the player
        x = owner.getX();
        y = owner.getY();
        lifeTicks++;
        if (lifeTicks >= maxLife) {
            alive = false;
        }
    }

    private void updateGrenade() {
        lifeTicks++;
        double t = lifeTicks / (double) maxLife;
        x += vx;
        y += vy;
        height = Math.sin(Math.PI * Math.min(1.0, t)) * 34;   // up and down again
        if (lifeTicks >= maxLife) {
            x = targetX;
            y = targetY;
            height = 0;
            end(true);
        }
    }

    private void updateFlying() {
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

    /** Stops the bullet; an explosive one leaves an explosion for GamePanel to pick up. */
    private void end(boolean explode) {
        alive = false;
        if (explode && explosive) {
            explosionPending = true;
        }
    }

    /**
     * Checks one target. Returns true exactly once per target when this bullet hits it, and
     * uses up the bullet unless it still has pierce left. Grenades never hit directly (they explode).
     */
    public boolean tryHit(Entity target) {
        if (!alive || target == null || !target.isAlive() || kind == GameData.Projectile.GRENADE || alreadyHit.contains(target)) {
            return false;
        }
        boolean hit;
        if (kind == GameData.Projectile.SLASH) {
            hit = isInsideArc(target);
        } else {
            Rectangle me = new Rectangle((int) (x - kind.radius), (int) (y - kind.radius), kind.radius * 2, kind.radius * 2);
            hit = me.intersects(target.getBounds());
        }
        if (!hit) {
            return false;
        }
        alreadyHit.add(target);
        if (kind != GameData.Projectile.SLASH) {
            if (explosive) {
                end(true);
            } else if (pierceLeft > 0) {
                pierceLeft--;
            } else {
                alive = false;
            }
        }
        return true;
    }

    /** A target is inside the swing if it is within reach and within half the arc of the swing direction. */
    private boolean isInsideArc(Entity target) {
        double reach = maxRange + Math.max(target.getWidth(), target.getHeight()) / 2.0;
        double dist = Math.hypot(target.getX() - x, target.getY() - y);
        if (dist > reach) {
            return false;
        }
        double toTarget = Math.atan2(target.getY() - y, target.getX() - x);
        double diff = Math.abs(Math.atan2(Math.sin(toTarget - angle), Math.cos(toTarget - angle)));
        return diff <= arcRad / 2 || dist < 10;
    }

    /** True once, after an explosive bullet ended (rocket hit, wall, max range, grenade landed). */
    public boolean consumeExplosion() {
        boolean e = explosionPending;
        explosionPending = false;
        return e;
    }

    /** True once, after the bullet stopped against a wall or prop (for impact sparks). */
    public boolean consumeWallHit() {
        boolean w = wallHitPending;
        wallHitPending = false;
        return w;
    }

    // ------------------------------------------------------------------ getters
    public GameData.Projectile getKind() {
        return kind;
    }

    public int getDamage() {
        return damage;
    }

    /** Travel (or swing) direction in radians; monsters are pushed this way. */
    public double getAngle() {
        return angle;
    }

    /** How many pixels a monster is pushed back by this hit. */
    public int getKnockback() {
        return knockback;
    }

    public boolean isHostile() {
        return hostile;
    }

    public boolean isExplosive() {
        return explosive;
    }

    public int getBlastRadius() {
        return blastRadius;
    }

    public boolean isMelee() {
        return kind == GameData.Projectile.SLASH;
    }

    /** Toxic spit poisons the player for a while (number of ticks), other bullets do not. */
    public int getPoisonTicks() {
        return kind == GameData.Projectile.TOXIC_SPIT ? GameData.msToTicks(3000) : 0;
    }

    // ------------------------------------------------------------------ drawing
    @Override
    public void draw(Graphics2D g2) {
        if (!alive) {
            return;
        }
        if (kind == GameData.Projectile.SLASH) {
            drawSlash(g2);
            return;
        }
        if (kind == GameData.Projectile.GRENADE) {
            // shadow stays on the ground while the grenade flies up
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
        double drawY = y - height;
        g2.translate(x, drawY);
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

    /** A fading white crescent along the swing arc. */
    private void drawSlash(Graphics2D g2) {
        float life = 1f - lifeTicks / (float) maxLife;
        Composite oldC = g2.getComposite();
        Stroke oldS = g2.getStroke();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, 0.75f * life)));
        g2.setColor(Color.WHITE);
        double reach = maxRange;
        double sweep = Math.toDegrees(arcRad);
        double progress = Math.min(1.0, lifeTicks / 4.0);    // the arc grows during the first ticks
        double start = -Math.toDegrees(angle) - sweep / 2;
        g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(new Arc2D.Double(x - reach * 0.8, y - reach * 0.8, reach * 1.6, reach * 1.6,
                start, sweep * progress, Arc2D.OPEN));
        g2.setStroke(new BasicStroke(1.5f));
        g2.draw(new Arc2D.Double(x - reach * 0.55, y - reach * 0.55, reach * 1.1, reach * 1.1,
                start, sweep * progress, Arc2D.OPEN));
        g2.setComposite(oldC);
        g2.setStroke(oldS);
    }

    /** Bullets are drawn above everything on the ground, so they sort by their own y. */
    @Override
    public double getSortY() {
        return y + (kind == GameData.Projectile.SLASH ? 20 : 0);
    }
}
