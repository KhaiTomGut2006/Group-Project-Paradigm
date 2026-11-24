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

final class Theme {

    static final Color INK = new Color(0x16121A), BG = new Color(0x1B1820), PANEL = new Color(0x2A282F);
    static final Color SLOT = new Color(0x24232B), GREY_1 = new Color(0x3B3942), GREY_2 = new Color(0x55525D);
    static final Color GREY_3 = new Color(0x77737E), TEXT = new Color(0xF0E6C0), TEXT_2 = new Color(0xB3AB98);
    static final Color TEXT_3 = new Color(0x77737E), PAPER_TEXT = new Color(0x3B3942), GOLD = new Color(0xD8BF5C);
    static final Color RED = new Color(0xD05A48), DARK_RED = new Color(0x922628);
    static final Color GREEN = new Color(0x7AB040), DARK_GREEN = new Color(0x4A7A2A);

    static final int S = 2, SLOT_SIZE = 20 * S;
    static final String KEY_HINT = "kit.keyHint", BUTTON_SCALE = "kit.scale";

    private static final String FAMILY = pickFontFamily();
    private static boolean lookAndFeelInstalled;

    private Theme() {}

    private static String pickFontFamily() {
        try {
            Set<String> available = new HashSet<>(List.of(
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
            for (String family : new String[]{"Segoe UI", "Helvetica Neue", "Arial"}) {
                if (available.contains(family)) return family;
            }
        } catch (RuntimeException ex) {
            System.err.println("Font list not available: " + ex.getMessage());
        }
        return Font.SANS_SERIF;
    }

    static Font font(int style, int size) { return new Font(FAMILY, style, size); }

    private static final Map<String, BufferedImage> KIT_CACHE = new HashMap<>();

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

    private static final Map<String, String> KEY_SPRITES = new HashMap<>();
    static {
        for (String k : new String[]{"W", "A", "S", "D", "E", "F", "Q", "R", "B", "G", "P", "1", "2", "3", "4", "5"}) {
            KEY_SPRITES.put(k.toLowerCase(), "keys/key_" + k);
        }
        for (String k : new String[]{"esc", "enter", "tab", "ctrl", "shift", "space", "1to8"}) {
            KEY_SPRITES.put(k, "keys/key_" + k);
        }
        for (String k : new String[]{"mouse_left", "mouse_mid", "mouse_right"}) KEY_SPRITES.put(k, "keys/" + k);
    }

    static BufferedImage keySprite(String key, int factor) {
        String path = KEY_SPRITES.get(key.toLowerCase());
        return path == null ? null : kit(path, factor);
    }

    static JPanel keyCaps(int factor, String... keys) {
        JPanel panel = transparentPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        for (String key : keys) {
            BufferedImage sprite = keySprite(key, factor);
            if (sprite != null) panel.add(new JLabel(new ImageIcon(sprite)));
        }
        return panel;
    }

    private static final String GLYPHS = " !$%&+-./0123456789:ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static boolean[][][] glyphMasks;

    private static synchronized boolean[][][] glyphs() {
        if (glyphMasks == null) {
            BufferedImage sheet = kit("font/hollow_creek_font_3x5", 1);
            glyphMasks = new boolean[GLYPHS.length()][5][];
            for (int i = 0; i < GLYPHS.length(); i++) {
                char c = GLYPHS.charAt(i);
                int width = (c == 'M' || c == 'W') ? 5 : 3, gx = (i % 16) * 6, gy = (i / 16) * 6;
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

    private static int glyphIndex(char c) {
        char u = Character.toUpperCase(c);
        if (u == ',') u = '.';
        else if (u == '_' || u == '>' || u == '<') u = '-';
        else if (u == '*') u = '+';
        return GLYPHS.indexOf(u);
    }

    static int pixelWidth(String text, int scale) {
        boolean[][][] g = glyphs();
        int w = 0;
        for (int i = 0; i < text.length(); i++) {
            int index = glyphIndex(text.charAt(i));
            if (index >= 0) w += (g[index][0].length + 1) * scale;
        }
        return Math.max(0, w - scale);
    }

    static void drawPixelText(Graphics2D g2, String text, int x, int y, int scale, Color color) {
        boolean[][][] g = glyphs();
        g2.setColor(color);
        int cx = x;
        for (int i = 0; i < text.length(); i++) {
            int index = glyphIndex(text.charAt(i));
            if (index < 0) continue;
            boolean[][] mask = g[index];
            for (int row = 0; row < 5; row++) {
                for (int col = 0; col < mask[row].length; col++) {
                    if (mask[row][col]) g2.fillRect(cx + col * scale, y + row * scale, scale, scale);
                }
            }
            cx += (mask[0].length + 1) * scale;
        }
    }

    static void drawPixelTextShadow(Graphics2D g2, String text, int x, int y, int scale, Color color) {
        int off = Math.max(1, scale / 2);
        drawPixelText(g2, text, x + off, y + off, scale, INK);
        drawPixelText(g2, text, x, y, scale, color);
    }

    static void installLookAndFeel() {
        if (lookAndFeelInstalled) return;
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

    static synchronized Cursor cursor(String name, int hotX, int hotY) {
        return CURSORS.computeIfAbsent(name, key -> {
            if (!GraphicsEnvironment.isHeadless()) {
                try {
                    return Toolkit.getDefaultToolkit().createCustomCursor(kit("cursors/" + key, S),
                            new Point(hotX * S, hotY * S), key);
                } catch (RuntimeException ex) {
                    System.err.println("Custom cursor not available: " + ex.getMessage());
                }
            }
            return Cursor.getDefaultCursor();
        });
    }

    static Cursor arrowCursor() { return cursor("cursor_arrow", 1, 0); }
    static Cursor handCursor() { return cursor("cursor_hand", 5, 0); }

    static JLabel label(String text, int style, int size, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font(style, size));
        label.setForeground(color);
        return label;
    }

    static PixelLabel caption(String text) { return new PixelLabel(text, S, TEXT_2); }

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

    static void onEdit(JTextField field, Runnable action) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { action.run(); }
            @Override public void removeUpdate(DocumentEvent e) { action.run(); }
            @Override public void changedUpdate(DocumentEvent e) { action.run(); }
        });
    }

    private static final class TextBlock extends JTextArea {
        private static final long serialVersionUID = 1L;
        TextBlock(String text) { super(text); }

        @Override
        public Dimension getPreferredSize() {
            if (!isPreferredSizeSet() && getWidth() == 0 && getParent() != null) {
                Insets in = getParent().getInsets();
                int pw = getParent().getWidth() - in.left - in.right;
                if (pw > 0) super.setSize(pw, Short.MAX_VALUE);
            }
            Dimension d = super.getPreferredSize();
            return !isPreferredSizeSet() ? new Dimension(Math.min(100, d.width), d.height) : d;
        }

        @Override public Dimension getMinimumSize() { return getPreferredSize(); }

        @Override
        @SuppressWarnings("deprecation")
        public void reshape(int x, int y, int w, int h) {
            super.reshape(x, y, w, h);
            if (!isPreferredSizeSet() && w > 0 && super.getPreferredSize().height != h && getParent() != null) {
                Container p = getParent();
                p.invalidate();
                SwingUtilities.invokeLater(p::revalidate);
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
        for (Component part : parts) panel.add(part);
        return panel;
    }

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

        void setColor(Color newColor) { color = newColor; repaint(); }
        void setShadow(boolean on) { shadow = on; }
        void setAlignment(int swingConstant) { align = swingConstant; }

        @Override
        public Dimension getPreferredSize() {
            int extra = shadow ? Math.max(1, scale / 2) : 0;
            return new Dimension(pixelWidth(text, scale) + extra, 5 * scale + extra);
        }

        @Override public Dimension getMinimumSize() { return getPreferredSize(); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            int w = pixelWidth(text, scale);
            int x = align == SwingConstants.CENTER ? (getWidth() - w) / 2 : align == SwingConstants.RIGHT ? getWidth() - w : 0;
            int y = (getHeight() - 5 * scale) / 2;
            if (shadow) drawPixelTextShadow(g2, text, x, y, scale, color);
            else drawPixelText(g2, text, x, y, scale, color);
            g2.dispose();
        }
    }

    static final class NineSlice {
        private final BufferedImage img;
        private final int l, t, r, b;
        private final boolean tile;
        private final Color centreFill;

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
            int[] sx = {0, l, iw - r, iw}, sy = {0, t, ih - b, ih};
            int[] dx = {x, x + l * scale, x + w - r * scale, x + w}, dy = {y, y + t * scale, y + h - b * scale, y + h};
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    if (dx[col + 1] <= dx[col] || dy[row + 1] <= dy[row]) continue;
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

    enum Panel {
        METAL("metal"), PAPER("paper"), GLASS("glass"), DANGER("danger");
        private final String name;
        Panel(String name) { this.name = name; }

        NineSlice slice() {
            BufferedImage raw = kit("panels/panel_" + name + "_48", 1);
            return switch (this) {
                case PAPER -> new NineSlice(copyWithPatches(raw, false, 20, 0, 28, 3, 8, 0, 16, 3), 16, 15, 16, 9, true, null);
                case DANGER -> new NineSlice(raw, 12, 12, 12, 12, true, null);
                case METAL -> new NineSlice(copyWithPatches(raw, false, 45, 24, 48, 25, 45, 20, 48, 21,
                        39, 1, 41, 2, 20, 1, 22, 2), 16, 16, 16, 16, true, PANEL);
                case GLASS -> new NineSlice(raw, 16, 16, 16, 16, true, null);
            };
        }
    }

    private static BufferedImage copyWithPatches(BufferedImage src, boolean replace, int... rects) {
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(src, 0, 0, null);
        if (replace) g.setComposite(AlphaComposite.Src);
        for (int i = 0; i < rects.length; i += 8) {
            g.drawImage(src, rects[i], rects[i + 1], rects[i + 2], rects[i + 3],
                    rects[i + 4], rects[i + 5], rects[i + 6], rects[i + 7], null);
        }
        g.dispose();
        return copy;
    }

    private static final Map<Panel, NineSlice> PANELS = new HashMap<>();

    static synchronized void paintPanel(Graphics2D g2, Panel panel, int x, int y, int w, int h, int scale) {
        PANELS.computeIfAbsent(panel, Panel::slice).paint(g2, x, y, w, h, scale);
    }

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
        protected void paintComponent(Graphics g) { paintPanel((Graphics2D) g, panel, 0, 0, getWidth(), getHeight(), S); }
    }

    enum SlotKind { NORMAL, SELECTED, EMPTY, LOCKED }

    static void drawSlot(Graphics2D g2, SlotKind kind, int x, int y, int scale) {
        g2.drawImage(kit("hud/inventory_slot_" + kind.name().toLowerCase(), scale), x, y, null);
    }

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

        void setCountColor(Color color) { countColor = color; repaint(); }

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
                if (faded) g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
                g2.drawImage(icon, (SLOT_SIZE - icon.getWidth()) / 2, (SLOT_SIZE - icon.getHeight()) / 2 - 1, null);
                g2.setComposite(AlphaComposite.SrcOver);
            }
            if (!count.isEmpty()) {
                drawPixelTextShadow(g2, count, SLOT_SIZE - pixelWidth(count, 1) - 5, SLOT_SIZE - 10, 1, faded ? TEXT_3 : countColor);
            }
            g2.dispose();
        }
    }

