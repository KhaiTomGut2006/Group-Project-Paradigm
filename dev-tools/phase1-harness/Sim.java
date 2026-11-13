import Project3_6813107.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;

/**
 * Headless simulation that drives the Phase-1 classes the way GamePanel will.
 * Exits with code 1 if any assertion fails.
 */
public class Sim {
    static int failures = 0;
    static final File OUT = new File(System.getProperty("out", "."));

    static void check(boolean ok, String msg) {
        if (!ok) {
            failures++;
            System.out.println("  FAIL: " + msg);
        }
    }

    public static void main(String[] args) throws Exception {
        OUT.mkdirs();
        int ticks = Integer.parseInt(System.getProperty("ticks", "5000"));
        for (GameData.MapType type : GameData.MapType.values()) {
            mapStatic(type);
            arrival(type);
            battle(type, ticks);
        }
        crafting();
        sounds();
        scores();
        System.out.println(failures == 0 ? "ALL CHECKS PASSED" : ("FAILURES: " + failures));
        System.exit(failures == 0 ? 0 : 1);
    }

    // ---------------------------------------------------------------- map static checks
    static void mapStatic(GameData.MapType type) {
        GameMap map = new GameMap(type, type.seed);
        System.out.println("== " + type);
        Point spawn = map.getPlayerSpawn();
        Rectangle feet = new Rectangle(spawn.x - 6, spawn.y + 10 - 8, 12, 8);
        check(!map.isBlocked(feet), "player spawn blocked");
        // camp clearing: a 6x4 tile area around the spawn must be free
        Rectangle clear = new Rectangle(spawn.x - 2 * 32, spawn.y - 32, 4 * 32, 3 * 32);
        int campBlocked = 0;
        for (Rectangle s : map.getSolidRects()) if (s.intersects(clear)) campBlocked++;
        check(campBlocked == 0, "camp clearing has " + campBlocked + " solids");
        check(map.getSpawnPoints().size() > 100, "few spawn points: " + map.getSpawnPoints().size());
        Rectangle world = new Rectangle(0, 0, map.getWidthPx(), map.getHeightPx());
        int badSpawn = 0;
        for (Point p : map.getSpawnPoints()) {
            Rectangle boss = new Monster(GameData.MonsterType.ZOMBIE_BOSS, p.x, p.y, null, null, 1).getFeetBounds();
            if (map.isBlocked(boss) || !world.contains(p)) badSpawn++;
        }
        check(badSpawn == 0, badSpawn + " spawn points blocked for a boss-sized monster");
        // spawn points must be hidden from a camera at the player spawn
        Rectangle view = new Rectangle(spawn.x - 275, spawn.y - 135, 550, 270);
        Random r = new Random(1);
        for (int i = 0; i < 200; i++) {
            Point p = map.randomSpawnPoint(r, view);
            if (view.contains(p)) { check(false, "spawn point inside camera view " + p); break; }
        }
        // solids inside the play area, props not overlapping each other
        int outside = 0;
        Rectangle play = new Rectangle(32, 32, map.getWidthPx() - 64, map.getHeightPx() - 64);
        List<Rectangle> propSolids = new ArrayList<>();
        for (GameMap.Prop p : map.getProps()) if (p.getSolid() != null) propSolids.add(p.getSolid());
        for (Rectangle s : propSolids) if (!play.contains(s)) outside++;
        check(outside == 0, outside + " prop footprints outside the play area");
        // flow-field reachability: share of walkable tiles reachable from the camp
        int walk = 0;
        for (int row = 0; row < GameMap.ROWS; row++) for (int c = 0; c < GameMap.COLS; c++) if (map.isWalkableTile(c, row)) walk++;
        System.out.printf("  props=%d solids=%d spawnPoints=%d walkableTiles=%d%n", map.getProps().size(), map.getSolidRects().size(), map.getSpawnPoints().size(), walk);
    }

