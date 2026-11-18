// Tapatsawin Chaieua          6813105
// Thanapon Thanadunpremdet    6813106
// Tanawat Wongwad             6813107
// Natthanon Achachan          6813241
package Project3_6813107;

import Project3_6813107.GamePanel.AllyNpc;
import Project3_6813107.GamePanel.Banner;
import Project3_6813107.GamePanel.ExitDoor;
import Project3_6813107.GamePanel.HazardTrap;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.geom.Arc2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.IntSupplier;

/** Draws the screen overlays on the Swing thread, using the current scene. */
final class GameOverlay {
    private static final int SCALE = GameData.SCALE;
    private static final Color GLASS = new Color(14, 16, 20, 215);
    private static final Color SCRIM = new Color(8, 9, 12, 165);
    private static final Font FONT_HELP = Theme.font(Font.PLAIN, 14);

    record Viewport(int width, int height, double cameraX, double cameraY) {}

    private final Player player;
    private final List<Monster> monsters;
    private final List<DroppedItem> drops;
    private final List<AllyNpc> allies;
    private final List<HazardTrap> traps;
    private final List<ExitDoor> exitDoors;
    private final IntSupplier hostilesRemaining;
    private BufferedImage nightMask;
    private BufferedImage vignette;

    GameOverlay(Player player, List<Monster> monsters, List<DroppedItem> drops, List<AllyNpc> allies,
            List<HazardTrap> traps, List<ExitDoor> exitDoors, IntSupplier hostilesRemaining) {
        this.player = player;
        this.monsters = monsters;
        this.drops = drops;
        this.allies = allies;
        this.traps = traps;
        this.exitDoors = exitDoors;
        this.hostilesRemaining = hostilesRemaining;
    }

    /** Atmospheric Nightfall darkness with warm light pools around the player, campfire, and guard posts. */
    void drawNightLighting(Graphics2D g2, Viewport view, GameMap map, float nightAlpha) {
        if (nightAlpha <= 0.01f) {
            return;
        }
        int vw = view.width(), vh = view.height();
        int mw = vw / SCALE + 2, mh = vh / SCALE + 2;
        if (nightMask == null || nightMask.getWidth() != mw || nightMask.getHeight() != mh) {
            nightMask = new BufferedImage(mw, mh, BufferedImage.TYPE_INT_ARGB);
        }
        Graphics2D mg = nightMask.createGraphics();
        mg.setComposite(AlphaComposite.Src);
        mg.setColor(new Color(8, 12, 26, Math.round(nightAlpha * 130)));
        mg.fillRect(0, 0, mw, mh);

        mg.setComposite(AlphaComposite.DstOut);
        punchLightPool(mg, (float) (player.getX() - view.cameraX()), (float) (player.getY() - view.cameraY()), 120f);
        if (map.hasSafeCamp()) {
            Rectangle camp = map.getCampArea();
            punchLightPool(mg, (float) (camp.getCenterX() - view.cameraX()), (float) (camp.getCenterY() - view.cameraY()), 145f);
        }
        for (ExitDoor ed : exitDoors) {
            punchLightPool(mg, (float) (ed.x - view.cameraX()), (float) (ed.y - view.cameraY()), 74f);
        }
        for (AllyNpc a : allies) {
            punchLightPool(mg, (float) (a.x - view.cameraX()), (float) (a.y - view.cameraY()), 68f);
        }
        for (Point gp : map.getGuardPosts()) {
            punchLightPool(mg, (float) (gp.x - view.cameraX()), (float) (gp.y - view.cameraY()), 68f);
        }
        mg.dispose();
        g2.drawImage(nightMask, 0, 0, vw, vh, null);
    }

    private static void punchLightPool(Graphics2D mg, float cx, float cy, float radius) {
        if (cx + radius < 0 || cy + radius < 0 || cx - radius > 1200 || cy - radius > 900) {
            return;
        }
        mg.setPaint(new RadialGradientPaint(cx, cy, radius, new float[]{0f, 0.55f, 1f},
                new Color[]{new Color(0, 0, 0, 255), new Color(0, 0, 0, 150), new Color(0, 0, 0, 0)}));
        mg.fillOval(Math.round(cx - radius), Math.round(cy - radius), Math.round(radius * 2), Math.round(radius * 2));
    }

