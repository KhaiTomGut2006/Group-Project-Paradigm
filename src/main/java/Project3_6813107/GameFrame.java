// Tapatsawin Chaieua          6813105
// Thanapon Thanadunpremdet    6813106
// Tanawat Wongwad             6813107
// Natthanon Achachan          6813241
package Project3_6813107;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
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
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;

/**
 * Main gameplay window (1100x660): top HUD bar, GamePanel canvas in CENTER,
 * bottom weapon hotbar/backpack/NPC bar, plus modal dialogs for Workshop, Perks, and Field Merchant.
 */
public final class GameFrame extends JFrame implements ActionListener {

    private static final long serialVersionUID = 1L;

    private final transient GameData.GameSettings settings;
    private final MainApplication launcher;
    private final GamePanel gamePanel;

    private JLabel heartIcon, armorSegments, poisonedIcon, injuredIcon, bleedingIcon, emptyBackpackLabel;
    private JProgressBar hpBar, chamberProgress;
    private Theme.PixelLabel hpValueLabel, chamberLabel, objectiveLabel, alliesLabel, killsValueLabel;
    private JCheckBox autoFireBox;
    private JButton pauseButton, quitButton, workshopButton;
    private HotbarView hotbar;
    private AmmoView ammoView;
    private JPanel backpackRow;
    private final transient Map<GameData.ItemType, Theme.Slot> itemSlots = new EnumMap<>(GameData.ItemType.class);

    private String shownHeart = "";
    private int shownArmorLevel = -1;
    private boolean dialogOpen, finished, closed;

