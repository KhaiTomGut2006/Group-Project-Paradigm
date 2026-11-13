// Tapatsawin Chaieua          6813105
// Thanapon Thanadunpremdet    6813106
// Tanawat Wongwad             6813107
// Natthanon Achachan          6813241
package Project3_6813107;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.BasicProgressBarUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicSliderUI;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;

/**
 * The look of every window, built from the Hollow Creek UI kit (resources/ui/kit):
 * 9-slice panels (metal, paper, glass, danger), red and grey buttons in four states, check boxes,
 * sliders, bars, inventory slots, the 3x5 pixel font, key caps, cursors, portraits and titles.
 *
 * Every control is still a normal Swing component (JButton, JCheckBox, JSlider, JProgressBar ...);
 * only its painting is replaced by a small "UI" class here (Swing's pluggable look and feel).
 * The pixel art is always enlarged by whole numbers (usually 2x) with nearest-neighbour scaling,
 * so it stays sharp.
 */
final class Theme {

    // ------------------------------------------------------------------ colours (sampled from the kit)
    static final Color INK = new Color(0x16121A);        // outlines, darkest
    static final Color BG = new Color(0x1B1820);         // window background
    static final Color PANEL = new Color(0x2A282F);      // inside of the metal panel
    static final Color SLOT = new Color(0x24232B);       // inside of slots, fields and table rows
    static final Color GREY_1 = new Color(0x3B3942);
    static final Color GREY_2 = new Color(0x55525D);
    static final Color GREY_3 = new Color(0x77737E);
    static final Color TEXT = new Color(0xF0E6C0);       // cream
    static final Color TEXT_2 = new Color(0xB3AB98);     // muted cream
    static final Color TEXT_3 = new Color(0x77737E);     // captions, disabled
    static final Color PAPER_TEXT = new Color(0x3B3942); // ink on the paper panel
    static final Color GOLD = new Color(0xD8BF5C);
    static final Color RED = new Color(0xD05A48);
    static final Color DARK_RED = new Color(0x922628);
    static final Color GREEN = new Color(0x7AB040);
    static final Color DARK_GREEN = new Color(0x4A7A2A);
    static final Color ORANGE = new Color(0xE88A30);

    static final int S = 2;                       // the kit is drawn at 2x everywhere
    static final int BUTTON_HEIGHT = 16 * S;      // kit buttons are 16 px tall
    static final int SLOT_SIZE = 20 * S;          // kit inventory slots are 20 x 20

    /** Client property: a key-cap sprite ("b", "esc", "enter" ...) drawn inside a button after its text. */
    static final String KEY_HINT = "kit.keyHint";
    /** Client property: Integer scale for one button (default 2), e.g. 3 for the big START button. */
    static final String BUTTON_SCALE = "kit.scale";

    private static final String FAMILY = pickFontFamily();
    private static boolean lookAndFeelInstalled;

    private Theme() {
    }

