package Project3_6813107;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Throwaway UI test: drives every launcher / workshop control and renders PNGs. */
public class UiHarness {
    static String OUT;
    static int fails = 0;

    static void check(boolean ok, String what) {
        if (!ok) { fails++; System.out.println("FAIL: " + what); }
    }

    @SuppressWarnings("unchecked")
    static <T> T f(Object o, String name) throws Exception {
        Class<?> c = o.getClass();
        while (c != null) {
            try { Field fl = c.getDeclaredField(name); fl.setAccessible(true); return (T) fl.get(o); }
            catch (NoSuchFieldException e) { c = c.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }

    static void edt(Runnable r) throws Exception { SwingUtilities.invokeAndWait(r); }

    static void render(Window w, String name) throws Exception {
        Thread.sleep(150);
        edt(() -> {
            Container cp = (w instanceof JFrame) ? ((JFrame) w).getContentPane() : ((JDialog) w).getContentPane();
            cp.validate();
            BufferedImage img = new BufferedImage(cp.getWidth(), cp.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            cp.paint(g);
            g.dispose();
            try { ImageIO.write(img, "png", new File(OUT, name)); } catch (IOException e) { throw new UncheckedIOException(e); }
            System.out.println("wrote " + name + " " + img.getWidth() + "x" + img.getHeight());
        });
    }

    public static void main(String[] a) throws Exception {
        OUT = a[0];
        AssetLoader.setVolume(15);
        edt(MainApplication.Theme::installLookAndFeel);
        if (a.length > 1 && a[1].equals("dialog")) { dialogTests(); } else { launcherTests(); }
        System.out.println(fails == 0 ? "ALL CHECKS PASSED" : fails + " CHECKS FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }

    static void launcherTests() throws Exception {
        MainApplication[] holder = new MainApplication[1];
        edt(() -> { holder[0] = new MainApplication(); holder[0].showLauncher(); });
        MainApplication app = holder[0];
        Thread.sleep(400);
        System.out.println("frame " + app.getSize() + " content " + app.getContentPane().getSize());
        render(app, "launcher_default.png");

        javax.swing.Timer timer = f(app, "previewTimer");
        check(timer.isRunning(), "preview timer runs while visible");

        // radios
        JRadioButton[] radios = f(app, "classRadios");
        JTextArea classInfo = f(app, "classInfoArea");
        for (int i = 0; i < radios.length; i++) {
            int k = i;
            edt(() -> radios[k].doClick(0));
            String expect = GameData.PlayerClass.values()[i].displayName.toUpperCase();
            check(classInfo.getText().startsWith(expect), "class info for " + expect + " got " + classInfo.getText());
            System.out.println("class " + classInfo.getText().replace('\n', '|'));
        }
        render(app, "launcher_engineer.png");
        edt(() -> radios[1].doClick(0));

        // weapons
        JComboBox<?> weapon = f(app, "weaponCombo");
        JTextArea wstats = f(app, "weaponStatsArea");
        for (int i = 0; i < weapon.getItemCount(); i++) {
            int k = i;
            edt(() -> weapon.setSelectedIndex(k));
            GameData.WeaponType w = GameData.START_WEAPONS[i];
            check(wstats.getText().startsWith(w.displayName), "weapon stats " + w);
            System.out.println("weapon " + wstats.getText().replace('\n', '|'));
        }
        // maps
        JComboBox<?> map = f(app, "mapCombo");
        JTextArea mdesc = f(app, "mapDescriptionArea");
        for (int i = 0; i < map.getItemCount(); i++) {
            int k = i;
            long t0 = System.nanoTime();
            edt(() -> map.setSelectedIndex(k));
            GameData.MapType m = GameData.MapType.values()[i];
            check(mdesc.getText().startsWith(m.displayName), "map desc " + m);
            System.out.println("map " + m + " select took " + (System.nanoTime() - t0) / 1_000_000 + " ms");
            if (i == 2) render(app, "launcher_bunker_shotgun.png");
        }

        // checkboxes
        JCheckBox sound = f(app, "soundBox"), music = f(app, "musicBox"), hit = f(app, "hitboxBox"),
                auto = f(app, "autoFireBox"), dbl = f(app, "doubleSpawnBox");
        JTextArea diffArea = f(app, "difficultyEffectArea");
        String before = diffArea.getText();
        edt(() -> dbl.doClick(0));
        check(!diffArea.getText().equals(before), "double spawn changes the readout");
        System.out.println("double spawn readout: " + diffArea.getText().replace('\n', '|'));
        edt(() -> sound.doClick(0));
        check(!AssetLoader.isSoundEnabled(), "sound off");
        edt(() -> sound.doClick(0));
        check(AssetLoader.isSoundEnabled(), "sound on");
        edt(() -> music.doClick(0));
        check(!AssetLoader.isMusicEnabled(), "music off");
        edt(() -> music.doClick(0));
        check(AssetLoader.isMusicEnabled(), "music on");
        edt(() -> auto.doClick(0));
        edt(() -> hit.doClick(0));
        JLabel hint = f(app, "optionHintLabel");
        System.out.println("option hint: " + hint.getText());
        render(app, "launcher_options_on.png");

        // sliders
        JSlider vol = f(app, "volumeSlider"), diff = f(app, "difficultySlider");
        JLabel volLabel = f(app, "volumeValueLabel"), diffName = f(app, "difficultyNameLabel");
        for (int v = 0; v <= 100; v += 10) {
            int k = v;
            edt(() -> vol.setValue(k));
            check(AssetLoader.getVolume() == v, "volume applied " + v);
        }
        System.out.println("volume label at 100: " + volLabel.getText());
        edt(() -> vol.setValue(0));
        check(volLabel.getText().equals("Muted"), "muted label");
        edt(() -> vol.setValue(15));
        for (int d = 1; d <= 5; d++) {
            int k = d;
            edt(() -> diff.setValue(k));
            check(diffName.getText().startsWith(d + " - "), "difficulty name " + d);
            System.out.println("difficulty " + diffName.getText() + " : " + diffArea.getText().replace('\n', '|'));
        }
        edt(() -> diff.setValue(5));
        render(app, "launcher_nightmare.png");

        // names
        JTextField name = f(app, "nameField");
        JLabel nameHint = f(app, "nameHintLabel");
        JButton start = f(app, "startButton");
        String[] bad = {"", "    ", "ThisNameIsWayTooLong", "bad\tname", "<b>x</b>", "Anné"};
        for (String s : bad) {
            edt(() -> { name.setText(s); start.doClick(0); });
            check(app.isVisible(), "launcher stays for bad name [" + s + "]");
            check(nameHint.getForeground().equals(MainApplication.Theme.BAD), "red hint for [" + s + "]");
            System.out.println("bad name [" + s + "] -> " + nameHint.getText());
            if (s.equals("ThisNameIsWayTooLong")) render(app, "launcher_bad_name.png");
        }
        edt(() -> { name.setText("  Alex_01  "); start.doClick(0); });
        check(!app.isVisible(), "launcher hidden after valid start");
        check(!timer.isRunning(), "preview timer stopped while hidden");
        check(name.getText().equals("Alex_01"), "name trimmed");

        // fake scores in the temp working dir, then come back
        File scores = new File("Project3_6813107/highscores.txt");
        String[][] rows = {{"Alex_01", "Soldier", "Gas Station", "4120", "131", "7", "245"},
                {"Bea", "Medic", "Survivor Camp", "6900", "212", "10", "300"},
                {"Chai", "Scout", "Quarantine Zone", "880", "30", "2", "61"},
                {"Dee", "Engineer", "Abandoned Bunker", "2400", "77", "5", "160"}};
        try (PrintWriter pw = new PrintWriter(new FileWriter(scores))) {
            for (String[] r : rows) pw.println(String.join("\t", r));
            for (int i = 0; i < 9; i++) pw.println("Filler" + i + "\tScavenger\tWasteland Highway\t" + (100 * i) + "\t" + i + "\t1\t" + (20 + i));
        }
        app.onGameFinished();   // from a non-EDT thread on purpose
        Thread.sleep(500);
        check(app.isVisible(), "launcher visible again");
        check(timer.isRunning(), "timer restarted");
        JTable table = f(app, "scoreTable");
        check(table.getRowCount() == 10, "top 10 rows, got " + table.getRowCount());
        check(String.valueOf(table.getValueAt(0, 1)).equals("Bea"), "best first");
        check(table.isCellEditable(0, 1) == false, "read only");
        JTabbedPane tabs = f(app, "infoTabs");
        check(tabs.getSelectedIndex() == 0, "scores tab shown");
        render(app, "launcher_scores.png");

        // windows-like content area (884x581) to be sure nothing clips there
        edt(() -> { app.setMinimumSize(null); Insets in = app.getInsets(); app.setSize(884 + in.left + in.right, 581 + in.top + in.bottom); });
        Thread.sleep(300);
        render(app, "launcher_windows_size.png");
        edt(() -> tabs.setSelectedIndex(1));
        render(app, "launcher_howto_windows_size.png");
        edt(() -> { Insets in = app.getInsets(); app.setSize(1150 + in.left + in.right, 680 + in.top + in.bottom); });
        Thread.sleep(300);
        render(app, "launcher_large.png");
        scores.delete();
        edt(app::dispose);
        AssetLoader.stopMusic();
    }

    static Player emptyPlayer(GameData.PlayerClass pc) {
        GameData.GameSettings s = new GameData.GameSettings();
        s.playerName = "Alex_01";
        s.playerClass = pc;
        Player p = new Player(s, 100, 100);
        for (GameData.ItemType t : GameData.ItemType.values()) p.addItem(t, -1000);
        return p;
    }

    static UpgradeDialog open(Player p) throws Exception {
        UpgradeDialog[] h = new UpgradeDialog[1];
        edt(() -> { h[0] = new UpgradeDialog(null, p); h[0].setModal(false); h[0].setVisible(true); });
        Thread.sleep(300);
        return h[0];
    }

    static void dialogTests() throws Exception {
        // (a) empty inventory
        Player empty = emptyPlayer(GameData.PlayerClass.SOLDIER);
        UpgradeDialog d0 = open(empty); UpgradeDialog d = d0;
        System.out.println("dialog " + d.getSize() + " content " + d.getContentPane().getSize());
        JTable table = f(d, "recipeTable");
        JButton craft = f(d, "craftButton");
        JTextArea log = f(d, "craftLogArea");
        for (int r = 0; r < table.getRowCount(); r++) {
            int k = r;
            edt(() -> table.setRowSelectionInterval(k, k));
            check(!craft.isEnabled(), "craft disabled on empty row " + r);
            if (r == 4) { try { render(d0, "dialog_empty_rocket_selected.png"); } catch (Exception ex) { throw new RuntimeException(ex); } }
        }
        edt(() -> { table.setRowSelectionInterval(1, 1); d0.craftSelectedUpgrade(); table.setRowSelectionInterval(0, 0); d0.craftSelectedUpgrade(); });
        System.out.println("empty log:\n" + log.getText());
        render(d, "dialog_empty.png");
        edt(d::dispose);

        // (b) rich inventory, craft everything to max
        Player rich = emptyPlayer(GameData.PlayerClass.ENGINEER);
        for (GameData.ItemType t : GameData.ItemType.values()) rich.addItem(t, 300);
        d = open(rich);
        JTable t2 = f(d, "recipeTable");
        JButton craft2 = f(d, "craftButton");
        JTextArea log2 = f(d, "craftLogArea");
        Map<GameData.ItemType, Object> counts = f(d, "itemSlots");
        render(d, "dialog_rich_start.png");
        UpgradeDialog dd = d;
        for (int r = 0; r < GameData.RECIPES.size(); r++) {
            GameData.UpgradeRecipe rec = GameData.RECIPES.get(r);
            int k = r;
            edt(() -> t2.setRowSelectionInterval(k, k));
            int guard = 0;
            while (rich.canCraft(rec) && guard++ < 6) {
                check(craft2.isEnabled(), "enabled when craftable " + rec.name);
                edt(dd::craftSelectedUpgrade);
                check(t2.getSelectedRow() == k, "selection kept on " + rec.name);
                for (Map.Entry<GameData.ItemType, Object> e : counts.entrySet()) {
                    int shown = (Integer) f(e.getValue(), "count");
                    check(shown == rich.getItemCount(e.getKey()), "slot count " + e.getKey());
                }
                check(counts.size() == GameData.ItemType.values().length - 1, "11 slots");
                check(String.valueOf(t2.getValueAt(k, 3)).equals(rich.getRecipeStatus(rec)), "status cell");
            }
            System.out.println(rec.name + " -> " + rich.getRecipeStatus(rec) + " enabled=" + craft2.isEnabled());
            if (rec.maxLevel > 0) check(!craft2.isEnabled() && (rich.getRecipeStatus(rec).equals("MAX") || rich.getRecipeStatus(rec).equals("Owned")), "maxed and disabled " + rec.name);
            if (r == 6) render(d, "dialog_rich_mid.png");
        }
        // failure line for a maxed row via double-click and Enter
        edt(() -> t2.setRowSelectionInterval(0, 0));
        Rectangle cell = t2.getCellRect(0, 0, true);
        edt(() -> {
            MouseEvent me = new MouseEvent(t2, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, cell.x + 5, cell.y + 5, 2, false, MouseEvent.BUTTON1);
            for (MouseListener ml : t2.getMouseListeners()) ml.mouseClicked(me);
        });
        edt(() -> t2.getActionMap().get("craft").actionPerformed(new ActionEvent(t2, 0, "craft")));
        // medkit row: double-click crafts
        int medRow = GameData.RECIPES.size() - 1;
        int medBefore = rich.getItemCount(GameData.ItemType.MEDKIT);
        edt(() -> t2.setRowSelectionInterval(medRow, medRow));
        Rectangle mc = t2.getCellRect(medRow, 0, true);
        edt(() -> {
            MouseEvent me = new MouseEvent(t2, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, mc.x + 5, mc.y + 5, 2, false, MouseEvent.BUTTON1);
            for (MouseListener ml : t2.getMouseListeners()) ml.mouseClicked(me);
        });
        System.out.println("medkits " + medBefore + " -> " + rich.getItemCount(GameData.ItemType.MEDKIT));
        System.out.println("rich log:\n" + log2.getText());
        render(d, "dialog_rich_end.png");
        // B within the grace period should not close; Esc closes
        UpgradeDialog d3 = d;
        edt(() -> d3.getRootPane().getActionMap().get("closeB").actionPerformed(new ActionEvent(d3, 0, "b")));
        check(!d.isDisplayable(), "B after 400 ms closes");
        UpgradeDialog d5 = open(rich);
        edt(() -> d5.getRootPane().getActionMap().get("close").actionPerformed(new ActionEvent(d5, 0, "esc")));
        check(!d5.isDisplayable(), "Esc closed the dialog");

        // (c) dead player with starting items, B closes after the grace period
        Player dead = emptyPlayer(GameData.PlayerClass.MEDIC);
        dead.addItem(GameData.ItemType.SCRAPS, 5);
        dead.addItem(GameData.ItemType.WOOD, 2);
        dead.takeDamage(10_000);
        check(!dead.isAlive(), "player dead");
        UpgradeDialog d4 = open(dead);
        edt(() -> d4.getRootPane().getActionMap().get("closeB").actionPerformed(new ActionEvent(d4, 0, "b")));
        check(d4.isDisplayable(), "B within grace does not close");
        render(d4, "dialog_dead_player.png");
        edt(() -> { d4.setMinimumSize(null); Insets in = d4.getInsets(); d4.setSize(764 + in.left + in.right, 481 + in.top + in.bottom); });
        Thread.sleep(300);
        render(d4, "dialog_windows_size.png");
        Thread.sleep(450);
        edt(() -> d4.getRootPane().getActionMap().get("closeB").actionPerformed(new ActionEvent(d4, 0, "b")));
        check(!d4.isDisplayable(), "B after grace closes");
    }
}
