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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class Monster extends Entity {

    private static final int CORPSE_TICKS = 150, FADE_TICKS = 40, HIT_FLASH_TICKS = 6;

    private final GameData.MonsterType type;
    private final Player target;
    private final BufferedImage[] runFrames, hitFrames, deathFrames, aimFrames;

    private int attackCooldown, rangedCooldown, hitFlash, hitAnim, deathTick;
    private int repathTimer, stuckTicks, nudgeTicks, strafeSign, aimWarning, slowTicks, lungeTicks, wanderTimer;
    private double pushX, pushY, chaseAngle, nudgeAngle, wanderAngle;
    private double damageScale = 1.0;
    private boolean raiding, provoked;
    private final List<Bullet> pendingShots = new ArrayList<>();

    public Monster(GameData.MonsterType type, double x, double y, GameMap map, Player target, double hpMultiplier) {
        super(x, y, type.hitboxW, type.hitboxH, Math.max(1, (int) Math.round(type.maxHp * hpMultiplier)), type.speed);
        this.type = type;
        this.target = target;
        this.map = map;
        this.runFrames = AssetLoader.loadStrip(type.runStrip(), 8);
        this.hitFrames = AssetLoader.loadStrip(type.hitStrip(), 3);
        this.deathFrames = AssetLoader.loadStrip(type.deathStrip(), 8);
        this.aimFrames = type == GameData.MonsterType.BANDIT_SHOOTER ? AssetLoader.loadStrip("characters/bandit_idle.png", 6) : runFrames;
        int seed = (int) (x * 7 + y * 13);
        this.animTimer = Math.floorMod(seed, 64);
        this.repathTimer = Math.floorMod(seed, 6);
        this.rangedCooldown = GameData.msToTicks(type.rangedCooldownMs / 2 + Math.floorMod(seed, 900));
        this.strafeSign = (seed & 1) == 0 ? 1 : -1;
        this.raiding = type == GameData.MonsterType.ZOMBIE_BOSS;
        this.wanderAngle = Math.floorMod(seed, 360) * Math.PI / 180.0;
        this.wanderTimer = 40 + Math.floorMod(seed, 50);
        this.chaseAngle = target == null ? 0 : Math.atan2(target.getY() - y, target.getX() - x);
        if (map != null) pushOutOfWalls();
    }

    public void applyChamberScaling(double speedScale, double dmgScale) {
        this.speed = type.speed * Math.max(0.4, speedScale);
        this.damageScale = Math.max(0.25, dmgScale);
    }

    public GameData.MonsterType getType() { return type; }
    public void setRaiding(boolean raiding) { this.raiding = raiding; }
    public Bullet takeProjectile() { return pendingShots.isEmpty() ? null : pendingShots.remove(0); }
    public void applySlow(int ticks) { slowTicks = Math.max(slowTicks, ticks); }
    private int deathAnimTicks() { return deathFrames.length * 5; }
    public boolean isRemovable() { return !alive && deathTick >= deathAnimTicks() + CORPSE_TICKS; }
    @Override public double getSortY() { return alive ? getFeetY() : getFeetY() - 24; }

    @Override
    public void update() {
        animTimer++;
        if (!alive) {
            deathTick++;
            return;
        }
        if (attackCooldown > 0) attackCooldown--;
        if (rangedCooldown > 0) rangedCooldown--;
        if (hitFlash > 0) hitFlash--;
        if (hitAnim > 0) hitAnim--;
        if (slowTicks > 0) slowTicks--;
        if (lungeTicks > 0) lungeTicks--;
        if (Math.abs(pushX) + Math.abs(pushY) > 0.05) {
            moveWithCollision(pushX, pushY);
            pushX *= 0.7;
            pushY *= 0.7;
        }
        if (target == null || !target.isAlive()) return;
        double dist = distanceTo(target);
        facingLeft = target.getX() < x;
        if (hitAnim > 0) return;
        if (aimWarning == 0) moveTowardsTarget(dist);
        updateRangedAttack(dist);
    }

    private void moveTowardsTarget(double dist) {
        if (!raiding && dist > 260.0) provoked = false;
        double aggroRange = (map != null && !map.hasSafeCamp()) ? 9999.0 : (provoked ? 225.0 : 130.0);
        if (!raiding && (targetIsInCamp() || dist > aggroRange)) {
            wanderOuterZone();
            return;
        }
        if (--repathTimer <= 0) {
            repathTimer = 6;
            chaseAngle = map == null ? angleTo(target) : map.getChaseAngle(x, getFeetY() - 4, target.getX(), target.getFeetY() - 4, width);
        }
        if (type == GameData.MonsterType.ZOMBIE_RUNNER && lungeTicks == 0
                && dist < 165.0 && dist > 28.0 && ((animTimer + (int) Math.abs(x)) % 58 == 0)) {
            lungeTicks = 24;
        }
        double moveSpeed = (slowTicks > 0 ? speed * 0.65 : speed) * (lungeTicks > 0 ? 1.65 : 1.0)
                * ((hp * 100 < maxHp * 50) ? 1.28 : 1.0);
        double moveAngle = chaseAngle;
        if (type.preferredDistance > 0) {
            if (dist < type.preferredDistance * 0.75) {
                moveAngle = angleTo(target) + Math.PI;
            } else if (dist < type.preferredDistance * 1.15) {
                moveAngle = angleTo(target) + strafeSign * Math.PI / 2;
                moveSpeed *= 0.6;
            }
        } else if (dist < 6) {
            return;
        }
        if (nudgeTicks > 0) {
            nudgeTicks--;
            moveAngle = nudgeAngle;
        }
        double oldX = x, oldY = y;
        moveWithCollision(Math.cos(moveAngle) * moveSpeed, Math.sin(moveAngle) * moveSpeed);
        if (Math.hypot(x - oldX, y - oldY) > moveSpeed * 0.3) {
            stuckTicks = 0;
            return;
        }
        if (++stuckTicks > 12) {
            stuckTicks = 0;
            strafeSign = -strafeSign;
            nudgeAngle = chaseAngle + strafeSign * Math.PI / 2;
            nudgeTicks = 18;
            repathTimer = 0;
        }
    }

    private void wanderOuterZone() {
        if (--wanderTimer <= 0) {
            wanderTimer = 45 + (int) ((animTimer * 17L + (long) x) % 60);
            wanderAngle += (((animTimer & 1) == 0) ? 1 : -1) * (0.7 + (animTimer % 5) * 0.25);
        }
        if (map != null) {
            Rectangle safe = map.getCampArea();
            safe.grow(28, 28);
            if (safe.contains((int) x, (int) y)) {
                wanderAngle = Math.atan2(y - safe.getCenterY(), x - safe.getCenterX());
            }
        }
        double oldX = x, oldY = y, step = speed * 0.52;
        moveWithCollision(Math.cos(wanderAngle) * step, Math.sin(wanderAngle) * step);
        facingLeft = Math.cos(wanderAngle) < 0;
        if (Math.hypot(x - oldX, y - oldY) < step * 0.25) {
            wanderAngle += Math.PI * 0.65;
            wanderTimer = 25;
        }
    }

    private void updateRangedAttack(double dist) {
        if (type.rangedProjectile == null) return;
        if (!raiding && targetIsInCamp()) {
            aimWarning = 0;
            return;
        }
        if (aimWarning > 0) {
            if (--aimWarning == 0) fireVolley(angleTo(target));
            return;
        }
        boolean inSight = map == null || map.hasClearPath(x, y, target.getX(), target.getY());
        if (rangedCooldown == 0 && dist <= type.rangedRange && inSight) {
            aimWarning = type == GameData.MonsterType.BANDIT_SHOOTER ? 7 : 1;
        }
    }

    private void fireVolley(double angle) {
        switch (type) {
            case ZOMBIE_BOSS -> {
                queueEnemyVolley(angle, 7, 0.24, Math.max(6, (int) Math.round((type.damage / 2.0) * damageScale)), 10, "boss_roar");
                lungeTicks = 38;
            }
            case ZOMBIE_TOXIC -> queueEnemyVolley(angle, 3, 0.20, Math.max(6, (int) Math.round((type.damage * 0.75) * damageScale)), 8, "toxic_spit");
            case BANDIT_SHOOTER -> queueEnemyVolley(angle, 2, 0.14, Math.max(7, (int) Math.round((type.damage * 0.80) * damageScale)), 8, "enemy_shoot");
            default -> queueEnemyVolley(angle, 1, 0.0, Math.max(5, (int) Math.round(type.damage * damageScale)), 8,
                    type.rangedProjectile == GameData.Projectile.TOXIC_SPIT ? "toxic_spit" : "enemy_shoot");
        }
    }

    private boolean targetIsInCamp() {
        return map != null && map.hasSafeCamp() && target != null && map.getCampArea().contains((int) target.getX(), (int) target.getY());
    }

    private void queueEnemyVolley(double centerAngle, int count, double stepRad, int damage, double spawnOffset, String sound) {
        double start = -0.5 * (count - 1);
        for (int i = 0; i < count; i++) {
            double ang = centerAngle + (start + i) * stepRad;
            Bullet b = Bullet.enemyShot(x + Math.cos(ang) * spawnOffset, y + Math.sin(ang) * spawnOffset,
                    ang, type.rangedProjectile, damage, type.rangedRange + 60);
            b.setMap(map);
            pendingShots.add(b);
        }
        rangedCooldown = GameData.msToTicks(type.rangedCooldownMs);
        AssetLoader.playSound(sound);
    }

    public int tryContactAttack() {
        if (!alive || target == null || !target.isAlive() || attackCooldown > 0) return 0;
        Rectangle reach = getBounds();
        reach.grow(3, 3);
        if (!reach.intersects(target.getBounds())) return 0;
        attackCooldown = GameData.msToTicks(type.attackCooldownMs);
        return Math.max(1, (int) Math.round(type.damage * damageScale));
    }

    public boolean takeHit(int damage, double pushAngle, double pushPixels) {
        if (!alive) return false;
        provoked = true;
        takeDamage(damage);
        hitFlash = HIT_FLASH_TICKS;
        double resist = (type == GameData.MonsterType.ZOMBIE_BOSS || type == GameData.MonsterType.ZOMBIE_RUNNER) ? 0.10 : 0.20;
        pushX += Math.cos(pushAngle) * pushPixels * resist;
        pushY += Math.sin(pushAngle) * pushPixels * resist;
        if (alive) {
            if (type != GameData.MonsterType.ZOMBIE_BOSS && type != GameData.MonsterType.ZOMBIE_RUNNER) hitAnim = 2;
            AssetLoader.playSound("hit");
        } else {
            deathTick = 0;
            pendingShots.clear();
            AssetLoader.playSound(type == GameData.MonsterType.ZOMBIE_BOSS ? "boss_roar" : "monster_death");
        }
        return !alive;
    }

    public List<DroppedItem> rollDrops(Random rng, int lootBonusPercent) {
        List<DroppedItem> list = new ArrayList<>();
        GameData.Drop[] pool = type.getDrops();
        int start = pool.length > 0 ? rng.nextInt(pool.length) : 0;
        for (int offset = 0; offset < pool.length; offset++) {
            GameData.Drop d = pool[(start + offset) % pool.length];
            int chance = d.chancePercent >= 100 ? 100
                    : Math.min(85, (int) Math.round(d.chancePercent * (1.0 + lootBonusPercent / 100.0)));
            if (rng.nextInt(100) < chance) {
                int amount = d.min + (d.max > d.min ? rng.nextInt(d.max - d.min + 1) : 0);
                DroppedItem item = new DroppedItem(d.item, amount, x, getFeetY() - 6, target);
                item.setMap(map);
                item.scatter(rng);
                list.add(item);
                if (type != GameData.MonsterType.ZOMBIE_BOSS) break;
            }
        }
        return list;
    }

    public static void separate(List<Monster> monsters) {
        int n = monsters.size();
        for (int i = 0; i < n; i++) {
            Monster a = monsters.get(i);
            if (!a.alive) continue;
            for (int j = i + 1; j < n; j++) {
                Monster b = monsters.get(j);
                if (!b.alive) continue;
                double dx = b.x - a.x, dy = b.y - a.y, minDist = (a.width + b.width) * 0.5, d2 = dx * dx + dy * dy;
                if (d2 >= minDist * minDist) continue;
                double d = Math.sqrt(d2);
                if (d < 0.01) {
                    dx = (i % 2 == 0) ? 1 : -1;
                    dy = 0;
                    d = 1;
                }
                double push = Math.min(0.3, (minDist - d) * 0.2), px = dx / d * push, py = dy / d * push;
                a.moveWithCollision(-px, -py);
                b.moveWithCollision(px, py);
            }
        }
    }

    @Override
    public void draw(Graphics2D g2) {
        double scale = type.drawScale;
        if (!alive) {
            drawCorpse(g2, scale);
            return;
        }
        drawShadow(g2, (int) (16 * scale));
        BufferedImage frame = hitAnim > 0 ? hitFrames[Math.min(hitFrames.length - 1, (8 - hitAnim) / 3)]
                : aimWarning > 0 ? aimFrames[(animTimer / 6) % aimFrames.length]
                : runFrames[(animTimer / 5) % runFrames.length];
        drawFrame(g2, frame, scale, facingLeft);
        if (hitFlash > 0 && hitAnim == 0) drawFlash(g2, frame, scale, facingLeft, hitFlash / (float) HIT_FLASH_TICKS);
        if (type == GameData.MonsterType.ZOMBIE_TOXIC) drawToxicDrip(g2);
        if (aimWarning > 1) {
            BufferedImage[] alert = AssetLoader.getAlertFrames();
            g2.drawImage(alert[Math.min(alert.length - 1, (24 - aimWarning) / 3)], (int) x - 10, (int) (getFeetY() - 32 * scale) - 18, null);
        }
        if (hp < maxHp) drawHealthBar(g2, scale);
    }

    private void drawToxicDrip(Graphics2D g2) {
        g2.setColor(new Color(150, 230, 40, 150));
        for (int i = 0; i < 3; i++) {
            double t = (animTimer + i * 17) % 50 / 50.0;
            g2.fillRect((int) (x - 7 + i * 7), (int) (y - 6 + t * 16), 2, 2);
        }
    }

    private void drawHealthBar(Graphics2D g2, double scale) {
        int w = (int) (18 * scale), top = (int) (getFeetY() - 30 * scale) - 4, left = (int) x - w / 2;
        g2.setColor(new Color(20, 10, 10, 200));
        g2.fillRect(left - 1, top - 1, w + 2, 4);
        g2.setColor(type == GameData.MonsterType.ZOMBIE_BOSS ? new Color(230, 120, 20) : new Color(210, 40, 40));
        g2.fillRect(left, top, Math.max(1, w * hp / maxHp), 2);
    }

    private void drawCorpse(Graphics2D g2, double scale) {
        BufferedImage frame = deathFrames[Math.min(deathFrames.length - 1, deathTick / 5)];
        int left = deathAnimTicks() + CORPSE_TICKS - deathTick;
        float alpha = left < FADE_TICKS ? Math.max(0f, left / (float) FADE_TICKS) : 1f;
        Composite old = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        drawFrame(g2, frame, scale, facingLeft);
        g2.setComposite(old);
    }
}
