// Tapatsawin Chaieua          6813105
// Thanapon Thanadunpremdet    6813106
// Tanawat Wongwad             6813107
// Natthanon Achachan          6813241
package Project3_6813107;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The arena: a tile map 56 x 36 tiles (32 px each) with a pre-rendered ground picture, props
 * (cars, tents, lockers ...) that block movement with their lower part, a wall/fence border,
 * the central camp where the player starts, monster spawn points near the edges, and a
 * "flow field" that tells monsters which way to walk around obstacles towards the player.
 *
 * Every layout is built by hand-written rules (roads, rows of tents, rooms, fenced compounds),
 * with a seeded Random only for small variations, so a map always looks the same.
 */
public final class GameMap {

    public static final int COLS = 56, ROWS = 36;
    private static final int T = GameData.TILE;

    // ------------------------------------------------------------------ drawing order
    /** Anything drawn in the world layer that must be sorted by its feet line (props and entities). */
    public interface Drawable {
        double getSortY();

        void draw(Graphics2D g2);
    }

    /** Sorts drawables so things lower on the screen are drawn later (in front). */
    public static final Comparator<Drawable> DEPTH_ORDER = Comparator.comparingDouble(Drawable::getSortY);

    // ------------------------------------------------------------------ props
    /** A placed object of the map. solid is the blocking footprint (its lower part) or null. */
    public static final class Prop implements Drawable {
        private final String name;
        private final BufferedImage[] frames;
        private final int ticksPerFrame;
        private final int drawX, drawY;
        private final Rectangle solid;
        private final double sortY;
        private int animTick;

        Prop(String name, BufferedImage[] frames, int ticksPerFrame, int drawX, int drawY, Rectangle solid, double sortY) {
            this.name = name;
            this.frames = frames;
            this.ticksPerFrame = ticksPerFrame;
            this.drawX = drawX;
            this.drawY = drawY;
            this.solid = solid;
            this.sortY = sortY;
        }

        public String getName() {
            return name;
        }

        public BufferedImage getImage() {
            return frames[(animTick / Math.max(1, ticksPerFrame)) % frames.length];
        }

        public BufferedImage[] getFrames() {
            return frames.clone();
        }

        public int getDrawX() {
            return drawX;
        }

        public int getDrawY() {
            return drawY;
        }

        /** Blocking footprint in world pixels (a copy), or null if you can walk through it. */
        public Rectangle getSolid() {
            return solid == null ? null : new Rectangle(solid);
        }

        /** The whole picture's rectangle in world pixels (for culling). */
        public Rectangle getDrawBounds() {
            return new Rectangle(drawX, drawY, frames[0].getWidth(), frames[0].getHeight());
        }

        public boolean isAnimated() {
            return frames.length > 1;
        }

        @Override
        public double getSortY() {
            return sortY;
        }

        /** Advances the animation by one tick (only animated props need this). */
        public void update() {
            animTick++;
        }

        @Override
        public void draw(Graphics2D g2) {
            g2.drawImage(getImage(), drawX, drawY, null);
        }
    }

    /** A catalog entry: where the art is on its sheet and how big its blocking footprint is. */
    private static final class PropDef {
        final String name;
        final String sheet;
        final int sx, sy, w, h;
        final int footH;       // height of the solid footprint measured from the art's bottom (0 = walk-through)
        final int inset;       // footprint is this much narrower on each side than the art
        final int frames;      // > 1 = horizontal animation strip starting at (sx, sy)
        final int padBottom;   // empty rows under the art inside each frame
        final int ticksPerFrame;

        PropDef(String name, String sheet, int sx, int sy, int w, int h, int footH, int inset) {
            this(name, sheet, sx, sy, w, h, footH, inset, 1, 0, 1);
        }

        PropDef(String name, String sheet, int sx, int sy, int w, int h, int footH, int inset,
                int frames, int padBottom, int ticksPerFrame) {
            this.name = name;
            this.sheet = sheet;
            this.sx = sx;
            this.sy = sy;
            this.w = w;
            this.h = h;
            this.footH = footH;
            this.inset = inset;
            this.frames = frames;
            this.padBottom = padBottom;
            this.ticksPerFrame = ticksPerFrame;
        }

        BufferedImage[] images() {
            if (frames > 1) {
                return AssetLoader.loadStrip(sheet, frames);
            }
            return new BufferedImage[]{AssetLoader.subImage(sheet, sx, sy, w, h)};
        }
    }

    private static final String WASTE = "tiles/wasteland.png";
    private static final String CAMP = "tiles/camp.png";
    private static final String INTER = "tiles/interior.png";

    // Wasteland sheet: vehicles, crates, barrels, tyres, pumps, signs
    private static final PropDef CAR_RED_V = new PropDef("red car", WASTE, 0, 164, 32, 59, 46, 2);
    private static final PropDef CAR_RED_H = new PropDef("red car", WASTE, 32, 185, 64, 38, 24, 2);
    private static final PropDef CAR_BLUE_V = new PropDef("blue car", WASTE, 0, 224, 32, 64, 50, 2);
    private static final PropDef VAN_BLUE_H = new PropDef("blue van", WASTE, 32, 248, 77, 40, 26, 2);
    private static final PropDef CAR_BURNT_V = new PropDef("burnt car", WASTE, 0, 292, 32, 59, 46, 2);
    private static final PropDef CAR_BURNT_H = new PropDef("burnt car", WASTE, 32, 313, 63, 37, 24, 2);
    private static final PropDef VAN_BURNT_V = new PropDef("burnt van", WASTE, 1, 354, 30, 62, 48, 2);
    private static final PropDef VAN_BURNT_H = new PropDef("burnt van", WASTE, 32, 376, 76, 39, 26, 2);
    private static final PropDef CRATE_METAL = new PropDef("metal crate", WASTE, 131, 162, 26, 27, 16, 1);
    private static final PropDef CRATE_METAL_G = new PropDef("metal crate", WASTE, 160, 162, 32, 31, 16, 4);
    private static final PropDef CRATE_WOOD = new PropDef("wood crate", WASTE, 131, 194, 26, 27, 16, 1);
    private static final PropDef CRATE_WOOD_G = new PropDef("wood crate", WASTE, 160, 194, 32, 31, 16, 4);
    private static final PropDef BARREL_RED = new PropDef("red barrel", WASTE, 132, 225, 24, 29, 12, 2);
    private static final PropDef BARREL_RED_G = new PropDef("red barrel", WASTE, 162, 225, 27, 32, 12, 4);
    private static final PropDef BARREL_ORANGE = new PropDef("orange barrel", WASTE, 132, 257, 24, 29, 12, 2);
    private static final PropDef BARREL_ORANGE_G = new PropDef("orange barrel", WASTE, 162, 257, 27, 32, 12, 4);
    private static final PropDef GARAGE_GATE = new PropDef("garage gate", WASTE, 201, 178, 46, 78, 22, 0);
    private static final PropDef PLANKS = new PropDef("planks", WASTE, 196, 265, 24, 18, 0, 0);
    private static final PropDef STOP_SIGN = new PropDef("stop sign", WASTE, 229, 260, 22, 26, 5, 7);
    private static final PropDef TIRES = new PropDef("tyres", WASTE, 130, 289, 28, 30, 14, 1);
    private static final PropDef TIRES_LOW = new PropDef("tyres", WASTE, 162, 293, 28, 24, 12, 1);
    private static final PropDef TIRES_G = new PropDef("tyres", WASTE, 192, 289, 32, 31, 14, 3);
    private static final PropDef TIRES_LOW_G = new PropDef("tyres", WASTE, 225, 293, 31, 27, 12, 3);
    private static final PropDef FENCE_BROKEN_A = new PropDef("broken fence", WASTE, 128, 324, 63, 28, 8, 2);
    private static final PropDef FENCE_BROKEN_B = new PropDef("broken fence", WASTE, 198, 324, 53, 29, 8, 2);
    private static final PropDef PUMP = new PropDef("fuel pump", WASTE, 141, 369, 35, 46, 16, 3);
    private static final PropDef PUMP_G = new PropDef("fuel pump", WASTE, 205, 369, 38, 47, 16, 5);
    private static final PropDef PUMP_ROW = new PropDef("pump island", WASTE, 0, 426, 128, 54, 20, 2);
    private static final PropDef PUMP_ROW_BURNT = new PropDef("burnt pump island", WASTE, 0, 490, 128, 54, 20, 2);
    private static final PropDef PUMP_BURNT = new PropDef("burnt pump", WASTE, 13, 561, 38, 47, 16, 4);
    private static final PropDef CRATE_STACK = new PropDef("crate stack", WASTE, 134, 437, 51, 40, 22, 1);
    private static final PropDef CRATE_STACK_G = new PropDef("crate stack", WASTE, 195, 437, 57, 44, 22, 4);
    private static final PropDef METAL_STACK = new PropDef("metal crates", WASTE, 134, 499, 51, 41, 22, 1);
    private static final PropDef METAL_STACK_G = new PropDef("metal crates", WASTE, 196, 499, 55, 43, 22, 4);
    private static final PropDef METAL_STACK_OPEN = new PropDef("open crates", WASTE, 134, 564, 51, 41, 22, 1);
    private static final PropDef SIGN_EXIT = new PropDef("exit sign", WASTE, 69, 546, 54, 30, 5, 6);
    private static final PropDef SIGN_DANGER = new PropDef("danger sign", WASTE, 69, 578, 54, 30, 5, 6);
    private static final PropDef TABLE_WORK = new PropDef("workbench", WASTE, 257, 64, 31, 32, 14, 0);
    private static final PropDef TABLE_MED = new PropDef("medical table", WASTE, 257, 32, 31, 32, 14, 0);
    private static final PropDef DESK_RADIO = new PropDef("radio desk", WASTE, 257, 96, 31, 32, 14, 0);

    // Camp sheet: tents, stalls, fences, boxes, barrels, signs, farm plots, logs
    private static final PropDef TENT_A = new PropDef("tent", CAMP, 28, 229, 68, 75, 44, 6);
    private static final PropDef TENT_FLAT = new PropDef("tent", CAMP, 112, 250, 64, 54, 36, 2);
    private static final PropDef TENT_GREEN = new PropDef("army tent", CAMP, 30, 336, 36, 64, 34, 2);
    private static final PropDef TENT_GREEN_FLAT = new PropDef("army tent", CAMP, 80, 357, 63, 40, 28, 2);
    private static final PropDef STALL_A = new PropDef("market stall", CAMP, 144, 325, 57, 75, 16, 6);
    private static final PropDef STALL_B = new PropDef("market stall", CAMP, 209, 325, 57, 75, 16, 6);
    private static final PropDef SHED = new PropDef("shed", CAMP, 276, 322, 50, 78, 16, 4);
    private static final PropDef BOX = new PropDef("box", CAMP, 22, 438, 20, 26, 10, 1);
    private static final PropDef PALLET = new PropDef("pallet", CAMP, 49, 432, 30, 31, 12, 1);
    private static final PropDef BUCKET_TOOLS = new PropDef("tool bucket", CAMP, 81, 426, 29, 38, 10, 5);
    private static final PropDef BUCKET_PIPES = new PropDef("pipe bucket", CAMP, 113, 424, 29, 40, 10, 5);
    private static final PropDef BUCKET_WOOD = new PropDef("wood bucket", CAMP, 149, 426, 22, 38, 10, 3);
    private static final PropDef SIGN_ELECTRIC = new PropDef("warning sign", CAMP, 213, 434, 24, 30, 4, 9);
    private static final PropDef SIGN_SHELTER = new PropDef("shelter sign", CAMP, 243, 432, 27, 32, 4, 10);
    private static final PropDef SIGN_KEEP_OUT = new PropDef("keep out sign", CAMP, 287, 433, 72, 31, 5, 8);
    private static final PropDef SIGN_WARNING = new PropDef("warning sign", CAMP, 213, 466, 24, 30, 4, 9);
    private static final PropDef SIGN_TOXIC = new PropDef("toxic sign", CAMP, 245, 466, 24, 30, 4, 9);
    private static final PropDef SIGN_QUARANTINE = new PropDef("quarantine sign", CAMP, 287, 466, 72, 30, 5, 8);
    private static final PropDef BOX_OPEN = new PropDef("open box", CAMP, 18, 470, 28, 26, 12, 2);
    private static final PropDef BOX_PILE = new PropDef("box pile", CAMP, 55, 480, 37, 36, 16, 1);
    private static final PropDef BOX_SUPPLY = new PropDef("supply boxes", CAMP, 99, 482, 38, 35, 14, 2);
    private static final PropDef WATER_TANK_H = new PropDef("water tank", CAMP, 144, 474, 32, 21, 12, 1);
    private static final PropDef WATER_TANK_V = new PropDef("water tank", CAMP, 181, 464, 22, 32, 16, 1);
    private static final PropDef BOX_WATER = new PropDef("water box", CAMP, 18, 502, 28, 26, 12, 2);
    private static final PropDef COT_V = new PropDef("cot", CAMP, 151, 496, 18, 32, 0, 0);
    private static final PropDef COT_H = new PropDef("cot", CAMP, 176, 509, 32, 18, 0, 0);
    private static final PropDef TOOLBOXES = new PropDef("toolboxes", CAMP, 240, 496, 46, 32, 12, 2);
    private static final PropDef BOX_TOOLS = new PropDef("tool box", CAMP, 18, 536, 29, 24, 12, 2);
    private static final PropDef BOX_EXTINGUISHER = new PropDef("box", CAMP, 49, 531, 30, 29, 12, 2);
    private static final PropDef BOX_AMMO = new PropDef("ammo box", CAMP, 83, 529, 28, 31, 12, 2);
    private static final PropDef BOX_MEDS = new PropDef("med box", CAMP, 114, 534, 29, 26, 12, 2);
    private static final PropDef GAS_TANK = new PropDef("gas tank", CAMP, 21, 578, 23, 34, 14, 1);
    private static final PropDef CRATE_CLUSTER = new PropDef("crates", CAMP, 48, 574, 63, 45, 22, 2);
    private static final PropDef AMMO_CRATE_V = new PropDef("ammo crate", CAMP, 112, 583, 13, 38, 14, 0);
    private static final PropDef AMMO_CRATE_H = new PropDef("ammo crate", CAMP, 145, 603, 30, 18, 12, 0);
    private static final PropDef BARREL_STACK = new PropDef("barrels", CAMP, 190, 580, 37, 40, 18, 2);
    private static final PropDef SIDE_TABLE = new PropDef("table", CAMP, 21, 628, 23, 28, 10, 1);
    private static final PropDef CRATE_EMPTY = new PropDef("crate", CAMP, 52, 625, 24, 30, 14, 1);
    private static final PropDef CRATE_CLOSED = new PropDef("crate", CAMP, 84, 625, 24, 30, 14, 1);
    private static final PropDef BARREL_WOOD = new PropDef("barrel", CAMP, 117, 626, 22, 29, 12, 2);
    private static final PropDef BARREL_GREY = new PropDef("barrel", CAMP, 149, 626, 22, 29, 12, 2);
    private static final PropDef BARREL_BLUE = new PropDef("barrel", CAMP, 180, 624, 22, 31, 12, 2);
    private static final PropDef BARREL_OIL = new PropDef("oil barrel", CAMP, 212, 624, 22, 31, 12, 2);
    private static final PropDef STALL_FOOD = new PropDef("food stall", CAMP, 18, 669, 60, 40, 14, 2);
    private static final PropDef STALL_TRADE = new PropDef("trade stall", CAMP, 80, 667, 64, 42, 14, 2);
    private static final PropDef TABLE_FOOD = new PropDef("food table", CAMP, 146, 677, 60, 32, 14, 2);
    private static final PropDef TABLE_TRADE = new PropDef("trade table", CAMP, 208, 677, 64, 32, 14, 2);
    private static final PropDef PIPE_STACK = new PropDef("pipes", CAMP, 278, 666, 21, 54, 10, 2);
    private static final PropDef WATER_TOWER = new PropDef("water tower", CAMP, 18, 722, 28, 62, 14, 1);
    private static final PropDef PLANTER = new PropDef("planter", CAMP, 84, 726, 24, 26, 12, 1);
    private static final PropDef PLANTER_LONG = new PropDef("planter", CAMP, 48, 760, 64, 24, 12, 1);
    private static final PropDef PLANTER_TALL = new PropDef("planter", CAMP, 116, 720, 24, 64, 50, 1);
    private static final PropDef CROP_BEET = new PropDef("crop", CAMP, 148, 725, 24, 27, 12, 1);
    private static final PropDef CROP_ONION = new PropDef("crop", CAMP, 180, 723, 24, 29, 12, 1);
    private static final PropDef CROP_WHEAT = new PropDef("crop", CAMP, 148, 756, 24, 28, 12, 1);
    private static final PropDef CROP_CORN = new PropDef("crop", CAMP, 180, 756, 24, 28, 12, 1);
    private static final PropDef LOGS_PAIR = new PropDef("logs", CAMP, 278, 721, 21, 63, 14, 1);
    private static final PropDef LOGS_TIED = new PropDef("logs", CAMP, 304, 721, 32, 63, 14, 1);
    private static final PropDef LOG_SINGLE = new PropDef("log", CAMP, 347, 726, 11, 58, 12, 0);
    private static final PropDef TRASH_A = new PropDef("trash", CAMP, 244, 552, 25, 39, 0, 0);
    private static final PropDef TRASH_B = new PropDef("trash", CAMP, 272, 556, 34, 35, 0, 0);
    private static final PropDef TRASH_C = new PropDef("trash", CAMP, 317, 552, 38, 39, 0, 0);
    private static final PropDef PAPERS = new PropDef("papers", CAMP, 289, 497, 15, 15, 0, 0);
    private static final PropDef PAPERS_B = new PropDef("papers", CAMP, 306, 497, 12, 15, 0, 0);
    private static final PropDef SACK = new PropDef("sack", CAMP, 304, 513, 16, 15, 0, 0);

