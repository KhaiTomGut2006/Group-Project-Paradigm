// Member1 Firstname Lastname   6XXXXXX1
// Member2 Firstname Lastname   6XXXXXX2
// Member3 Firstname Lastname   6XXXXXX3
// Member4 Firstname Lastname   6XXXXXX4
// Member5 Firstname Lastname   6XXXXXX5
package Project3_XXX;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.plaf.basic.BasicProgressBarUI;

/**
 * The game window (1100 x 660): a HUD bar on top (health, wave, time, score), the GamePanel in
 * the middle, and a HUD bar at the bottom (weapon, ammo, inventory, workshop button, options).
 *
 * The HUD is made of real Swing components. GamePanel asks for refreshHud() a few times per second
 * through SwingUtilities.invokeLater, so the components are only touched on the Swing thread.
 * No HUD component can take the keyboard focus, so W A S D always reach the GamePanel.
 */
public final class GameFrame extends JFrame implements ActionListener {

    private static final long serialVersionUID = 1L;

    // explicit colours and logical fonts so the HUD looks the same on macOS and Windows
    private static final Color BG = new Color(22, 20, 26);
    private static final Color BAR_BG = new Color(36, 32, 40);
    private static final Color EDGE = new Color(78, 70, 82);
    private static final Color TEXT = new Color(232, 226, 214);
    private static final Color DIM = new Color(120, 114, 108);
    private static final Color ACCENT = new Color(255, 196, 70);
    private static final Color HP_FILL = new Color(200, 48, 44);
    private static final Color HP_LOW = new Color(255, 90, 40);
    private static final Color WAVE_FILL = new Color(70, 130, 200);
    private static final Color WARN = new Color(255, 96, 80);
    private static final Font FONT = new Font(Font.SANS_SERIF, Font.BOLD, 13);
    private static final Font FONT_SMALL = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    private static final Font FONT_TIMER = new Font(Font.MONOSPACED, Font.BOLD, 18);

    private final transient GameData.GameSettings settings;
    private final MainApplication launcher;   // null when the window is started on its own (tests)
    private final GamePanel gamePanel;

    // top HUD
    private JProgressBar hpBar;
    private JLabel armorLabel;
    private JLabel waveLabel;
    private JProgressBar waveBar;
    private JLabel timerLabel;
    private JLabel scoreLabel;
    private JLabel killsLabel;
    private JButton pauseButton;
    private JButton quitButton;
    // bottom HUD
    private JLabel weaponIconLabel;
    private JLabel weaponLabel;
    private JLabel ammoLabel;
    private final transient Map<GameData.ItemType, JLabel> itemLabels = new EnumMap<>(GameData.ItemType.class);
    private JButton workshopButton;
    private JCheckBox autoFireBox;
    private JCheckBox hitboxBox;

    private GameData.WeaponType shownWeapon;   // the weapon icon is only rebuilt when this changes
    private boolean dialogOpen;
    private boolean finished;                  // onGameOver already ran
    private boolean closed;                    // threads stopped and window disposed