    // ================================================================== fonts
    /** Body text (descriptions, names): Segoe UI on Windows, Helvetica Neue on macOS, else SansSerif. */
    private static String pickFontFamily() {
        String[] preferred = {"Segoe UI", "Helvetica Neue", "Arial"};
        try {
            Set<String> available = new HashSet<>(Arrays.asList(
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
            for (String family : preferred) {
                if (available.contains(family)) {
                    return family;
                }
            }
        } catch (RuntimeException ex) {
            System.err.println("Font list not available: " + ex.getMessage());
        }
        return Font.SANS_SERIF;
    }

    static Font font(int style, int size) {
        return new Font(FAMILY, style, size);
    }

    // ================================================================== kit images
    private static final Map<String, BufferedImage> KIT_CACHE = new HashMap<>();

    /** A kit sprite such as "titles/paused" (no .png), enlarged by a whole factor. */
    static synchronized BufferedImage kit(String path, int factor) {
        String key = path + "@" + factor;
        BufferedImage img = KIT_CACHE.get(key);
        if (img == null) {
            BufferedImage raw = AssetLoader.loadImage("ui/kit/" + path + ".png");
            img = factor == 1 ? raw : scale(raw, factor);
            KIT_CACHE.put(key, img);
        }
        return img;
    }

    // Keys that have a sprite. B, G, P and "1-8" were drawn with the kit's own pixel font
    // (dev-tools/asset-tools/MakeKeys.java); everything else is the kit's original art.
    private static final Map<String, String> KEY_SPRITES = new HashMap<>();
    static {
        String[] letters = {"W", "A", "S", "D", "E", "F", "Q", "R", "B", "G", "P", "1", "2", "3", "4", "5"};
        for (String k : letters) {
            KEY_SPRITES.put(k.toLowerCase(), "keys/key_" + k);
        }
        for (String k : new String[]{"esc", "enter", "tab", "ctrl", "shift", "space", "1to8"}) {
            KEY_SPRITES.put(k, "keys/key_" + k);
        }
        for (String k : new String[]{"mouse_left", "mouse_mid", "mouse_right"}) {
            KEY_SPRITES.put(k, "keys/" + k);
        }
    }

    /** The key-cap sprite for "w", "esc", "mouse_left" ... or null when the kit has none. */
    static BufferedImage keySprite(String key, int factor) {
        String path = KEY_SPRITES.get(key.toLowerCase());
        return path == null ? null : kit(path, factor);
    }

    /** A row of key-cap sprites, e.g. keyCaps(2, "w", "a", "s", "d"). */
    static JPanel keyCaps(int factor, String... keys) {
        JPanel panel = transparentPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        for (String key : keys) {
            BufferedImage sprite = keySprite(key, factor);
            if (sprite != null) {
                panel.add(new JLabel(new ImageIcon(sprite)));
            }
        }
        return panel;
    }

    // ================================================================== pixel font (font/hollow_creek_font_3x5.png)
    private static final String GLYPHS = " !$%&+-./0123456789:ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static boolean[][][] glyphMasks;   // [glyph][row][column]

    private static synchronized boolean[][][] glyphs() {
        if (glyphMasks == null) {
            BufferedImage sheet = kit("font/hollow_creek_font_3x5", 1);
            glyphMasks = new boolean[GLYPHS.length()][5][];
            for (int i = 0; i < GLYPHS.length(); i++) {
                char c = GLYPHS.charAt(i);
                int width = c == 'M' || c == 'W' ? 5 : 3;
                int gx = (i % 16) * 6, gy = (i / 16) * 6;
                for (int row = 0; row < 5; row++) {
                    glyphMasks[i][row] = new boolean[width];
                    for (int col = 0; col < width; col++) {
                        int px = gx + col, py = gy + row;
                        glyphMasks[i][row][col] = px < sheet.getWidth() && py < sheet.getHeight()
                                && (sheet.getRGB(px, py) >>> 24) != 0;
                    }
                }
            }
        }
        return glyphMasks;
    }

    /** The font only has capitals, digits and a few signs: other characters are mapped or dropped. */
    private static int glyphIndex(char c) {
        char u = Character.toUpperCase(c);
        if (u == ',') {
            u = '.';
        } else if (u == '_' || u == '>' || u == '<') {
            u = '-';
        } else if (u == '*') {
            u = '+';
        }
        return GLYPHS.indexOf(u);   // -1 = not in the font
    }

    static int pixelWidth(String text, int scale) {
        boolean[][][] g = glyphs();
        int w = 0;
        for (int i = 0; i < text.length(); i++) {
            int index = glyphIndex(text.charAt(i));
            if (index >= 0) {
                w += (g[index][0].length + 1) * scale;
            }
        }
        return Math.max(0, w - scale);
    }

    /** Draws text in the kit's pixel font with its top-left corner at x, y. */
    static void drawPixelText(Graphics2D g2, String text, int x, int y, int scale, Color color) {
        boolean[][][] g = glyphs();
        g2.setColor(color);
        int cx = x;
        for (int i = 0; i < text.length(); i++) {
            int index = glyphIndex(text.charAt(i));
            if (index < 0) {
                continue;
            }
            boolean[][] mask = g[index];
            for (int row = 0; row < 5; row++) {
                for (int col = 0; col < mask[row].length; col++) {
                    if (mask[row][col]) {
                        g2.fillRect(cx + col * scale, y + row * scale, scale, scale);
                    }
                }
            }
            cx += (mask[0].length + 1) * scale;
        }
    }

    /** Pixel text with a dark drop shadow, readable on top of the game world. */
    static void drawPixelTextShadow(Graphics2D g2, String text, int x, int y, int scale, Color color) {
        drawPixelText(g2, text, x + Math.max(1, scale / 2), y + Math.max(1, scale / 2), scale, INK);
        drawPixelText(g2, text, x, y, scale, color);
    }

    // ================================================================== look and feel, cursors
    /** Swing's cross-platform look (Metal) in the kit's colours, so popups and tooltips match. Safe to call twice. */
    static void installLookAndFeel() {
        if (lookAndFeelInstalled) {
            return;
        }
        lookAndFeelInstalled = true;
        MetalLookAndFeel.setCurrentTheme(new KitMetalTheme());
        try {
            UIManager.setLookAndFeel(new MetalLookAndFeel());
        } catch (UnsupportedLookAndFeelException ex) {
            System.err.println("Metal look and feel not available: " + ex.getMessage());
        }
        UIManager.put("Panel.background", new ColorUIResource(BG));
        UIManager.put("ToolTip.background", new ColorUIResource(INK));
        UIManager.put("ToolTip.foreground", new ColorUIResource(TEXT));
        UIManager.put("ToolTip.border", BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(GREY_3), BorderFactory.createEmptyBorder(4, 7, 4, 7)));
        UIManager.put("ToolTip.font", new FontUIResource(font(Font.PLAIN, 12)));
    }

    private static final Map<String, Cursor> CURSORS = new HashMap<>();

    /** One of the kit's mouse cursors ("cursor_arrow", "cursor_hand") at 2x; the normal cursor if the PC cannot. */
    static synchronized Cursor cursor(String name, int hotX, int hotY) {
        Cursor c = CURSORS.get(name);
        if (c == null) {
            c = Cursor.getDefaultCursor();
            if (!GraphicsEnvironment.isHeadless()) {
                try {
                    c = Toolkit.getDefaultToolkit().createCustomCursor(kit("cursors/" + name, S),
                            new Point(hotX * S, hotY * S), name);
                } catch (RuntimeException ex) {
                    System.err.println("Custom cursor not available: " + ex.getMessage());
                }
            }
            CURSORS.put(name, c);
        }
        return c;
    }

    static Cursor arrowCursor() {
        return cursor("cursor_arrow", 1, 0);
    }

    static Cursor handCursor() {
        return cursor("cursor_hand", 5, 0);
    }

    // ================================================================== simple components
    static JLabel label(String text, int style, int size, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font(style, size));
        label.setForeground(color);
        return label;
    }

    /** A small pixel-font caption such as "YOUR NAME". */
    static PixelLabel caption(String text) {
        return new PixelLabel(text, S, TEXT_2);
    }

    /** Read-only wrapped text that sits on its panel (descriptions, help). */
    static JTextArea textBlock(String text, int size, Color color) {
        TextBlock area = new TextBlock(text);
        area.setEditable(false);
        area.setFocusable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setFont(font(Font.PLAIN, size));
        area.setForeground(color);
        area.setBorder(null);
        return area;
    }

    /**
     * A wrapped JTextArea that stays narrow before its first layout (so GridBagLayout does not stretch
     * the column to the unwrapped line width) and revalidates when laid out at a new width.
     */
    private static final class TextBlock extends JTextArea {
        private static final long serialVersionUID = 1L;

        TextBlock(String text) {
            super(text);
        }

        @Override
        public Dimension getPreferredSize() {
            if (!isPreferredSizeSet() && getWidth() == 0 && getParent() != null) {
                Insets in = getParent().getInsets();
                int pw = getParent().getWidth() - in.left - in.right;
                if (pw > 0) {
                    super.setSize(pw, Short.MAX_VALUE);
                }
            }
            Dimension d = super.getPreferredSize();
            if (!isPreferredSizeSet()) {
                return new Dimension(Math.min(100, d.width), d.height);
            }
            return d;
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        @SuppressWarnings("deprecation")
        public void reshape(int x, int y, int w, int h) {
            super.reshape(x, y, w, h);
            if (!isPreferredSizeSet() && w > 0 && super.getPreferredSize().height != h) {
                Container p = getParent();
                if (p != null) {
                    p.invalidate();
                    SwingUtilities.invokeLater(p::revalidate);
                }
            }
        }
    }

