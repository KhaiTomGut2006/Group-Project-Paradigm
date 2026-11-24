// Tapatsawin Chaieua          6813105
// Thanapon Thanadunpremdet    6813106
// Tanawat Wongwad             6813107
// Natthanon Achachan          6813241
package Project3_6813107;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;

public final class AssetLoader {

    private AssetLoader() {}

    private static final String CLASSPATH_ROOT = "/Project3_6813107/resources/";
    private static final String[] FILE_ROOTS = {"src/main/java/Project3_6813107/resources/", "Project3_6813107/resources/"};

    private static final Map<String, BufferedImage> IMAGE_CACHE = new HashMap<>();
    private static final Map<String, BufferedImage[]> FRAMES_CACHE = new HashMap<>();
    private static final Set<String> MISSING = new HashSet<>();

    private static InputStream open(String relPath) throws IOException {
        InputStream in = AssetLoader.class.getResourceAsStream(CLASSPATH_ROOT + relPath);
        if (in != null) return in;
        for (String root : FILE_ROOTS) {
            File f = new File(root + relPath);
            if (f.isFile()) return new FileInputStream(f);
        }
        throw new IOException("resource not found: " + relPath);
    }

    public static synchronized BufferedImage loadImage(String relPath) {
        BufferedImage cached = IMAGE_CACHE.get(relPath);
        if (cached != null) return cached;
        BufferedImage img;
        try (InputStream in = open(relPath)) {
            BufferedImage raw = ImageIO.read(in);
            if (raw == null) throw new IOException("not an image");
            img = toArgb(raw);
        } catch (IOException | RuntimeException ex) {
            System.err.println("AssetLoader: cannot load image " + relPath + " (" + ex.getMessage() + ")");
            img = makeFallback(32, 32);
            MISSING.add(relPath);
        }
        IMAGE_CACHE.put(relPath, img);
        return img;
    }

    public static synchronized BufferedImage[] loadStrip(String relPath, int frameCount) {
        String key = relPath + "#strip" + frameCount;
        BufferedImage[] cached = FRAMES_CACHE.get(key);
        if (cached != null) return cached;
        BufferedImage sheet = loadImage(relPath);
        int count = Math.max(1, frameCount);
        int frameW = sheet.getWidth() / count;
        BufferedImage[] frames = new BufferedImage[count];
        boolean usable = frameW > 0 && !MISSING.contains(relPath);
        for (int i = 0; i < count; i++) {
            frames[i] = usable ? sheet.getSubimage(i * frameW, 0, frameW, sheet.getHeight()) : makeFallback(32, 32);
        }
        FRAMES_CACHE.put(key, frames);
        return frames;
    }