    /** Builds the window, shows it and starts the game. launcher may be null. */
    public GameFrame(GameData.GameSettings settings, MainApplication launcher) {
        super("Last Camp Survivor");
        useCrossPlatformLookAndFeel();   // must happen before any component is created
        this.settings = settings == null ? new GameData.GameSettings() : settings;
        this.launcher = launcher;
        gamePanel = new GamePanel(this.settings, this);

        setTitle("Last Camp Survivor - " + gamePanel.getPlayer().getPlayerName() + " ("
                + gamePanel.getPlayer().getPlayerClass().displayName + ") - " + this.settings.map);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeGame();   // the X button quits the run like the Quit button (without asking)
            }
        });
        setContentPane(buildContent());
        setSize(GameData.GAME_W, GameData.GAME_H);
        setResizable(false);
        setLocationRelativeTo(null);
        refreshHud();
        setVisible(true);
        gamePanel.startGame();
        gamePanel.requestFocusInWindow();
    }

    /** The platform look (Aqua on a Mac) ignores our colours, so the game always uses Swing's own look. */
    private static void useCrossPlatformLookAndFeel() {
        String wanted = UIManager.getCrossPlatformLookAndFeelClassName();
        if (UIManager.getLookAndFeel().getClass().getName().equals(wanted)) {
            return;
        }
        try {
            UIManager.setLookAndFeel(wanted);
        } catch (ReflectiveOperationException | UnsupportedLookAndFeelException ex) {
            System.err.println("Could not switch the look and feel: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ layout
    private JPanel buildContent() {
        JPanel content = new JPanel(new BorderLayout(0, 6));
        content.setBackground(BG);
        content.setBorder(BorderFactory.createEmptyBorder(6, 10, 8, 10));   // keep the HUD away from the window edge
        content.add(buildTopBar(), BorderLayout.NORTH);
        JPanel gameHolder = new JPanel(new BorderLayout());
        gameHolder.setBorder(BorderFactory.createLineBorder(EDGE, 2));
        gameHolder.add(gamePanel, BorderLayout.CENTER);
        content.add(gameHolder, BorderLayout.CENTER);
        content.add(buildBottomBar(), BorderLayout.SOUTH);
        return content;
    }

    private JPanel buildTopBar() {
        hpBar = makeBar(HP_FILL, 190);
        armorLabel = makeLabel("Armor 0%", FONT_SMALL, DIM);
        JPanel left = makeRow(FlowLayout.LEFT);
        left.add(makeLabel("HP", FONT, ACCENT));
        left.add(hpBar);
        left.add(armorLabel);

        waveLabel = makeLabel("WAVE 1", FONT, ACCENT);
        waveBar = makeBar(WAVE_FILL, 160);
        timerLabel = makeLabel("5:00 left", FONT_TIMER, TEXT);
        JPanel middle = makeRow(FlowLayout.CENTER);
        middle.add(waveLabel);
        middle.add(waveBar);
        middle.add(timerLabel);

        scoreLabel = makeLabel("Score 0", FONT, TEXT);
        killsLabel = makeLabel("Kills 0", FONT, TEXT);
        pauseButton = makeButton("Pause (P)", new Color(60, 70, 96));
        quitButton = makeButton("Quit", new Color(110, 40, 40));
        JPanel right = makeRow(FlowLayout.RIGHT);
        right.add(scoreLabel);
        right.add(killsLabel);
        right.add(pauseButton);
        right.add(quitButton);

        return makeBarPanel(left, middle, right);
    }

    private JPanel buildBottomBar() {
        weaponIconLabel = new JLabel();
        weaponIconLabel.setPreferredSize(new Dimension(36, 36));
        weaponIconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        weaponLabel = makeLabel("Pistol", FONT, TEXT);
        ammoLabel = makeLabel("Ammo 0", FONT_SMALL, DIM);
        JPanel weaponText = new JPanel(new GridLayout(2, 1));
        weaponText.setOpaque(false);
        weaponText.add(weaponLabel);
        weaponText.add(ammoLabel);
        weaponText.setPreferredSize(new Dimension(120, 36));
        JPanel left = makeRow(FlowLayout.LEFT);
        left.add(weaponIconLabel);
        left.add(weaponText);

        // the inventory strip: one icon + count per item type (chests are never stored)
        JPanel middle = makeRow(FlowLayout.CENTER);
        ((FlowLayout) middle.getLayout()).setHgap(3);
        for (GameData.ItemType t : GameData.ItemType.values()) {
            if (t == GameData.ItemType.CHEST) {
                continue;
            }
            JLabel label = makeLabel("0", FONT_SMALL, TEXT);
            label.setIcon(new ImageIcon(scaledIcon(AssetLoader.getItemIcon(t), 2)));
            label.setIconTextGap(1);
            label.setToolTipText(t.displayName);
            // fixed widths keep the strip from jumping when a count changes (ammo can have 3 digits)
            label.setPreferredSize(new Dimension(t == GameData.ItemType.AMMO ? 58 : 48, 34));
            itemLabels.put(t, label);
            middle.add(label);
        }

        workshopButton = makeButton("Open Workshop (B)", new Color(150, 96, 30));
        autoFireBox = makeCheckBox("Auto-Fire", settings.autoFire);
        hitboxBox = makeCheckBox("Show Hitbox", settings.showHitbox);
        JPanel options = new JPanel(new GridLayout(2, 1));
        options.setOpaque(false);
        options.add(autoFireBox);
        options.add(hitboxBox);
        JPanel right = makeRow(FlowLayout.RIGHT);
        right.add(workshopButton);
        right.add(options);

        return makeBarPanel(left, middle, right);
    }

    /** A dark HUD strip with three groups: left, centre and right. */
    private static JPanel makeBarPanel(JComponent left, JComponent middle, JComponent right) {
        JPanel bar = new JPanel(new BorderLayout(6, 0));
        bar.setBackground(BAR_BG);
        bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(EDGE, 1),
                BorderFactory.createEmptyBorder(3, 6, 3, 6)));
        bar.add(left, BorderLayout.WEST);
        bar.add(middle, BorderLayout.CENTER);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private static JPanel makeRow(int align) {
        JPanel row = new JPanel(new FlowLayout(align, 8, 2));
        row.setOpaque(false);
        return row;
    }

    private static JLabel makeLabel(String text, Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    private JButton makeButton(String text, Color background) {
        JButton b = new JButton(text);
        b.setFont(FONT);
        b.setBackground(background);
        b.setForeground(Color.WHITE);
        b.setFocusable(false);        // the GamePanel must keep the keyboard
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(background.brighter(), 1),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)));
        b.addActionListener(this);
        return b;
    }

    private JCheckBox makeCheckBox(String text, boolean selected) {
        JCheckBox box = new JCheckBox(text, selected);
        box.setFont(FONT_SMALL);
        box.setForeground(TEXT);
        box.setOpaque(false);
        box.setFocusable(false);
        box.addActionListener(this);
        return box;
    }

    private static JProgressBar makeBar(Color fill, int width) {
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setUI(new HudBarUI());
        bar.setStringPainted(true);
        bar.setFont(FONT_SMALL);
        bar.setForeground(fill);
        bar.setBackground(new Color(20, 18, 22));
        bar.setBorder(BorderFactory.createLineBorder(EDGE, 1));
        bar.setPreferredSize(new Dimension(width, 22));
        bar.setFocusable(false);
        return bar;
    }

    /** Plain flat progress bar with white text, identical on every platform. */
    private static final class HudBarUI extends BasicProgressBarUI {
        @Override
        protected Color getSelectionForeground() {
            return Color.WHITE;
        }

        @Override
        protected Color getSelectionBackground() {
            return Color.WHITE;
        }
    }

    /** Pixel art icons are enlarged without smoothing so they stay crisp. */
    private static BufferedImage scaledIcon(BufferedImage src, int factor) {
        BufferedImage out = new BufferedImage(src.getWidth() * factor, src.getHeight() * factor, BufferedImage.TYPE_INT_ARGB);
        Graphics g = out.getGraphics();
        g.drawImage(src, 0, 0, out.getWidth(), out.getHeight(), null);
        g.dispose();
        return out;
    }

    // ------------------------------------------------------------------ HUD refresh
    /** Copies the current game state into the HUD components. Runs on the Swing thread. */
    public void refreshHud() {
        if (closed) {
            return;
        }
        Player p = gamePanel.getPlayer();
        hpBar.setMaximum(p.getMaxHp());
        hpBar.setValue(p.getHp());
        hpBar.setString(p.getHp() + " / " + p.getMaxHp());
        hpBar.setForeground(p.getHp() * 100 < p.getMaxHp() * 30 ? HP_LOW : HP_FILL);
        armorLabel.setText("Armor " + p.getArmorPercent() + "%");
        refreshWaveAndTime();
        scoreLabel.setText("Score " + gamePanel.getScore());
        killsLabel.setText("Kills " + gamePanel.getKills());
        pauseButton.setText(gamePanel.isPaused() ? "Resume (P)" : "Pause (P)");
        refreshWeapon(p);
        Map<GameData.ItemType, Integer> inventory = p.getInventory();
        for (Map.Entry<GameData.ItemType, JLabel> e : itemLabels.entrySet()) {
            int count = inventory.get(e.getKey());
            e.getValue().setText(String.valueOf(count));
            e.getValue().setForeground(count > 0 ? TEXT : DIM);
        }
    }

    private void refreshWaveAndTime() {
        int wave = gamePanel.getWave();
        int elapsed = gamePanel.getElapsedSeconds();
        int left = Math.max(0, gamePanel.getTimeLimitSeconds() - elapsed);
        int nextWaveIn = GameData.WAVE_SECONDS - elapsed % GameData.WAVE_SECONDS;
        waveLabel.setText("WAVE " + wave);
        waveBar.setValue(gamePanel.getWaveProgressPercent());
        if (left <= GameData.WAVE_SECONDS) {
            waveBar.setString("final wave");
        } else if ((wave + 1) % GameData.BOSS_EVERY_WAVES == 0) {
            waveBar.setString("BOSS in " + formatTime(nextWaveIn));
        } else {
            waveBar.setString("next wave " + formatTime(nextWaveIn));
        }
        timerLabel.setText(formatTime(left) + " left");
        timerLabel.setForeground(left <= 30 ? ACCENT : TEXT);
    }

    private void refreshWeapon(Player p) {
        GameData.WeaponType w = p.getCurrentWeapon();
        if (w != shownWeapon) {
            shownWeapon = w;
            weaponIconLabel.setIcon(new ImageIcon(AssetLoader.getWeaponIcon(w)));
            weaponLabel.setText(w.displayName);
        }
        if (w.melee) {
            ammoLabel.setText("Melee - no ammo");
            ammoLabel.setForeground(DIM);
        } else if (w.ammoCost == 0) {
            ammoLabel.setText("Ammo: infinite (" + p.getAmmo() + ")");
            ammoLabel.setForeground(DIM);
        } else {
            ammoLabel.setText("Ammo " + p.getAmmo());
            ammoLabel.setForeground(p.getAmmo() < w.ammoCost * 5 ? WARN : TEXT);
        }
    }

    private static String formatTime(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    // ------------------------------------------------------------------ buttons and check boxes
    @Override
    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();
        if (source == workshopButton) {
            openUpgradeDialog();
        } else if (source == pauseButton) {
            if (gamePanel.isPaused()) {
                gamePanel.resumeGame();
            } else {
                gamePanel.pauseGame();
            }
            refreshHud();
        } else if (source == quitButton) {
            confirmQuit();
        } else if (source == autoFireBox) {
            gamePanel.setAutoFire(autoFireBox.isSelected());
        } else if (source == hitboxBox) {
            gamePanel.setShowHitbox(hitboxBox.isSelected());
        }
        gamePanel.requestFocusInWindow();   // clicking the HUD must not steal the keyboard
    }

    /** Pauses the game, shows the modal workshop, and continues the game when it is closed. */
    public void openUpgradeDialog() {
        if (dialogOpen || closed || gamePanel.isGameOver()) {
            return;
        }
        dialogOpen = true;
        boolean wasPaused = gamePanel.isPaused();
        gamePanel.pauseGame();
        try {
            UpgradeDialog dialog = new UpgradeDialog(this, gamePanel.getPlayer());
            dialog.setVisible(true);   // modal: this line waits until the dialog is closed
        } finally {
            dialogOpen = false;
        }
        if (closed) {
            return;
        }
        if (!wasPaused) {
            gamePanel.resumeGame();
        }
        refreshHud();
        gamePanel.requestFocusInWindow();
    }

    private void confirmQuit() {
        boolean wasPaused = gamePanel.isPaused();
        gamePanel.pauseGame();
        int answer = JOptionPane.showConfirmDialog(this, "Quit this run? It will not be saved.",
                "Quit", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer == JOptionPane.YES_OPTION) {
            closeGame();
        } else if (!wasPaused) {
            gamePanel.resumeGame();
        }
    }

    // ------------------------------------------------------------------ end of the run
    /** Called once by GamePanel: save the score, show the summary, go back to the launcher. */
    public void onGameOver(boolean survived) {
        if (finished || closed) {
            return;
        }
        finished = true;
        refreshHud();
        GameData.ScoreEntry entry = new GameData.ScoreEntry();
        entry.name = gamePanel.getPlayer().getPlayerName();
        entry.playerClass = gamePanel.getPlayer().getPlayerClass().displayName;
        entry.map = settings.map == null ? "-" : settings.map.displayName;
        entry.score = gamePanel.getScore();
        entry.kills = gamePanel.getKills();
        entry.wave = gamePanel.getWave();
        entry.seconds = gamePanel.getElapsedSeconds();
        GameData.saveScore(entry);
        JOptionPane.showMessageDialog(this, summaryText(survived, entry),
                survived ? "You survived!" : "Game over", JOptionPane.PLAIN_MESSAGE);
        closeGame();
    }

    private static String summaryText(boolean survived, GameData.ScoreEntry e) {
        return "<html><h2>" + (survived ? "You survived the horde!" : "You were overrun.") + "</h2>"
                + "<table>"
                + "<tr><td>Player</td><td><b>" + e.name + "</b> (" + e.playerClass + ")</td></tr>"
                + "<tr><td>Map</td><td>" + e.map + "</td></tr>"
                + "<tr><td>Score</td><td><b>" + e.score + "</b></td></tr>"
                + "<tr><td>Kills</td><td>" + e.kills + "</td></tr>"
                + "<tr><td>Wave</td><td>" + e.wave + "</td></tr>"
                + "<tr><td>Time</td><td>" + formatTime(e.seconds) + "</td></tr>"
                + "</table><br>Your score was saved to the high-score table.</html>";
    }

    /** Stops both game threads and the music, closes the window and shows the launcher again. */
    private void closeGame() {
        if (closed) {
            return;
        }
        closed = true;
        gamePanel.stopGame();
        dispose();
        if (launcher != null) {
            launcher.onGameFinished();
        }
    }
}