    static JPanel transparentPanel(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    static JPanel row(int gap, Component... parts) {
        JPanel panel = transparentPanel(new FlowLayout(FlowLayout.LEFT, gap, 0));
        for (Component part : parts) {
            panel.add(part);
        }
        return panel;
    }

    /** Text in the kit's pixel font as a Swing component. */
    static final class PixelLabel extends JComponent {
        private static final long serialVersionUID = 1L;
        private String text;
        private final int scale;
        private Color color;
        private boolean shadow;
        private int align = SwingConstants.LEFT;

        PixelLabel(String text, int scale, Color color) {
            this.text = text;
            this.scale = scale;
            this.color = color;
            setOpaque(false);
        }

        void setText(String newText) {
            if (!newText.equals(text)) {
                text = newText;
                revalidate();
                repaint();
            }
        }

        String getText() {
            return text;
        }

        void setColor(Color newColor) {
            color = newColor;
            repaint();
        }

        void setShadow(boolean on) {
            shadow = on;
        }

        void setAlignment(int swingConstant) {
            align = swingConstant;
        }

        @Override
        public Dimension getPreferredSize() {
            int extra = shadow ? Math.max(1, scale / 2) : 0;
            return new Dimension(pixelWidth(text, scale) + extra, 5 * scale + extra);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            int w = pixelWidth(text, scale);
            int x = align == SwingConstants.CENTER ? (getWidth() - w) / 2 : align == SwingConstants.RIGHT ? getWidth() - w : 0;
            int y = (getHeight() - 5 * scale) / 2;
            if (shadow) {
                drawPixelTextShadow(g2, text, x, y, scale, color);
            } else {
                drawPixelText(g2, text, x, y, scale, color);
            }
            g2.dispose();
        }
    }

    // ================================================================== 9-slice painting
    /**
     * Draws a small kit image stretched to any size without stretching its border:
     * the four corners are copied, the edges and the centre are tiled (or stretched).
     * l, t, r, b are the border sizes in the image's own pixels.
     */
    static final class NineSlice {
        private final BufferedImage img;
        private final int l, t, r, b;
        private final boolean tile;
        private final Color centreFill;   // null = draw the image's centre

        NineSlice(BufferedImage img, int l, int t, int r, int b, boolean tile, Color centreFill) {
            this.img = img;
            this.l = l;
            this.t = t;
            this.r = r;
            this.b = b;
            this.tile = tile;
            this.centreFill = centreFill;
        }

        void paint(Graphics2D g2, int x, int y, int w, int h, int scale) {
            Graphics2D g = (Graphics2D) g2.create();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            int iw = img.getWidth(), ih = img.getHeight();
            int[] sx = {0, l, iw - r, iw};
            int[] sy = {0, t, ih - b, ih};
            int[] dx = {x, x + l * scale, x + w - r * scale, x + w};
            int[] dy = {y, y + t * scale, y + h - b * scale, y + h};
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    if (dx[col + 1] <= dx[col] || dy[row + 1] <= dy[row]) {
                        continue;
                    }
                    if (row == 1 && col == 1 && centreFill != null) {
                        g.setColor(centreFill);
                        g.fillRect(dx[1], dy[1], dx[2] - dx[1], dy[2] - dy[1]);
                    } else if (tile && (row == 1 || col == 1)) {
                        tilePiece(g, sx[col], sy[row], sx[col + 1], sy[row + 1], dx[col], dy[row], dx[col + 1], dy[row + 1], scale);
                    } else {
                        g.drawImage(img, dx[col], dy[row], dx[col + 1], dy[row + 1], sx[col], sy[row], sx[col + 1], sy[row + 1], null);
                    }
                }
            }
            g.dispose();
        }

        /** Repeats one piece of the image at its real (scaled) size, cutting the last copy short. */
        private void tilePiece(Graphics2D g, int sx1, int sy1, int sx2, int sy2, int dx1, int dy1, int dx2, int dy2, int scale) {
            int pw = (sx2 - sx1) * scale, ph = (sy2 - sy1) * scale;
            for (int y = dy1; y < dy2; y += ph) {
                int hh = Math.min(ph, dy2 - y);
                for (int x = dx1; x < dx2; x += pw) {
                    int ww = Math.min(pw, dx2 - x);
                    g.drawImage(img, x, y, x + ww, y + hh, sx1, sy1, sx1 + (ww + scale - 1) / scale, sy1 + (hh + scale - 1) / scale, null);
                }
            }
        }
    }

    /** The four kit panels. */
    enum Panel {
        METAL("metal"), PAPER("paper"), GLASS("glass"), DANGER("danger");

        private final String name;

        Panel(String name) {
            this.name = name;
        }

        NineSlice slice() {
            BufferedImage raw = kit("panels/panel_" + name + "_48", 1);
            if (this == PAPER) {
                BufferedImage clean = copyImage(raw);
                Graphics2D g = clean.createGraphics();
                // replace the single tape mark on the top edge so tiling does not repeat dashes
                g.drawImage(raw, 20, 0, 28, 3, 8, 0, 16, 3, null);
                g.dispose();
                // middle height 48 - 15 - 9 = 24 (exact multiple of the 6 px ruled line spacing)
                return new NineSlice(clean, 16, 15, 16, 9, true, null);
            }
            if (this == DANGER) {
                // middle size 48 - 12 - 12 = 24 (exact multiple of the 6 px diagonal stripe period)
                return new NineSlice(raw, 12, 12, 12, 12, true, null);
            }
            if (this == METAL) {
                BufferedImage clean = copyImage(raw);
                Graphics2D g = clean.createGraphics();
                // remove stray rust specks on the metal edges before 9-slice tiling
                g.drawImage(raw, 45, 24, 48, 25, 45, 20, 48, 21, null);
                g.drawImage(raw, 39, 1, 41, 2, 20, 1, 22, 2, null);
                g.dispose();
                return new NineSlice(clean, 16, 16, 16, 16, true, PANEL);
            }
            return new NineSlice(raw, 16, 16, 16, 16, true, null);
        }
    }

    private static BufferedImage copyImage(BufferedImage src) {
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return copy;
    }

    private static final Map<Panel, NineSlice> PANELS = new HashMap<>();

    static synchronized void paintPanel(Graphics2D g2, Panel panel, int x, int y, int w, int h, int scale) {
        NineSlice slice = PANELS.get(panel);
        if (slice == null) {
            slice = panel.slice();
            PANELS.put(panel, slice);
        }
        slice.paint(g2, x, y, w, h, scale);
    }

