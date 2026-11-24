// Tapatsawin Chaieua          6813105
// Thanapon Thanadunpremdet    6813106
// Tanawat Wongwad             6813107
// Natthanon Achachan          6813241
package Project3_6813107;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LinearGradientPaint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Entry-point launcher window (900x620): animated camp preview + group members on the left,
 * tabbed Play / Options / Scores / Help controls on the right. Launches GameFrame on Start.
 *
 * Event handlers:
 *   actionPerformed(ActionEvent) - Start/Quit, tabs, 5 class radios, 5 check boxes, map combo, name field, timer
 *   stateChanged(ChangeEvent)    - difficulty and volume JSliders
 */
public final class MainApplication extends JFrame implements ActionListener, ChangeListener {

    private static final long serialVersionUID = 1L;
    private static final int MAX_NAME_LENGTH = 16, ANIMATION_MS = 32, TOP_SCORES = 10, HERO_WIDTH = 340;
    private static final String NAME_PATTERN = "[A-Za-z0-9 _.-]+";
    private static final String NAME_HINT = "Letters, digits, space, - _ .  (max " + MAX_NAME_LENGTH + ")";
    private static final String[] DIFFICULTY_NAMES = {"Rookie", "Survivor", "Veteran", "Hardened", "Nightmare"};
    private static final String[] TAB_NAMES = {"Play", "Options", "Scores", "Help"};

    // ---- Play tab components
    private Theme.KitField nameField;
    private JLabel nameHintLabel, difficultyInfoLabel;
    private JRadioButton[] classRadios = new JRadioButton[GameData.PlayerClass.values().length];
    private final ButtonGroup classGroup = new ButtonGroup();
    private JTextArea classInfoArea, mapInfoArea, howToArea;
    private JComboBox<GameData.MapType> mapCombo;
    private JSlider difficultySlider, volumeSlider;
    private Theme.PixelLabel difficultyValueLabel, volumeValueLabel;

    // ---- Options tab components
    private JCheckBox autoFireBox, doubleSpawnBox, hitboxBox, soundBox, musicBox;

    // ---- Navigation, Scores & Animation
    private final JToggleButton[] tabButtons = new JToggleButton[TAB_NAMES.length];
    private final CardLayout tabCards = new CardLayout(), scoreCards = new CardLayout();
    private final JPanel tabPages = Theme.transparentPanel(tabCards), scorePages = Theme.transparentPanel(scoreCards);
    private ScoreTableModel scoreTableModel;
    private JButton startButton, quitButton;
    private final HeroPanel heroPanel;
    private final Timer animationTimer = new Timer(ANIMATION_MS, this);
    private final transient Map<GameData.MapType, GameMap> mapCache = new EnumMap<>(GameData.MapType.class);