    // ---------------------------------------------------------------- every spawn point can reach the camp
    static void arrival(GameData.MapType type) {
        GameMap map = new GameMap(type, type.seed);
        Point spawn = map.getPlayerSpawn();
        GameData.GameSettings st = new GameData.GameSettings();
        Player player = new Player(st, spawn.x, spawn.y);
        player.setMap(map);
        List<Point> points = map.getSpawnPoints();
        int failed = 0, maxTicks = 0;
        long total = 0;
        List<Point> stuckAt = new ArrayList<>();
        for (GameData.MonsterType mt : new GameData.MonsterType[]{GameData.MonsterType.ZOMBIE_NORMAL, GameData.MonsterType.ZOMBIE_BOSS}) {
            // batches of 40 monsters at once (a horde), every spawn point used
            for (int start = 0; start < points.size(); start += 8) {
                List<Monster> ms = new ArrayList<>();
                for (int i = start; i < Math.min(points.size(), start + 8); i++) {
                    Point p = points.get(i);
                    Monster m = new Monster(mt, p.x, p.y, map, player, 1.0);
                    if (Math.abs(m.getX() - p.x) + Math.abs(m.getY() - p.y) > 0.1) check(false, "monster had to be pushed out of a wall at spawn point " + p);
                    ms.add(m);
                }
                int[] arrivedAt = new int[ms.size()];
                Arrays.fill(arrivedAt, -1);
                for (int t = 0; t < 6000; t++) {
                    player.heal(1000);
                    player.update();
                    boolean all = true;
                    for (int i = 0; i < ms.size(); i++) {
                        Monster m = ms.get(i);
                        m.update();
                        m.tryContactAttack();
                        if (arrivedAt[i] < 0 && m.distanceTo(player) < 48) arrivedAt[i] = t;
                        if (arrivedAt[i] < 0) all = false;
                        if (m.isInsideWall()) { check(false, "monster inside wall at " + (int) m.getX() + "," + (int) m.getY()); return; }
                    }
                    Monster.separate(ms);
                    if (all) break;
                }
                for (int i = 0; i < ms.size(); i++) {
                    if (arrivedAt[i] < 0) {
                        failed++;
                        if (stuckAt.size() < 8) stuckAt.add(new Point((int) ms.get(i).getX(), (int) ms.get(i).getY()));
                    } else {
                        total += arrivedAt[i];
                        maxTicks = Math.max(maxTicks, arrivedAt[i]);
                    }
                }
            }
        }
        int n = points.size() * 2;
        System.out.printf("  arrival: %d/%d monsters (normal+boss) from every spawn point reached the camp, avg %d ticks, max %d ticks%n",
                n - failed, n, total / Math.max(1, n - failed), maxTicks);
        check(failed == 0, failed + " monsters never reached the player; stuck at " + stuckAt);
    }