    // Interior sheet: bunker furniture
    private static final PropDef CABINET = new PropDef("cabinet", INTER, 0, 224, 32, 45, 18, 0);
    private static final PropDef LOCKER_PAIR = new PropDef("lockers", INTER, 47, 224, 34, 52, 18, 0);
    private static final PropDef CUPBOARD = new PropDef("cupboard", INTER, 111, 224, 34, 45, 18, 0);
    private static final PropDef LOCKER_TALL = new PropDef("tall lockers", INTER, 175, 224, 34, 59, 18, 0);
    private static final PropDef SHELF_EMPTY = new PropDef("shelf", INTER, 227, 229, 26, 49, 14, 0);
    private static final PropDef SHELF_WIDE = new PropDef("shelf", INTER, 268, 228, 40, 49, 14, 0);
    private static final PropDef LOCKER = new PropDef("locker", INTER, 8, 288, 17, 45, 14, 0);
    private static final PropDef LOCKER_BROKEN = new PropDef("broken locker", INTER, 40, 288, 22, 46, 14, 0);
    private static final PropDef LOCKER_DOUBLE = new PropDef("lockers", INTER, 79, 288, 34, 45, 14, 0);
    private static final PropDef FRIDGE = new PropDef("fridge", INTER, 130, 288, 28, 44, 16, 0);
    private static final PropDef FRIDGE_OPEN = new PropDef("open fridge", INTER, 161, 288, 29, 45, 16, 0);
    private static final PropDef FRIDGE_DOUBLE = new PropDef("fridges", INTER, 207, 288, 34, 48, 16, 0);
    private static final PropDef SHELF_STOCKED = new PropDef("shelf", INTER, 268, 295, 40, 49, 14, 0);
    private static final PropDef DESK_V = new PropDef("desk", INTER, 0, 353, 26, 48, 30, 0);
    private static final PropDef DESK_V2 = new PropDef("desk", INTER, 38, 353, 26, 48, 30, 0);
    private static final PropDef DESK_H = new PropDef("desk", INTER, 71, 354, 50, 30, 16, 0);
    private static final PropDef VENDING_RED = new PropDef("vending machine", INTER, 128, 352, 32, 42, 16, 0);
    private static final PropDef VENDING_BLACK = new PropDef("vending machine", INTER, 160, 352, 32, 42, 16, 0);
    private static final PropDef VENDING_COFFEE = new PropDef("coffee machine", INTER, 193, 352, 30, 45, 16, 0);
    private static final PropDef RACK = new PropDef("rack", INTER, 225, 364, 30, 32, 12, 0);
    private static final PropDef RACK_STOCKED = new PropDef("rack", INTER, 257, 364, 30, 32, 12, 0);
    private static final PropDef DESK_PC_V = new PropDef("computer desk", INTER, 0, 417, 26, 50, 30, 0);
    private static final PropDef DESK_LAB_V = new PropDef("lab desk", INTER, 38, 416, 26, 51, 30, 0);
    private static final PropDef DESK_PC_H = new PropDef("computer desk", INTER, 71, 416, 50, 32, 16, 0);
    private static final PropDef DRAWERS = new PropDef("drawers", INTER, 132, 438, 23, 22, 12, 0);
    private static final PropDef DRAWERS_B = new PropDef("drawers", INTER, 165, 438, 23, 22, 12, 0);
    private static final PropDef GURNEY_CURTAIN = new PropDef("ward bed", INTER, 192, 432, 45, 46, 18, 0);
    private static final PropDef WATER_COOLER = new PropDef("water cooler", INTER, 264, 425, 16, 30, 10, 0);
    private static final PropDef BED_V = new PropDef("bed", INTER, 4, 480, 24, 39, 34, 0);
    private static final PropDef BED_H = new PropDef("bed", INTER, 57, 483, 39, 29, 20, 0);
    private static final PropDef BED_FRAME_V = new PropDef("bed frame", INTER, 100, 480, 24, 39, 34, 0);
    private static final PropDef BED_FRAME_H = new PropDef("bed frame", INTER, 153, 483, 39, 29, 20, 0);
    private static final PropDef OP_TABLE = new PropDef("operating table", INTER, 217, 480, 39, 27, 18, 0);
    private static final PropDef OP_TABLE_B = new PropDef("operating table", INTER, 192, 496, 39, 43, 18, 0);
    private static final PropDef BED_H2 = new PropDef("bed", INTER, 32, 515, 39, 29, 20, 0);
    private static final PropDef BED_FRAME_H2 = new PropDef("bed frame", INTER, 128, 515, 39, 29, 20, 0);

    // Animated camp props (strips)
    private static final PropDef FLAME_BARREL = new PropDef("flame barrel", "tiles/flame_barrel.png", 0, 0, 64, 64, 12, 22, 7, 5, 6);
    private static final PropDef FLAG = new PropDef("camp flag", "tiles/flag.png", 0, 0, 64, 64, 10, 21, 7, 5, 7);
    private static final PropDef NPC_TRADER = new PropDef("trader", "characters/npc_idle.png", 0, 0, 32, 32, 6, 9, 6, 0, 9);
    private static final PropDef NPC_GUARD = new PropDef("guard", "characters/npc2_idle.png", 0, 0, 32, 32, 6, 9, 6, 0, 10);

    // ------------------------------------------------------------------ ground tiles
    private static final int SAND = 0, DIRT = 1, FLOOR = 2;
    private static final int DIRT_RGB = 0xAD7757;

    private final GameData.MapType type;
    private final boolean safeHub;
    private final Random rng;
    private final int[][] terrain = new int[ROWS][COLS];
    private final boolean[][] wall = new boolean[ROWS][COLS];   // bunker walls (solid, drawn into the ground)
    private BufferedImage ground;
    private BufferedImage minimap;
    private final List<Prop> props = new ArrayList<>();
    private final List<Prop> animatedProps = new ArrayList<>();
    private final List<Rectangle> solids = new ArrayList<>();
    private final List<List<Rectangle>> solidsByCell = new ArrayList<>();
    private final List<Rectangle> keepClear = new ArrayList<>();
    private final List<int[]> decals = new ArrayList<>();          // {defIndex, x, y}
    private final List<PropDef> decalDefs = new ArrayList<>();
    private final List<Rectangle> dashes = new ArrayList<>();             // faded road markings
    private final Rectangle playArea = new Rectangle(T, T, (COLS - 2) * T, (ROWS - 2) * T);
    private Rectangle campArea;          // world pixels
    private Point playerSpawn;
    private Point gatePosition;
    private String chamberLayoutName = "SAFE CAMP HUB";
    private final List<Point> spawnPoints = new ArrayList<>();
    private final List<Point> guardPosts = new ArrayList<>();
    private Point traderPosition;
    private int appliedTurretLv;
    private int appliedWallLv;
    private int appliedMedicLv;
    private int appliedFarmLv;
    private boolean[][] walkable;
    private boolean[][] reachable;
    private boolean[][] openEast;     // can walk from (c, r) to (c + 1, r)
    private boolean[][] openSouth;    // can walk from (c, r) to (c, r + 1)

    // flow field towards the player
    private final int[][] flow = new int[ROWS][COLS];
    private int flowCol = -1, flowRow = -1;
    private static final int FAR = Integer.MAX_VALUE;

    // ------------------------------------------------------------------ construction
    public GameMap(GameData.MapType type, long seed) {
        this(type, seed, true);
    }

    public GameMap(GameData.MapType type, long seed, boolean safeHub) {
        this.type = type;
        this.safeHub = safeHub;
        this.rng = new Random(seed);
        for (int i = 0; i < ROWS * COLS; i++) {
            solidsByCell.add(new ArrayList<>());
        }
        int campCol = COLS / 2 - 6, campRow = ROWS / 2 - 5;
        campArea = new Rectangle(campCol * T, campRow * T, 12 * T, 10 * T);
        playerSpawn = new Point(campArea.x + campArea.width / 2, campArea.y + campArea.height / 2 + T);
        gatePosition = new Point(campArea.x + campArea.width / 2, campArea.y + campArea.height - 10);
        if (safeHub) {
            buildCamp();
            keepClear.add(new Rectangle(campArea.x + T, campArea.y + 3 * T, campArea.width - 2 * T, campArea.height - 3 * T));
            switch (type) {
                case WASTELAND_HIGHWAY: buildHighway(); break;
                case SURVIVOR_CAMP: buildSurvivorCamp(); break;
                case ABANDONED_BUNKER: buildBunker(); break;
                case GAS_STATION: buildGasStation(); break;
                default: buildQuarantine(); break;
            }
        } else {
            buildProceduralChamber();
        }
        renderGround();
        computeWalkable();
        findSpawnPoints();
        renderMinimap();
    }

    // ------------------------------------------------------------------ public API
    public String getChamberLayoutName() {
        return chamberLayoutName;
    }

    public GameData.MapType getType() {
        return type;
    }

    public int getWidthPx() {
        return COLS * T;
    }

    public int getHeightPx() {
        return ROWS * T;
    }

