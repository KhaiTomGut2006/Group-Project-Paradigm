// Tapatsawin Chaieua          6813105
// Thanapon Thanadunpremdet    6813106
// Tanawat Wongwad             6813107
// Natthanon Achachan          6813241
package Project3_6813107;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
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
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

/**
 * The workshop: a modal JDialog opened from the game (B key or the WORKSHOP button) while the game
 * is paused, drawn with the Hollow Creek UI kit. Top: the camp trader speaks from the kit's dialog
 * box. Left: a JTable with every recipe of GameData.RECIPES, its real cost for this player and its
 * status. Right: the selected recipe on a paper note with the CRAFT button. Bottom: the backpack.
 *
 * Event handlers:
 *   actionPerformed(ActionEvent)     - CRAFT and BACK buttons
 *   valueChanged(ListSelectionEvent) - a new table row is selected: show its details
 *   mouseClicked(MouseEvent)         - double-click on a row crafts it (RowClickHandler)
 *   key bindings                     - Enter crafts, Esc or B closes (KeyCommand)
 */
public final class UpgradeDialog extends JDialog implements ActionListener, ListSelectionListener {

    private static final long serialVersionUID = 1L;
    private static final long B_KEY_GRACE_MS = 400;   // see KeyCommand: ignore the B press that opened us
    private static final int ROW_HEIGHT = 28;
    private static final int DETAIL_WIDTH = 290;
    private static final String GREETING = "Welcome back to camp! Craft guns, heal wounds, or build base defenses.";

    private enum RowState { READY, MISSING, LOCKED, DONE }

    // the game objects are not Serializable; Swing dialogs never get serialized here
    private final transient Player player;
    private final transient Map<GameData.ItemType, Theme.Slot> itemSlots = new EnumMap<>(GameData.ItemType.class);
    private final long openedAt = System.currentTimeMillis();

    private Theme.SpeechBox trader;
    private RecipeTableModel recipeModel;
    private JTable recipeTable;
    private Theme.PixelLabel detailKindLabel;
    private Theme.PixelLabel detailLevelLabel;
    private JLabel detailIconLabel;
    private Theme.PixelLabel detailTitleLabel;
    private JTextArea detailDescriptionArea;
    private JPanel costList;
    private JTextArea feedbackArea;
    private JButton craftButton;
    private JButton closeButton;

