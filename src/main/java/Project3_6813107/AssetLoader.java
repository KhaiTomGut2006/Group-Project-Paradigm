// Tapatsawin Chaieua          6813105
// Thanapon Thanadunpremdet    6813106
// Tanawat Wongwad             6813107
// Natthanon Achachan          6813241
package Project3_6813107;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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

/**
 * Loads every picture and sound of the game. All methods are static and crash-proof:
 * a missing image becomes a magenta/black checker (so it is easy to spot), a missing or
 * unplayable sound is skipped. Images are cached, so loading the same file twice is free.
 *
 * Paths are relative to the resources folder, e.g. "characters/player_run.png".
 */
public final class AssetLoader {

    private AssetLoader() {
        // only static members
    }

    private static final String CLASSPATH_ROOT = "/Project3_6813107/resources/";
    private static final String[] FILE_ROOTS = {"src/main/java/Project3_6813107/resources/", "Project3_6813107/resources/"};

    private static final Map<String, BufferedImage> IMAGE_CACHE = new HashMap<>();
    private static final Map<String, BufferedImage[]> FRAMES_CACHE = new HashMap<>();
    private static final Set<String> MISSING = new HashSet<>();   // images that could not be loaded

    // ------------------------------------------------------------------ file lookup
    /** Opens a resource: classpath first, then the source folder, then a folder next to the working dir. */
    private static InputStream open(String relPath) throws IOException {
        InputStream in = AssetLoader.class.getResourceAsStream(CLASSPATH_ROOT + relPath);
        if (in != null) {
            return in;
        }
        for (String root : FILE_ROOTS) {
            File f = new File(root + relPath);
            if (f.isFile()) {
                return new FileInputStream(f);
            }
        }
        throw new IOException("resource not found: " + relPath);
    }

    // ------------------------------------------------------------------ images
    /** Loads (and caches) a whole PNG. Never returns null. */
    public static synchronized BufferedImage loadImage(String relPath) {
        BufferedImage cached = IMAGE_CACHE.get(relPath);
        if (cached != null) {
            return cached;
        }
        BufferedImage img;
        try (InputStream in = open(relPath)) {
            BufferedImage raw = ImageIO.read(in);
            if (raw == null) {
                throw new IOException("not an image");
            }
            img = toArgb(raw);
        } catch (IOException | RuntimeException ex) {
            System.err.println("AssetLoader: cannot load image " + relPath + " (" + ex.getMessage() + ")");
            img = makeFallback(32, 32);
            MISSING.add(relPath);
        }
        IMAGE_CACHE.put(relPath, img);
        return img;
    }

    /** Cuts a horizontal strip into frameCount equal frames. */
    public static synchronized BufferedImage[] loadStrip(String relPath, int frameCount) {
        String key = relPath + "#strip" + frameCount;
        BufferedImage[] cached = FRAMES_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        BufferedImage sheet = loadImage(relPath);
        int count = Math.max(1, frameCount);
        int frameW = sheet.getWidth() / count;
        BufferedImage[] frames = new BufferedImage[count];
        for (int i = 0; i < count; i++) {
            // a missing sheet gives full-size checker frames instead of thin slivers of the fallback
            boolean usable = frameW > 0 && !MISSING.contains(relPath);
            frames[i] = usable ? sheet.getSubimage(i * frameW, 0, frameW, sheet.getHeight()) : makeFallback(32, 32);
        }
        FRAMES_CACHE.put(key, frames);
        return frames;
    }

