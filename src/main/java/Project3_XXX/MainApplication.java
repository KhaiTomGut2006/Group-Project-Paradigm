// Member1 Firstname Lastname   6XXXXXX1
// Member2 Firstname Lastname   6XXXXXX2
// Member3 Firstname Lastname   6XXXXXX3
// Member4 Firstname Lastname   6XXXXXX4
// Member5 Firstname Lastname   6XXXXXX5
package Project3_XXX;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Hashtable;
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
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumnModel;

/**
 * The launcher (first window). The player types a name, picks a class, weapon, map and options,
 * then presses "Start Survival" to open the GameFrame. It also shows the high scores, the
 * how-to-play text and the group members.
 *
 * Event handlers presented in class:
 *   actionPerformed(ActionEvent) - buttons, radio buttons, check boxes, combo boxes, name field, preview timer
 *   stateChanged(ChangeEvent)    - the volume and difficulty JSliders
 */
public final class MainApplication extends JFrame implements ActionListener, ChangeListener {

    private static final long serialVersionUID = 1L;

    private static final int MAX_NAME_LENGTH = 16;
    // the name is written into the score file, so only plain characters are allowed
    private static final String NAME_PATTERN = "[A-Za-z0-9 _.-]+";
    private static final String NAME_HINT = "A-Z, 0-9, space - _ .  max " + MAX_NAME_LENGTH;
    private static final String[] DIFFICULTY_NAMES = {"Rookie", "Survivor", "Veteran", "Hardened", "Nightmare"};
    private static final int PREVIEW_TICK_MS = 32;      // the preview advances 2 game ticks (16 ms each) per timer tick
    private static final int TOP_SCORES = 10;
    private static final int MAP_THUMB_W = 150, MAP_THUMB_H = 96;

    // ---- survivor
    private JTextField nameField;
    private JLabel nameHintLabel;
    private final JRadioButton[] classRadios = new JRadioButton[GameData.PlayerClass.values().length];
    private final ButtonGroup classGroup = new ButtonGroup();
    private JTextArea classInfoArea;
    private SurvivorPreview survivorPreview;

    // ---- loadout
    private JComboBox<GameData.WeaponType> weaponCombo;
    private JLabel weaponIconLabel;
    private JTextArea weaponStatsArea;
    private JComboBox<GameData.MapType> mapCombo;
    private JLabel mapPreviewLabel;
    private JTextArea mapDescriptionArea;

    // ---- options
    private JCheckBox autoFireBox, hitboxBox, soundBox, musicBox, doubleSpawnBox;
    private JLabel optionHintLabel;
    private JSlider volumeSlider, difficultySlider;
    private JLabel volumeValueLabel, difficultyNameLabel;
    private JTextArea difficultyEffectArea;

    // ---- scores, help, crew
    private JTabbedPane infoTabs;
    private ScoreTableModel scoreTableModel;
    private JTable scoreTable;
    private CardLayout scoreCards;
    private JPanel scoreCardPanel;
    private JTextArea howToArea;
    private JButton startButton, quitButton;

    private final Timer previewTimer = new Timer(PREVIEW_TICK_MS, this);
    // GameMap and BufferedImage are not Serializable; the launcher is never serialized
    private final transient Map<GameData.MapType, GameMap> mapCache = new EnumMap<>(GameData.MapType.class);

    public MainApplication() {
        super("Last Camp Survivor - Launcher");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        Backdrop root = new Backdrop(new BorderLayout(0, 10));
        root.setBorder(new EmptyBorder(12, 14, 14, 14));
        root.add(buildHeaderPanel(), BorderLayout.NORTH);
        root.add(buildBodyPanel(), BorderLayout.CENTER);
        setContentPane(root);

        // show the initial selection everywhere before the window appears
        showClassInfo();
        showWeaponInfo();
        showMapInfo();
        showVolume();
        updateDifficultyReadout();
        reloadHighScores();

        setSize(GameData.LAUNCHER_W, GameData.LAUNCHER_H);
        setMinimumSize(new Dimension(GameData.LAUNCHER_W, GameData.LAUNCHER_H));
        setLocationRelativeTo(null);
    }

    // ================================================================== layout
    private JPanel buildHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel("LAST CAMP SURVIVOR");
        title.setFont(Theme.font(Font.BOLD, 26));
        title.setForeground(Theme.ACCENT);
        JLabel subtitle = new JLabel("Shoot the horde, loot the parts, craft in camp - and survive "
                + formatTime(GameData.TIME_LIMIT_SECONDS) + ".");
        subtitle.setFont(Theme.font(Font.PLAIN, 13));
        subtitle.setForeground(Theme.MUTED);

        JPanel titles = new JPanel(new GridLayout(2, 1));
        titles.setOpaque(false);
        titles.add(title);
        titles.add(subtitle);
        header.add(titles, BorderLayout.WEST);

