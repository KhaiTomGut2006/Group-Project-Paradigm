import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;

/** Blobs file [x y w h] [mergeDist] -> bounding boxes of 8-connected non-transparent regions (merged if within mergeDist). */
public class Blobs {
    public static void main(String[] a) throws Exception {
        BufferedImage im = ImageIO.read(new File(a[0]));
        int x0 = 0, y0 = 0, w = im.getWidth(), h = im.getHeight();
        int merge = 0;
        if (a.length >= 5) {
            x0 = Integer.parseInt(a[1]); y0 = Integer.parseInt(a[2]);
            w = Integer.parseInt(a[3]); h = Integer.parseInt(a[4]);
        }
        if (a.length >= 6) merge = Integer.parseInt(a[5]);
        boolean[][] seen = new boolean[h][w];
        List<int[]> boxes = new ArrayList<>();
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                if (seen[y][x] || (im.getRGB(x0 + x, y0 + y) >>> 24) == 0) continue;
                int minx = x, maxx = x, miny = y, maxy = y, cnt = 0;
                ArrayDeque<int[]> q = new ArrayDeque<>();
                q.add(new int[]{x, y}); seen[y][x] = true;
                while (!q.isEmpty()) {
                    int[] p = q.poll(); cnt++;
                    minx = Math.min(minx, p[0]); maxx = Math.max(maxx, p[0]);
                    miny = Math.min(miny, p[1]); maxy = Math.max(maxy, p[1]);
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = p[0] + dx, ny = p[1] + dy;
                            if (nx < 0 || ny < 0 || nx >= w || ny >= h || seen[ny][nx]) continue;
                            if ((im.getRGB(x0 + nx, y0 + ny) >>> 24) == 0) continue;
                            seen[ny][nx] = true; q.add(new int[]{nx, ny});
                        }
                }
                boxes.add(new int[]{minx + x0, miny + y0, maxx + x0, maxy + y0, cnt});
            }
        // merge close boxes
        boolean changed = true;
        while (changed && merge > 0) {
            changed = false;
            outer:
            for (int i = 0; i < boxes.size(); i++)
                for (int j = i + 1; j < boxes.size(); j++) {
                    int[] p = boxes.get(i), q = boxes.get(j);
                    Rectangle r1 = new Rectangle(p[0] - merge, p[1] - merge, p[2] - p[0] + 1 + 2 * merge, p[3] - p[1] + 1 + 2 * merge);
                    Rectangle r2 = new Rectangle(q[0], q[1], q[2] - q[0] + 1, q[3] - q[1] + 1);
                    if (r1.intersects(r2)) {
                        p[0] = Math.min(p[0], q[0]); p[1] = Math.min(p[1], q[1]);
                        p[2] = Math.max(p[2], q[2]); p[3] = Math.max(p[3], q[3]); p[4] += q[4];
                        boxes.remove(j); changed = true; break outer;
                    }
                }
        }
        boxes.sort((p, q) -> p[1] != q[1] ? Integer.compare(p[1] / 8, q[1] / 8) != 0 ? Integer.compare(p[1] / 8, q[1] / 8) : Integer.compare(p[0], q[0]) : Integer.compare(p[0], q[0]));
        for (int[] b : boxes)
            if (b[4] >= 3)
                System.out.printf("x=%d y=%d w=%d h=%d  (px=%d)%n", b[0], b[1], b[2] - b[0] + 1, b[3] - b[1] + 1, b[4]);
    }
}
