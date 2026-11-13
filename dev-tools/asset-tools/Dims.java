import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Prints size of each PNG and, for strips, the occupied column ranges per frame-size guess. */
public class Dims {
    public static void main(String[] a) throws Exception {
        for (String p : a) {
            BufferedImage im = ImageIO.read(new File(p));
            int w = im.getWidth(), h = im.getHeight();
            StringBuilder sb = new StringBuilder();
            // per-cell occupancy using cell = h (square frames)
            int cell = Integer.parseInt(System.getProperty("cell", "" + h));
            int n = w / cell;
            for (int f = 0; f < n; f++) {
                int cnt = 0;
                for (int y = 0; y < Math.min(h, cell); y++)
                    for (int x = f * cell; x < (f + 1) * cell; x++)
                        if ((im.getRGB(x, y) >>> 24) > 0) cnt++;
                sb.append(cnt).append(' ');
            }
            System.out.println(new File(p).getName() + " " + w + "x" + h + " cell=" + cell + " n=" + n + (w % cell != 0 ? " REM=" + (w % cell) : "") + " occ=[" + sb.toString().trim() + "]");
        }
    }
}
