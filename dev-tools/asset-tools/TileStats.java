import java.awt.image.BufferedImage; import java.io.File; import javax.imageio.ImageIO; import java.util.*;
public class TileStats { public static void main(String[] a) throws Exception { BufferedImage im = ImageIO.read(new File(a[0]));
 for (int ty = 0; ty + 32 <= im.getHeight(); ty += 32) for (int tx = 0; tx + 32 <= im.getWidth(); tx += 32) {
  Map<Integer,Integer> hist = new HashMap<>(); int opaque=0;
  for (int y=0;y<32;y++) for (int x=0;x<32;x++){int p=im.getRGB(tx+x,ty+y); if((p>>>24)==255){opaque++; hist.merge(p&0xffffff,1,Integer::sum);} }
  if (opaque < 1024) continue;
  int best=0,bc=0; for (var e: hist.entrySet()) if (e.getValue()>bc){bc=e.getValue();best=e.getKey();}
  System.out.printf("(%d,%d) colors=%d dominant=%06x %.0f%%%n", tx, ty, hist.size(), best, 100.0*bc/1024);
 } } }
