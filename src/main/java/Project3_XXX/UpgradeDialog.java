// Member1 Firstname Lastname   6XXXXXX1
// Member2 Firstname Lastname   6XXXXXX2
// Member3 Firstname Lastname   6XXXXXX3
// Member4 Firstname Lastname   6XXXXXX4
// Member5 Firstname Lastname   6XXXXXX5
package Project3_XXX;

import java.awt.BorderLayout;
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
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.AbstractAction;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;

/**
 * The workshop: a modal JDialog opened from the game (B key or the HUD button) while the game is
 * paused. A JTable lists every recipe of GameData.RECIPES with its real cost for this player; the
 * player selects a row and crafts it with the items in the backpack.
 *
 * Event handlers:
 *   actionPerformed(ActionEvent)   - Craft and Close buttons
 *   valueChanged(ListSelectionEvent) - a new table row is selected: show its details
 *   mouseClicked(MouseEvent)       - double-click on a row crafts it (RowClickHandler)
 *   key bindings                   - Enter crafts, Esc or B closes (KeyCommand)
 */
public final class UpgradeDialog extends JDialog implements ActionListener, ListSelectionListener {

    private static final long serialVersionUID = 1L;
    private static final int ICON_ZOOM = 2;                 // 16x16 item icons drawn at 32x32
    private static final long B_KEY_GRACE_MS = 400;         // see KeyCommand: ignore the B press that opened us
    private static final int ROW_HEIGHT = 18;
    private static final int BOTTOM_ROW_HEIGHT = 160;
    private static final EmptyBorder SECTION_PADDING = new EmptyBorder(31, 10, 8, 10);

    private enum RowState { READY, MISSING, LOCKED, DONE }

    // the game objects are not Serializable; Swing dialogs never get serialized here
    private final transient Player player;
    private final transient Map<GameData.ItemType, ItemSlot> itemSlots = new EnumMap<>(GameData.ItemType.class);
    private final long openedAt = System.currentTimeMillis();

    private RecipeTableModel recipeModel;
    private JTable recipeTable;
    private MainApplication.Section detailSection;   // its title shows the selected recipe
    private JTextArea detailCostArea;
    private JLabel detailReasonLabel;
    private JButton craftButton;
    private JButton closeButton;
    private JTextArea craftLogArea;

    public UpgradeDialog(JFrame owner, Player player) {
        super(owner, "Workshop - " + player.getPlayerName(), true);
        this.player = player;
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        MainApplication.Backdrop root = new MainApplication.Backdrop(new GridBagLayout());
        root.setBorder(new EmptyBorder(12, 14, 14, 14));
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.gridx = 0;
        c.gridwidth = 3;
        c.weightx = 1;

        c.gridy = 0;
        c.insets = new Insets(0, 0, 10, 0);
        root.add(buildHeaderPanel(), c);
        c.gridy = 1;
        c.weighty = 1;
        root.add(buildRecipeTablePanel(), c);

        // bottom row: fixed base widths so the three boxes keep their shape; the log takes any extra width
        c.gridy = 2;
        c.gridwidth = 1;
        c.weighty = 0;
        c.weightx = 0;
        c.insets = new Insets(0, 0, 0, 10);
        root.add(fixSize(buildBackpackPanel(), 290), c);
        c.gridx = 1;
        root.add(fixSize(buildDetailPanel(), 222), c);
        c.gridx = 2;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 0, 0);
        root.add(fixSize(buildLogPanel(), 190), c);
        setContentPane(root);

        installKeyBindings();
        selectFirstUsefulRow();
        refreshTable();
        log("Workshop open - the game is paused.");
        log("Double-click or Enter crafts. Esc or B closes.");

