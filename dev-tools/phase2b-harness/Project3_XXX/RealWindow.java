package Project3_XXX;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Real (non-headless) window test: threads, FPS, HUD screenshot, workshop, game over, close. */
public class RealWindow {
    static final AtomicLong paints = new AtomicLong(), paintNanos = new AtomicLong();
    static int fails = 0;
    static File out;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + what);
        if (!ok) fails++;
    }

    static Object field(Object o, Class<?> c, String n) throws Exception {
        Field f = c.getDeclaredField(n); f.setAccessible(true); return f.get(o);
    }

    static void edt(Runnable r) throws Exception { SwingUtilities.invokeAndWait(r); }

    static GameFrame frame;

    public static void main(String[] a) throws Exception {
        out = new File(a[0]);
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> { System.out.println("UNCAUGHT in " + t.getName()); e.printStackTrace(System.out); fails++; });
        RepaintManager.setCurrentManager(new RepaintManager() {
            @Override public void paintDirtyRegions() {
                long t0 = System.nanoTime();
                super.paintDirtyRegions();
                paintNanos.addAndGet(System.nanoTime() - t0);
                paints.incrementAndGet();
            }
        });
        File scores = new File("highscores.txt");
        scores.delete();

        // ---- run 1: play ~8 s with random input, HUD shot, workshop, pause button, death -> onGameOver -> close
        GameData.GameSettings s = new GameData.GameSettings();
        s.map = GameData.MapType.GAS_STATION; s.playerClass = GameData.PlayerClass.SOLDIER;
        s.startWeapon = GameData.WeaponType.SHOTGUN; s.difficulty = 4; s.doubleSpawn = true;
        s.soundOn = true; s.musicOn = true; s.volume = 8; s.autoFire = false;
        edt(() -> frame = new GameFrame(s, null));
        GamePanel panel = (GamePanel) field(frame, GameFrame.class, "gamePanel");
        check(alive("gameThread") && alive("waveSpawnerThread"), "both threads running after construction");
        Random r = new Random(5);
        Thread.sleep(500);
        long p0 = paints.get(), n0 = paintNanos.get(), t0 = System.nanoTime();
        int ticks0 = (Integer) field(panel, GamePanel.class, "tickCount");
        for (int i = 0; i < 8000 / 16; i++) {
            final int k = i;
            SwingUtilities.invokeLater(() -> drive(panel, r, k));
            panel.getPlayer().heal(1000);   // keep it alive for the screenshots
            Thread.sleep(16);
        }
        double secs = (System.nanoTime() - t0) / 1e9;
        long frames = paints.get() - p0;
        int ticks = (Integer) field(panel, GamePanel.class, "tickCount") - ticks0;
        System.out.printf("  measured: %.1f paint passes/s (avg paint %.2f ms), %.1f game updates/s over %.1f s, live monsters %d, wave %d%n",
                frames / secs, (paintNanos.get() - n0) / 1e6 / Math.max(1, frames), ticks / secs, secs, panel.getLiveMonsterCount(), panel.getWave());
        check(frames / secs > 50, "frame rate above 50");
        edt(() -> shot("frame_playing.png"));
        edt(RealWindow::checkStrip);
        // Windows has thicker borders: content about 16 px narrower and 20 px lower than on a Mac
        edt(() -> { frame.setSize(1084, 640); frame.validate(); });
        Thread.sleep(300);
        edt(() -> shot("frame_narrow.png"));
        edt(RealWindow::checkStrip);
        edt(() -> { frame.setSize(1100, 660); frame.validate(); });
        // focus
        edt(() -> System.out.println("  focus owner: " + KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner()));
        // pause button
        JButton pause = (JButton) field(frame, GameFrame.class, "pauseButton");
        edt(pause::doClick);
        Thread.sleep(300);
        check(panel.isPaused(), "Pause button pauses");
        edt(() -> shot("frame_paused.png"));
        check(pause.getText().startsWith("Resume"), "pause button text says Resume");
        edt(pause::doClick);
        check(!panel.isPaused(), "Pause button resumes");
        // checkboxes
        JCheckBox auto = (JCheckBox) field(frame, GameFrame.class, "autoFireBox");
        edt(auto::doClick);
        check((Boolean) field(panel, GamePanel.class, "autoFire"), "Auto-Fire checkbox works live");
        JCheckBox hb = (JCheckBox) field(frame, GameFrame.class, "hitboxBox");
        edt(hb::doClick);
        check((Boolean) field(panel, GamePanel.class, "showHitbox"), "Show Hitbox checkbox works live");
        Thread.sleep(300);
        edt(() -> shot("frame_hitbox.png"));
        edt(hb::doClick);
        // workshop via B key
        SwingUtilities.invokeLater(() -> panel.keyPressed(new KeyEvent(panel, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_B, 'b')));
        Dialog dlg = waitForDialog(3000);
        check(dlg != null, "workshop dialog opened by B");
        check(panel.isPaused(), "game paused while workshop open");
        if (dlg != null) {
            Thread.sleep(600);
            final Dialog d = dlg;
            edt(() -> shotWindow(d, "frame_workshop.png"));
            SwingUtilities.invokeLater(d::dispose);
        }
        Thread.sleep(500);
        check(!panel.isPaused(), "game resumed after workshop closed");
        edt(() -> System.out.println("  focus owner after workshop: " + KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner()));
        // death -> game over
        long l0 = scores.exists() ? Files.readAllLines(scores.toPath()).size() : 0;
        for (int i = 0; i < 40 && panel.getPlayer().isAlive(); i++) { panel.getPlayer().takeDamage(1000); Thread.sleep(50); }
        Thread.sleep(1200);
        edt(() -> shot("frame_dead.png"));
        Dialog summary = waitForDialog(6000);
        check(summary != null, "summary dialog shown after death");
        if (summary != null) {
            Thread.sleep(300);
            final Dialog d = summary;
            edt(() -> shotWindow(d, "frame_summary.png"));
            SwingUtilities.invokeLater(d::dispose);
        }
        Thread.sleep(1500);
        long l1 = scores.exists() ? Files.readAllLines(scores.toPath()).size() : 0;
        check(l1 - l0 == 1, "onGameOver ran exactly once (one score line saved): " + (l1 - l0));
        check(!frame.isDisplayable(), "frame disposed after summary");
        check(!alive("gameThread") && !alive("waveSpawnerThread"), "no game thread alive after game over");
        check(field(null, AssetLoader.class, "musicClip") == null, "music stopped");

        // ---- run 2: X button while running
        GameData.GameSettings s2 = new GameData.GameSettings();
        s2.map = GameData.MapType.ABANDONED_BUNKER; s2.soundOn = false; s2.musicOn = true; s2.volume = 8;
        edt(() -> frame = new GameFrame(s2, null));
        Thread.sleep(2500);
        edt(() -> shot("frame_bunker.png"));
        edt(() -> frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING)));
        Thread.sleep(300);
        check(!frame.isDisplayable(), "X closes the window");
        check(!alive("gameThread") && !alive("waveSpawnerThread"), "no game thread alive after X");
        check(field(null, AssetLoader.class, "musicClip") == null, "music stopped after X");
        // ---- run 3: workshop button with the real dialog while paused stays paused
        System.out.println(fails == 0 ? "REAL WINDOW ALL OK" : "REAL WINDOW FAILURES " + fails);
        System.exit(0);
    }

    @SuppressWarnings("unchecked")
    static void checkStrip() {
        try {
            Map<GameData.ItemType, JLabel> m = (Map<GameData.ItemType, JLabel>) field(frame, GameFrame.class, "itemLabels");
            int y = -1; boolean ok = true;
            for (JLabel l : m.values()) {
                if (y < 0) y = l.getY();
                ok &= l.getY() == y && l.isShowing() && l.getX() + l.getWidth() <= l.getParent().getWidth();
            }
            check(ok, "all 11 inventory labels on one visible row (content width " + frame.getContentPane().getWidth() + ")");
        } catch (Exception e) { e.printStackTrace(); fails++; }
    }

    static boolean alive(String name) {
        for (Thread t : Thread.getAllStackTraces().keySet()) if (t.getName().equals(name) && t.isAlive()) return true;
        return false;
    }

    static Dialog waitForDialog(int ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            for (Window w : Window.getWindows()) if (w instanceof Dialog && w.isShowing()) return (Dialog) w;
            Thread.sleep(50);
        }
        return null;
    }

    static void shot(String name) {
        try {
            JComponent c = (JComponent) frame.getContentPane();
            BufferedImage img = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics(); c.paint(g); g.dispose();
            ImageIO.write(img, "png", new File(out, name));
            System.out.println("  wrote " + name + " " + c.getWidth() + "x" + c.getHeight() + " (frame " + frame.getWidth() + "x" + frame.getHeight() + ", panel " + ((JComponent) c.getComponent(1)).getWidth() + "x" + ((JComponent) c.getComponent(1)).getHeight() + ")");
        } catch (Exception e) { e.printStackTrace(); }
    }

    static void shotWindow(Window w, String name) {
        try {
            Container c = ((RootPaneContainer) w).getContentPane();
            BufferedImage img = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics(); c.paint(g); g.dispose();
            ImageIO.write(img, "png", new File(out, name));
        } catch (Exception e) { e.printStackTrace(); }
    }

    static int mx = 550, my = 260; static boolean firing;
    static final int[] KEYS = {KeyEvent.VK_W, KeyEvent.VK_A, KeyEvent.VK_S, KeyEvent.VK_D};

    static void drive(GamePanel p, Random r, int i) {
        long now = System.currentTimeMillis();
        if (i % 40 == 0) for (int k : KEYS) {
            boolean down = r.nextInt(3) == 0;
            KeyEvent e = new KeyEvent(p, down ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED, now, 0, k, KeyEvent.CHAR_UNDEFINED);
            if (down) p.keyPressed(e); else p.keyReleased(e);
        }
        mx = Math.max(0, Math.min(1000, mx + r.nextInt(41) - 20)); my = Math.max(0, Math.min(500, my + r.nextInt(31) - 15));
        MouseEvent m = new MouseEvent(p, firing ? MouseEvent.MOUSE_DRAGGED : MouseEvent.MOUSE_MOVED, now, 0, mx, my, 0, false);
        if (firing) p.mouseDragged(m); else p.mouseMoved(m);
        if (i % 60 == 0) {
            firing = !firing;
            MouseEvent b = new MouseEvent(p, firing ? MouseEvent.MOUSE_PRESSED : MouseEvent.MOUSE_RELEASED, now, 0, mx, my, 1, false, MouseEvent.BUTTON1);
            if (firing) p.mousePressed(b); else p.mouseReleased(b);
        }
        if (i % 150 == 70) p.keyPressed(new KeyEvent(p, KeyEvent.KEY_PRESSED, now, 0, KeyEvent.VK_G, 'g'));
        if (i % 120 == 30) p.mouseWheelMoved(new MouseWheelEvent(p, MouseEvent.MOUSE_WHEEL, now, 0, mx, my, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, 1));
    }
}