    public MainApplication() {
        super("Last Camp Survivor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);
        heroPanel = buildHeroPanel();
        root.add(heroPanel, BorderLayout.WEST);
        root.add(buildRightColumn(), BorderLayout.CENTER);
        setContentPane(root);
        getRootPane().setCursor(Theme.arrowCursor());

        showClassInfo();
        showMapInfo();
        showVolume();
        updateDifficultyReadout();
        reloadHighScores();
        selectTab(0);

        setSize(GameData.LAUNCHER_W, GameData.LAUNCHER_H);
        setMinimumSize(new Dimension(GameData.LAUNCHER_W, GameData.LAUNCHER_H));
        setLocationRelativeTo(null);
    }

    // ================================================================== left side (preview + group names)
    private HeroPanel buildHeroPanel() {
        HeroPanel hero = new HeroPanel();
        hero.setPreferredSize(new Dimension(HERO_WIDTH, 10));
        hero.setBorder(new EmptyBorder(30, 30, 26, 32));

        JPanel title = Theme.transparentPanel(new GridLayout(0, 1, 0, 10));
        title.add(Theme.row(0, new Theme.PixelLabel("EGCO 213 - GROUP PROJECT 3", 2, Theme.GOLD)));
        title.add(Theme.row(0, shadowed("LAST CAMP", 6, Theme.TEXT)));
        title.add(Theme.row(0, shadowed("SURVIVOR", 6, Theme.GOLD)));
        title.add(Theme.row(0, new Theme.PixelLabel("PREPARE AT CAMP. RAID CHAMBERS. EVOLVE.", 2, Theme.TEXT_2)));
        hero.add(title, BorderLayout.NORTH);

        JPanel crew = Theme.transparentPanel(new GridLayout(0, 1, 0, 4));
        crew.add(Theme.row(0, Theme.caption("Crew")));
        for (String member : GameData.GROUP_MEMBERS) crew.add(buildMemberRow(member));
        hero.add(crew, BorderLayout.SOUTH);
        return hero;
    }

    private static Theme.PixelLabel shadowed(String text, int scale, Color color) {
        Theme.PixelLabel label = new Theme.PixelLabel(text, scale, color);
        label.setShadow(true);
        return label;
    }

    private static JPanel buildMemberRow(String member) {
        String text = member.trim();
        int split = text.lastIndexOf(' ');
        String name = split > 0 ? text.substring(0, split).trim() : text;
        String id = split > 0 ? text.substring(split + 1) : "";
        JPanel row = Theme.transparentPanel(new BorderLayout(8, 0));
        JLabel nameLabel = Theme.label(name, Font.PLAIN, 12, Theme.TEXT);
        nameLabel.setToolTipText(name);
        row.add(nameLabel, BorderLayout.CENTER);
        row.add(Theme.label(id, Font.PLAIN, 12, Theme.TEXT_2), BorderLayout.EAST);
        return row;
    }

    // ================================================================== right side (tabs + pages + Start/Quit)
    private JPanel buildRightColumn() {
        JPanel column = Theme.transparentPanel(new BorderLayout(0, 14));
        column.setBorder(new EmptyBorder(24, 26, 20, 26));
        column.add(buildTabBar(), BorderLayout.NORTH);

        tabPages.add(buildPlayPage(), TAB_NAMES[0]);
        tabPages.add(buildOptionsPage(), TAB_NAMES[1]);
        tabPages.add(buildScoresPage(), TAB_NAMES[2]);
        tabPages.add(buildHelpPage(), TAB_NAMES[3]);
        column.add(tabPages, BorderLayout.CENTER);

        quitButton = Theme.button("Quit", Theme.ButtonStyle.RED);
        quitButton.addActionListener(this);
        startButton = Theme.button("Start", Theme.ButtonStyle.RED);
        Theme.setButtonScale(startButton, 3);
        startButton.addActionListener(this);
        getRootPane().setDefaultButton(startButton);
        JPanel quitHolder = Theme.transparentPanel(new GridBagLayout());
        quitHolder.add(quitButton);
        JPanel bottom = Theme.transparentPanel(new BorderLayout());
        bottom.add(quitHolder, BorderLayout.WEST);
        bottom.add(startButton, BorderLayout.EAST);
        column.add(bottom, BorderLayout.SOUTH);
        return column;
    }

    private JComponent buildTabBar() {
        JPanel strip = Theme.transparentPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        ButtonGroup group = new ButtonGroup();
        for (int i = 0; i < TAB_NAMES.length; i++) {
            tabButtons[i] = new JToggleButton(TAB_NAMES[i].toUpperCase());
            Theme.styleToggle(tabButtons[i]);
            tabButtons[i].addActionListener(this);
            group.add(tabButtons[i]);
            strip.add(tabButtons[i]);
        }
        return strip;
    }

    private JComponent buildPlayPage() {
        JPanel page = Theme.transparentPanel(new GridBagLayout());
        Form form = new Form(page);

        nameField = new Theme.KitField("Survivor", 16);
        nameField.addActionListener(this);
        Theme.onEdit(nameField, this::clearNameError);
        nameHintLabel = Theme.label(NAME_HINT, Font.PLAIN, 11, Theme.TEXT_3);
        form.add(Theme.caption("Your name"), 0);
        form.add(nameField, 7);
        form.add(nameHintLabel, 3);

        JPanel classButtons = Theme.transparentPanel(new GridLayout(1, classRadios.length, 4, 0));
        GameData.PlayerClass[] classes = GameData.PlayerClass.values();
        for (int i = 0; i < classes.length; i++) {
            classRadios[i] = new JRadioButton(classes[i].displayName.toUpperCase(), i == 0);
            Theme.styleToggle(classRadios[i]);
            classRadios[i].addActionListener(this);
            classGroup.add(classRadios[i]);
            classButtons.add(classRadios[i]);
        }
        classInfoArea = Theme.textBlock("", 12, Theme.TEXT_2);
        form.add(Theme.caption("Class"), 14);
        form.add(classButtons, 7);
        form.add(classInfoArea, 6);

        mapCombo = new JComboBox<>(GameData.MapType.values());
        Theme.styleCombo(mapCombo, new Theme.ListRenderer<>());
        mapCombo.addActionListener(this);
        mapInfoArea = Theme.textBlock("", 12, Theme.TEXT_2);
        mapInfoArea.setRows(2);
        JTextArea gearArea = Theme.textBlock("Survival Knife (Infinite) + 1 Unlocked Firearm.\n"
                + "Permanent salvage stays 100% on death for Camp Upgrades.", 12, Theme.TEXT_2);
        JPanel loadout = Theme.transparentPanel(new GridLayout(1, 2, 14, 0));
        loadout.add(fieldColumn("Starting Gear", Theme.label("Knife + Camp Arsenal", Font.BOLD, 13, Theme.GOLD), gearArea));
        loadout.add(fieldColumn("Region", mapCombo, mapInfoArea));
        form.add(loadout, 14);

        difficultySlider = new JSlider(1, 5, 3);
        difficultySlider.setSnapToTicks(true);
        difficultySlider.setMajorTickSpacing(1);
        Theme.styleSlider(difficultySlider);
        difficultySlider.addChangeListener(this);
        difficultyValueLabel = new Theme.PixelLabel("", 2, Theme.GOLD);
        difficultyInfoLabel = Theme.label("", Font.PLAIN, 12, Theme.TEXT_2);
        form.add(headerWithValue("Hazard Level", difficultyValueLabel), 14);
        form.add(difficultySlider, 4);
        form.add(difficultyInfoLabel, 0);
        form.fillRest();
        return onPanel(page);
    }

    private JComponent buildOptionsPage() {
        JPanel page = Theme.transparentPanel(new GridBagLayout());
        Form form = new Form(page);
        autoFireBox = createCheckBox("Auto-fire", false);
        doubleSpawnBox = createCheckBox("Double spawn", false);
        hitboxBox = createCheckBox("Show hitboxes", false);
        soundBox = createCheckBox("Sound effects", true);
        musicBox = createCheckBox("Music", true);

        form.add(Theme.caption("Gameplay"), 0);
        form.add(checkRow(autoFireBox, "Your gun fires at the nearest monster by itself."), 6);
        form.add(checkRow(doubleSpawnBox, "Twice as many monsters each wave. Harder, more loot."), 4);
        form.add(checkRow(hitboxBox, "Draws the collision boxes (handy for the demo)."), 4);
        form.add(Theme.caption("Audio"), 16);
        form.add(checkRow(soundBox, "Shots, hits and pickups."), 6);
        form.add(checkRow(musicBox, "The camp music loop."), 4);

        volumeSlider = new JSlider(0, 100, 70);
        Theme.styleSlider(volumeSlider);
        volumeSlider.addChangeListener(this);
        volumeValueLabel = new Theme.PixelLabel("", 2, Theme.GOLD);
        form.add(headerWithValue("Volume", volumeValueLabel), 14);
        form.add(volumeSlider, 4);
        form.fillRest();
        return onPanel(page);
    }

    private JComponent buildScoresPage() {
        scoreTableModel = new ScoreTableModel();
        JTable scoreTable = new JTable(scoreTableModel);
        Theme.styleTable(scoreTable, 30);
        scoreTable.setRowSelectionAllowed(false);
        scoreTable.setDefaultRenderer(Object.class, new ScoreCellRenderer());
        int[] widths = {40, 120, 140, 60, 56, 74};
        for (int i = 0; i < widths.length; i++) scoreTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);

        JPanel message = Theme.transparentPanel(new GridLayout(0, 1, 0, 10));
        Theme.PixelLabel title = new Theme.PixelLabel("NO RUNS YET", 3, Theme.TEXT);
        title.setAlignment(SwingConstants.CENTER);
        JLabel sub = Theme.label("Finish a run and your best scores show up here.", Font.PLAIN, 13, Theme.TEXT_2);
        sub.setHorizontalAlignment(SwingConstants.CENTER);
        message.add(title);
        message.add(sub);
        JPanel empty = Theme.transparentPanel(new GridBagLayout());
        empty.add(message);

        scorePages.add(Theme.scroll(scoreTable, Theme.PANEL), "table");
        scorePages.add(empty, "empty");
        return onPanel(scorePages);
    }

