// Tapatsawin Chaieua          6813105
// Thanapon Thanadunpremdet    6813106
// Tanawat Wongwad             6813107
// Natthanon Achachan          6813241
package Project3_6813107;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.border.EmptyBorder;

/**
 * The game window (1100 x 660), drawn with the Hollow Creek UI kit: a metal HUD plate on top
 * (heart, health bar, status icons, beat stage and combo, score, kills, auto-fire, pause, quit),
 * the GamePanel in the middle, and a metal plate at the bottom (all 7 weapons unlocked, INF ammo,
 * pure rhythm control strip, and live Rank / Max Combo readout).
 */
public final class GameFrame extends JFrame implements ActionListener {

    private static final long serialVersionUID = 1L;

    private final transient GameData.GameSettings settings;
    private final MainApplication launcher;   // null when the window is started on its own (tests)
    private final GamePanel gamePanel;

    // top HUD
    private JLabel heartIcon;
    private JProgressBar hpBar;
    private Theme.PixelLabel hpValueLabel;
    private JLabel poisonedIcon;   // kit status icons, shown only while they apply
    private JLabel injuredIcon;
    private JLabel bleedingIcon;
    private Theme.PixelLabel waveLabel;
    private Theme.PixelLabel timerLabel;
    private JProgressBar waveBar;
    private Theme.PixelLabel scoreValueLabel;
    private Theme.PixelLabel killsValueLabel;
    private JCheckBox autoFireBox;
    private JButton pauseButton;
    private JButton quitButton;
    // bottom HUD
    private HotbarView hotbar;
    private AmmoView ammoView;
    private Theme.PixelLabel rhythmRankLabel;

    private String shownHeart = "";            // icons are only swapped when they change
    private boolean dialogOpen;
    private boolean finished;                  // onGameOver already ran
    private boolean closed;                    // threads stopped and window disposed

