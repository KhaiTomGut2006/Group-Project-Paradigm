package Project3_6813107;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Renders every redesigned screen to PNG. Not part of the project. */
public class UiShots {
    static File out;

    static void shot(Component c, String name) throws Exception {
        BufferedImage img = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        c.paint(g);
        g.dispose();
        ImageIO.write(img, "png", new File(out, name + ".png"));
        System.out.println("wrote " + name + " " + c.getWidth() + "x" + c.getHeight());
    }

    static void onEdt(Runnable r) throws Exception { SwingUtilities.invokeAndWait(r); }

    static <T extends Component> T find(Container root, Class<T> type, String text) {
        for (Component c : root.getComponents()) {
            if (type.isInstance(c) && (text == null || (c instanceof AbstractButton && text.equals(((AbstractButton) c).getText())))) {
                return type.cast(c);
            }
            if (c instanceof Container) {
                T t = find((Container) c, type, text);
                if (t != null) return t;
            }
        }
        return null;
    }

    public static void main(String[] a) throws Exception {
        out = new File(a[0]);
        out.mkdirs();
        String part = a.length > 1 ? a[1] : "all";
        final MainApplication[] app = new MainApplication[1];
        onEdt(() -> { Theme.installLookAndFeel(); app[0] = new MainApplication(); app[0].setVisible(true); });
        Thread.sleep(800);
        final Container cp = app[0].getContentPane();
        String[] tabs = {"PLAY", "OPTIONS", "SCORES", "HELP"};
        for (String t : tabs) {
            onEdt(() -> { find(cp, JToggleButton.class, t).doClick(); });
            Thread.sleep(250);
            onEdt(() -> { try { shot(cp, "launcher_" + t.toLowerCase()); } catch (Exception e) { throw new RuntimeException(e); } });
        }
        // bad name
        onEdt(() -> {
            find(cp, JToggleButton.class, "PLAY").doClick();
            JTextField f = find(cp, JTextField.class, null);
            f.setText("<bad>");
            find(cp, JButton.class, "Start").doClick();
        });
        Thread.sleep(200);
        onEdt(() -> { try { shot(cp, "launcher_bad_name"); } catch (Exception e) { throw new RuntimeException(e); } });
        // windows-ish size: content 884x581
        onEdt(() -> { JTextField f = find(cp, JTextField.class, null); f.setText("Alex"); find(cp, JRadioButton.class, "ENGINEER").doClick(); app[0].setSize(884, 600); app[0].validate(); });
        Thread.sleep(300);
        onEdt(() -> { try { shot(cp, "launcher_small"); } catch (Exception e) { throw new RuntimeException(e); } });
        onEdt(() -> app[0].setVisible(false));

        // workshop with some loot
        GameData.GameSettings s = new GameData.GameSettings();
        s.playerName = "Alex";
        Player p = new Player(s, 100, 100);
        p.addItem(GameData.ItemType.SCRAPS, 9); p.addItem(GameData.ItemType.WOOD, 3); p.addItem(GameData.ItemType.STEEL, 1);
        p.addItem(GameData.ItemType.FOOD, 2); p.addItem(GameData.ItemType.ELECTRONICS, 1);
        final UpgradeDialog[] d = new UpgradeDialog[1];
        onEdt(() -> { d[0] = new UpgradeDialog(null, p); d[0].setModal(false); d[0].setVisible(true); });
        Thread.sleep(500);
        onEdt(() -> { try { shot(d[0].getContentPane(), "workshop_start"); } catch (Exception e) { throw new RuntimeException(e); } });
        onEdt(() -> d[0].craftSelectedUpgrade());
        Thread.sleep(200);
        onEdt(() -> { try { shot(d[0].getContentPane(), "workshop_after_craft"); } catch (Exception e) { throw new RuntimeException(e); } });
        onEdt(() -> { JTable t = find(d[0].getContentPane(), JTable.class, null); t.setRowSelectionInterval(4, 4); });
        Thread.sleep(200);
        onEdt(() -> { try { shot(d[0].getContentPane(), "workshop_locked"); } catch (Exception e) { throw new RuntimeException(e); } });
        onEdt(() -> d[0].dispose());

        // real game window
        final GameFrame[] gf = new GameFrame[1];
        GameData.GameSettings gs = new GameData.GameSettings();
        gs.playerName = "Alex"; gs.map = GameData.MapType.SURVIVOR_CAMP; gs.soundOn = false; gs.musicOn = false;
        onEdt(() -> gf[0] = new GameFrame(gs, null));
        Thread.sleep(9000);
        onEdt(() -> { try { shot(gf[0].getContentPane(), "game_running"); } catch (Exception e) { throw new RuntimeException(e); } });
        // hurt and poison the player to show the HUD status icons
        java.lang.reflect.Field panelField = GameFrame.class.getDeclaredField("gamePanel");
        panelField.setAccessible(true);
        GamePanel panel = (GamePanel) panelField.get(gf[0]);
        Thread.sleep(1200);   // mercy frames from earlier hits run out
        panel.getPlayer().takeDamage(80);
        panel.getPlayer().applyPoison(400);
        Thread.sleep(300);
        onEdt(() -> { gf[0].refreshHud(); try { shot(gf[0].getContentPane(), "game_hurt"); } catch (Exception e) { throw new RuntimeException(e); } });
        onEdt(() -> { find(gf[0].getContentPane(), JButton.class, "PAUSE").doClick(); });
        Thread.sleep(300);
        onEdt(() -> { try { shot(gf[0].getContentPane(), "game_paused"); } catch (Exception e) { throw new RuntimeException(e); } });
        onEdt(() -> gf[0].dispatchEvent(new java.awt.event.WindowEvent(gf[0], java.awt.event.WindowEvent.WINDOW_CLOSING)));
        Thread.sleep(1500);
        // end screen, drawn by a GamePanel without a window
        GameData.GameSettings es = new GameData.GameSettings();
        es.soundOn = false;
        es.musicOn = false;
        GamePanel end = new GamePanel(es, null);
        end.setSize(1100, 508);
        for (int i = 0; i < 200; i++) end.updateGame();
        end.getPlayer().takeDamage(100000);
        for (int i = 0; i < 90 && !end.isGameOver(); i++) end.updateGame();
        for (int i = 0; i < 40; i++) end.updateGame();
        shot(end, "game_over_screen");
        int alive = 0;
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            String n = t.getName().toLowerCase();
            if (t.isAlive() && (n.contains("game") || n.contains("spawner"))) { alive++; System.out.println("ALIVE " + t.getName()); }
        }
        System.out.println("game threads alive after close: " + alive);
        System.exit(0);
    }
}
