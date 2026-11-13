import java.awt.image.BufferedImage; import java.io.File; import javax.imageio.ImageIO;
public class Px { public static void main(String[] a) throws Exception { BufferedImage im = ImageIO.read(new File(a[0]));
 int y = Integer.parseInt(a[1]); int x0 = Integer.parseInt(a[2]), x1 = Integer.parseInt(a[3]); boolean col = a.length > 4;
 for (int i = x0; i < x1; i++) { int c = col ? im.getRGB(y, i) : im.getRGB(i, y); System.out.printf("%d:%06x/%d ", i, c & 0xffffff, c>>>24); } System.out.println(); } }