    /** A JPanel painted as one of the kit's panels. */
    static final class Card extends JPanel {
        private static final long serialVersionUID = 1L;
        private final Panel panel;

        Card(LayoutManager layout, Panel panel) {
            super(layout);
            this.panel = panel;
            setOpaque(false);
            setBorder(new EmptyBorder(18, 20, 18, 20));
        }

        @Override
        protected void paintComponent(Graphics g) {
            paintPanel((Graphics2D) g, panel, 0, 0, getWidth(), getHeight(), S);
        }
    }

    // ================================================================== slots
    enum SlotKind { NORMAL, SELECTED, EMPTY, LOCKED }

    /** One kit inventory slot (20 x 20) at the given scale. */
    static void drawSlot(Graphics2D g2, SlotKind kind, int x, int y, int scale) {
        g2.drawImage(kit("hud/inventory_slot_" + kind.name().toLowerCase(), scale), x, y, null);
    }

    /** An inventory slot as a component: icon in the middle, count in the corner. */
    static final class Slot extends JComponent {
        private static final long serialVersionUID = 1L;
        private SlotKind kind = SlotKind.NORMAL;
        private transient BufferedImage icon;
        private String count = "";
        private boolean faded;
        private Color countColor = TEXT;

        Slot(BufferedImage icon) {
            this.icon = icon;
            setPreferredSize(new Dimension(SLOT_SIZE, SLOT_SIZE));
            setOpaque(false);
        }

        /** Colour of the count in the corner (e.g. red when a recipe needs more than you have). */
        void setCountColor(Color color) {
            countColor = color;
            repaint();
        }

        void update(SlotKind newKind, BufferedImage newIcon, String newCount, boolean fade) {
            kind = newKind;
            icon = newIcon;
            count = newCount == null ? "" : newCount;
            faded = fade;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            drawSlot(g2, kind, 0, 0, S);
            if (icon != null && kind != SlotKind.LOCKED) {
                if (faded) {
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
                }
                g2.drawImage(icon, (SLOT_SIZE - icon.getWidth()) / 2, (SLOT_SIZE - icon.getHeight()) / 2 - 1, null);
                g2.setComposite(AlphaComposite.SrcOver);
            }
            if (!count.isEmpty()) {
                int w = pixelWidth(count, 1);
                drawPixelTextShadow(g2, count, SLOT_SIZE - w - 5, SLOT_SIZE - 10, 1, faded ? TEXT_3 : countColor);
            }
            g2.dispose();
        }
    }

    // ================================================================== buttons
    enum ButtonStyle { RED, GREY }

    // the kit has these words baked into red buttons (buttons/labeled)
    private static final Set<String> LABELED = new HashSet<>(Arrays.asList("START", "QUIT", "OPTIONS", "CONTINUE", "BACK"));
    private static final Map<String, NineSlice> BUTTON_SLICES = new HashMap<>();

    /** Grey buttons: the kit's grey (disabled) art, lightened for hover and pushed down for pressed. */
    private static synchronized NineSlice buttonSlice(boolean red, String state) {
        String key = (red ? "red_" : "grey_") + state;
        NineSlice slice = BUTTON_SLICES.get(key);
        if (slice == null) {
            BufferedImage img;
            if (red) {
                img = kit("buttons/button_" + state + "_48", 1);
            } else if (state.equals("hover")) {
                img = recolor(kit("buttons/button_disabled_48", 1),
                        new int[]{0xFF55525D, 0xFF3B3942, 0xFF2A282F}, new int[]{0xFF77737E, 0xFF4E4B56, 0xFF3B3942});
            } else if (state.equals("pressed")) {
                img = recolor(kit("buttons/button_pressed_48", 1),
                        new int[]{0xFF922628, 0xFF66161F}, new int[]{0xFF55525D, 0xFF2A282F});
            } else {
                img = kit("buttons/button_disabled_48", 1);
            }
            slice = new NineSlice(img, 3, 3, 3, 3, false, null);
            BUTTON_SLICES.put(key, slice);
        }
        return slice;
    }

