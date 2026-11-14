// Tapatsawin Chaieua          6813105
// Thanapon Thanadunpremdet    6813106
// Tanawat Wongwad             6813107
// Natthanon Achachan          6813241
package Project3_6813107;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
 * (heart, health bar, armour segments, status icons, wave and time, score, kills, auto-fire,
 * pause, quit), the GamePanel in the middle, and a metal plate at the bottom (weapon hotbar,
 * ammo counter, backpack slots, WORKSHOP button).
 *
 * The HUD is made of real Swing components. GamePanel asks for refreshHud() a few times per second
 * through SwingUtilities.invokeLater, so the components are only touched on the Swing thread.
 * No HUD component can take the keyboard focus, so W A S D always reach the GamePanel.
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
    private JLabel armorSegments;
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
    private final transient Map<GameData.ItemType, Theme.Slot> itemSlots = new EnumMap<>(GameData.ItemType.class);
    private JLabel emptyBackpackLabel;
    private JPanel backpackRow;
    private JButton workshopButton;

    private String shownHeart = "";            // icons are only swapped when they change
    private int shownArmorLevel = -1;
    private boolean dialogOpen;
    private boolean finished;                  // onGameOver already ran
    private boolean closed;                    // threads stopped and window disposed

    /** Builds the window, shows it and starts the game. launcher may be null. */
    public GameFrame(GameData.GameSettings settings, MainApplication launcher) {
        super("Last Camp Survivor");
        Theme.installLookAndFeel();   // does nothing if the launcher already did it
        this.settings = settings == null ? new GameData.GameSettings() : settings;
        this.launcher = launcher;
        gamePanel = new GamePanel(this.settings, this);

        setTitle("Last Camp Survivor - " + gamePanel.getPlayer().getPlayerName() + " the "
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

    /** Heart, health, armour, status | wave and time | score, kills, auto-fire, pause, quit. */
    private JComponent buildTopBar() {
        heartIcon = new JLabel();
        hpBar = Theme.bar(Theme.Fill.HEALTH, 140, Theme.S);
        hpValueLabel = new Theme.PixelLabel("100/100", 2, Theme.TEXT);
        armorSegments = new JLabel();
        armorSegments.setToolTipText("Armour level (Workshop: Craft Armor Plating)");
        poisonedIcon = statusIcon("poisoned", "Poisoned: you lose HP for a few seconds");
        injuredIcon = statusIcon("injured", "Badly hurt: press E to use a medkit");
        bleedingIcon = statusIcon("bleeding", "You were just hit");
        JPanel left = Theme.row(8, heartIcon, column(stack(hpBar), hpValueLabel),
                column(armorSegments, Theme.caption("Armor")), Theme.row(2, poisonedIcon, injuredIcon, bleedingIcon));

        waveLabel = new Theme.PixelLabel("DAY 1: DAYLIGHT - SAFE CAMP", 2, Theme.TEXT_2);
        timerLabel = new Theme.PixelLabel("5:00", 4, Theme.TEXT);
        timerLabel.setShadow(true);
        waveBar = Theme.bar(Theme.Fill.STAMINA, 170, 1);
        JPanel middle = Theme.transparentPanel(new GridBagLayout());
        middle.add(column(centred(waveLabel), centred(timerLabel), centred(waveBar)));

        scoreValueLabel = new Theme.PixelLabel("1 NPC", 2, Theme.GOLD);
        scoreValueLabel.setToolTipText("Active NPC allies & camp guards (recruit more at Base Camp [B])");
        killsValueLabel = new Theme.PixelLabel("0", 3, Theme.TEXT);
        autoFireBox = new JCheckBox("Auto-fire", settings.autoFire);
        Theme.styleCheckBox(autoFireBox);
        autoFireBox.setFocusable(false);
        autoFireBox.addActionListener(this);
        pauseButton = makeButton("Pause", Theme.ButtonStyle.GREY, "p");
        quitButton = makeButton("Quit", Theme.ButtonStyle.GREY, null);
        JPanel right = Theme.row(10, column(Theme.caption("Allies"), scoreValueLabel),
                column(Theme.caption("Kills"), killsValueLabel), autoFireBox, Theme.row(4, pauseButton, quitButton));

        return makePlate(left, middle, right);
    }

    /** Weapon hotbar and ammo | backpack slots | WORKSHOP button. */
    private JComponent buildBottomBar() {
        hotbar = new HotbarView();
        ammoView = new AmmoView();
        JPanel left = Theme.row(10, hotbar, ammoView);

        // one slot per item type; only items you actually have are shown
        backpackRow = Theme.transparentPanel(new FlowLayout(FlowLayout.CENTER, 2, 0));
        for (GameData.ItemType t : GameData.ItemType.values()) {
            if (t == GameData.ItemType.CHEST) {
                continue;   // chests open on the ground, they are never stored
            }
            Theme.Slot slot = new Theme.Slot(Theme.fit(AssetLoader.getItemIcon(t), 24, 24));
            slot.setToolTipText(t.displayName);
            slot.setVisible(false);
            itemSlots.put(t, slot);
            backpackRow.add(slot);
        }
        emptyBackpackLabel = new JLabel("Pack empty - scavenge outside & loot chests");
        emptyBackpackLabel.setFont(Theme.font(java.awt.Font.PLAIN, 12));
        emptyBackpackLabel.setForeground(Theme.TEXT_3);
        backpackRow.add(emptyBackpackLabel);
        JPanel middle = Theme.transparentPanel(new GridBagLayout());   // centres the row vertically
        middle.add(backpackRow);

        workshopButton = makeButton("Base NPC", Theme.ButtonStyle.RED, "b");
        return makePlate(left, middle, centred(workshopButton));
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
        int armorLevel = Math.min(5, p.getLevel(GameData.RecipeKind.ARMOR));
        if (armorLevel != shownArmorLevel) {
            shownArmorLevel = armorLevel;
            armorSegments.setIcon(new ImageIcon(Theme.kit("bars/segments/health_segments_" + armorLevel + "of5", Theme.S)));
        }
        armorSegments.setToolTipText("Armour " + p.getArmorPercent() + "% less damage (level " + armorLevel + " of 5)");
        poisonedIcon.setVisible(p.isPoisoned());
        injuredIcon.setVisible(p.isAlive() && hpPercent < 30);
        bleedingIcon.setVisible(p.isHurt());
        refreshWaveAndTime();
        scoreValueLabel.setText(gamePanel.getAllyCount() + " NPC");
        killsValueLabel.setText(String.valueOf(gamePanel.getKills()));
        pauseButton.setText(gamePanel.isPaused() ? "RESUME" : "PAUSE");
        hotbar.update(p);
        ammoView.update(p);
        refreshBackpack(p.getInventory());
    }

    private void refreshWaveAndTime() {
        int day = gamePanel.getDay();
        int elapsed = gamePanel.getElapsedSeconds();
        int left = Math.max(0, gamePanel.getTimeLimitSeconds() - elapsed);
        int phaseLeft = Math.max(0, gamePanel.getPhaseSecondsRemaining());
        String status = gamePanel.isNight()
                ? "DAY " + day + " NIGHT - DAWN " + formatTime(phaseLeft)
                : "DAY " + day + " DAY - NIGHT " + formatTime(phaseLeft);
        waveLabel.setText(status);
        waveLabel.setColor(gamePanel.isNight() ? Theme.RED : (gamePanel.isPlayerInBase() ? new Color(0x88D470) : Theme.GOLD));
        waveBar.setValue(gamePanel.getDayCycleProgressPercent());
        timerLabel.setText(formatTime(left));
        timerLabel.setColor(left <= 30 ? Theme.GOLD : Theme.TEXT);
    }

    /** A kit slot for every item you hold; the row only re-lays out when that set changes. */
    private void refreshBackpack(Map<GameData.ItemType, Integer> inventory) {
        boolean layoutChanged = false;
        boolean any = false;
        for (Map.Entry<GameData.ItemType, Theme.Slot> e : itemSlots.entrySet()) {
            int count = inventory.get(e.getKey());
            Theme.Slot slot = e.getValue();
            slot.update(Theme.SlotKind.NORMAL, Theme.fit(AssetLoader.getItemIcon(e.getKey()), 24, 24),
                    String.valueOf(count), false);
            slot.setToolTipText(e.getKey().displayName + ": " + count);
            if (slot.isVisible() != (count > 0)) {
                slot.setVisible(count > 0);
                layoutChanged = true;
            }
            any |= count > 0;
        }
        if (emptyBackpackLabel.isVisible() == any) {
            emptyBackpackLabel.setVisible(!any);
            layoutChanged = true;
        }
        if (layoutChanged) {
            backpackRow.revalidate();
            backpackRow.repaint();
        }
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
            setToolTipText("Ammo: shared by every gun except the pistol");
            setPreferredSize(new Dimension(Theme.ammoCounterWidth("000", Theme.S), 16 * Theme.S));
        }

        void update(Player p) {
            GameData.WeaponType w = p.getCurrentWeapon();
            String t = w.melee ? "--" : w.ammoCost == 0 ? "INF" : String.valueOf(p.getAmmo());
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
        if (source == workshopButton) {
            if (!gamePanel.isPlayerInBase()) {
                gamePanel.getPlayer().showHint("Return to Base Camp (green icon) to talk to NPCs!");
                AssetLoader.playSound("click");
            } else {
                openUpgradeDialog();
            }
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
            {"Days survived", entry.wave + " / 10"},
            {"Infected killed", String.valueOf(entry.kills)},
            {"Base upgrades", gamePanel.getTotalBaseLevel() + " built"},
            {"Survival time", formatTime(entry.seconds)},
            {"Region", entry.map.toUpperCase()}
        };
        Theme.ask(this, Theme.Panel.METAL, survived ? "titles/survived" : "titles/game_over",
                survived ? "Survived" : "Game over",
                (survived ? "Extraction arrived and the camp held through the night. " : "The infected overwhelmed you. ")
                        + "Your survival log is saved.",
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