    /** Red flash when hurt, green tint when poisoned, pulsing red edge when health is low. */
    void drawDangerTint(Graphics2D g2, Viewport view, int tickCount) {
        int w = view.width(), h = view.height();
        if (player.isHurt()) {
            g2.setColor(new Color(200, 0, 0, 55));
            g2.fillRect(0, 0, w, h);
        } else if (player.isPoisoned()) {
            g2.setColor(new Color(80, 200, 40, 30));
            g2.fillRect(0, 0, w, h);
        }
        boolean lowHp = player.getHp() * 100 < player.getMaxHp() * 30;
        if (lowHp || !player.isAlive()) {
            if (vignette == null || vignette.getWidth() != w || vignette.getHeight() != h) {
                vignette = createVignette(w, h);
            }
            float pulse = player.isAlive() ? 0.55f + 0.45f * (float) Math.abs(Math.sin(tickCount * 0.08)) : 1f;
            Composite old = g2.getComposite();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, pulse));
            g2.drawImage(vignette, 0, 0, null);
            g2.setComposite(old);
        }
    }

    /** Transparent in the middle, dark red at the edges. Made once per panel size. */
    private static BufferedImage createVignette(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        float radius = (float) Math.hypot(w / 2.0, h / 2.0);
        g.setPaint(new RadialGradientPaint(w / 2f, h / 2f, radius, new float[]{0f, 0.55f, 1f},
                new Color[]{new Color(120, 0, 0, 0), new Color(140, 0, 0, 40), new Color(150, 0, 0, 200)},
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    /** The boss's health on a glass panel at the top, with the kit's zombie portrait. */
    void drawBossBar(Graphics2D g2, Viewport view) {
        Monster boss = null;
        for (Monster m : monsters) {
            if (m.isAlive() && m.getType() == GameData.MonsterType.ZOMBIE_BOSS) {
                boss = m;
                break;
            }
        }
        if (boss == null) {
            return;
        }
        Graphics2D g = (Graphics2D) g2.create();
        BufferedImage portrait = Theme.kit("hud/portrait_zombie", 1);
        int barW = 300, panelW = barW + portrait.getWidth() + 44, panelH = 52;
        int x = (view.width() - panelW) / 2, y = 12;
        Theme.paintPanel(g, Theme.Panel.GLASS, x, y, panelW, panelH, 1);
        g.drawImage(portrait, x + 12, y + 10, null);
        int bx = x + portrait.getWidth() + 24;
        Theme.drawPixelTextShadow(g, boss.getType().displayName.toUpperCase(), bx, y + 10, 2, Theme.RED);
        String hp = boss.getHp() + "/" + boss.getMaxHp();
        Theme.drawPixelTextShadow(g, hp, bx + barW - Theme.pixelWidth(hp, 2), y + 10, 2, Theme.TEXT_2);
        Theme.drawBar(g, Theme.Fill.HEALTH, bx, y + 26, barW, boss.getHp() / (double) boss.getMaxHp(), 2);
        g.dispose();
    }

    /** The 56x36 map picture drawn 3x in the kit's minimap frame, with the kit's map markers. */
    void drawMiniMap(Graphics2D g2, Viewport view, GameMap map, int chamber, boolean chamberCleared) {
        BufferedImage mini = map.getMinimap();
        int s = 3, w = mini.getWidth() * s, h = mini.getHeight() * s;
        int left = view.width() - w - 22, top = 22;
        double perPx = s / (double) GameData.TILE;   // mini-map pixels per world pixel
        Graphics2D g = (Graphics2D) g2.create();
        g.setColor(GLASS);
        g.fillRect(left - 4, top - 4, w + 8, h + 8);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f));
        g.drawImage(mini, left, top, w, h, null);
        g.setComposite(AlphaComposite.SrcOver);
        if (map.hasSafeCamp()) {
            Rectangle camp = map.getCampArea();
            drawMarker(g, "map_marker_safe", left + (int) (camp.getCenterX() * perPx), top + (int) (camp.getCenterY() * perPx));
        } else if (chamber > 0) {
            Point tp = map.getTraderPosition();
            int tx = left + (int) (tp.x * perPx), ty = top + (int) (tp.y * perPx);
            g.setColor(new Color(0xF5, 0xC4, 0x51));
            g.fillRect(tx - 3, ty - 3, 6, 6);
            g.setColor(new Color(40, 28, 12));
            g.drawRect(tx - 3, ty - 3, 6, 6);
        }
        for (HazardTrap t : traps) {
            if (t.triggered) {
                continue;
            }
            int tx = left + (int) (t.x * perPx), ty = top + (int) (t.y * perPx);
            if (t.kind == HazardTrap.TrapKind.TOXIC) {
                g.setColor(new Color(145, 235, 60));
            } else if (t.kind == HazardTrap.TrapKind.SHOCK) {
                g.setColor(new Color(95, 225, 255));
            } else if (t.kind == HazardTrap.TrapKind.FLAME_JET) {
                g.setColor(new Color(255, 145, 45));
            } else if (t.kind == HazardTrap.TrapKind.MINE) {
                g.setColor(new Color(255, 70, 70));
            } else {
                g.setColor(new Color(225, 175, 75));
            }
            g.fillRect(tx - 1, ty - 1, 2, 2);
        }
        for (ExitDoor ed : exitDoors) {
            int dx = left + (int) (ed.x * perPx), dy = top + (int) (ed.y * perPx);
            g.setColor(ed.portalColor());
            g.fillRect(dx - 2, dy - 2, 5, 5);
        }
        for (DroppedItem d : drops) {
            if (d.getType() == GameData.ItemType.CHEST) {
                drawMarker(g, "map_marker_loot", left + (int) (d.getX() * perPx), top + (int) (d.getY() * perPx));
            }
        }
        g.setColor(Theme.RED);
        for (Monster m : monsters) {
            if (!m.isAlive()) {
                continue;
            }
            int mx = left + (int) (m.getX() * perPx), my = top + (int) (m.getY() * perPx);
            if (m.getType() == GameData.MonsterType.ZOMBIE_BOSS) {
                drawMarker(g, "map_marker_zombie", mx, my);
            } else {
                g.fillRect(mx - 1, my - 1, 3, 3);   // ordinary monsters as small dots, so a horde stays readable
            }
        }
        g.setColor(new Color(0x88D470));
        for (AllyNpc a : allies) {
            int ax = left + (int) (a.x * perPx), ay = top + (int) (a.y * perPx);
            g.fillRect(ax - 1, ay - 1, 3, 3);
        }
        g.setColor(new Color(240, 230, 192, 110));
        g.drawRect(left + (int) (view.cameraX() * perPx), top + (int) (view.cameraY() * perPx),
                (int) (view.width() / (double) SCALE * perPx), (int) (view.height() / (double) SCALE * perPx));
        drawMarker(g, "map_marker_player", left + (int) (player.getX() * perPx), top + (int) (player.getY() * perPx));
        Theme.drawMinimapFrame(g, left - 6, top - 6, w + 12, h + 12, 2);

        // Roguelite chamber telemetry box right beneath the minimap
        int boxY = top + h + 10;
        Theme.paintPanel(g, Theme.Panel.GLASS, left - 6, boxY, w + 12, 42, 1);
        String zoneText;
        Color zoneColor;
        String intel;
        if (chamber == 0) {
            zoneText = "SAFE CAMP HUB";
            zoneColor = new Color(0x88D470);
            intel = "NPC [B]   GATE [F]";
        } else if (chamberCleared) {
            zoneText = "CH " + chamber + " CLEARED";
            zoneColor = new Color(0x88D470);
            intel = "SHOP [B]  PORTAL [F]";
        } else {
            zoneText = "CHAMBER " + chamber + " / 15";
            zoneColor = Theme.GOLD;
            intel = "FOES " + hostilesRemaining.getAsInt() + "   SHOP [B]";
        }
        Theme.drawPixelTextShadow(g, zoneText, left + 2, boxY + 8, 2, zoneColor);
        Theme.drawPixelTextShadow(g, intel, left + 2, boxY + 24, 2, Theme.TEXT_2);
        g.dispose();
    }

    /** A kit map marker (8 x 8) centred on a mini-map point. */
    private static void drawMarker(Graphics2D g, String name, int cx, int cy) {
        g.drawImage(Theme.kit("hud/" + name, 1), cx - 4, cy - 4, null);
    }

    /** Title in the kit style with a red banner underneath; fades in, stays, fades out. */
    void drawBanner(Graphics2D g2, Viewport view, Banner b, int tickCount) {
        if (b == null) {
            return;
        }
        int age = tickCount - b.startTick;
        if (age < 0 || age > b.length) {
            return;
        }
        float alpha = Math.min(1f, Math.min(age / 15f, (b.length - age) / 30f));
        Graphics2D g = (Graphics2D) g2.create();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, alpha)));
        int cx = view.width() / 2, top = view.height() / 7;
        Theme.drawPixelTextShadow(g, b.title, cx - Theme.pixelWidth(b.title, 5) / 2, top, 5, b.color);
        top += 28;
        Theme.drawBanner(g, b.subtitle.toUpperCase(), cx, top + 12, 2);
        g.dispose();
    }

    /** Player messages such as "Out of ammo - switched to Pistol", on a glass panel near the bottom. */
    void drawHint(Graphics2D g2, Viewport view) {
        String hint = player.getHint();
        if (hint == null) {
            return;
        }
        Graphics2D g = (Graphics2D) g2.create();
        int w = Theme.pixelWidth(hint, 2) + 40, h = 34;
        int x = (view.width() - w) / 2, y = view.height() - h - 18;
        Theme.paintPanel(g, Theme.Panel.GLASS, x, y, w, h, 1);
        Theme.drawPixelTextShadow(g, hint, x + 20, y + 12, 2, Theme.GOLD);
        g.dispose();
    }

    /** A kit crosshair that suits the weapon; a ring around it fills up while the weapon reloads. */
    void drawCrosshair(Graphics2D g2, int mouseX, int mouseY) {
        GameData.WeaponType w = player.getCurrentWeapon();
        String name;
        if (w.melee) {
            name = "cursor_attack";
        } else if (w.pellets > 1 || w.explosive) {
            name = "crosshair_2";
        } else if (w.pierce > 1) {
            name = "crosshair_4";
        } else {
            name = "crosshair_1";
        }
        BufferedImage cross = Theme.kit("cursors/" + name, 2);
        g2.drawImage(cross, mouseX - cross.getWidth() / 2, mouseY - cross.getHeight() / 2, null);
        double reload = player.getReloadProgress();
        if (reload < 1.0 && player.getCooldownTicks(w) > 20) {
            g2.setColor(new Color(240, 230, 192, 170));
            g2.setStroke(new BasicStroke(2f));
            g2.draw(new Arc2D.Double(mouseX - 20, mouseY - 20, 40, 40, 90, -360 * reload, Arc2D.OPEN));
        }
    }

    /** Darkens the game and shows the kit's PAUSED title and the controls as key caps on a glass panel. */
    void drawPauseScreen(Graphics2D g2, Viewport view) {
        int w = view.width(), h = view.height();
        Graphics2D g = Theme.smooth(g2);
        g.setColor(SCRIM);
        g.fillRect(0, 0, w, h);
        String[][] controls = {
            {"Move", "w", "a", "s", "d"}, {"Aim, hold to fire", "mouse_left"},
            {"Dash (Dodge)", "space", "shift"}, {"Gate / Portal", "f"},
            {"Grenade", "mouse_right", "g"}, {"Medkit", "e"},
            {"Switch weapon (1-7)", "mouse_mid"}, {"Camp NPC [Hub]", "b"},
            {"Resume", "p", "esc"}
        };
        BufferedImage title = Theme.kit("titles/paused", 2);
        int rowH = 40, rows = (controls.length + 1) / 2;
        int cardW = 560, cardH = 34 + title.getHeight() + 44 + rows * rowH + 14;
        int x0 = (w - cardW) / 2, y0 = (h - cardH) / 2;
        Theme.paintPanel(g, Theme.Panel.GLASS, x0, y0, cardW, cardH, 2);
        g.drawImage(title, x0 + 34, y0 + 30, null);
        g.setFont(FONT_HELP);
        g.setColor(Theme.TEXT_2);
        int textY = y0 + 30 + title.getHeight() + 24;
        g.drawString("Press P or Esc to keep playing.", x0 + 36, textY);
        int colW = (cardW - 68) / 2;
        for (int i = 0; i < controls.length; i++) {
            int x = x0 + 34 + (i % 2) * colW;
            int y = textY + 16 + (i / 2) * rowH;
            for (int k = 1; k < controls[i].length; k++) {
                BufferedImage key = Theme.keySprite(controls[i][k], 2);
                g.drawImage(key, x, y, null);
                x += key.getWidth() + 4;
            }
            g.setColor(Theme.TEXT);
            g.drawString(controls[i][0], x + 8, y + 21);
        }
        g.dispose();
    }

    /** The kit's "SURVIVED" or "YOU DIED" title with the run's numbers, shown for a moment before the summary. */
    void drawGameOverScreen(Graphics2D g2, Viewport view, boolean won, String stats) {
        int w = view.width(), h = view.height();
        g2.setColor(SCRIM);
        g2.fillRect(0, 0, w, h);
        BufferedImage title = Theme.kit(won ? "titles/survived" : "titles/you_died", 3);
        g2.drawImage(title, (w - title.getWidth()) / 2, h / 2 - title.getHeight() - 10, null);
        Theme.drawPixelTextShadow(g2, stats, (w - Theme.pixelWidth(stats, 3)) / 2, h / 2 + 16, 3, Theme.TEXT);
    }

}