    /** Builds the window, shows it and starts the game. launcher may be null. */
    public GameFrame(GameData.GameSettings settings, MainApplication launcher) {
        super("Beat Highway Shooter");
        Theme.installLookAndFeel();   // does nothing if the launcher already did it
        this.settings = settings == null ? new GameData.GameSettings() : settings;
        this.launcher = launcher;
        gamePanel = new GamePanel(this.settings, this);

        setTitle("Beat Highway Shooter - " + gamePanel.getPlayer().getPlayerName() + " the "
                + gamePanel.getPlayer().getPlayerClass().displayName);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeGame();   // the X button quits the run like the Quit button (without asking)
            }
        });
        setContentPane(buildContent());
        getRootPane().setCursor(Theme.arrowCursor());
        setSize(GameData.GAME_W, GameData.GAME_H);
        setResizable(false);
        setLocationRelativeTo(null);
        refreshHud();
        setVisible(true);
        gamePanel.startGame();
        gamePanel.requestFocusInWindow();
    }

    // ------------------------------------------------------------------ layout
    private JPanel buildContent() {
        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(Theme.INK);
        content.add(buildTopBar(), BorderLayout.NORTH);
        content.add(gamePanel, BorderLayout.CENTER);
        content.add(buildBottomBar(), BorderLayout.SOUTH);
        return content;
    }

    /** Heart, health, status | beat stage, combo, fever | score, kills, auto-fire, pause, quit. */
    private JComponent buildTopBar() {
        heartIcon = new JLabel();
        hpBar = Theme.bar(Theme.Fill.HEALTH, 140, Theme.S);
        hpValueLabel = new Theme.PixelLabel("100/100", 2, Theme.TEXT);
        poisonedIcon = statusIcon("poisoned", "Poisoned: parry dash or land kills to heal");
        injuredIcon = statusIcon("injured", "Low HP: parry dash (+6 HP) or chain kills");
        bleedingIcon = statusIcon("bleeding", "You were just hit");
        JPanel left = Theme.row(8, heartIcon, column(stack(hpBar), hpValueLabel),
                Theme.row(2, poisonedIcon, injuredIcon, bleedingIcon));

        waveLabel = new Theme.PixelLabel("125 BPM OVERDRIVE - STAGE 1  5:00", 2, Theme.TEXT_2);
        timerLabel = new Theme.PixelLabel("COMBO x0", 3, Theme.TEXT);
        timerLabel.setShadow(true);
        waveBar = Theme.bar(Theme.Fill.STAMINA, 180, 1);
        JPanel middle = Theme.transparentPanel(new GridBagLayout());
        middle.add(column(centred(waveLabel), centred(timerLabel), centred(waveBar)));

        scoreValueLabel = new Theme.PixelLabel("0 - 1X", 2, Theme.GOLD);
        scoreValueLabel.setToolTipText("Total Rhythm Score and Combo Multiplier");
        killsValueLabel = new Theme.PixelLabel("0", 3, Theme.TEXT);
        autoFireBox = new JCheckBox("Auto-fire", settings.autoFire);
        Theme.styleCheckBox(autoFireBox);
        autoFireBox.setFocusable(false);
        autoFireBox.addActionListener(this);
        pauseButton = makeButton("Pause", Theme.ButtonStyle.GREY, "p");
        quitButton = makeButton("Quit", Theme.ButtonStyle.GREY, null);
        JPanel right = Theme.row(10, column(Theme.caption("Score"), scoreValueLabel),
                column(Theme.caption("Kills"), killsValueLabel), autoFireBox, Theme.row(4, pauseButton, quitButton));

        return makePlate(left, middle, right);
    }

    /** Weapon hotbar (1-7 INF) | rhythm controls guide | live Rank & Max Combo readout. */
    private JComponent buildBottomBar() {
        hotbar = new HotbarView();
        ammoView = new AmmoView();
        JPanel left = Theme.row(10, hotbar, ammoView);

        JLabel guideLabel = new JLabel("SPACE/CLICK: BEAT SHOT | SHIFT/R-CLICK: PARRY (+6 HP) | 1-7: GUNS");
        guideLabel.setFont(Theme.font(Font.BOLD, 11));
        guideLabel.setForeground(Theme.TEXT_2);
        JPanel middle = Theme.transparentPanel(new GridBagLayout());
        middle.add(guideLabel);

        rhythmRankLabel = new Theme.PixelLabel("RANK A  MAX 0X  ACC 100%", 2, Theme.GOLD);
        return makePlate(left, middle, centred(rhythmRankLabel));
    }

    /** A HUD strip on the kit's metal panel: left, centre and right groups. */
    private static JComponent makePlate(JComponent left, JComponent middle, JComponent right) {
        Theme.Card plate = new Theme.Card(new BorderLayout(10, 0), Theme.Panel.METAL);
        plate.setBorder(new EmptyBorder(8, 14, 8, 14));
        plate.add(centred(left), BorderLayout.WEST);
        plate.add(middle, BorderLayout.CENTER);
        plate.add(centred(right), BorderLayout.EAST);
        return plate;
    }

    private static JPanel centred(JComponent c) {
        JPanel holder = Theme.transparentPanel(new GridBagLayout());
        holder.add(c);
        return holder;
    }

    /** Components stacked top to bottom, left-aligned, 4 px apart. */
    private static JPanel column(JComponent... parts) {
        JPanel col = Theme.transparentPanel(new java.awt.GridLayout(0, 1, 0, 4));
        for (JComponent part : parts) {
            JPanel line = Theme.transparentPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            line.add(part);
            col.add(line);
        }
        return col;
    }

    /** One component on its own, so a JProgressBar keeps its preferred size inside a column. */
    private static JPanel stack(JComponent c) {
        JPanel holder = Theme.transparentPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        holder.add(c);
        return holder;
    }

    private static JLabel statusIcon(String name, String tooltip) {
        JLabel icon = new JLabel(new ImageIcon(Theme.kit("status_icons/" + name, Theme.S)));
        icon.setToolTipText(tooltip);
        icon.setVisible(false);
        return icon;
    }

    private JButton makeButton(String text, Theme.ButtonStyle style, String keyHint) {
        JButton b = Theme.button(text.toUpperCase(), style);
        if (keyHint != null) {
            Theme.setKeyHint(b, keyHint);
        }
        b.setFocusable(false);   // the GamePanel must keep the keyboard
        b.addActionListener(this);
        return b;
    }

    // ------------------------------------------------------------------ HUD refresh
    /** Copies the current game state into the HUD components. Runs on the Swing thread. */
    public void refreshHud() {
        if (closed) {
            return;
        }
        Player p = gamePanel.getPlayer();
        int hpPercent = p.getHp() * 100 / Math.max(1, p.getMaxHp());
        hpBar.setMaximum(p.getMaxHp());
        hpBar.setValue(p.getHp());
        hpValueLabel.setText(p.getHp() + "/" + p.getMaxHp());
        hpValueLabel.setColor(hpPercent < 30 ? Theme.RED : Theme.TEXT);
        String heart = hpPercent <= 0 ? "heart_empty" : hpPercent < 50 ? "heart_half" : "heart_full";
        if (!heart.equals(shownHeart)) {
            shownHeart = heart;
            heartIcon.setIcon(new ImageIcon(Theme.fit(Theme.kit("bars/" + heart, 1), 27, 24)));
        }
        poisonedIcon.setVisible(p.isPoisoned());
        injuredIcon.setVisible(p.isAlive() && hpPercent < 30);
        bleedingIcon.setVisible(p.isHurt());
        refreshWaveAndTime();
        scoreValueLabel.setText(gamePanel.getScore() + " - " + gamePanel.getComboMultiplier() + "X");
        killsValueLabel.setText(String.valueOf(gamePanel.getKills()));
        pauseButton.setText(gamePanel.isPaused() ? "RESUME" : "PAUSE");
        hotbar.update(p);
        ammoView.update(p);
        rhythmRankLabel.setText("RANK " + gamePanel.getRhythmRank() + "  MAX " + gamePanel.getMaxCombo()
                + "X  ACC " + gamePanel.getAccuracyPercent() + "%");
    }

    private void refreshWaveAndTime() {
        int elapsed = gamePanel.getElapsedSeconds();
        int left = Math.max(0, gamePanel.getTimeLimitSeconds() - elapsed);
        int stage = gamePanel.getStageNumber();
        boolean fever = gamePanel.isFeverActive();
        String status = fever
                ? ("125 BPM FEVER - STAGE " + stage + "  " + formatTime(left))
                : ("125 BPM OVERDRIVE - STAGE " + stage + "  " + formatTime(left));
        waveLabel.setText(status);
        waveLabel.setColor(fever ? Theme.GOLD : new Color(0x5CE8FF));
        waveBar.setValue(gamePanel.getFeverPercent());
        timerLabel.setText("COMBO x" + gamePanel.getCombo());
        timerLabel.setColor(fever ? Theme.GOLD : (gamePanel.getCombo() >= 10 ? new Color(0x5CE8FF) : Theme.TEXT));
    }

    private static String formatTime(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    /**
     * The kit hotbar with all eight weapons in key order (1 = Knife ... 8 = Rocket Launcher):
     * gold slot = in your hands, normal slot = owned, lock = not crafted yet.
     */
    private static final class HotbarView extends JComponent {
        private static final long serialVersionUID = 1L;
        private static final GameData.WeaponType[] WEAPONS = GameData.HOTBAR_WEAPONS;
        private final transient BufferedImage[] icons = new BufferedImage[WEAPONS.length];
        private final boolean[] owned = new boolean[WEAPONS.length];
        private GameData.WeaponType current;

        HotbarView() {
            for (int i = 0; i < WEAPONS.length; i++) {
                icons[i] = Theme.fit(AssetLoader.getWeaponIcon(WEAPONS[i]), 32, 32);
            }
            setPreferredSize(new Dimension(Theme.hotbarWidth(WEAPONS.length, Theme.S), 26 * Theme.S));
            setToolTipText("Weapons: press 1-7 or turn the mouse wheel");
        }

        void update(Player p) {
            List<GameData.WeaponType> mine = p.getOwnedWeapons();
            boolean changed = p.getCurrentWeapon() != current;
            current = p.getCurrentWeapon();
            for (int i = 0; i < WEAPONS.length; i++) {
                boolean has = mine.contains(WEAPONS[i]);
                changed |= has != owned[i];
                owned[i] = has;
            }
            if (changed) {
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            int s = Theme.S;
            Theme.drawHotbarFrame(g2, 0, 0, WEAPONS.length, s);
            for (int i = 0; i < WEAPONS.length; i++) {
                int x = (3 + i * 22) * s, y = 3 * s;
                Theme.SlotKind kind = !owned[i] ? Theme.SlotKind.LOCKED
                        : WEAPONS[i] == current ? Theme.SlotKind.SELECTED : Theme.SlotKind.NORMAL;
                Theme.drawSlot(g2, kind, x, y, s);
                if (owned[i]) {
                    BufferedImage icon = icons[i];
                    g2.drawImage(icon, x + (20 * s - icon.getWidth()) / 2, y + (20 * s - icon.getHeight()) / 2, null);
                }
                Theme.drawPixelTextShadow(g2, String.valueOf(i + 1), x + 3 * s, y + 3 * s, 1, owned[i] ? Theme.TEXT : Theme.TEXT_3);
            }
            g2.dispose();
        }
    }

    /** The kit's ammo counter with the ammo you carry ("INF" for the pistol, "--" for melee). */
    private static final class AmmoView extends JComponent {
        private static final long serialVersionUID = 1L;
        private String text = "0";

        AmmoView() {
            setToolTipText("Infinite Rhythm Ammo on all firearms");
            setPreferredSize(new Dimension(Theme.ammoCounterWidth("000", Theme.S), 16 * Theme.S));
        }

        void update(Player p) {
            GameData.WeaponType w = p.getCurrentWeapon();
            String t = w.melee ? "--" : "INF";
            if (!t.equals(text)) {
                text = t;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Theme.drawAmmoCounter((Graphics2D) g, text, 0, (getHeight() - 16 * Theme.S) / 2, Theme.S);
        }
    }

    // ------------------------------------------------------------------ buttons and the check box
    @Override
    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();
        if (source == pauseButton) {
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
        boolean quit = Theme.ask(this, Theme.Panel.DANGER, null, "QUIT THIS RUN?",
                "Your score from this run will not be saved.", null, "Quit", "Keep playing");
        if (quit) {
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
        String[][] stats = {
            {"Rhythm Rank", gamePanel.getRhythmRank()},
            {"Max Combo", gamePanel.getMaxCombo() + "X"},
            {"Beat Accuracy", gamePanel.getAccuracyPercent() + "%"},
            {"Zombies Cleared", String.valueOf(entry.kills)},
            {"Stage Reached", "Stage " + gamePanel.getStageNumber() + " / 5"}
        };
        Theme.ask(this, Theme.Panel.METAL, survived ? "titles/survived" : "titles/game_over",
                survived ? "Stage Clear" : "Game over",
                (survived ? "You cleared the 125 BPM highway overdrive! " : "The zombie highway overwhelmed you. ")
                        + "Your rhythm score is saved.",
                stats, "Continue", null);
        closeGame();
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