    private JComponent buildHelpPage() {
        JPanel page = Theme.transparentPanel(new GridBagLayout());
        Form form = new Form(page);

        howToArea = Theme.textBlock("Prepare in Safe Camp Hub (talk to Camp NPCs [B] to unlock permanent guns, allies,"
                + " Vitality Siphon +3/ch heal, & Death Defiance). Step through the Gate [F] into 12+ procedural room styles"
                + " across 15 chambers: manual Medkit/Food healing is OFF - visit the Field Merchant [B] inside each chamber"
                + " for 3 random offers (Heal, 1-Chamber Temp Weapon, Perk) and choose Tactical Perks on clear!",
                13, Theme.PAPER_TEXT);
        form.add(new Theme.PixelLabel("ROGUELITE EXPEDITION LOOP", 2, Theme.DARK_RED), 0);
        form.add(howToArea, 6);

        String[][] controls = {
            {"Move", "w", "a", "s", "d"}, {"Aim, hold to fire", "mouse_left"},
            {"Dash (Dodge Traps)", "space", "shift"}, {"Enter Gate / Interact NPC", "f"},
            {"Grenade", "mouse_right", "g"}, {"Vitality Info (Manual Heal Off)", "e"},
            {"Switch weapon (1-8)", "mouse_mid"}, {"Camp NPC / Field Shop [3 Items]", "b"},
            {"Pause", "p", "esc"}, {"Unlock / Equip", "enter"}
        };
        JPanel keys = Theme.transparentPanel(new GridLayout(0, 2, 12, 6));
        for (String[] c : controls) {
            keys.add(Theme.row(8, Theme.keyCaps(2, Arrays.copyOfRange(c, 1, c.length)), Theme.label(c[0], Font.PLAIN, 13, Theme.PAPER_TEXT)));
        }
        form.add(new Theme.PixelLabel("CONTROLS", 2, Theme.DARK_RED), 14);
        form.add(keys, 8);
        form.add(new Theme.PixelLabel("TRAPS, PERKS & PERMANENT SALVAGE", 2, Theme.DARK_RED), 14);
        form.add(Theme.textBlock("Spikes, Toxic Pools, Shock Pylons & Landmines hurt BOTH you and zombies - dash [SPACE] through"
                + " hazards or lure enemies into traps! Each run carries your Knife + 1 Gun from Camp Arsenal. If you fall, 100%"
                + " of your Steel, Scraps, Electronics & Blueprints return with you to Safe Camp for permanent upgrades.",
                13, Theme.PAPER_TEXT), 6);
        form.fillRest();
        Theme.Card card = new Theme.Card(new BorderLayout(), Theme.Panel.PAPER);
        card.setBorder(new EmptyBorder(16, 40, 14, 22));
        card.add(page, BorderLayout.CENTER);
        return card;
    }