    enum ButtonStyle { RED, GREY }

    private static final Set<String> LABELED = Set.of("START", "QUIT", "OPTIONS", "CONTINUE", "BACK");
    private static final Map<String, NineSlice> BUTTON_SLICES = new HashMap<>();

    private static synchronized NineSlice buttonSlice(boolean red, String state) {
        return BUTTON_SLICES.computeIfAbsent((red ? "red_" : "grey_") + state, key -> {
            BufferedImage img = red ? kit("buttons/button_" + state + "_48", 1)
                    : state.equals("hover") ? recolor(kit("buttons/button_disabled_48", 1),
                            new int[]{0xFF55525D, 0xFF3B3942, 0xFF2A282F}, new int[]{0xFF77737E, 0xFF4E4B56, 0xFF3B3942})
                    : state.equals("pressed") ? recolor(kit("buttons/button_pressed_48", 1),
                            new int[]{0xFF922628, 0xFF66161F}, new int[]{0xFF55525D, 0xFF2A282F})
                    : kit("buttons/button_disabled_48", 1);
            return new NineSlice(img, 3, 3, 3, 3, false, null);
        });
    }

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

    static void styleToggle(AbstractButton button) { styleButton(button, ButtonStyle.GREY); }

    static void setKeyHint(AbstractButton button, String key) {
        button.putClientProperty(KEY_HINT, key);
        button.revalidate();
    }