    public UpgradeDialog(JFrame owner, Player player) {
        super(owner, "Workshop", true);
        this.player = player;
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(Theme.BG);
        root.setBorder(new EmptyBorder(18, 22, 18, 22));
        root.add(buildHeader(), BorderLayout.NORTH);
        JPanel body = Theme.transparentPanel(new BorderLayout(14, 0));
        body.add(buildRecipeTable(), BorderLayout.CENTER);
        body.add(buildDetailCard(), BorderLayout.EAST);
        root.add(body, BorderLayout.CENTER);
        root.add(buildBackpack(), BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().setCursor(Theme.arrowCursor());

        installKeyBindings();
        selectFirstUsefulRow();
        refreshTable();

        setSize(860, 660);
        setMinimumSize(new Dimension(840, 640));
        setLocationRelativeTo(owner);
    }

    // ================================================================== layout
    /** The trader in the kit's dialog box on the left; the WORKSHOP title and BACK button on the right. */
    private JPanel buildHeader() {
        trader = new Theme.SpeechBox("portrait_other", GREETING);
        Theme.PixelLabel title = new Theme.PixelLabel("BASE CAMP NPCs", 5, Theme.GOLD);
        title.setShadow(true);
        title.setAlignment(javax.swing.SwingConstants.RIGHT);
        Theme.PixelLabel paused = new Theme.PixelLabel("GAME PAUSED", 2, Theme.TEXT_3);
        paused.setAlignment(javax.swing.SwingConstants.RIGHT);
        closeButton = Theme.button("Back", Theme.ButtonStyle.RED);   // the kit's BACK button (Esc also works)
        closeButton.addActionListener(this);
        JPanel right = Theme.transparentPanel(new BorderLayout(0, 8));
        right.add(title, BorderLayout.NORTH);
        right.add(paused, BorderLayout.CENTER);
        JPanel buttonRow = Theme.transparentPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttonRow.add(closeButton);
        right.add(buttonRow, BorderLayout.SOUTH);

        JPanel header = Theme.transparentPanel(new BorderLayout());
        header.add(trader, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JComponent buildRecipeTable() {
        recipeModel = new RecipeTableModel();
        recipeTable = new JTable(recipeModel);
        Theme.styleTable(recipeTable, ROW_HEIGHT);
        recipeTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        recipeTable.setDefaultRenderer(Object.class, new RecipeCellRenderer());
        recipeTable.getColumnModel().getColumn(1).setCellRenderer(new CostCellRenderer());
        int[] widths = {190, 170, 110};
        for (int i = 0; i < widths.length; i++) {
            recipeTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        recipeTable.getSelectionModel().addListSelectionListener(this);
        recipeTable.addMouseListener(new RowClickHandler());
        recipeTable.setCursor(Theme.handCursor());
        Theme.Card card = new Theme.Card(new BorderLayout(), Theme.Panel.METAL);
        card.setBorder(new EmptyBorder(8, 8, 8, 8));
        card.add(Theme.scroll(recipeTable, Theme.PANEL), BorderLayout.CENTER);
        return card;
    }

    /** The selected recipe on the kit's paper note: what it is, what it costs (have / need), and CRAFT. */
    private JComponent buildDetailCard() {
        detailKindLabel = new Theme.PixelLabel("WEAPON", 2, Theme.DARK_RED);
        detailLevelLabel = new Theme.PixelLabel("", 2, Theme.DARK_RED);
        detailLevelLabel.setAlignment(javax.swing.SwingConstants.RIGHT);
        detailIconLabel = new JLabel();
        detailTitleLabel = new Theme.PixelLabel("", 3, Theme.PAPER_TEXT);
        detailDescriptionArea = Theme.textBlock(" ", 13, Theme.PAPER_TEXT);

        JPanel kindRow = Theme.transparentPanel(new BorderLayout());
        kindRow.add(detailKindLabel, BorderLayout.WEST);
        kindRow.add(detailLevelLabel, BorderLayout.EAST);
        JPanel titleRow = Theme.transparentPanel(new BorderLayout(8, 0));
        titleRow.add(detailTitleLabel, BorderLayout.CENTER);
        titleRow.add(detailIconLabel, BorderLayout.EAST);
        JPanel top = Theme.transparentPanel(new BorderLayout(0, 8));
        top.add(kindRow, BorderLayout.NORTH);
        top.add(titleRow, BorderLayout.CENTER);
        top.add(detailDescriptionArea, BorderLayout.SOUTH);

        costList = Theme.transparentPanel(new GridLayout(0, 1, 0, 6));
        JPanel cost = Theme.transparentPanel(new BorderLayout(0, 8));
        cost.setBorder(new EmptyBorder(14, 0, 0, 0));
        cost.add(new Theme.PixelLabel("COST", 2, Theme.DARK_RED), BorderLayout.NORTH);
        cost.add(costList, BorderLayout.CENTER);
        JPanel middle = Theme.transparentPanel(new BorderLayout());
        middle.add(cost, BorderLayout.NORTH);

        feedbackArea = Theme.textBlock(" ", 12, Theme.PAPER_TEXT);
        craftButton = Theme.button("CRAFT", Theme.ButtonStyle.RED);
        Theme.setKeyHint(craftButton, "enter");
        craftButton.addActionListener(this);
        JPanel buttonRow = Theme.transparentPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttonRow.add(craftButton);
        JPanel bottom = Theme.transparentPanel(new BorderLayout(0, 8));
        bottom.add(feedbackArea, BorderLayout.CENTER);
        bottom.add(buttonRow, BorderLayout.SOUTH);

        Theme.Card card = new Theme.Card(new BorderLayout(), Theme.Panel.PAPER);
        card.setBorder(new EmptyBorder(14, 30, 16, 18));   // clear of the paper's red margin line
        card.setPreferredSize(new Dimension(DETAIL_WIDTH, 10));
        card.add(top, BorderLayout.NORTH);
        card.add(middle, BorderLayout.CENTER);
        card.add(bottom, BorderLayout.SOUTH);
        return card;
    }

    /** Every item that can be stored, as kit inventory slots. Hover a slot for the item name. */
    private JPanel buildBackpack() {
        JPanel slots = Theme.transparentPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        for (GameData.ItemType type : GameData.ItemType.values()) {
            if (type != GameData.ItemType.CHEST) {
                Theme.Slot slot = new Theme.Slot(Theme.fit(AssetLoader.getItemIcon(type), 24, 24));
                itemSlots.put(type, slot);
                slots.add(slot);
            }
        }
        JPanel backpack = Theme.transparentPanel(new BorderLayout(0, 8));
        backpack.add(Theme.row(0, Theme.caption("Backpack")), BorderLayout.NORTH);
        backpack.add(slots, BorderLayout.CENTER);
        return backpack;
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
            showSelectedRecipe(true);
        }
    }

    // ================================================================== public API
    /** Re-reads the player's inventory and levels into the table, backpack and detail note; keeps the selection. */
    public void refreshTable() {
        int selected = recipeTable.getSelectedRow();
        recipeModel.fireTableRowsUpdated(0, recipeModel.getRowCount() - 1);
        if (selected >= 0 && recipeTable.getSelectedRow() != selected) {
            recipeTable.setRowSelectionInterval(selected, selected);
        }
        showSelectedRecipe(false);   // also refreshes the backpack slots
    }

    /** Crafts the selected recipe through Player.craft and shows the result (or the reason it failed). */
    public void craftSelectedUpgrade() {
        GameData.UpgradeRecipe recipe = getSelectedRecipe();
        if (recipe == null) {
            showFeedback("Pick something in the list first.", Theme.PAPER_TEXT);
            return;
        }
        String result = player.craft(recipe);
        refreshTable();
        if (result != null) {
            showFeedback(result, Theme.DARK_GREEN);
            trader.setMessage("Done! " + shortName(recipe) + " is ready. Anything else?");
        } else {
            showFeedback(capitalize(explainWhyNot(recipe)) + ".", Theme.DARK_RED);
            trader.setMessage("I can't make that yet: " + explainWhyNot(recipe) + ".");
        }
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
        if (status.equals("Full HP")) {
            return "your health is already full";
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

    /** Fills the detail note and the backpack highlights for the selected row. */
    private void showSelectedRecipe(boolean newSelection) {
        GameData.UpgradeRecipe r = getSelectedRecipe();
        Map<GameData.ItemType, Integer> cost = r == null ? new EnumMap<>(GameData.ItemType.class) : player.getCost(r);
        updateBackpack(cost);
        costList.removeAll();
        if (r == null) {
            detailTitleLabel.setText("NOTHING SELECTED");
            detailDescriptionArea.setText("Pick an upgrade in the list.");
            detailKindLabel.setText("");
            detailLevelLabel.setText("");
            detailIconLabel.setIcon(null);
            craftButton.setEnabled(false);
            costList.revalidate();
            return;
        }
        RowState state = stateOf(r);
        String status = player.getRecipeStatus(r);
        detailKindLabel.setText(kindName(r));
        detailTitleLabel.setText(shortName(r).toUpperCase());
        detailDescriptionArea.setText(r.description);
        detailIconLabel.setIcon(iconFor(r));
        detailLevelLabel.setText(status.startsWith("Lv") || status.startsWith("Have") || state == RowState.DONE
                ? status.toUpperCase() : "");
        for (Map.Entry<GameData.ItemType, Integer> e : cost.entrySet()) {
            costList.add(costRow(e.getKey(), player.getItemCount(e.getKey()), e.getValue(), state == RowState.DONE));
        }
        costList.revalidate();
        costList.repaint();
        craftButton.setText(actionLabelFor(r));
        craftButton.setEnabled(state == RowState.READY);
        if (newSelection) {
            trader.setMessage(npcSpeakerLine(r));
        }
        if (newSelection || feedbackArea.getText().trim().isEmpty()) {
            showFeedback(state == RowState.READY ? "Ready: " + actionLabelFor(r).toLowerCase() + " now." : capitalize(explainWhyNot(r)) + ".",
                    state == RowState.READY ? Theme.DARK_GREEN : Theme.PAPER_TEXT);
        }
    }

    private static String actionLabelFor(GameData.UpgradeRecipe r) {
        switch (r.kind) {
            case BASE_TURRET:
                return "RECRUIT";
            case BASE_WALL:
            case BASE_MEDIC:
            case BASE_FARM:
                return "BUILD";
            case HEAL_NOW:
                return "HEAL NOW";
            default:
                return "CRAFT";
        }
    }

    private static String npcSpeakerLine(GameData.UpgradeRecipe r) {
        switch (r.kind) {
            case WEAPON:
            case DAMAGE:
            case FIRE_RATE:
                return "GUNSMITH: Bring scavenged steel and parts, and I'll upgrade your firepower.";
            case BASE_TURRET:
            case BASE_WALL:
            case BASE_FARM:
                return "ENGINEER: Let's fortify the camp perimeter before the next Horde Raid hits!";
            case BASE_MEDIC:
            case HEAL_NOW:
            case MEDKIT:
                return "MEDIC: Hurt from scavenging? I can patch you up or expand the medical tent.";
            default:
                return "TRAINER: Spend your loot to boost your survivor's speed, armour, and endurance.";
        }
    }

    /** One cost line: item icon and name on the left, "have / need" on the right (green, red, or grey when done). */
    private static JPanel costRow(GameData.ItemType item, int have, int need, boolean done) {
        JLabel name = Theme.label(item.displayName, Font.PLAIN, 13, Theme.PAPER_TEXT);
        name.setIcon(new ImageIcon(Theme.fit(AssetLoader.getItemIcon(item), 20, 20)));
        name.setIconTextGap(8);
        Color amountColor = done ? Theme.GREY_3 : have >= need ? Theme.DARK_GREEN : Theme.DARK_RED;
        Theme.PixelLabel amount = new Theme.PixelLabel(have + "/" + need, 2, amountColor);
        JPanel row = Theme.transparentPanel(new BorderLayout());
        row.add(name, BorderLayout.CENTER);
        JPanel holder = Theme.transparentPanel(new GridBagLayout());
        holder.add(amount);
        row.add(holder, BorderLayout.EAST);
        return row;
    }

    private void showFeedback(String text, Color color) {
        feedbackArea.setText(text);
        feedbackArea.setForeground(color);
    }

    /** New counts in every slot; slots used by the selected recipe get a gold frame and a red count when short. */
    private void updateBackpack(Map<GameData.ItemType, Integer> cost) {
        for (Map.Entry<GameData.ItemType, Theme.Slot> e : itemSlots.entrySet()) {
            int count = player.getItemCount(e.getKey());
            Integer need = cost.get(e.getKey());
            Theme.Slot slot = e.getValue();
            Theme.SlotKind kind = need != null ? Theme.SlotKind.SELECTED
                    : count == 0 ? Theme.SlotKind.EMPTY : Theme.SlotKind.NORMAL;
            slot.update(kind, Theme.fit(AssetLoader.getItemIcon(e.getKey()), 24, 24), String.valueOf(count), count == 0 && need == null);
            slot.setCountColor(need != null && count < need ? Theme.RED : Theme.TEXT);
            slot.setToolTipText(e.getKey().displayName + ": " + count + (need != null ? " (need " + need + ")" : ""));
        }
    }

    private static String kindName(GameData.UpgradeRecipe r) {
        switch (r.kind) {
            case WEAPON:
                return "WEAPON";
            case BASE_TURRET:
            case BASE_WALL:
            case BASE_MEDIC:
            case BASE_FARM:
                return "BASE BUILD";
            case HEAL_NOW:
            case MEDKIT:
                return "MEDICAL";
            default:
                return "UPGRADE";
        }
    }

    /** "Craft Revolver" -> "Revolver": the list already says what kind it is. */
    private static String shortName(GameData.UpgradeRecipe r) {
        return r.name.replaceFirst("^(Craft|Upgrade|Reinforce|Recruit) ", "");
    }

    private static ImageIcon iconFor(GameData.UpgradeRecipe r) {
        if (r.kind == GameData.RecipeKind.WEAPON && r.weapon != null) {
            return new ImageIcon(Theme.fit(AssetLoader.getWeaponIcon(r.weapon), 64, 32));
        }
        if (r.kind == GameData.RecipeKind.MEDKIT || r.kind == GameData.RecipeKind.HEAL_NOW || r.kind == GameData.RecipeKind.BASE_MEDIC) {
            return new ImageIcon(Theme.fit(AssetLoader.getItemIcon(GameData.ItemType.MEDKIT), 32, 32));
        }
        if (r.kind == GameData.RecipeKind.BASE_FARM) {
            return new ImageIcon(Theme.fit(AssetLoader.getItemIcon(GameData.ItemType.FOOD), 32, 32));
        }
        if (r.kind == GameData.RecipeKind.BASE_TURRET) {
            return new ImageIcon(Theme.fit(AssetLoader.getItemIcon(GameData.ItemType.STEEL), 32, 32));
        }
        if (r.kind == GameData.RecipeKind.BASE_WALL) {
            return new ImageIcon(Theme.fit(AssetLoader.getItemIcon(GameData.ItemType.WOOD), 32, 32));
        }
        if (r.kind == GameData.RecipeKind.ARMOR) {
            return new ImageIcon(Theme.kit("status_icons/armored", Theme.S));
        }
        return null;
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void closeWorkshop() {
        AssetLoader.playSound("click");
        dispose();
    }

    /** Short status for the table; the detail note explains the reason in full. */
    private String statusText(GameData.UpgradeRecipe r) {
        RowState state = stateOf(r);
        if (state == RowState.READY) {
            return "Ready";
        }
        if (state == RowState.LOCKED) {
            return "Locked";
        }
        String status = player.getRecipeStatus(r);
        return status.equals("Missing items") ? "Need parts" : status;
    }

    private static Color statusColor(RowState state) {
        switch (state) {
            case READY: return Theme.GREEN;
            case LOCKED: return Theme.GOLD;
            default: return Theme.TEXT_3;
        }
    }

    // ================================================================== inner classes
    /** The recipe JTable's data: one row per GameData.RECIPES entry, values computed from the player now. */
    private final class RecipeTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private final String[] columns = {"UPGRADE", "COST", "STATUS"};

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
                case 0: return shortName(r);
                case 1: return player.getCost(r);
                default: return statusText(r);
            }
        }
    }

    /** Name and status cells: bright when craftable, grey when owned or maxed; a gold bar marks the selected row. */
    private final class RecipeCellRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, false, row, column);
            RowState state = stateOf(GameData.RECIPES.get(row));
            boolean marker = selected && column == 0;
            javax.swing.border.Border inner = marker
                    ? new javax.swing.border.CompoundBorder(
                            new MatteBorder(0, 2 * Theme.S, 0, 0, Theme.GOLD),
                            new EmptyBorder(0, 10, 0, 10))
                    : new EmptyBorder(0, 12, 0, 10);
            setBorder(new javax.swing.border.CompoundBorder(
                    new MatteBorder(0, 0, Theme.S, 0, Theme.SLOT), inner));
            setBackground(selected ? Theme.GREY_1 : Theme.PANEL);
            if (column == 2) {
                setText("\u25A0  " + value);   // a small square, like a pixel bullet
                setFont(Theme.font(Font.BOLD, 12));
                setForeground(statusColor(state));
            } else {
                setFont(Theme.font(state == RowState.READY ? Font.BOLD : Font.PLAIN, 13));
                setForeground(state == RowState.DONE ? Theme.TEXT_3 : Theme.TEXT);
            }
            return this;
        }
    }