    /** True if the rectangle leaves the walkable area or touches a solid footprint / wall. */
    public boolean isBlocked(Rectangle r) {
        if (!playArea.contains(r)) {
            return true;
        }
        int c0 = r.x / T, c1 = (r.x + r.width - 1) / T;
        int r0 = r.y / T, r1 = (r.y + r.height - 1) / T;
        for (int row = r0; row <= r1; row++) {
            for (int col = c0; col <= c1; col++) {
                if (wall[row][col]) {
                    return true;
                }
                for (Rectangle s : solidsByCell.get(row * COLS + col)) {
                    if (s.intersects(r)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Where the player starts: the middle of the camp clearing. */
    public Point getPlayerSpawn() {
        return new Point(playerSpawn);
    }

    /** The camp clearing in world pixels. */
    public Rectangle getCampArea() {
        return new Rectangle(campArea);
    }

    /**
     * A free spot near the map edge that is not visible in cameraView (world pixels), for spawning
     * a monster. Falls back to the spawn point farthest from the view if every point is visible.
     */
    public Point randomSpawnPoint(Random random, Rectangle cameraView) {
        Rectangle hidden = new Rectangle(cameraView);
        hidden.grow(48, 48);
        List<Point> choices = new ArrayList<>();
        for (Point p : spawnPoints) {
            if (!hidden.contains(p)) {
                choices.add(p);
            }
        }
        if (!choices.isEmpty()) {
            return new Point(choices.get(random.nextInt(choices.size())));
        }
        Point best = spawnPoints.get(0);
        double bestDist = -1;
        double cx = cameraView.getCenterX(), cy = cameraView.getCenterY();
        for (Point p : spawnPoints) {
            double d = Math.hypot(p.x - cx, p.y - cy);
            if (d > bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return new Point(best);
    }

    /** All spawn points (tile centres near the edges, reachable from the camp). */
    public List<Point> getSpawnPoints() {
        List<Point> copy = new ArrayList<>();
        for (Point p : spawnPoints) {
            copy.add(new Point(p));
        }
        return copy;
    }

    /** World coordinates of the camp trader/workshop NPC. */
    public Point getTraderPosition() {
        return traderPosition != null ? new Point(traderPosition) : new Point(playerSpawn);
    }

    /** True if this map hosts the Safe Camp Hub (false in procedural combat chambers). */
    public boolean hasSafeCamp() {
        return safeHub;
    }

    /** Expedition Gate location at the Safe Camp entrance. */
    public Point getGatePosition() {
        return gatePosition != null ? new Point(gatePosition) : new Point(playerSpawn);
    }

    /** Random reachable walkable tile at least minDist pixels away from (fromX, fromY). */
    public Point randomWalkablePoint(Random random, double fromX, double fromY, double minDist) {
        for (int tries = 0; tries < 90; tries++) {
            int col = 3 + random.nextInt(COLS - 6);
            int row = 3 + random.nextInt(ROWS - 6);
            if (!isWalkableTile(col, row) || (reachable != null && !reachable[row][col])) {
                continue;
            }
            double px = col * T + T / 2.0;
            double py = row * T + T / 2.0;
            if (Math.hypot(px - fromX, py - fromY) < minDist) {
                continue;
            }
            Rectangle probe = new Rectangle((int) px - 12, (int) py - 10, 24, 20);
            if (!isBlocked(probe)) {
                return new Point((int) px, (int) py);
            }
        }
        return new Point(spawnPoints.get(random.nextInt(spawnPoints.size())));
    }

    /** Active automated guard post positions around the camp. */
    public List<Point> getGuardPosts() {
        List<Point> copy = new ArrayList<>();
        for (Point p : guardPosts) {
            copy.add(new Point(p));
        }
        return copy;
    }

    /** Random walkable tile at least 11 tiles outside the camp center (for roaming zombies & supply chests). */
    public Point randomOuterLootPoint(Random random) {
        double cx = campArea.getCenterX();
        double cy = campArea.getCenterY();
        for (int tries = 0; tries < 60; tries++) {
            int col = 2 + random.nextInt(COLS - 4);
            int row = 2 + random.nextInt(ROWS - 4);
            if (!isWalkableTile(col, row)) {
                continue;
            }
            double px = col * T + T / 2.0;
            double py = row * T + T / 2.0;
            if (Math.hypot(px - cx, py - cy) < 11.0 * T) {
                continue;
            }
            Rectangle probe = new Rectangle((int) px - 10, (int) py - 8, 20, 16);
            if (!isBlocked(probe)) {
                return new Point((int) px, (int) py);
            }
        }
        return new Point(spawnPoints.get(random.nextInt(spawnPoints.size())));
    }

    /**
     * Dynamically places base structures (guard posts, walls, medical tent, farm/supply depot)
     * in and around campArea as the player builds them at the camp NPCs. Returns true if anything changed.
     */
    public boolean applyBaseUpgrades(int turretLv, int wallLv, int medicLv, int farmLv) {
        if (turretLv == appliedTurretLv && wallLv == appliedWallLv
                && medicLv == appliedMedicLv && farmLv == appliedFarmLv) {
            return false;
        }
        int c0 = campArea.x / T;
        int r0 = campArea.y / T;
        boolean structureChanged = false;

        if (wallLv > appliedWallLv) {
            if (appliedWallLv < 1 && wallLv >= 1) {
                putCampTile(CRATE_WOOD, c0 + 2, r0 + 9, true);
                putCampTile(BARREL_ORANGE, c0 + 3, r0 + 9, true);
                putCampTile(BARREL_RED, c0 + 8, r0 + 9, true);
                putCampTile(CRATE_METAL, c0 + 9, r0 + 9, true);
            }
            if (appliedWallLv < 2 && wallLv >= 2) {
                putCampTile(TIRES, c0, r0 + 3, true);
                putCampTile(CRATE_CLOSED, c0, r0 + 7, true);
                putCampTile(TIRES, c0 + 11, r0 + 3, true);
                putCampTile(CRATE_CLOSED, c0 + 11, r0 + 7, true);
            }
            if (appliedWallLv < 3 && wallLv >= 3) {
                putCampTile(CRATE_METAL_G, c0 + 3, r0 + 8, true);
                putCampTile(CRATE_METAL_G, c0 + 8, r0 + 8, true);
                putCampTile(SIGN_KEEP_OUT, c0 + 5, r0 + 9, false);
            }
            appliedWallLv = wallLv;
            structureChanged = true;
        }

        if (turretLv > appliedTurretLv) {
            if (appliedTurretLv < 1 && turretLv >= 1) {
                guardPosts.add(new Point((c0 + 1) * T + T / 2, (r0 + 5) * T + T / 2));
                putCampTile(BARREL_GREY, c0 + 2, r0 + 5, false);
            }
            if (appliedTurretLv < 2 && turretLv >= 2) {
                putCampTile(NPC_GUARD, c0 + 10, r0 + 5, true);
                guardPosts.add(new Point((c0 + 10) * T + T / 2, (r0 + 5) * T + T / 2));
            }
            if (appliedTurretLv < 3 && turretLv >= 3) {
                putCampTile(NPC_GUARD, c0 + 3, r0 + 7, true);
                guardPosts.add(new Point((c0 + 3) * T + T / 2, (r0 + 7) * T + T / 2));
            }
            if (appliedTurretLv < 4 && turretLv >= 4) {
                putCampTile(NPC_GUARD, c0 + 8, r0 + 7, true);
                guardPosts.add(new Point((c0 + 8) * T + T / 2, (r0 + 7) * T + T / 2));
            }
            appliedTurretLv = turretLv;
            structureChanged = true;
        }

        if (medicLv > appliedMedicLv) {
            if (appliedMedicLv < 1 && medicLv >= 1) {
                putCampTile(BOX_MEDS, c0 + 5, r0 + 3, false);
                putCampTile(COT_H, c0 + 2, r0 + 3, false);
            }
            if (appliedMedicLv < 2 && medicLv >= 2) {
                putCampTile(GURNEY_CURTAIN, c0 + 1, r0 + 3, true);
                putCampTile(WATER_COOLER, c0 + 4, r0 + 3, false);
            }
            if (appliedMedicLv < 3 && medicLv >= 3) {
                putCampTile(OP_TABLE, c0 + 3, r0 + 3, true);
            }
            appliedMedicLv = medicLv;
            structureChanged = true;
        }

        if (farmLv > appliedFarmLv) {
            if (appliedFarmLv < 1 && farmLv >= 1) {
                putCampTile(PLANTER, c0 + 9, r0 + 3, false);
                putCampTile(CROP_CORN, c0 + 10, r0 + 3, false);
            }
            if (appliedFarmLv < 2 && farmLv >= 2) {
                putCampTile(CROP_WHEAT, c0 + 9, r0 + 4, false);
                putCampTile(AMMO_CRATE_H, c0 + 10, r0 + 4, false);
            }
            if (appliedFarmLv < 3 && farmLv >= 3) {
                putCampTile(BOX_AMMO, c0 + 8, r0 + 3, false);
                putCampTile(BOX_SUPPLY, c0 + 7, r0 + 3, false);
            }
            appliedFarmLv = farmLv;
            structureChanged = true;
        }

        if (structureChanged) {
            flowCol = -1;
            flowRow = -1;
            computeWalkable();
            renderMinimap();
        }
        return structureChanged;
    }

    private void putCampTile(PropDef d, int col, int row, boolean solidFootprint) {
        double cx = col * T + T / 2.0;
        double bottom = (row + 1) * T;
        if (Math.hypot(cx - playerSpawn.x, bottom - playerSpawn.y) < 28) {
            return;
        }
        int left = (int) Math.round(cx - d.w / 2.0);
        int artBottom = (int) Math.round(bottom);
        Rectangle solid = null;
        if (solidFootprint && d.footH > 0) {
            solid = new Rectangle(left + d.inset, artBottom - d.footH, d.w - 2 * d.inset, d.footH);
            if (!playArea.contains(solid) || overlapsWall(solid)) {
                return;
            }
            for (Rectangle s : solids) {
                if (s.intersects(solid)) {
                    solid = null; // still show visual prop without trapping player
                    break;
                }
            }
        }
        BufferedImage[] imgs = d.images();
        int drawY = artBottom + d.padBottom - imgs[0].getHeight();
        int drawX = (int) Math.round(cx - imgs[0].getWidth() / 2.0);
        Prop p = new Prop(d.name, imgs, d.ticksPerFrame, drawX, drawY, solid, artBottom);
        props.add(p);
        if (p.isAnimated()) {
            animatedProps.add(p);
        }
        if (solid != null) {
            addSolid(solid);
        }
    }

    /** Draws only the visible part of the pre-rendered ground (world coordinates). */
    public void drawGround(Graphics2D g2, Rectangle cameraView) {
        Rectangle v = cameraView.intersection(new Rectangle(0, 0, getWidthPx(), getHeightPx()));
        if (v.isEmpty()) {
            return;
        }
        g2.drawImage(ground, v.x, v.y, v.x + v.width, v.y + v.height, v.x, v.y, v.x + v.width, v.y + v.height, null);
    }

    /** All props (read-only list). */
    public List<Prop> getProps() {
        return Collections.unmodifiableList(props);
    }

    /** Props whose picture overlaps the view (so the panel does not sort hundreds of off-screen props). */
    public List<Prop> getPropsInView(Rectangle cameraView) {
        List<Prop> list = new ArrayList<>();
        for (Prop p : props) {
            if (p.getDrawBounds().intersects(cameraView)) {
                list.add(p);
            }
        }
        return list;
    }

    /** Advances animated props (flame barrel, flag, NPCs). Call once per tick. */
    public void update() {
        for (Prop p : animatedProps) {
            p.update();
        }
    }

    /** One pixel per tile overview (ground colour, solid = dark) for the HUD mini-map. */
    public BufferedImage getMinimap() {
        return minimap;
    }

    /** The whole pre-rendered ground picture (used by tools and tests). */
    public BufferedImage getGroundImage() {
        return ground;
    }

    /** Every blocking rectangle (prop footprints) plus wall cells, in world pixels. */
    public List<Rectangle> getSolidRects() {
        List<Rectangle> list = new ArrayList<>();
        for (Rectangle s : solids) {
            list.add(new Rectangle(s));
        }
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (wall[r][c]) {
                    list.add(new Rectangle(c * T, r * T, T, T));
                }
            }
        }
        return list;
    }

    /** True if a walker can stand in the middle of this tile. */
    public boolean isWalkableTile(int col, int row) {
        return col >= 0 && row >= 0 && col < COLS && row < ROWS && walkable[row][col];
    }

    // ------------------------------------------------------------------ path finding for monsters
    /**
     * Direction (radians) a walker whose feet are centred on (fromX, fromY) should go to reach
     * (toX, toY): straight at the target when nothing is in the way, otherwise downhill on a
     * breadth-first "distance to the target" grid, which leads around props and walls.
     * feetWidth is the walker's width, so a big boss does not try to squeeze through small gaps.
     */
    public double getChaseAngle(double fromX, double fromY, double toX, double toY, int feetWidth) {
        double direct = Math.atan2(toY - fromY, toX - fromX);
        if (hasClearPath(fromX, fromY, toX, toY, feetWidth)) {
            return direct;
        }
        refreshFlow((int) (toX / T), (int) (toY / T));
        int col = (int) (fromX / T), row = (int) (fromY / T);
        if (col < 0 || row < 0 || col >= COLS || row >= ROWS) {
            return direct;
        }
        // 1st choice: the lowest neighbour we can walk to in a straight line from where we stand
        int[] step = bestNeighbour(col, row, fromX, fromY, feetWidth, true);
        if (step == null) {
            // 2nd choice: the lowest neighbour connected to our tile; sliding along walls gets us there
            step = bestNeighbour(col, row, fromX, fromY, feetWidth, false);
        }
        if (step == null) {
            return direct;
        }
        double tx = step[0] * T + T / 2.0, ty = step[1] * T + T / 2.0;
        return Math.atan2(ty - fromY, tx - fromX);
    }

    /** Same as above for a normal-sized (12 px wide) walker. */
    public double getChaseAngle(double fromX, double fromY, double toX, double toY) {
        return getChaseAngle(fromX, fromY, toX, toY, 12);
    }

    /** The neighbouring tile {col, row} with the smallest distance to the target, or null. */
    private int[] bestNeighbour(int col, int row, double fromX, double fromY, int feetWidth, boolean needLineOfSight) {
        int[] bestStep = null;
        int best = flow[row][col];
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int nc = col + dc, nr = row + dr;
                if ((dc == 0 && dr == 0) || !isWalkableTile(nc, nr) || flow[nr][nc] >= best) {
                    continue;
                }
                boolean ok;
                if (needLineOfSight) {
                    ok = hasClearPath(fromX, fromY, nc * T + T / 2.0, nr * T + T / 2.0, feetWidth);
                } else {
                    ok = (dc == 0 || dr == 0) && isLinked(col, row, nc, nr);
                }
                if (ok) {
                    best = flow[nr][nc];
                    bestStep = new int[]{nc, nr};
                }
            }
        }
        return bestStep;
    }

    /** Samples the straight line every 8 px with a feet-sized box (feetWidth x 8). */
    public boolean hasClearPath(double x0, double y0, double x1, double y1, int feetWidth) {
        double dist = Math.hypot(x1 - x0, y1 - y0);
        int steps = (int) (dist / 8);
        Rectangle probe = new Rectangle(0, 0, feetWidth, 8);
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            probe.x = (int) (x0 + (x1 - x0) * t) - feetWidth / 2;
            probe.y = (int) (y0 + (y1 - y0) * t) - 4;
            if (isBlocked(probe)) {
                return false;
            }
        }
        return true;
    }

    /** Line check for a normal-sized walker (also used for line of sight of ranged monsters). */
    public boolean hasClearPath(double x0, double y0, double x1, double y1) {
        return hasClearPath(x0, y0, x1, y1, 12);
    }

    /** Rebuilds the distance grid when the target moved to another tile. */
    private void refreshFlow(int targetCol, int targetRow) {
        targetCol = Math.max(0, Math.min(COLS - 1, targetCol));
        targetRow = Math.max(0, Math.min(ROWS - 1, targetRow));
        if (targetCol == flowCol && targetRow == flowRow) {
            return;
        }
        flowCol = targetCol;
        flowRow = targetRow;
        fillDistances(flow, targetCol, targetRow);
    }

    /** Breadth-first search: every reachable walkable tile gets its step count from the start tile. */
    private void fillDistances(int[][] dist, int startCol, int startRow) {
        for (int[] row : dist) {
            Arrays.fill(row, FAR);
        }
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

    // ------------------------------------------------------------------ building helpers
    /** Marks a tile rectangle with a ground type. */
    private void paint(int ground, int col, int row, int w, int h) {
        for (int r = row; r < row + h; r++) {
            for (int c = col; c < col + w; c++) {
                if (r >= 0 && c >= 0 && r < ROWS && c < COLS) {
                    terrain[r][c] = ground;
                }
            }
        }
    }

    /** No solid prop may be placed here (lanes for the horde, the camp clearing). Tile units. */
    private void reserve(int col, int row, int w, int h) {
        keepClear.add(new Rectangle(col * T, row * T, w * T, h * T));
    }

    /**
     * Places a prop whose art stands on the point (cx, bottom). Returns false (and places nothing)
     * if its footprint would leave the play area, overlap another footprint or a reserved lane.
     */
    private boolean put(PropDef d, double cx, double bottom) {
        int left = (int) Math.round(cx - d.w / 2.0);
        int artBottom = (int) Math.round(bottom);
        Rectangle solid = null;
        if (d.footH > 0) {
            solid = new Rectangle(left + d.inset, artBottom - d.footH, d.w - 2 * d.inset, d.footH);
            if (!playArea.contains(solid)) {
                return false;
            }
            for (Rectangle k : keepClear) {
                if (k.intersects(solid)) {
                    return false;
                }
            }
            for (Rectangle s : solids) {
                if (s.intersects(solid)) {
                    return false;
                }
            }
            if (overlapsWall(solid)) {
                return false;
            }
        } else if (!playArea.contains(new Rectangle(left, artBottom - 1, d.w, 1))) {
            return false;
        }
        BufferedImage[] imgs = d.images();
        int drawY = artBottom + d.padBottom - imgs[0].getHeight();
        int drawX = (int) Math.round(cx - imgs[0].getWidth() / 2.0);
        Prop p = new Prop(d.name, imgs, d.ticksPerFrame, drawX, drawY, solid, artBottom);
        props.add(p);
        if (p.isAnimated()) {
            animatedProps.add(p);
        }
        if (solid != null) {
            addSolid(solid);
        }
        return true;
    }

    /** Places a prop standing on the bottom of tile (col, row), centred on that tile. */
    private boolean putTile(PropDef d, int col, int row) {
        return put(d, col * T + T / 2.0, (row + 1) * T);
    }

    /** Places a prop whose art's left edge starts at tile col (for wide props that span several tiles). */
    private boolean putLeft(PropDef d, int col, int row) {
        return put(d, col * T + d.w / 2.0, (row + 1) * T);
    }

    /** Tries up to `tries` random spots inside a tile area and places `count` props from the list. */
    private int scatter(PropDef[] options, int col, int row, int w, int h, int count, int tries) {
        int placed = 0;
        for (int i = 0; i < tries && placed < count; i++) {
            PropDef d = options[rng.nextInt(options.length)];
            double cx = (col + rng.nextDouble() * w) * T;
            double bottom = (row + 1 + rng.nextDouble() * (h - 1)) * T;
            Rectangle gap = new Rectangle((int) cx - d.w / 2 - 6, (int) bottom - d.footH - 6, d.w + 12, d.footH + 12);
            if (touchesSolid(gap)) {
                continue;  // keep a little space so clusters stay readable and walkable
            }
            if (put(d, cx, bottom)) {
                placed++;
            }
        }
        return placed;
    }

    private boolean touchesSolid(Rectangle r) {
        for (Rectangle s : solids) {
            if (s.intersects(r)) {
                return true;
            }
        }
        return overlapsWall(r);
    }

    /** A flat ground decoration painted into the ground picture (papers, planks ...). */
    private void decal(PropDef d, int x, int y) {
        decalDefs.add(d);
        decals.add(new int[]{decalDefs.size() - 1, x, y});
    }

    private void addSolid(Rectangle s) {
        solids.add(s);
        int c0 = Math.max(0, s.x / T), c1 = Math.min(COLS - 1, (s.x + s.width - 1) / T);
        int r0 = Math.max(0, s.y / T), r1 = Math.min(ROWS - 1, (s.y + s.height - 1) / T);
        for (int r = r0; r <= r1; r++) {
            for (int c = c0; c <= c1; c++) {
                solidsByCell.get(r * COLS + c).add(s);
            }
        }
    }

    private boolean overlapsWall(Rectangle r) {
        int c0 = Math.max(0, r.x / T), c1 = Math.min(COLS - 1, (r.x + r.width - 1) / T);
        int r0 = Math.max(0, r.y / T), r1 = Math.min(ROWS - 1, (r.y + r.height - 1) / T);
        for (int row = r0; row <= r1; row++) {
            for (int col = c0; col <= c1; col++) {
                if (wall[row][col]) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A straight wall of cells (bunker). */
    private void wallRect(int col, int row, int w, int h) {
        for (int r = row; r < row + h; r++) {
            for (int c = col; c < col + w; c++) {
                if (r >= 0 && c >= 0 && r < ROWS && c < COLS) {
                    wall[r][c] = true;
                }
            }
        }
    }

    private void buildProceduralChamber() {
        int padC = 1 + rng.nextInt(4);
        int padR = 1 + rng.nextInt(3);
        int minC = padC, maxC = COLS - 1 - padC;
        int minR = Math.max(2, padR), maxR = ROWS - 1 - padR;

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

        int midC = (minC + maxC) / 2;
        int midR = (minR + maxR) / 2;
        int offR = (rng.nextInt(3) - 1) * 4;
        int offC = (rng.nextInt(3) - 1) * 6;
        int dir = rng.nextInt(8);
        int sc, sr, gc, gr;
        switch (dir) {
            case 0: // West -> East
                sc = minC + 4; sr = clampInt(midR + offR, minR + 5, maxR - 5);
                gc = maxC - 4; gr = clampInt(midR - offR, minR + 5, maxR - 5);
                break;
            case 1: // East -> West
                sc = maxC - 4; sr = clampInt(midR + offR, minR + 5, maxR - 5);
                gc = minC + 4; gr = clampInt(midR - offR, minR + 5, maxR - 5);
                break;
            case 2: // South -> North
                sc = clampInt(midC + offC, minC + 6, maxC - 6); sr = maxR - 4;
                gc = clampInt(midC - offC, minC + 6, maxC - 6); gr = minR + 4;
                break;
            case 3: // North -> South
                sc = clampInt(midC + offC, minC + 6, maxC - 6); sr = minR + 4;
                gc = clampInt(midC - offC, minC + 6, maxC - 6); gr = maxR - 4;
                break;
            case 4: // North-West -> South-East
                sc = minC + 5; sr = minR + 4;
                gc = maxC - 5; gr = maxR - 4;
                break;
            case 5: // South-East -> North-West
                sc = maxC - 5; sr = maxR - 4;
                gc = minC + 5; gr = minR + 4;
                break;
            case 6: // South-West -> North-East
                sc = minC + 5; sr = maxR - 4;
                gc = maxC - 5; gr = minR + 4;
                break;
            default: // North-East -> South-West
                sc = maxC - 5; sr = minR + 4;
                gc = minC + 5; gr = maxR - 4;
                break;
        }
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

        int layout = rng.nextInt(12);
        switch (layout) {
            case 0:
                chamberLayoutName = "HIGHWAY AMBUSH";
                buildProceduralHighway(minC, minR, maxC, maxR, sc, sr, gc, gr);
                break;
            case 1:
                chamberLayoutName = "BUNKER LABYRINTH";
                buildProceduralLabyrinth(minC, minR, maxC, maxR);
                break;
            case 2:
                chamberLayoutName = "TWIN BRIDGES";
                buildProceduralTwinBridges(minC, minR, maxC, maxR, dir);
                break;
            case 3:
                chamberLayoutName = "PILLAR COLOSSEUM";
                buildProceduralColosseum(minC, minR, maxC, maxR, midC, midR);
                break;
            case 4:
                chamberLayoutName = "SHANTY ALLEYS";
                buildProceduralShanty(minC, minR, maxC, maxR);
                break;
            case 5:
                chamberLayoutName = "REACTOR CORE";
                buildProceduralReactor(minC, minR, maxC, maxR, midC, midR);
                break;
            case 6:
                chamberLayoutName = "SERPENTINE GAUNTLET";
                buildProceduralSerpentine(minC, minR, maxC, maxR);
                break;
            case 7:
                chamberLayoutName = "QUARANTINE CELLS";
                buildProceduralQuarantine(minC, minR, maxC, maxR, midC, midR);
                break;
            case 8:
                chamberLayoutName = "CROSSROAD CITADEL";
                buildProceduralCitadel(minC, minR, maxC, maxR, midC, midR);
                break;
            case 9:
                chamberLayoutName = "SUNKEN COURTYARD";
                buildProceduralCourtyard(minC, minR, maxC, maxR, midC, midR);
                break;
            case 10:
                chamberLayoutName = "SPLIT FOUNDRY";
                buildProceduralFoundry(minC, minR, maxC, maxR, midC, midR);
                break;
            default:
                chamberLayoutName = "CATACOMB RINGS";
                buildProceduralCatacombs(minC, minR, maxC, maxR, midC, midR);
                break;
        }
        ensureChamberConnectivity(sc, sr, gc, gr, midC, midR, tc, tr);
        keepClear.remove(traderPlaza);
        paint(DIRT, tc - 2, tr - 2, 5, 4);
        putTile(NPC_TRADER, tc, tr - 1);
        putTile(TABLE_TRADE, tc - 1, tr - 1);
        putTile(BOX_SUPPLY, tc + 1, tr - 1);
    }

    private void buildProceduralHighway(int minC, int minR, int maxC, int maxR, int sc, int sr, int gc, int gr) {
        paint(type == GameData.MapType.ABANDONED_BUNKER ? FLOOR : SAND, minC, minR, maxC - minC + 1, maxR - minR + 1);
        int midR = (sr + gr) / 2;
        int midC = (sc + gc) / 2;
        paint(DIRT, minC, Math.max(minR, midR - 2), maxC - minC + 1, 5);
        paint(DIRT, Math.max(minC, midC - 2), minR, 5, maxR - minR + 1);
        roadLine(minC, maxC, midR * T + 12, false);
        roadLine(minR, maxR, midC * T + 12, true);
        PropDef[] roadWrecks = {CAR_RED_H, CAR_BURNT_H, VAN_BURNT_H, VAN_BLUE_H, CAR_BLUE_V, CAR_BURNT_V};
        scatter(roadWrecks, minC + 3, minR + 3, maxC - minC - 6, maxR - minR - 6, 8 + rng.nextInt(5), 45);
        PropDef[] clutter = {BARREL_RED, BARREL_ORANGE, TIRES, CRATE_METAL, CRATE_WOOD, STOP_SIGN};
        scatter(clutter, minC + 2, minR + 2, maxC - minC - 4, maxR - minR - 4, 14 + rng.nextInt(6), 60);
    }

    private void buildProceduralLabyrinth(int minC, int minR, int maxC, int maxR) {
        paint(FLOOR, minC, minR, maxC - minC + 1, maxR - minR + 1);
        int spanC = maxC - minC + 1;
        int spanR = maxR - minR + 1;
        int divCount = 2 + rng.nextInt(2);
        for (int i = 1; i <= divCount; i++) {
            int wc = minC + i * spanC / (divCount + 1);
            wallRect(wc, minR + 2, 1, spanR - 4);
            int gapA = minR + 3 + rng.nextInt(Math.max(2, spanR / 2 - 4));
            int gapB = minR + spanR / 2 + rng.nextInt(Math.max(2, spanR / 2 - 5));
            clearWallRect(wc, gapA, 1, 4);
            clearWallRect(wc, gapB, 1, 4);
        }
        if (rng.nextBoolean()) {
            int wr = minR + spanR / 2;
            wallRect(minC + 4, wr, spanC - 8, 1);
            clearWallRect(minC + spanC / 3, wr, 4, 1);
            clearWallRect(minC + 2 * spanC / 3 - 2, wr, 4, 1);
        }
        PropDef[] furniture = {LOCKER_PAIR, DESK_H, TABLE_MED, BARREL_RED, CRATE_METAL, BOX_SUPPLY};
        scatter(furniture, minC + 2, minR + 2, spanC - 4, spanR - 4, 14 + rng.nextInt(6), 60);
    }

    private void buildProceduralTwinBridges(int minC, int minR, int maxC, int maxR, int dir) {
        paint(DIRT, minC, minR, maxC - minC + 1, maxR - minR + 1);
        int midC = (minC + maxC) / 2;
        int midR = (minR + maxR) / 2;
        if (dir < 2) {
            wallRect(midC - 1, minR, 3, maxR - minR + 1);
            int br1 = minR + 4 + rng.nextInt(4);
            int br2 = maxR - 7 - rng.nextInt(4);
            clearWallRect(midC - 1, br1, 3, 4);
            clearWallRect(midC - 1, br2, 3, 4);
            paint(FLOOR, midC - 2, br1, 5, 4);
            paint(FLOOR, midC - 2, br2, 5, 4);
        } else {
            wallRect(minC, midR - 1, maxC - minC + 1, 3);
            int br1 = minC + 6 + rng.nextInt(6);
            int br2 = maxC - 10 - rng.nextInt(6);
            clearWallRect(br1, midR - 1, 4, 3);
            clearWallRect(br2, midR - 1, 4, 3);
            paint(FLOOR, br1, midR - 2, 4, 5);
            paint(FLOOR, br2, midR - 2, 4, 5);
        }
        PropDef[] cover = {FENCE_BROKEN_A, BARREL_ORANGE, CRATE_WOOD, TIRES, SIGN_DANGER, BOX_PILE};
        scatter(cover, minC + 2, minR + 2, maxC - minC - 4, maxR - minR - 4, 14, 55);
    }

    private void buildProceduralColosseum(int minC, int minR, int maxC, int maxR, int midC, int midR) {
        paint(SAND, minC, minR, maxC - minC + 1, maxR - minR + 1);
        paint(DIRT, midC - 10, midR - 6, 20, 12);
        int numPillars = 6 + rng.nextInt(3);
        for (int i = 0; i < numPillars; i++) {
            double ang = i * (2 * Math.PI / numPillars) + 0.3;
            int pc = clampInt((int) Math.round(midC + Math.cos(ang) * (8 + (i % 2) * 5)), minC + 4, maxC - 6);
            int pr = clampInt((int) Math.round(midR + Math.sin(ang) * (5 + (i % 2) * 3)), minR + 4, maxR - 6);
            wallRect(pc, pr, 2, 2);
        }
        putTile(FLAME_BARREL, midC, midR);
        PropDef[] arena = {CRATE_STACK, METAL_STACK, BARREL_RED, TIRES, BOX_PILE};
        scatter(arena, minC + 3, minR + 3, maxC - minC - 6, maxR - minR - 6, 12, 48);
    }

    private void buildProceduralShanty(int minC, int minR, int maxC, int maxR) {
        paint(DIRT, minC, minR, maxC - minC + 1, maxR - minR + 1);
        paint(FLOOR, minC + 3, minR + 3, maxC - minC - 6, maxR - minR - 6);
        int blocks = 3 + rng.nextInt(3);
        for (int i = 0; i < blocks; i++) {
            int bc = minC + 6 + rng.nextInt(Math.max(4, maxC - minC - 14));
            int br = minR + 4 + rng.nextInt(Math.max(4, maxR - minR - 10));
            wallRect(bc, br, 3 + rng.nextInt(3), 2 + rng.nextInt(2));
        }
        PropDef[] shanty = {SHED, STALL_A, STALL_B, TENT_GREEN_FLAT, WATER_TOWER, CRATE_CLUSTER, BARREL_STACK};
        scatter(shanty, minC + 2, minR + 2, maxC - minC - 4, maxR - minR - 4, 14, 60);
    }

    private void buildProceduralReactor(int minC, int minR, int maxC, int maxR, int midC, int midR) {
        paint(FLOOR, minC, minR, maxC - minC + 1, maxR - minR + 1);
        wallRect(midC - 2, midR - 2, 4, 4);
        wallRect(midC - 9, midR, 5, 1);
        wallRect(midC + 5, midR, 5, 1);
        wallRect(midC, midR - 7, 1, 3);
        wallRect(midC, midR + 5, 1, 3);
        putTile(SIGN_TOXIC, midC - 3, midR - 2);
        putTile(SIGN_ELECTRIC, midC + 2, midR + 2);
        PropDef[] lab = {GAS_TANK, BARREL_STACK, CRATE_METAL, DESK_LAB_V, VENDING_RED, BOX_SUPPLY};
        scatter(lab, minC + 2, minR + 2, maxC - minC - 4, maxR - minR - 4, 16, 65);
    }

    private void buildProceduralSerpentine(int minC, int minR, int maxC, int maxR) {
        int h = maxR - minR + 1;
        int w = maxC - minC + 1;
        paint(DIRT, minC + 1, minR + 1, w - 2, h - 2);
        int segs = 4;
        int stepC = Math.max(8, (w - 8) / segs);
        for (int i = 1; i < segs; i++) {
            int c = minC + 3 + i * stepC;
            if (c >= maxC - 5) {
                break;
            }
            if ((i & 1) == 1) {
                wallRect(c, minR + 2, 2, Math.max(4, h - 10));
            } else {
                wallRect(c, minR + 8, 2, Math.max(4, h - 10));
            }
            int breachR = clampInt(minR + 4 + rng.nextInt(Math.max(3, h - 9)), minR + 3, maxR - 5);
            clearWallRect(c, breachR, 2, 3);
        }
        paint(SAND, minC + 4, minR + 3, w - 8, h - 6);
        PropDef[] cover = {CRATE_STACK, CRATE_METAL, BARREL_RED, BARREL_OIL, TIRES};
        scatter(cover, minC + 2, minR + 2, w - 4, h - 4, 24, 90);
        putTile(SIGN_DANGER, minC + 5, minR + 3);
        putTile(SIGN_WARNING, maxC - 5, maxR - 3);
    }

    private void buildProceduralQuarantine(int minC, int minR, int maxC, int maxR, int midC, int midR) {
        paint(DIRT, minC + 2, midR - 2, maxC - minC - 4, 5);
        int cellW = 10;
        int cellH = 8;
        int colsStart = minC + 5;
        for (int c = colsStart; c + cellW <= maxC - 4; c += cellW + 3) {
            int topR = minR + 3;
            wallRect(c, topR, cellW, 2);
            wallRect(c, topR, 2, cellH);
            wallRect(c + cellW - 2, topR, 2, cellH);
            wallRect(c, topR + cellH - 1, cellW / 2 - 1, 1);
            wallRect(c + cellW / 2 + 2, topR + cellH - 1, cellW - (cellW / 2 + 2), 1);
            putTile(TABLE_MED, c + 3, topR + 3);
            putTile(LOCKER_PAIR, c + cellW - 4, topR + 3);

            int botR = maxR - 2 - cellH;
            if (botR > topR + cellH + 3) {
                wallRect(c, botR + cellH - 1, cellW, 2);
                wallRect(c, botR, 2, cellH);
                wallRect(c + cellW - 2, botR, 2, cellH);
                wallRect(c, botR, cellW / 2 - 1, 1);
                wallRect(c + cellW / 2 + 2, botR, cellW - (cellW / 2 + 2), 1);
                putTile(DESK_LAB_V, c + 3, botR + 3);
                putTile(BARREL_ORANGE, c + cellW - 4, botR + 4);
            }
        }
        putTile(SIGN_TOXIC, midC - 2, midR);
        PropDef[] cover = {BARREL_ORANGE, CRATE_WOOD, TABLE_WORK, BOX_SUPPLY};
        scatter(cover, minC + 3, midR - 2, maxC - minC - 6, 5, 10, 45);
    }

    private void buildProceduralCitadel(int minC, int minR, int maxC, int maxR, int midC, int midR) {
        paint(SAND, minC + 2, minR + 2, maxC - minC - 4, maxR - minR - 4);
        paint(DIRT, midC - 6, minR + 2, 12, maxR - minR - 4);
        paint(DIRT, minC + 2, midR - 4, maxC - minC - 4, 8);
        int rw = 14, rh = 10;
        int x0 = midC - rw / 2, y0 = midR - rh / 2;
        wallRect(x0, y0, rw, 2);
        wallRect(x0, y0 + rh - 2, rw, 2);
        wallRect(x0, y0, 2, rh);
        wallRect(x0 + rw - 2, y0, 2, rh);
        clearWallRect(midC - 2, y0, 4, 2);
        clearWallRect(midC - 2, y0 + rh - 2, 4, 2);
        clearWallRect(x0, midR - 2, 2, 4);
        clearWallRect(x0 + rw - 2, midR - 2, 2, 4);

        putTile(BOX_AMMO, midC - 2, midR - 1);
        putTile(SHED, midC + 2, midR - 1);
        putTile(FENCE_BROKEN_A, x0 - 4, midR - 3);
        putTile(FENCE_BROKEN_B, x0 + rw + 3, midR + 2);
        putTile(STOP_SIGN, midC - 4, y0 - 2);
        putTile(SIGN_KEEP_OUT, midC + 3, y0 + rh + 2);
        PropDef[] fort = {CRATE_CLUSTER, BARREL_STACK, CAR_BURNT_H, TIRES, METAL_STACK};
        scatter(fort, minC + 2, minR + 2, maxC - minC - 4, maxR - minR - 4, 20, 80);
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

        putTile(WATER_TOWER, midC, midR - 2);
        putTile(FLAME_BARREL, midC - 3, midR + 1);
        putTile(FLAME_BARREL, midC + 3, midR + 1);
        putTile(STALL_A, midC - 7, midR - 4);
        putTile(STALL_B, midC + 6, midR + 3);
        putLeft(CAR_BURNT_H, clampInt(midC - 6, minC + 2, maxC - 6), clampInt(midR + 4, minR + 3, maxR - 3));
        putLeft(VAN_BURNT_H, clampInt(midC + 2, minC + 2, maxC - 6), clampInt(midR - 5, minR + 3, maxR - 3));
        PropDef[] yard = {CRATE_WOOD, BARREL_RED, TIRES, BOX_PILE, CAR_RED_H};
        scatter(yard, minC + 2, minR + 2, maxC - minC - 4, maxR - minR - 4, 20, 80);
    }

    private void buildProceduralFoundry(int minC, int minR, int maxC, int maxR, int midC, int midR) {
        paint(DIRT, minC + 1, minR + 1, maxC - minC - 2, maxR - minR - 2);
        wallRect(midC - 1, minR + 2, 2, maxR - minR - 4);
        clearWallRect(midC - 1, minR + 5, 2, 4);
        clearWallRect(midC - 1, midR - 2, 2, 4);
        clearWallRect(midC - 1, maxR - 8, 2, 4);
        paint(SAND, midC - 5, midR - 3, 10, 6);

        putTile(PIPE_STACK, midC - 7, midR - 4);
        putTile(PIPE_STACK, midC + 6, midR + 3);
        putTile(WATER_TANK_H, minC + 8, midR - 1);
        putTile(GAS_TANK, maxC - 8, midR);
        putTile(SIGN_ELECTRIC, midC - 3, minR + 4);
        putTile(SIGN_DANGER, midC + 3, maxR - 4);
        PropDef[] foundry = {METAL_STACK, CRATE_METAL, CRATE_CLUSTER, BARREL_OIL, BARREL_STACK};
        scatter(foundry, minC + 2, minR + 2, maxC - minC - 4, maxR - minR - 4, 24, 90);
    }

    private void buildProceduralCatacombs(int minC, int minR, int maxC, int maxR, int midC, int midR) {
        int outW = Math.min(30, maxC - minC - 10);
        int outH = Math.min(20, maxR - minR - 6);
        int ox = midC - outW / 2;
        int oy = midR - outH / 2;
        if (outW >= 18 && outH >= 12) {
            wallRect(ox, oy, outW, 2);
            wallRect(ox, oy + outH - 2, outW, 2);
            wallRect(ox, oy, 2, outH);
            wallRect(ox + outW - 2, oy, 2, outH);
            clearWallRect(midC - 2, oy, 4, 2);
            clearWallRect(midC - 2, oy + outH - 2, 4, 2);
            clearWallRect(ox, midR - 2, 2, 4);
            clearWallRect(ox + outW - 2, midR - 2, 2, 4);
        }
        int inW = 10, inH = 6;
        int ix = midC - inW / 2, iy = midR - inH / 2;
        wallRect(ix, iy, inW, 1);
        wallRect(ix, iy + inH - 1, inW, 1);
        wallRect(ix, iy, 1, inH);
        wallRect(ix + inW - 1, iy, 1, inH);
        clearWallRect(midC - 1, iy, 3, 1);
        clearWallRect(midC - 1, iy + inH - 1, 3, 1);
        clearWallRect(ix, midR - 1, 1, 2);
        clearWallRect(ix + inW - 1, midR - 1, 1, 2);

        putTile(VENDING_RED, midC - 2, midR);
        putTile(TENT_GREEN_FLAT, midC + 2, midR);
        putTile(DESK_H, ox + 4, oy + 3);
        putTile(CRATE_STACK, ox + outW - 5, oy + outH - 4);
        PropDef[] crypt = {CRATE_WOOD, BOX_PILE, BARREL_RED, BARREL_ORANGE, CAR_BLUE_V};
        scatter(crypt, minC + 2, minR + 2, maxC - minC - 4, maxR - minR - 4, 18, 70);
    }

    private void ensureChamberConnectivity(int sc, int sr, int gc, int gr, int midC, int midR, int tc, int tr) {
        clearWallRect(sc - 2, sr - 2, 5, 5);
        clearWallRect(gc - 2, gr - 2, 5, 5);
        clearWallRect(tc - 2, tr - 2, 5, 4);
        carveCorridor(sc, sr, tc, tr);

        int wp1C = clampInt(midC + (rng.nextInt(3) - 1) * 4, 5, COLS - 6);
        int wp1R = clampInt(minR_safe(midR - 5), 5, ROWS - 6);
        int wp2C = clampInt(midC + (rng.nextInt(3) - 1) * 4, 5, COLS - 6);
        int wp2R = clampInt(minR_safe(midR + 5), 5, ROWS - 6);
        carveCorridor(sc, sr, wp1C, wp1R);
        carveCorridor(wp1C, wp1R, gc, gr);
        carveCorridor(tc, tr, wp2C, wp2R);
        carveCorridor(wp2C, wp2R, gc, gr);
    }

    private int minR_safe(int r) {
        return clampInt(r, 4, ROWS - 5);
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

    private void clearWallRect(int col, int row, int w, int h) {
        for (int r = row; r < row + h; r++) {
            for (int c = col; c < col + w; c++) {
                setWallSafe(c, r, false);
            }
        }
    }

    private void setWallSafe(int c, int r, boolean val) {
        if (r >= 0 && r < ROWS && c >= 0 && c < COLS) {
            wall[r][c] = val;
        }
    }

    private static int clampInt(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ------------------------------------------------------------------ the camp (same idea on every map)
    /**
     * The camp in the middle: shelter, workbench with the trader and the radio along the back,
     * low barricades on the south corners, the burning barrel and the flag. Its middle stays empty
     * and it is open to the west, east and south so the horde can come in from three sides.
     */
    private void buildCamp() {
        int c0 = campArea.x / T, r0 = campArea.y / T;     // camp = 12 x 10 tiles
        boolean indoor = type == GameData.MapType.ABANDONED_BUNKER;
        if (!indoor) {
            paint(DIRT, c0, r0, 12, 10);
        }
        // back row
        if (indoor) {
            putLeft(BED_FRAME_H, c0, r0 + 2);
            putLeft(LOCKER_DOUBLE, c0 + 2, r0 + 2);
        } else {
            putLeft(TENT_A, c0, r0 + 2);
        }
        putTile(TABLE_WORK, c0 + 4, r0 + 2);
        putTile(TABLE_MED, c0 + 5, r0 + 2);
        putTile(NPC_TRADER, c0 + 6, r0 + 2);
        traderPosition = new Point((c0 + 6) * T + T / 2, (r0 + 2) * T + T / 2);
        putTile(DESK_RADIO, c0 + 7, r0 + 2);
        putLeft(indoor ? SHELF_STOCKED : TENT_GREEN, c0 + 8, r0 + 2);
        putLeft(indoor ? RACK_STOCKED : BOX_SUPPLY, c0 + 10, r0 + 2);
        // south corners: low barricades of crates and barrels
        putLeft(CRATE_WOOD, c0, r0 + 9);
        putLeft(BARREL_ORANGE, c0 + 1, r0 + 9);
        putLeft(BOX_PILE, c0, r0 + 8);
        putLeft(CRATE_METAL, c0 + 11, r0 + 9);
        putLeft(BARREL_RED, c0 + 10, r0 + 9);
        putLeft(BOX_AMMO, c0 + 11, r0 + 8);
        // landmarks: the fire, the flag, a guard
        put(FLAME_BARREL, playerSpawn.x - 2.5 * T, playerSpawn.y - 0.5 * T);
        put(FLAG, campArea.x + campArea.width - 1.5 * T, r0 * T + 5.6 * T);
        putTile(NPC_GUARD, c0 + 1, r0 + 5);
        put(indoor ? WATER_COOLER : BOX_WATER, campArea.x + campArea.width - 1.5 * T, playerSpawn.y + 1.4 * T);
        decal(SACK, campArea.x + 5 * T, playerSpawn.y + 2 * T);
    }

    // ------------------------------------------------------------------ small reusable arrangements
    /** Three barrels in a tight triangle. */
    private void barrels(int col, int row, PropDef a, PropDef b, PropDef c) {
        double x = col * T, bottom = (row + 1) * T;
        put(a, x + 12, bottom - 12);
        put(b, x + 36, bottom - 12);
        put(c, x + 24, bottom + 2);
    }

    /** A pile of tyres. */
    private void tyres(int col, int row) {
        double x = col * T, bottom = (row + 1) * T;
        put(TIRES, x + 14, bottom - 10);
        put(TIRES_LOW, x + 44, bottom - 10);
        put(rng.nextBoolean() ? TIRES_G : TIRES_LOW_G, x + 30, bottom + 6);
    }

    /** A row of the same prop, `count` times, `gap` pixels apart (left edges), standing on tile row `row`. */
    private void rowOf(PropDef d, int col, int row, int count, int gap) {
        for (int i = 0; i < count; i++) {
            put(d, col * T + d.w / 2.0 + i * gap, (row + 1) * T);
        }
    }

    /** Faded paint dashes along a road (horizontal when vertical == false). */
    private void roadLine(int from, int to, int at, boolean vertical) {
        for (int i = from; i <= to; i++) {
            if (vertical) {
                dashes.add(new Rectangle(at - 1, i * T + 8, 2, 14));
            } else {
                dashes.add(new Rectangle(i * T + 8, at - 1, 14, 2));
            }
        }
    }

    /** A concrete building: wall cells whose lowest row shows its front face. */
    private void building(int col, int row, int w, int h) {
        wallRect(col, row, w, h);
    }

    // ------------------------------------------------------------------ map 1: highway
    private void buildHighway() {
        paint(DIRT, 0, 4, COLS, 6);                 // the highway, running into the border on both sides
        paint(DIRT, 25, 10, 6, ROWS - 10);          // side road south through the camp
        roadLine(0, COLS - 1, 7 * T, false);
        roadLine(10, 12, 28 * T, true);
        roadLine(23, ROWS - 1, 28 * T, true);
        reserve(1, 6, 6, 2);                        // the inner lanes stay open except at the pile-up
        reserve(18, 6, COLS - 19, 2);
        reserve(26, 10, 4, ROWS - 11);
        reserve(1, 26, COLS - 2, 2);

        // north roadside
        putLeft(SIGN_EXIT, 4, 2);
        tyres(10, 2);
        putLeft(FENCE_BROKEN_A, 16, 2);
        putLeft(FENCE_BROKEN_B, 18, 2);
        putLeft(FENCE_BROKEN_A, 20, 2);
        putLeft(SIGN_DANGER, 31, 2);
        putLeft(CRATE_STACK_G, 38, 2);
        putLeft(CRATE_WOOD_G, 40, 2);
        barrels(43, 2, BARREL_RED_G, BARREL_ORANGE, BARREL_RED);
        putLeft(STOP_SIGN, 48, 3);
        tyres(51, 2);

        // pile-up in the west lanes
        putLeft(CAR_RED_H, 2, 5);
        putLeft(VAN_BURNT_H, 7, 6);
        putLeft(CAR_BURNT_V, 11, 8);
        putLeft(CAR_RED_H, 12, 5);
        putLeft(CAR_BLUE_V, 14, 9);
        putLeft(TIRES_LOW, 9, 8);
        putLeft(TIRES_LOW, 16, 7);
        decal(PLANKS, 6 * T, 8 * T + 6);
        decal(PLANKS, 17 * T, 5 * T);
        // single wrecks on the shoulders
        putLeft(CAR_BURNT_H, 20, 4);
        putLeft(VAN_BLUE_H, 33, 4);
        putLeft(CAR_RED_H, 37, 9);
        putLeft(CAR_BURNT_H, 23, 9);
        // road block in the east with a gap in the middle lanes
        barrels(45, 4, BARREL_ORANGE, BARREL_RED, BARREL_ORANGE);
        putLeft(CRATE_STACK, 44, 9);
        putLeft(BARREL_RED, 46, 9);
        putLeft(CAR_BURNT_V, 50, 9);
        putLeft(VAN_BURNT_H, 51, 4);
        decal(PLANKS, 47 * T, 7 * T + 12);

        // where the side road meets the highway
        putLeft(STOP_SIGN, 24, 11);
        putLeft(SIGN_EXIT, 31, 11);
        barrels(20, 10, BARREL_ORANGE_G, BARREL_RED, BARREL_ORANGE);

        // scrapyard (south-west): a broken fence with an opening, wrecks parked in rows
        rowOf(FENCE_BROKEN_A, 2, 13, 2, 64);
        rowOf(FENCE_BROKEN_B, 10, 13, 2, 64);
        putLeft(CAR_BURNT_V, 3, 17);
        putLeft(CAR_RED_V, 4, 17);
        putLeft(CAR_BURNT_V, 5, 17);
        putLeft(VAN_BURNT_H, 8, 16);
        putLeft(CAR_BURNT_H, 12, 16);
        putLeft(METAL_STACK_G, 16, 16);
        putLeft(CAR_BLUE_V, 3, 23);
        putLeft(VAN_BURNT_V, 4, 23);
        tyres(7, 21);
        tyres(7, 23);
        putLeft(METAL_STACK, 11, 22);
        putLeft(CRATE_METAL, 14, 22);
        putLeft(CRATE_METAL_G, 15, 23);
        putLeft(CAR_RED_H, 17, 22);

        // ranch (south-east): a fenced yard with a water tower, troughs and a feed store
        rowOf(FENCE_BROKEN_A, 36, 13, 2, 64);
        rowOf(FENCE_BROKEN_B, 44, 13, 2, 64);
        rowOf(FENCE_BROKEN_A, 36, 23, 2, 64);
        rowOf(FENCE_BROKEN_B, 44, 23, 2, 64);
        putLeft(WATER_TOWER, 39, 18);
        barrels(41, 17, BARREL_ORANGE, BARREL_RED, BARREL_ORANGE_G);
        putLeft(PALLET, 45, 16);
        putLeft(CRATE_WOOD, 46, 16);
        putLeft(PLANTER_LONG, 44, 20);
        putLeft(PLANTER_LONG, 47, 20);
        putLeft(TIRES_G, 50, 18);
        putLeft(BUCKET_WOOD, 49, 21);

        // south-west: a burnt-out roadside motel (a broken wall with gaps) and its car park
        building(3, 29, 5, 2);
        building(10, 29, 4, 2);
        building(16, 29, 4, 2);
        putLeft(CAR_BURNT_H, 4, 33);
        putLeft(CAR_RED_H, 10, 33);
        putLeft(VAN_BURNT_H, 16, 33);
        putLeft(BARREL_RED_G, 8, 31);
        putLeft(CRATE_WOOD_G, 14, 31);
        putLeft(SIGN_DANGER, 4, 27);
        tyres(19, 25);

        // south-east: a trailer park of wrecked vans around a dry patch
        putLeft(VAN_BURNT_V, 36, 31);
        putLeft(VAN_BLUE_H, 39, 33);
        putLeft(VAN_BURNT_H, 45, 33);
        putLeft(CAR_BURNT_V, 52, 32);
        putLeft(CAR_RED_V, 51, 29);
        barrels(42, 29, BARREL_RED, BARREL_ORANGE_G, BARREL_RED_G);
        putLeft(WATER_TANK_H, 47, 28);
        putLeft(PALLET, 38, 28);
        putLeft(SIGN_EXIT, 33, 28);

        // along the side road
        putLeft(STOP_SIGN, 31, 24);
        putLeft(BARREL_ORANGE_G, 23, 31);
        putLeft(TIRES_LOW_G, 31, 33);
    }

    // ------------------------------------------------------------------ map 2: survivor camp
    private void buildSurvivorCamp() {
        // a main street, three cross streets, the market square and the farm
        paint(DIRT, 0, 17, COLS, 3);
        paint(DIRT, 8, 0, 3, ROWS);
        paint(DIRT, 26, 0, 4, ROWS);
        paint(DIRT, 45, 0, 3, ROWS);
        paint(DIRT, 12, 22, 11, 11);
        reserve(1, 17, COLS - 2, 3);
        reserve(8, 1, 3, ROWS - 2);
        reserve(26, 1, 4, ROWS - 2);
        reserve(45, 1, 3, ROWS - 2);

        // tent rows: every block has two rows of tents facing each other with a yard between
        int[][] blocks = {{1, 7}, {11, 25}, {30, 44}, {48, 54}};
        for (int b = 0; b < blocks.length; b++) {
            int from = blocks[b][0], to = blocks[b][1];
            boolean army = b == 3;
            for (int row : new int[]{5, 15}) {
                int c = from;
                int i = 0;
                while (c + 2 <= to) {
                    PropDef tent = army ? (i % 2 == 0 ? TENT_GREEN : TENT_GREEN_FLAT)
                            : (i % 2 == 0 ? TENT_A : TENT_FLAT);
                    if (!touchesCamp(tent, c, row)) {
                        putLeft(tent, c, row);
                    }
                    c += tent == TENT_GREEN ? 2 : 3;
                    i++;
                }
            }
            // yard between the rows: cots, a fire pit corner and supplies
            for (int c = from + 1; c + 1 <= to; c += 4) {
                putTile(army ? AMMO_CRATE_H : (c % 8 == from % 8 ? BARREL_WOOD : BOX), c, 9);
                putTile(c % 3 == 0 ? COT_H : COT_V, c + 2, 11);
            }
        }
        putLeft(LOGS_TIED, 1, 10);
        putLeft(LOGS_PAIR, 3, 10);
        putLeft(WATER_TANK_V, 23, 10);
        putLeft(BUCKET_TOOLS, 20, 12);
        putLeft(WATER_TANK_H, 41, 10);
        putLeft(BUCKET_PIPES, 31, 12);

        // market square (south-west): stalls round the square
        putLeft(STALL_A, 12, 24);
        putLeft(STALL_B, 14, 24);
        putLeft(SHED, 16, 24);
        putLeft(STALL_A, 18, 24);
        putLeft(STALL_B, 20, 24);
        putLeft(STALL_FOOD, 13, 28);
        putLeft(STALL_TRADE, 17, 28);
        putLeft(TABLE_FOOD, 13, 32);
        putLeft(TABLE_TRADE, 17, 32);
        putTile(BARREL_WOOD, 21, 28);
        putTile(BARREL_BLUE, 22, 28);
        putTile(BOX_SUPPLY, 21, 32);
        putTile(BOX_OPEN, 12, 30);
        decal(PAPERS, 16 * T, 30 * T + 4);
        decal(PAPERS_B, 20 * T, 26 * T + 12);

        // storage yard (far south-west)
        putLeft(CRATE_CLUSTER, 1, 23);
        putLeft(BARREL_STACK, 4, 23);
        putLeft(PALLET, 6, 23);
        putLeft(PIPE_STACK, 1, 27);
        putLeft(WATER_TANK_V, 3, 27);
        putLeft(BOX_PILE, 5, 27);
        putLeft(CRATE_CLOSED, 1, 32);
        putLeft(CRATE_EMPTY, 2, 32);
        putLeft(BARREL_STACK, 4, 32);
        putLeft(WATER_TANK_H, 6, 33);

        // farm (south-east of the centre): a plank fence with a gate and rows of planters
        rowOf(WOOD_FENCE, 31, 21, 1, 0);
        rowOf(WOOD_FENCE, 39, 21, 1, 0);
        for (int r = 24; r <= 32; r += 4) {
            putLeft(PLANTER_LONG, 32, r);
            putLeft(PLANTER_LONG, 35, r);
            putTile(CROP_CORN, 38, r);
            putTile(CROP_WHEAT, 39, r);
            putTile(CROP_BEET, 40, r);
            putTile(CROP_ONION, 41, r);
        }
        putLeft(WATER_TOWER, 43, 26);
        putTile(PLANTER, 43, 23);
        putTile(PLANTER, 43, 30);
        putLeft(PLANTER_TALL, 43, 33);

        // workshop (far south-east)
        putLeft(SHED, 49, 24);
        putLeft(TOOLBOXES, 51, 23);
        putLeft(SIDE_TABLE, 53, 25);
        putLeft(BUCKET_TOOLS, 49, 28);
        putLeft(PIPE_STACK, 51, 29);
        putLeft(CRATE_CLOSED, 53, 29);
        putLeft(BARREL_STACK, 49, 33);
        putLeft(GAS_TANK, 52, 33);
    }

    /** True if a tent's picture placed with putLeft would touch the camp (keeps a half-tile gap). */
    private boolean touchesCamp(PropDef d, int col, int row) {
        Rectangle art = new Rectangle(col * T, (row + 1) * T - d.h, d.w, d.h);
        Rectangle camp = new Rectangle(campArea);
        camp.grow(T / 2, 0);
        return art.intersects(camp);
    }

    // ------------------------------------------------------------------ map 3: bunker
    private void buildBunker() {
        paint(FLOOR, 0, 0, COLS, ROWS);
        wallRect(0, 0, COLS, 2);                      // the north wall shows its face
        wallRect(0, 0, 1, ROWS);
        wallRect(COLS - 1, 0, 1, ROWS);
        wallRect(0, ROWS - 1, COLS, 1);

        // four rooms in the corners, each with a wide doorway into the central hall
        wallRect(1, 11, 7, 2);
        wallRect(13, 11, 6, 2);
        wallRect(18, 2, 1, 9);
        wallRect(37, 11, 6, 2);
        wallRect(48, 11, 7, 2);
        wallRect(37, 2, 1, 9);
        wallRect(1, 24, 7, 2);
        wallRect(13, 24, 6, 2);
        wallRect(18, 26, 1, 9);
        wallRect(37, 24, 6, 2);
        wallRect(48, 24, 7, 2);
        wallRect(37, 26, 1, 9);
        reserve(8, 9, 5, 6);
        reserve(43, 9, 5, 6);
        reserve(8, 22, 5, 6);
        reserve(43, 22, 5, 6);
        reserve(1, 15, COLS - 2, 6);
        reserve(19, 2, 3, ROWS - 3);
        reserve(34, 2, 3, ROWS - 3);

        // dorm (north-west): lockers on the wall, two rows of bunks with drawers between
        rowOf(LOCKER, 1, 3, 5, 18);
        putLeft(LOCKER_PAIR, 15, 3);
        for (int c = 2; c <= 14; c += 3) {
            putLeft(c % 2 == 0 ? BED_V : BED_FRAME_V, c, 6);
            putLeft(c % 2 == 0 ? BED_FRAME_V : BED_V, c, 10);
        }
        putLeft(DRAWERS, 7, 3);
        putLeft(DRAWERS_B, 10, 3);
        putLeft(CABINET, 16, 7);
        decal(PAPERS, 9 * T, 8 * T + 6);

        // lab (north-east): ward beds on the wall, operating tables, computer desks
        putLeft(GURNEY_CURTAIN, 38, 4);
        putLeft(GURNEY_CURTAIN, 40, 4);
        putLeft(GURNEY_CURTAIN, 42, 4);
        putLeft(GURNEY_CURTAIN, 44, 4);
        putLeft(WATER_COOLER, 47, 3);
        putLeft(CABINET, 48, 3);
        putLeft(FRIDGE_DOUBLE, 50, 3);
        putLeft(LOCKER_TALL, 52, 3);
        putLeft(OP_TABLE, 39, 8);
        putLeft(OP_TABLE_B, 43, 9);
        putLeft(DESK_LAB_V, 49, 8);
        putLeft(DESK_PC_V, 51, 8);
        putLeft(DESK_LAB_V, 53, 8);

        // offices (north centre): two rows of desks facing each other
        for (int c = 23; c <= 31; c += 3) {
            putLeft(DESK_PC_H, c, 5);
            putLeft(DESK_H, c, 8);
        }
        rowOf(CABINET, 22, 3, 1, 0);
        putLeft(DESK_V, 22, 9);
        putLeft(DESK_V2, 32, 9);
        putLeft(SHELF_WIDE, 31, 3);
        putLeft(WATER_COOLER, 29, 3);

        // storage (south-west): three aisles of shelves
        for (int c = 2; c <= 14; c += 3) {
            putLeft(c % 2 == 0 ? SHELF_STOCKED : SHELF_WIDE, c, 28);
            putLeft(c % 2 == 0 ? RACK_STOCKED : RACK, c, 31);
        }
        rowOf(FRIDGE, 2, 34, 3, 32);
        putLeft(LOCKER_TALL, 7, 34);
        putLeft(CUPBOARD, 10, 34);
        putLeft(LOCKER_BROKEN, 13, 34);
        putLeft(LOCKER_DOUBLE, 15, 34);

        // canteen (south-east): machines against the north wall, tables in a grid
        putLeft(VENDING_RED, 38, 26);
        putLeft(VENDING_BLACK, 39, 26);
        putLeft(VENDING_COFFEE, 40, 26);
        putLeft(FRIDGE, 49, 26);
        putLeft(FRIDGE_OPEN, 50, 26);
        putLeft(WATER_COOLER, 52, 26);
        putLeft(SHELF_EMPTY, 53, 26);
        for (int c = 39; c <= 51; c += 4) {
            putLeft(DESK_H, c, 30);
            putLeft(DESK_H, c, 33);
        }

        // south centre: the records room
        for (int c = 22; c <= 31; c += 3) {
            putLeft(c % 2 == 0 ? LOCKER_DOUBLE : CABINET, c, 26);
        }
        putLeft(RACK, 23, 30);
        putLeft(RACK_STOCKED, 26, 30);
        putLeft(DRAWERS_B, 29, 30);
        putLeft(BED_H2, 23, 33);
        putLeft(BED_H, 26, 33);
        putLeft(BED_FRAME_H2, 29, 33);

        // corridor clutter along the outer walls
        putLeft(LOCKER_DOUBLE, 1, 15);
        putLeft(CUPBOARD, 1, 21);
        putLeft(LOCKER_TALL, 53, 15);
        putLeft(SHELF_EMPTY, 53, 21);
        decal(PAPERS_B, 14 * T, 18 * T);
        decal(PAPERS, 41 * T, 19 * T);
    }

    // ------------------------------------------------------------------ map 4: gas station
    private void buildGasStation() {
        paint(DIRT, 11, 4, 34, 9);              // forecourt
        paint(DIRT, 0, 27, COLS, 5);            // the road
        paint(DIRT, 15, 13, 5, 14);             // two driveways
        paint(DIRT, 36, 13, 5, 14);
        roadLine(0, COLS - 1, 29 * T + 16, false);
        building(12, 1, 13, 3);                 // shop
        building(31, 1, 13, 3);                 // workshop / garage
        reserve(1, 28, COLS - 2, 3);
        reserve(16, 11, 3, 17);
        reserve(37, 11, 3, 17);
        reserve(11, 8, 34, 2);                  // lane between the pumps and the parked cars

        // shop front: crates and signs; garage: roller doors and parts
        putLeft(CRATE_STACK, 12, 5);
        putLeft(METAL_STACK_OPEN, 22, 5);
        putLeft(BARREL_RED, 25, 4);
        putLeft(SIGN_EXIT, 26, 3);
        putLeft(GARAGE_GATE, 32, 4);
        putLeft(GARAGE_GATE, 36, 4);
        putLeft(GARAGE_GATE, 40, 4);
        putLeft(TOOLBOXES, 34, 5);
        putLeft(TIRES_LOW, 38, 5);
        putLeft(GAS_TANK, 44, 4);
        // pump islands
        putLeft(PUMP_ROW, 14, 7);
        putLeft(PUMP_ROW, 21, 7);
        putLeft(PUMP_ROW_BURNT, 33, 7);
        decal(PLANKS, 30 * T, 7 * T);
        // parked cars below the pumps
        putLeft(CAR_BLUE_V, 13, 12);
        putLeft(CAR_RED_V, 21, 12);
        putLeft(CAR_BURNT_V, 23, 12);
        putLeft(VAN_BURNT_V, 34, 12);
        putLeft(CAR_RED_H, 41, 11);

        // tyre shop yard (north-west)
        tyres(1, 2);
        tyres(4, 2);
        tyres(7, 2);
        tyres(1, 6);
        putLeft(TIRES_G, 5, 6);
        putLeft(CAR_BURNT_V, 8, 7);
        putLeft(CRATE_WOOD_G, 2, 10);
        putLeft(CRATE_STACK_G, 5, 11);
        putLeft(PUMP_BURNT, 8, 11);
        putLeft(PUMP, 10, 14);

        // fuel depot (north-east): barrels in rows
        rowOf(BARREL_OIL, 46, 3, 4, 26);
        rowOf(BARREL_GREY, 46, 5, 4, 26);
        putLeft(BARREL_STACK, 51, 3);
        putLeft(GAS_TANK, 53, 5);
        putLeft(BARREL_STACK, 46, 9);
        putLeft(BARREL_STACK, 48, 9);
        putLeft(PUMP_G, 51, 10);
        putLeft(METAL_STACK_G, 46, 12);

        // parking lot (west) and container yard (east)
        rowOf(CAR_RED_V, 2, 17, 1, 0);
        putLeft(CAR_BLUE_V, 3, 17);
        putLeft(CAR_BURNT_V, 5, 17);
        putLeft(CAR_RED_V, 6, 17);
        putLeft(VAN_BURNT_V, 9, 17);
        putLeft(CAR_BURNT_H, 3, 23);
        putLeft(VAN_BLUE_H, 8, 23);
        putLeft(STOP_SIGN, 13, 25);
        putLeft(METAL_STACK, 43, 16);
        putLeft(METAL_STACK_G, 46, 16);
        putLeft(METAL_STACK_OPEN, 50, 16);
        putLeft(CRATE_STACK, 43, 22);
        putLeft(CRATE_STACK_G, 47, 23);
        putLeft(CRATE_METAL, 51, 22);
        putLeft(CRATE_METAL_G, 52, 23);
        putLeft(SIGN_DANGER, 41, 25);

        // traffic on the road and junk beyond it
        putLeft(VAN_BURNT_H, 3, 27);
        putLeft(CAR_RED_H, 10, 31);
        putLeft(CAR_BURNT_H, 23, 27);
        putLeft(VAN_BLUE_H, 29, 31);
        putLeft(CAR_BURNT_H, 45, 27);
        putLeft(CAR_RED_H, 50, 31);
        rowOf(FENCE_BROKEN_A, 2, 33, 1, 0);
        tyres(8, 33);
        putLeft(FENCE_BROKEN_B, 14, 33);
        barrels(20, 33, BARREL_ORANGE_G, BARREL_RED, BARREL_ORANGE);
        putLeft(FENCE_BROKEN_A, 33, 33);
        tyres(40, 33);
        putLeft(FENCE_BROKEN_B, 46, 33);
        putLeft(CRATE_WOOD_G, 52, 33);
    }

    // ------------------------------------------------------------------ map 5: quarantine zone
    private void buildQuarantine() {
        paint(DIRT, 0, 17, COLS, 3);
        paint(DIRT, 26, 0, 4, ROWS);
        reserve(1, 17, COLS - 2, 3);
        reserve(26, 1, 4, ROWS - 2);

        // north-west compound: field hospital
        compound(3, 3, 6, 10, 2);
        for (int c = 4; c <= 18; c += 4) {
            putLeft(TENT_GREEN, c, 7);
            putLeft(TENT_GREEN_FLAT, c + 1, 12);
        }
        rowOf(COT_V, 4, 9, 7, 64);
        putLeft(WATER_TANK_V, 19, 9);
        putLeft(BOX_MEDS, 6, 4);
        putLeft(BOX_WATER, 8, 4);

        // north-east compound: the lab building with crates in front
        compound(33, 3, 6, 10, 2);
        building(35, 4, 9, 3);
        putLeft(METAL_STACK, 45, 6);
        putLeft(CRATE_CLUSTER, 47, 6);
        putLeft(AMMO_CRATE_V, 50, 6);
        putLeft(BOX_AMMO, 35, 10);
        putLeft(AMMO_CRATE_H, 38, 9);
        putLeft(BARREL_STACK, 45, 10);
        putLeft(PALLET, 48, 10);
        putLeft(BOX_TOOLS, 50, 10);
        putLeft(SIGN_KEEP_OUT, 41, 13);

        // south-west compound: contamination dump
        compound(3, 22, 6, 9, 2);
        rowOf(BARREL_OIL, 4, 26, 6, 24);
        rowOf(BARREL_GREY, 4, 28, 5, 24);
        putLeft(GAS_TANK, 12, 26);
        putLeft(GAS_TANK, 13, 26);
        putLeft(TRASH_C, 15, 26);
        putLeft(TRASH_B, 17, 27);
        putLeft(TRASH_A, 12, 29);
        putLeft(SIGN_TOXIC, 19, 25);
        putLeft(SIGN_WARNING, 4, 30);

        // south-east compound: decontamination sheds
        compound(33, 22, 6, 9, 2);
        putLeft(SHED, 34, 27);
        putLeft(SHED, 37, 27);
        putLeft(STALL_A, 40, 27);
        putLeft(STALL_B, 43, 27);
        putLeft(SHED, 46, 27);
        putLeft(WATER_TANK_H, 49, 25);
        putLeft(BOX_EXTINGUISHER, 34, 30);
        putLeft(SIDE_TABLE, 38, 30);
        putLeft(BOX_TOOLS, 44, 30);
        putLeft(SIGN_ELECTRIC, 49, 29);

        // checkpoints where the paths meet the compounds
        putLeft(SIGN_QUARANTINE, 2, 15);
        putLeft(SIGN_QUARANTINE, 50, 15);
        putLeft(SIGN_SHELTER, 23, 14);
        putLeft(SIGN_WARNING, 31, 14);
        barrels(22, 23, BARREL_GREY, BARREL_OIL, BARREL_GREY);
        barrels(31, 23, BARREL_OIL, BARREL_GREY, BARREL_OIL);
        putLeft(CRATE_METAL, 23, 2);
        putLeft(CRATE_METAL_G, 31, 2);
        putLeft(TRASH_A, 21, 33);
        putLeft(TRASH_B, 33, 33);
        putLeft(SIGN_WARNING, 23, 32);
        decal(PAPERS, 28 * T, 9 * T);
        decal(PAPERS_B, 14 * T, 18 * T);
        decal(SACK, 40 * T, 20 * T);
    }

    /**
     * A chain-link compound: `panels` fence panels (3 tiles each) along the north and south sides,
     * posts down the sides, and a gate gap of `gate` panels in the middle of the side that faces
     * the main path (south for the northern compounds, north for the southern ones).
     */
    private void compound(int col, int row, int panels, int height, int gate) {
        int width = panels * 3;
        int gateStart = (panels - gate) / 2;
        boolean gateSouth = row < ROWS / 2;
        for (int i = 0; i < panels; i++) {
            boolean isGate = i >= gateStart && i < gateStart + gate;
            if (!(gateSouth && isGate)) {
                fencePanel(col + i * 3, row + height, false);
            }
            if (!(!gateSouth && isGate)) {
                fencePanel(col + i * 3, row, i == 1);
            }
        }
        fencePost(col * T, row, height);
        fencePost((col + width) * T - 6, row, height);
    }

    // fence pieces from the camp sheet
    private static final PropDef CHAIN_PANEL = new PropDef("fence", CAMP, 16, 16, 96, 36, 6, 0);
    private static final PropDef CHAIN_PANEL_SIGN = new PropDef("fence", CAMP, 16, 80, 96, 32, 6, 0);
    private static final PropDef WOOD_FENCE = new PropDef("plank fence", CAMP, 16, 184, 128, 24, 8, 0);

    /** One chain-link panel, 3 tiles wide, standing on the bottom of tile row `row`. */
    private void fencePanel(int col, int row, boolean withSign) {
        PropDef d = withSign ? CHAIN_PANEL_SIGN : CHAIN_PANEL;
        put(d, col * T + d.w / 2.0, (row + 1) * T - 2);
    }

    /**
     * The left or right side of a compound: a run of fence posts from the north panel down to the
     * south panel, drawn as one tall prop so it sorts correctly. x is the post's left edge.
     */
    private void fencePost(int x, int row, int height) {
        BufferedImage seg = AssetLoader.subImage(CAMP, 16, 50, 6, 30);
        int top = (row + 1) * T - 8;
        int bottom = (row + height + 1) * T - 32;
        BufferedImage img = new BufferedImage(6, bottom - top, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        for (int y = 0; y < img.getHeight(); y += seg.getHeight()) {
            g.drawImage(seg, 0, y, null);
        }
        g.dispose();
        Rectangle solid = new Rectangle(x, top, 6, bottom - top);
        if (!playArea.contains(solid)) {
            return;
        }
        Prop p = new Prop("fence post", new BufferedImage[]{img}, 1, x, top, solid, bottom);
        props.add(p);
        addSolid(solid);
    }

    // ------------------------------------------------------------------ ground rendering
    private BufferedImage tile(String sheet, int x, int y) {
        return AssetLoader.subImage(sheet, x, y, T, T);
    }

    // palette taken from the tile sheets (sand specks, dirt specks, concrete cracks)
    private static final int[] SAND_SPECKS = {0xFFAD7757, 0xFFAD7757, 0xFF9B6658, 0xFFCCA07F};
    private static final int[] DIRT_SPECKS = {0xFF9B6658, 0xFF9B6658, 0xFFC09473, 0xFFCCA07F};
    private static final int[] FLOOR_SPECKS = {0xFF788C8B, 0xFF6C8281, 0xFFA8B5B2};

    /**
     * Plain tiles from the sheets with a few specks of the sheet's own colours sprinkled on, so
     * open ground has texture without the visible checkerboard of mixing differently coloured tiles.
     */
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
                if (rng.nextBoolean()) {
                    img.setRGB(x + 1, y, color);
                }
                if (rng.nextInt(4) == 0) {
                    img.setRGB(x, y + 1, color);
                }
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
                    if (mask != 0) {
                        img = dirtEdges.get(mask);
                        if (img == null) {
                            img = buildDirtEdge(dirt[0], mask);
                            dirtEdges.put(mask, img);
                        }
                    } else {
                        img = roll < 94 ? dirt[roll % dirt.length] : dirtRare[roll % dirtRare.length];
                    }
                } else {
                    img = sand[roll % sand.length];
                }
                g.drawImage(img, c * T, r * T, null);
            }
        }
        g.setColor(new Color(0xCC, 0xA0, 0x7F, 150));
        for (Rectangle d : dashes) {
            g.fill(d);
        }
        for (int[] d : decals) {
            PropDef def = decalDefs.get(d[0]);
            g.drawImage(def.images()[0], d[1], d[2], null);
        }
        drawWalls(g);
        if (type != GameData.MapType.ABANDONED_BUNKER) {
            drawFenceBorder(g);
        }
        g.dispose();
    }

    private boolean isSandAt(int c, int r) {
        if (c < 0 || r < 0 || c >= COLS || r >= ROWS) {
            return false;   // outside counts as dirt so roads run cleanly into the border
        }
        return terrain[r][c] == SAND;
    }

    /** Bits: 1=N 2=E 4=S 8=W sand neighbours, 16=NE 32=SE 64=SW 128=NW sand corner (inner corners). */
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

    /**
     * Builds a dirt tile with a sand lip on every side that touches sand. The sheet only has the
     * lip for the north side, so the other sides are that overlay rotated or flipped, and an inner
     * corner is the part where two side overlays overlap.
     */
    private static BufferedImage buildDirtEdge(BufferedImage base, int mask) {
        BufferedImage north = northLipOverlay();
        BufferedImage[] side = {north, AssetLoader.rotate90(north, true), AssetLoader.flipVertical(north),
            AssetLoader.rotate90(north, false)};
        BufferedImage out = new BufferedImage(T, T, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(base, 0, 0, null);
        for (int s = 0; s < 4; s++) {
            if ((mask & (1 << s)) != 0) {
                g.drawImage(side[s], 0, 0, null);
            }
        }
        // inner corners: NE = N and E overlap, SE = S and E, SW = S and W, NW = N and W
        int[][] corners = {{16, 0, 1}, {32, 2, 1}, {64, 2, 3}, {128, 0, 3}};
        for (int[] cn : corners) {
            if ((mask & cn[0]) != 0) {
                g.drawImage(overlap(side[cn[1]], side[cn[2]]), 0, 0, null);
            }
        }
        g.dispose();
        return out;
    }

    private static BufferedImage northLip;

    /**
     * The sand band of the sheet's "dirt below sand" tile with the dirt made transparent.
     * Only pixels connected to the top row are kept, so stray specks inside the dirt are dropped.
     */
    private static synchronized BufferedImage northLipOverlay() {
        if (northLip != null) {
            return northLip;
        }
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
                if (nx >= 0 && ny >= 0 && nx < T && ny < T && !keep[ny][nx]
                        && (src.getRGB(nx, ny) & 0xFFFFFF) != DIRT_RGB) {
                    keep[ny][nx] = true;
                    queue.add(new int[]{nx, ny});
                }
            }
        }
        northLip = new BufferedImage(T, T, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < T; y++) {
            for (int x = 0; x < T; x++) {
                if (keep[y][x]) {
                    northLip.setRGB(x, y, src.getRGB(x, y));
                }
            }
        }
        return northLip;
    }

    /** Pixels of a that are also covered in b (used for the small sand notch of an inner corner). */
    private static BufferedImage overlap(BufferedImage a, BufferedImage b) {
        BufferedImage out = new BufferedImage(T, T, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < T; y++) {
            for (int x = 0; x < T; x++) {
                if ((a.getRGB(x, y) >>> 24) != 0 && (b.getRGB(x, y) >>> 24) != 0) {
                    out.setRGB(x, y, a.getRGB(x, y));
                }
            }
        }
        return out;
    }

    // walls: a dark roof/top surface with a light rim, and front faces (concrete with a baseboard)
    private static final Color WALL_OUTLINE = new Color(0x1F2E33);
    private static final Color WALL_TOP = new Color(0x465E62);
    private static final Color WALL_RIM = new Color(0x6C8281);

    private boolean isWall(int c, int r) {
        if (c < 0 || r < 0 || c >= COLS || r >= ROWS) {
            return true;
        }
        return wall[r][c];
    }

    private void drawWalls(Graphics2D g) {
        BufferedImage face = tile(INTER, 32, 160);
        BufferedImage[] faceDeco = {tile(INTER, 160, 96), tile(INTER, 192, 96), tile(INTER, 160, 128)};
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (!wall[r][c]) {
                    continue;
                }
                int x = c * T, y = r * T;
                boolean front = !isWall(c, r + 1);
                if (front) {
                    boolean deco = rng.nextInt(6) == 0;
                    g.drawImage(deco ? faceDeco[rng.nextInt(faceDeco.length)] : face, x, y, null);
                    g.setColor(WALL_OUTLINE);
                    if (!isWall(c - 1, r)) {
                        g.fillRect(x, y, 2, T);
                    }
                    if (!isWall(c + 1, r)) {
                        g.fillRect(x + T - 2, y, 2, T);
                    }
                    if (!isWall(c, r - 1)) {
                        g.fillRect(x, y, T, 2);
                    }
                } else {
                    g.setColor(WALL_TOP);
                    g.fillRect(x, y, T, T);
                    // the rim shows where the top surface ends; the outline is the wall's silhouette
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

    /** Outdoor maps: a chain-link (or palisade) fence around the whole arena. */
    private void drawFenceBorder(Graphics2D g) {
        boolean palisade = type == GameData.MapType.SURVIVOR_CAMP;
        BufferedImage panel = palisade ? LOGS_TIED.images()[0] : CHAIN_PANEL.images()[0];
        BufferedImage post = AssetLoader.subImage(CAMP, 16, 50, 6, 30);
        // top and bottom rows
        int step = palisade ? 24 : panel.getWidth();
        for (int x = -4; x < COLS * T; x += step) {
            if (palisade) {
                g.drawImage(panel, x, T - panel.getHeight() + 6, null);
            } else {
                g.drawImage(panel, x, T - panel.getHeight() + 2, null);
            }
        }
        for (int x = -4; x < COLS * T; x += step) {
            int y = ROWS * T - panel.getHeight() + (palisade ? 4 : 0);
            g.drawImage(panel, x, y, null);
        }
        // left and right columns
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

    // ------------------------------------------------------------------ analysis after building
    /**
     * Marks tiles a monster can stand in, and which neighbouring tiles are really connected: two
     * open tiles can still be separated by a slit too narrow for a body (e.g. between a table and
     * an NPC), so each link is tested by sliding a feet-sized box from centre to centre.
     */
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

    /** True if a walker can step between the two orthogonally neighbouring tiles. */
    private boolean isLinked(int c, int r, int nc, int nr) {
        if (!isWalkableTile(c, r) || !isWalkableTile(nc, nr)) {
            return false;
        }
        if (nr == r) {
            return openEast[r][Math.min(c, nc)];
        }
        return openSouth[Math.min(r, nr)][c];
    }

    /**
     * Spawn points: tiles within 3 tiles of the border, reachable from the camp, where both a normal
     * monster and the boss fit when their hitbox centre is put on the point.
     */
    private void findSpawnPoints() {
        int[][] fromCamp = new int[ROWS][COLS];
        fillDistances(fromCamp, playerSpawn.x / T, playerSpawn.y / T);
        reachable = new boolean[ROWS][COLS];
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                reachable[r][c] = fromCamp[r][c] != FAR;
            }
        }
        GameData.MonsterType big = GameData.MonsterType.ZOMBIE_BOSS;
        for (int r = 1; r < ROWS - 1; r++) {
            for (int c = 1; c < COLS - 1; c++) {
                if (fromCamp[r][c] == FAR) {
                    continue;
                }
                Point p = new Point(c * T + T / 2, r * T + 8);
                if (safeHub) {
                    boolean nearEdge = r <= 3 || c <= 3 || r >= ROWS - 4 || c >= COLS - 4;
                    if (!nearEdge) {
                        continue;
                    }
                } else {
                    if (Math.hypot(p.x - playerSpawn.x, p.y - playerSpawn.y) < 175.0) {
                        continue;
                    }
                }
                // feet boxes exactly as Entity.getFeetBounds() will compute them
                Rectangle bossFeet = new Rectangle(p.x - big.hitboxW / 2, p.y + big.hitboxH / 2 - 8, big.hitboxW, 8);
                Rectangle smallFeet = new Rectangle(p.x - 7, p.y + 1, 14, 8);
                if (!isBlocked(bossFeet) && !isBlocked(smallFeet)) {
                    spawnPoints.add(p);
                }
            }
        }
        if (spawnPoints.isEmpty()) {
            spawnPoints.add(new Point(playerSpawn));   // never happens with the built-in maps
        }
    }

    private void renderMinimap() {
        minimap = new BufferedImage(COLS, ROWS, BufferedImage.TYPE_INT_ARGB);
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int rgb;
                if (wall[r][c] || r == 0 || c == 0 || r == ROWS - 1 || c == COLS - 1) {
                    rgb = 0xFF2A2A30;
                } else if (!walkable[r][c]) {
                    rgb = 0xFF5A4636;
                } else if (terrain[r][c] == FLOOR) {
                    rgb = 0xFF8EA39D;
                } else if (terrain[r][c] == DIRT) {
                    rgb = 0xFFAD7757;
                } else {
                    rgb = 0xFFC09473;
                }
                minimap.setRGB(c, r, rgb);
            }
        }
    }
}