    static void setButtonScale(AbstractButton button, int scale) {
        button.putClientProperty(BUTTON_SCALE, scale);
        button.revalidate();
    }

    static final class KitButtonUI extends BasicButtonUI {
        private final ButtonStyle style;
        KitButtonUI(ButtonStyle style) { this.style = style; }

        private static int scaleOf(AbstractButton b) {
            return b.getClientProperty(BUTTON_SCALE) instanceof Integer s ? s : S;
        }

        private static String state(AbstractButton b) {
            ButtonModel m = b.getModel();
            if (!m.isEnabled()) return "disabled";
            if (m.isArmed() && m.isPressed()) return "pressed";
            return m.isRollover() ? "hover" : "normal";
        }

        private BufferedImage labeled(AbstractButton b) {
            String word = b.getText().trim().toUpperCase();
            return style == ButtonStyle.RED && LABELED.contains(word) && b.getClientProperty(KEY_HINT) == null
                    ? kit("buttons/labeled/" + word.toLowerCase() + "_" + state(b), scaleOf(b)) : null;
        }

        private static BufferedImage hintSprite(AbstractButton b) {
            Object hint = b.getClientProperty(KEY_HINT);
            return hint == null ? null : keySprite(hint.toString(), 1);
        }

        @Override
        public Dimension getPreferredSize(JComponent c) {
            AbstractButton b = (AbstractButton) c;
            BufferedImage word = labeled(b);
            if (word != null) return new Dimension(word.getWidth(), word.getHeight());
            int s = scaleOf(b);
            int pad = (b instanceof JToggleButton || b instanceof JRadioButton) ? 8 * s : 14 * s;
            BufferedImage hint = hintSprite(b);
            return new Dimension(pixelWidth(b.getText(), s) + pad + (hint == null ? 0 : hint.getWidth() + 3 * s), 16 * s);
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
            boolean red = style == ButtonStyle.RED || ((b instanceof JToggleButton || b instanceof JRadioButton) && b.isSelected());
            buttonSlice(red, state).paint(g2, 0, 0, w, h, s);
            BufferedImage hint = hintSprite(b);
            int textW = pixelWidth(b.getText(), s);
            int groupW = textW + (hint == null ? 0 : hint.getWidth() + 3 * s);
            int x = (w - groupW) / 2, push = state.equals("pressed") ? s : 0, y = (h - 5 * s) / 2 - s / 2 + push;
            drawPixelText(g2, b.getText(), x, y, s, b.isEnabled() ? TEXT : TEXT_3);
            if (hint != null) g2.drawImage(hint, x + textW + 3 * s, (h - hint.getHeight()) / 2 + push, null);
            g2.dispose();
        }
    }

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