    public GameFrame(GameData.GameSettings settings, MainApplication launcher) {
        super("Last Camp Survivor");
        Theme.installLookAndFeel();
        this.settings = settings == null ? new GameData.GameSettings() : settings;
        this.launcher = launcher;
        gamePanel = new GamePanel(this.settings, this);

        setTitle("Last Camp Survivor - " + gamePanel.getPlayer().getPlayerName() + " the "
                + gamePanel.getPlayer().getPlayerClass().displayName);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { closeGame(); }
        });

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(Theme.INK);
        content.add(buildTopBar(), BorderLayout.NORTH);
        content.add(gamePanel, BorderLayout.CENTER);
        content.add(buildBottomBar(), BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setCursor(Theme.arrowCursor());
        setSize(GameData.GAME_W, GameData.GAME_H);
        setResizable(false);
        setLocationRelativeTo(null);
        refreshHud();
        setVisible(true);
        gamePanel.startGame();
        gamePanel.requestFocusInWindow();
    }

    // ------------------------------------------------------------------ HUD layout
    private JComponent buildTopBar() {
        heartIcon = new JLabel();
        hpBar = Theme.bar(Theme.Fill.HEALTH, 140, Theme.S);
        hpValueLabel = new Theme.PixelLabel("100/100", 2, Theme.TEXT);
        armorSegments = new JLabel();
        poisonedIcon = statusIcon("poisoned", "Poisoned: you lose HP for a few seconds");
        injuredIcon = statusIcon("injured", "Badly hurt: clear chambers (+3/lv HP) or visit Field Merchant [B]");
        bleedingIcon = statusIcon("bleeding", "You were just hit");
        JPanel left = Theme.row(8, heartIcon, column(Theme.row(0, hpBar), hpValueLabel),
                column(armorSegments, Theme.caption("Armor")), Theme.row(2, poisonedIcon, injuredIcon, bleedingIcon));

        chamberLabel = new Theme.PixelLabel("DAY 1: DAYLIGHT - SAFE CAMP", 2, Theme.TEXT_2);
        objectiveLabel = new Theme.PixelLabel("5:00", 4, Theme.TEXT);
        objectiveLabel.setShadow(true);
        chamberProgress = Theme.bar(Theme.Fill.STAMINA, 170, 1);
        JPanel middle = centred(column(centred(chamberLabel), centred(objectiveLabel), centred(chamberProgress)));

        alliesLabel = new Theme.PixelLabel("1 NPC", 2, Theme.GOLD);
        alliesLabel.setToolTipText("Active NPC allies & camp guards (recruit more at Base Camp [B])");
        killsValueLabel = new Theme.PixelLabel("0", 3, Theme.TEXT);
        autoFireBox = new JCheckBox("Auto-fire", settings.autoFire);
        Theme.styleCheckBox(autoFireBox);
        autoFireBox.setFocusable(false);
        autoFireBox.addActionListener(this);
        pauseButton = makeButton("Pause", Theme.ButtonStyle.GREY, "p");
        quitButton = makeButton("Quit", Theme.ButtonStyle.GREY, null);
        JPanel right = Theme.row(10, column(Theme.caption("Allies"), alliesLabel),
                column(Theme.caption("Kills"), killsValueLabel), autoFireBox, Theme.row(4, pauseButton, quitButton));

        return makePlate(left, middle, right);
    }

    private JComponent buildBottomBar() {
        hotbar = new HotbarView();
        ammoView = new AmmoView();
        backpackRow = Theme.transparentPanel(new FlowLayout(FlowLayout.CENTER, 2, 0));
        for (GameData.ItemType t : GameData.ItemType.values()) {
            if (t == GameData.ItemType.CHEST) continue;
            Theme.Slot slot = new Theme.Slot(Theme.fit(AssetLoader.getItemIcon(t), 24, 24));
            slot.setToolTipText(t.displayName);
            slot.setVisible(false);
            itemSlots.put(t, slot);
            backpackRow.add(slot);
        }
        emptyBackpackLabel = Theme.label("Pack empty - scavenge outside & loot chests", Font.PLAIN, 12, Theme.TEXT_3);
        backpackRow.add(emptyBackpackLabel);

        workshopButton = makeButton("Camp NPC", Theme.ButtonStyle.RED, "b");
        return makePlate(Theme.row(10, hotbar, ammoView), centred(backpackRow), centred(workshopButton));
    }

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

    private static JPanel column(JComponent... parts) {
        JPanel col = Theme.transparentPanel(new GridLayout(0, 1, 0, 4));
        for (JComponent part : parts) col.add(Theme.row(0, part));
        return col;
    }

    private static JLabel statusIcon(String name, String tooltip) {
        JLabel icon = new JLabel(new ImageIcon(Theme.kit("status_icons/" + name, Theme.S)));
        icon.setToolTipText(tooltip);
        icon.setVisible(false);
        return icon;
    }

    private JButton makeButton(String text, Theme.ButtonStyle style, String keyHint) {
        JButton b = Theme.button(text.toUpperCase(), style);
        if (keyHint != null) Theme.setKeyHint(b, keyHint);
        b.setFocusable(false);
        b.addActionListener(this);
        return b;
    }

    // ------------------------------------------------------------------ HUD refresh
    public void refreshHud() {
        if (closed) return;
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
        armorSegments.setToolTipText("Armor " + p.getArmorPercent() + "% reduction | Death Defiance revives left: "
                + p.getDeathDefianceLeft() + " | Active Perks: " + gamePanel.getTotalPerkLevels());
        poisonedIcon.setVisible(p.isPoisoned());
        injuredIcon.setVisible(p.isAlive() && hpPercent < 30);
        bleedingIcon.setVisible(p.isHurt());
        refreshChamberHud();
        alliesLabel.setText(gamePanel.getAllyCount() + " NPC / " + p.getDeathDefianceLeft() + " DD");
        killsValueLabel.setText(String.valueOf(gamePanel.getKills()));
        pauseButton.setText(gamePanel.isPaused() ? "RESUME" : "PAUSE");
        workshopButton.setText(gamePanel.getChamber() > 0 ? "Shop NPC" : "Camp NPC");
        workshopButton.setToolTipText(gamePanel.getChamber() > 0
                ? "Open Field Merchant (3 Random Chamber Offers) [B]" : "Talk to Safe Camp NPCs [B]");
        hotbar.update(p, gamePanel.isInSafeCamp());
        ammoView.update(p);
        refreshBackpack(p.getInventory());
    }

    private void refreshChamberHud() {
        int chamber = gamePanel.getChamber();
        if (chamber == 0) {
            chamberLabel.setText("SAFE CAMP HUB - RUN #" + gamePanel.getRunNumber());
            chamberLabel.setColor(new Color(0x88D470));
            chamberProgress.setValue(100);
            objectiveLabel.setText("GATE [F]");
            objectiveLabel.setColor(Theme.GOLD);
            return;
        }
        boolean clear = gamePanel.isChamberCleared(), bossChamber = chamber % GameData.BOSS_EVERY_CHAMBERS == 0;
        String suffix = chamber <= GameData.TOTAL_CHAMBERS ? "/" + GameData.TOTAL_CHAMBERS : "+";
        chamberLabel.setText(gamePanel.getActLabel(chamber) + " - CH " + chamber + suffix
                + " (" + gamePanel.getCurrentChamberType().displayName.toUpperCase() + ")");
        chamberLabel.setColor(clear ? new Color(0x88D470) : (bossChamber ? Theme.RED : Theme.GOLD));
        chamberProgress.setValue(gamePanel.getChamberClearPercent());
        objectiveLabel.setText(clear ? "PORTAL [F]" : "FOES: " + gamePanel.getHostilesRemaining());
        objectiveLabel.setColor(clear ? new Color(0x88D470) : (bossChamber ? Theme.RED : Theme.TEXT));
    }

    private void refreshBackpack(Map<GameData.ItemType, Integer> inventory) {
        boolean layoutChanged = false, any = false;
        for (Map.Entry<GameData.ItemType, Theme.Slot> e : itemSlots.entrySet()) {
            int count = inventory.get(e.getKey());
            Theme.Slot slot = e.getValue();
            slot.update(Theme.SlotKind.NORMAL, Theme.fit(AssetLoader.getItemIcon(e.getKey()), 24, 24), String.valueOf(count), false);
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

    // ------------------------------------------------------------------ bottom bar views
    private static final class HotbarView extends JComponent {
        private static final long serialVersionUID = 1L;
        private static final GameData.WeaponType[] WEAPONS = GameData.HOTBAR_WEAPONS;
        private final transient BufferedImage[] icons = new BufferedImage[WEAPONS.length];
        private final boolean[] owned = new boolean[WEAPONS.length], allowedInRun = new boolean[WEAPONS.length];
        private GameData.WeaponType current, tempWeapon;

        HotbarView() {
            for (int i = 0; i < WEAPONS.length; i++) icons[i] = Theme.fit(AssetLoader.getWeaponIcon(WEAPONS[i]), 32, 32);
            setPreferredSize(new Dimension(Theme.hotbarWidth(WEAPONS.length, Theme.S), 26 * Theme.S));
            setToolTipText("Camp: equip 1 Firearm (2-8) for your run | Chamber: Knife [1] + Gun + TMP Rental");
        }

        void update(Player p, boolean inSafeCamp) {
            List<GameData.WeaponType> mine = p.getOwnedWeapons();
            GameData.WeaponType tmp = p.getTempChamberWeapon();
            boolean changed = p.getCurrentWeapon() != current || tmp != tempWeapon;
            current = p.getCurrentWeapon();
            tempWeapon = tmp;
            for (int i = 0; i < WEAPONS.length; i++) {
                boolean has = mine.contains(WEAPONS[i]) || WEAPONS[i] == tmp;
                boolean canRun = p.canUseWeaponInRun(WEAPONS[i], inSafeCamp);
                changed |= has != owned[i] || canRun != allowedInRun[i];
                owned[i] = has;
                allowedInRun[i] = canRun;
            }
            if (changed) repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            int s = Theme.S;
            Theme.drawHotbarFrame(g2, 0, 0, WEAPONS.length, s);
            for (int i = 0; i < WEAPONS.length; i++) {
                int x = (3 + i * 22) * s, y = 3 * s;
                Theme.SlotKind kind = !owned[i] ? Theme.SlotKind.LOCKED
                        : WEAPONS[i] == current ? Theme.SlotKind.SELECTED
                        : allowedInRun[i] ? Theme.SlotKind.NORMAL : Theme.SlotKind.EMPTY;
                Theme.drawSlot(g2, kind, x, y, s);
                if (owned[i]) {
                    BufferedImage icon = icons[i];
                    Composite old = g2.getComposite();
                    if (!allowedInRun[i]) g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.32f));
                    g2.drawImage(icon, x + (20 * s - icon.getWidth()) / 2, y + (20 * s - icon.getHeight()) / 2, null);
                    g2.setComposite(old);
                }
                Theme.drawPixelTextShadow(g2, String.valueOf(i + 1), x + 3 * s, y + 3 * s, 1,
                        owned[i] && allowedInRun[i] ? Theme.TEXT : Theme.TEXT_3);
                if (WEAPONS[i] == tempWeapon) Theme.drawPixelTextShadow(g2, "TMP", x + 3 * s, y + 13 * s, 1, Theme.GOLD);
            }
            g2.dispose();
        }
    }

    private static final class AmmoView extends JComponent {
        private static final long serialVersionUID = 1L;
        private String text = "0";

        AmmoView() {
            setToolTipText("Ammo: consumed by all unlocked firearms (Survival Knife uses 0 ammo)");
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

    // ------------------------------------------------------------------ actions & modal dialogs
    @Override
    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();
        if (source == workshopButton) {
            if (gamePanel.getChamber() > 0) {
                showFieldShopDialog();
            } else if (!gamePanel.isPlayerInBase()) {
                gamePanel.getPlayer().showHint("Camp NPCs are only available at the Safe Camp Hub!");
                AssetLoader.playSound("click");
            } else {
                openUpgradeDialog();
            }
        } else if (source == pauseButton) {
            if (gamePanel.isPaused()) gamePanel.resumeGame();
            else gamePanel.pauseGame();
            refreshHud();
        } else if (source == quitButton) {
            confirmQuit();
        } else if (source == autoFireBox) {
            gamePanel.setAutoFire(autoFireBox.isSelected());
        }
        gamePanel.requestFocusInWindow();
    }

    public void openUpgradeDialog() {
        if (!dialogOpen && !closed && !gamePanel.isGameOver()) {
            showPausedDialog(() -> new UpgradeDialog(this, gamePanel.getPlayer()).setVisible(true));
        }
    }

    private void showPausedDialog(Runnable showDialog) {
        dialogOpen = true;
        boolean wasPaused = gamePanel.isPaused();
        gamePanel.pauseGame();
        try {
            showDialog.run();
        } finally {
            dialogOpen = false;
        }
        if (closed) return;
        if (!wasPaused) gamePanel.resumeGame();
        refreshHud();
        gamePanel.requestFocusInWindow();
    }

    private void confirmQuit() {
        boolean wasPaused = gamePanel.isPaused();
        gamePanel.pauseGame();
        boolean quit = Theme.ask(this, Theme.Panel.DANGER, null, "QUIT THIS RUN?",
                "Your score from this run will not be saved.", null, "Quit", "Keep playing");
        if (quit) closeGame();
        else if (!wasPaused) gamePanel.resumeGame();
    }

    public void showPerkSelectionDialog(List<GameData.TacticalPerk> choices) {
        if (dialogOpen || closed || gamePanel.isGameOver() || choices == null || choices.isEmpty()) return;
        showPausedDialog(() -> {
            JDialog dlg = new JDialog(this, "Choose a Tactical Perk", true);
            JPanel root = dialogRoot(14, 18, 22);
            Theme.PixelLabel header = new Theme.PixelLabel("CHAMBER CLEARED - CHOOSE 1 TACTICAL PERK", 3, Theme.GOLD);
            header.setShadow(true);
            root.add(centred(header), BorderLayout.NORTH);

            JPanel grid = Theme.transparentPanel(new GridLayout(1, choices.size(), 12, 0));
            Player p = gamePanel.getPlayer();
            for (GameData.TacticalPerk perk : choices) {
                int curLv = p.getPerkLevel(perk);
                String lvTag = curLv == 0 ? "NEW LV 1" : "LV " + curLv + " -> " + (curLv + 1);
                JButton pickBtn = Theme.button("CHOOSE", Theme.ButtonStyle.RED);
                pickBtn.addActionListener(ev -> {
                    gamePanel.applyChosenPerk(perk);
                    dlg.dispose();
                });
                grid.add(choiceCard(perk.displayName.toUpperCase(), Theme.DARK_RED, lvTag, Theme.DARK_GREEN, perk.description, pickBtn, 235, 220));
            }
            root.add(grid, BorderLayout.CENTER);
            showModalDialog(dlg, root, false);
        });
    }

    public void showFieldShopDialog() {
        if (dialogOpen || closed || gamePanel.isGameOver() || gamePanel.getChamber() <= 0) return;
        List<GamePanel.FieldShopOffer> offers = gamePanel.getFieldShopOffers();
        showPausedDialog(() -> {
            JDialog dlg = new JDialog(this, "Field Merchant - Chamber " + gamePanel.getChamber(), true);
            JPanel root = dialogRoot(12, 16, 20);
            Player p = gamePanel.getPlayer();

            Theme.PixelLabel header = new Theme.PixelLabel("FIELD MERCHANT - 3 RANDOM OFFERS (CH " + gamePanel.getChamber() + ")", 3, Theme.GOLD);
            header.setShadow(true);
            Theme.PixelLabel scrapsLabel = new Theme.PixelLabel(
                    "SCRAPS: " + p.getItemCount(GameData.ItemType.SCRAPS) + "  |  HP: " + p.getHp() + "/" + p.getMaxHp(), 2, Theme.TEXT);
            JPanel top = Theme.transparentPanel(new BorderLayout(0, 6));
            top.add(centred(header), BorderLayout.NORTH);
            top.add(centred(scrapsLabel), BorderLayout.SOUTH);
            root.add(top, BorderLayout.NORTH);

            JLabel feedbackLabel = Theme.label("Pick any offer using Scraps. Temporary weapons vanish when entering the next chamber.",
                    Font.PLAIN, 13, Theme.TEXT_2);
            JPanel grid = Theme.transparentPanel(new GridLayout(1, Math.max(1, offers.size()), 12, 0));
            for (int i = 0; i < offers.size(); i++) {
                final int idx = i;
                GamePanel.FieldShopOffer offer = offers.get(i);
                String kindTag = offer.kind == GamePanel.FieldShopOffer.OfferKind.HEAL ? "FIELD RECOVERY"
                        : offer.kind == GamePanel.FieldShopOffer.OfferKind.TEMP_WEAPON ? "TEMP WEAPON (1 CH)" : "TACTICAL PERK";
                Color kindCol = offer.kind == GamePanel.FieldShopOffer.OfferKind.HEAL ? Theme.DARK_GREEN
                        : offer.kind == GamePanel.FieldShopOffer.OfferKind.TEMP_WEAPON ? Theme.DARK_RED : new Color(0x7A4B00);
                JButton buyBtn = Theme.button(offer.sold ? "SOLD" : ("BUY - " + offer.costScraps + " SCRAPS"),
                        offer.sold ? Theme.ButtonStyle.GREY : Theme.ButtonStyle.RED);
                buyBtn.setEnabled(!offer.sold);
                buyBtn.addActionListener(ev -> {
                    feedbackLabel.setText(gamePanel.buyFieldShopOffer(idx));
                    scrapsLabel.setText("SCRAPS: " + p.getItemCount(GameData.ItemType.SCRAPS) + "  |  HP: " + p.getHp() + "/" + p.getMaxHp());
                    if (offer.sold) {
                        buyBtn.setText("SOLD");
                        buyBtn.setEnabled(false);
                    }
                    refreshHud();
                });
                grid.add(choiceCard(kindTag, kindCol, offer.title.toUpperCase(), Theme.PAPER_TEXT, offer.detail, buyBtn, 238, 226));
            }
            root.add(grid, BorderLayout.CENTER);

            JButton closeBtn = Theme.button("CLOSE", Theme.ButtonStyle.GREY);
            closeBtn.addActionListener(ev -> dlg.dispose());
            JPanel bottom = Theme.transparentPanel(new BorderLayout(0, 8));
            bottom.add(centred(feedbackLabel), BorderLayout.NORTH);
            bottom.add(centred(closeBtn), BorderLayout.SOUTH);
            root.add(bottom, BorderLayout.SOUTH);
            showModalDialog(dlg, root, true);
        });
    }

    private static JPanel dialogRoot(int gap, int padV, int padH) {
        JPanel root = new JPanel(new BorderLayout(0, gap));
        root.setBackground(Theme.BG);
        root.setBorder(new EmptyBorder(padV, padH, padV, padH));
        return root;
    }

    private static Theme.Card choiceCard(String topText, Color topColor, String subText, Color subColor,
                                         String body, JButton action, int w, int h) {
        Theme.Card card = new Theme.Card(new BorderLayout(0, 8), Theme.Panel.PAPER);
        card.setPreferredSize(new Dimension(w, h));
        JPanel head = Theme.transparentPanel(new BorderLayout(0, 4));
        head.add(new Theme.PixelLabel(topText, 2, topColor), BorderLayout.NORTH);
        head.add(new Theme.PixelLabel(subText, 2, subColor), BorderLayout.SOUTH);
        card.add(head, BorderLayout.NORTH);
        JTextArea desc = new JTextArea(body);
        desc.setEditable(false);
        desc.setLineWrap(true);
        desc.setWrapStyleWord(true);
        desc.setOpaque(false);
        desc.setFont(Theme.font(Font.PLAIN, 13));
        desc.setForeground(Theme.PAPER_TEXT);
        card.add(desc, BorderLayout.CENTER);
        card.add(action, BorderLayout.SOUTH);
        return card;
    }

    private void showModalDialog(JDialog dlg, JPanel root, boolean closable) {
        if (!closable) dlg.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dlg.setContentPane(root);
        dlg.pack();
        dlg.setResizable(false);
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    // ------------------------------------------------------------------ end of run & close
    public void onGameOver(boolean survived) {
        if (finished || closed) return;
        finished = true;
        refreshHud();
        Player p = gamePanel.getPlayer();
        GameData.ScoreEntry entry = new GameData.ScoreEntry();
        entry.name = p.getPlayerName();
        entry.playerClass = p.getPlayerClass().displayName;
        entry.map = settings.map == null ? "-" : settings.map.displayName;
        entry.score = gamePanel.getScore();
        entry.kills = gamePanel.getKills();
        entry.wave = Math.max(1, gamePanel.getHighestChamberReached());
        entry.seconds = gamePanel.getElapsedSeconds();
        GameData.saveScore(entry);

        String salvage = p.getItemCount(GameData.ItemType.SCRAPS) + " Scrap, "
                + p.getItemCount(GameData.ItemType.STEEL) + " Steel, "
                + p.getItemCount(GameData.ItemType.ELECTRONICS) + " Parts, "
                + p.getItemCount(GameData.ItemType.BLUEPRINT) + " BP";
        String[][] stats = {
            {"Highest Chamber", entry.wave + (entry.wave >= 15 ? " (CLEARED ACT 3)" : " / 15")},
            {"Infected killed", String.valueOf(entry.kills)},
            {"Permanent Salvage", salvage},
            {"Survival time", GameData.formatRunTime(entry.seconds)},
            {"Run #", String.valueOf(gamePanel.getRunNumber())}
        };
        boolean backToCamp = Theme.ask(this, Theme.Panel.METAL, survived ? "titles/survived" : "titles/game_over",
                survived ? "Expedition Complete!" : "Fallen in Chamber " + Math.max(1, gamePanel.getChamber()),
                (survived ? "You extracted from the Dead Zone in triumph! "
                        : "You fell in combat, but 100% of your salvaged Steel, Parts & Blueprints stay with you in Safe Camp. ")
                        + "Spend salvage at Camp NPCs to unlock permanent power!",
                stats, "Return to Safe Camp", "Main Menu");
        if (backToCamp && !closed) {
            finished = false;
            gamePanel.returnToSafeCamp();
            refreshHud();
        } else {
            closeGame();
        }
    }

    private void closeGame() {
        if (closed) return;
        closed = true;
        gamePanel.stopGame();
        dispose();
        if (launcher != null) launcher.onGameFinished();
    }
}