    private static JComponent onPanel(JComponent content) {
        Theme.Card card = new Theme.Card(new BorderLayout(), Theme.Panel.METAL);
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private static JPanel fieldColumn(String caption, JComponent field, JComponent info) {
        JPanel column = Theme.transparentPanel(new BorderLayout(0, 7));
        column.add(Theme.row(0, Theme.caption(caption)), BorderLayout.NORTH);
        JPanel body = Theme.transparentPanel(new BorderLayout(0, 6));
        body.add(field, BorderLayout.NORTH);
        body.add(info, BorderLayout.CENTER);
        column.add(body, BorderLayout.CENTER);
        return column;
    }

    private static JPanel headerWithValue(String caption, JComponent value) {
        JPanel header = Theme.transparentPanel(new BorderLayout());
        header.add(Theme.caption(caption), BorderLayout.WEST);
        header.add(value, BorderLayout.EAST);
        return header;
    }

    private JCheckBox createCheckBox(String text, boolean on) {
        JCheckBox box = new JCheckBox(text, on);
        Theme.styleCheckBox(box);
        box.addActionListener(this);
        return box;
    }

    private static JPanel checkRow(JCheckBox box, String description) {
        JPanel row = Theme.transparentPanel(new BorderLayout(0, 0));
        row.add(box, BorderLayout.NORTH);
        JLabel text = Theme.label(description, Font.PLAIN, 12, Theme.TEXT_3);
        text.setBorder(new EmptyBorder(0, 36, 0, 0));
        row.add(text, BorderLayout.CENTER);
        return row;
    }

    private static final class Form {
        private final JPanel panel;
        private final GridBagConstraints c = new GridBagConstraints();

        Form(JPanel panel) {
            this.panel = panel;
            c.gridx = c.gridy = 0;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.anchor = GridBagConstraints.NORTHWEST;
        }

        void add(Component component, int gapAbove) {
            c.insets = new Insets(gapAbove, 0, 0, 0);
            panel.add(component, c);
            c.gridy++;
        }

        void fillRest() {
            c.weighty = 1;
            c.insets = new Insets(0, 0, 0, 0);
            panel.add(Theme.transparentPanel(null), c);
        }
    }

    // ================================================================== event handlers
    @Override
    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();
        if (source == startButton || source == nameField) startSurvival();
        else if (source == quitButton) quitLauncher();
        else if (source == animationTimer) heroPanel.tick();
        else updateSelection(source);
    }