    public static synchronized BufferedImage[] loadGrid(String relPath, int cellW, int cellH) {
        String key = relPath + "#grid" + cellW + "x" + cellH;
        BufferedImage[] cached = FRAMES_CACHE.get(key);
        if (cached != null) return cached;
        BufferedImage sheet = loadImage(relPath);
        int cols = Math.max(1, sheet.getWidth() / Math.max(1, cellW));
        int rows = Math.max(1, sheet.getHeight() / Math.max(1, cellH));
        BufferedImage[] cells = new BufferedImage[cols * rows];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                boolean fits = (c + 1) * cellW <= sheet.getWidth() && (r + 1) * cellH <= sheet.getHeight();
                cells[r * cols + c] = fits ? sheet.getSubimage(c * cellW, r * cellH, cellW, cellH) : makeFallback(cellW, cellH);
            }
        }
        FRAMES_CACHE.put(key, cells);
        return cells;
    }

    public static BufferedImage subImage(String relPath, int x, int y, int w, int h) {
        BufferedImage sheet = loadImage(relPath);
        if (w <= 0 || h <= 0 || x < 0 || y < 0 || x + w > sheet.getWidth() || y + h > sheet.getHeight()) {
            System.err.println("AssetLoader: rectangle outside " + relPath + ": " + x + "," + y + " " + w + "x" + h);
            return makeFallback(Math.max(1, w), Math.max(1, h));
        }
        return sheet.getSubimage(x, y, w, h);
    }

    private static BufferedImage makeFallback(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, ((x / 4 + y / 4) % 2 == 0) ? 0xFFFF00FF : 0xFF000000);
            }
        }
        return img;
    }

    private static BufferedImage toArgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_ARGB) return src;
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    public static BufferedImage flipHorizontal(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) out.setRGB(w - 1 - x, y, src.getRGB(x, y));
        }
        return out;
    }

    public static BufferedImage flipVertical(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) out.setRGB(x, h - 1 - y, src.getRGB(x, y));
        }
        return out;
    }

    public static BufferedImage rotate90(BufferedImage src, boolean clockwise) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(h, w, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out.setRGB(clockwise ? h - 1 - y : y, clockwise ? x : w - 1 - x, src.getRGB(x, y));
            }
        }
        return out;
    }

    public static BufferedImage[] getItemFrames(GameData.ItemType type) {
        BufferedImage sheet = loadImage(type.spritePath);
        return loadStrip(type.spritePath, Math.max(1, sheet.getWidth() / type.frameW));
    }

    public static BufferedImage getItemIcon(GameData.ItemType type) {
        return getItemFrames(type)[0];
    }

    public static BufferedImage getWeaponIcon(GameData.WeaponType w) {
        return loadGrid("weapons/weapons_sheet.png", 32, 32)[w.iconRow * 6 + w.iconCol];
    }

    public static BufferedImage getProjectileImage(GameData.Projectile p) {
        return p.sheetX < 0 ? null : subImage("weapons/projectiles.png", p.sheetX, p.sheetY, 16, 16);
    }

    public static BufferedImage[] getExplosionFrames() { return loadStrip("ui/explosion.png", 7); }
    public static BufferedImage[] getImpactFrames() { return loadStrip("ui/bullet_impact.png", 5); }
    public static BufferedImage[] getAlertFrames() { return loadStrip("ui/popup_alert.png", 5); }

    private static volatile boolean soundEnabled = true;
    private static volatile boolean musicEnabled = true;
    private static volatile int volumePercent = 70;
    private static final int MAX_PLAYING_SOUNDS = 10;
    private static final AtomicInteger PLAYING = new AtomicInteger();
    private record Sound(byte[] samples, AudioFormat format) {}

    private static final Map<String, Sound> SOUNDS = new HashMap<>();
    private static final Map<String, Long> LAST_PLAYED = new HashMap<>();
    private static final Object MUSIC_LOCK = new Object();
    private static Clip musicClip;
    private static String musicName;
    private static boolean audioBroken;

    private static synchronized Sound loadSound(String name) {
        Sound cached = SOUNDS.get(name);
        if (cached != null) return cached;
        try (InputStream raw = open("sounds/" + name + ".wav");
             AudioInputStream in = AudioSystem.getAudioInputStream(new BufferedInputStream(raw))) {
            Sound sound = new Sound(in.readAllBytes(), in.getFormat());
            SOUNDS.put(name, sound);
            return sound;
        } catch (Exception ex) {
            return null;
        }
    }

    private static Clip createClip(String name) {
        Sound sound = audioBroken ? null : loadSound(name);
        if (sound == null) return null;
        try {
            Clip clip = AudioSystem.getClip();
            clip.open(sound.format(), sound.samples(), 0, sound.samples().length);
            applyVolume(clip);
            return clip;
        } catch (IllegalArgumentException ex) {
            audioBroken = true;
            System.err.println("AssetLoader: audio not available (" + ex.getMessage() + "), sound disabled");
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    private static void applyVolume(Clip clip) {
        if (clip == null || !clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) return;
        FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        float db = (float) (20.0 * Math.log10(Math.max(0.0001, volumePercent / 100.0)));
        gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db)));
    }

    public static void playSound(String name) {
        if (!soundEnabled || audioBroken || name == null || volumePercent <= 0) return;
        synchronized (LAST_PLAYED) {
            long now = System.nanoTime();
            Long last = LAST_PLAYED.get(name);
            if (last != null && now - last < 40_000_000L) return;
            LAST_PLAYED.put(name, now);
        }
        if (PLAYING.incrementAndGet() > MAX_PLAYING_SOUNDS) {
            PLAYING.decrementAndGet();
            return;
        }
        Thread t = new Thread(() -> playNow(name), "sfx-" + name);
        t.setDaemon(true);
        t.start();
    }

    private static void playNow(String name) {
        Clip clip = createClip(name);
        if (clip == null) {
            PLAYING.decrementAndGet();
            return;
        }
        clip.addLineListener(event -> {
            if (event.getType() == LineEvent.Type.STOP) {
                event.getLine().close();
                PLAYING.decrementAndGet();
            }
        });
        clip.start();
    }

    public static void playMusic(String name) {
        synchronized (MUSIC_LOCK) {
            musicName = name;
            stopMusicClip();
            if (!musicEnabled || name == null) return;
            musicClip = createClip(name);
            if (musicClip != null) musicClip.loop(Clip.LOOP_CONTINUOUSLY);
        }
    }

    public static void stopMusic() {
        synchronized (MUSIC_LOCK) {
            musicName = null;
            stopMusicClip();
        }
    }

    private static void stopMusicClip() {
        if (musicClip != null) {
            musicClip.stop();
            musicClip.close();
            musicClip = null;
        }
    }

    public static void setSoundEnabled(boolean b) { soundEnabled = b; }

    public static void setMusicEnabled(boolean b) {
        synchronized (MUSIC_LOCK) {
            musicEnabled = b;
            String wanted = musicName;
            if (!b) stopMusicClip();
            else if (wanted != null && musicClip == null) playMusic(wanted);
        }
    }

    public static void setVolume(int percent0to100) {
        volumePercent = Math.max(0, Math.min(100, percent0to100));
        synchronized (MUSIC_LOCK) { applyVolume(musicClip); }
    }

    public static int getVolume() { return volumePercent; }

    public static synchronized boolean canLoadSound(String name) {
        Sound sound = loadSound(name);
        return sound != null && sound.samples().length > 0;
    }
}