    /** Cuts a sheet into cells of cellW x cellH, row by row (row-major). */
    public static synchronized BufferedImage[] loadGrid(String relPath, int cellW, int cellH) {
        String key = relPath + "#grid" + cellW + "x" + cellH;
        BufferedImage[] cached = FRAMES_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
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

    /** One rectangle of a sheet (used for map props that are not on a clean grid). */
    public static BufferedImage subImage(String relPath, int x, int y, int w, int h) {
        BufferedImage sheet = loadImage(relPath);
        if (w <= 0 || h <= 0 || x < 0 || y < 0 || x + w > sheet.getWidth() || y + h > sheet.getHeight()) {
            System.err.println("AssetLoader: rectangle outside " + relPath + ": " + x + "," + y + " " + w + "x" + h);
            return makeFallback(Math.max(1, w), Math.max(1, h));
        }
        return sheet.getSubimage(x, y, w, h);
    }

    /** Magenta/black checkerboard: impossible to miss, so broken art is found quickly. */
    private static BufferedImage makeFallback(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean magenta = ((x / 4) + (y / 4)) % 2 == 0;
                img.setRGB(x, y, magenta ? 0xFFFF00FF : 0xFF000000);
            }
        }
        return img;
    }

    /** Converts any decoded PNG (indexed, grey...) to ARGB so drawing and pixel reads behave the same. */
    private static BufferedImage toArgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_ARGB) {
            return src;
        }
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    // ------------------------------------------------------------------ image transforms (used when building maps)
    /** Mirror left-right. */
    public static BufferedImage flipHorizontal(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out.setRGB(w - 1 - x, y, src.getRGB(x, y));
            }
        }
        return out;
    }

    /** Mirror top-bottom. */
    public static BufferedImage flipVertical(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out.setRGB(x, h - 1 - y, src.getRGB(x, y));
            }
        }
        return out;
    }

    /** Quarter turn. clockwise=true turns the top edge to the right side. */
    public static BufferedImage rotate90(BufferedImage src, boolean clockwise) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(h, w, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (clockwise) {
                    out.setRGB(h - 1 - y, x, src.getRGB(x, y));
                } else {
                    out.setRGB(y, w - 1 - x, src.getRGB(x, y));
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ ready-made sprites for the HUD, dialog and effects
    /** The 7-frame glinting animation of an item (CHEST: the 6-frame opening animation, 32x16). */
    public static BufferedImage[] getItemFrames(GameData.ItemType type) {
        BufferedImage sheet = loadImage(type.spritePath);
        return loadStrip(type.spritePath, Math.max(1, sheet.getWidth() / type.frameW));
    }

    /** A still 16x16 (chest: 32x16) icon of an item for tables and the HUD. */
    public static BufferedImage getItemIcon(GameData.ItemType type) {
        return getItemFrames(type)[0];
    }

    /** 32x32 picture of a weapon from the weapons sheet (pointing right). */
    public static BufferedImage getWeaponIcon(GameData.WeaponType w) {
        BufferedImage[] cells = loadGrid("weapons/weapons_sheet.png", 32, 32);
        return cells[w.iconRow * 6 + w.iconCol];
    }

    /** The 32x32 crosshair used as the mouse cursor (cell 4 of row 0 in the weapons sheet). */
    public static BufferedImage getCrosshair() {
        return loadGrid("weapons/weapons_sheet.png", 32, 32)[4];
    }

    /** 16x16 projectile sprite pointing right, or null for the invisible melee slash. */
    public static BufferedImage getProjectileImage(GameData.Projectile p) {
        if (p.sheetX < 0) {
            return null;
        }
        return subImage("weapons/projectiles.png", p.sheetX, p.sheetY, 16, 16);
    }

    /** Explosion: 7 frames of 48x48 (white flash then grey smoke). */
    public static BufferedImage[] getExplosionFrames() {
        return loadStrip("ui/explosion.png", 7);
    }

    /** Bullet impact spark: 5 frames of 20x20. */
    public static BufferedImage[] getImpactFrames() {
        return loadStrip("ui/bullet_impact.png", 5);
    }

    /** "!" speech bubble that pops up: 5 frames of 20x20. */
    public static BufferedImage[] getAlertFrames() {
        return loadStrip("ui/popup_alert.png", 5);
    }

    /** Small particles, 16x16 cells: 0-2 dust puffs, 3-4 dark smoke, 5-6 dust clods, 7-8 smoke, 9 spark. */
    public static BufferedImage[] getParticleFrames() {
        return loadGrid("ui/particles.png", 16, 16);
    }

    // ------------------------------------------------------------------ sound
    private static volatile boolean soundEnabled = true;
    private static volatile boolean musicEnabled = true;
    private static volatile int volumePercent = 70;
    private static final int MAX_PLAYING_SOUNDS = 10;   // more at once only turns into noise
    private static final AtomicInteger PLAYING = new AtomicInteger();
    private static final Map<String, byte[]> SOUND_DATA = new HashMap<>();
    private static final Map<String, AudioFormat> SOUND_FORMAT = new HashMap<>();
    private static final Map<String, Long> LAST_PLAYED = new HashMap<>();
    private static final Object MUSIC_LOCK = new Object();
    private static Clip musicClip;
    private static String musicName;
    private static boolean audioBroken;   // set after the first hard failure so we stop retrying

    /** Reads a .wav into memory once; returns false if it cannot be used. */
    private static synchronized boolean loadSound(String name) {
        if (SOUND_DATA.containsKey(name)) {
            return true;
        }
        try (InputStream raw = open("sounds/" + name + ".wav");
             AudioInputStream in = AudioSystem.getAudioInputStream(new java.io.BufferedInputStream(raw))) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                bytes.write(buffer, 0, read);
            }
            SOUND_DATA.put(name, bytes.toByteArray());
            SOUND_FORMAT.put(name, in.getFormat());
            return true;
        } catch (Exception ex) {
            // missing file, unsupported format ... the game simply stays silent for this sound
            return false;
        }
    }

    /** Opens a Clip filled with the sound's samples, or returns null when audio is not available. */
    private static Clip createClip(String name) {
        if (audioBroken || !loadSound(name)) {
            return null;
        }
        byte[] data;
        AudioFormat format;
        synchronized (AssetLoader.class) {
            data = SOUND_DATA.get(name);
            format = SOUND_FORMAT.get(name);
        }
        try {
            Clip clip = AudioSystem.getClip();
            clip.open(format, data, 0, data.length);
            applyVolume(clip);
            return clip;
        } catch (IllegalArgumentException ex) {
            // no mixer on this machine supports clips (e.g. a server without a sound card)
            audioBroken = true;
            System.err.println("AssetLoader: audio not available (" + ex.getMessage() + "), sound disabled");
            return null;
        } catch (Exception ex) {
            // busy device or similar: skip this one sound, try again next time
            return null;
        }
    }

    /** Converts 0..100 % to decibels on the clip's gain control (if the clip has one). */
    private static void applyVolume(Clip clip) {
        if (clip == null || !clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return;
        }
        FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        double fraction = Math.max(0.0001, volumePercent / 100.0);
        float db = (float) (20.0 * Math.log10(fraction));
        gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db)));
    }

    /** Plays a short sound effect on a background thread so the game loop never waits for audio. */
    public static void playSound(String name) {
        if (!soundEnabled || audioBroken || name == null || volumePercent <= 0) {
            return;
        }
        synchronized (LAST_PLAYED) {
            // the same sound twice within 40 ms just sounds like one louder sound
            long now = System.nanoTime();
            Long last = LAST_PLAYED.get(name);
            if (last != null && now - last < 40_000_000L) {
                return;
            }
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

    /** Runs on the sound thread: open, start, and close the clip again when it has finished. */
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

    /** Starts a looping background track (replaces the current one). */
    public static void playMusic(String name) {
        synchronized (MUSIC_LOCK) {
            musicName = name;
            stopMusicClip();
            if (!musicEnabled || name == null) {
                return;
            }
            musicClip = createClip(name);
            if (musicClip != null) {
                musicClip.loop(Clip.LOOP_CONTINUOUSLY);
            }
        }
    }

    /** Stops the background track. */
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

    public static void setSoundEnabled(boolean b) {
        soundEnabled = b;
    }

    /** Turning music off stops it; turning it back on resumes the last requested track. */
    public static void setMusicEnabled(boolean b) {
        synchronized (MUSIC_LOCK) {
            musicEnabled = b;
            String wanted = musicName;
            if (!b) {
                stopMusicClip();
            } else if (wanted != null && musicClip == null) {
                playMusic(wanted);
            }
        }
    }

    /** 0..100. Also changes the music that is already playing. */
    public static void setVolume(int percent0to100) {
        volumePercent = Math.max(0, Math.min(100, percent0to100));
        synchronized (MUSIC_LOCK) {
            applyVolume(musicClip);
        }
    }

    public static boolean isSoundEnabled() {
        return soundEnabled;
    }

    public static boolean isMusicEnabled() {
        return musicEnabled;
    }

    public static int getVolume() {
        return volumePercent;
    }

    /** True when the named .wav exists and can be decoded (used by tests and the launcher). */
    public static synchronized boolean canLoadSound(String name) {
        return loadSound(name) && SOUND_DATA.get(name).length > 0;
    }
}
