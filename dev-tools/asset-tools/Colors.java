import java.awt.image.BufferedImage; import java.io.File; import javax.imageio.ImageIO; import java.util.*;
public class Colors { public static void main(String[] a) throws Exception { BufferedImage im = ImageIO.read(new File(a[0]));
 int x0=Integer.parseInt(a[1]),y0=Integer.parseInt(a[2]),w=Integer.parseInt(a[3]),h=Integer.parseInt(a[4]);
 Map<Integer,Integer> hist = new TreeMap<>(); for (int y=y0;y<y0+h;y++) for (int x=x0;x<x0+w;x++){int p=im.getRGB(x,y); if((p>>>24)>0) hist.merge(p&0xffffff,1,Integer::sum);}
 for (var e: hist.entrySet()) System.out.printf("%06x:%d ", e.getKey(), e.getValue()); System.out.println(); } }
