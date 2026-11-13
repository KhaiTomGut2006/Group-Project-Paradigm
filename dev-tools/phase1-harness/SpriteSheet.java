import Project3_6813107.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

/** Contact sheet of every sliced animation + composite renders (player with each weapon, monsters, props). */
public class SpriteSheet {
    static final int S = 3;               // zoom
    static List<Object[]> rows = new ArrayList<>();   // {label, BufferedImage[]}

    static void row(String label, BufferedImage[] frames) { rows.add(new Object[]{label, frames}); }

    public static void main(String[] args) throws Exception {
        row("player_idle x6", AssetLoader.loadStrip("characters/player_idle.png", 6));
        row("player_run x8", AssetLoader.loadStrip("characters/player_run.png", 8));
        row("player_hit x3", AssetLoader.loadStrip("characters/player_hit.png", 3));
        row("player_death x8", AssetLoader.loadStrip("characters/player_death.png", 8));
        for (GameData.MonsterType m : GameData.MonsterType.values()) {
            row(m + " run x8", AssetLoader.loadStrip(m.runStrip(), 8));
            row(m + " hit x3", AssetLoader.loadStrip(m.hitStrip(), 3));
            row(m + " death x8", AssetLoader.loadStrip(m.deathStrip(), 8));
        }
        row("bandit_idle x6", AssetLoader.loadStrip("characters/bandit_idle.png", 6));
        row("npc_idle x6", AssetLoader.loadStrip("characters/npc_idle.png", 6));
        row("npc2_idle x6", AssetLoader.loadStrip("characters/npc2_idle.png", 6));
        List<BufferedImage> icons = new ArrayList<>();
        for (GameData.WeaponType w : GameData.WeaponType.values()) icons.add(AssetLoader.getWeaponIcon(w));
        icons.add(AssetLoader.getCrosshair());
        row("weapon icons (8) + crosshair", icons.toArray(new BufferedImage[0]));
        for (GameData.WeaponType w : GameData.WeaponType.values()) {
            row(w + " flicker x7", AssetLoader.loadStrip(w.flickerStrip(), 7));
            if (w.shootStrip != null) row(w + " shoot x" + w.shootFrames, AssetLoader.loadStrip(w.shootStrip, w.shootFrames));
        }
        for (GameData.ItemType t : GameData.ItemType.values()) row("item " + t + " x" + AssetLoader.getItemFrames(t).length, AssetLoader.getItemFrames(t));
        List<BufferedImage> proj = new ArrayList<>();
        for (GameData.Projectile p : GameData.Projectile.values()) if (AssetLoader.getProjectileImage(p) != null) proj.add(AssetLoader.getProjectileImage(p));
        row("projectiles (small,big,sniper,rocket,grenade,enemy,toxic)", proj.toArray(new BufferedImage[0]));
        row("explosion x7 (48)", AssetLoader.getExplosionFrames());
        row("bullet impact x5 (20)", AssetLoader.getImpactFrames());
        row("alert popup x5 (20)", AssetLoader.getAlertFrames());
        row("particles 10 cells (16)", AssetLoader.getParticleFrames());
        row("flame barrel x7 (64)", AssetLoader.loadStrip("tiles/flame_barrel.png", 7));
        row("flag x7 (64)", AssetLoader.loadStrip("tiles/flag.png", 7));

        // composites: player holding every weapon at 8 aim angles
        List<BufferedImage> comps = new ArrayList<>();
        for (GameData.WeaponType w : GameData.WeaponType.values()) {
            BufferedImage strip = new BufferedImage(8 * 48, 48, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = strip.createGraphics();
            for (int i = 0; i < 8; i++) {
                GameData.GameSettings st = new GameData.GameSettings();
                st.startWeapon = w;
                Player p = new Player(st, i * 48 + 24, 28);
                double a = i * Math.PI / 4;
                p.setAim(p.getX() + Math.cos(a) * 100, p.getY() + Math.sin(a) * 100);
                for (int k = 0; k < 40; k++) p.update();   // let the switch flicker finish
                p.draw(g);
            }
            g.dispose();
            comps.add(strip);
            row("player + " + w + " aim 0..315 deg", new BufferedImage[]{strip});
        }
        // monsters as drawn in game (boss scaled), with hit flash and hp bar
        BufferedImage mon = new BufferedImage(5 * 70, 70, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = mon.createGraphics();
        int i = 0;
        Player dummy = new Player(new GameData.GameSettings(), 1000, 1000);
        for (GameData.MonsterType mt : GameData.MonsterType.values()) {
            Monster m = new Monster(mt, i * 70 + 35, 50, null, dummy, 1.0);
            m.update();
            m.takeHit(1, 0, 0);
            m.draw(g);
            i++;
        }
        g.dispose();
        row("monsters in game (hit flash + hp bar)", new BufferedImage[]{mon});

        // render
        int width = 0, height = 0;
        for (Object[] r : rows) {
            BufferedImage[] f = (BufferedImage[]) r[1];
            int w = 260;
            int h = 0;
            for (BufferedImage b : f) { w += b.getWidth() * S + 6; h = Math.max(h, b.getHeight() * S); }
            width = Math.max(width, w);
            height += h + 22;
        }
        BufferedImage out = new BufferedImage(width + 10, height + 10, BufferedImage.TYPE_INT_RGB);
        g = out.createGraphics();
        g.setColor(new Color(70, 74, 82));
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        int y = 6;
        for (Object[] r : rows) {
            BufferedImage[] f = (BufferedImage[]) r[1];
            int h = 0;
            for (BufferedImage b : f) h = Math.max(h, b.getHeight() * S);
            g.setColor(Color.WHITE);
            g.drawString((String) r[0], 6, y + 14);
            int x = 260;
            for (int k = 0; k < f.length; k++) {
                BufferedImage b = f[k];
                g.setColor(new Color(95, 100, 110));
                g.fillRect(x, y + 16, b.getWidth() * S, b.getHeight() * S);
                g.drawImage(b, x, y + 16, b.getWidth() * S, b.getHeight() * S, null);
                g.setColor(new Color(255, 230, 0));
                g.drawString("" + k, x + 2, y + 14);
                x += b.getWidth() * S + 6;
            }
            y += h + 22;
        }
        g.dispose();
        File dir = new File(args[0]);
        dir.mkdirs();
        ImageIO.write(out, "png", new File(dir, "sprites.png"));
        System.out.println("sprites.png " + out.getWidth() + "x" + out.getHeight() + " rows=" + rows.size());
    }
}