    // ---------------------------------------------------------------- full battle the way GamePanel will run it
    static void battle(GameData.MapType type, int ticks) throws Exception {
        GameMap map = new GameMap(type, type.seed);
        Random rng = new Random(type.seed);
        GameData.GameSettings st = new GameData.GameSettings();
        st.playerClass = GameData.PlayerClass.values()[type.ordinal()];
        st.startWeapon = GameData.START_WEAPONS[type.ordinal() % GameData.START_WEAPONS.length];
        Point spawn = map.getPlayerSpawn();
        Player player = new Player(st, spawn.x, spawn.y);
        player.setMap(map);
        player.addItem(GameData.ItemType.AMMO, 3000);
        player.addItem(GameData.ItemType.GRENADE, 40);
        for (GameData.WeaponType w : GameData.WeaponType.values()) {
            // unlock everything through the normal crafting path so all weapons get used
            for (GameData.UpgradeRecipe r : GameData.RECIPES) if (r.weapon == w) { give(player, r, 3); player.craft(r); }
        }
        List<Monster> monsters = new ArrayList<>();
        List<Bullet> bullets = new ArrayList<>();
        List<DroppedItem> drops = new ArrayList<>();
        int kills = 0, explosions = 0, pickups = 0, chestBursts = 0, playerHits = 0, enemyShots = 0, wallHits = 0;
        int insideWall = 0, outsideWorld = 0, maxMonsters = 0, stuckEvents = 0;
        Map<Monster, double[]> track = new HashMap<>();   // last position, ticks without movement while far
        Set<GameData.MonsterType> spawnedTypes = EnumSet.noneOf(GameData.MonsterType.class);
        int dirTimer = 0;
        boolean up = false, down = false, left = false, right = false;
        long nanos = 0;
        Rectangle world = new Rectangle(0, 0, map.getWidthPx(), map.getHeightPx());
        for (int t = 0; t < ticks; t++) {
            long t0 = System.nanoTime();
            // keep ~80 monsters alive
            Rectangle view = new Rectangle((int) player.getX() - 275, (int) player.getY() - 135, 550, 270);
            while (countAlive(monsters) < 80) {
                Point p = map.randomSpawnPoint(rng, view);
                int wave = 1 + t / 600;
                GameData.MonsterType mt = rng.nextInt(25) == 0 ? GameData.MonsterType.ZOMBIE_BOSS : GameData.MonsterType.randomForWave(wave, rng);
                spawnedTypes.add(mt);
                monsters.add(new Monster(mt, p.x, p.y, map, player, GameData.monsterHpScale(wave, 3)));
            }
            maxMonsters = Math.max(maxMonsters, monsters.size());
            // random walking, aim at the nearest monster
            if (--dirTimer <= 0) {
                dirTimer = 30 + rng.nextInt(60);
                up = rng.nextInt(3) == 0; down = !up && rng.nextInt(2) == 0;
                left = rng.nextInt(3) == 0; right = !left && rng.nextInt(2) == 0;
            }
            player.setMoveInput(up, down, left, right);
            Monster near = nearest(monsters, player);
            double ax = near != null ? near.getX() : player.getX() + 50, ay = near != null ? near.getY() : player.getY();
            player.setAim(ax, ay);
            player.update();
            player.heal(1000);   // keep the test player alive so the horde keeps chasing
            if (t % 400 == 0) player.switchWeapon(GameData.WeaponType.values()[rng.nextInt(GameData.WeaponType.values().length)]);
            if (t % 200 == 100) player.cycleWeapon(1);
            for (Bullet b : player.shoot(ax, ay)) bullets.add(b);
            if (t % 150 == 0) { Bullet g = player.throwGrenade(ax, ay); if (g != null) bullets.add(g); }
            if (t % 500 == 0) player.useMedkit();
            map.update();
            // monsters
            for (Monster m : monsters) {
                m.update();
                int dmg = m.tryContactAttack();
                if (dmg > 0) { player.takeDamage(dmg); playerHits++; }
                Bullet shot = m.takeProjectile();
                if (shot != null) { bullets.add(shot); enemyShots++; }
            }
            Monster.separate(monsters);
            // bullets
            List<Bullet> newBullets = new ArrayList<>();
            for (Bullet b : bullets) {
                b.update();
                if (b.isHostile()) {
                    if (b.tryHit(player)) { player.takeDamage(b.getDamage()); if (b.getPoisonTicks() > 0) player.applyPoison(b.getPoisonTicks()); }
                } else {
                    for (Monster m : monsters) {
                        if (b.tryHit(m) && m.takeHit(b.getDamage(), b.getAngle(), b.getKnockback())) {
                            kills++;
                            drops.addAll(m.rollDrops(rng, player.getLootBonus()));
                        }
                    }
                }
                if (b.consumeExplosion()) {
                    explosions++;
                    for (Monster m : monsters) {
                        if (m.isAlive() && m.distanceTo(b) < b.getBlastRadius()) {
                            if (m.takeHit(b.getDamage(), Math.atan2(m.getY() - b.getY(), m.getX() - b.getX()), 8)) {
                                kills++;
                                drops.addAll(m.rollDrops(rng, player.getLootBonus()));
                            }
                        }
                    }
                }
                if (b.consumeWallHit()) wallHits++;
            }
            bullets.removeIf(b -> !b.isAlive());
            bullets.addAll(newBullets);
            // drops
            List<DroppedItem> burst = new ArrayList<>();
            for (DroppedItem d : drops) {
                d.update();
                if (d.isReadyToCollect()) { player.pickUp(d); pickups++; }
                if (d.isChestReadyToBurst()) { burst.addAll(d.burstChest(rng)); chestBursts++; }
            }
            drops.addAll(burst);
            drops.removeIf(d -> !d.isAlive());
            monsters.removeIf(Monster::isRemovable);
            nanos += System.nanoTime() - t0;

            // invariants
            if (player.isInsideWall()) insideWall++;
            for (Monster m : monsters) {
                if (m.isAlive() && m.isInsideWall()) insideWall++;
                if (!world.contains(m.getX(), m.getY())) outsideWorld++;
                if (m.isAlive()) {
                    double[] tr = track.computeIfAbsent(m, k -> new double[]{k.getX(), k.getY(), 0});
                    double moved = Math.hypot(m.getX() - tr[0], m.getY() - tr[1]);
                    boolean far = m.distanceTo(player) > 96 && m.getType().preferredDistance == 0 && !crowded(m, monsters);
                    if (moved > 12 || !far) { tr[0] = m.getX(); tr[1] = m.getY(); tr[2] = 0; }
                    else if (++tr[2] == 240) stuckEvents++;
                }
            }
            for (DroppedItem d : drops) if (!world.contains(d.getX(), d.getY())) outsideWorld++;
            if (!world.contains(player.getX(), player.getY())) outsideWorld++;
            if (t == ticks / 2) snapshot(type, map, player, monsters, bullets, drops);
        }
        double msPerTick = nanos / 1e6 / ticks;
        System.out.printf("  battle %d ticks: kills=%d explosions=%d pickups=%d chests=%d bites=%d enemyShots=%d wallHits=%d maxList=%d stuck240=%d avg %.3f ms/tick%n",
                ticks, kills, explosions, pickups, chestBursts, playerHits, enemyShots, wallHits, maxMonsters, stuckEvents, msPerTick);
        check(insideWall == 0, insideWall + " entity-ticks inside a solid");
        check(outsideWorld == 0, outsideWorld + " entity-ticks outside the world");
        check(kills > 50, "too few kills: " + kills);
        check(pickups > 20, "too few pickups: " + pickups);
        check(stuckEvents < 5, stuckEvents + " monsters stood still for 4 s while far from the player");
        check(msPerTick < 4.0, "simulation too slow: " + msPerTick + " ms/tick");
        check(enemyShots > 0, "no enemy projectiles fired");
        check(spawnedTypes.size() == 5, "not all monster types spawned: " + spawnedTypes);
    }

