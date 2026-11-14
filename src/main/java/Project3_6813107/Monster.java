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
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * One enemy. The same class is used for all 5 monster types: the numbers and sprites come
 * from GameData.MonsterType.
 *
 * Each tick GamePanel calls update(), then asks:
 *   tryContactAttack()  -> damage to apply to the player this tick (0 = no bite)
 *   takeProjectile()    -> a Bullet the monster just fired (bandit / toxic), or null
 * When a bullet hits: takeHit(damage, angle, knockback) returns true if that hit killed it;
 * then rollDrops(...) gives the loot. After the death animation and a short fade the monster
 * reports isRemovable() and can be taken out of the list.
 */
public final class Monster extends Entity {

    private static final int CORPSE_TICKS = 150;     // how long the body stays after the death animation
    private static final int FADE_TICKS = 40;        // last part of that time is a fade-out
    private static final int HIT_FLASH_TICKS = 6;

    private final GameData.MonsterType type;
    private final Player target;
    private final BufferedImage[] runFrames, hitFrames, deathFrames;
    private final BufferedImage[] aimFrames;   // bandit only: stands still (idle strip) while aiming

    private int attackCooldown;      // ticks until the next bite is allowed
    private int rangedCooldown;      // ticks until the next shot
    private int hitFlash;            // white flash after being hit
    private int hitAnim;             // ticks left showing the "hit" strip
    private double pushX, pushY;     // knockback velocity, decays every tick
    private int deathTick;           // ticks since death
    private double chaseAngle;       // cached path direction
    private int repathTimer;
    private int stuckTicks;
    private double nudgeAngle;
    private int nudgeTicks;
    private int strafeSign;
    private Bullet pendingShot;
    private int aimWarning;          // ticks of the "!" warning before a bandit fires
    private boolean raiding;         // true = marching on base during Raid; false = outer-zone scavenger
    private boolean provoked;        // true once hit by player or guard
    private double wanderAngle;
    private int wanderTimer;
    private GameData.BeatNoteKind noteKind;
    private int countdownBeats;      // 3..2..1..0 (0 = STRIKE! window for COUNTDOWN_FINISHER)
    private boolean doubleBeatPrimed;
    private int doubleBeatTimer;
    private int beatBounceTick;

    /**
     * @param hpMultiplier wave/difficulty scaling for health (1.0 = base value)
     */
    public Monster(GameData.MonsterType type, double x, double y, GameMap map, Player target, double hpMultiplier) {
        super(x, y, type.hitboxW, type.hitboxH, Math.max(1, (int) Math.round(type.maxHp * hpMultiplier)), type.speed);
        this.type = type;
        this.target = target;
        this.map = map;
        this.runFrames = AssetLoader.loadStrip(type.runStrip(), 8);
        this.hitFrames = AssetLoader.loadStrip(type.hitStrip(), 3);
        this.deathFrames = AssetLoader.loadStrip(type.deathStrip(), 8);
        this.aimFrames = type == GameData.MonsterType.BANDIT_SHOOTER ? AssetLoader.loadStrip("characters/bandit_idle.png", 6) : runFrames;
        // spread out the timers so a big wave does not attack/shoot/re-path on the same tick
        int seed = (int) (x * 7 + y * 13);
        this.animTimer = Math.floorMod(seed, 64);
        this.repathTimer = Math.floorMod(seed, 6);
        this.rangedCooldown = GameData.msToTicks(type.rangedCooldownMs / 2 + Math.floorMod(seed, 900));
        this.strafeSign = (seed & 1) == 0 ? 1 : -1;
        this.raiding = true;
        this.wanderAngle = (Math.floorMod(seed, 360)) * Math.PI / 180.0;
        this.wanderTimer = 40 + Math.floorMod(seed, 50);
        if (type == GameData.MonsterType.ZOMBIE_RUNNER) {
            this.noteKind = GameData.BeatNoteKind.DOUBLE_BEAT;
        } else if (type == GameData.MonsterType.ZOMBIE_TOXIC
                || type == GameData.MonsterType.BANDIT_SHOOTER
                || type == GameData.MonsterType.ZOMBIE_BOSS) {
            this.noteKind = GameData.BeatNoteKind.COUNTDOWN_FINISHER;
            this.countdownBeats = 1 + Math.floorMod(seed, 3);
        } else {
            this.noteKind = GameData.BeatNoteKind.SINGLE_BEAT;
        }
        this.chaseAngle = target == null ? 0 : Math.atan2(target.getY() - y, target.getX() - x);
        if (map != null) {
            pushOutOfWalls();   // safety net if a caller spawns it on a blocked spot
        }
    }

