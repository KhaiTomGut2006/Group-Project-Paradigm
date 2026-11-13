import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Zoom in out x y w h scale grid  -> upscaled crop with grid lines every 'grid' px and coordinate labels. */
public class Zoom {
    public static void main(String[] a) throws Exception {
        BufferedImage im = ImageIO.read(new File(a[0]));
        int x0 = Integer.parseInt(a[2]), y0 = Integer.parseInt(a[3]);
        int w = Integer.parseInt(a[4]), h = Integer.parseInt(a[5]);
        if (w <= 0) w = im.getWidth() - x0;
        if (h <= 0) h = im.getHeight() - y0;
        int s = Integer.parseInt(a[6]);
        int grid = Integer.parseInt(a[7]);
        int minor = a.length > 8 ? Integer.parseInt(a[8]) : 0;
        int m = 28;
        BufferedImage out = new BufferedImage(w * s + m, h * s + m, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(new Color(60, 60, 70));
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        // checker background
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int c = ((x + y) & 1) == 0 ? 0x2a2a35 : 0x33333f;
                g.setColor(new Color(c));
                g.fillRect(m + x * s, m + y * s, s, s);
            }
        g.drawImage(im.getSubimage(x0, y0, w, h), m, m, w * s, h * s, null);
        if (minor > 0) {
            g.setColor(new Color(255, 255, 255, 40));
            for (int x = 0; x <= w; x += minor) g.drawLine(m + x * s, m, m + x * s, m + h * s);
            for (int y = 0; y <= h; y += minor) g.drawLine(m, m + y * s, m + w * s, m + y * s);
        }
        g.setColor(new Color(255, 0, 255, 170));
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        for (int x = 0; x <= w; x++) {
            if ((x0 + x) % grid != 0) continue;
            g.drawLine(m + x * s, m, m + x * s, m + h * s);
            g.setColor(Color.YELLOW);
            g.drawString("" + (x0 + x), m + x * s + 1, 10);
            g.setColor(new Color(255, 0, 255, 170));
        }
        for (int y = 0; y <= h; y++) {
            if ((y0 + y) % grid != 0) continue;
            g.drawLine(m, m + y * s, m + w * s, m + y * s);
            g.setColor(Color.YELLOW);
            g.drawString("" + (y0 + y), 1, m + y * s + 10);
            g.setColor(new Color(255, 0, 255, 170));
        }
        g.dispose();
        ImageIO.write(out, "png", new File(a[1]));
    }
}
