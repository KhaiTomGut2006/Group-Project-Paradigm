package Project3_XXX;

import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.*;
import javax.imageio.ImageIO;

/** Headless driver: plays GamePanel through its real listener methods. */
public class HeadlessSim {
    static File out;
    static int failures = 0;

    public static void main(String[] a) throws Exception {
        out = new File(a[0]);
        out.mkdirs();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> { System.out.println("UNCAUGHT in " + t.getName()); e.printStackTrace(System.out); failures++; });
        GameData.MapType[] maps = GameData.MapType.values();
        GameData.PlayerClass[] classes = GameData.PlayerClass.values();
        String only = a.length > 1 ? a[1] : null;
        for (int i = 0; i < maps.length; i++) {
            if (only != null && !maps[i].name().equals(only)) continue;
            // strong player survives to the win, random input
            run(maps[i], classes[i], GameData.START_WEAPONS[i % 6], 1 + (i * 2) % 5, i % 2 == 1, "strong", 20000, 100 + i);
            // normal random player
            run(maps[i], classes[(i + 2) % 5], GameData.START_WEAPONS[(i + 3) % 6], 3, false, "random", 20000, 200 + i);
            // stands still and dies
            run(maps[i], classes[(i + 4) % 5], GameData.START_WEAPONS[(i + 1) % 6], 5, true, "idle", 20000, 300 + i);
        }
        if (only == null || only.equals("PERF")) perf();
        System.out.println(failures == 0 ? "ALL OK" : ("FAILURES: " + failures));
    }

    static Object field(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    static int size(GamePanel p, String list) throws Exception {
        return ((List<?>) field(p, list)).size();
    }

    static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            System.out.println("  FAIL: " + what);
        }
    }

    static void render(GamePanel p, String name) throws Exception {
        BufferedImage img = new BufferedImage(1100, 540, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        p.paint(g);
        g.dispose();
        ImageIO.write(img, "png", new File(out, name + ".png"));
    }

    static void run(GameData.MapType map, GameData.PlayerClass cls, GameData.WeaponType weapon, int diff, boolean dbl,
                    String mode, int ticks, long seed) throws Exception {
        GameData.GameSettings s = new GameData.GameSettings();
        s.map = map; s.playerClass = cls; s.startWeapon = weapon; s.difficulty = diff; s.doubleSpawn = dbl;
        s.autoFire = mode.equals("strong"); s.soundOn = false; s.musicOn = false;
        GamePanel p = new GamePanel(s, null);
        p.setSize(1100, 540);
        Player pl = p.getPlayer();
        Input in = new Input(p);
        Random r = new Random(seed);
        String tag = map.name().toLowerCase() + "_" + mode;
        long maxNs = 0, sumNs = 0; int measured = 0, maxAt = -1, maxWaveAt = 0;
        int maxMon = 0, maxBul = 0, maxDrop = 0, maxFx = 0, maxWave = 1, bossesSeen = 0;
        Set<Object> bossSet = new HashSet<>();
        int startItems = itemsTotal(pl);
        int pickups = 0, lastItems = startItems;
        int overTick = -1; int ammoUsedMax = 0;
        boolean pausedShot = false, lowShot = false, bossShot = false, deathShot = false;
        long[] times = new long[ticks];
        int craftIndex = 0;
        for (int t = 0; t < ticks; t++) {
            if (!mode.equals("idle") && !p.isGameOver()) in.randomInput(r, t);
            if (mode.equals("strong")) {
                if (t % 20 == 0) pl.heal(1000);
                if (t % 600 == 300) {
                    for (GameData.ItemType it : GameData.ItemType.values()) pl.addItem(it, 6);
                    for (GameData.UpgradeRecipe rec : GameData.RECIPES) if (pl.canCraft(rec)) { pl.craft(rec); break; }
                }
            }
            if (mode.equals("random") && t == 1200 && !p.isGameOver()) {
                // pause overlay: press P, tick (must not advance), render, press P
                int before = p.getElapsedSeconds();
                in.key(KeyEvent.VK_P);
                for (int k = 0; k < 200; k++) p.updateGame();
                check(p.isPaused(), "pause flag");
                check(p.getElapsedSeconds() == before, "time frozen while paused");
                render(p, tag + "_paused");
                pausedShot = true;
                in.key(KeyEvent.VK_ESCAPE);
                check(!p.isPaused(), "resume with Esc");
            }
            if (mode.equals("strong") && t == 7000) {
                Field hpF = Entity.class.getDeclaredField("hp"); hpF.setAccessible(true); hpF.setInt(pl, 12);
                for (int k = 0; k < 3; k++) p.updateGame();
                render(p, tag + "_lowhp");
                lowShot = true;
                pl.heal(1000);
            }
            int itemsBefore = itemsTotal(pl);
            long t0 = System.nanoTime();
            p.updateGame();
            long dt = System.nanoTime() - t0;
            times[t] = dt;
            int itemsAfter = itemsTotal(pl);
            if (itemsAfter > itemsBefore) pickups++;
            if (t > 300) { if (dt > maxNs) { maxNs = dt; maxAt = t; maxWaveAt = p.getWave(); } sumNs += dt; measured++; }
            if (dt > 4_000_000L) System.out.println("    slow tick " + t + ": " + dt / 1e6 + " ms, wave " + p.getWave() + " monsters " + size(p, "monsters"));
            maxMon = Math.max(maxMon, size(p, "monsters"));
            maxBul = Math.max(maxBul, size(p, "bullets"));
            maxDrop = Math.max(maxDrop, size(p, "drops"));
            maxFx = Math.max(maxFx, size(p, "effects"));
            maxWave = Math.max(maxWave, p.getWave());
            for (Object m : (List<?>) field(p, "monsters")) {
                if (((Monster) m).getType() == GameData.MonsterType.ZOMBIE_BOSS) bossSet.add(m);
            }
            if (p.isGameOver() && overTick < 0) {
                overTick = t;
            }
            if (t == 120) render(p, tag + "_early");
            if (t == 1000) render(p, tag + "_mid");
            if (!bossShot && !p.isGameOver()) {
                for (Object m : (List<?>) field(p, "monsters")) {
                    Monster mm = (Monster) m;
                    if (mm.getType() == GameData.MonsterType.ZOMBIE_BOSS && mm.isAlive()
                            && Math.abs(mm.getX() - pl.getX()) < 220 && Math.abs(mm.getY() - pl.getY()) < 110) {
                        render(p, tag + "_boss");
                        bossShot = true;
                        break;
                    }
                }
            }
            if (!pl.isAlive() && !deathShot && !p.isGameOver()) {
                // a few ticks into the death animation
                for (int k = 0; k < 20; k++) p.updateGame();
                render(p, tag + "_dying");
                deathShot = true;
            }
            if (overTick >= 0 && t == overTick + 30) render(p, tag + "_over");
            if (overTick >= 0 && t > overTick + 400) break;
        }
        boolean won = (Boolean) field(p, "won");
        int timer = (Integer) field(p, "gameOverTimer");
        long[] sorted = Arrays.copyOf(times, times.length);
        Arrays.sort(sorted);
        long p99 = sorted[(int) (sorted.length * 0.99)];
        System.out.printf("%-32s %-9s %-15s d%d%s: over@%d won=%s score=%d kills=%d wave=%d bosses=%d pickups=%d max[mon=%d bul=%d drop=%d fx=%d] tick avg=%.3fms p99=%.3fms max=%.2fms%n",
                tag, cls, weapon, diff, dbl ? "x2" : "", overTick, won, p.getScore(), p.getKills(), maxWave, bossSet.size(), pickups,
                maxMon, maxBul, maxDrop, maxFx, sumNs / 1e6 / Math.max(1, measured), p99 / 1e6, maxNs / 1e6);
        check(p.isGameOver(), tag + " reached game over");
        check(timer == 0, tag + " game over delay finished (report would have fired)");
        if (mode.equals("strong")) {
            check(won, tag + " strong player wins");
            check(p.getElapsedSeconds() == 300, tag + " elapsed 300");
            check(maxWave >= 10, tag + " wave 10 reached");
            check(bossSet.size() >= 3, tag + " 3 bosses appeared");
            check(pickups > 20, tag + " loot picked up");
            check(lowShot, "low hp shot");
        }
        if (mode.equals("idle")) {
            check(!won, tag + " idle player dies");
            check(deathShot, tag + " death shot");
        }
        check(maxMon <= GameData.MAX_LIVE_MONSTERS + 60, tag + " monsters bounded (incl. corpses)");
        check(maxBul < 400 && maxDrop < 600 && maxFx <= 260, tag + " lists bounded");
        check(maxNs < 16_000_000L, tag + " max tick < 16ms");
    }

    static int itemsTotal(Player pl) {
        int n = 0;
        for (Map.Entry<GameData.ItemType, Integer> e : pl.getInventory().entrySet()) {
            if (e.getKey() != GameData.ItemType.AMMO) n += e.getValue();
        }
        return n;
    }

    /** 120 monsters chasing a player that cannot die: tick and paint cost. */
    static void perf() throws Exception {
        GameData.GameSettings s = new GameData.GameSettings();
        s.map = GameData.MapType.SURVIVOR_CAMP; s.difficulty = 5; s.doubleSpawn = true; s.soundOn = false; s.musicOn = false;
        GamePanel p = new GamePanel(s, null);
        p.setSize(1100, 540);
        Player pl = p.getPlayer();
        int t = 0;
        // let the wave clock run until monsters fill up to the cap; player never shoots
        while (p.getLiveMonsterCount() < GameData.MAX_LIVE_MONSTERS && t < 18000) {
            pl.heal(1000);
            p.updateGame();
            t++;
        }
        System.out.println("PERF: live monsters " + p.getLiveMonsterCount() + " after " + t + " ticks, wave " + p.getWave());
        BufferedImage img = new BufferedImage(1100, 540, BufferedImage.TYPE_INT_RGB);
        long upd = 0, paint = 0, maxUpd = 0, maxPaint = 0;
        int n = 600;
        for (int i = 0; i < n; i++) {
            pl.heal(1000);
            long a0 = System.nanoTime();
            p.updateGame();
            long a1 = System.nanoTime();
            Graphics2D g = img.createGraphics();
            p.paint(g);
            g.dispose();
            long a2 = System.nanoTime();
            if (i > 60) { upd += a1 - a0; paint += a2 - a1; maxUpd = Math.max(maxUpd, a1 - a0); maxPaint = Math.max(maxPaint, a2 - a1); }
        }
        int m = n - 61;
        System.out.printf("PERF 120 monsters: update avg %.3f ms max %.2f ms | paint(1100x540) avg %.3f ms max %.2f ms | visible-ish monsters near player%n",
                upd / 1e6 / m, maxUpd / 1e6, paint / 1e6 / m, maxPaint / 1e6);
        ImageIO.write(img, "png", new File(out, "perf_120_monsters.png"));
        check(p.getLiveMonsterCount() <= GameData.MAX_LIVE_MONSTERS, "cap respected");
    }

    /** Synthetic key and mouse events through the real listener methods. */
    static final class Input {
        final GamePanel p;
        boolean firing;
        int mx = 600, my = 270;
        int[] moveKeys = {KeyEvent.VK_W, KeyEvent.VK_A, KeyEvent.VK_S, KeyEvent.VK_D};
        boolean[] held = new boolean[4];

        Input(GamePanel p) { this.p = p; }

        void key(int code) {
            p.keyPressed(new KeyEvent(p, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, code, KeyEvent.CHAR_UNDEFINED));
            p.keyReleased(new KeyEvent(p, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, code, KeyEvent.CHAR_UNDEFINED));
        }

        void setHeld(int i, boolean down) {
            if (held[i] == down) return;
            held[i] = down;
            int id = down ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED;
            KeyEvent e = new KeyEvent(p, id, System.currentTimeMillis(), 0, moveKeys[i], KeyEvent.CHAR_UNDEFINED);
            if (down) p.keyPressed(e); else p.keyReleased(e);
        }

        void randomInput(Random r, int t) {
            if (t % 45 == 0) {
                for (int i = 0; i < 4; i++) setHeld(i, r.nextInt(3) == 0);
            }
            if (t % 4 == 0) {
                mx = Math.max(0, Math.min(1099, mx + r.nextInt(81) - 40));
                my = Math.max(0, Math.min(539, my + r.nextInt(61) - 30));
                int id = firing ? MouseEvent.MOUSE_DRAGGED : MouseEvent.MOUSE_MOVED;
                MouseEvent e = new MouseEvent(p, id, System.currentTimeMillis(), firing ? MouseEvent.BUTTON1_DOWN_MASK : 0, mx, my, 0, false, MouseEvent.NOBUTTON);
                if (firing) p.mouseDragged(e); else p.mouseMoved(e);
            }
            if (t % 70 == 0) {
                boolean want = r.nextInt(3) != 0;
                if (want != firing) {
                    firing = want;
                    int id = want ? MouseEvent.MOUSE_PRESSED : MouseEvent.MOUSE_RELEASED;
                    MouseEvent e = new MouseEvent(p, id, System.currentTimeMillis(), want ? MouseEvent.BUTTON1_DOWN_MASK : 0, mx, my, 1, false, MouseEvent.BUTTON1);
                    if (want) p.mousePressed(e); else p.mouseReleased(e);
                }
            }
            if (t % 400 == 133) {
                MouseEvent e = new MouseEvent(p, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), MouseEvent.BUTTON3_DOWN_MASK, mx, my, 1, false, MouseEvent.BUTTON3);
                p.mousePressed(e);
                p.mouseReleased(new MouseEvent(p, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, mx, my, 1, false, MouseEvent.BUTTON3));
            }
            if (t % 500 == 250) key(KeyEvent.VK_G);
            if (t % 300 == 77) key(KeyEvent.VK_E);
            if (t % 350 == 11) key(KeyEvent.VK_1 + r.nextInt(8));
            if (t % 260 == 19) {
                p.mouseWheelMoved(new MouseWheelEvent(p, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0, mx, my, 0, false,
                        MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, r.nextBoolean() ? 1 : -1));
            }
            if (t % 900 == 450) key(KeyEvent.VK_B);   // frame is null: must be ignored
        }
    }
}