    static final class CheckIcon implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            boolean on = c instanceof AbstractButton b && b.isSelected();
            g.drawImage(kit(on ? "buttons/checkbox_on" : "buttons/checkbox_off", S), x, y, null);
        }
        @Override public int getIconWidth() { return 16 * S; }
        @Override public int getIconHeight() { return 16 * S; }
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

    static final class KitSliderUI extends BasicSliderUI {
        KitSliderUI(JSlider slider) { super(slider); }
        @Override protected Dimension getThumbSize() { return new Dimension(5 * S, 8 * S); }

        @Override
        public void paintTrack(Graphics g) {
            Rectangle t = trackRect;
            int y = t.y + t.height / 2 - S, knob = thumbRect.x + thumbRect.width / 2;
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

        @Override public void paintFocus(Graphics g) {}
    }

    enum Fill { HEALTH, STAMINA, AMMO, INFECTION, WATER }
    private static NineSlice barFrame;

    static synchronized void drawBar(Graphics2D g2, Fill fill, int x, int y, int w, double fraction, int scale) {
        if (barFrame == null) barFrame = new NineSlice(kit("bars/bar_frame_64", 1), 2, 2, 2, 2, false, SLOT);
        barFrame.paint(g2, x, y, w, 10 * scale, scale);
        int fillW = (int) Math.round((w - 4 * scale) * Math.max(0, Math.min(1, fraction)));
        if (fillW > 0) {
            Graphics2D g = (Graphics2D) g2.create();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(kit("bars/fill_" + fill.name().toLowerCase() + "_60x6", 1), x + 2 * scale, y + 2 * scale, fillW, 6 * scale, null);
            g.dispose();
        }
    }

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
        KitBarUI(Fill fill, int scale) { this.fill = fill; this.scale = scale; }

        @Override
        public void paint(Graphics g, JComponent c) {
            JProgressBar bar = (JProgressBar) c;
            int range = Math.max(1, bar.getMaximum() - bar.getMinimum());
            drawBar((Graphics2D) g, fill, 0, (c.getHeight() - 10 * scale) / 2, c.getWidth(),
                    (bar.getValue() - bar.getMinimum()) / (double) range, scale);
        }
    }

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
            onEdit(this, () -> { if (error) setError(false); });
            addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { repaint(); }
                @Override public void focusLost(FocusEvent e) { repaint(); }
            });
        }

        void setError(boolean on) { error = on; repaint(); }

        @Override
        protected void paintComponent(Graphics g) {
            SlotKind frame = error ? SlotKind.LOCKED : hasFocus() ? SlotKind.SELECTED : SlotKind.NORMAL;
            slotFrame(frame).paint((Graphics2D) g, 0, 0, getWidth(), getHeight(), S);
            super.paintComponent(g);
        }
    }

    private static final Map<SlotKind, NineSlice> SLOT_FRAMES = new HashMap<>();

    static synchronized NineSlice slotFrame(SlotKind kind) {
        return SLOT_FRAMES.computeIfAbsent(kind, k -> new NineSlice(kit("hud/inventory_slot_" + k.name().toLowerCase(), 1), 3, 3, 3, 3, false, SLOT));
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
            slotFrame(isPopupVisible(comboBox) ? SlotKind.SELECTED : SlotKind.NORMAL).paint((Graphics2D) g, 0, 0, c.getWidth(), c.getHeight(), S);
            super.paint(g, c);
        }

        @Override public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {}

        @Override
        protected ComboPopup createPopup() {
            BasicComboPopup popup = new BasicComboPopup(comboBox);
            popup.setBorder(BorderFactory.createLineBorder(GREY_3, S));
            popup.getList().setBackground(SLOT);
            return popup;
        }
    }

    private static final class ArrowIcon implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(TEXT);
            for (int i = 0; i < 3; i++) g.fillRect(x + i * S, y + i * S, (5 - 2 * i) * S, S);
        }
        @Override public int getIconWidth() { return 5 * S; }
        @Override public int getIconHeight() { return 3 * S; }
    }

    static class ListRenderer<T> extends JLabel implements ListCellRenderer<T> {
        private static final long serialVersionUID = 1L;
        ListRenderer() { setFont(font(Font.PLAIN, 13)); }
        String textFor(T value) { return String.valueOf(value); }

        @Override
        public Component getListCellRendererComponent(JList<? extends T> list, T value, int index, boolean selected, boolean focused) {
            setText(value == null ? "" : textFor(value));
            boolean inPopup = index >= 0;
            setOpaque(inPopup);
            setBackground(selected ? GREY_1 : SLOT);
            setForeground(selected && inPopup ? GOLD : TEXT);
            setBorder(inPopup ? new EmptyBorder(6, 10, 6, 10) : new EmptyBorder(0, 2, 0, 0));
            return this;
        }
    }

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

    static final class KitScrollBarUI extends BasicScrollBarUI {
        @Override protected void configureScrollBarColors() { thumbColor = GREY_2; trackColor = new Color(0, 0, 0, 0); }
        @Override protected JButton createDecreaseButton(int orientation) { return zeroButton(); }
        @Override protected JButton createIncreaseButton(int orientation) { return zeroButton(); }
        @Override public Dimension getPreferredSize(JComponent c) { return new Dimension(6 * S, 6 * S); }
        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle bounds) {}

        private static JButton zeroButton() {
            JButton b = new JButton();
            Dimension zero = new Dimension(0, 0);
            b.setPreferredSize(zero);
            b.setMinimumSize(zero);
            b.setMaximumSize(zero);
            return b;
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty() || !scrollbar.isEnabled()) return;
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

    static final class HeaderRenderer extends JComponent implements TableCellRenderer {
        private static final long serialVersionUID = 1L;
        private String text = "";
        private boolean right;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focused, int row, int column) {
            text = String.valueOf(value);
            right = column > 0 && text.startsWith("#");
            if (right) text = text.substring(1);
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

    static final class SpeechBox extends JComponent {
        private static final long serialVersionUID = 1L;
        private final transient BufferedImage boxImage, portrait;
        private String message;

        SpeechBox(String portraitName, String message) {
            BufferedImage raw = kit("panels/dialog_box_176x48", 1);
            this.boxImage = scale(copyWithPatches(raw, false, 173, 24, 176, 25, 173, 20, 176, 21), S);
            this.portrait = kit("hud/" + portraitName, S);
            this.message = message;
            setPreferredSize(new Dimension(176 * S, 48 * S));
            setMinimumSize(getPreferredSize());
            setOpaque(false);
        }

        void setMessage(String text) { message = text; repaint(); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.drawImage(boxImage, 0, 0, null);
            g2.drawImage(portrait, 8 * S, 8 * S, null);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(font(Font.PLAIN, 12));
            g2.setColor(TEXT);
            int[] lines = {12, 20, 28};
            List<String> wrapped = wrap(message, g2.getFontMetrics(), (166 - 48) * S);
            for (int i = 0; i < Math.min(lines.length, wrapped.size()); i++) {
                g2.drawString(wrapped.get(i), 48 * S, lines[i] * S - 3);
            }
            g2.dispose();
        }
    }

    static List<String> wrap(String text, FontMetrics fm, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String test = line.isEmpty() ? word : line + " " + word;
            if (fm.stringWidth(test) > width && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    static void drawBanner(Graphics2D g2, String text, int cx, int y, int scale) {
        drawLabeledStrip(g2, "hud/day_banner_example", text, cx, y, scale, 2, 11, 14, 12, 5, 12 + pixelWidth(text, 2) + 14, true);
    }

    static int drawAmmoCounter(Graphics2D g2, String count, int x, int y, int scale) {
        return drawLabeledStrip(g2, "hud/ammo_counter_example", count, x, y, scale, 1, 16, 2, 18, 5, 17 + pixelWidth(count, 1) + 5, false);
    }

    private static int drawLabeledStrip(Graphics2D g2, String spritePath, String text, int x, int y,
            int scale, int fontScale, int leftCap, int rightCap, int textX, int textY, int width, boolean centerX) {
        BufferedImage src = kit(spritePath, 1);
        BufferedImage strip = new BufferedImage(width, src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D sg = strip.createGraphics();
        drawHudStrip(sg, src, 0, 0, width, 1, leftCap, rightCap);
        drawPixelText(sg, text, textX, textY, fontScale, TEXT);
        sg.dispose();
        Graphics2D g = (Graphics2D) g2.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(strip, centerX ? x - width * scale / 2 : x, y, width * scale, strip.getHeight() * scale, null);
        g.dispose();
        return width * scale;
    }

    static int ammoCounterWidth(String count, int scale) { return (17 + pixelWidth(count, 1) + 5) * scale; }
    static int hotbarWidth(int slots, int scale) { return (3 + slots * 20 + Math.max(0, slots - 1) * 2 + 3) * scale; }

    static void drawHotbarFrame(Graphics2D g2, int x, int y, int slots, int scale) {
        Graphics2D g = (Graphics2D) g2.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        drawHudStrip(g, kit("hud/hotbar_6", 1), x, y, hotbarWidth(slots, 1), scale, 2, 2);
        g.dispose();
    }

    private static void drawHudStrip(Graphics2D g, BufferedImage src, int x, int y, int width, int scale, int leftCap, int rightCap) {
        int h = src.getHeight(), bot = y + h * scale, leftEnd = x + leftCap * scale;
        int rightStart = x + (width - rightCap) * scale, right = x + width * scale;
        g.drawImage(src, x, y, leftEnd, bot, 0, 0, leftCap, h, null);
        g.drawImage(src, leftEnd, y, rightStart, bot, leftCap, 0, leftCap + 1, h, null);
        g.drawImage(src, rightStart, y, right, bot, src.getWidth() - rightCap, 0, src.getWidth(), h, null);
    }

    private static NineSlice minimapFrame;

    static synchronized void drawMinimapFrame(Graphics2D g2, int x, int y, int w, int h, int scale) {
        BufferedImage src = kit("hud/minimap_frame_square", 1);
        if (minimapFrame == null) {
            BufferedImage plain = copyWithPatches(src, true, 28, 0, 36, 5, 20, 0, 28, 5);
            minimapFrame = new NineSlice(plain, 3, 3, 3, 3, false, null);
        }
        minimapFrame.paint(g2, x, y, w, h, scale);
        Graphics2D g = (Graphics2D) g2.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        int nx = x + w / 2 - 4 * scale;
        g.drawImage(src, nx, y, nx + 8 * scale, y + 5 * scale, 28, 0, 36, 5, null);
        g.dispose();
    }

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
        body.setSize(new Dimension(320, 1));
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
        JButton secondary = secondaryText != null ? button(secondaryText, ButtonStyle.GREY) : null;
        if (secondary != null) buttons.add(secondary);
        buttons.add(primary);
        content.add(buttons, BorderLayout.SOUTH);

        ActionListener close = e -> {
            answer[0] = e.getSource() == primary || "enter".equals(e.getActionCommand());
            dialog.dispose();
        };
        primary.addActionListener(close);
        if (secondary != null) secondary.addActionListener(close);
        bindKey(dialog, KeyEvent.VK_ENTER, "enter", close);
        bindKey(dialog, KeyEvent.VK_ESCAPE, "escape", close);

        dialog.setContentPane(content);
        dialog.getRootPane().setCursor(arrowCursor());
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return answer[0];
    }

    private static void bindKey(JDialog dialog, int key, String command, ActionListener listener) {
        dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, 0), command);
        dialog.getRootPane().getActionMap().put(command, new AbstractAction(command) {
            private static final long serialVersionUID = 1L;
            @Override public void actionPerformed(ActionEvent e) {
                listener.actionPerformed(new ActionEvent(dialog, ActionEvent.ACTION_PERFORMED, command));
            }
        });
    }

    static BufferedImage scale(BufferedImage src, int factor) {
        int w = src.getWidth() * factor, h = src.getHeight() * factor;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

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
        if (maxX < 0) return src;
        BufferedImage trimmed = src.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
        return scale(trimmed, Math.max(1, Math.min(w / trimmed.getWidth(), h / trimmed.getHeight())));
    }

    static Graphics2D smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        return g2;
    }

    private static final class KitMetalTheme extends DefaultMetalTheme {
        private static final ColorUIResource P1 = new ColorUIResource(GOLD), P2 = new ColorUIResource(GREY_1);
        private static final ColorUIResource S1 = new ColorUIResource(GREY_3), S2 = new ColorUIResource(GREY_1), S3 = new ColorUIResource(BG);
        private static final ColorUIResource TX = new ColorUIResource(TEXT), DK = new ColorUIResource(SLOT);
        private static final FontUIResource FONT = new FontUIResource(font(Font.PLAIN, 13));

        @Override public String getName() { return "Hollow Creek"; }
        @Override protected ColorUIResource getPrimary1() { return P1; }
        @Override protected ColorUIResource getPrimary2() { return P2; }
        @Override protected ColorUIResource getPrimary3() { return P2; }
        @Override protected ColorUIResource getSecondary1() { return S1; }
        @Override protected ColorUIResource getSecondary2() { return S2; }
        @Override protected ColorUIResource getSecondary3() { return S3; }
        @Override protected ColorUIResource getBlack() { return TX; }
        @Override protected ColorUIResource getWhite() { return DK; }
        @Override public FontUIResource getControlTextFont() { return FONT; }
        @Override public FontUIResource getSystemTextFont() { return FONT; }
        @Override public FontUIResource getUserTextFont() { return FONT; }
        @Override public FontUIResource getMenuTextFont() { return FONT; }
    }
}
