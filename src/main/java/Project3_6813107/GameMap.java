// Tapatsawin Chaieua          6813105
// Thanapon Thanadunpremdet    6813106
// Tanawat Wongwad             6813107
// Natthanon Achachan          6813241
package Project3_6813107;

import static Project3_6813107.MapSprites.*;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class GameMap {

    public static final int COLS = 56, ROWS = 36;
    private static final int T = GameData.TILE;
    private static final int SAND = 0, DIRT = 1, FLOOR = 2;
    private static final int DIRT_RGB = 0xAD7757, FAR = Integer.MAX_VALUE;

    public interface Drawable {
        double getSortY();
        void draw(Graphics2D g2);
    }

    public static final Comparator<Drawable> DEPTH_ORDER = Comparator.comparingDouble(Drawable::getSortY);

    public static final class Prop implements Drawable {
        private final BufferedImage[] frames;
        private final int ticksPerFrame, drawX, drawY;
        private final double sortY;
        private int animTick;

        Prop(BufferedImage[] frames, int ticksPerFrame, int drawX, int drawY, double sortY) {
            this.frames = frames;
            this.ticksPerFrame = ticksPerFrame;
            this.drawX = drawX;
            this.drawY = drawY;
            this.sortY = sortY;
        }

        public BufferedImage getImage() { return frames[(animTick / Math.max(1, ticksPerFrame)) % frames.length]; }
        public Rectangle getDrawBounds() { return new Rectangle(drawX, drawY, frames[0].getWidth(), frames[0].getHeight()); }
        public boolean isAnimated() { return frames.length > 1; }
        @Override public double getSortY() { return sortY; }
        public void update() { animTick++; }
        @Override public void draw(Graphics2D g2) { g2.drawImage(getImage(), drawX, drawY, null); }
    }

    private final GameData.MapType type;
    private final boolean safeHub;
    private final Random rng;
    private final int[][] terrain = new int[ROWS][COLS];
    private final boolean[][] wall = new boolean[ROWS][COLS];
    private final List<Prop> props = new ArrayList<>();
    private final List<Prop> animatedProps = new ArrayList<>();
    private final List<Rectangle> solids = new ArrayList<>();
    private final List<List<Rectangle>> solidsByCell = new ArrayList<>();
    private final List<Rectangle> keepClear = new ArrayList<>();
    private final List<int[]> decals = new ArrayList<>();
    private final List<PropDef> decalDefs = new ArrayList<>();
    private final List<Rectangle> dashes = new ArrayList<>();
    private final Rectangle playArea = new Rectangle(T, T, (COLS - 2) * T, (ROWS - 2) * T);
    private final List<Point> spawnPoints = new ArrayList<>();
    private final List<Point> guardPosts = new ArrayList<>();
    private final int[][] flow = new int[ROWS][COLS];

    private BufferedImage ground, minimap;
    private Rectangle campArea;
    private Point playerSpawn, gatePosition, traderPosition;
    private String chamberLayoutName = "SAFE CAMP HUB";
    private int appliedTurretLv, appliedWallLv, appliedMedicLv, appliedFarmLv;
    private boolean[][] walkable, reachable, openEast, openSouth;
    private int flowCol = -1, flowRow = -1;

    public GameMap(GameData.MapType type, long seed) {
        this(type, seed, true);
    }

    public GameMap(GameData.MapType type, long seed, boolean safeHub) {
        this.type = type;
        this.safeHub = safeHub;
        this.rng = new Random(seed);
        for (int i = 0; i < ROWS * COLS; i++) solidsByCell.add(new ArrayList<>());
        int campCol = COLS / 2 - 6, campRow = ROWS / 2 - 5;
        campArea = new Rectangle(campCol * T, campRow * T, 12 * T, 10 * T);
        playerSpawn = new Point(campArea.x + campArea.width / 2, campArea.y + campArea.height / 2 + T);
        gatePosition = new Point(campArea.x + campArea.width / 2, campArea.y + campArea.height - 10);
        if (safeHub) {
            buildCamp();
            keepClear.add(new Rectangle(campArea.x + T, campArea.y + 3 * T, campArea.width - 2 * T, campArea.height - 3 * T));
            switch (type) {
                case WASTELAND_HIGHWAY -> buildHighway();
                case SURVIVOR_CAMP -> buildSurvivorCamp();
                case ABANDONED_BUNKER -> buildBunker();
                case GAS_STATION -> buildGasStation();
                case QUARANTINE_ZONE -> buildQuarantine();
            }
        } else {
            buildProceduralChamber();
        }
        renderGround();
        computeWalkable();
        findSpawnPoints();
        renderMinimap();
    }

    public String getChamberLayoutName() { return chamberLayoutName; }
    public GameData.MapType getType() { return type; }
    public int getWidthPx() { return COLS * T; }
    public int getHeightPx() { return ROWS * T; }
    public boolean hasSafeCamp() { return safeHub; }
    public Point getPlayerSpawn() { return new Point(playerSpawn); }
    public Rectangle getCampArea() { return new Rectangle(campArea); }
    public Point getTraderPosition() { return new Point(traderPosition != null ? traderPosition : playerSpawn); }
    public Point getGatePosition() { return new Point(gatePosition != null ? gatePosition : playerSpawn); }
    public BufferedImage getMinimap() { return minimap; }
    public BufferedImage getGroundImage() { return ground; }
    public boolean isWalkableTile(int col, int row) { return col >= 0 && row >= 0 && col < COLS && row < ROWS && walkable[row][col]; }
    public void update() { for (Prop p : animatedProps) p.update(); }

    public List<Point> getSpawnPoints() {
        List<Point> copy = new ArrayList<>();
        for (Point p : spawnPoints) copy.add(new Point(p));
        return copy;
    }

    public List<Point> getGuardPosts() {
        List<Point> copy = new ArrayList<>();
        for (Point p : guardPosts) copy.add(new Point(p));
        return copy;
    }

    public boolean isBlocked(Rectangle r) {
        if (!playArea.contains(r)) return true;
        int c0 = r.x / T, c1 = (r.x + r.width - 1) / T;
        int r0 = r.y / T, r1 = (r.y + r.height - 1) / T;
        for (int row = r0; row <= r1; row++) {
            for (int col = c0; col <= c1; col++) {
                if (wall[row][col]) return true;
                for (Rectangle s : solidsByCell.get(row * COLS + col)) {
                    if (s.intersects(r)) return true;
                }
            }
        }
        return false;
    }

    public Point randomSpawnPoint(Random random, Rectangle cameraView) {
        Rectangle hidden = new Rectangle(cameraView);
        hidden.grow(48, 48);
        List<Point> choices = new ArrayList<>();
        for (Point p : spawnPoints) {
            if (!hidden.contains(p)) choices.add(p);
        }
        if (!choices.isEmpty()) return new Point(choices.get(random.nextInt(choices.size())));
        Point best = spawnPoints.get(0);
        double bestDist = -1, cx = cameraView.getCenterX(), cy = cameraView.getCenterY();
        for (Point p : spawnPoints) {
            double d = Math.hypot(p.x - cx, p.y - cy);
            if (d > bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return new Point(best);
    }

    public Point randomWalkablePoint(Random random, double fromX, double fromY, double minDist) {
        for (int tries = 0; tries < 90; tries++) {
            int col = 3 + random.nextInt(COLS - 6), row = 3 + random.nextInt(ROWS - 6);
            if (!isWalkableTile(col, row) || (reachable != null && !reachable[row][col])) continue;
            double px = col * T + T / 2.0, py = row * T + T / 2.0;
            if (Math.hypot(px - fromX, py - fromY) >= minDist
                    && !isBlocked(new Rectangle((int) px - 12, (int) py - 10, 24, 20))) {
                return new Point((int) px, (int) py);
            }
        }
        return new Point(spawnPoints.get(random.nextInt(spawnPoints.size())));
    }

    public Point randomOuterLootPoint(Random random) {
        double cx = campArea.getCenterX(), cy = campArea.getCenterY();
        for (int tries = 0; tries < 60; tries++) {
            int col = 2 + random.nextInt(COLS - 4), row = 2 + random.nextInt(ROWS - 4);
            if (!isWalkableTile(col, row)) continue;
            double px = col * T + T / 2.0, py = row * T + T / 2.0;
            if (Math.hypot(px - cx, py - cy) >= 11.0 * T
                    && !isBlocked(new Rectangle((int) px - 10, (int) py - 8, 20, 16))) {
                return new Point((int) px, (int) py);
            }
        }
        return new Point(spawnPoints.get(random.nextInt(spawnPoints.size())));
    }

    public boolean applyBaseUpgrades(int turretLv, int wallLv, int medicLv, int farmLv) {
        if (turretLv == appliedTurretLv && wallLv == appliedWallLv
                && medicLv == appliedMedicLv && farmLv == appliedFarmLv) {
            return false;
        }
        int c0 = campArea.x / T, r0 = campArea.y / T;
        boolean changed = false;

        if (wallLv > appliedWallLv) {
            if (appliedWallLv < 1 && wallLv >= 1) placeCampTiles(CRATE_WOOD, c0 + 2, r0 + 9, true, BARREL_ORANGE, c0 + 3, r0 + 9, true, BARREL_RED, c0 + 8, r0 + 9, true, CRATE_METAL, c0 + 9, r0 + 9, true);
            if (appliedWallLv < 2 && wallLv >= 2) placeCampTiles(TIRES, c0, r0 + 3, true, CRATE_CLOSED, c0, r0 + 7, true, TIRES, c0 + 11, r0 + 3, true, CRATE_CLOSED, c0 + 11, r0 + 7, true);
            if (appliedWallLv < 3 && wallLv >= 3) placeCampTiles(CRATE_METAL_G, c0 + 3, r0 + 8, true, CRATE_METAL_G, c0 + 8, r0 + 8, true, SIGN_KEEP_OUT, c0 + 5, r0 + 9, false);
            appliedWallLv = wallLv;
            changed = true;
        }
        if (turretLv > appliedTurretLv) {
            if (appliedTurretLv < 1 && turretLv >= 1) {
                guardPosts.add(new Point((c0 + 1) * T + T / 2, (r0 + 5) * T + T / 2));
                putCampTile(BARREL_GREY, c0 + 2, r0 + 5, false);
            }
            if (appliedTurretLv < 2 && turretLv >= 2) addGuardPost(c0 + 10, r0 + 5);
            if (appliedTurretLv < 3 && turretLv >= 3) addGuardPost(c0 + 3, r0 + 7);
            if (appliedTurretLv < 4 && turretLv >= 4) addGuardPost(c0 + 8, r0 + 7);
            appliedTurretLv = turretLv;
            changed = true;
        }
        if (medicLv > appliedMedicLv) {
            if (appliedMedicLv < 1 && medicLv >= 1) placeCampTiles(BOX_MEDS, c0 + 5, r0 + 3, false, COT_H, c0 + 2, r0 + 3, false);
            if (appliedMedicLv < 2 && medicLv >= 2) placeCampTiles(GURNEY_CURTAIN, c0 + 1, r0 + 3, true, WATER_COOLER, c0 + 4, r0 + 3, false);
            if (appliedMedicLv < 3 && medicLv >= 3) putCampTile(OP_TABLE, c0 + 3, r0 + 3, true);
            appliedMedicLv = medicLv;
            changed = true;
        }
        if (farmLv > appliedFarmLv) {
            if (appliedFarmLv < 1 && farmLv >= 1) placeCampTiles(PLANTER, c0 + 9, r0 + 3, false, CROP_CORN, c0 + 10, r0 + 3, false);
            if (appliedFarmLv < 2 && farmLv >= 2) placeCampTiles(CROP_WHEAT, c0 + 9, r0 + 4, false, AMMO_CRATE_H, c0 + 10, r0 + 4, false);
            if (appliedFarmLv < 3 && farmLv >= 3) placeCampTiles(BOX_AMMO, c0 + 8, r0 + 3, false, BOX_SUPPLY, c0 + 7, r0 + 3, false);
            appliedFarmLv = farmLv;
            changed = true;
        }
        if (changed) {
            flowCol = flowRow = -1;
            computeWalkable();
            renderMinimap();
        }
        return changed;
    }

    private void addGuardPost(int col, int row) {
        putCampTile(NPC_GUARD, col, row, true);
        guardPosts.add(new Point(col * T + T / 2, row * T + T / 2));
    }

    private void placeCampTiles(Object... entries) {
        for (int i = 0; i < entries.length; i += 4) {
            putCampTile((PropDef) entries[i], (Integer) entries[i + 1], (Integer) entries[i + 2], (Boolean) entries[i + 3]);
        }
    }

    private void putCampTile(PropDef d, int col, int row, boolean solidFootprint) {
        double cx = col * T + T / 2.0, bottom = (row + 1) * T;
        if (Math.hypot(cx - playerSpawn.x, bottom - playerSpawn.y) < 28) return;
        int left = (int) Math.round(cx - d.w() / 2.0), artBottom = (int) Math.round(bottom);
        Rectangle solid = null;
        if (solidFootprint && d.footH() > 0) {
            solid = new Rectangle(left + d.inset(), artBottom - d.footH(), d.w() - 2 * d.inset(), d.footH());
            if (!playArea.contains(solid) || overlapsWall(solid)) return;
            for (Rectangle s : solids) {
                if (s.intersects(solid)) {
                    solid = null;
                    break;
                }
            }
        }
        addProp(d, cx, artBottom, solid);
    }

    public void drawGround(Graphics2D g2, Rectangle cameraView) {
        Rectangle v = cameraView.intersection(new Rectangle(0, 0, getWidthPx(), getHeightPx()));
        if (!v.isEmpty()) g2.drawImage(ground, v.x, v.y, v.x + v.width, v.y + v.height, v.x, v.y, v.x + v.width, v.y + v.height, null);
    }

    public List<Prop> getPropsInView(Rectangle cameraView) {
        List<Prop> list = new ArrayList<>();
        for (Prop p : props) {
            if (p.getDrawBounds().intersects(cameraView)) list.add(p);
        }
        return list;
    }

    public List<Rectangle> getSolidRects() {
        List<Rectangle> list = new ArrayList<>();
        for (Rectangle s : solids) list.add(new Rectangle(s));
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (wall[r][c]) list.add(new Rectangle(c * T, r * T, T, T));
            }
        }
        return list;
    }

    public double getChaseAngle(double fromX, double fromY, double toX, double toY, int feetWidth) {
        double direct = Math.atan2(toY - fromY, toX - fromX);
        if (hasClearPath(fromX, fromY, toX, toY, feetWidth)) return direct;
        refreshFlow((int) (toX / T), (int) (toY / T));
        int col = (int) (fromX / T), row = (int) (fromY / T);
        if (col < 0 || row < 0 || col >= COLS || row >= ROWS) return direct;
        int[] step = bestNeighbour(col, row, fromX, fromY, feetWidth, true);
        if (step == null) step = bestNeighbour(col, row, fromX, fromY, feetWidth, false);
        if (step == null) return direct;
        return Math.atan2(step[1] * T + T / 2.0 - fromY, step[0] * T + T / 2.0 - fromX);
    }

    public double getChaseAngle(double fromX, double fromY, double toX, double toY) {
        return getChaseAngle(fromX, fromY, toX, toY, 12);
    }

    private int[] bestNeighbour(int col, int row, double fromX, double fromY, int feetWidth, boolean needLineOfSight) {
        int[] bestStep = null;
        int best = flow[row][col];
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int nc = col + dc, nr = row + dr;
                if ((dc == 0 && dr == 0) || !isWalkableTile(nc, nr) || flow[nr][nc] >= best) continue;
                boolean ok = needLineOfSight
                        ? hasClearPath(fromX, fromY, nc * T + T / 2.0, nr * T + T / 2.0, feetWidth)
                        : (dc == 0 || dr == 0) && isLinked(col, row, nc, nr);
                if (ok) {
                    best = flow[nr][nc];
                    bestStep = new int[]{nc, nr};
                }
            }
        }
        return bestStep;
    }

    public boolean hasClearPath(double x0, double y0, double x1, double y1, int feetWidth) {
        int steps = (int) (Math.hypot(x1 - x0, y1 - y0) / 8);
        Rectangle probe = new Rectangle(0, 0, feetWidth, 8);
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            probe.x = (int) (x0 + (x1 - x0) * t) - feetWidth / 2;
            probe.y = (int) (y0 + (y1 - y0) * t) - 4;
            if (isBlocked(probe)) return false;
        }
        return true;
    }

    public boolean hasClearPath(double x0, double y0, double x1, double y1) {
        return hasClearPath(x0, y0, x1, y1, 12);
    }

    private void refreshFlow(int targetCol, int targetRow) {
        targetCol = clampInt(targetCol, 0, COLS - 1);
        targetRow = clampInt(targetRow, 0, ROWS - 1);
        if (targetCol == flowCol && targetRow == flowRow) return;
        flowCol = targetCol;
        flowRow = targetRow;
        fillDistances(flow, targetCol, targetRow);
    }

    private void fillDistances(int[][] dist, int startCol, int startRow) {
        for (int[] row : dist) Arrays.fill(row, FAR);
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        dist[startRow][startCol] = 0;
        queue.add(new int[]{startCol, startRow});
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            for (int[] s : steps) {
                int nc = cur[0] + s[0], nr = cur[1] + s[1];
                if (isLinked(cur[0], cur[1], nc, nr) && dist[nr][nc] == FAR) {
                    dist[nr][nc] = dist[cur[1]][cur[0]] + 1;
                    queue.add(new int[]{nc, nr});
                }
            }
        }
    }

    private void paint(int groundType, int col, int row, int w, int h) {
        for (int r = row; r < row + h; r++) {
            for (int c = col; c < col + w; c++) {
                if (r >= 0 && c >= 0 && r < ROWS && c < COLS) terrain[r][c] = groundType;
            }
        }
    }

    private void reserve(int col, int row, int w, int h) {
        keepClear.add(new Rectangle(col * T, row * T, w * T, h * T));
    }

    private boolean put(PropDef d, double cx, double bottom) {
        int left = (int) Math.round(cx - d.w() / 2.0), artBottom = (int) Math.round(bottom);
        Rectangle solid = null;
        if (d.footH() > 0) {
            solid = new Rectangle(left + d.inset(), artBottom - d.footH(), d.w() - 2 * d.inset(), d.footH());
            if (!playArea.contains(solid) || overlapsWall(solid)) return false;
            for (Rectangle k : keepClear) if (k.intersects(solid)) return false;
            for (Rectangle s : solids) if (s.intersects(solid)) return false;
        } else if (!playArea.contains(new Rectangle(left, artBottom - 1, d.w(), 1))) {
            return false;
        }
        addProp(d, cx, artBottom, solid);
        return true;
    }

    private void addProp(PropDef d, double cx, int artBottom, Rectangle solid) {
        BufferedImage[] imgs = d.images();
        int drawY = artBottom + d.padBottom() - imgs[0].getHeight();
        int drawX = (int) Math.round(cx - imgs[0].getWidth() / 2.0);
        Prop p = new Prop(imgs, d.ticksPerFrame(), drawX, drawY, artBottom);
        props.add(p);
        if (p.isAnimated()) animatedProps.add(p);
        if (solid != null) addSolid(solid);
    }

    private boolean putTile(PropDef d, int col, int row) {
        return put(d, col * T + T / 2.0, (row + 1) * T);
    }

    private boolean putLeft(PropDef d, int col, int row) {
        return put(d, col * T + d.w() / 2.0, (row + 1) * T);
    }

    private void placeLeft(Object... entries) {
        for (int i = 0; i < entries.length; i += 3) putLeft((PropDef) entries[i], (Integer) entries[i + 1], (Integer) entries[i + 2]);
    }

    private void placeTile(Object... entries) {
        for (int i = 0; i < entries.length; i += 3) putTile((PropDef) entries[i], (Integer) entries[i + 1], (Integer) entries[i + 2]);
    }

    private int scatter(PropDef[] options, int col, int row, int w, int h, int count, int tries) {
        int placed = 0;
        for (int i = 0; i < tries && placed < count; i++) {
            PropDef d = options[rng.nextInt(options.length)];
            double cx = (col + rng.nextDouble() * w) * T;
            double bottom = (row + 1 + rng.nextDouble() * (h - 1)) * T;
            Rectangle gap = new Rectangle((int) cx - d.w() / 2 - 6, (int) bottom - d.footH() - 6, d.w() + 12, d.footH() + 12);
            if (!touchesSolid(gap) && put(d, cx, bottom)) placed++;
        }
        return placed;
    }

    private boolean touchesSolid(Rectangle r) {
        for (Rectangle s : solids) if (s.intersects(r)) return true;
        return overlapsWall(r);
    }

    private void decal(PropDef d, int x, int y) {
        decalDefs.add(d);
        decals.add(new int[]{decalDefs.size() - 1, x, y});
    }

    private void addSolid(Rectangle s) {
        solids.add(s);
        int c0 = Math.max(0, s.x / T), c1 = Math.min(COLS - 1, (s.x + s.width - 1) / T);
        int r0 = Math.max(0, s.y / T), r1 = Math.min(ROWS - 1, (s.y + s.height - 1) / T);
        for (int r = r0; r <= r1; r++) {
            for (int c = c0; c <= c1; c++) solidsByCell.get(r * COLS + c).add(s);
        }
    }

    private boolean overlapsWall(Rectangle r) {
        int c0 = Math.max(0, r.x / T), c1 = Math.min(COLS - 1, (r.x + r.width - 1) / T);
        int r0 = Math.max(0, r.y / T), r1 = Math.min(ROWS - 1, (r.y + r.height - 1) / T);
        for (int row = r0; row <= r1; row++) {
            for (int col = c0; col <= c1; col++) if (wall[row][col]) return true;
        }
        return false;
    }

    private void wallRect(int col, int row, int w, int h) {
        setWallRect(col, row, w, h, true);
    }

    private void clearWallRect(int col, int row, int w, int h) {
        setWallRect(col, row, w, h, false);
    }

    private void setWallRect(int col, int row, int w, int h, boolean val) {
        for (int r = row; r < row + h; r++) {
            for (int c = col; c < col + w; c++) setWallSafe(c, r, val);
        }
    }

    private void setWallSafe(int c, int r, boolean val) {
        if (r >= 0 && r < ROWS && c >= 0 && c < COLS) wall[r][c] = val;
    }

    private static int clampInt(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private void walledBox(int x, int y, int w, int h, int t, int midC, int midR, int hDoorW, int vDoorH) {
        wallRect(x, y, w, t);
        wallRect(x, y + h - t, w, t);
        wallRect(x, y, t, h);
        wallRect(x + w - t, y, t, h);
        clearWallRect(midC - hDoorW / 2, y, hDoorW, t);
        clearWallRect(midC - hDoorW / 2, y + h - t, hDoorW, t);
        clearWallRect(x, midR - vDoorH / 2, t, vDoorH);
        clearWallRect(x + w - t, midR - vDoorH / 2, t, vDoorH);
    }

    private void buildProceduralChamber() {
        int padC = 1 + rng.nextInt(4), padR = 1 + rng.nextInt(3);
        int minC = padC, maxC = COLS - 1 - padC, minR = Math.max(2, padR), maxR = ROWS - 1 - padR;
        wallRect(0, 0, COLS, minR);
        wallRect(0, maxR + 1, COLS, ROWS - (maxR + 1));
        wallRect(0, 0, minC, ROWS);
        wallRect(maxC + 1, 0, COLS - (maxC + 1), ROWS);

        if (rng.nextBoolean()) {
            int cut = 3 + rng.nextInt(3);
            for (int d = 0; d < cut; d++) {
                for (int k = 0; k < cut - d; k++) {
                    setWallSafe(minC + k, minR + d, true);
                    setWallSafe(maxC - k, minR + d, true);
                    setWallSafe(minC + k, maxR - d, true);
                    setWallSafe(maxC - k, maxR - d, true);
                }
            }
        }

        int midC = (minC + maxC) / 2, midR = (minR + maxR) / 2;
        int offR = (rng.nextInt(3) - 1) * 4, offC = (rng.nextInt(3) - 1) * 6;
        int dir = rng.nextInt(8);
        int sr1 = clampInt(midR + offR, minR + 5, maxR - 5), sr2 = clampInt(midR - offR, minR + 5, maxR - 5);
        int sc1 = clampInt(midC + offC, minC + 6, maxC - 6), sc2 = clampInt(midC - offC, minC + 6, maxC - 6);
        int[][] pairs = {
            {minC + 4, sr1, maxC - 4, sr2}, {maxC - 4, sr1, minC + 4, sr2},
            {sc1, maxR - 4, sc2, minR + 4}, {sc1, minR + 4, sc2, maxR - 4},
            {minC + 5, minR + 4, maxC - 5, maxR - 4}, {maxC - 5, maxR - 4, minC + 5, minR + 4},
            {minC + 5, maxR - 4, maxC - 5, minR + 4}, {maxC - 5, minR + 4, minC + 5, maxR - 4}
        };
        int sc = pairs[dir][0], sr = pairs[dir][1], gc = pairs[dir][2], gr = pairs[dir][3];
        int tc = clampInt(sc + (sc < midC ? 4 : -4), minC + 5, maxC - 5);
        int tr = clampInt(sr + (sr < midR ? 3 : -3), minR + 5, maxR - 4);
        if (Math.abs(tc - gc) <= 4 && Math.abs(tr - gr) <= 4) {
            tc = midC;
            tr = midR;
        }

        playerSpawn = new Point(sc * T + T / 2, sr * T + T / 2);
        gatePosition = new Point(gc * T + T / 2, gr * T + T / 2);
        traderPosition = new Point(tc * T + T / 2, tr * T + T / 2);
        campArea = new Rectangle(playerSpawn.x - 3 * T, playerSpawn.y - 3 * T, 6 * T, 6 * T);
        reserve(sc - 2, sr - 2, 5, 5);
        reserve(gc - 2, gr - 2, 5, 5);
        Rectangle traderPlaza = new Rectangle((tc - 2) * T, (tr - 2) * T, 5 * T, 4 * T);
        keepClear.add(traderPlaza);

        switch (rng.nextInt(12)) {
            case 0 -> { chamberLayoutName = "HIGHWAY AMBUSH"; buildProceduralHighway(minC, minR, maxC, maxR, sc, sr, gc, gr); }
            case 1 -> { chamberLayoutName = "BUNKER LABYRINTH"; buildProceduralLabyrinth(minC, minR, maxC, maxR); }
            case 2 -> { chamberLayoutName = "TWIN BRIDGES"; buildProceduralTwinBridges(minC, minR, maxC, maxR, dir); }
            case 3 -> { chamberLayoutName = "PILLAR COLOSSEUM"; buildProceduralColosseum(minC, minR, maxC, maxR, midC, midR); }
            case 4 -> { chamberLayoutName = "SHANTY ALLEYS"; buildProceduralShanty(minC, minR, maxC, maxR); }
            case 5 -> { chamberLayoutName = "REACTOR CORE"; buildProceduralReactor(minC, minR, maxC, maxR, midC, midR); }
            case 6 -> { chamberLayoutName = "SERPENTINE GAUNTLET"; buildProceduralSerpentine(minC, minR, maxC, maxR); }
            case 7 -> { chamberLayoutName = "QUARANTINE CELLS"; buildProceduralQuarantine(minC, minR, maxC, maxR, midC, midR); }
            case 8 -> { chamberLayoutName = "CROSSROAD CITADEL"; buildProceduralCitadel(minC, minR, maxC, maxR, midC, midR); }
            case 9 -> { chamberLayoutName = "SUNKEN COURTYARD"; buildProceduralCourtyard(minC, minR, maxC, maxR, midC, midR); }
            case 10 -> { chamberLayoutName = "SPLIT FOUNDRY"; buildProceduralFoundry(minC, minR, maxC, maxR, midC, midR); }
            default -> { chamberLayoutName = "CATACOMB RINGS"; buildProceduralCatacombs(minC, minR, maxC, maxR, midC, midR); }
        }
        ensureChamberConnectivity(sc, sr, gc, gr, midC, midR, tc, tr);
        keepClear.remove(traderPlaza);
        paint(DIRT, tc - 2, tr - 2, 5, 4);
        placeTile(NPC_TRADER, tc, tr - 1, TABLE_TRADE, tc - 1, tr - 1, BOX_SUPPLY, tc + 1, tr - 1);
    }

    private void buildProceduralHighway(int minC, int minR, int maxC, int maxR, int sc, int sr, int gc, int gr) {
        paint(type == GameData.MapType.ABANDONED_BUNKER ? FLOOR : SAND, minC, minR, maxC - minC + 1, maxR - minR + 1);
        int midR = (sr + gr) / 2, midC = (sc + gc) / 2;
        paint(DIRT, minC, Math.max(minR, midR - 2), maxC - minC + 1, 5);
        paint(DIRT, Math.max(minC, midC - 2), minR, 5, maxR - minR + 1);
        roadLine(minC, maxC, midR * T + 12, false);
        roadLine(minR, maxR, midC * T + 12, true);
        scatter(new PropDef[]{CAR_RED_H, CAR_BURNT_H, VAN_BURNT_H, VAN_BLUE_H, CAR_BLUE_V, CAR_BURNT_V},
                minC + 3, minR + 3, maxC - minC - 6, maxR - minR - 6, 8 + rng.nextInt(5), 45);
        scatter(new PropDef[]{BARREL_RED, BARREL_ORANGE, TIRES, CRATE_METAL, CRATE_WOOD, STOP_SIGN},
                minC + 2, minR + 2, maxC - minC - 4, maxR - minR - 4, 14 + rng.nextInt(6), 60);
    }

    private void buildProceduralLabyrinth(int minC, int minR, int maxC, int maxR) {
        int spanC = maxC - minC + 1, spanR = maxR - minR + 1;
        paint(FLOOR, minC, minR, spanC, spanR);
        int divCount = 2 + rng.nextInt(2);
        for (int i = 1; i <= divCount; i++) {
            int wc = minC + i * spanC / (divCount + 1);
            wallRect(wc, minR + 2, 1, spanR - 4);
            clearWallRect(wc, minR + 3 + rng.nextInt(Math.max(2, spanR / 2 - 4)), 1, 4);
            clearWallRect(wc, minR + spanR / 2 + rng.nextInt(Math.max(2, spanR / 2 - 5)), 1, 4);
        }
        if (rng.nextBoolean()) {
            int wr = minR + spanR / 2;
            wallRect(minC + 4, wr, spanC - 8, 1);
            clearWallRect(minC + spanC / 3, wr, 4, 1);
            clearWallRect(minC + 2 * spanC / 3 - 2, wr, 4, 1);
        }
        scatter(new PropDef[]{LOCKER_PAIR, DESK_H, TABLE_MED, BARREL_RED, CRATE_METAL, BOX_SUPPLY},
                minC + 2, minR + 2, spanC - 4, spanR - 4, 14 + rng.nextInt(6), 60);
    }

    private void buildProceduralTwinBridges(int minC, int minR, int maxC, int maxR, int dir) {
        paint(DIRT, minC, minR, maxC - minC + 1, maxR - minR + 1);
        int midC = (minC + maxC) / 2, midR = (minR + maxR) / 2;
        if (dir < 2) {
            wallRect(midC - 1, minR, 3, maxR - minR + 1);
            int br1 = minR + 4 + rng.nextInt(4), br2 = maxR - 7 - rng.nextInt(4);
            clearWallRect(midC - 1, br1, 3, 4);
            clearWallRect(midC - 1, br2, 3, 4);
            paint(FLOOR, midC - 2, br1, 5, 4);
            paint(FLOOR, midC - 2, br2, 5, 4);
        } else {
            wallRect(minC, midR - 1, maxC - minC + 1, 3);
            int br1 = minC + 6 + rng.nextInt(6), br2 = maxC - 10 - rng.nextInt(6);
            clearWallRect(br1, midR - 1, 4, 3);
            clearWallRect(br2, midR - 1, 4, 3);
            paint(FLOOR, br1, midR - 2, 4, 5);
            paint(FLOOR, br2, midR - 2, 4, 5);
        }
        scatter(new PropDef[]{FENCE_BROKEN_A, BARREL_ORANGE, CRATE_WOOD, TIRES, SIGN_DANGER, BOX_PILE},
                minC + 2, minR + 2, maxC - minC - 4, maxR - minR - 4, 14, 55);
    }

    private void buildProceduralColosseum(int minC, int minR, int maxC, int maxR, int midC, int midR) {
        paint(SAND, minC, minR, maxC - minC + 1, maxR - minR + 1);
        paint(DIRT, midC - 10, midR - 6, 20, 12);
        int numPillars = 6 + rng.nextInt(3);
        for (int i = 0; i < numPillars; i++) {
            double ang = i * (2 * Math.PI / numPillars) + 0.3;
            wallRect(clampInt((int) Math.round(midC + Math.cos(ang) * (8 + (i % 2) * 5)), minC + 4, maxC - 6),
                    clampInt((int) Math.round(midR + Math.sin(ang) * (5 + (i % 2) * 3)), minR + 4, maxR - 6), 2, 2);
        }
        putTile(FLAME_BARREL, midC, midR);
        scatter(new PropDef[]{CRATE_STACK, METAL_STACK, BARREL_RED, TIRES, BOX_PILE},
                minC + 3, minR + 3, maxC - minC - 6, maxR - minR - 6, 12, 48);
    }

    private void buildProceduralShanty(int minC, int minR, int maxC, int maxR) {
        paint(DIRT, minC, minR, maxC - minC + 1, maxR - minR + 1);
        paint(FLOOR, minC + 3, minR + 3, maxC - minC - 6, maxR - minR - 6);
        int blocks = 3 + rng.nextInt(3);
        for (int i = 0; i < blocks; i++) {
            wallRect(minC + 6 + rng.nextInt(Math.max(4, maxC - minC - 14)),
                    minR + 4 + rng.nextInt(Math.max(4, maxR - minR - 10)), 3 + rng.nextInt(3), 2 + rng.nextInt(2));
        }
        scatter(new PropDef[]{SHED, STALL_A, STALL_B, TENT_GREEN_FLAT, WATER_TOWER, CRATE_CLUSTER, BARREL_STACK},
                minC + 2, minR + 2, maxC - minC - 4, maxR - minR - 4, 14, 60);
    }

    private void buildProceduralReactor(int minC, int minR, int maxC, int maxR, int midC, int midR) {
        paint(FLOOR, minC, minR, maxC - minC + 1, maxR - minR + 1);
        wallRect(midC - 2, midR - 2, 4, 4);
        wallRect(midC - 9, midR, 5, 1);
        wallRect(midC + 5, midR, 5, 1);
        wallRect(midC, midR - 7, 1, 3);
        wallRect(midC, midR + 5, 1, 3);
        placeTile(SIGN_TOXIC, midC - 3, midR - 2, SIGN_ELECTRIC, midC + 2, midR + 2);
        scatter(new PropDef[]{GAS_TANK, BARREL_STACK, CRATE_METAL, DESK_LAB_V, VENDING_RED, BOX_SUPPLY},
                minC + 2, minR + 2, maxC - minC - 4, maxR - minR - 4, 16, 65);
    }

    private void buildProceduralSerpentine(int minC, int minR, int maxC, int maxR) {
        int h = maxR - minR + 1, w = maxC - minC + 1;
        paint(DIRT, minC + 1, minR + 1, w - 2, h - 2);
        int segs = 4, stepC = Math.max(8, (w - 8) / segs);
        for (int i = 1; i < segs; i++) {
            int c = minC + 3 + i * stepC;
            if (c >= maxC - 5) break;
            wallRect(c, (i & 1) == 1 ? minR + 2 : minR + 8, 2, Math.max(4, h - 10));
            clearWallRect(c, clampInt(minR + 4 + rng.nextInt(Math.max(3, h - 9)), minR + 3, maxR - 5), 2, 3);
        }
        paint(SAND, minC + 4, minR + 3, w - 8, h - 6);
        scatter(new PropDef[]{CRATE_STACK, CRATE_METAL, BARREL_RED, BARREL_OIL, TIRES}, minC + 2, minR + 2, w - 4, h - 4, 24, 90);
        placeTile(SIGN_DANGER, minC + 5, minR + 3, SIGN_WARNING, maxC - 5, maxR - 3);
    }

    private void buildProceduralQuarantine(int minC, int minR, int maxC, int maxR, int midC, int midR) {
        paint(DIRT, minC + 2, midR - 2, maxC - minC - 4, 5);
        int cellW = 10, cellH = 8;
        for (int c = minC + 5; c + cellW <= maxC - 4; c += cellW + 3) {
            int topR = minR + 3;
            quarantineCell(c, topR, cellW, cellH, true);
            placeTile(TABLE_MED, c + 3, topR + 3, LOCKER_PAIR, c + cellW - 4, topR + 3);
            int botR = maxR - 2 - cellH;
            if (botR > topR + cellH + 3) {
                quarantineCell(c, botR, cellW, cellH, false);
                placeTile(DESK_LAB_V, c + 3, botR + 3, BARREL_ORANGE, c + cellW - 4, botR + 4);
            }
        }
        putTile(SIGN_TOXIC, midC - 2, midR);
        scatter(new PropDef[]{BARREL_ORANGE, CRATE_WOOD, TABLE_WORK, BOX_SUPPLY}, minC + 3, midR - 2, maxC - minC - 6, 5, 10, 45);
    }

    private void quarantineCell(int c, int r, int cellW, int cellH, boolean openSouth) {
        wallRect(c, openSouth ? r : r + cellH - 1, cellW, 2);
        wallRect(c, r, 2, cellH);
        wallRect(c + cellW - 2, r, 2, cellH);
        int gateRow = openSouth ? r + cellH - 1 : r;
        wallRect(c, gateRow, cellW / 2 - 1, 1);
        wallRect(c + cellW / 2 + 2, gateRow, cellW - (cellW / 2 + 2), 1);
    }

    private void buildProceduralCitadel(int minC, int minR, int maxC, int maxR, int midC, int midR) {
        paint(SAND, minC + 2, minR + 2, maxC - minC - 4, maxR - minR - 4);
        paint(DIRT, midC - 6, minR + 2, 12, maxR - minR - 4);
        paint(DIRT, minC + 2, midR - 4, maxC - minC - 4, 8);
        int rw = 14, rh = 10, x0 = midC - rw / 2, y0 = midR - rh / 2;
        walledBox(x0, y0, rw, rh, 2, midC, midR, 4, 4);
        placeTile(BOX_AMMO, midC - 2, midR - 1, SHED, midC + 2, midR - 1,
                FENCE_BROKEN_A, x0 - 4, midR - 3, FENCE_BROKEN_B, x0 + rw + 3, midR + 2,
                STOP_SIGN, midC - 4, y0 - 2, SIGN_KEEP_OUT, midC + 3, y0 + rh + 2);
        scatter(new PropDef[]{CRATE_CLUSTER, BARREL_STACK, CAR_BURNT_H, TIRES, METAL_STACK},
                minC + 2, minR + 2, maxC - minC - 4, maxR - minR - 4, 20, 80);
    }

    private void buildProceduralCourtyard(int minC, int minR, int maxC, int maxR, int midC, int midR) {
        paint(SAND, midC - 11, midR - 7, 22, 14);
        int bw = 8, bh = 5;
        wallRect(minC + 5, minR + 4, bw, bh);
        wallRect(maxC - 5 - bw, minR + 4, bw, bh);
        wallRect(minC + 5, maxR - 4 - bh, bw, bh);
        wallRect(maxC - 5 - bw, maxR - 4 - bh, bw, bh);
        clearWallRect(minC + 8, minR + 4 + bh - 2, 2, 2);
        clearWallRect(maxC - 9, maxR - 4 - bh, 2, 2);
        placeTile(WATER_TOWER, midC, midR - 2, FLAME_BARREL, midC - 3, midR + 1, FLAME_BARREL, midC + 3, midR + 1,
                STALL_A, midC - 7, midR - 4, STALL_B, midC + 6, midR + 3);
        placeLeft(CAR_BURNT_H, clampInt(midC - 6, minC + 2, maxC - 6), clampInt(midR + 4, minR + 3, maxR - 3),
                VAN_BURNT_H, clampInt(midC + 2, minC + 2, maxC - 6), clampInt(midR - 5, minR + 3, maxR - 3));
        scatter(new PropDef[]{CRATE_WOOD, BARREL_RED, TIRES, BOX_PILE, CAR_RED_H},
                minC + 2, minR + 2, maxC - minC - 4, maxR - minR - 4, 20, 80);
    }

    private void buildProceduralFoundry(int minC, int minR, int maxC, int maxR, int midC, int midR) {
        paint(DIRT, minC + 1, minR + 1, maxC - minC - 2, maxR - minR - 2);
        wallRect(midC - 1, minR + 2, 2, maxR - minR - 4);
        clearWallRect(midC - 1, minR + 5, 2, 4);
        clearWallRect(midC - 1, midR - 2, 2, 4);
        clearWallRect(midC - 1, maxR - 8, 2, 4);
        paint(SAND, midC - 5, midR - 3, 10, 6);
        placeTile(PIPE_STACK, midC - 7, midR - 4, PIPE_STACK, midC + 6, midR + 3,
                WATER_TANK_H, minC + 8, midR - 1, GAS_TANK, maxC - 8, midR,
                SIGN_ELECTRIC, midC - 3, minR + 4, SIGN_DANGER, midC + 3, maxR - 4);
        scatter(new PropDef[]{METAL_STACK, CRATE_METAL, CRATE_CLUSTER, BARREL_OIL, BARREL_STACK},
                minC + 2, minR + 2, maxC - minC - 4, maxR - minR - 4, 24, 90);
    }

    private void buildProceduralCatacombs(int minC, int minR, int maxC, int maxR, int midC, int midR) {
        int outW = Math.min(30, maxC - minC - 10), outH = Math.min(20, maxR - minR - 6);
        int ox = midC - outW / 2, oy = midR - outH / 2;
        if (outW >= 18 && outH >= 12) walledBox(ox, oy, outW, outH, 2, midC, midR, 4, 4);
        walledBox(midC - 5, midR - 3, 10, 6, 1, midC, midR, 3, 2);
        placeTile(VENDING_RED, midC - 2, midR, TENT_GREEN_FLAT, midC + 2, midR,
                DESK_H, ox + 4, oy + 3, CRATE_STACK, ox + outW - 5, oy + outH - 4);
        scatter(new PropDef[]{CRATE_WOOD, BOX_PILE, BARREL_RED, BARREL_ORANGE, CAR_BLUE_V},
                minC + 2, minR + 2, maxC - minC - 4, maxR - minR - 4, 18, 70);
    }

    private void ensureChamberConnectivity(int sc, int sr, int gc, int gr, int midC, int midR, int tc, int tr) {
        clearWallRect(sc - 2, sr - 2, 5, 5);
        clearWallRect(gc - 2, gr - 2, 5, 5);
        clearWallRect(tc - 2, tr - 2, 5, 4);
        carveCorridor(sc, sr, tc, tr);
        int wp1C = clampInt(midC + (rng.nextInt(3) - 1) * 4, 5, COLS - 6);
        int wp1R = clampInt(clampInt(midR - 5, 4, ROWS - 5), 5, ROWS - 6);
        int wp2C = clampInt(midC + (rng.nextInt(3) - 1) * 4, 5, COLS - 6);
        int wp2R = clampInt(clampInt(midR + 5, 4, ROWS - 5), 5, ROWS - 6);
        carveCorridor(sc, sr, wp1C, wp1R);
        carveCorridor(wp1C, wp1R, gc, gr);
        carveCorridor(tc, tr, wp2C, wp2R);
        carveCorridor(wp2C, wp2R, gc, gr);
    }

    private void carveCorridor(int c0, int r0, int c1, int r1) {
        int c = c0, r = r0;
        while (c != c1) {
            clearWallRect(c - 1, r - 1, 3, 3);
            c += Integer.compare(c1, c);
        }
        while (r != r1) {
            clearWallRect(c - 1, r - 1, 3, 3);
            r += Integer.compare(r1, r);
        }
        clearWallRect(c1 - 1, r1 - 1, 3, 3);
    }

    private void buildCamp() {
        int c0 = campArea.x / T, r0 = campArea.y / T;
        boolean indoor = type == GameData.MapType.ABANDONED_BUNKER;
        if (!indoor) paint(DIRT, c0, r0, 12, 10);
        if (indoor) placeLeft(BED_FRAME_H, c0, r0 + 2, LOCKER_DOUBLE, c0 + 2, r0 + 2);
        else putLeft(TENT_A, c0, r0 + 2);
        placeTile(TABLE_WORK, c0 + 4, r0 + 2, TABLE_MED, c0 + 5, r0 + 2, NPC_TRADER, c0 + 6, r0 + 2);
        traderPosition = new Point((c0 + 6) * T + T / 2, (r0 + 2) * T + T / 2);
        putTile(DESK_RADIO, c0 + 7, r0 + 2);
        placeLeft(indoor ? SHELF_STOCKED : TENT_GREEN, c0 + 8, r0 + 2, indoor ? RACK_STOCKED : BOX_SUPPLY, c0 + 10, r0 + 2,
                CRATE_WOOD, c0, r0 + 9, BARREL_ORANGE, c0 + 1, r0 + 9, BOX_PILE, c0, r0 + 8,
                CRATE_METAL, c0 + 11, r0 + 9, BARREL_RED, c0 + 10, r0 + 9, BOX_AMMO, c0 + 11, r0 + 8);
        put(FLAME_BARREL, playerSpawn.x - 2.5 * T, playerSpawn.y - 0.5 * T);
        put(FLAG, campArea.x + campArea.width - 1.5 * T, r0 * T + 5.6 * T);
        putTile(NPC_GUARD, c0 + 1, r0 + 5);
        put(indoor ? WATER_COOLER : BOX_WATER, campArea.x + campArea.width - 1.5 * T, playerSpawn.y + 1.4 * T);
        decal(SACK, campArea.x + 5 * T, playerSpawn.y + 2 * T);
    }

    private void barrels(int col, int row, PropDef a, PropDef b, PropDef c) {
        double x = col * T, bottom = (row + 1) * T;
        put(a, x + 12, bottom - 12);
        put(b, x + 36, bottom - 12);
        put(c, x + 24, bottom + 2);
    }

    private void tyres(int col, int row) {
        double x = col * T, bottom = (row + 1) * T;
        put(TIRES, x + 14, bottom - 10);
        put(TIRES_LOW, x + 44, bottom - 10);
        put(rng.nextBoolean() ? TIRES_G : TIRES_LOW_G, x + 30, bottom + 6);
    }

    private void rowOf(PropDef d, int col, int row, int count, int gap) {
        for (int i = 0; i < count; i++) put(d, col * T + d.w() / 2.0 + i * gap, (row + 1) * T);
    }

    private void roadLine(int from, int to, int at, boolean vertical) {
        for (int i = from; i <= to; i++) {
            dashes.add(vertical ? new Rectangle(at - 1, i * T + 8, 2, 14) : new Rectangle(i * T + 8, at - 1, 14, 2));
        }
    }

    private void buildHighway() {
        paint(DIRT, 0, 4, COLS, 6);
        paint(DIRT, 25, 10, 6, ROWS - 10);
        roadLine(0, COLS - 1, 7 * T, false);
        roadLine(10, 12, 28 * T, true);
        roadLine(23, ROWS - 1, 28 * T, true);
        reserve(1, 6, 6, 2);
        reserve(18, 6, COLS - 19, 2);
        reserve(26, 10, 4, ROWS - 11);
        reserve(1, 26, COLS - 2, 2);

        putLeft(SIGN_EXIT, 4, 2);
        tyres(10, 2);
        placeLeft(FENCE_BROKEN_A, 16, 2, FENCE_BROKEN_B, 18, 2, FENCE_BROKEN_A, 20, 2,
                SIGN_DANGER, 31, 2, CRATE_STACK_G, 38, 2, CRATE_WOOD_G, 40, 2);
        barrels(43, 2, BARREL_RED_G, BARREL_ORANGE, BARREL_RED);
        putLeft(STOP_SIGN, 48, 3);
        tyres(51, 2);

        placeLeft(CAR_RED_H, 2, 5, VAN_BURNT_H, 7, 6, CAR_BURNT_V, 11, 8, CAR_RED_H, 12, 5,
                CAR_BLUE_V, 14, 9, TIRES_LOW, 9, 8, TIRES_LOW, 16, 7);
        decal(PLANKS, 6 * T, 8 * T + 6);
        decal(PLANKS, 17 * T, 5 * T);
        placeLeft(CAR_BURNT_H, 20, 4, VAN_BLUE_H, 33, 4, CAR_RED_H, 37, 9, CAR_BURNT_H, 23, 9);
        barrels(45, 4, BARREL_ORANGE, BARREL_RED, BARREL_ORANGE);
        placeLeft(CRATE_STACK, 44, 9, BARREL_RED, 46, 9, CAR_BURNT_V, 50, 9, VAN_BURNT_H, 51, 4);
        decal(PLANKS, 47 * T, 7 * T + 12);

        placeLeft(STOP_SIGN, 24, 11, SIGN_EXIT, 31, 11);
        barrels(20, 10, BARREL_ORANGE_G, BARREL_RED, BARREL_ORANGE);

        rowOf(FENCE_BROKEN_A, 2, 13, 2, 64);
        rowOf(FENCE_BROKEN_B, 10, 13, 2, 64);
        placeLeft(CAR_BURNT_V, 3, 17, CAR_RED_V, 4, 17, CAR_BURNT_V, 5, 17, VAN_BURNT_H, 8, 16,
                CAR_BURNT_H, 12, 16, METAL_STACK_G, 16, 16, CAR_BLUE_V, 3, 23, VAN_BURNT_V, 4, 23);
        tyres(7, 21);
        tyres(7, 23);
        placeLeft(METAL_STACK, 11, 22, CRATE_METAL, 14, 22, CRATE_METAL_G, 15, 23, CAR_RED_H, 17, 22);

        rowOf(FENCE_BROKEN_A, 36, 13, 2, 64);
        rowOf(FENCE_BROKEN_B, 44, 13, 2, 64);
        rowOf(FENCE_BROKEN_A, 36, 23, 2, 64);
        rowOf(FENCE_BROKEN_B, 44, 23, 2, 64);
        putLeft(WATER_TOWER, 39, 18);
        barrels(41, 17, BARREL_ORANGE, BARREL_RED, BARREL_ORANGE_G);
        placeLeft(PALLET, 45, 16, CRATE_WOOD, 46, 16, PLANTER_LONG, 44, 20, PLANTER_LONG, 47, 20,
                TIRES_G, 50, 18, BUCKET_WOOD, 49, 21);

        wallRect(3, 29, 5, 2);
        wallRect(10, 29, 4, 2);
        wallRect(16, 29, 4, 2);
        placeLeft(CAR_BURNT_H, 4, 33, CAR_RED_H, 10, 33, VAN_BURNT_H, 16, 33, BARREL_RED_G, 8, 31,
                CRATE_WOOD_G, 14, 31, SIGN_DANGER, 4, 27);
        tyres(19, 25);

        placeLeft(VAN_BURNT_V, 36, 31, VAN_BLUE_H, 39, 33, VAN_BURNT_H, 45, 33, CAR_BURNT_V, 52, 32, CAR_RED_V, 51, 29);
        barrels(42, 29, BARREL_RED, BARREL_ORANGE_G, BARREL_RED_G);
        placeLeft(WATER_TANK_H, 47, 28, PALLET, 38, 28, SIGN_EXIT, 33, 28, STOP_SIGN, 31, 24,
                BARREL_ORANGE_G, 23, 31, TIRES_LOW_G, 31, 33);
    }

    private void buildSurvivorCamp() {
        paint(DIRT, 0, 17, COLS, 3);
        paint(DIRT, 8, 0, 3, ROWS);
        paint(DIRT, 26, 0, 4, ROWS);
        paint(DIRT, 45, 0, 3, ROWS);
        paint(DIRT, 12, 22, 11, 11);
        reserve(1, 17, COLS - 2, 3);
        reserve(8, 1, 3, ROWS - 2);
        reserve(26, 1, 4, ROWS - 2);
        reserve(45, 1, 3, ROWS - 2);

        int[][] blocks = {{1, 7}, {11, 25}, {30, 44}, {48, 54}};
        for (int b = 0; b < blocks.length; b++) {
            int from = blocks[b][0], to = blocks[b][1];
            boolean army = b == 3;
            for (int row : new int[]{5, 15}) {
                int c = from, i = 0;
                while (c + 2 <= to) {
                    PropDef tent = army ? (i % 2 == 0 ? TENT_GREEN : TENT_GREEN_FLAT) : (i % 2 == 0 ? TENT_A : TENT_FLAT);
                    if (!touchesCamp(tent, c, row)) putLeft(tent, c, row);
                    c += tent == TENT_GREEN ? 2 : 3;
                    i++;
                }
            }
            for (int c = from + 1; c + 1 <= to; c += 4) {
                putTile(army ? AMMO_CRATE_H : (c % 8 == from % 8 ? BARREL_WOOD : BOX), c, 9);
                putTile(c % 3 == 0 ? COT_H : COT_V, c + 2, 11);
            }
        }
        placeLeft(LOGS_TIED, 1, 10, LOGS_PAIR, 3, 10, WATER_TANK_V, 23, 10, BUCKET_TOOLS, 20, 12,
                WATER_TANK_H, 41, 10, BUCKET_PIPES, 31, 12,
                STALL_A, 12, 24, STALL_B, 14, 24, SHED, 16, 24, STALL_A, 18, 24, STALL_B, 20, 24,
                STALL_FOOD, 13, 28, STALL_TRADE, 17, 28, TABLE_FOOD, 13, 32, TABLE_TRADE, 17, 32);
        placeTile(BARREL_WOOD, 21, 28, BARREL_BLUE, 22, 28, BOX_SUPPLY, 21, 32, BOX_OPEN, 12, 30);
        decal(PAPERS, 16 * T, 30 * T + 4);
        decal(PAPERS_B, 20 * T, 26 * T + 12);

        placeLeft(CRATE_CLUSTER, 1, 23, BARREL_STACK, 4, 23, PALLET, 6, 23, PIPE_STACK, 1, 27,
                WATER_TANK_V, 3, 27, BOX_PILE, 5, 27, CRATE_CLOSED, 1, 32, CRATE_EMPTY, 2, 32,
                BARREL_STACK, 4, 32, WATER_TANK_H, 6, 33);

        rowOf(WOOD_FENCE, 31, 21, 1, 0);
        rowOf(WOOD_FENCE, 39, 21, 1, 0);
        for (int r = 24; r <= 32; r += 4) {
            placeLeft(PLANTER_LONG, 32, r, PLANTER_LONG, 35, r);
            placeTile(CROP_CORN, 38, r, CROP_WHEAT, 39, r, CROP_BEET, 40, r, CROP_ONION, 41, r);
        }
        putLeft(WATER_TOWER, 43, 26);
        placeTile(PLANTER, 43, 23, PLANTER, 43, 30);
        placeLeft(PLANTER_TALL, 43, 33, SHED, 49, 24, TOOLBOXES, 51, 23, SIDE_TABLE, 53, 25,
                BUCKET_TOOLS, 49, 28, PIPE_STACK, 51, 29, CRATE_CLOSED, 53, 29, BARREL_STACK, 49, 33, GAS_TANK, 52, 33);
    }

    private boolean touchesCamp(PropDef d, int col, int row) {
        Rectangle art = new Rectangle(col * T, (row + 1) * T - d.h(), d.w(), d.h());
        Rectangle camp = new Rectangle(campArea);
        camp.grow(T / 2, 0);
        return art.intersects(camp);
    }

    private void buildBunker() {
        paint(FLOOR, 0, 0, COLS, ROWS);
        wallRect(0, 0, COLS, 2);
        wallRect(0, 0, 1, ROWS);
        wallRect(COLS - 1, 0, 1, ROWS);
        wallRect(0, ROWS - 1, COLS, 1);

        int[] walls = {
            1, 11, 7, 2, 13, 11, 6, 2, 18, 2, 1, 9,
            37, 11, 6, 2, 48, 11, 7, 2, 37, 2, 1, 9,
            1, 24, 7, 2, 13, 24, 6, 2, 18, 26, 1, 9,
            37, 24, 6, 2, 48, 24, 7, 2, 37, 26, 1, 9
        };
        for (int i = 0; i < walls.length; i += 4) wallRect(walls[i], walls[i + 1], walls[i + 2], walls[i + 3]);
        reserve(8, 9, 5, 6);
        reserve(43, 9, 5, 6);
        reserve(8, 22, 5, 6);
        reserve(43, 22, 5, 6);
        reserve(1, 15, COLS - 2, 6);
        reserve(19, 2, 3, ROWS - 3);
        reserve(34, 2, 3, ROWS - 3);

        rowOf(LOCKER, 1, 3, 5, 18);
        putLeft(LOCKER_PAIR, 15, 3);
        for (int c = 2; c <= 14; c += 3) {
            placeLeft(c % 2 == 0 ? BED_V : BED_FRAME_V, c, 6, c % 2 == 0 ? BED_FRAME_V : BED_V, c, 10);
        }
        placeLeft(DRAWERS, 7, 3, DRAWERS_B, 10, 3, CABINET, 16, 7);
        decal(PAPERS, 9 * T, 8 * T + 6);

        placeLeft(GURNEY_CURTAIN, 38, 4, GURNEY_CURTAIN, 40, 4, GURNEY_CURTAIN, 42, 4, GURNEY_CURTAIN, 44, 4,
                WATER_COOLER, 47, 3, CABINET, 48, 3, FRIDGE_DOUBLE, 50, 3, LOCKER_TALL, 52, 3,
                OP_TABLE, 39, 8, OP_TABLE_B, 43, 9, DESK_LAB_V, 49, 8, DESK_PC_V, 51, 8, DESK_LAB_V, 53, 8);

        for (int c = 23; c <= 31; c += 3) placeLeft(DESK_PC_H, c, 5, DESK_H, c, 8);
        rowOf(CABINET, 22, 3, 1, 0);
        placeLeft(DESK_V, 22, 9, DESK_V2, 32, 9, SHELF_WIDE, 31, 3, WATER_COOLER, 29, 3);

        for (int c = 2; c <= 14; c += 3) {
            placeLeft(c % 2 == 0 ? SHELF_STOCKED : SHELF_WIDE, c, 28, c % 2 == 0 ? RACK_STOCKED : RACK, c, 31);
        }
        rowOf(FRIDGE, 2, 34, 3, 32);
        placeLeft(LOCKER_TALL, 7, 34, CUPBOARD, 10, 34, LOCKER_BROKEN, 13, 34, LOCKER_DOUBLE, 15, 34,
                VENDING_RED, 38, 26, VENDING_BLACK, 39, 26, VENDING_COFFEE, 40, 26,
                FRIDGE, 49, 26, FRIDGE_OPEN, 50, 26, WATER_COOLER, 52, 26, SHELF_EMPTY, 53, 26);
        for (int c = 39; c <= 51; c += 4) placeLeft(DESK_H, c, 30, DESK_H, c, 33);

        for (int c = 22; c <= 31; c += 3) putLeft(c % 2 == 0 ? LOCKER_DOUBLE : CABINET, c, 26);
        placeLeft(RACK, 23, 30, RACK_STOCKED, 26, 30, DRAWERS_B, 29, 30, BED_H2, 23, 33, BED_H, 26, 33, BED_FRAME_H2, 29, 33,
                LOCKER_DOUBLE, 1, 15, CUPBOARD, 1, 21, LOCKER_TALL, 53, 15, SHELF_EMPTY, 53, 21);
        decal(PAPERS_B, 14 * T, 18 * T);
        decal(PAPERS, 41 * T, 19 * T);
    }

    private void buildGasStation() {
        paint(DIRT, 11, 4, 34, 9);
        paint(DIRT, 0, 27, COLS, 5);
        paint(DIRT, 15, 13, 5, 14);
        paint(DIRT, 36, 13, 5, 14);
        roadLine(0, COLS - 1, 29 * T + 16, false);
        wallRect(12, 1, 13, 3);
        wallRect(31, 1, 13, 3);
        reserve(1, 28, COLS - 2, 3);
        reserve(16, 11, 3, 17);
        reserve(37, 11, 3, 17);
        reserve(11, 8, 34, 2);

        placeLeft(CRATE_STACK, 12, 5, METAL_STACK_OPEN, 22, 5, BARREL_RED, 25, 4, SIGN_EXIT, 26, 3,
                GARAGE_GATE, 32, 4, GARAGE_GATE, 36, 4, GARAGE_GATE, 40, 4, TOOLBOXES, 34, 5, TIRES_LOW, 38, 5, GAS_TANK, 44, 4,
                PUMP_ROW, 14, 7, PUMP_ROW, 21, 7, PUMP_ROW_BURNT, 33, 7);
        decal(PLANKS, 30 * T, 7 * T);
        placeLeft(CAR_BLUE_V, 13, 12, CAR_RED_V, 21, 12, CAR_BURNT_V, 23, 12, VAN_BURNT_V, 34, 12, CAR_RED_H, 41, 11);

        tyres(1, 2);
        tyres(4, 2);
        tyres(7, 2);
        tyres(1, 6);
        placeLeft(TIRES_G, 5, 6, CAR_BURNT_V, 8, 7, CRATE_WOOD_G, 2, 10, CRATE_STACK_G, 5, 11, PUMP_BURNT, 8, 11, PUMP, 10, 14);

        rowOf(BARREL_OIL, 46, 3, 4, 26);
        rowOf(BARREL_GREY, 46, 5, 4, 26);
        placeLeft(BARREL_STACK, 51, 3, GAS_TANK, 53, 5, BARREL_STACK, 46, 9, BARREL_STACK, 48, 9, PUMP_G, 51, 10, METAL_STACK_G, 46, 12);

        rowOf(CAR_RED_V, 2, 17, 1, 0);
        placeLeft(CAR_BLUE_V, 3, 17, CAR_BURNT_V, 5, 17, CAR_RED_V, 6, 17, VAN_BURNT_V, 9, 17,
                CAR_BURNT_H, 3, 23, VAN_BLUE_H, 8, 23, STOP_SIGN, 13, 25,
                METAL_STACK, 43, 16, METAL_STACK_G, 46, 16, METAL_STACK_OPEN, 50, 16,
                CRATE_STACK, 43, 22, CRATE_STACK_G, 47, 23, CRATE_METAL, 51, 22, CRATE_METAL_G, 52, 23, SIGN_DANGER, 41, 25,
                VAN_BURNT_H, 3, 27, CAR_RED_H, 10, 31, CAR_BURNT_H, 23, 27, VAN_BLUE_H, 29, 31, CAR_BURNT_H, 45, 27, CAR_RED_H, 50, 31);
        rowOf(FENCE_BROKEN_A, 2, 33, 1, 0);
        tyres(8, 33);
        putLeft(FENCE_BROKEN_B, 14, 33);
        barrels(20, 33, BARREL_ORANGE_G, BARREL_RED, BARREL_ORANGE);
        putLeft(FENCE_BROKEN_A, 33, 33);
        tyres(40, 33);
        placeLeft(FENCE_BROKEN_B, 46, 33, CRATE_WOOD_G, 52, 33);
    }

    private void buildQuarantine() {
        paint(DIRT, 0, 17, COLS, 3);
        paint(DIRT, 26, 0, 4, ROWS);
        reserve(1, 17, COLS - 2, 3);
        reserve(26, 1, 4, ROWS - 2);

        compound(3, 3, 6, 10, 2);
        for (int c = 4; c <= 18; c += 4) placeLeft(TENT_GREEN, c, 7, TENT_GREEN_FLAT, c + 1, 12);
        rowOf(COT_V, 4, 9, 7, 64);
        placeLeft(WATER_TANK_V, 19, 9, BOX_MEDS, 6, 4, BOX_WATER, 8, 4);

        compound(33, 3, 6, 10, 2);
        wallRect(35, 4, 9, 3);
        placeLeft(METAL_STACK, 45, 6, CRATE_CLUSTER, 47, 6, AMMO_CRATE_V, 50, 6, BOX_AMMO, 35, 10,
                AMMO_CRATE_H, 38, 9, BARREL_STACK, 45, 10, PALLET, 48, 10, BOX_TOOLS, 50, 10, SIGN_KEEP_OUT, 41, 13);

        compound(3, 22, 6, 9, 2);
        rowOf(BARREL_OIL, 4, 26, 6, 24);
        rowOf(BARREL_GREY, 4, 28, 5, 24);
        placeLeft(GAS_TANK, 12, 26, GAS_TANK, 13, 26, TRASH_C, 15, 26, TRASH_B, 17, 27,
                TRASH_A, 12, 29, SIGN_TOXIC, 19, 25, SIGN_WARNING, 4, 30);

        compound(33, 22, 6, 9, 2);
        placeLeft(SHED, 34, 27, SHED, 37, 27, STALL_A, 40, 27, STALL_B, 43, 27, SHED, 46, 27,
                WATER_TANK_H, 49, 25, BOX_EXTINGUISHER, 34, 30, SIDE_TABLE, 38, 30, BOX_TOOLS, 44, 30, SIGN_ELECTRIC, 49, 29,
                SIGN_QUARANTINE, 2, 15, SIGN_QUARANTINE, 50, 15, SIGN_SHELTER, 23, 14, SIGN_WARNING, 31, 14);
        barrels(22, 23, BARREL_GREY, BARREL_OIL, BARREL_GREY);
        barrels(31, 23, BARREL_OIL, BARREL_GREY, BARREL_OIL);
        placeLeft(CRATE_METAL, 23, 2, CRATE_METAL_G, 31, 2, TRASH_A, 21, 33, TRASH_B, 33, 33, SIGN_WARNING, 23, 32);
        decal(PAPERS, 28 * T, 9 * T);
        decal(PAPERS_B, 14 * T, 18 * T);
        decal(SACK, 40 * T, 20 * T);
    }

    private static final PropDef CHAIN_PANEL = new PropDef(CAMP, 16, 16, 96, 36, 6, 0);
    private static final PropDef CHAIN_PANEL_SIGN = new PropDef(CAMP, 16, 80, 96, 32, 6, 0);
    private static final PropDef WOOD_FENCE = new PropDef(CAMP, 16, 184, 128, 24, 8, 0);

    private void compound(int col, int row, int panels, int height, int gate) {
        int gateStart = (panels - gate) / 2;
        boolean gateSouth = row < ROWS / 2;
        for (int i = 0; i < panels; i++) {
            boolean isGate = i >= gateStart && i < gateStart + gate;
            if (!(gateSouth && isGate)) fencePanel(col + i * 3, row + height, false);
            if (!(!gateSouth && isGate)) fencePanel(col + i * 3, row, i == 1);
        }
        fencePost(col * T, row, height);
        fencePost((col + panels * 3) * T - 6, row, height);
    }

    private void fencePanel(int col, int row, boolean withSign) {
        PropDef d = withSign ? CHAIN_PANEL_SIGN : CHAIN_PANEL;
        put(d, col * T + d.w() / 2.0, (row + 1) * T - 2);
    }

    private void fencePost(int x, int row, int height) {
        BufferedImage seg = AssetLoader.subImage(CAMP, 16, 50, 6, 30);
        int top = (row + 1) * T - 8, bottom = (row + height + 1) * T - 32;
        BufferedImage img = new BufferedImage(6, bottom - top, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        for (int y = 0; y < img.getHeight(); y += seg.getHeight()) g.drawImage(seg, 0, y, null);
        g.dispose();
        Rectangle solid = new Rectangle(x, top, 6, bottom - top);
        if (!playArea.contains(solid)) return;
        props.add(new Prop(new BufferedImage[]{img}, 1, x, top, bottom));
        addSolid(solid);
    }

    private BufferedImage tile(String sheet, int x, int y) { return AssetLoader.subImage(sheet, x, y, T, T); }

    private static final int[] SAND_SPECKS = {0xFFAD7757, 0xFFAD7757, 0xFF9B6658, 0xFFCCA07F};
    private static final int[] DIRT_SPECKS = {0xFF9B6658, 0xFF9B6658, 0xFFC09473, 0xFFCCA07F};
    private static final int[] FLOOR_SPECKS = {0xFF788C8B, 0xFF6C8281, 0xFFA8B5B2};

    private BufferedImage[] speckled(BufferedImage base, int[] palette, int count, int specksPerTile) {
        BufferedImage[] out = new BufferedImage[count];
        for (int i = 0; i < count; i++) {
            BufferedImage img = new BufferedImage(T, T, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.drawImage(base, 0, 0, null);
            g.dispose();
            int n = i == 0 ? 0 : 1 + rng.nextInt(specksPerTile);
            for (int k = 0; k < n; k++) {
                int x = 1 + rng.nextInt(T - 3), y = 1 + rng.nextInt(T - 3);
                int color = palette[rng.nextInt(palette.length)];
                img.setRGB(x, y, color);
                if (rng.nextBoolean()) img.setRGB(x + 1, y, color);
                if (rng.nextInt(4) == 0) img.setRGB(x, y + 1, color);
            }
            out[i] = img;
        }
        return out;
    }

    private void renderGround() {
        ground = new BufferedImage(COLS * T, ROWS * T, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = ground.createGraphics();
        BufferedImage[] sand = speckled(tile(WASTE, 32, 32), SAND_SPECKS, 12, 6);
        BufferedImage[] dirt = speckled(tile(WASTE, 160, 0), DIRT_SPECKS, 8, 4);
        BufferedImage[] dirtRare = {tile(WASTE, 192, 0), tile(WASTE, 192, 32), AssetLoader.flipHorizontal(tile(WASTE, 192, 32))};
        BufferedImage[] floor = speckled(tile(INTER, 32, 64), FLOOR_SPECKS, 8, 3);
        BufferedImage[] floorRare = {tile(INTER, 96, 192), tile(INTER, 128, 192), tile(INTER, 96, 96)};
        Map<Integer, BufferedImage> dirtEdges = new HashMap<>();
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                BufferedImage img;
                int roll = rng.nextInt(100);
                if (terrain[r][c] == FLOOR) {
                    img = roll < 90 ? floor[roll % floor.length] : floorRare[roll % floorRare.length];
                } else if (terrain[r][c] == DIRT) {
                    int mask = sandMask(c, r);
                    img = mask != 0 ? dirtEdges.computeIfAbsent(mask, m -> buildDirtEdge(dirt[0], m))
                            : (roll < 94 ? dirt[roll % dirt.length] : dirtRare[roll % dirtRare.length]);
                } else {
                    img = sand[roll % sand.length];
                }
                g.drawImage(img, c * T, r * T, null);
            }
        }
        g.setColor(new Color(0xCC, 0xA0, 0x7F, 150));
        for (Rectangle d : dashes) g.fill(d);
        for (int[] d : decals) g.drawImage(decalDefs.get(d[0]).images()[0], d[1], d[2], null);
        drawWalls(g);
        if (type != GameData.MapType.ABANDONED_BUNKER) drawFenceBorder(g);
        g.dispose();
    }

    private boolean isSandAt(int c, int r) {
        return c >= 0 && r >= 0 && c < COLS && r < ROWS && terrain[r][c] == SAND;
    }

    private int sandMask(int c, int r) {
        int m = 0;
        if (isSandAt(c, r - 1)) m |= 1;
        if (isSandAt(c + 1, r)) m |= 2;
        if (isSandAt(c, r + 1)) m |= 4;
        if (isSandAt(c - 1, r)) m |= 8;
        if ((m & 3) == 0 && isSandAt(c + 1, r - 1)) m |= 16;
        if ((m & 6) == 0 && isSandAt(c + 1, r + 1)) m |= 32;
        if ((m & 12) == 0 && isSandAt(c - 1, r + 1)) m |= 64;
        if ((m & 9) == 0 && isSandAt(c - 1, r - 1)) m |= 128;
        return m;
    }

    private static BufferedImage buildDirtEdge(BufferedImage base, int mask) {
        BufferedImage north = northLipOverlay();
        BufferedImage[] side = {north, AssetLoader.rotate90(north, true), AssetLoader.flipVertical(north), AssetLoader.rotate90(north, false)};
        BufferedImage out = new BufferedImage(T, T, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(base, 0, 0, null);
        for (int s = 0; s < 4; s++) if ((mask & (1 << s)) != 0) g.drawImage(side[s], 0, 0, null);
        for (int[] cn : new int[][]{{16, 0, 1}, {32, 2, 1}, {64, 2, 3}, {128, 0, 3}}) {
            if ((mask & cn[0]) != 0) g.drawImage(overlap(side[cn[1]], side[cn[2]]), 0, 0, null);
        }
        g.dispose();
        return out;
    }

    private static BufferedImage northLip;

    private static synchronized BufferedImage northLipOverlay() {
        if (northLip != null) return northLip;
        BufferedImage src = AssetLoader.subImage(WASTE, 32, 64, T, T);
        boolean[][] keep = new boolean[T][T];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int x = 0; x < T; x++) {
            if ((src.getRGB(x, 0) & 0xFFFFFF) != DIRT_RGB) {
                keep[0][x] = true;
                queue.add(new int[]{x, 0});
            }
        }
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = p[0] + d[0], ny = p[1] + d[1];
                if (nx >= 0 && ny >= 0 && nx < T && ny < T && !keep[ny][nx] && (src.getRGB(nx, ny) & 0xFFFFFF) != DIRT_RGB) {
                    keep[ny][nx] = true;
                    queue.add(new int[]{nx, ny});
                }
            }
        }
        northLip = new BufferedImage(T, T, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < T; y++) {
            for (int x = 0; x < T; x++) if (keep[y][x]) northLip.setRGB(x, y, src.getRGB(x, y));
        }
        return northLip;
    }

    private static BufferedImage overlap(BufferedImage a, BufferedImage b) {
        BufferedImage out = new BufferedImage(T, T, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < T; y++) {
            for (int x = 0; x < T; x++) {
                if ((a.getRGB(x, y) >>> 24) != 0 && (b.getRGB(x, y) >>> 24) != 0) out.setRGB(x, y, a.getRGB(x, y));
            }
        }
        return out;
    }

    private static final Color WALL_OUTLINE = new Color(0x1F2E33), WALL_TOP = new Color(0x465E62), WALL_RIM = new Color(0x6C8281);

    private boolean isWall(int c, int r) { return c < 0 || r < 0 || c >= COLS || r >= ROWS || wall[r][c]; }

    private void drawWalls(Graphics2D g) {
        BufferedImage face = tile(INTER, 32, 160);
        BufferedImage[] faceDeco = {tile(INTER, 160, 96), tile(INTER, 192, 96), tile(INTER, 160, 128)};
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (!wall[r][c]) continue;
                int x = c * T, y = r * T;
                if (!isWall(c, r + 1)) {
                    g.drawImage(rng.nextInt(6) == 0 ? faceDeco[rng.nextInt(faceDeco.length)] : face, x, y, null);
                    g.setColor(WALL_OUTLINE);
                    if (!isWall(c - 1, r)) g.fillRect(x, y, 2, T);
                    if (!isWall(c + 1, r)) g.fillRect(x + T - 2, y, 2, T);
                    if (!isWall(c, r - 1)) g.fillRect(x, y, T, 2);
                } else {
                    g.setColor(WALL_TOP);
                    g.fillRect(x, y, T, T);
                    g.setColor(WALL_RIM);
                    if (!isWall(c, r - 1)) g.fillRect(x, y + 2, T, 1);
                    if (!isWall(c - 1, r)) g.fillRect(x + 2, y, 1, T);
                    if (!isWall(c + 1, r)) g.fillRect(x + T - 3, y, 1, T);
                    if (!isWall(c, r + 1)) g.fillRect(x, y + T - 3, T, 1);
                    g.setColor(WALL_OUTLINE);
                    if (!isWall(c, r - 1)) g.fillRect(x, y, T, 2);
                    if (!isWall(c - 1, r)) g.fillRect(x, y, 2, T);
                    if (!isWall(c + 1, r)) g.fillRect(x + T - 2, y, 2, T);
                    if (!isWall(c, r + 1)) g.fillRect(x, y + T - 2, T, 2);
                }
            }
        }
    }

    private void drawFenceBorder(Graphics2D g) {
        boolean palisade = type == GameData.MapType.SURVIVOR_CAMP;
        BufferedImage panel = palisade ? LOGS_TIED.images()[0] : CHAIN_PANEL.images()[0];
        BufferedImage post = AssetLoader.subImage(CAMP, 16, 50, 6, 30);
        int step = palisade ? 24 : panel.getWidth();
        for (int x = -4; x < COLS * T; x += step) {
            g.drawImage(panel, x, T - panel.getHeight() + (palisade ? 6 : 2), null);
            g.drawImage(panel, x, ROWS * T - panel.getHeight() + (palisade ? 4 : 0), null);
        }
        for (int y = 0; y < ROWS * T; y += post.getHeight()) {
            if (palisade) {
                BufferedImage log = LOG_SINGLE.images()[0];
                g.drawImage(log, T - 14, y, null);
                g.drawImage(log, COLS * T - T + 3, y, null);
            } else {
                g.drawImage(post, T - 6, y, null);
                g.drawImage(post, COLS * T - T, y, null);
            }
        }
    }

    private void computeWalkable() {
        walkable = new boolean[ROWS][COLS];
        openEast = new boolean[ROWS][COLS];
        openSouth = new boolean[ROWS][COLS];
        Rectangle probe = new Rectangle();
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                probe.setBounds(c * T + 6, r * T + 10, T - 12, 12);
                walkable[r][c] = !isBlocked(probe);
            }
        }
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                double cx = c * T + T / 2.0, cy = r * T + T / 2.0;
                openEast[r][c] = c + 1 < COLS && walkable[r][c] && walkable[r][c + 1] && hasClearPath(cx, cy, cx + T, cy);
                openSouth[r][c] = r + 1 < ROWS && walkable[r][c] && walkable[r + 1][c] && hasClearPath(cx, cy, cx, cy + T);
            }
        }
    }

    private boolean isLinked(int c, int r, int nc, int nr) {
        if (!isWalkableTile(c, r) || !isWalkableTile(nc, nr)) return false;
        return nr == r ? openEast[r][Math.min(c, nc)] : openSouth[Math.min(r, nr)][c];
    }

    private void findSpawnPoints() {
        int[][] fromCamp = new int[ROWS][COLS];
        fillDistances(fromCamp, playerSpawn.x / T, playerSpawn.y / T);
        reachable = new boolean[ROWS][COLS];
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) reachable[r][c] = fromCamp[r][c] != FAR;
        }
        GameData.MonsterType big = GameData.MonsterType.ZOMBIE_BOSS;
        for (int r = 1; r < ROWS - 1; r++) {
            for (int c = 1; c < COLS - 1; c++) {
                if (fromCamp[r][c] == FAR) continue;
                Point p = new Point(c * T + T / 2, r * T + 8);
                if (safeHub ? (r > 3 && c > 3 && r < ROWS - 4 && c < COLS - 4)
                        : Math.hypot(p.x - playerSpawn.x, p.y - playerSpawn.y) < 175.0) {
                    continue;
                }
                Rectangle bossFeet = new Rectangle(p.x - big.hitboxW / 2, p.y + big.hitboxH / 2 - 8, big.hitboxW, 8);
                Rectangle smallFeet = new Rectangle(p.x - 7, p.y + 1, 14, 8);
                if (!isBlocked(bossFeet) && !isBlocked(smallFeet)) spawnPoints.add(p);
            }
        }
        if (spawnPoints.isEmpty()) spawnPoints.add(new Point(playerSpawn));
    }

    private void renderMinimap() {
        minimap = new BufferedImage(COLS, ROWS, BufferedImage.TYPE_INT_ARGB);
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int rgb = (wall[r][c] || r == 0 || c == 0 || r == ROWS - 1 || c == COLS - 1) ? 0xFF2A2A30
                        : !walkable[r][c] ? 0xFF5A4636
                        : terrain[r][c] == FLOOR ? 0xFF8EA39D
                        : terrain[r][c] == DIRT ? 0xFFAD7757 : 0xFFC09473;
                minimap.setRGB(c, r, rgb);
            }
        }
    }
}