        setSize(780, 520);
        setMinimumSize(new Dimension(760, 500));
        setLocationRelativeTo(owner);
    }

    // ================================================================== layout
    private static JComponent fixSize(JComponent panel, int width) {
        Dimension size = new Dimension(width, BOTTOM_ROW_HEIGHT);
        panel.setPreferredSize(size);
        panel.setMinimumSize(size);
        return panel;
    }

    private JPanel buildHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);
        JPanel titles = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        titles.setOpaque(false);
        titles.add(MainApplication.Theme.label("WORKSHOP   ", Font.BOLD, 22, MainApplication.Theme.ACCENT));
        titles.add(MainApplication.Theme.label(player.getPlayerName() + " the " + player.getPlayerClass().displayName
                + "  -  game paused, turn your loot into gear", Font.PLAIN, 12, MainApplication.Theme.MUTED));
        JPanel titleHolder = new JPanel(new GridBagLayout());   // centres the titles vertically
        titleHolder.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.weightx = 1;
        c.anchor = GridBagConstraints.WEST;
        titleHolder.add(titles, c);
        header.add(titleHolder, BorderLayout.CENTER);

        closeButton = new JButton("Close (Esc)");
        MainApplication.Theme.styleButton(closeButton, false);
        closeButton.addActionListener(this);
        JPanel holder = new JPanel(new GridBagLayout());   // keeps the button its natural height
        holder.setOpaque(false);
        holder.add(closeButton);
        header.add(holder, BorderLayout.EAST);
        return header;
    }

    private JComponent buildRecipeTablePanel() {
        recipeModel = new RecipeTableModel();
        recipeTable = new JTable(recipeModel);
        MainApplication.Theme.styleTable(recipeTable);
        recipeTable.setRowHeight(ROW_HEIGHT);
        recipeTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        recipeTable.setDefaultRenderer(Object.class, new RecipeCellRenderer());
        recipeTable.getColumnModel().getColumn(2).setCellRenderer(new CostCellRenderer());
        int[] widths = {166, 258, 134, 140};
        TableColumnModel columns = recipeTable.getColumnModel();
        for (int i = 0; i < widths.length; i++) {
            columns.getColumn(i).setPreferredWidth(widths[i]);
        }
        recipeTable.getSelectionModel().addListSelectionListener(this);
        recipeTable.addMouseListener(new RowClickHandler());
        return MainApplication.Theme.scroll(recipeTable);
    }

    /** Every item that can be stored, as game-style slots (icon + count). Hover a slot for the item name. */
    private JPanel buildBackpackPanel() {
        JPanel grid = new JPanel(new GridLayout(2, 6, 4, 4));
        grid.setOpaque(false);
        for (GameData.ItemType type : GameData.ItemType.values()) {
            if (type != GameData.ItemType.CHEST) {
                ItemSlot slot = new ItemSlot(type);
                itemSlots.put(type, slot);
                grid.add(slot);
            }
        }
        MainApplication.Section section = new MainApplication.Section("BACKPACK", new BorderLayout(0, 4));
        section.setBorder(SECTION_PADDING);
        section.add(grid, BorderLayout.NORTH);
        section.add(MainApplication.Theme.label("Green / red frame = needed by the selected recipe", Font.ITALIC, 10,
                MainApplication.Theme.MUTED), BorderLayout.SOUTH);
        return section;
    }

    private JPanel buildDetailPanel() {
        detailSection = new MainApplication.Section("SELECTED", new BorderLayout(0, 3));
        MainApplication.Section section = detailSection;
        section.setBorder(SECTION_PADDING);

        detailCostArea = new JTextArea(4, 20);
        detailCostArea.setEditable(false);
        detailCostArea.setFocusable(false);
        detailCostArea.setOpaque(false);
        detailCostArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        detailCostArea.setForeground(MainApplication.Theme.TEXT);
        section.add(detailCostArea, BorderLayout.CENTER);

        detailReasonLabel = MainApplication.Theme.label(" ", Font.BOLD, 11, MainApplication.Theme.MUTED);
        craftButton = new JButton("Craft / Upgrade");
        MainApplication.Theme.styleButton(craftButton, true);
        craftButton.addActionListener(this);
        JPanel bottom = new JPanel(new BorderLayout(0, 3));
        bottom.setOpaque(false);
        bottom.add(detailReasonLabel, BorderLayout.NORTH);
        bottom.add(craftButton, BorderLayout.SOUTH);
        section.add(bottom, BorderLayout.SOUTH);
        return section;
    }

    private JPanel buildLogPanel() {
        craftLogArea = new JTextArea(6, 18);
        craftLogArea.setEditable(false);
        craftLogArea.setLineWrap(true);
        craftLogArea.setWrapStyleWord(true);
        craftLogArea.setFont(MainApplication.Theme.font(Font.PLAIN, 11));
        craftLogArea.setForeground(MainApplication.Theme.TEXT);
        craftLogArea.setBackground(MainApplication.Theme.FIELD);
        craftLogArea.setBorder(new EmptyBorder(4, 6, 4, 6));
        MainApplication.Section section = new MainApplication.Section("CRAFTING LOG", new BorderLayout());
        section.setBorder(SECTION_PADDING);
        section.add(MainApplication.Theme.scroll(craftLogArea), BorderLayout.CENTER);
        return section;
    }

    /** Enter crafts the selected row; Esc and B close. Bound on the dialog so they work wherever the focus is. */
    private void installKeyBindings() {
        InputMap keys = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_B, 0), "closeB");
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "craft");
        getRootPane().getActionMap().put("close", new KeyCommand("close"));
        getRootPane().getActionMap().put("closeB", new KeyCommand("closeB"));
        getRootPane().getActionMap().put("craft", new KeyCommand("craft"));
        // JTable uses Enter to move to the next row; here it should craft instead
        recipeTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "craft");
        recipeTable.getActionMap().put("craft", new KeyCommand("craft"));
    }

    // ================================================================== event handlers
    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == craftButton) {
            craftSelectedUpgrade();
        } else if (e.getSource() == closeButton) {
            closeWorkshop();
        }
    }

    /** A different row was selected: show what it costs and whether it can be crafted. */
    @Override
    public void valueChanged(ListSelectionEvent e) {
        if (!e.getValueIsAdjusting()) {
            showSelectedRecipe();
        }
    }

    // ================================================================== public API
    /** Re-reads the player's inventory and levels into the table, backpack and detail panel; keeps the selection. */
    public void refreshTable() {
        int selected = recipeTable.getSelectedRow();
        recipeModel.fireTableRowsUpdated(0, recipeModel.getRowCount() - 1);
        if (selected >= 0 && recipeTable.getSelectedRow() != selected) {
            recipeTable.setRowSelectionInterval(selected, selected);
        }
        showSelectedRecipe();   // also refreshes the backpack slots
    }

    /** Crafts the selected recipe through Player.craft and writes the result (or the reason it failed) to the log. */
    public void craftSelectedUpgrade() {
        GameData.UpgradeRecipe recipe = getSelectedRecipe();
        if (recipe == null) {
            log("Select a recipe in the table first.");
            return;
        }
        String result = player.craft(recipe);
        if (result != null) {
            log(result);
        } else {
            log(recipe.name + " failed: " + explainWhyNot(recipe) + ".");
        }
        refreshTable();
    }

    // ================================================================== helpers
    private GameData.UpgradeRecipe getSelectedRecipe() {
        int row = recipeTable.getSelectedRow();
        return row >= 0 ? GameData.RECIPES.get(row) : null;
    }

    /** Selects the first recipe the player can afford, otherwise the first row. */
    private void selectFirstUsefulRow() {
        int row = 0;
        for (int i = 0; i < GameData.RECIPES.size(); i++) {
            if (player.canCraft(GameData.RECIPES.get(i))) {
                row = i;
                break;
            }
        }
        recipeTable.setRowSelectionInterval(row, row);
        recipeTable.scrollRectToVisible(recipeTable.getCellRect(row, 0, true));
    }

    private RowState stateOf(GameData.UpgradeRecipe r) {
        if (player.canCraft(r)) {
            return RowState.READY;
        }
        String status = player.getRecipeStatus(r);
        if (status.equals("Owned") || status.equals("MAX")) {
            return RowState.DONE;
        }
        if (r.requiredWeapon != null && !player.ownsWeapon(r.requiredWeapon)) {
            return RowState.LOCKED;
        }
        return RowState.MISSING;
    }

    private String explainWhyNot(GameData.UpgradeRecipe r) {
        String status = player.getRecipeStatus(r);
        if (status.equals("Owned")) {
            return "you already own it";
        }
        if (status.equals("MAX")) {
            return "already at the maximum level";
        }
        if (r.requiredWeapon != null && !player.ownsWeapon(r.requiredWeapon)) {
            return "craft the " + r.requiredWeapon.displayName + " first";
        }
        List<String> missing = new ArrayList<>();
        for (Map.Entry<GameData.ItemType, Integer> e : player.getCost(r).entrySet()) {
            int lacking = e.getValue() - player.getItemCount(e.getKey());
            if (lacking > 0) {
                missing.add(lacking + " more " + e.getKey().displayName);
            }
        }
        return missing.isEmpty() ? "not possible right now" : "need " + String.join(", ", missing);
    }

    /** Fills the detail panel: one have/need line per cost item, the reason, and enables the craft button. */
    private void showSelectedRecipe() {
        GameData.UpgradeRecipe r = getSelectedRecipe();
        Map<GameData.ItemType, Integer> cost = r == null ? new EnumMap<>(GameData.ItemType.class) : player.getCost(r);
        updateBackpack(cost);
        if (r == null) {
            detailSection.setTitle("NO RECIPE SELECTED");
            detailCostArea.setText("");
            detailReasonLabel.setText(" ");
            MainApplication.Theme.setPrimaryEnabled(craftButton, false);
            return;
        }
        detailSection.setTitle(r.name.toUpperCase());
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<GameData.ItemType, Integer> e : cost.entrySet()) {
            int have = player.getItemCount(e.getKey());
            int need = e.getValue();
            sb.append(String.format("%-12s%4d /%3d  %s%n", e.getKey().displayName, have, need, have >= need ? "ok" : "short"));
        }
        detailCostArea.setText(sb.toString().trim());
        RowState state = stateOf(r);
        if (state == RowState.READY) {
            detailReasonLabel.setText("Ready to craft");
            detailReasonLabel.setForeground(MainApplication.Theme.GOOD);
        } else {
            detailReasonLabel.setText(capitalize(explainWhyNot(r)));
            detailReasonLabel.setForeground(state == RowState.DONE ? MainApplication.Theme.MUTED : MainApplication.Theme.BAD);
        }
        MainApplication.Theme.setPrimaryEnabled(craftButton, player.canCraft(r));
    }

    /** New counts in every slot; slots used by the selected recipe get a green (enough) or red (short) frame. */
    private void updateBackpack(Map<GameData.ItemType, Integer> cost) {
        for (Map.Entry<GameData.ItemType, ItemSlot> e : itemSlots.entrySet()) {
            int count = player.getItemCount(e.getKey());
            Integer need = cost.get(e.getKey());
            Color frame = null;
            if (need != null) {
                frame = count >= need ? MainApplication.Theme.GOOD : MainApplication.Theme.BAD;
            }
            e.getValue().show(count, frame);
        }
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Appends one line to the crafting log and scrolls it into view. */
    private void log(String line) {
        if (craftLogArea.getDocument().getLength() > 0) {
            craftLogArea.append("\n");
        }
        craftLogArea.append("> " + line);
        craftLogArea.setCaretPosition(craftLogArea.getDocument().getLength());
    }

    private void closeWorkshop() {
        AssetLoader.playSound("click");
        dispose();
    }

    private static Color statusColor(RowState state) {
        switch (state) {
            case READY: return MainApplication.Theme.GOOD;
            case LOCKED: return MainApplication.Theme.LOCKED;
            case DONE: return MainApplication.Theme.MUTED;
            default: return MainApplication.Theme.BAD;
        }
    }

    // ================================================================== inner classes
    /** The recipe JTable's data: one row per GameData.RECIPES entry, values computed from the player now. */
    private final class RecipeTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private final String[] columns = {"Upgrade", "Effect", "Cost", "Status"};

        @Override
        public int getRowCount() {
            return GameData.RECIPES.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            GameData.UpgradeRecipe r = GameData.RECIPES.get(row);
            switch (column) {
                case 0: return r.name;
                case 1: return r.description;
                case 2: return player.getCost(r);
                default: return player.getRecipeStatus(r);
            }
        }
    }

    /**
     * Colours each row by what the player can do with it: green status = ready, red = missing items,
     * yellow = needs another weapon first, grey = owned or at max level.
     */
    private final class RecipeCellRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, false, row, column);
            RowState state = stateOf(GameData.RECIPES.get(row));
            setBorder(new EmptyBorder(0, 6, 0, 6));
            setToolTipText(String.valueOf(value));
            setFont(MainApplication.Theme.font(column == 0 || column == 3 ? Font.BOLD : Font.PLAIN, column == 1 ? 11 : 12));
            if (!selected) {
                setBackground(state == RowState.READY ? new Color(0x1d, 0x26, 0x17)
                        : row % 2 == 0 ? MainApplication.Theme.FIELD : MainApplication.Theme.ROW_ALT);
            }
            if (column == 3) {
                setText("\u25CF " + value);   // a filled circle
                setForeground(statusColor(state));
            } else if (state == RowState.DONE) {
                setForeground(MainApplication.Theme.MUTED);
            } else {
                setForeground(column == 1 ? new Color(0xc9, 0xbf, 0xaa) : MainApplication.Theme.TEXT);
            }
            return this;
        }
    }

    /** Cost cell: each item as its icon and the amount; red when the backpack holds too few. */
    private final class CostCellRenderer extends JComponent implements TableCellRenderer {
        private static final long serialVersionUID = 1L;
        private transient Map<GameData.ItemType, Integer> cost;
        private Color background;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            cost = player.getCost(GameData.RECIPES.get(row));
            boolean ready = stateOf(GameData.RECIPES.get(row)) == RowState.READY;
            background = selected ? table.getSelectionBackground()
                    : ready ? new Color(0x1d, 0x26, 0x17)
                    : row % 2 == 0 ? MainApplication.Theme.FIELD : MainApplication.Theme.ROW_ALT;
            setToolTipText(GameData.UpgradeRecipe.costToText(cost));
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(background);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(MainApplication.Theme.font(Font.BOLD, 12));
            int x = 6;
            int iconY = (getHeight() - 16) / 2;
            int textY = (getHeight() + g2.getFontMetrics().getAscent()) / 2 - 1;
            if (cost != null) {
                for (Map.Entry<GameData.ItemType, Integer> e : cost.entrySet()) {
                    BufferedImage icon = AssetLoader.getItemIcon(e.getKey());
                    g2.drawImage(icon, x, iconY, null);
                    x += 17;
                    boolean enough = player.getItemCount(e.getKey()) >= e.getValue();
                    g2.setColor(enough ? MainApplication.Theme.TEXT : MainApplication.Theme.BAD);
                    String amount = String.valueOf(e.getValue());
                    g2.drawString(amount, x, textY);
                    x += g2.getFontMetrics().stringWidth(amount) + 8;
                }
            }
            g2.dispose();
        }
    }

    /** One backpack slot: the item icon at 2x with its count in the corner, optionally framed in a colour. */
    private static final class ItemSlot extends JComponent {
        private static final long serialVersionUID = 1L;
        private static final int SIZE = 42;
        private final transient BufferedImage icon;
        private final String itemName;
        private int count;
        private Color frame;

        ItemSlot(GameData.ItemType type) {
            icon = MainApplication.Theme.scale(AssetLoader.getItemIcon(type), ICON_ZOOM);
            itemName = type.displayName;
            setPreferredSize(new Dimension(SIZE, SIZE));
            setToolTipText(type.displayName);
        }

        void show(int newCount, Color newFrame) {
            count = newCount;
            frame = newFrame;
            setToolTipText(itemName + " x" + count);
            repaint();
        }

        int getCount() {
            return count;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            int w = getWidth(), h = getHeight();
            g2.setColor(MainApplication.Theme.FIELD);
            g2.fillRect(0, 0, w, h);
            g2.setColor(frame != null ? frame : MainApplication.Theme.EDGE);
            g2.drawRect(0, 0, w - 1, h - 1);
            if (frame != null) {
                g2.drawRect(1, 1, w - 3, h - 3);
            }
            if (count == 0) {
                // empty slots are drawn faded so the useful items stand out
                g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.3f));
            }
            g2.drawImage(icon, (w - icon.getWidth()) / 2, (h - icon.getHeight()) / 2 - 3, null);
            g2.setComposite(java.awt.AlphaComposite.SrcOver);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(MainApplication.Theme.font(Font.BOLD, 11));
            String text = String.valueOf(count);
            int tx = w - 4 - g2.getFontMetrics().stringWidth(text);
            g2.setColor(Color.BLACK);
            g2.drawString(text, tx + 1, h - 3);
            g2.setColor(count > 0 ? MainApplication.Theme.TEXT : MainApplication.Theme.MUTED);
            g2.drawString(text, tx, h - 4);
            g2.dispose();
        }
    }

    /** Double-click on a recipe row crafts it. */
    private final class RowClickHandler extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2 && recipeTable.rowAtPoint(e.getPoint()) >= 0) {
                craftSelectedUpgrade();
            }
        }
    }

    /** Keyboard shortcuts: "craft" (Enter), "close" (Esc) and "closeB" (B). */
    private final class KeyCommand extends AbstractAction {
        private static final long serialVersionUID = 1L;
        private final String command;

        KeyCommand(String command) {
            this.command = command;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (command.equals("craft")) {
                craftSelectedUpgrade();
            } else if (command.equals("close")) {
                closeWorkshop();
            } else if (System.currentTimeMillis() - openedAt > B_KEY_GRACE_MS) {
                // B toggles the workshop, but the key repeat of the press that opened it must not close it again
                closeWorkshop();
            }
        }
    }
}
