import Project3_XXX.GameData;
import Project3_XXX.GameMap;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/** Renders every map in full: ground + props in depth order, and a debug copy with solids/spawns. */
public class MapRender {
    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        for (GameData.MapType type : GameData.MapType.values()) {
            if (args.length > 1 && !type.name().equals(args[1])) continue;
            long t0 = System.nanoTime();
            GameMap map = new GameMap(type, type.seed);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            BufferedImage img = new BufferedImage(map.getWidthPx(), map.getHeightPx(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            Rectangle all = new Rectangle(0, 0, map.getWidthPx(), map.getHeightPx());
            map.drawGround(g, all);
            List<GameMap.Drawable> list = new ArrayList<>(map.getPropsInView(all));
            list.sort(GameMap.DEPTH_ORDER);
            for (GameMap.Drawable d : list) d.draw(g);
            g.dispose();
            String name = type.name().toLowerCase();
            ImageIO.write(img, "png", new File(out, name + ".png"));
            BufferedImage dbg = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            g = dbg.createGraphics();
            g.drawImage(img, 0, 0, null);
            g.setColor(new Color(255, 0, 0, 220));
            for (Rectangle r : map.getSolidRects()) g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
            g.setColor(new Color(0, 255, 0, 200));
            for (Point p : map.getSpawnPoints()) g.fillOval(p.x - 3, p.y - 3, 6, 6);
            g.setColor(Color.BLUE);
            Rectangle camp = map.getCampArea();
            g.drawRect(camp.x, camp.y, camp.width, camp.height);
            Point s = map.getPlayerSpawn();
            g.setColor(Color.CYAN);
            g.fillRect(s.x - 5, s.y - 5, 10, 10);
            // unwalkable tiles shaded
            g.setColor(new Color(0, 0, 0, 70));
            for (int r = 0; r < GameMap.ROWS; r++)
                for (int c = 0; c < GameMap.COLS; c++)
                    if (!map.isWalkableTile(c, r)) g.fillRect(c * 32 + 12, r * 32 + 12, 8, 8);
            g.dispose();
            ImageIO.write(dbg, "png", new File(out, name + "_solids.png"));
            System.out.println(type + ": built in " + ms + " ms, props=" + map.getProps().size() + ", solids=" + map.getSolidRects().size() + ", spawns=" + map.getSpawnPoints().size());
        }
    }
}