    public GameData.MonsterType getType() {
        return type;
    }

    public boolean isRaiding() {
        return raiding;
    }

    public void setRaiding(boolean raiding) {
        this.raiding = raiding;
    }

    public GameData.BeatNoteKind getNoteKind() {
        return noteKind;
    }

    public void setNoteKind(GameData.BeatNoteKind kind) {
        this.noteKind = kind == null ? GameData.BeatNoteKind.SINGLE_BEAT : kind;
        if (this.noteKind == GameData.BeatNoteKind.COUNTDOWN_FINISHER && countdownBeats <= 0) {
            countdownBeats = 3;
        }
    }

    /** Pulses this monster on every 125 BPM beat, advancing countdown finishers (3 -> 2 -> 1 -> STRIKE!). */
    public void onBeatPulse() {
        if (!alive) {
            return;
        }
        beatBounceTick = 6;
        if (noteKind == GameData.BeatNoteKind.COUNTDOWN_FINISHER) {
            countdownBeats = (countdownBeats <= 0) ? 3 : (countdownBeats - 1);
        }
    }

    public boolean isFinisherReady() {
        return noteKind == GameData.BeatNoteKind.COUNTDOWN_FINISHER && countdownBeats == 0;
    }

    public int getCountdownBeats() {
        return countdownBeats;
    }

    public boolean isDoubleBeatPrimed() {
        return doubleBeatPrimed && doubleBeatTimer > 0;
    }

    public void primeDoubleBeat() {
        doubleBeatPrimed = true;
        doubleBeatTimer = 28;
    }

    public void consumeDoubleBeat() {
        doubleBeatPrimed = false;
        doubleBeatTimer = 0;
    }

    // ------------------------------------------------------------------ simulation
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
        if (beatBounceTick > 0) beatBounceTick--;
        if (doubleBeatTimer > 0 && --doubleBeatTimer == 0) {
            doubleBeatPrimed = false;
        }