    private void updateSelection(Object source) {
        for (int i = 0; i < tabButtons.length; i++) {
            if (source == tabButtons[i]) {
                selectTab(i);
                AssetLoader.playSound("click");
                return;
            }
        }
        if (source instanceof JRadioButton) {
            showClassInfo();
            AssetLoader.playSound("click");
        } else if (source == mapCombo) {
            showMapInfo();
            AssetLoader.playSound("click");
        } else if (source == soundBox) {
            AssetLoader.setSoundEnabled(soundBox.isSelected());
            AssetLoader.playSound("click");
        } else if (source == musicBox) {
            AssetLoader.setMusicEnabled(musicBox.isSelected());
        } else if (source == hitboxBox) {
            heroPanel.setShowHitbox(hitboxBox.isSelected());
        } else if (source == doubleSpawnBox) {
            updateDifficultyReadout();
        } else if (source == autoFireBox) {
            AssetLoader.playSound("click");
        }
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        if (e.getSource() == volumeSlider) {
            AssetLoader.setVolume(volumeSlider.getValue());
            showVolume();
            if (!volumeSlider.getValueIsAdjusting()) AssetLoader.playSound("click");
        } else if (e.getSource() == difficultySlider) {
            updateDifficultyReadout();
        }
    }

    // ================================================================== actions & helpers
    private void selectTab(int index) {
        tabButtons[index].setSelected(true);
        tabCards.show(tabPages, TAB_NAMES[index]);
    }

    private GameData.PlayerClass getSelectedClass() {
        GameData.PlayerClass[] classes = GameData.PlayerClass.values();
        for (int i = 0; i < classRadios.length; i++) if (classRadios[i].isSelected()) return classes[i];
        return GameData.PlayerClass.SCAVENGER;
    }

    private GameData.MapType getSelectedMap() {
        return mapCombo.getSelectedItem() instanceof GameData.MapType m ? m : GameData.MapType.WASTELAND_HIGHWAY;
    }

    private void showClassInfo() { classInfoArea.setText(getSelectedClass().description); refreshHero(); }
    private void showMapInfo() { mapInfoArea.setText(getSelectedMap().description); refreshHero(); }

    private void refreshHero() {
        if (heroPanel != null && mapCombo != null) {
            GameData.MapType type = getSelectedMap();
            GameMap map = mapCache.computeIfAbsent(type, t -> new GameMap(t, t.seed));
            heroPanel.setScene(map, getSelectedClass(), GameData.WeaponType.KNIFE);
        }
    }