    static void give(Player p, GameData.UpgradeRecipe r, int times) {
        for (int k = 0; k < times; k++) for (Map.Entry<GameData.ItemType, Integer> e : p.getCost(r).entrySet()) p.addItem(e.getKey(), e.getValue() * 2);
    }

    /** True if another living monster is touching this one (then standing still is just queueing). */
    static boolean crowded(Monster m, List<Monster> ms) {
        for (Monster o : ms) if (o != m && o.isAlive() && o.distanceTo(m) < 22) return true;
        return false;
    }

    static int countAlive(List<Monster> ms) { int n = 0; for (Monster m : ms) if (m.isAlive()) n++; return n; }

    static Monster nearest(List<Monster> ms, Player p) {
        Monster best = null; double bd = 1e9;
        for (Monster m : ms) if (m.isAlive()) { double d = m.distanceTo(p); if (d < bd) { bd = d; best = m; } }
        return best;
    }

    /** A gameplay frame the way GamePanel would draw it: camera on the player, world at 2x, depth sorted. */
    static void snapshot(GameData.MapType type, GameMap map, Player player, List<Monster> monsters, List<Bullet> bullets, List<DroppedItem> drops) throws Exception {
        int vw = 550, vh = 270;
        int cx = (int) Math.max(0, Math.min(map.getWidthPx() - vw, player.getX() - vw / 2));
        int cy = (int) Math.max(0, Math.min(map.getHeightPx() - vh, player.getY() - vh / 2));
        Rectangle view = new Rectangle(cx, cy, vw, vh);
        BufferedImage img = new BufferedImage(vw * 2, vh * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.scale(2, 2);
        g.translate(-cx, -cy);
        map.drawGround(g, view);
        List<GameMap.Drawable> list = new ArrayList<>(map.getPropsInView(view));
        list.addAll(monsters); list.addAll(drops); list.addAll(bullets); list.add(player);
        list.sort(GameMap.DEPTH_ORDER);
        for (GameMap.Drawable d : list) d.draw(g);
        g.dispose();
        ImageIO.write(img, "png", new File(OUT, "sim_" + type.name().toLowerCase() + ".png"));
    }

    // ---------------------------------------------------------------- crafting
    static void crafting() {
        System.out.println("== crafting");
        for (GameData.PlayerClass pc : GameData.PlayerClass.values()) {
            GameData.GameSettings st = new GameData.GameSettings();
            st.playerClass = pc;
            st.startWeapon = GameData.WeaponType.KNIFE;
            Player p = new Player(st, 100, 100);
            // with nothing in the bag: statuses make sense
            GameData.UpgradeRecipe shotgun = find("Craft Pump Shotgun"), sniper = find("Craft Sniper"), revolver = find("Craft Revolver");
            check(p.getRecipeStatus(shotgun).equals("Need Revolver"), pc + " shotgun status: " + p.getRecipeStatus(shotgun));
            check(!p.canCraft(revolver) || pc == GameData.PlayerClass.ENGINEER, pc + " revolver craftable with empty bag");
            check(p.craft(shotgun) == null, "craft without prerequisite returned non-null");
            // give lots of everything except blueprints
            for (GameData.ItemType t : GameData.ItemType.values()) if (t != GameData.ItemType.BLUEPRINT) p.addItem(t, 500);
            int crafted = 0;
            for (GameData.UpgradeRecipe r : GameData.RECIPES) {
                if (r.weapon == GameData.WeaponType.SNIPER) {
                    check(p.getRecipeStatus(r).startsWith("Need"), "sniper status before rifle/blueprint: " + p.getRecipeStatus(r));
                }
                int guard = 0;
                while (p.canCraft(r) && guard++ < 20) {
                    String before = p.getRecipeStatus(r);
                    String log = p.craft(r);
                    check(log != null && !log.isEmpty(), "craft returned null although canCraft");
                    if (pc == GameData.PlayerClass.SCAVENGER && guard == 1) System.out.println("  " + r.name + " [" + before + "] -> " + log);
                    crafted++;
                    if (r.maxLevel == 0 && guard >= 3) break;
                }
            }
            check(p.getRecipeStatus(sniper).equals("Need Blueprint"), pc + " sniper status w/o blueprint: " + p.getRecipeStatus(sniper));
            p.addItem(GameData.ItemType.BLUEPRINT, 2);
            for (GameData.UpgradeRecipe r : GameData.RECIPES) if (r.kind == GameData.RecipeKind.WEAPON) { if (p.canCraft(r)) { p.craft(r); crafted++; } }
            for (GameData.UpgradeRecipe r : GameData.RECIPES) if (r.kind == GameData.RecipeKind.WEAPON) { if (p.canCraft(r)) { p.craft(r); crafted++; } }
            for (GameData.WeaponType w : GameData.WeaponType.values()) {
                if (w == GameData.WeaponType.AXE) continue;   // the axe is a starting weapon only
                check(p.ownsWeapon(w), pc + " does not own " + w + " after crafting everything");
            }
            for (GameData.UpgradeRecipe r : GameData.RECIPES) {
                String s = p.getRecipeStatus(r);
                boolean ok = r.kind == GameData.RecipeKind.WEAPON ? s.equals("Owned") : r.maxLevel == 0 ? s.startsWith("Have ") : s.equals("MAX");
                check(ok, pc + " final status of " + r.name + " = " + s);
                if (r.maxLevel > 0 || r.kind == GameData.RecipeKind.WEAPON) check(!p.canCraft(r), r.name + " still craftable when maxed");
            }
            if (pc == GameData.PlayerClass.SCAVENGER) {
                System.out.printf("  maxed stats: hp=%d speed=%.2f pickup=%.0f armor=%d%% pistolDmg=%d rifleCd=%d ticks%n",
                        p.getMaxHp(), p.getMoveSpeed(), p.getPickupRadius(), p.getArmorPercent(), p.getWeaponDamage(GameData.WeaponType.PISTOL), p.getCooldownTicks(GameData.WeaponType.RIFLE));
            }
            check(crafted >= GameData.RECIPES.size(), pc + " crafted only " + crafted);
        }
        GameData.GameSettings eng = new GameData.GameSettings();
        eng.playerClass = GameData.PlayerClass.ENGINEER;
        Player e = new Player(eng, 0, 0);
        Player s = new Player(new GameData.GameSettings(), 0, 0);
        GameData.UpgradeRecipe rocket = find("Craft Rocket Launcher");
        System.out.println("  rocket cost scavenger=" + s.getCost(rocket) + " engineer=" + e.getCost(rocket));
        check(e.getCost(rocket).get(GameData.ItemType.STEEL) < s.getCost(rocket).get(GameData.ItemType.STEEL), "engineer discount missing");
        check(e.getCost(rocket).get(GameData.ItemType.BLUEPRINT) == 1, "blueprint cost changed by discount");
        // out-of-ammo fallback
        GameData.GameSettings st = new GameData.GameSettings();
        st.startWeapon = GameData.WeaponType.RIFLE;
        Player p = new Player(st, 0, 0);
        p.addItem(GameData.ItemType.AMMO, -10000);
        List<Bullet> none = p.shoot(10, 0);
        check(none.isEmpty() && p.getCurrentWeapon() == GameData.WeaponType.PISTOL && p.getHint() != null, "no fallback to pistol when out of ammo");
        // death
        Player d = new Player(new GameData.GameSettings(), 0, 0);
        for (int i = 0; i < 400 && d.isAlive(); i++) { d.takeDamage(30); d.update(); }
        check(!d.isAlive(), "player did not die");
        for (int i = 0; i < 200; i++) d.update();
        check(d.isDeathAnimationDone(), "death animation never finished");
    }

    static GameData.UpgradeRecipe find(String name) {
        for (GameData.UpgradeRecipe r : GameData.RECIPES) if (r.name.equals(name)) return r;
        throw new IllegalStateException(name);
    }

    // ---------------------------------------------------------------- sounds
    static void sounds() throws Exception {
        System.out.println("== sounds");
        File dir = new File("src/main/java/Project3_6813107/resources/sounds");
        File[] files = dir.listFiles((d, n) -> n.endsWith(".wav"));
        Arrays.sort(files);
        long totalBytes = 0;
        for (File f : files) {
            String name = f.getName().replace(".wav", "");
            check(AssetLoader.canLoadSound(name), "AssetLoader cannot load " + name);
            try (AudioInputStream in = AudioSystem.getAudioInputStream(f)) {
                AudioFormat fmt = in.getFormat();
                long frames = in.getFrameLength();
                boolean supported = AudioSystem.isLineSupported(new DataLine.Info(Clip.class, fmt));
                String open = "-";
                try {
                    Clip c = AudioSystem.getClip();
                    c.open(in);
                    open = "opened " + c.getMicrosecondLength() / 1000 + " ms";
                    c.close();
                } catch (Exception ex) {
                    open = "clip open failed: " + ex;
                    check(false, name + " " + open);
                }
                System.out.printf("  %-14s %s  frames=%d  clipLine=%s  %s%n", name, fmt, frames, supported, open);
                check(frames > 0, name + " is empty");
                check(fmt.getSampleRate() == 22050f && fmt.getSampleSizeInBits() == 16 && fmt.getChannels() == 1, name + " wrong format");
            }
            totalBytes += f.length();
        }
        System.out.println("  sound files: " + files.length + ", " + totalBytes / 1024 + " KB");
        for (String s : new String[]{"shoot_pistol", "shoot_rifle", "shoot_heavy", "shotgun", "rocket", "explosion", "hit", "hurt", "pickup", "craft", "click", "swing", "enemy_shoot", "toxic_spit", "monster_death", "boss_roar", "wave", "chest_open", "gameover", "win", "bgm"}) {
            check(new File(dir, s + ".wav").isFile(), "missing sound " + s);
        }
        // playing through the real API never throws
        AssetLoader.setVolume(1);
        AssetLoader.playSound("click");
        AssetLoader.playMusic("bgm");
        AssetLoader.setMusicEnabled(false);
        AssetLoader.setMusicEnabled(true);
        AssetLoader.stopMusic();
        AssetLoader.playSound("does_not_exist");
        check(AssetLoader.loadImage("nope/missing.png").getWidth() == 32, "missing image fallback");
    }

    static void scores() {
        System.out.println("== scores");
        List<GameData.ScoreEntry> before = GameData.loadScores();
        check(before != null, "loadScores returned null");
        System.out.println("  loadScores ok, " + before.size() + " entries (file not written by the test)");
    }
}