        JLabel course = new JLabel("EGCO 213  |  Group Project 3");
        course.setFont(Theme.font(Font.BOLD, 12));
        course.setForeground(Theme.MUTED);
        course.setVerticalAlignment(SwingConstants.BOTTOM);
        header.add(course, BorderLayout.EAST);
        return header;
    }

    /** Two rows: the three set-up sections on top; scores/help and crew/start below. */
    private JPanel buildBodyPanel() {
        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 0.6;

        // fixed base widths, so the columns do not jump when a description gets longer or shorter
        c.gridy = 0;
        c.gridx = 0;
        c.weightx = 0.32;
        c.insets = new Insets(0, 0, 10, 10);
        body.add(fixSize(buildSurvivorPanel(), 268, 300), c);
        c.gridx = 1;
        c.weightx = 0.34;
        body.add(fixSize(buildLoadoutPanel(), 286, 300), c);
        c.gridx = 2;
        c.weightx = 0.34;
        c.insets = new Insets(0, 0, 10, 0);
        body.add(fixSize(buildOptionsPanel(), 286, 300), c);

        c.gridy = 1;
        c.weighty = 0.4;
        c.gridx = 0;
        c.gridwidth = 2;
        c.insets = new Insets(0, 0, 0, 10);
        body.add(fixSize(buildInfoTabs(), 564, 180), c);
        c.gridx = 2;
        c.gridwidth = 1;
        c.insets = new Insets(0, 0, 0, 0);
        body.add(fixSize(buildCrewAndStartPanel(), 286, 180), c);
        return body;
    }

    private static JComponent fixSize(JComponent panel, int width, int height) {
        Dimension size = new Dimension(width, height);
        panel.setPreferredSize(size);
        panel.setMinimumSize(size);
        return panel;
    }

    private JPanel buildSurvivorPanel() {
        Section section = new Section("1  SURVIVOR", new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;

        JLabel nameLabel = Theme.label("Name", Font.BOLD, 12, Theme.TEXT);
        c.gridx = 0;
        c.gridy = 0;
        c.insets = new Insets(0, 0, 0, 8);
        section.add(nameLabel, c);

        nameField = new JTextField("Survivor", 12);
        Theme.styleTextField(nameField);
        nameField.setToolTipText(NAME_HINT);
        nameField.addActionListener(this);     // Enter in the field = Start
        c.gridx = 1;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 0, 0);
        section.add(nameField, c);

        nameHintLabel = Theme.label(NAME_HINT, Font.PLAIN, 11, Theme.MUTED);
        c.gridx = 1;
        c.gridy = 1;
        c.insets = new Insets(3, 0, 6, 0);
        section.add(nameHintLabel, c);

        JPanel classRow = new JPanel(new BorderLayout(8, 0));
        classRow.setOpaque(false);
        classRow.add(buildClassRadioPanel(), BorderLayout.CENTER);
        survivorPreview = new SurvivorPreview();
        classRow.add(survivorPreview, BorderLayout.EAST);
        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 2;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 6, 0);
        section.add(classRow, c);

        classInfoArea = Theme.infoArea(3);
        c.gridy = 3;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(0, 0, 0, 0);
        section.add(classInfoArea, c);
        return section;
    }

    private JPanel buildClassRadioPanel() {
        JPanel radios = new JPanel(new GridLayout(classRadios.length, 1, 0, 1));
        radios.setOpaque(false);
        GameData.PlayerClass[] classes = GameData.PlayerClass.values();
        for (int i = 0; i < classes.length; i++) {
            classRadios[i] = new JRadioButton(classes[i].displayName);
            Theme.styleToggle(classRadios[i]);
            classRadios[i].setToolTipText(classes[i].description);
            classGroup.add(classRadios[i]);
            radios.add(classRadios[i]);
        }
        classRadios[0].setSelected(true);
        for (JRadioButton radio : classRadios) {
            radio.addActionListener(this);
        }
        return radios;
    }

    private JPanel buildLoadoutPanel() {
        Section section = new Section("2  LOADOUT", new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.NORTHWEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        weaponCombo = new JComboBox<>(GameData.START_WEAPONS);
        weaponCombo.setRenderer(new WeaponListRenderer());
        weaponCombo.setSelectedItem(GameData.WeaponType.PISTOL);
        Theme.styleCombo(weaponCombo);
        weaponCombo.setToolTipText("Starting weapon (the Pistol is always carried as a backup)");
        addRow(section, c, 0, "Weapon", weaponCombo);

        weaponIconLabel = new JLabel();
        weaponIconLabel.setPreferredSize(new Dimension(64, 64));
        weaponIconLabel.setOpaque(true);
        weaponIconLabel.setBackground(Theme.FIELD);
        weaponIconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        weaponIconLabel.setBorder(BorderFactory.createLineBorder(Theme.EDGE));
        weaponStatsArea = Theme.infoArea(4);
        addPictureRow(section, c, 1, weaponIconLabel, weaponStatsArea);

        mapCombo = new JComboBox<>(GameData.MapType.values());
        Theme.styleCombo(mapCombo);
        mapCombo.setToolTipText("Every map has its own fixed layout around the central camp");
        c.insets = new Insets(10, 0, 0, 0);
        addRow(section, c, 2, "Map", mapCombo);

        mapPreviewLabel = new JLabel();
        mapPreviewLabel.setPreferredSize(new Dimension(MAP_THUMB_W + 2, MAP_THUMB_H + 2));
        mapPreviewLabel.setBorder(BorderFactory.createLineBorder(Theme.EDGE));
        mapDescriptionArea = Theme.infoArea(5);
        addPictureRow(section, c, 3, mapPreviewLabel, mapDescriptionArea);
        c.gridy = 4;
        c.weighty = 1;   // empty filler: keeps the rows at the top when the window is taller
        section.add(new JLabel(), c);
        c.weighty = 0;

        // listeners last, so setting the first selection above does not fire events
        weaponCombo.addActionListener(this);
        mapCombo.addActionListener(this);
        return section;
    }

    /** A "Label  [component]" row inside the loadout section. */
    private void addRow(JPanel panel, GridBagConstraints c, int row, String text, JComponent field) {
        c.gridy = row;
        c.gridx = 0;
        c.weightx = 0;
        c.gridwidth = 1;
        c.insets = new Insets(c.insets.top, 0, 0, 8);
        c.anchor = GridBagConstraints.WEST;   // label centred on the (taller) combo box
        panel.add(Theme.label(text, Font.BOLD, 12, Theme.TEXT), c);
        c.anchor = GridBagConstraints.NORTHWEST;
        c.gridx = 1;
        c.weightx = 1;
        c.insets = new Insets(c.insets.top, 0, 0, 0);
        panel.add(field, c);
        c.insets = new Insets(0, 0, 0, 0);
    }

    /** A picture with a text block beside it, spanning both columns. */
    private void addPictureRow(JPanel panel, GridBagConstraints c, int row, JComponent picture, JComponent text) {
        JPanel line = new JPanel(new BorderLayout(10, 0));
        line.setOpaque(false);
        JPanel pictureHolder = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        pictureHolder.setOpaque(false);
        pictureHolder.add(picture);
        line.add(pictureHolder, BorderLayout.WEST);
        line.add(text, BorderLayout.CENTER);
        c.gridy = row;
        c.gridx = 0;
        c.gridwidth = 2;
        c.weightx = 1;
        c.insets = new Insets(6, 0, 0, 0);
        panel.add(line, c);
        c.gridwidth = 1;
        c.insets = new Insets(0, 0, 0, 0);
    }

    private JPanel buildOptionsPanel() {
        Section section = new Section("3  OPTIONS", new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;

        GameData.GameSettings defaults = new GameData.GameSettings();
        autoFireBox = createOption("Auto-Fire", defaults.autoFire, "fires at the nearest monster");
        hitboxBox = createOption("Show Hitbox", defaults.showHitbox, "boxes drawn (see preview)");
        soundBox = createOption("Sound SFX", defaults.soundOn, "shots, hits and pick-ups");
        musicBox = createOption("Background Music", defaults.musicOn, "looping soundtrack");
        doubleSpawnBox = createOption("Double Spawn Mode", defaults.doubleSpawn, "2x monsters per spawn");
        JPanel boxes = new JPanel(new GridBagLayout());
        boxes.setOpaque(false);
        GridBagConstraints b = new GridBagConstraints();
        b.anchor = GridBagConstraints.WEST;
        b.weightx = 1;
        b.gridy = 0;
        boxes.add(autoFireBox, b);
        boxes.add(hitboxBox, b);
        b.gridy = 1;
        boxes.add(soundBox, b);
        boxes.add(musicBox, b);
        b.gridy = 2;
        b.gridwidth = 2;
        boxes.add(doubleSpawnBox, b);
        c.gridy = 0;
        section.add(boxes, c);

        optionHintLabel = Theme.label(" ", Font.ITALIC, 11, Theme.MUTED);
        c.gridy = 1;
        c.insets = new Insets(2, 0, 6, 0);
        section.add(optionHintLabel, c);

        volumeSlider = createSlider(0, 100, defaults.volume, 25);
        volumeValueLabel = Theme.label("", Font.BOLD, 12, Theme.ACCENT);
        c.gridy = 2;
        c.insets = new Insets(0, 0, 0, 0);
        section.add(sliderHeader("Volume", volumeValueLabel), c);
        c.gridy = 3;
        section.add(volumeSlider, c);

        difficultySlider = createSlider(1, 5, defaults.difficulty, 1);
        difficultyNameLabel = Theme.label("", Font.BOLD, 12, Theme.ACCENT);
        c.gridy = 4;
        c.insets = new Insets(6, 0, 0, 0);
        section.add(sliderHeader("Difficulty", difficultyNameLabel), c);
        c.gridy = 5;
        c.insets = new Insets(0, 0, 0, 0);
        section.add(difficultySlider, c);

        difficultyEffectArea = Theme.infoArea(2);
        c.gridy = 6;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(4, 0, 0, 0);
        section.add(difficultyEffectArea, c);
        return section;
    }

    private JCheckBox createOption(String text, boolean selected, String tip) {
        JCheckBox box = new JCheckBox(text, selected);
        Theme.styleToggle(box);
        box.setToolTipText(tip);
        box.addActionListener(this);
        return box;
    }

    /** Slider with numbers under the major ticks; labels are coloured to suit the dark theme. */
    private JSlider createSlider(int min, int max, int value, int labelStep) {
        JSlider slider = new JSlider(min, max, value);
        slider.setOpaque(false);
        slider.setForeground(Theme.MUTED);
        slider.setMajorTickSpacing(labelStep);
        slider.setMinorTickSpacing(max - min > 10 ? 5 : 1);
        slider.setPaintTicks(true);
        slider.setSnapToTicks(max - min <= 10);
        Hashtable<Integer, JComponent> labels = new Hashtable<>();
        for (int v = min; v <= max; v += labelStep) {
            labels.put(v, Theme.label(String.valueOf(v), Font.PLAIN, 10, Theme.MUTED));
        }
        slider.setLabelTable(labels);
        slider.setPaintLabels(true);
        slider.setFocusable(false);
        slider.addChangeListener(this);
        return slider;
    }

    private JPanel sliderHeader(String text, JLabel valueLabel) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.add(Theme.label(text, Font.BOLD, 12, Theme.TEXT), BorderLayout.WEST);
        row.add(valueLabel, BorderLayout.EAST);
        return row;
    }

    private JTabbedPane buildInfoTabs() {
        infoTabs = new JTabbedPane();
        infoTabs.setFont(Theme.font(Font.BOLD, 12));
        infoTabs.setForeground(Theme.TEXT);
        infoTabs.setBackground(Theme.PANEL);
        infoTabs.setFocusable(false);
        infoTabs.addTab("High Scores", buildScorePanel());
        infoTabs.addTab("How to Play", buildHowToPanel());
        return infoTabs;
    }

    private JPanel buildScorePanel() {
        scoreTableModel = new ScoreTableModel();
        scoreTable = new JTable(scoreTableModel);
        Theme.styleTable(scoreTable);
        scoreTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        scoreTable.setDefaultRenderer(Object.class, new ScoreCellRenderer());
        int[] widths = {26, 100, 66, 116, 48, 40, 40, 40};
        TableColumnModel columns = scoreTable.getColumnModel();
        for (int i = 0; i < widths.length; i++) {
            columns.getColumn(i).setPreferredWidth(widths[i]);
        }

        JLabel empty = new JLabel("No runs recorded yet - survive the first night to claim rank #1.",
                SwingConstants.CENTER);
        empty.setFont(Theme.font(Font.ITALIC, 13));
        empty.setForeground(Theme.MUTED);

        scoreCards = new CardLayout();
        scoreCardPanel = new JPanel(scoreCards);
        scoreCardPanel.setBackground(Theme.FIELD);
        scoreCardPanel.add(Theme.scroll(scoreTable), "table");
        scoreCardPanel.add(empty, "empty");
        return scoreCardPanel;
    }

    private JPanel buildHowToPanel() {
        howToArea = new JTextArea(buildHowToText());
        howToArea.setEditable(false);
        howToArea.setLineWrap(true);
        howToArea.setWrapStyleWord(true);
        howToArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));   // keeps the controls list aligned
        howToArea.setForeground(Theme.TEXT);
        howToArea.setBackground(Theme.FIELD);
        howToArea.setBorder(new EmptyBorder(8, 10, 8, 10));
        howToArea.setCaretPosition(0);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(Theme.scroll(howToArea), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildCrewAndStartPanel() {
        JPanel column = new JPanel(new BorderLayout(0, 10));
        column.setOpaque(false);

        Section crew = new Section("CREW", new GridLayout(GameData.GROUP_MEMBERS.length, 1, 0, 1));
        for (String member : GameData.GROUP_MEMBERS) {
            crew.add(buildMemberRow(member));
        }
        column.add(crew, BorderLayout.CENTER);

        startButton = new JButton("Start Survival");
        Theme.styleButton(startButton, true);
        startButton.setFont(Theme.font(Font.BOLD, 15));
        startButton.addActionListener(this);
        quitButton = new JButton("Quit");
        Theme.styleButton(quitButton, false);
        quitButton.addActionListener(this);

        JPanel buttons = new JPanel(new BorderLayout(8, 0));
        buttons.setOpaque(false);
        buttons.add(quitButton, BorderLayout.WEST);
        buttons.add(startButton, BorderLayout.CENTER);
        buttons.setPreferredSize(new Dimension(10, 40));
        column.add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(startButton);
        return column;
    }

    /** "Member1 Firstname Lastname   6XXXXXX1" -> name on the left, student ID on the right. */
    private JPanel buildMemberRow(String member) {
        String text = member.trim();
        int split = text.lastIndexOf(' ');
        String name = split > 0 ? text.substring(0, split).trim() : text;
        String id = split > 0 ? text.substring(split + 1) : "";
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        JLabel nameLabel = Theme.label(name, Font.PLAIN, 12, Theme.TEXT);
        nameLabel.setToolTipText(name);
        row.add(nameLabel, BorderLayout.CENTER);
        JLabel idLabel = new JLabel(id);
        idLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        idLabel.setForeground(Theme.ACCENT);
        row.add(idLabel, BorderLayout.EAST);
        return row;
    }

    // ================================================================== event handlers
    /** Every button, radio button, check box, combo box, the name field and the preview timer end up here. */
    @Override
    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();
        if (source == previewTimer) {
            survivorPreview.tick();
        } else if (source == startButton || source == nameField) {
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
            showOptionHint(soundBox);
        } else if (source == musicBox) {
            AssetLoader.setMusicEnabled(musicBox.isSelected());
            showOptionHint(musicBox);
        } else if (source == hitboxBox) {
            survivorPreview.setShowHitbox(hitboxBox.isSelected());
            showOptionHint(hitboxBox);
        } else if (source == autoFireBox) {
            showOptionHint(autoFireBox);
        } else if (source == doubleSpawnBox) {
            updateDifficultyReadout();       // the spawn numbers double
            showOptionHint(doubleSpawnBox);
        }
    }

    /** The two JSliders: volume is applied to the sound system live, difficulty updates its readout. */
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
        GameData.PlayerClass pc = getSelectedClass();
        classInfoArea.setText(pc.displayName.toUpperCase() + ": " + pc.description + "\n" + describeBonuses(pc));
        classInfoArea.setCaretPosition(0);
        refreshPreview();
    }

    /** The class's numbers from GameData, written out so the player sees exactly what changes. */
    private static String describeBonuses(GameData.PlayerClass pc) {
        List<String> parts = new ArrayList<>();
        if (pc.bonusMaxHp != 0) parts.add("+" + pc.bonusMaxHp + " max HP");
        if (pc.damageMult != 1.0) parts.add(percent(pc.damageMult - 1.0) + " damage");
        if (pc.speedMult != 1.0) parts.add(percent(pc.speedMult - 1.0) + " speed");
        if (pc.cooldownMult != 1.0) parts.add(percent(pc.cooldownMult - 1.0) + " reload time");
        if (pc.pickupMult != 1.0) parts.add(percent(pc.pickupMult - 1.0) + " pickup radius");
        if (pc.lootBonusPercent != 0) parts.add("+" + pc.lootBonusPercent + "% drop chance");
        if (pc.costMult != 1.0) parts.add(percent(pc.costMult - 1.0) + " craft cost");
        if (pc.bonusAmmo != 0) parts.add("+" + pc.bonusAmmo + " ammo");
        if (pc.startMedkits != 0) parts.add(pc.startMedkits + " medkits");
        if (pc.healMult != 1.0) parts.add(percent(pc.healMult - 1.0) + " healing");
        if (pc.startGrenades != 0) parts.add(pc.startGrenades + " grenade");
        if (pc.startScraps != 0) parts.add(pc.startScraps + " scraps");
        return "Bonus: " + String.join(", ", parts);
    }

    private static String percent(double fraction) {
        long p = Math.round(fraction * 100);
        return (p > 0 ? "+" : "") + p + "%";
    }

    private void showWeaponInfo() {
        GameData.WeaponType w = getSelectedWeapon();
        weaponIconLabel.setIcon(new ImageIcon(Theme.fit(AssetLoader.getWeaponIcon(w), 58, 58)));
        weaponStatsArea.setText(describeWeapon(w));
        weaponStatsArea.setCaretPosition(0);
        refreshPreview();
    }

    /** Weapon numbers from GameData.WeaponType: damage, fire rate, range, ammo and its special trait. */
    private static String describeWeapon(GameData.WeaponType w) {
        double shotsPerSecond = 1000.0 / w.cooldownMs;
        StringBuilder sb = new StringBuilder();
        sb.append(w.displayName).append("\n");
        sb.append("Damage ").append(w.damage);
        if (w.pellets > 1) {
            sb.append(" x").append(w.pellets);
        }
        sb.append("  |  ").append(String.format("%.1f", shotsPerSecond)).append(" hits/s\n");
        if (w.melee) {
            sb.append("Melee ").append(w.spreadDeg).append(" deg swing, reach ").append(w.range).append("\n");
        } else {
            sb.append("Range ").append(w.range).append("  |  ");
            sb.append(w.ammoCost == 0 ? "unlimited ammo" : w.ammoCost + " ammo/shot").append("\n");
        }
        if (w.melee) {
            sb.append("No ammo needed, hits all in the arc");
        } else if (w.pellets > 1) {
            sb.append(w.pellets).append(" pellets in a ").append(w.spreadDeg).append(" deg cone");
        } else if (w.pierce > 0) {
            sb.append("Pierces ").append(w.pierce).append(w.pierce == 1 ? " enemy" : " enemies");
        } else {
            sb.append(w.cooldownMs < 200 ? "Very fast automatic fire" : "Reliable sidearm");
        }
        return sb.toString();
    }

    private void showMapInfo() {
        GameData.MapType type = getSelectedMap();
        GameMap map = getMap(type);
        mapPreviewLabel.setIcon(new ImageIcon(buildMapThumbnail(map)));
        mapDescriptionArea.setText(type.displayName + "\n" + type.description);
        mapDescriptionArea.setCaretPosition(0);
        survivorPreview.setBackdrop(buildGroundPatch(map));
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

    /** Paints the whole map (ground + props) once and shrinks it to a thumbnail. */
    private static BufferedImage buildMapThumbnail(GameMap map) {
        int w = map.getWidthPx(), h = map.getHeightPx();
        BufferedImage world = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = world.createGraphics();
        map.drawGround(g, new Rectangle(0, 0, w, h));
        List<GameMap.Prop> props = new ArrayList<>(map.getProps());
        props.sort(GameMap.DEPTH_ORDER);
        for (GameMap.Prop p : props) {
            p.draw(g);
        }
        g.dispose();
        // halve a few times with smoothing: shrinking 12x in one step would drop most pixels
        BufferedImage img = world;
        while (img.getWidth() / 2 >= MAP_THUMB_W * 2) {
            img = Theme.resize(img, img.getWidth() / 2, img.getHeight() / 2, true);
        }
        return Theme.resize(img, MAP_THUMB_W, MAP_THUMB_H, true);
    }

    /** A small square of ground at the player's spawn point, used behind the animated survivor. */
    private static BufferedImage buildGroundPatch(GameMap map) {
        Point spawn = map.getPlayerSpawn();
        int size = SurvivorPreview.WORLD_SIZE;
        int x = Math.max(0, Math.min(map.getWidthPx() - size, spawn.x - size / 2));
        int y = Math.max(0, Math.min(map.getHeightPx() - size, spawn.y - size / 2));
        BufferedImage patch = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = patch.createGraphics();
        g.translate(-x, -y);
        map.drawGround(g, new Rectangle(x, y, size, size));
        g.dispose();
        return patch;
    }

    private void refreshPreview() {
        if (survivorPreview != null && weaponCombo != null) {
            survivorPreview.setLoadout(getSelectedClass(), getSelectedWeapon());
        }
    }

    private void showOptionHint(JCheckBox box) {
        if (box.isSelected()) {
            optionHintLabel.setText(box.getText() + " on - " + box.getToolTipText());
        } else {
            optionHintLabel.setText(box.getText() + " off");
        }
    }

    private void showVolume() {
        int v = volumeSlider.getValue();
        volumeValueLabel.setText(v == 0 ? "Muted" : v + "%");
    }

    /** Uses the same formulas as the game (GameData) so the numbers shown are the real ones. */
    private void updateDifficultyReadout() {
        int d = difficultySlider.getValue();
        boolean doubled = doubleSpawnBox.isSelected();
        difficultyNameLabel.setText(d + " - " + DIFFICULTY_NAMES[d - 1]);
        double hpScale = GameData.monsterHpScale(1, d);
        int firstWave = GameData.spawnBatchSize(1, d, doubled);
        int fifthWave = GameData.spawnBatchSize(5, d, doubled);
        int zombieHp = (int) Math.round(GameData.MonsterType.ZOMBIE_NORMAL.maxHp * hpScale);
        difficultyEffectArea.setText(String.format("Monster HP x%.2f (a zombie has %d HP)%n"
                + "Monsters per spawn: %d at wave 1, %d at wave 5", hpScale, zombieHp, firstWave, fifthWave));
    }

    /** Validates the name; on a problem it explains it under the field and puts the cursor back there. */
    private String validatePlayerName() {
        String name = nameField.getText().trim();
        String problem = null;
        if (name.isEmpty()) {
            problem = "Please type a name first.";
        } else if (name.length() > MAX_NAME_LENGTH) {
            problem = "Too long: " + name.length() + " of max " + MAX_NAME_LENGTH + " chars.";
        } else if (!name.matches(NAME_PATTERN)) {
            problem = "Only letters, digits, space and - _ . please.";
        }
        if (problem != null) {
            nameHintLabel.setText(problem);
            nameHintLabel.setForeground(Theme.BAD);
            nameField.setBorder(Theme.fieldBorder(Theme.BAD));
            nameField.requestFocusInWindow();
            nameField.selectAll();
            AssetLoader.playSound("hurt");
            return null;
        }
        nameHintLabel.setText(NAME_HINT);
        nameHintLabel.setForeground(Theme.MUTED);
        nameField.setBorder(Theme.fieldBorder(Theme.EDGE));
        nameField.setText(name);
        return name;
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
        previewTimer.stop();
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
        previewTimer.start();
        AssetLoader.playMusic("bgm");
    }

    /** Hides the launcher; the animation timer and the music stop with it. */
    private void hideLauncher() {
        previewTimer.stop();
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
        showLauncher();
    }

    private void reloadHighScores() {
        List<GameData.ScoreEntry> all = GameData.loadScores();
        scoreTableModel.setScores(all.subList(0, Math.min(TOP_SCORES, all.size())));
        scoreCards.show(scoreCardPanel, all.isEmpty() ? "empty" : "table");
        // a first-time player is better served by the instructions than by an empty table
        infoTabs.setSelectedIndex(all.isEmpty() ? 1 : 0);
    }

    private static String formatTime(int seconds) {
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }

    /** The how-to-play text. Numbers come from GameData so the help never disagrees with the game. */
    private static String buildHowToText() {
        StringBuilder sb = new StringBuilder();
        sb.append("GOAL\n");
        sb.append("Survive ").append(formatTime(GameData.TIME_LIMIT_SECONDS)).append(" in the arena. A new wave starts every ")
          .append(GameData.WAVE_SECONDS).append(" s and a Brute Boss arrives every ").append(GameData.BOSS_EVERY_WAVES)
          .append(" waves. If your HP reaches 0 the run is over. Kills, time and difficulty make your score.\n\n");
        sb.append("CONTROLS\n");
        sb.append("  W A S D ............ move\n");
        sb.append("  Mouse .............. aim (the gun follows the crosshair)\n");
        sb.append("  Hold left mouse .... fire (or tick Auto-Fire)\n");
        sb.append("  Right click or G ... throw a grenade\n");
        sb.append("  E .................. use a medkit (+").append(GameData.MEDKIT_HEAL).append(" HP)\n");
        sb.append("  1 - 8 / mouse wheel  switch weapon\n");
        sb.append("  B .................. open the workshop (game pauses)\n");
        sb.append("  P or Esc ........... pause\n\n");
        sb.append("LOOT -> CRAFT LOOP\n");
        sb.append("Monsters drop parts. Walk near them and they fly into your backpack. Press B to open the "
                + "workshop: pick a recipe in the table and press Craft. Weapons unlock in order Pistol -> Revolver -> "
                + "Shotgun -> Rifle -> Sniper -> Rocket Launcher (the last two need a Blueprint from a boss). "
                + "Stat upgrades raise damage, fire rate, max HP, speed, armour and loot magnet. Food also heals a "
                + "little when picked up.\n\n");
        sb.append("MONSTERS\n");
        for (GameData.MonsterType m : GameData.MonsterType.values()) {
            sb.append("  ").append(m.displayName).append(" - ").append(monsterNote(m)).append(" Drops: ")
              .append(dropNames(m)).append(".\n");
        }
        return sb.toString();
    }

    private static String monsterNote(GameData.MonsterType m) {
        switch (m) {
            case ZOMBIE_NORMAL: return "slow and steady, " + m.maxHp + " HP.";
            case ZOMBIE_RUNNER: return "fast but fragile, " + m.maxHp + " HP.";
            case ZOMBIE_TOXIC: return "spits poison from range, " + m.maxHp + " HP.";
            case BANDIT_SHOOTER: return "keeps its distance and shoots back, " + m.maxHp + " HP.";
            default: return "huge, " + m.maxHp + " HP, every " + GameData.BOSS_EVERY_WAVES + " waves.";
        }
    }

    private static String dropNames(GameData.MonsterType m) {
        List<String> names = new ArrayList<>();
        for (GameData.Drop d : m.getDrops()) {
            names.add(d.item.displayName);
        }
        return String.join(", ", names);
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
    /** The high-score JTable's data: top runs, read-only (AbstractTableModel cells are not editable). */
    private static final class ScoreTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private static final String[] COLUMNS = {"#", "Name", "Class", "Map", "Score", "Kills", "Wave", "Time"};
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
                case 2: return e.playerClass;
                case 3: return e.map;
                case 4: return e.score;
                case 5: return e.kills;
                case 6: return e.wave;
                default: return formatTime(e.seconds);
            }
        }
    }

    /** Gold, silver and bronze rank numbers; numbers right-aligned; alternating row shades. */
    private static final class ScoreCellRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;
        private static final Color[] MEDALS = {new Color(0xF2C14E), new Color(0xC9CED6), new Color(0xD08A4E)};

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, false, row, column);
            setBorder(new EmptyBorder(0, 6, 0, 6));
            setHorizontalAlignment(value instanceof Integer ? SwingConstants.RIGHT : SwingConstants.LEFT);
            setFont(Theme.font(column == 4 || column == 0 ? Font.BOLD : Font.PLAIN, 11));
            if (!selected) {
                setBackground(row % 2 == 0 ? Theme.FIELD : Theme.ROW_ALT);
                setForeground(column == 0 && row < MEDALS.length ? MEDALS[row] : Theme.TEXT);
            }
            if (column == 0) {
                setHorizontalAlignment(SwingConstants.CENTER);
            }
            return this;
        }
    }

    /** Weapon combo entries with their sprite. */
    private static final class WeaponListRenderer extends javax.swing.DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean selected, boolean focused) {
            super.getListCellRendererComponent(list, value, index, selected, focused);
            if (value instanceof GameData.WeaponType) {
                GameData.WeaponType w = (GameData.WeaponType) value;
                setText(w.displayName);
                setIcon(new ImageIcon(Theme.fit(AssetLoader.getWeaponIcon(w), 36, 20)));
            }
            setIconTextGap(8);
            setBorder(new EmptyBorder(0, 4, 0, 4));
            setFont(Theme.font(Font.BOLD, 12));
            if (!selected) {
                setBackground(Theme.FIELD);
                setForeground(Theme.TEXT);
            }
            return this;
        }
    }

    /**
     * Small animated picture of the survivor: a real Player object (same drawing code as the game)
     * standing on the selected map's ground, slowly aiming left and right with the chosen weapon.
     * The launcher's javax.swing.Timer calls tick(); it is stopped whenever the launcher is hidden.
     */
    private static final class SurvivorPreview extends JPanel {
        private static final long serialVersionUID = 1L;
        static final int WORLD_SIZE = 40;   // world pixels shown (drawn at 3x)
        private static final int ZOOM = 3;
        private transient Player player;
        private transient BufferedImage backdrop;
        private boolean showHitbox;
        private double aimPhase;

        SurvivorPreview() {
            setOpaque(false);
            setPreferredSize(new Dimension(WORLD_SIZE * ZOOM, WORLD_SIZE * ZOOM));
            setToolTipText("Your survivor with the chosen weapon");
        }

        void setLoadout(GameData.PlayerClass pc, GameData.WeaponType weapon) {
            GameData.GameSettings s = new GameData.GameSettings();
            s.playerClass = pc;
            s.startWeapon = weapon;
            player = new Player(s, 0, 0);
            aimAndUpdate();
            repaint();
        }

        void setBackdrop(BufferedImage image) {
            backdrop = image;
            repaint();
        }

        void setShowHitbox(boolean show) {
            showHitbox = show;
            repaint();
        }

        /** Two game ticks per timer tick (the timer runs at 32 ms, a game tick is 16 ms). */
        void tick() {
            aimPhase += 0.03;
            aimAndUpdate();
            aimAndUpdate();
            repaint();
        }

        private void aimAndUpdate() {
            if (player == null) {
                return;
            }
            // the aim point swings around the survivor, so the gun turns and the body flips left/right
            player.setAim(Math.cos(aimPhase) * 40, Math.sin(aimPhase * 2) * 12);
            player.update();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            int w = getWidth(), h = getHeight();
            if (backdrop != null) {
                g2.drawImage(backdrop, 0, 0, w, h, null);
            } else {
                g2.setColor(Theme.FIELD);
                g2.fillRect(0, 0, w, h);
            }
            g2.setPaint(new GradientPaint(0, 0, new Color(0, 0, 0, 0), 0, h, new Color(0, 0, 0, 90)));
            g2.fillRect(0, 0, w, h);
            if (player != null) {
                Graphics2D world = (Graphics2D) g2.create();
                world.translate(w / 2.0, h / 2.0 - 4);
                world.scale(ZOOM, ZOOM);
                player.draw(world);
                if (showHitbox) {
                    world.setStroke(new BasicStroke(2f / ZOOM));   // 2 screen pixels wide
                    player.drawHitbox(world);
                }
                world.dispose();
            }
            g2.setColor(Theme.EDGE);
            g2.drawRect(0, 0, w - 1, h - 1);
            g2.dispose();
        }
    }

    /** The window background: a dark vertical gradient. */
    static final class Backdrop extends JPanel {
        private static final long serialVersionUID = 1L;

        Backdrop(LayoutManager layout) {
            super(layout);
            setOpaque(true);
            setBackground(Theme.BG);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setPaint(new GradientPaint(0, 0, Theme.BG_TOP, 0, getHeight(), Theme.BG));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
        }
    }

    /** A titled group box: rounded dark panel, amber title, content placed below the title. */
    static final class Section extends JPanel {
        private static final long serialVersionUID = 1L;
        private String title;

        Section(String title, LayoutManager layout) {
            super(layout);
            this.title = title;
            setOpaque(false);
            setBorder(new EmptyBorder(32, 12, 10, 12));
        }

        void setTitle(String newTitle) {
            title = newTitle;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g2.setColor(Theme.PANEL);
            g2.fillRoundRect(0, 0, w - 1, h - 1, 10, 10);
            g2.setColor(Theme.EDGE);
            g2.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);
            g2.setColor(Theme.ACCENT);
            g2.fillRect(12, 11, 4, 12);
            g2.setFont(Theme.font(Font.BOLD, 13));
            g2.drawString(title, 22, 22);
            g2.setColor(Theme.EDGE);
            g2.drawLine(12, 28, w - 13, 28);
            g2.dispose();
        }
    }

    /**
     * Colours, fonts and small styling helpers shared by the launcher and the workshop dialog,
     * so both windows look like one game. Only logical fonts are used (they exist on every PC).
     */
    static final class Theme {
        static final Color BG = new Color(0x15, 0x13, 0x11);
        static final Color BG_TOP = new Color(0x2a, 0x22, 0x1a);
        static final Color PANEL = new Color(0x22, 0x1f, 0x1b);
        static final Color FIELD = new Color(0x14, 0x13, 0x11);
        static final Color ROW_ALT = new Color(0x1b, 0x19, 0x16);
        static final Color EDGE = new Color(0x4d, 0x43, 0x36);
        static final Color TEXT = new Color(0xec, 0xe2, 0xcc);
        static final Color MUTED = new Color(0xa8, 0x9d, 0x88);
        static final Color ACCENT = new Color(0xe8, 0x93, 0x34);
        static final Color ACCENT_DARK = new Color(0x8f, 0x50, 0x1a);
        static final Color SELECT = new Color(0x5e, 0x40, 0x1f);
        static final Color GOOD = new Color(0x93, 0xcf, 0x5f);
        static final Color BAD = new Color(0xec, 0x67, 0x52);
        static final Color LOCKED = new Color(0xd8, 0xb0, 0x4c);

        private Theme() {
        }

        static Font font(int style, int size) {
            return new Font(Font.SANS_SERIF, style, size);
        }

        /** Cross-platform (Metal) look and feel with dark colours, so it looks the same on macOS and Windows. */
        static void installLookAndFeel() {
            MetalLookAndFeel.setCurrentTheme(new WastelandMetalTheme());
            try {
                UIManager.setLookAndFeel(new MetalLookAndFeel());
            } catch (UnsupportedLookAndFeelException ex) {
                System.err.println("Metal look and feel not available: " + ex.getMessage());
            }
            UIManager.put("TabbedPane.selected", PANEL);
            UIManager.put("TabbedPane.contentAreaColor", PANEL);
            UIManager.put("TabbedPane.unselectedBackground", FIELD);
            UIManager.put("TabbedPane.tabAreaBackground", BG);
            UIManager.put("TabbedPane.borderHightlightColor", EDGE);
            UIManager.put("TabbedPane.darkShadow", EDGE);
            UIManager.put("TabbedPane.light", EDGE);
            UIManager.put("TabbedPane.highlight", EDGE);
            UIManager.put("TabbedPane.shadow", EDGE);
            UIManager.put("TabbedPane.focus", ACCENT);
            UIManager.put("TabbedPane.selectHighlight", ACCENT);
            UIManager.put("ToolTip.background", new ColorUIResource(0x2e, 0x29, 0x22));
            UIManager.put("ToolTip.foreground", new ColorUIResource(TEXT));
            UIManager.put("ToolTip.border", BorderFactory.createLineBorder(ACCENT_DARK));
            UIManager.put("ComboBox.selectionBackground", new ColorUIResource(SELECT));
            UIManager.put("ComboBox.selectionForeground", new ColorUIResource(TEXT));
        }

        static JLabel label(String text, int style, int size, Color color) {
            JLabel label = new JLabel(text);
            label.setFont(font(style, size));
            label.setForeground(color);
            return label;
        }

        /** Read-only wrapped text that blends into its section (used for descriptions and readouts). */
        static JTextArea infoArea(int rows) {
            JTextArea area = new JTextArea(rows, 10);
            area.setEditable(false);
            area.setFocusable(false);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            area.setOpaque(false);
            area.setFont(font(Font.PLAIN, 11));
            area.setForeground(MUTED);
            area.setBorder(null);
            return area;
        }

        static Border fieldBorder(Color line) {
            return BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(line),
                    new EmptyBorder(3, 6, 3, 6));
        }

        static void styleTextField(JTextField field) {
            field.setFont(font(Font.BOLD, 13));
            field.setForeground(TEXT);
            field.setBackground(FIELD);
            field.setCaretColor(ACCENT);
            field.setSelectionColor(SELECT);
            field.setSelectedTextColor(TEXT);
            field.setBorder(fieldBorder(EDGE));
        }

        static void styleToggle(javax.swing.AbstractButton toggle) {
            toggle.setOpaque(false);
            toggle.setFont(font(Font.PLAIN, 12));
            toggle.setForeground(TEXT);
            toggle.setFocusPainted(false);
        }

        static void styleButton(JButton button, boolean primary) {
            button.setFont(font(Font.BOLD, 13));
            button.setFocusPainted(false);
            button.setBackground(primary ? ACCENT : PANEL);
            button.setForeground(primary ? new Color(0x1d, 0x12, 0x06) : TEXT);
            button.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(primary ? ACCENT_DARK : EDGE), new EmptyBorder(6, 14, 6, 14)));
        }

        /** Metal keeps a coloured background on disabled buttons, so a disabled main button is greyed out here. */
        static void setPrimaryEnabled(JButton button, boolean enabled) {
            button.setEnabled(enabled);
            button.setBackground(enabled ? ACCENT : PANEL);
        }

        static void styleCombo(JComboBox<?> combo) {
            combo.setFont(font(Font.BOLD, 12));
            combo.setForeground(TEXT);
            combo.setBackground(FIELD);
            combo.setFocusable(false);
            combo.setMaximumRowCount(8);
        }

        static void styleTable(JTable table) {
            table.setFont(font(Font.PLAIN, 12));
            table.setRowHeight(20);
            table.setBackground(FIELD);
            table.setForeground(TEXT);
            table.setGridColor(PANEL);
            table.setShowGrid(false);
            table.setIntercellSpacing(new Dimension(0, 0));
            table.setSelectionBackground(SELECT);
            table.setSelectionForeground(TEXT);
            table.setFillsViewportHeight(true);
            JTableHeader header = table.getTableHeader();
            header.setReorderingAllowed(false);
            header.setResizingAllowed(false);
            header.setDefaultRenderer(new HeaderRenderer());
        }

        static JScrollPane scroll(JComponent view) {
            JScrollPane pane = new JScrollPane(view);
            pane.setBorder(BorderFactory.createLineBorder(EDGE));
            pane.getViewport().setBackground(FIELD);
            return pane;
        }

        /** Nearest-neighbour upscale: keeps pixel art sharp. */
        static BufferedImage scale(BufferedImage src, int factor) {
            return resize(src, src.getWidth() * factor, src.getHeight() * factor, false);
        }

        /** Cuts away the transparent margin of a sprite, then scales it by the largest whole factor that fits w x h. */
        static BufferedImage fit(BufferedImage src, int w, int h) {
            int minX = src.getWidth(), minY = src.getHeight(), maxX = -1, maxY = -1;
            for (int y = 0; y < src.getHeight(); y++) {
                for (int x = 0; x < src.getWidth(); x++) {
                    if ((src.getRGB(x, y) >>> 24) != 0) {
                        minX = Math.min(minX, x);
                        minY = Math.min(minY, y);
                        maxX = Math.max(maxX, x);
                        maxY = Math.max(maxY, y);
                    }
                }
            }
            if (maxX < 0) {
                return src;   // fully transparent: nothing to trim
            }
            BufferedImage trimmed = src.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
            int factor = Math.max(1, Math.min(w / trimmed.getWidth(), h / trimmed.getHeight()));
            return scale(trimmed, factor);
        }

        static BufferedImage resize(BufferedImage src, int w, int h, boolean smooth) {
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, smooth
                    ? RenderingHints.VALUE_INTERPOLATION_BILINEAR : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(src, 0, 0, w, h, null);
            g.dispose();
            return out;
        }
    }

    /** Table headers: dark bar with amber bold text. */
    static final class HeaderRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, false, false, row, column);
            setFont(Theme.font(Font.BOLD, 12));
            setForeground(Theme.ACCENT);
            setBackground(Theme.PANEL);
            setHorizontalAlignment(SwingConstants.LEFT);
            setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.EDGE),
                    new EmptyBorder(3, 6, 3, 6)));
            return this;
        }
    }

    /** Metal colours swapped for the game's dark palette (backgrounds, text, selection, scroll bars). */
    private static final class WastelandMetalTheme extends DefaultMetalTheme {
        private static final ColorUIResource PRIMARY1 = new ColorUIResource(Theme.ACCENT);
        private static final ColorUIResource PRIMARY2 = new ColorUIResource(Theme.ACCENT_DARK);
        private static final ColorUIResource PRIMARY3 = new ColorUIResource(Theme.SELECT);
        private static final ColorUIResource SECONDARY1 = new ColorUIResource(0x6e, 0x60, 0x4c);
        private static final ColorUIResource SECONDARY2 = new ColorUIResource(Theme.EDGE);
        private static final ColorUIResource SECONDARY3 = new ColorUIResource(Theme.PANEL);
        private static final ColorUIResource TEXT = new ColorUIResource(Theme.TEXT);
        private static final ColorUIResource DARK = new ColorUIResource(Theme.FIELD);
        private static final FontUIResource CONTROL_FONT = new FontUIResource(Font.SANS_SERIF, Font.PLAIN, 12);

        @Override
        public String getName() {
            return "Wasteland";
        }

        @Override
        protected ColorUIResource getPrimary1() {
            return PRIMARY1;
        }

        @Override
        protected ColorUIResource getPrimary2() {
            return PRIMARY2;
        }

        @Override
        protected ColorUIResource getPrimary3() {
            return PRIMARY3;
        }

        @Override
        protected ColorUIResource getSecondary1() {
            return SECONDARY1;
        }

        @Override
        protected ColorUIResource getSecondary2() {
            return SECONDARY2;
        }

        @Override
        protected ColorUIResource getSecondary3() {
            return SECONDARY3;
        }

        @Override
        protected ColorUIResource getBlack() {
            return TEXT;      // Metal draws text in "black": make it light
        }

        @Override
        protected ColorUIResource getWhite() {
            return DARK;      // and fills text fields/lists with "white": make it dark
        }

        @Override
        public FontUIResource getControlTextFont() {
            return CONTROL_FONT;
        }

        @Override
        public FontUIResource getSystemTextFont() {
            return CONTROL_FONT;
        }

        @Override
        public FontUIResource getUserTextFont() {
            return CONTROL_FONT;
        }

        @Override
        public FontUIResource getMenuTextFont() {
            return CONTROL_FONT;
        }
    }
}
