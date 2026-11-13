import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Builds the key-cap sprites the Hollow Creek UI kit does not include (B, G, P, "1-8") from the kit's
 * own blank key and its 3x5 pixel font, so they match the kit exactly. Run once; output goes to
 * src/main/java/Project3_6813107/resources/ui/kit/keys/. Not part of the game.
 * usage: java dev-tools/asset-tools/MakeKeys.java <HollowCreek_UI_Kit_Free folder> <output folder>
 */
public class MakeKeys {
    static final String CHARS = " !$%&+-./0123456789:ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    static final int FACE = 0xFFD8CFA8, INK = 0xFF16121A;

    public static void main(String[] a) throws Exception {
        File kit = new File(a[0]), out = new File(a[1]);
        out.mkdirs();
        BufferedImage font = ImageIO.read(new File(kit, "font/hollow_creek_font_3x5.png"));
        BufferedImage small = blank(ImageIO.read(new File(kit, "keys/key_A.png")));
        BufferedImage wide = blank(ImageIO.read(new File(kit, "keys/key_esc.png")));
        write(small, font, "B", new File(out, "key_B.png"));
        write(small, font, "G", new File(out, "key_G.png"));
        write(small, font, "P", new File(out, "key_P.png"));
        write(wide, font, "1-8", new File(out, "key_1to8.png"));
    }

    /** The key with its letter painted over in the face colour (the letter sits in rows 4-8). */
    static BufferedImage blank(BufferedImage key) {
        BufferedImage b = copy(key);
        for (int y = 4; y <= 8; y++) {
            for (int x = 2; x < b.getWidth() - 2; x++) {
                if (b.getRGB(x, y) == INK) {
                    b.setRGB(x, y, FACE);
                }
            }
        }
        return b;
    }

    static void write(BufferedImage blank, BufferedImage font, String text, File file) throws Exception {
        BufferedImage key = copy(blank);
        int total = 0;
        for (int i = 0; i < text.length(); i++) {
            total += 3 + (i > 0 ? 1 : 0);   // every glyph used here is 3 px wide, 1 px apart
        }
        int x = (key.getWidth() - total) / 2;
        for (char c : text.toCharArray()) {
            int index = CHARS.indexOf(c);
            int gx = (index % 16) * 6, gy = (index / 16) * 6;
            for (int dy = 0; dy < 5; dy++) {
                for (int dx = 0; dx < 3; dx++) {
                    if ((font.getRGB(gx + dx, gy + dy) >>> 24) != 0) {
                        key.setRGB(x + dx, 4 + dy, INK);
                    }
                }
            }
            x += 4;
        }
        ImageIO.write(key, "png", file);
        System.out.println("wrote " + file);
    }

    static BufferedImage copy(BufferedImage src) {
        BufferedImage b = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        b.getGraphics().drawImage(src, 0, 0, null);
        return b;
    }
}