    /** Cost cell: each item as its icon and the amount in the pixel font; red when the backpack holds too few. */
    private final class CostCellRenderer extends JComponent implements TableCellRenderer {
        private static final long serialVersionUID = 1L;
        private transient Map<GameData.ItemType, Integer> cost;
        private Color background;
        private boolean done;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            cost = player.getCost(GameData.RECIPES.get(row));
            done = stateOf(GameData.RECIPES.get(row)) == RowState.DONE;
            background = selected ? Theme.GREY_1 : Theme.PANEL;
            setToolTipText(GameData.UpgradeRecipe.costToText(cost));
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(background);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(Theme.SLOT);
            g2.fillRect(0, getHeight() - Theme.S, getWidth(), Theme.S);
            if (done) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
            }
            int x = 10;
            int iconY = (getHeight() - 16) / 2;
            if (cost != null) {
                for (Map.Entry<GameData.ItemType, Integer> e : cost.entrySet()) {
                    g2.drawImage(AssetLoader.getItemIcon(e.getKey()), x, iconY, null);
                    x += 17;
                    boolean enough = player.getItemCount(e.getKey()) >= e.getValue();
                    String amount = String.valueOf(e.getValue());
                    Theme.drawPixelText(g2, amount, x, (getHeight() - 10) / 2, 2, enough ? Theme.TEXT_2 : Theme.RED);
                    x += Theme.pixelWidth(amount, 2) + 9;
                }
            }
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