        // knockback slides the body back, then fades out
        if (Math.abs(pushX) + Math.abs(pushY) > 0.05) {
            moveWithCollision(pushX, pushY);
            pushX *= 0.7;
            pushY *= 0.7;
        }
        if (target == null || !target.isAlive()) {
            return;
        }
        double dist = distanceTo(target);
        facingLeft = target.getX() < x;
        if (hitAnim > 0) {
            return;   // staggered while the hit animation plays
        }
        if (aimWarning == 0) {
            moveTowardsTarget(dist);   // a bandit stands still while it aims (fair warning)
        }
        updateRangedAttack(dist);
    }

    /** Walks towards the player when raiding or aggroed; otherwise patrols outer zones outside the base. */
    private void moveTowardsTarget(double dist) {
        boolean playerInCamp = map != null && target != null && map.getCampArea().contains((int) target.getX(), (int) target.getY());
        if (!raiding && dist > 230.0) {
            provoked = false;
        }
        double aggroRange = provoked ? 225.0 : 130.0;
        if (!raiding && (playerInCamp || dist > aggroRange)) {
            wanderOuterZone();
            return;
        }
        if (--repathTimer <= 0) {
            repathTimer = 6;
            chaseAngle = map == null ? angleTo(target) : map.getChaseAngle(x, getFeetY() - 4, target.getX(), target.getFeetY() - 4, width);
        }
        double moveAngle = chaseAngle;
        double moveSpeed = speed;
        if (type.preferredDistance > 0) {
            if (dist < type.preferredDistance * 0.75) {
                moveAngle = angleTo(target) + Math.PI;              // too close: back off
            } else if (dist < type.preferredDistance * 1.15) {
                moveAngle = angleTo(target) + strafeSign * Math.PI / 2;   // in range: circle
                moveSpeed = speed * 0.6;
            }
        } else if (dist < 6) {
            return;   // already hugging the player
        }
        if (nudgeTicks > 0) {
            nudgeTicks--;
            moveAngle = nudgeAngle;
        }
        double oldX = x, oldY = y;
        moveWithCollision(Math.cos(moveAngle) * moveSpeed, Math.sin(moveAngle) * moveSpeed);
        if (Math.hypot(x - oldX, y - oldY) > moveSpeed * 0.3) {
            stuckTicks = 0;   // moving (sliding along a wall counts as moving)
            return;
        }
        // barely moving: after a short while, step sideways for a moment so we do not grind on a corner
        stuckTicks++;
        if (stuckTicks > 12) {
            stuckTicks = 0;
            strafeSign = -strafeSign;
            nudgeAngle = chaseAngle + strafeSign * Math.PI / 2;
            nudgeTicks = 18;
            repathTimer = 0;
        }
    }

    /** Roams outer zones during scavenge phase without entering the player's safe base camp. */
    private void wanderOuterZone() {
        if (--wanderTimer <= 0) {
            wanderTimer = 45 + (int) ((animTimer * 17L + (long) x) % 60);
            wanderAngle += (((animTimer & 1) == 0) ? 1 : -1) * (0.7 + ((animTimer % 5) * 0.25));
        }
        if (map != null) {
            Rectangle safe = map.getCampArea();
            safe.grow(28, 28);
            if (safe.contains((int) x, (int) y)) {
                wanderAngle = Math.atan2(y - safe.getCenterY(), x - safe.getCenterX());
            }
        }
        double oldX = x, oldY = y;
        double step = speed * 0.52;
        moveWithCollision(Math.cos(wanderAngle) * step, Math.sin(wanderAngle) * step);
        facingLeft = Math.cos(wanderAngle) < 0;
        if (Math.hypot(x - oldX, y - oldY) < step * 0.25) {
            wanderAngle += Math.PI * 0.65;
            wanderTimer = 25;
        }
    }

    /** Ranged monsters fire when the player is in range and in sight; bandits show a "!" first. */
    private void updateRangedAttack(double dist) {
        if (type.rangedProjectile == null) {
            return;
        }
        boolean playerInCamp = map != null && target != null && map.getCampArea().contains((int) target.getX(), (int) target.getY());
        if (!raiding && playerInCamp) {
            aimWarning = 0;
            return;
        }
        if (aimWarning > 0) {
            aimWarning--;
            if (aimWarning == 0) {
                double a = angleTo(target);
                pendingShot = Bullet.enemyShot(x + Math.cos(a) * 8, y + Math.sin(a) * 8, a,
                        type.rangedProjectile, type.damage, type.rangedRange + 60);
                pendingShot.setMap(map);
                rangedCooldown = GameData.msToTicks(type.rangedCooldownMs);
                AssetLoader.playSound(type.rangedProjectile == GameData.Projectile.TOXIC_SPIT ? "toxic_spit" : "enemy_shoot");
            }
            return;
        }
        boolean inSight = map == null || map.hasClearPath(x, y, target.getX(), target.getY());
        if (rangedCooldown == 0 && dist <= type.rangedRange && inSight) {
            aimWarning = type == GameData.MonsterType.BANDIT_SHOOTER ? 24 : 1;
        }
    }

    /** Bites the player if touching and the bite cooldown is over. Returns the damage to apply (0 = none). */
    public int tryContactAttack() {
        if (!alive || target == null || !target.isAlive() || attackCooldown > 0) {
            return 0;
        }
        Rectangle reach = getBounds();
        reach.grow(3, 3);
        if (!reach.intersects(target.getBounds())) {
            return 0;
        }
        attackCooldown = GameData.msToTicks(type.attackCooldownMs);
        return type.damage;
    }

    /** The projectile fired this tick (once), or null. */
    public Bullet takeProjectile() {
        Bullet b = pendingShot;
        pendingShot = null;
        return b;
    }

    /** True while the bandit is about to shoot (GamePanel may show the "!" bubble / play a sound). */
    public boolean isAiming() {
        return aimWarning > 1;
    }

    /**
     * Applies a hit: damage, a white flash, the hit animation and a push in direction pushAngle.
     * Returns true if this hit killed the monster.
     */
    public boolean takeHit(int damage, double pushAngle, double pushPixels) {
        if (!alive) {
            return false;
        }
        provoked = true;
        int effective = (noteKind == GameData.BeatNoteKind.COUNTDOWN_FINISHER && countdownBeats > 0)
                ? Math.max(1, damage / 2) : damage;
        takeDamage(effective);
        if (!alive && doubleBeatPrimed) {
            alive = true;
            hp = 1;
        }
        hitFlash = HIT_FLASH_TICKS;
        double resist = type == GameData.MonsterType.ZOMBIE_BOSS ? 0.25 : 1.0;
        pushX += Math.cos(pushAngle) * pushPixels * 0.35 * resist;
        pushY += Math.sin(pushAngle) * pushPixels * 0.35 * resist;
        if (alive) {
            if (type != GameData.MonsterType.ZOMBIE_BOSS) {
                hitAnim = 8;
            }
            aimWarning = 0;   // getting shot interrupts aiming
            AssetLoader.playSound("hit");
        } else {
            deathTick = 0;
            pendingShot = null;
            AssetLoader.playSound(type == GameData.MonsterType.ZOMBIE_BOSS ? "boss_roar" : "monster_death");
        }
        return !alive;
    }

    /** Loot for this monster. lootBonusPercent comes from the player's class. */
    public List<DroppedItem> rollDrops(Random rng, int lootBonusPercent) {
        List<DroppedItem> list = new ArrayList<>();
        for (GameData.Drop d : type.getDrops()) {
            int chance = d.chancePercent >= 100 ? 100 : d.chancePercent + lootBonusPercent;
            if (rng.nextInt(100) < chance) {
                int amount = d.min + (d.max > d.min ? rng.nextInt(d.max - d.min + 1) : 0);
                DroppedItem item = new DroppedItem(d.item, amount, x, getFeetY() - 6, target);
                item.setMap(map);
                item.scatter(rng);
                list.add(item);
            }
        }
        return list;
    }

    /** Dead, death animation done and the corpse has faded: take it out of the list. */
    public boolean isRemovable() {
        return !alive && deathTick >= deathAnimTicks() + CORPSE_TICKS;
    }

    /** True while the death animation is still playing (corpse not yet lying still). */
    public boolean isDying() {
        return !alive && deathTick < deathAnimTicks();
    }

    private int deathAnimTicks() {
        return deathFrames.length * 5;
    }

    /** Gently pushes overlapping living monsters apart so a horde does not merge into one sprite. */
    public static void separate(List<Monster> monsters) {
        int n = monsters.size();
        for (int i = 0; i < n; i++) {
            Monster a = monsters.get(i);
            if (!a.alive) continue;
            for (int j = i + 1; j < n; j++) {
                Monster b = monsters.get(j);
                if (!b.alive) continue;
                double dx = b.x - a.x, dy = b.y - a.y;
                double minDist = (a.width + b.width) * 0.5;
                double d2 = dx * dx + dy * dy;
                if (d2 >= minDist * minDist) continue;
                double d = Math.sqrt(d2);
                if (d < 0.01) {
                    dx = (i % 2 == 0) ? 1 : -1;
                    dy = 0;
                    d = 1;
                }
                // weaker than walking speed, so a crowd squeezing through a door still makes progress
                double push = Math.min(0.3, (minDist - d) * 0.2);
                double px = dx / d * push, py = dy / d * push;
                a.moveWithCollision(-px, -py);
                b.moveWithCollision(px, py);
            }
        }
    }

    // ------------------------------------------------------------------ drawing
    @Override
    public void draw(Graphics2D g2) {
        double scale = type.drawScale;
        if (!alive) {
            drawCorpse(g2, scale);
            return;
        }
        drawShadow(g2, (int) (16 * scale));
        BufferedImage frame;
        if (hitAnim > 0) {
            frame = hitFrames[Math.min(hitFrames.length - 1, (8 - hitAnim) / 3)];
        } else if (aimWarning > 0) {
            frame = aimFrames[(animTimer / 6) % aimFrames.length];
        } else {
            frame = runFrames[(animTimer / 5) % runFrames.length];
        }
        drawFrame(g2, frame, scale, facingLeft);
        if (hitFlash > 0 && hitAnim == 0) {
            // the boss has no stagger animation, so it gets a white flash overlay instead
            drawFlash(g2, frame, scale, facingLeft, hitFlash / (float) HIT_FLASH_TICKS);
        }
        if (type == GameData.MonsterType.ZOMBIE_TOXIC) {
            drawToxicDrip(g2);
        }
        if (aimWarning > 1) {
            BufferedImage[] alert = AssetLoader.getAlertFrames();
            int f = Math.min(alert.length - 1, (24 - aimWarning) / 3);
            g2.drawImage(alert[f], (int) x - 10, (int) (getFeetY() - 32 * scale) - 18, null);
        }
        if (hp < maxHp) {
            drawHealthBar(g2, scale);
        }
    }

    /**
     * Draws choreographed neon beat target rings & overhead note badges for each BeatNoteKind:
     * SINGLE_BEAT (cyan), DOUBLE_BEAT (2x rapid magenta), COUNTDOWN_FINISHER (3-2-1-STRIKE! gold),
     * and DANGER_PARRY (red warning when lunging near the player).
     */
    public void drawBeatTargetRing(Graphics2D g2, double beatPhase, boolean perfectWindow) {
        if (!alive) {
            return;
        }
        Stroke old = g2.getStroke();
        boolean nearPlayer = target != null && distanceTo(target) <= 72.0;
        double effectivePhase = (noteKind == GameData.BeatNoteKind.DOUBLE_BEAT)
                ? ((beatPhase * 2.0) % 1.0)
                : beatPhase;
        double pulse = 1.0 - effectivePhase;
        int bounceBoost = beatBounceTick > 0 ? 3 : 0;
        int baseR = (int) Math.round(13.0 * type.drawScale + pulse * 10.0) + bounceBoost;
        int cx = (int) x;
        int cy = (int) getFeetY() - 3;

        Color ringColor;
        String badge = null;
        Color badgeColor;
        if (nearPlayer) {
            ringColor = new Color(255, 65, 85, 225);
            badge = "PARRY!";
            badgeColor = new Color(255, 95, 110);
        } else if (noteKind == GameData.BeatNoteKind.COUNTDOWN_FINISHER) {
            boolean ready = (countdownBeats == 0);
            ringColor = ready ? new Color(255, 225, 55, 240) : new Color(255, 165, 50, 185);
            badge = ready ? "STRIKE!" : String.valueOf(countdownBeats);
            badgeColor = ready ? Theme.GOLD : new Color(255, 185, 70);
        } else if (noteKind == GameData.BeatNoteKind.DOUBLE_BEAT) {
            boolean primed = isDoubleBeatPrimed();
            ringColor = primed ? new Color(255, 225, 65, 240) : new Color(255, 85, 215, 210);
            badge = primed ? "HIT 2X!" : "2X";
            badgeColor = primed ? Theme.GOLD : new Color(255, 120, 230);
        } else {
            ringColor = perfectWindow
                    ? new Color(255, 225, 65, 215)
                    : new Color(70, 225, 255, (int) (105 + 90 * (1.0 - Math.abs(beatPhase - 0.5) * 2.0)));
            badge = perfectWindow ? "BEAT" : null;
            badgeColor = new Color(110, 245, 255);
        }

        g2.setColor(ringColor);
        g2.setStroke(new BasicStroke((perfectWindow || nearPlayer || isFinisherReady() || isDoubleBeatPrimed()) ? 2.2f : 1.4f));
        g2.drawOval(cx - baseR, cy - baseR / 2, baseR * 2, baseR);

        if (noteKind == GameData.BeatNoteKind.DOUBLE_BEAT || noteKind == GameData.BeatNoteKind.COUNTDOWN_FINISHER) {
            int outerR = baseR + 4;
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawOval(cx - outerR, cy - outerR / 2, outerR * 2, outerR);
        }
        if (perfectWindow || isFinisherReady() || nearPlayer) {
            int tick = 5;
            g2.drawLine(cx - baseR - tick, cy, cx - baseR + 2, cy);
            g2.drawLine(cx + baseR - 2, cy, cx + baseR + tick, cy);
        }

        if (badge != null) {
            int topY = (int) (getFeetY() - 32 * type.drawScale) - (hp < maxHp ? 16 : 9);
            int bw = Theme.pixelWidth(badge, 1);
            Theme.drawPixelTextShadow(g2, badge, cx - bw / 2, topY, 1, badgeColor);
        }
        g2.setStroke(old);
    }

    /** Small bobbing green drops around the toxic zombie. */
    private void drawToxicDrip(Graphics2D g2) {
        g2.setColor(new Color(150, 230, 40, 150));
        for (int i = 0; i < 3; i++) {
            double t = (animTimer + i * 17) % 50 / 50.0;
            int px = (int) (x - 7 + i * 7);
            int py = (int) (y - 6 + t * 16);
            g2.fillRect(px, py, 2, 2);
        }
    }

    private void drawHealthBar(Graphics2D g2, double scale) {
        int w = (int) (18 * scale);
        int top = (int) (getFeetY() - 30 * scale) - 4;
        int left = (int) x - w / 2;
        g2.setColor(new Color(20, 10, 10, 200));
        g2.fillRect(left - 1, top - 1, w + 2, 4);
        g2.setColor(type == GameData.MonsterType.ZOMBIE_BOSS ? new Color(230, 120, 20) : new Color(210, 40, 40));
        g2.fillRect(left, top, Math.max(1, w * hp / maxHp), 2);
    }

    /** Death animation, then the last frame lying on the ground, fading at the end. */
    private void drawCorpse(Graphics2D g2, double scale) {
        int animTicks = deathAnimTicks();
        BufferedImage frame = deathFrames[Math.min(deathFrames.length - 1, deathTick / 5)];
        int left = animTicks + CORPSE_TICKS - deathTick;
        float alpha = left < FADE_TICKS ? Math.max(0f, left / (float) FADE_TICKS) : 1f;
        Composite old = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        drawFrame(g2, frame, scale, facingLeft);
        g2.setComposite(old);
    }

    /** Corpses lie flat on the ground, so they are drawn behind standing things. */
    @Override
    public double getSortY() {
        return alive ? getFeetY() : getFeetY() - 24;
    }
}