    private void showVolume() {
        int v = volumeSlider.getValue();
        volumeValueLabel.setText(v == 0 ? "MUTED" : v + "%");
    }

    private void updateDifficultyReadout() {
        int d = difficultySlider.getValue();
        boolean doubled = doubleSpawnBox.isSelected();
        difficultyValueLabel.setText(d + " - " + DIFFICULTY_NAMES[d - 1].toUpperCase());
        int zombieHp = (int) Math.round(GameData.MonsterType.ZOMBIE_NORMAL.maxHp * GameData.monsterHpScale(1, d));
        difficultyInfoLabel.setText("Infected HP: " + zombieHp + ". Patrol density: "
                + GameData.spawnBatchSize(1, d, doubled) + " by Day 1, "
                + GameData.spawnBatchSize(5, d, doubled) + " by Day 5.");
    }

    private String validatePlayerName() {
        String name = nameField.getText().trim();
        String problem = name.isEmpty() ? "Please type a name first."
                : name.length() > MAX_NAME_LENGTH ? "Too long: " + name.length() + " of max " + MAX_NAME_LENGTH + " characters."
                : !name.matches(NAME_PATTERN) ? "Only letters, digits, space and - _ . please." : null;
        if (problem != null) {
            selectTab(0);
            nameHintLabel.setText(problem);
            nameHintLabel.setForeground(Theme.RED);
            nameField.setError(true);
            nameField.requestFocusInWindow();
            nameField.selectAll();
            AssetLoader.playSound("hurt");
            return null;
        }
        clearNameError();
        nameField.setText(name);
        return name;
    }

    private void clearNameError() {
        nameHintLabel.setText(NAME_HINT);
        nameHintLabel.setForeground(Theme.TEXT_3);
        nameField.setError(false);
    }

    private void startSurvival() {
        String name = validatePlayerName();
        if (name == null) return;
        GameData.GameSettings s = new GameData.GameSettings();
        s.playerName = name;
        s.playerClass = getSelectedClass();
        s.startWeapon = GameData.WeaponType.KNIFE;
        s.map = getSelectedMap();
        s.difficulty = difficultySlider.getValue();
        s.volume = volumeSlider.getValue();
        s.autoFire = autoFireBox.isSelected();
        s.showHitbox = hitboxBox.isSelected();
        s.soundOn = soundBox.isSelected();
        s.musicOn = musicBox.isSelected();
        s.doubleSpawn = doubleSpawnBox.isSelected();

        AssetLoader.playSound("click");
        animationTimer.stop();
        AssetLoader.stopMusic();
        setVisible(false);
        try {
            new GameFrame(s, this);
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(null, "The game could not start:\n" + ex, "Last Camp Survivor", JOptionPane.ERROR_MESSAGE);
            showLauncher();
        }
    }

    private void quitLauncher() {
        animationTimer.stop();
        AssetLoader.stopMusic();
        dispose();
        System.exit(0);
    }

    public void showLauncher() {
        AssetLoader.setVolume(volumeSlider.getValue());
        AssetLoader.setSoundEnabled(soundBox.isSelected());
        AssetLoader.setMusicEnabled(musicBox.isSelected());
        setVisible(true);
        toFront();
        animationTimer.start();
        AssetLoader.playMusic("bgm");
    }

