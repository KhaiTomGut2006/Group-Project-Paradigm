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
import javax.swing.ImageIcon;
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
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * The launcher (first window), drawn with the Hollow Creek UI kit. Left: the real camp of the
 * chosen map with your survivor, the title and the crew. Right: four tabs - PLAY (name, class,
 * weapon, map, difficulty), OPTIONS (gameplay and audio check boxes, volume), SCORES (high-score
 * table) and HELP (how to play) - and the QUIT / START buttons. START opens the GameFrame.
 *
 * Event handlers presented in class:
 *   actionPerformed(ActionEvent) - buttons, tabs, class buttons, check boxes, combo boxes, name field, animation timer
 *   stateChanged(ChangeEvent)    - the difficulty and volume JSliders
 */
public final class MainApplication extends JFrame implements ActionListener, ChangeListener {

    private static final long serialVersionUID = 1L;

    private static final int MAX_NAME_LENGTH = 16;
    // the name is written into the score file, so only plain characters are allowed
    private static final String NAME_PATTERN = "[A-Za-z0-9 _.-]+";
    private static final String NAME_HINT = "Letters, digits, space, - _ .  (max " + MAX_NAME_LENGTH + ")";
    private static final String[] DIFFICULTY_NAMES = {"Rookie", "Survivor", "Veteran", "Hardened", "Nightmare"};
    private static final String[] TAB_NAMES = {"Play", "Options", "Scores", "Help"};
    private static final int ANIMATION_MS = 32;       // the camp picture advances 2 game ticks (16 ms) per timer tick
    private static final int TOP_SCORES = 10;
    private static final int HERO_WIDTH = 340;

    // ---- play tab
    private Theme.KitField nameField;
    private JLabel nameHintLabel;
    private final JRadioButton[] classRadios = new JRadioButton[GameData.PlayerClass.values().length];
    private final ButtonGroup classGroup = new ButtonGroup();
    private JTextArea classInfoArea;
    private JComboBox<GameData.WeaponType> weaponCombo;
    private JTextArea weaponInfoArea;
    private JComboBox<GameData.MapType> mapCombo;
    private JTextArea mapInfoArea;
    private JSlider difficultySlider;
    private Theme.PixelLabel difficultyValueLabel;
    private JLabel difficultyInfoLabel;

    // ---- options tab
    private JCheckBox autoFireBox, doubleSpawnBox, hitboxBox, soundBox, musicBox;
    private JSlider volumeSlider;
    private Theme.PixelLabel volumeValueLabel;

    // ---- tabs, scores, help, buttons
    private final JToggleButton[] tabButtons = new JToggleButton[TAB_NAMES.length];
    private final CardLayout tabCards = new CardLayout();
    private final JPanel tabPages = Theme.transparentPanel(tabCards);
    private ScoreTableModel scoreTableModel;
    private final CardLayout scoreCards = new CardLayout();
    private final JPanel scorePages = Theme.transparentPanel(scoreCards);
    private JTextArea howToArea;
    private JButton startButton, quitButton;

    private HeroPanel heroPanel;
    private final Timer animationTimer = new Timer(ANIMATION_MS, this);
    // GameMap is not Serializable; the launcher is never serialized
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

        // show the starting selection everywhere before the window appears
        showClassInfo();
        showWeaponInfo();
        showMapInfo();
        showVolume();
        updateDifficultyReadout();
        reloadHighScores();
        selectTab(0);