    /** A copy of an image with some exact colours swapped (used to make grey hover/pressed buttons). */
    static BufferedImage recolor(BufferedImage src, int[] from, int[] to) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int p = src.getRGB(x, y);
                for (int i = 0; i < from.length; i++) {
                    if (p == from[i]) {
                        p = to[i];
                        break;
                    }
                }
                out.setRGB(x, y, p);
            }
        }
        return out;
    }

    static JButton button(String text, ButtonStyle style) {
        JButton button = new JButton(text);
        styleButton(button, style);
        return button;
    }

    static void styleButton(AbstractButton button, ButtonStyle style) {
        button.setUI(new KitButtonUI(style));
        button.setRolloverEnabled(true);
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setBorder(new EmptyBorder(0, 0, 0, 0));
        button.setCursor(handCursor());
    }

    /** Tabs and the class picker: grey kit buttons that turn red while selected. */
    static void styleToggle(AbstractButton button) {
        styleButton(button, ButtonStyle.GREY);
    }

    static void setKeyHint(AbstractButton button, String key) {
        button.putClientProperty(KEY_HINT, key);
        button.revalidate();
    }

    static void setButtonScale(AbstractButton button, int scale) {
        button.putClientProperty(BUTTON_SCALE, scale);
        button.revalidate();
    }

    /** Paints kit buttons: 9-slice background for the state, pixel-font text, optional key cap. */
    static final class KitButtonUI extends BasicButtonUI {
        private final ButtonStyle style;

        KitButtonUI(ButtonStyle style) {
            this.style = style;
        }

        private static int scaleOf(AbstractButton b) {
            Object s = b.getClientProperty(BUTTON_SCALE);
            return s instanceof Integer ? (Integer) s : S;
        }

        private boolean isRed(AbstractButton b) {
            boolean toggle = b instanceof JToggleButton || b instanceof JRadioButton;
            return style == ButtonStyle.RED || (toggle && b.isSelected());
        }

        private static String state(AbstractButton b) {
            ButtonModel m = b.getModel();
            if (!m.isEnabled()) {
                return "disabled";
            }
            if (m.isArmed() && m.isPressed()) {
                return "pressed";
            }
            return m.isRollover() ? "hover" : "normal";
        }

        /** The kit's ready-made word button (START, QUIT ...) when this is a plain red button with that word. */
        private BufferedImage labeled(AbstractButton b) {
            String word = b.getText().trim().toUpperCase();
            if (style == ButtonStyle.RED && LABELED.contains(word) && b.getClientProperty(KEY_HINT) == null) {
                return kit("buttons/labeled/" + word.toLowerCase() + "_" + state(b), scaleOf(b));
            }
            return null;
        }

        @Override
        public Dimension getPreferredSize(JComponent c) {
            AbstractButton b = (AbstractButton) c;
            BufferedImage word = labeled(b);
            if (word != null) {
                return new Dimension(word.getWidth(), word.getHeight());
            }
            int s = scaleOf(b);
            int pad = (b instanceof JToggleButton || b instanceof JRadioButton) ? 8 * s : 14 * s;
            int w = pixelWidth(b.getText(), s) + pad;
            BufferedImage hint = hintSprite(b);
            if (hint != null) {
                w += hint.getWidth() + 3 * s;
            }
            return new Dimension(w, 16 * s);
        }

        private static BufferedImage hintSprite(AbstractButton b) {
            Object hint = b.getClientProperty(KEY_HINT);
            return hint == null ? null : keySprite(hint.toString(), 1);
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            Graphics2D g2 = (Graphics2D) g.create();
            int w = c.getWidth(), h = c.getHeight();
            BufferedImage word = labeled(b);
            if (word != null) {
                g2.drawImage(word, (w - word.getWidth()) / 2, (h - word.getHeight()) / 2, null);
                g2.dispose();
                return;
            }
            int s = scaleOf(b);
            String state = state(b);
            buttonSlice(isRed(b), state).paint(g2, 0, 0, w, h, s);
            BufferedImage hint = hintSprite(b);
            int textW = pixelWidth(b.getText(), s);
            int groupW = textW + (hint == null ? 0 : hint.getWidth() + 3 * s);
            int x = (w - groupW) / 2;
            int push = state.equals("pressed") ? s : 0;
            int y = (h - 5 * s) / 2 - s / 2 + push;
            drawPixelText(g2, b.getText(), x, y, s, b.isEnabled() ? TEXT : TEXT_3);
            if (hint != null) {
                g2.drawImage(hint, x + textW + 3 * s, (h - hint.getHeight()) / 2 + push, null);
            }
            g2.dispose();
        }
    }

    // ================================================================== check box, slider, bars
    static void styleCheckBox(JCheckBox box) {
        box.setIcon(new CheckIcon());
        box.setOpaque(false);
        box.setFocusPainted(false);
        box.setFont(font(Font.PLAIN, 13));
        box.setForeground(TEXT);
        box.setIconTextGap(4);
        box.setBorder(new EmptyBorder(0, 0, 0, 0));
        box.setCursor(handCursor());
    }

    /** The kit's check box (on / off) at 2x; it reads the check box's state when painting. */
    static final class CheckIcon implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            boolean on = c instanceof AbstractButton && ((AbstractButton) c).isSelected();
            g.drawImage(kit(on ? "buttons/checkbox_on" : "buttons/checkbox_off", S), x, y, null);
        }

        @Override
        public int getIconWidth() {
            return 16 * S;
        }

        @Override
        public int getIconHeight() {
            return 16 * S;
        }
    }

    static void styleSlider(JSlider slider) {
        slider.setUI(new KitSliderUI(slider));
        slider.setOpaque(false);
        slider.setFocusable(false);
        slider.setPaintTicks(false);
        slider.setPaintLabels(false);
        slider.setPreferredSize(new Dimension(160, 8 * S + 4));
        slider.setMinimumSize(new Dimension(80, 8 * S + 4));
        slider.setCursor(handCursor());
    }

    /** The kit slider: thin track, red up to the knob, cream knob with a dark outline. */
    static final class KitSliderUI extends BasicSliderUI {
        KitSliderUI(JSlider slider) {
            super(slider);
        }

        @Override
        protected Dimension getThumbSize() {
            return new Dimension(5 * S, 8 * S);
        }

        @Override
        public void paintTrack(Graphics g) {
            Rectangle t = trackRect;
            int y = t.y + t.height / 2 - S;
            int knob = thumbRect.x + thumbRect.width / 2;
            g.setColor(INK);
            g.fillRect(t.x, y, t.width, 2 * S);
            g.setColor(slider.isEnabled() ? DARK_RED : GREY_2);
            g.fillRect(t.x + S, y, Math.max(0, knob - t.x - S), 2 * S);
        }

        @Override
        public void paintThumb(Graphics g) {
            Rectangle r = thumbRect;
            int x = r.x + (r.width - 5 * S) / 2, y = r.y + (r.height - 8 * S) / 2;
            g.setColor(INK);
            g.fillRect(x, y, 5 * S, 8 * S);
            g.setColor(new Color(0xD8CFA8));
            g.fillRect(x + S, y + S, 3 * S, 6 * S);
        }

        @Override
        public void paintFocus(Graphics g) {
            // no focus rectangle
        }
    }

    /** Bar fills from the kit (bars/fill_*_60x6). */
    enum Fill { HEALTH, STAMINA, AMMO, INFECTION, WATER }

    private static NineSlice barFrame;

    /** Paints the kit bar: dark frame and a coloured fill for the given fraction (0..1). */
    static synchronized void drawBar(Graphics2D g2, Fill fill, int x, int y, int w, double fraction, int scale) {
        if (barFrame == null) {
            barFrame = new NineSlice(kit("bars/bar_frame_64", 1), 2, 2, 2, 2, false, SLOT);
        }
        int h = 10 * scale;
        barFrame.paint(g2, x, y, w, h, scale);
        int innerW = w - 4 * scale;
        int fillW = (int) Math.round(innerW * Math.max(0, Math.min(1, fraction)));
        if (fillW > 0) {
            Graphics2D g = (Graphics2D) g2.create();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(kit("bars/fill_" + fill.name().toLowerCase() + "_60x6", 1), x + 2 * scale, y + 2 * scale, fillW, 6 * scale, null);
            g.dispose();
        }
    }

    /** A JProgressBar painted as a kit bar. */
    static JProgressBar bar(Fill fill, int width, int scale) {
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setUI(new KitBarUI(fill, scale));
        bar.setBorder(null);
        bar.setOpaque(false);
        bar.setPreferredSize(new Dimension(width, 10 * scale));
        bar.setFocusable(false);
        return bar;
    }

    static final class KitBarUI extends BasicProgressBarUI {
        private final Fill fill;
        private final int scale;

        KitBarUI(Fill fill, int scale) {
            this.fill = fill;
            this.scale = scale;
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            JProgressBar bar = (JProgressBar) c;
            int range = Math.max(1, bar.getMaximum() - bar.getMinimum());
            drawBar((Graphics2D) g, fill, 0, (c.getHeight() - 10 * scale) / 2, c.getWidth(),
                    (bar.getValue() - bar.getMinimum()) / (double) range, scale);
        }
    }

    // ================================================================== text field and combo box
    /** A text field drawn inside a kit slot frame: gold frame while typing, red frame on an error. */
    static final class KitField extends JTextField {
        private static final long serialVersionUID = 1L;
        private boolean error;

        KitField(String text, int columns) {
            super(text, columns);
            setOpaque(false);
            setFont(font(Font.PLAIN, 14));
            setForeground(TEXT);
            setCaretColor(GOLD);
            setSelectionColor(GREY_2);
            setSelectedTextColor(TEXT);
            setBorder(new EmptyBorder(0, 14, 0, 14));
            setPreferredSize(new Dimension(10, 18 * S));
            setMinimumSize(new Dimension(40, 18 * S));
            getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    clearOnEdit();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    clearOnEdit();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    clearOnEdit();
                }

                private void clearOnEdit() {
                    if (error) {
                        setError(false);
                    }
                }
            });
            addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    repaint();
                }

                @Override
                public void focusLost(FocusEvent e) {
                    repaint();
                }
            });
        }

        void setError(boolean on) {
            error = on;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            SlotKind frame = error ? SlotKind.LOCKED : hasFocus() ? SlotKind.SELECTED : SlotKind.NORMAL;
            slotFrame(frame).paint((Graphics2D) g, 0, 0, getWidth(), getHeight(), S);
            super.paintComponent(g);
        }
    }

    private static final Map<SlotKind, NineSlice> SLOT_FRAMES = new HashMap<>();

    /** The frame of a kit slot stretched to any size (the centre is filled plainly). */
    static synchronized NineSlice slotFrame(SlotKind kind) {
        NineSlice slice = SLOT_FRAMES.get(kind);
        if (slice == null) {
            slice = new NineSlice(kit("hud/inventory_slot_" + kind.name().toLowerCase(), 1), 3, 3, 3, 3, false, SLOT);
            SLOT_FRAMES.put(kind, slice);
        }
        return slice;
    }

    static <T> void styleCombo(JComboBox<T> combo, ListCellRenderer<? super T> renderer) {
        combo.setUI(new KitComboUI());
        combo.setRenderer(renderer);
        combo.setFont(font(Font.PLAIN, 13));
        combo.setForeground(TEXT);
        combo.setBackground(SLOT);
        combo.setOpaque(false);
        combo.setFocusable(false);
        combo.setMaximumRowCount(8);
        combo.setBorder(new EmptyBorder(0, 10, 0, 4));
        combo.setPreferredSize(new Dimension(10, 18 * S));
        combo.setMinimumSize(new Dimension(10, 18 * S));
        combo.setCursor(handCursor());
    }

    static final class KitComboUI extends BasicComboBoxUI {
        @Override
        protected JButton createArrowButton() {
            JButton arrow = new JButton();
            arrow.setIcon(new ArrowIcon());
            arrow.setBorder(new EmptyBorder(0, 0, 0, 10));
            arrow.setContentAreaFilled(false);
            arrow.setFocusPainted(false);
            arrow.setOpaque(false);
            arrow.setFocusable(false);
            return arrow;
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            slotFrame(isPopupVisible(comboBox) ? SlotKind.SELECTED : SlotKind.NORMAL)
                    .paint((Graphics2D) g, 0, 0, c.getWidth(), c.getHeight(), S);
            super.paint(g, c);
        }

        @Override
        public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
            // the slot frame already filled the background
        }

        @Override
        protected ComboPopup createPopup() {
            BasicComboPopup popup = new BasicComboPopup(comboBox);
            popup.setBorder(BorderFactory.createLineBorder(GREY_3, S));
            popup.getList().setBackground(SLOT);
            return popup;
        }
    }

    /** A pixel "v" arrow for the combo box. */
    private static final class ArrowIcon implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(TEXT);
            for (int i = 0; i < 3; i++) {
                g.fillRect(x + i * S, y + i * S, (5 - 2 * i) * S, S);
            }
        }

        @Override
        public int getIconWidth() {
            return 5 * S;
        }

        @Override
        public int getIconHeight() {
            return 3 * S;
        }
    }

    /** Combo box rows: cream text on the slot colour; the closed combo shows only the value. */
    static class ListRenderer<T> extends JLabel implements ListCellRenderer<T> {
        private static final long serialVersionUID = 1L;

        ListRenderer() {
            setFont(font(Font.PLAIN, 13));
        }

        String textFor(T value) {
            return String.valueOf(value);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends T> list, T value, int index,
                                                      boolean selected, boolean focused) {
            setText(value == null ? "" : textFor(value));
            boolean inPopup = index >= 0;
            setOpaque(inPopup);
            setBackground(selected ? GREY_1 : SLOT);
            setForeground(selected && inPopup ? GOLD : TEXT);
            setBorder(inPopup ? new EmptyBorder(6, 10, 6, 10) : new EmptyBorder(0, 2, 0, 0));
            return this;
        }
    }

    // ================================================================== scrolling and tables
    static JScrollPane scroll(JComponent view, Color background) {
        JScrollPane pane = new JScrollPane(view);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.setViewportBorder(null);
        pane.setOpaque(false);
        pane.getViewport().setBackground(background);
        pane.getVerticalScrollBar().setUI(new KitScrollBarUI());
        pane.getHorizontalScrollBar().setUI(new KitScrollBarUI());
        pane.getVerticalScrollBar().setUnitIncrement(16);
        pane.getVerticalScrollBar().setOpaque(false);
        pane.setCorner(JScrollPane.UPPER_RIGHT_CORNER, transparentPanel(null));
        return pane;
    }

    /** Square grey thumb with a dark outline, no arrow buttons. */
    static final class KitScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            thumbColor = GREY_2;
            trackColor = new Color(0, 0, 0, 0);
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return zeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return zeroButton();
        }

        private static JButton zeroButton() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            return b;
        }

        @Override
        public Dimension getPreferredSize(JComponent c) {
            return new Dimension(6 * S, 6 * S);
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle bounds) {
            // invisible track
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty() || !scrollbar.isEnabled()) {
                return;
            }
            g.setColor(INK);
            g.fillRect(r.x + S, r.y, r.width - 2 * S, r.height);
            g.setColor(isThumbRollover() ? GREY_3 : GREY_2);
            g.fillRect(r.x + 2 * S, r.y + S, r.width - 4 * S, r.height - 2 * S);
        }
    }

    static void styleTable(JTable table, int rowHeight) {
        table.setFont(font(Font.PLAIN, 13));
        table.setRowHeight(rowHeight);
        table.setBackground(PANEL);
        table.setForeground(TEXT);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionBackground(GREY_1);
        table.setSelectionForeground(TEXT);
        table.setFillsViewportHeight(true);
        table.setFocusable(false);
        JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false);
        header.setResizingAllowed(false);
        header.setDefaultRenderer(new HeaderRenderer());
        header.setBackground(PANEL);
        header.setPreferredSize(new Dimension(10, 28));
    }

    /** Table headers in the pixel font with a dark line underneath. Columns named "#..." are right-aligned. */
    static final class HeaderRenderer extends JComponent implements TableCellRenderer {
        private static final long serialVersionUID = 1L;
        private String text = "";
        private boolean right;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            text = String.valueOf(value);
            right = column > 0 && text.startsWith("#");
            if (right) {
                text = text.substring(1);
            }
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(PANEL);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(INK);
            g2.fillRect(0, getHeight() - S, getWidth(), S);
            int w = pixelWidth(text, S);
            drawPixelText(g2, text, right ? getWidth() - w - 12 : 12, (getHeight() - 5 * S) / 2, S, TEXT_2);
            g2.dispose();
        }
    }

    // ================================================================== dialog box with a portrait
    /**
     * The kit's dialog box (176 x 48 at 2x): a portrait on the left and up to three lines of text
     * written on its ruled lines, like a character speaking.
     */
    static final class SpeechBox extends JComponent {
        private static final long serialVersionUID = 1L;
        private final transient BufferedImage boxImage;
        private final transient BufferedImage portrait;
        private String message;

        SpeechBox(String portraitName, String message) {
            BufferedImage raw = kit("panels/dialog_box_176x48", 1);
            BufferedImage clean = copyImage(raw);
            Graphics2D g = clean.createGraphics();
            // remove the stray rust speck on the right border
            g.drawImage(raw, 173, 24, 176, 25, 173, 20, 176, 21, null);
            g.dispose();
            this.boxImage = scale(clean, S);
            this.portrait = kit("hud/" + portraitName, S);
            this.message = message;
            setPreferredSize(new Dimension(176 * S, 48 * S));
            setMinimumSize(getPreferredSize());
            setOpaque(false);
        }

        void setMessage(String text) {
            message = text;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.drawImage(boxImage, 0, 0, null);
            g2.drawImage(portrait, 8 * S, 8 * S, null);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(font(Font.PLAIN, 12));
            g2.setColor(TEXT);
            int[] lines = {12, 20, 28};   // the ruled lines of the box (image pixels)
            List<String> wrapped = wrap(message, g2.getFontMetrics(), (166 - 48) * S);
            for (int i = 0; i < Math.min(lines.length, wrapped.size()); i++) {
                g2.drawString(wrapped.get(i), 48 * S, lines[i] * S - 3);
            }
            g2.dispose();
        }
    }

    /** Splits text into lines that fit a width. */
    static List<String> wrap(String text, FontMetrics fm, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String test = line.length() == 0 ? word : line + " " + word;
            if (fm.stringWidth(test) > width && line.length() > 0) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    // ================================================================== HUD pieces drawn with Graphics2D
    /**
     * The kit's red banner (from hud/day_banner_example) with any text: its ends are kept,
     * the middle is stretched, and the text is written in the pixel font. Centred on cx.
     */
    static void drawBanner(Graphics2D g2, String text, int cx, int y, int scale) {
        BufferedImage src = kit("hud/day_banner_example", 1);
        int textW = pixelWidth(text, 2);
        int w = 12 + textW + 14;
        BufferedImage banner = new BufferedImage(w, src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D b = banner.createGraphics();
        b.drawImage(src, 0, 0, 11, src.getHeight(), 0, 0, 11, src.getHeight(), null);                   // left end
        b.drawImage(src, 11, 0, w - 14, src.getHeight(), 11, 0, 12, src.getHeight(), null);             // middle
        b.drawImage(src, w - 14, 0, w, src.getHeight(), src.getWidth() - 14, 0, src.getWidth(), src.getHeight(), null);
        drawPixelText(b, text, 12, 5, 2, TEXT);
        b.dispose();
        Graphics2D g = (Graphics2D) g2.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(banner, cx - w * scale / 2, y, w * scale, banner.getHeight() * scale, null);
        g.dispose();
    }

    /** The kit's ammo counter (three bullets on a dark plate) with the real number. Returns its width. */
    static int drawAmmoCounter(Graphics2D g2, String count, int x, int y, int scale) {
        BufferedImage src = kit("hud/ammo_counter_example", 1);
        int textW = pixelWidth(count, 1);
        int w = 17 + textW + 5;
        BufferedImage plate = new BufferedImage(w, src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D p = plate.createGraphics();
        p.drawImage(src, 0, 0, 16, src.getHeight(), 0, 0, 16, src.getHeight(), null);
        p.drawImage(src, 16, 0, w - 2, src.getHeight(), 16, 0, 17, src.getHeight(), null);
        p.drawImage(src, w - 2, 0, w, src.getHeight(), src.getWidth() - 2, 0, src.getWidth(), src.getHeight(), null);
        drawPixelText(p, count, 18, 5, 1, TEXT);
        p.dispose();
        Graphics2D g = (Graphics2D) g2.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(plate, x, y, w * scale, plate.getHeight() * scale, null);
        g.dispose();
        return w * scale;
    }

    static int ammoCounterWidth(String count, int scale) {
        return (17 + pixelWidth(count, 1) + 5) * scale;
    }

    /** The kit's hotbar frame (left end, stretched middle, right end) behind a row of slots. */
    static void drawHotbarFrame(Graphics2D g2, int x, int y, int slots, int scale) {
        BufferedImage src = kit("hud/hotbar_6", 1);
        int w = hotbarWidth(slots, 1);
        int h = src.getHeight();
        Graphics2D g = (Graphics2D) g2.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(src, x, y, x + 2 * scale, y + h * scale, 0, 0, 2, h, null);
        g.drawImage(src, x + 2 * scale, y, x + (w - 2) * scale, y + h * scale, 2, 0, 3, h, null);
        g.drawImage(src, x + (w - 2) * scale, y, x + w * scale, y + h * scale, src.getWidth() - 2, 0, src.getWidth(), h, null);
        g.dispose();
    }

    static int hotbarWidth(int slots, int scale) {
        return (3 + slots * 20 + Math.max(0, slots - 1) * 2 + 3) * scale;
    }

    // minimap frame without its north notch, so the notch is not stretched
    private static NineSlice minimapFrame;

    /** The kit's square minimap frame stretched around a w x h area, with its notch centred on top. */
    static synchronized void drawMinimapFrame(Graphics2D g2, int x, int y, int w, int h, int scale) {
        BufferedImage src = kit("hud/minimap_frame_square", 1);
        if (minimapFrame == null) {
            BufferedImage plain = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D p = plain.createGraphics();
            p.drawImage(src, 0, 0, null);
            p.setComposite(AlphaComposite.Src);
            p.drawImage(src, 28, 0, 36, 5, 20, 0, 28, 5, null);   // copy a plain piece of edge over the notch
            p.dispose();
            minimapFrame = new NineSlice(plain, 3, 3, 3, 3, false, null);
        }
        minimapFrame.paint(g2, x, y, w, h, scale);
        Graphics2D g = (Graphics2D) g2.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        int nx = x + w / 2 - 4 * scale;
        g.drawImage(src, nx, y, nx + 8 * scale, y + 5 * scale, 28, 0, 36, 5, null);
        g.dispose();
    }

    // ================================================================== dialogs
    /**
     * A small modal message drawn on a kit panel (replaces JOptionPane).
     * titleSprite: a kit title such as "titles/survived", or null to write the title in the pixel font.
     * rows: optional label / value pairs. Returns true for the primary button (or Enter).
     */
    static boolean ask(Window owner, Panel panel, String titleSprite, String title, String message,
                       String[][] rows, String primaryText, String secondaryText) {
        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setUndecorated(true);
        final boolean[] answer = {false};
        Color textColor = panel == Panel.PAPER ? PAPER_TEXT : TEXT;
        Color mutedColor = panel == Panel.PAPER ? PAPER_TEXT : TEXT_2;

        Card content = new Card(new BorderLayout(0, 18), panel);
        content.setBorder(new EmptyBorder(26, 30, 24, 30));
        JPanel text = transparentPanel(new BorderLayout(0, 12));
        if (titleSprite != null) {
            JLabel titleLabel = new JLabel(new ImageIcon(kit(titleSprite, S)));
            titleLabel.setHorizontalAlignment(SwingConstants.LEFT);
            text.add(titleLabel, BorderLayout.NORTH);
        } else {
            PixelLabel titleLabel = new PixelLabel(title, 3, GOLD);
            titleLabel.setShadow(true);
            text.add(titleLabel, BorderLayout.NORTH);
        }
        JTextArea body = textBlock(message, 13, mutedColor);
        body.setSize(new Dimension(320, 1));   // a fixed width lets the text area work out its wrapped height
        body.setPreferredSize(new Dimension(320, body.getPreferredSize().height));
        text.add(body, BorderLayout.CENTER);
        if (rows != null) {
            JPanel list = transparentPanel(new GridLayout(rows.length, 2, 0, 8));
            list.setBorder(new EmptyBorder(6, 0, 0, 0));
            for (String[] r : rows) {
                list.add(label(r[0], Font.PLAIN, 13, mutedColor));
                PixelLabel value = new PixelLabel(r[1], S, textColor);
                value.setAlignment(SwingConstants.RIGHT);
                list.add(value);
            }
            text.add(list, BorderLayout.SOUTH);
        }
        content.add(text, BorderLayout.CENTER);

        JButton primary = button(primaryText, ButtonStyle.RED);
        JPanel buttons = transparentPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton secondary = null;
        if (secondaryText != null) {
            secondary = button(secondaryText, ButtonStyle.GREY);
            buttons.add(secondary);
        }
        buttons.add(primary);
        content.add(buttons, BorderLayout.SOUTH);

        ActionListener close = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                answer[0] = e.getSource() == primary || "enter".equals(e.getActionCommand());
                dialog.dispose();
            }
        };
        primary.addActionListener(close);
        if (secondary != null) {
            secondary.addActionListener(close);
        }
        bindKey(dialog, KeyEvent.VK_ENTER, "enter", close);
        bindKey(dialog, KeyEvent.VK_ESCAPE, "escape", close);

        dialog.setContentPane(content);
        dialog.getRootPane().setCursor(arrowCursor());
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);   // modal: waits here until a button closes it
        return answer[0];
    }

    private static void bindKey(JDialog dialog, int key, String command, ActionListener listener) {
        dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, 0), command);
        dialog.getRootPane().getActionMap().put(command, new AbstractAction(command) {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                listener.actionPerformed(new ActionEvent(dialog, ActionEvent.ACTION_PERFORMED, command));
            }
        });
    }

    // ================================================================== image helpers
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
            return src;
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

    /** Antialiased text and nearest-neighbour images for custom painting. */
    static Graphics2D smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        return g2;
    }

    /** Metal colours swapped for the kit palette (popups, tooltips, default panels). */
    private static final class KitMetalTheme extends DefaultMetalTheme {
        private static final ColorUIResource PRIMARY1 = new ColorUIResource(GOLD);
        private static final ColorUIResource PRIMARY2 = new ColorUIResource(GREY_1);
        private static final ColorUIResource PRIMARY3 = new ColorUIResource(GREY_1);
        private static final ColorUIResource SECONDARY1 = new ColorUIResource(GREY_3);
        private static final ColorUIResource SECONDARY2 = new ColorUIResource(GREY_1);
        private static final ColorUIResource SECONDARY3 = new ColorUIResource(BG);
        private static final ColorUIResource TEXT_COLOR = new ColorUIResource(TEXT);
        private static final ColorUIResource DARK = new ColorUIResource(SLOT);
        private static final FontUIResource CONTROL_FONT = new FontUIResource(font(Font.PLAIN, 13));

        @Override
        public String getName() {
            return "Hollow Creek";
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
            return TEXT_COLOR;
        }

        @Override
        protected ColorUIResource getWhite() {
            return DARK;
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