    public void onGameFinished() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::onGameFinished);
            return;
        }
        reloadHighScores();
        if (scoreTableModel.getRowCount() > 0) selectTab(2);
        showLauncher();
    }

    private void reloadHighScores() {
        List<GameData.ScoreEntry> all = GameData.loadScores();
        scoreTableModel.setScores(all.subList(0, Math.min(TOP_SCORES, all.size())));
        scoreCards.show(scorePages, all.isEmpty() ? "empty" : "table");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Theme.installLookAndFeel();
            new MainApplication().showLauncher();
        });
    }

    // ================================================================== helper classes
    private static final class ScoreTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private static final String[] COLUMNS = {"NO.", "SURVIVOR", "REGION", "#CHAMBER", "#KILLS", "#TIME"};
        private final transient List<GameData.ScoreEntry> rows = new ArrayList<>();

        void setScores(List<GameData.ScoreEntry> scores) {
            rows.clear();
            rows.addAll(scores);
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int column) { return COLUMNS[column]; }

        @Override
        public Object getValueAt(int row, int column) {
            GameData.ScoreEntry e = rows.get(row);
            return switch (column) {
                case 0 -> row + 1;
                case 1 -> e.name;
                case 2 -> e.map;
                case 3 -> e.wave;
                case 4 -> e.kills;
                default -> GameData.formatRunTime(e.seconds);
            };
        }
    }

    private static final class ScoreCellRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, false, false, row, column);
            setHorizontalAlignment(column >= 3 ? SwingConstants.RIGHT : SwingConstants.LEFT);
            setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, Theme.SLOT), new EmptyBorder(0, 12, 0, 12)));
            setBackground(Theme.PANEL);
            setFont(Theme.font(column == 5 || column == 1 ? Font.BOLD : Font.PLAIN, 13));
            setForeground(column == 0 ? (row < 3 ? Theme.GOLD : Theme.TEXT_3) : (column == 1 || column == 5 ? Theme.TEXT : Theme.TEXT_2));
            if (column == 1) setToolTipText(String.valueOf(value));
            return this;
        }
    }

    private static final class HeroPanel extends JPanel {
        private static final long serialVersionUID = 1L;
        private static final int ZOOM = 2;
        private static final Color FADE = new Color(27, 24, 32, 240), CLEAR = new Color(27, 24, 32, 0);
        private transient GameMap map;
        private transient Player player;
        private boolean showHitbox;
        private double aimPhase;

        HeroPanel() {
            super(new BorderLayout());
            setOpaque(true);
            setBackground(Theme.PANEL);
        }

        void setScene(GameMap newMap, GameData.PlayerClass pc, GameData.WeaponType weapon) {
            map = newMap;
            GameData.GameSettings s = new GameData.GameSettings();
            s.playerClass = pc;
            s.startWeapon = weapon;
            Point spawn = map.getPlayerSpawn();
            player = new Player(s, spawn.x, spawn.y);
            step();
            repaint();
        }

        void setShowHitbox(boolean show) { showHitbox = show; repaint(); }

        void tick() {
            aimPhase += 0.03;
            step();
            step();
            repaint();
        }

        private void step() {
            if (map == null || player == null) return;
            map.update();
            player.setAim(player.getX() + Math.cos(aimPhase) * 60, player.getY() + Math.sin(aimPhase * 2) * 20);
            player.update();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (map == null || player == null) return;
            Graphics2D g2 = Theme.smooth(g);
            int w = getWidth(), h = getHeight(), viewW = w / ZOOM + 1, viewH = h / ZOOM + 1;
            int camX = clamp((int) player.getX() - viewW / 2, map.getWidthPx() - viewW);
            int camY = clamp((int) player.getY() - viewH * 11 / 20, map.getHeightPx() - viewH);
            Rectangle view = new Rectangle(camX, camY, viewW, viewH);

            Graphics2D world = (Graphics2D) g2.create();
            world.scale(ZOOM, ZOOM);
            world.translate(-camX, -camY);
            map.drawGround(world, view);
            List<GameMap.Drawable> things = new ArrayList<>(map.getPropsInView(view));
            things.add(player);
            things.sort(GameMap.DEPTH_ORDER);
            for (GameMap.Drawable d : things) d.draw(world);
            if (showHitbox) player.drawHitbox(world);
            world.dispose();

            g2.setPaint(new LinearGradientPaint(0, 0, 0, h, new float[]{0f, 0.32f, 0.47f, 0.62f, 0.76f, 1f},
                    new Color[]{FADE, FADE, CLEAR, CLEAR, FADE, FADE}));
            g2.fillRect(0, 0, w, h);
            g2.setColor(Theme.INK);
            g2.fillRect(w - 2 * Theme.S, 0, 2 * Theme.S, h);
            g2.dispose();
        }

        private static int clamp(int v, int max) { return Math.max(0, Math.min(Math.max(0, max), v)); }
    }
}