        setSize(GameData.LAUNCHER_W, GameData.LAUNCHER_H);
        setMinimumSize(new Dimension(GameData.LAUNCHER_W, GameData.LAUNCHER_H));
        setLocationRelativeTo(null);
    }

    // ================================================================== layout: left side
    /** The camp picture with the pixel-font title on top and the crew (names + IDs) at the bottom. */
    private HeroPanel buildHeroPanel() {
        HeroPanel hero = new HeroPanel();
        hero.setPreferredSize(new Dimension(HERO_WIDTH, 10));
        hero.setBorder(new EmptyBorder(30, 30, 26, 32));

        JPanel title = Theme.transparentPanel(new GridLayout(0, 1, 0, 10));
        title.add(Theme.row(0, new Theme.PixelLabel("EGCO 213 - GROUP PROJECT 3", 2, Theme.GOLD)));
        title.add(Theme.row(0, shadowed("LAST CAMP", 6, Theme.TEXT)));
        title.add(Theme.row(0, shadowed("SURVIVOR", 6, Theme.GOLD)));
        title.add(Theme.row(0, new Theme.PixelLabel("KILL. LOOT. CRAFT. SURVIVE "
                + formatTime(GameData.TIME_LIMIT_SECONDS) + ".", 2, Theme.TEXT_2)));
        hero.add(title, BorderLayout.NORTH);

        JPanel crew = Theme.transparentPanel(new GridLayout(0, 1, 0, 4));
        crew.add(Theme.row(0, Theme.caption("Crew")));
        for (String member : GameData.GROUP_MEMBERS) {
            crew.add(buildMemberRow(member));
        }
        hero.add(crew, BorderLayout.SOUTH);
        return hero;
    }

    private static Theme.PixelLabel shadowed(String text, int scale, Color color) {
        Theme.PixelLabel label = new Theme.PixelLabel(text, scale, color);
        label.setShadow(true);
        return label;
    }

    /** "Tapatsawin Chaieua   6813105" -> name on the left, student ID on the right. */
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

    // ================================================================== layout: right side
    /** Tab buttons on top, the selected page on a kit panel, QUIT / START at the bottom. */
    private JPanel buildRightColumn() {
        JPanel column = Theme.transparentPanel(new BorderLayout(0, 14));
        column.setBorder(new EmptyBorder(24, 26, 20, 26));
        column.add(buildTabBar(), BorderLayout.NORTH);

        tabPages.add(buildPlayPage(), TAB_NAMES[0]);
        tabPages.add(buildOptionsPage(), TAB_NAMES[1]);
        tabPages.add(buildScoresPage(), TAB_NAMES[2]);
        tabPages.add(buildHelpPage(), TAB_NAMES[3]);
        column.add(tabPages, BorderLayout.CENTER);

        quitButton = Theme.button("Quit", Theme.ButtonStyle.RED);       // the kit's ready-made QUIT button
        quitButton.addActionListener(this);
        startButton = Theme.button("Start", Theme.ButtonStyle.RED);     // the kit's START button, drawn 3x
        Theme.setButtonScale(startButton, 3);
        startButton.addActionListener(this);
        getRootPane().setDefaultButton(startButton);   // Enter starts the game
        JPanel bottom = Theme.transparentPanel(new BorderLayout());
        JPanel quitHolder = Theme.transparentPanel(new GridBagLayout());   // keeps QUIT its own size, centred
        quitHolder.add(quitButton);
        bottom.add(quitHolder, BorderLayout.WEST);
        bottom.add(startButton, BorderLayout.EAST);
        column.add(bottom, BorderLayout.SOUTH);
        return column;
    }

    /** Four toggle buttons; only one can be selected (ButtonGroup), like tabs. */
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

    /** Name, class, weapon, map and difficulty: everything needed to start a run. */
    private JComponent buildPlayPage() {
        JPanel page = Theme.transparentPanel(new GridBagLayout());
        Form form = new Form(page);

        nameField = new Theme.KitField("Survivor", 16);
        nameField.addActionListener(this);
        nameField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                clearNameError();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                clearNameError();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                clearNameError();
            }
        });
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

        weaponCombo = new JComboBox<>(GameData.START_WEAPONS);
        weaponCombo.setSelectedItem(GameData.WeaponType.PISTOL);
        Theme.styleCombo(weaponCombo, new WeaponRenderer());
        weaponCombo.addActionListener(this);
        weaponInfoArea = Theme.textBlock("", 12, Theme.TEXT_2);
        weaponInfoArea.setRows(2);
        mapCombo = new JComboBox<>(GameData.MapType.values());
        Theme.styleCombo(mapCombo, new Theme.ListRenderer<GameData.MapType>() {
            private static final long serialVersionUID = 1L;

            @Override
            String textFor(GameData.MapType value) {
                return value.displayName;
            }
        });
        mapCombo.addActionListener(this);
        mapInfoArea = Theme.textBlock("", 12, Theme.TEXT_2);
        mapInfoArea.setRows(2);
        JPanel loadout = Theme.transparentPanel(new GridLayout(1, 2, 14, 0));
        loadout.add(fieldColumn("Weapon", weaponCombo, weaponInfoArea));
        loadout.add(fieldColumn("Map", mapCombo, mapInfoArea));
        form.add(loadout, 14);

        difficultySlider = new JSlider(1, 5, 3);
        difficultySlider.setSnapToTicks(true);
        difficultySlider.setMajorTickSpacing(1);
        Theme.styleSlider(difficultySlider);
        difficultySlider.addChangeListener(this);
        difficultyValueLabel = new Theme.PixelLabel("", 2, Theme.GOLD);
        difficultyInfoLabel = Theme.label("", Font.PLAIN, 12, Theme.TEXT_2);
        form.add(headerWithValue("Difficulty", difficultyValueLabel), 14);
        form.add(difficultySlider, 4);
        form.add(difficultyInfoLabel, 0);
        form.fillRest();
        return onPanel(page, Theme.Panel.METAL);
    }

    /** Check boxes for gameplay and sound, plus the volume slider. */
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
        return onPanel(page, Theme.Panel.METAL);
    }

    /** The high-score JTable, or a short message while there are no scores yet. */
    private JComponent buildScoresPage() {
        scoreTableModel = new ScoreTableModel();
        JTable scoreTable = new JTable(scoreTableModel);
        Theme.styleTable(scoreTable, 30);
        scoreTable.setRowSelectionAllowed(false);
        scoreTable.setDefaultRenderer(Object.class, new ScoreCellRenderer());
        int[] widths = {40, 120, 140, 60, 56, 74};
        for (int i = 0; i < widths.length; i++) {
            scoreTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        JPanel empty = Theme.transparentPanel(new GridBagLayout());
        JPanel message = Theme.transparentPanel(new GridLayout(0, 1, 0, 10));
        Theme.PixelLabel title = new Theme.PixelLabel("NO RUNS YET", 3, Theme.TEXT);
        title.setAlignment(SwingConstants.CENTER);
        JLabel sub = Theme.label("Finish a run and your best scores show up here.", Font.PLAIN, 13, Theme.TEXT_2);
        sub.setHorizontalAlignment(SwingConstants.CENTER);
        message.add(title);
        message.add(sub);
        empty.add(message);

        scorePages.add(Theme.scroll(scoreTable, Theme.PANEL), "table");
        scorePages.add(empty, "empty");
        return onPanel(scorePages, Theme.Panel.METAL);
    }

    /** How to play, written on the kit's paper note: goal, controls as key caps, the loot-and-craft loop. */
    private JComponent buildHelpPage() {
        JPanel page = Theme.transparentPanel(new GridBagLayout());
        Form form = new Form(page);

        howToArea = Theme.textBlock("Survive for " + formatTime(GameData.TIME_LIMIT_SECONDS) + " across alternating Scavenge and "
                + "Horde Raid phases! Venture outside base to hunt zombies and loot Supply Chests, then return to Base Camp before raids hit.",
                13, Theme.PAPER_TEXT);
        form.add(new Theme.PixelLabel("GOAL", 2, Theme.DARK_RED), 0);
        form.add(howToArea, 6);

        // each row: what it does, then the keys (sprites from the Hollow Creek UI kit)
        String[][] controls = {
            {"Move", "w", "a", "s", "d"}, {"Aim, hold to fire", "mouse_left"},
            {"Grenade", "mouse_right", "g"}, {"Medkit +" + GameData.MEDKIT_HEAL + " HP", "e"},
            {"Switch weapon", "mouse_mid", "1to8"}, {"Talk to Base NPCs", "b"},
            {"Pause", "p", "esc"}, {"Craft / Build / Heal", "enter"}
        };
        JPanel keys = Theme.transparentPanel(new GridLayout(0, 2, 12, 6));
        for (String[] c : controls) {
            String[] keyNames = Arrays.copyOfRange(c, 1, c.length);
            keys.add(Theme.row(8, Theme.keyCaps(2, keyNames), Theme.label(c[0], Font.PLAIN, 13, Theme.PAPER_TEXT)));
        }
        form.add(new Theme.PixelLabel("CONTROLS", 2, Theme.DARK_RED), 14);
        form.add(keys, 8);

        form.add(new Theme.PixelLabel("SCAVENGE, BASE NPC & RAIDS", 2, Theme.DARK_RED), 14);
        form.add(Theme.textBlock("Return inside your Base Camp (green minimap icon) and press B to talk to the Gunsmith, Doctor, "
                + "and Engineer: craft weapons, upgrade stats, heal wounds, and build Guard Posts, Fortified Walls, a Medical Tent, "
                + "and a Supply Depot to hold off periodic Horde Raids and Bosses.",
                13, Theme.PAPER_TEXT), 6);
        form.fillRest();
        Theme.Card card = new Theme.Card(new BorderLayout(), Theme.Panel.PAPER);
        card.setBorder(new EmptyBorder(16, 40, 14, 22));   // clear of the paper's red margin line
        card.add(page, BorderLayout.CENTER);
        return card;
    }

    // ---- small layout helpers
    private static JComponent onPanel(JComponent content, Theme.Panel panel) {
        Theme.Card card = new Theme.Card(new BorderLayout(), panel);
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

    /** A kit check box with a one-line explanation under its label. */
    private static JPanel checkRow(JCheckBox box, String description) {
        JPanel row = Theme.transparentPanel(new BorderLayout(0, 0));
        row.add(box, BorderLayout.NORTH);
        JLabel text = Theme.label(description, Font.PLAIN, 12, Theme.TEXT_3);
        text.setBorder(new EmptyBorder(0, 36, 0, 0));   // lines up with the check box's label
        row.add(text, BorderLayout.CENTER);
        return row;
    }

    /** Stacks components top to bottom, full width, with a gap above each one. */
    private static final class Form {
        private final JPanel panel;
        private final GridBagConstraints c = new GridBagConstraints();

        Form(JPanel panel) {
            this.panel = panel;
            c.gridx = 0;
            c.gridy = 0;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.anchor = GridBagConstraints.NORTHWEST;
        }

        void add(Component component, int gapAbove) {
            c.insets = new Insets(gapAbove, 0, 0, 0);
            panel.add(component, c);
            c.gridy++;
        }

        /** An empty last row takes the spare height, so the content stays at the top. */
        void fillRest() {
            c.weighty = 1;
            c.insets = new Insets(0, 0, 0, 0);
            panel.add(Theme.transparentPanel(null), c);
        }
    }

    // ================================================================== event handlers
    /** Every button, tab, class button, check box, combo box, the name field and the animation timer end up here. */
    @Override
    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();
        if (source == animationTimer) {
            heroPanel.tick();
            return;
        }
        for (int i = 0; i < tabButtons.length; i++) {
            if (source == tabButtons[i]) {
                selectTab(i);
                AssetLoader.playSound("click");
                return;
            }
        }
        if (source == startButton || source == nameField) {
            startSurvival();
        } else if (source == quitButton) {
            quitLauncher();
        } else if (source instanceof JRadioButton) {
            showClassInfo();
            AssetLoader.playSound("click");
        } else if (source == weaponCombo) {
            showWeaponInfo();
            AssetLoader.playSound("click");
        } else if (source == mapCombo) {
            showMapInfo();
            AssetLoader.playSound("click");
        } else if (source == soundBox) {
            AssetLoader.setSoundEnabled(soundBox.isSelected());
            AssetLoader.playSound("click");   // only audible when it was just switched on
        } else if (source == musicBox) {
            AssetLoader.setMusicEnabled(musicBox.isSelected());
        } else if (source == hitboxBox) {
            heroPanel.setShowHitbox(hitboxBox.isSelected());
        } else if (source == doubleSpawnBox) {
            updateDifficultyReadout();       // the spawn numbers double
        } else if (source == autoFireBox) {
            AssetLoader.playSound("click");
        }
    }

    /** The two JSliders: difficulty updates its readout, volume is applied to the sound system live. */
    @Override
    public void stateChanged(ChangeEvent e) {
        if (e.getSource() == volumeSlider) {
            AssetLoader.setVolume(volumeSlider.getValue());   // the launcher music gets louder/quieter at once
            showVolume();
            if (!volumeSlider.getValueIsAdjusting()) {
                AssetLoader.playSound("click");             // a sample at the new level once the knob is released
            }
        } else if (e.getSource() == difficultySlider) {
            updateDifficultyReadout();
        }
    }

    // ================================================================== what the handlers do
    private void selectTab(int index) {
        tabButtons[index].setSelected(true);
        tabCards.show(tabPages, TAB_NAMES[index]);
    }

    private GameData.PlayerClass getSelectedClass() {
        GameData.PlayerClass[] classes = GameData.PlayerClass.values();
        for (int i = 0; i < classRadios.length; i++) {
            if (classRadios[i].isSelected()) {
                return classes[i];
            }
        }
        return GameData.PlayerClass.SCAVENGER;
    }

    private GameData.WeaponType getSelectedWeapon() {
        Object item = weaponCombo.getSelectedItem();
        return item instanceof GameData.WeaponType ? (GameData.WeaponType) item : GameData.WeaponType.PISTOL;
    }

    private GameData.MapType getSelectedMap() {
        Object item = mapCombo.getSelectedItem();
        return item instanceof GameData.MapType ? (GameData.MapType) item : GameData.MapType.WASTELAND_HIGHWAY;
    }

    private void showClassInfo() {
        classInfoArea.setText(getSelectedClass().description);
        refreshHero();
    }

    private void showWeaponInfo() {
        weaponInfoArea.setText(describeWeapon(getSelectedWeapon()));
        refreshHero();
    }

    /** Two short lines from GameData.WeaponType: the numbers, then what makes the weapon special. */
    private static String describeWeapon(GameData.WeaponType w) {
        String numbers = w.damage + (w.pellets > 1 ? " x" + w.pellets : "") + " damage, "
                + String.format("%.1f", 1000.0 / w.cooldownMs) + " hits/s";
        String trait;
        if (w.melee) {
            trait = "Melee, no ammo needed";
        } else if (w.ammoCost == 0) {
            trait = "Unlimited ammo";
        } else if (w.pellets > 1) {
            trait = w.pellets + " pellets per shot";
        } else if (w.pierce > 0) {
            trait = "Pierces " + w.pierce + (w.pierce == 1 ? " enemy" : " enemies");
        } else {
            trait = "Fast automatic fire";
        }
        return numbers + "\n" + trait;
    }

    private void showMapInfo() {
        mapInfoArea.setText(getSelectedMap().description);
        refreshHero();
    }

    /** Maps are generated from a fixed seed, so one copy per type is enough (about 10-150 ms to build). */
    private GameMap getMap(GameData.MapType type) {
        GameMap map = mapCache.get(type);
        if (map == null) {
            map = new GameMap(type, type.seed);
            mapCache.put(type, map);
        }
        return map;
    }

    private void refreshHero() {
        if (heroPanel != null && weaponCombo != null && mapCombo != null) {
            heroPanel.setScene(getMap(getSelectedMap()), getSelectedClass(), getSelectedWeapon());
        }
    }

    private void showVolume() {
        int v = volumeSlider.getValue();
        volumeValueLabel.setText(v == 0 ? "MUTED" : v + "%");
    }

    /** Uses the same formulas as the game (GameData) so the numbers shown are the real ones. */
    private void updateDifficultyReadout() {
        int d = difficultySlider.getValue();
        boolean doubled = doubleSpawnBox.isSelected();
        difficultyValueLabel.setText(d + " - " + DIFFICULTY_NAMES[d - 1].toUpperCase());
        int zombieHp = (int) Math.round(GameData.MonsterType.ZOMBIE_NORMAL.maxHp * GameData.monsterHpScale(1, d));
        difficultyInfoLabel.setText("Zombies have " + zombieHp + " HP. "
                + GameData.spawnBatchSize(1, d, doubled) + " per spawn at wave 1, "
                + GameData.spawnBatchSize(5, d, doubled) + " at wave 5.");
    }

    /** Validates the name; on a problem it explains it under the field and puts the cursor back there. */
    private String validatePlayerName() {
        String name = nameField.getText().trim();
        String problem = null;
        if (name.isEmpty()) {
            problem = "Please type a name first.";
        } else if (name.length() > MAX_NAME_LENGTH) {
            problem = "Too long: " + name.length() + " of max " + MAX_NAME_LENGTH + " characters.";
        } else if (!name.matches(NAME_PATTERN)) {
            problem = "Only letters, digits, space and - _ . please.";
        }
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

    /** Collects every control into the settings object the game reads. */
    private GameData.GameSettings buildSettings(String playerName) {
        GameData.GameSettings s = new GameData.GameSettings();
        s.playerName = playerName;
        s.playerClass = getSelectedClass();
        s.startWeapon = getSelectedWeapon();
        s.map = getSelectedMap();
        s.difficulty = difficultySlider.getValue();
        s.volume = volumeSlider.getValue();
        s.autoFire = autoFireBox.isSelected();
        s.showHitbox = hitboxBox.isSelected();
        s.soundOn = soundBox.isSelected();
        s.musicOn = musicBox.isSelected();
        s.doubleSpawn = doubleSpawnBox.isSelected();
        return s;
    }

    private void startSurvival() {
        String name = validatePlayerName();
        if (name == null) {
            return;
        }
        GameData.GameSettings settings = buildSettings(name);
        AssetLoader.playSound("click");
        hideLauncher();
        try {
            new GameFrame(settings, this);   // GameFrame shows itself and calls onGameFinished() at the end
        } catch (RuntimeException ex) {
            // never leave the user with no window at all
            JOptionPane.showMessageDialog(null, "The game could not start:\n" + ex, "Last Camp Survivor",
                    JOptionPane.ERROR_MESSAGE);
            showLauncher();
        }
    }

    private void quitLauncher() {
        animationTimer.stop();
        AssetLoader.stopMusic();
        dispose();
        System.exit(0);
    }

    /** Shows the launcher with its animation and music running. */
    public void showLauncher() {
        AssetLoader.setVolume(volumeSlider.getValue());   // the game may have changed the sound settings
        AssetLoader.setSoundEnabled(soundBox.isSelected());
        AssetLoader.setMusicEnabled(musicBox.isSelected());
        setVisible(true);
        toFront();
        animationTimer.start();
        AssetLoader.playMusic("bgm");
    }

    /** Hides the launcher; the animation timer and the music stop with it. */
    private void hideLauncher() {
        animationTimer.stop();
        AssetLoader.stopMusic();
        setVisible(false);
    }

    /** Called by GameFrame when a run ends or its window closes: back to the launcher with fresh scores. */
    public void onGameFinished() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::onGameFinished);
            return;
        }
        reloadHighScores();
        if (scoreTableModel.getRowCount() > 0) {
            selectTab(2);   // show where the run just landed
        }
        showLauncher();
    }

    private void reloadHighScores() {
        List<GameData.ScoreEntry> all = GameData.loadScores();
        scoreTableModel.setScores(all.subList(0, Math.min(TOP_SCORES, all.size())));
        scoreCards.show(scorePages, all.isEmpty() ? "empty" : "table");
    }

    private static String formatTime(int seconds) {
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }

    // ================================================================== entry point
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                Theme.installLookAndFeel();
                MainApplication launcher = new MainApplication();
                launcher.showLauncher();
            }
        });
    }

    // ================================================================== helper classes
    /**
     * The high-score JTable's data: top runs, read-only (AbstractTableModel cells are not editable).
     * A leading "#" in a column name tells the kit header to right-align it.
     */
    private static final class ScoreTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private static final String[] COLUMNS = {"NO.", "PLAYER", "MAP", "#WAVE", "#TIME", "#SCORE"};
        private final transient List<GameData.ScoreEntry> rows = new ArrayList<>();

        void setScores(List<GameData.ScoreEntry> scores) {
            rows.clear();
            rows.addAll(scores);
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            GameData.ScoreEntry e = rows.get(row);
            switch (column) {
                case 0: return row + 1;
                case 1: return e.name;
                case 2: return e.map;
                case 3: return e.wave;
                case 4: return formatTime(e.seconds);
                default: return e.score;
            }
        }
    }

    /** Top three ranks in gold, the score in bold, numbers right-aligned, a dark line between rows. */
    private static final class ScoreCellRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, false, false, row, column);
            setHorizontalAlignment(column >= 3 ? SwingConstants.RIGHT : SwingConstants.LEFT);
            setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, Theme.SLOT),
                    new EmptyBorder(0, 12, 0, 12)));
            setBackground(Theme.PANEL);
            setFont(Theme.font(column == 5 || column == 1 ? Font.BOLD : Font.PLAIN, 13));
            if (column == 0) {
                setForeground(row < 3 ? Theme.GOLD : Theme.TEXT_3);
            } else {
                setForeground(column == 1 || column == 5 ? Theme.TEXT : Theme.TEXT_2);
            }
            if (column == 1) {
                setToolTipText(String.valueOf(value));
            }
            return this;
        }
    }

    /** Weapon combo entries with their sprite next to the name. */
    private static final class WeaponRenderer extends Theme.ListRenderer<GameData.WeaponType> {
        private static final long serialVersionUID = 1L;
        private final transient Map<GameData.WeaponType, ImageIcon> icons = new EnumMap<>(GameData.WeaponType.class);

        @Override
        String textFor(GameData.WeaponType w) {
            ImageIcon icon = icons.get(w);
            if (icon == null) {
                icon = new ImageIcon(Theme.fit(AssetLoader.getWeaponIcon(w), 34, 18));
                icons.put(w, icon);
            }
            setIcon(icon);
            setIconTextGap(10);
            return w.displayName;
        }
    }

    /**
     * The left side of the launcher: the real camp of the chosen map (ground, props, animated
     * fire and flag) with your survivor in the middle, slowly aiming around with the chosen weapon.
     * Dark fades at the top and bottom keep the title and crew readable.
     * The launcher's javax.swing.Timer calls tick(); it stops whenever the launcher is hidden.
     */
    private static final class HeroPanel extends JPanel {
        private static final long serialVersionUID = 1L;
        private static final int ZOOM = 2;
        private static final Color FADE = new Color(27, 24, 32, 240);
        private static final Color CLEAR = new Color(27, 24, 32, 0);
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

        void setShowHitbox(boolean show) {
            showHitbox = show;
            repaint();
        }

        /** Two game ticks per timer tick (the timer runs at 32 ms, a game tick is 16 ms). */
        void tick() {
            aimPhase += 0.03;
            step();
            step();
            repaint();
        }

        private void step() {
            if (map == null || player == null) {
                return;
            }
            map.update();   // flame barrel and flag animation
            // the aim point circles the survivor, so the gun turns and the body flips left and right
            player.setAim(player.getX() + Math.cos(aimPhase) * 60, player.getY() + Math.sin(aimPhase * 2) * 20);
            player.update();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (map == null || player == null) {
                return;
            }
            Graphics2D g2 = Theme.smooth(g);
            int w = getWidth(), h = getHeight();
            int viewW = w / ZOOM + 1, viewH = h / ZOOM + 1;
            // camera: survivor in the middle, a little below the centre; never past the map edge
            int camX = clamp((int) player.getX() - viewW / 2, 0, map.getWidthPx() - viewW);
            int camY = clamp((int) player.getY() - viewH * 11 / 20, 0, map.getHeightPx() - viewH);
            Rectangle view = new Rectangle(camX, camY, viewW, viewH);

            Graphics2D world = (Graphics2D) g2.create();
            world.scale(ZOOM, ZOOM);
            world.translate(-camX, -camY);
            map.drawGround(world, view);
            List<GameMap.Drawable> things = new ArrayList<>(map.getPropsInView(view));
            things.add(player);
            things.sort(GameMap.DEPTH_ORDER);
            for (GameMap.Drawable d : things) {
                d.draw(world);
            }
            if (showHitbox) {
                player.drawHitbox(world);
            }
            world.dispose();

            // nearly solid behind the title and the crew, clear around the survivor
            g2.setPaint(new LinearGradientPaint(0, 0, 0, h, new float[]{0f, 0.32f, 0.47f, 0.62f, 0.76f, 1f},
                    new Color[]{FADE, FADE, CLEAR, CLEAR, FADE, FADE}));
            g2.fillRect(0, 0, w, h);
            // a dark pixel edge between the picture and the menu, like the kit's panel outlines
            g2.setColor(Theme.INK);
            g2.fillRect(w - 2 * Theme.S, 0, 2 * Theme.S, h);
            g2.dispose();
        }

        private static int clamp(int v, int min, int max) {
            return Math.max(min, Math.min(Math.max(min, max), v));
        }
    }
}
