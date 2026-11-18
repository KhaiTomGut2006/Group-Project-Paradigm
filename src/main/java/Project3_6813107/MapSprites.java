// Tapatsawin Chaieua          6813105
// Thanapon Thanadunpremdet    6813106
// Tanawat Wongwad             6813107
// Natthanon Achachan          6813241
package Project3_6813107;

import java.awt.image.BufferedImage;

/** Sprite sheet coordinates and blocking footprints for props placed by GameMap. */
final class MapSprites {
    private MapSprites() {}

    /** A catalog entry: where the art is on its sheet and how big its blocking footprint is. */
    static final class PropDef {
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

    static final String WASTE = "tiles/wasteland.png";
    static final String CAMP = "tiles/camp.png";
    static final String INTER = "tiles/interior.png";

    // Wasteland sheet: vehicles, crates, barrels, tyres, pumps, signs
    static final PropDef CAR_RED_V = new PropDef("red car", WASTE, 0, 164, 32, 59, 46, 2);
    static final PropDef CAR_RED_H = new PropDef("red car", WASTE, 32, 185, 64, 38, 24, 2);
    static final PropDef CAR_BLUE_V = new PropDef("blue car", WASTE, 0, 224, 32, 64, 50, 2);
    static final PropDef VAN_BLUE_H = new PropDef("blue van", WASTE, 32, 248, 77, 40, 26, 2);
    static final PropDef CAR_BURNT_V = new PropDef("burnt car", WASTE, 0, 292, 32, 59, 46, 2);
    static final PropDef CAR_BURNT_H = new PropDef("burnt car", WASTE, 32, 313, 63, 37, 24, 2);
    static final PropDef VAN_BURNT_V = new PropDef("burnt van", WASTE, 1, 354, 30, 62, 48, 2);
    static final PropDef VAN_BURNT_H = new PropDef("burnt van", WASTE, 32, 376, 76, 39, 26, 2);
    static final PropDef CRATE_METAL = new PropDef("metal crate", WASTE, 131, 162, 26, 27, 16, 1);
    static final PropDef CRATE_METAL_G = new PropDef("metal crate", WASTE, 160, 162, 32, 31, 16, 4);
    static final PropDef CRATE_WOOD = new PropDef("wood crate", WASTE, 131, 194, 26, 27, 16, 1);
    static final PropDef CRATE_WOOD_G = new PropDef("wood crate", WASTE, 160, 194, 32, 31, 16, 4);
    static final PropDef BARREL_RED = new PropDef("red barrel", WASTE, 132, 225, 24, 29, 12, 2);
    static final PropDef BARREL_RED_G = new PropDef("red barrel", WASTE, 162, 225, 27, 32, 12, 4);
    static final PropDef BARREL_ORANGE = new PropDef("orange barrel", WASTE, 132, 257, 24, 29, 12, 2);
    static final PropDef BARREL_ORANGE_G = new PropDef("orange barrel", WASTE, 162, 257, 27, 32, 12, 4);
    static final PropDef GARAGE_GATE = new PropDef("garage gate", WASTE, 201, 178, 46, 78, 22, 0);
    static final PropDef PLANKS = new PropDef("planks", WASTE, 196, 265, 24, 18, 0, 0);
    static final PropDef STOP_SIGN = new PropDef("stop sign", WASTE, 229, 260, 22, 26, 5, 7);
    static final PropDef TIRES = new PropDef("tyres", WASTE, 130, 289, 28, 30, 14, 1);
    static final PropDef TIRES_LOW = new PropDef("tyres", WASTE, 162, 293, 28, 24, 12, 1);
    static final PropDef TIRES_G = new PropDef("tyres", WASTE, 192, 289, 32, 31, 14, 3);
    static final PropDef TIRES_LOW_G = new PropDef("tyres", WASTE, 225, 293, 31, 27, 12, 3);
    static final PropDef FENCE_BROKEN_A = new PropDef("broken fence", WASTE, 128, 324, 63, 28, 8, 2);
    static final PropDef FENCE_BROKEN_B = new PropDef("broken fence", WASTE, 198, 324, 53, 29, 8, 2);
    static final PropDef PUMP = new PropDef("fuel pump", WASTE, 141, 369, 35, 46, 16, 3);
    static final PropDef PUMP_G = new PropDef("fuel pump", WASTE, 205, 369, 38, 47, 16, 5);
    static final PropDef PUMP_ROW = new PropDef("pump island", WASTE, 0, 426, 128, 54, 20, 2);
    static final PropDef PUMP_ROW_BURNT = new PropDef("burnt pump island", WASTE, 0, 490, 128, 54, 20, 2);
    static final PropDef PUMP_BURNT = new PropDef("burnt pump", WASTE, 13, 561, 38, 47, 16, 4);
    static final PropDef CRATE_STACK = new PropDef("crate stack", WASTE, 134, 437, 51, 40, 22, 1);
    static final PropDef CRATE_STACK_G = new PropDef("crate stack", WASTE, 195, 437, 57, 44, 22, 4);
    static final PropDef METAL_STACK = new PropDef("metal crates", WASTE, 134, 499, 51, 41, 22, 1);
    static final PropDef METAL_STACK_G = new PropDef("metal crates", WASTE, 196, 499, 55, 43, 22, 4);
    static final PropDef METAL_STACK_OPEN = new PropDef("open crates", WASTE, 134, 564, 51, 41, 22, 1);
    static final PropDef SIGN_EXIT = new PropDef("exit sign", WASTE, 69, 546, 54, 30, 5, 6);
    static final PropDef SIGN_DANGER = new PropDef("danger sign", WASTE, 69, 578, 54, 30, 5, 6);
    static final PropDef TABLE_WORK = new PropDef("workbench", WASTE, 257, 64, 31, 32, 14, 0);
    static final PropDef TABLE_MED = new PropDef("medical table", WASTE, 257, 32, 31, 32, 14, 0);
    static final PropDef DESK_RADIO = new PropDef("radio desk", WASTE, 257, 96, 31, 32, 14, 0);

    // Camp sheet: tents, stalls, fences, boxes, barrels, signs, farm plots, logs
    static final PropDef TENT_A = new PropDef("tent", CAMP, 28, 229, 68, 75, 44, 6);
    static final PropDef TENT_FLAT = new PropDef("tent", CAMP, 112, 250, 64, 54, 36, 2);
    static final PropDef TENT_GREEN = new PropDef("army tent", CAMP, 30, 336, 36, 64, 34, 2);
    static final PropDef TENT_GREEN_FLAT = new PropDef("army tent", CAMP, 80, 357, 63, 40, 28, 2);
    static final PropDef STALL_A = new PropDef("market stall", CAMP, 144, 325, 57, 75, 16, 6);
    static final PropDef STALL_B = new PropDef("market stall", CAMP, 209, 325, 57, 75, 16, 6);
    static final PropDef SHED = new PropDef("shed", CAMP, 276, 322, 50, 78, 16, 4);
    static final PropDef BOX = new PropDef("box", CAMP, 22, 438, 20, 26, 10, 1);
    static final PropDef PALLET = new PropDef("pallet", CAMP, 49, 432, 30, 31, 12, 1);
    static final PropDef BUCKET_TOOLS = new PropDef("tool bucket", CAMP, 81, 426, 29, 38, 10, 5);
    static final PropDef BUCKET_PIPES = new PropDef("pipe bucket", CAMP, 113, 424, 29, 40, 10, 5);
    static final PropDef BUCKET_WOOD = new PropDef("wood bucket", CAMP, 149, 426, 22, 38, 10, 3);
    static final PropDef SIGN_ELECTRIC = new PropDef("warning sign", CAMP, 213, 434, 24, 30, 4, 9);
    static final PropDef SIGN_SHELTER = new PropDef("shelter sign", CAMP, 243, 432, 27, 32, 4, 10);
    static final PropDef SIGN_KEEP_OUT = new PropDef("keep out sign", CAMP, 287, 433, 72, 31, 5, 8);
    static final PropDef SIGN_WARNING = new PropDef("warning sign", CAMP, 213, 466, 24, 30, 4, 9);
    static final PropDef SIGN_TOXIC = new PropDef("toxic sign", CAMP, 245, 466, 24, 30, 4, 9);
    static final PropDef SIGN_QUARANTINE = new PropDef("quarantine sign", CAMP, 287, 466, 72, 30, 5, 8);
    static final PropDef BOX_OPEN = new PropDef("open box", CAMP, 18, 470, 28, 26, 12, 2);
    static final PropDef BOX_PILE = new PropDef("box pile", CAMP, 55, 480, 37, 36, 16, 1);
    static final PropDef BOX_SUPPLY = new PropDef("supply boxes", CAMP, 99, 482, 38, 35, 14, 2);
    static final PropDef WATER_TANK_H = new PropDef("water tank", CAMP, 144, 474, 32, 21, 12, 1);
    static final PropDef WATER_TANK_V = new PropDef("water tank", CAMP, 181, 464, 22, 32, 16, 1);
    static final PropDef BOX_WATER = new PropDef("water box", CAMP, 18, 502, 28, 26, 12, 2);
    static final PropDef COT_V = new PropDef("cot", CAMP, 151, 496, 18, 32, 0, 0);
    static final PropDef COT_H = new PropDef("cot", CAMP, 176, 509, 32, 18, 0, 0);
    static final PropDef TOOLBOXES = new PropDef("toolboxes", CAMP, 240, 496, 46, 32, 12, 2);
    static final PropDef BOX_TOOLS = new PropDef("tool box", CAMP, 18, 536, 29, 24, 12, 2);
    static final PropDef BOX_EXTINGUISHER = new PropDef("box", CAMP, 49, 531, 30, 29, 12, 2);
    static final PropDef BOX_AMMO = new PropDef("ammo box", CAMP, 83, 529, 28, 31, 12, 2);
    static final PropDef BOX_MEDS = new PropDef("med box", CAMP, 114, 534, 29, 26, 12, 2);
    static final PropDef GAS_TANK = new PropDef("gas tank", CAMP, 21, 578, 23, 34, 14, 1);
    static final PropDef CRATE_CLUSTER = new PropDef("crates", CAMP, 48, 574, 63, 45, 22, 2);
    static final PropDef AMMO_CRATE_V = new PropDef("ammo crate", CAMP, 112, 583, 13, 38, 14, 0);
    static final PropDef AMMO_CRATE_H = new PropDef("ammo crate", CAMP, 145, 603, 30, 18, 12, 0);
    static final PropDef BARREL_STACK = new PropDef("barrels", CAMP, 190, 580, 37, 40, 18, 2);
    static final PropDef SIDE_TABLE = new PropDef("table", CAMP, 21, 628, 23, 28, 10, 1);
    static final PropDef CRATE_EMPTY = new PropDef("crate", CAMP, 52, 625, 24, 30, 14, 1);
    static final PropDef CRATE_CLOSED = new PropDef("crate", CAMP, 84, 625, 24, 30, 14, 1);
    static final PropDef BARREL_WOOD = new PropDef("barrel", CAMP, 117, 626, 22, 29, 12, 2);
    static final PropDef BARREL_GREY = new PropDef("barrel", CAMP, 149, 626, 22, 29, 12, 2);
    static final PropDef BARREL_BLUE = new PropDef("barrel", CAMP, 180, 624, 22, 31, 12, 2);
    static final PropDef BARREL_OIL = new PropDef("oil barrel", CAMP, 212, 624, 22, 31, 12, 2);
    static final PropDef STALL_FOOD = new PropDef("food stall", CAMP, 18, 669, 60, 40, 14, 2);
    static final PropDef STALL_TRADE = new PropDef("trade stall", CAMP, 80, 667, 64, 42, 14, 2);
    static final PropDef TABLE_FOOD = new PropDef("food table", CAMP, 146, 677, 60, 32, 14, 2);
    static final PropDef TABLE_TRADE = new PropDef("trade table", CAMP, 208, 677, 64, 32, 14, 2);
    static final PropDef PIPE_STACK = new PropDef("pipes", CAMP, 278, 666, 21, 54, 10, 2);
    static final PropDef WATER_TOWER = new PropDef("water tower", CAMP, 18, 722, 28, 62, 14, 1);
    static final PropDef PLANTER = new PropDef("planter", CAMP, 84, 726, 24, 26, 12, 1);
    static final PropDef PLANTER_LONG = new PropDef("planter", CAMP, 48, 760, 64, 24, 12, 1);
    static final PropDef PLANTER_TALL = new PropDef("planter", CAMP, 116, 720, 24, 64, 50, 1);
    static final PropDef CROP_BEET = new PropDef("crop", CAMP, 148, 725, 24, 27, 12, 1);
    static final PropDef CROP_ONION = new PropDef("crop", CAMP, 180, 723, 24, 29, 12, 1);
    static final PropDef CROP_WHEAT = new PropDef("crop", CAMP, 148, 756, 24, 28, 12, 1);
    static final PropDef CROP_CORN = new PropDef("crop", CAMP, 180, 756, 24, 28, 12, 1);
    static final PropDef LOGS_PAIR = new PropDef("logs", CAMP, 278, 721, 21, 63, 14, 1);
    static final PropDef LOGS_TIED = new PropDef("logs", CAMP, 304, 721, 32, 63, 14, 1);
    static final PropDef LOG_SINGLE = new PropDef("log", CAMP, 347, 726, 11, 58, 12, 0);
    static final PropDef TRASH_A = new PropDef("trash", CAMP, 244, 552, 25, 39, 0, 0);
    static final PropDef TRASH_B = new PropDef("trash", CAMP, 272, 556, 34, 35, 0, 0);
    static final PropDef TRASH_C = new PropDef("trash", CAMP, 317, 552, 38, 39, 0, 0);
    static final PropDef PAPERS = new PropDef("papers", CAMP, 289, 497, 15, 15, 0, 0);
    static final PropDef PAPERS_B = new PropDef("papers", CAMP, 306, 497, 12, 15, 0, 0);
    static final PropDef SACK = new PropDef("sack", CAMP, 304, 513, 16, 15, 0, 0);

    // Interior sheet: bunker furniture
    static final PropDef CABINET = new PropDef("cabinet", INTER, 0, 224, 32, 45, 18, 0);
    static final PropDef LOCKER_PAIR = new PropDef("lockers", INTER, 47, 224, 34, 52, 18, 0);
    static final PropDef CUPBOARD = new PropDef("cupboard", INTER, 111, 224, 34, 45, 18, 0);
    static final PropDef LOCKER_TALL = new PropDef("tall lockers", INTER, 175, 224, 34, 59, 18, 0);
    static final PropDef SHELF_EMPTY = new PropDef("shelf", INTER, 227, 229, 26, 49, 14, 0);
    static final PropDef SHELF_WIDE = new PropDef("shelf", INTER, 268, 228, 40, 49, 14, 0);
    static final PropDef LOCKER = new PropDef("locker", INTER, 8, 288, 17, 45, 14, 0);
    static final PropDef LOCKER_BROKEN = new PropDef("broken locker", INTER, 40, 288, 22, 46, 14, 0);
    static final PropDef LOCKER_DOUBLE = new PropDef("lockers", INTER, 79, 288, 34, 45, 14, 0);
    static final PropDef FRIDGE = new PropDef("fridge", INTER, 130, 288, 28, 44, 16, 0);
    static final PropDef FRIDGE_OPEN = new PropDef("open fridge", INTER, 161, 288, 29, 45, 16, 0);
    static final PropDef FRIDGE_DOUBLE = new PropDef("fridges", INTER, 207, 288, 34, 48, 16, 0);
    static final PropDef SHELF_STOCKED = new PropDef("shelf", INTER, 268, 295, 40, 49, 14, 0);
    static final PropDef DESK_V = new PropDef("desk", INTER, 0, 353, 26, 48, 30, 0);
    static final PropDef DESK_V2 = new PropDef("desk", INTER, 38, 353, 26, 48, 30, 0);
    static final PropDef DESK_H = new PropDef("desk", INTER, 71, 354, 50, 30, 16, 0);
    static final PropDef VENDING_RED = new PropDef("vending machine", INTER, 128, 352, 32, 42, 16, 0);
    static final PropDef VENDING_BLACK = new PropDef("vending machine", INTER, 160, 352, 32, 42, 16, 0);
    static final PropDef VENDING_COFFEE = new PropDef("coffee machine", INTER, 193, 352, 30, 45, 16, 0);
    static final PropDef RACK = new PropDef("rack", INTER, 225, 364, 30, 32, 12, 0);
    static final PropDef RACK_STOCKED = new PropDef("rack", INTER, 257, 364, 30, 32, 12, 0);
    static final PropDef DESK_PC_V = new PropDef("computer desk", INTER, 0, 417, 26, 50, 30, 0);
    static final PropDef DESK_LAB_V = new PropDef("lab desk", INTER, 38, 416, 26, 51, 30, 0);
    static final PropDef DESK_PC_H = new PropDef("computer desk", INTER, 71, 416, 50, 32, 16, 0);
    static final PropDef DRAWERS = new PropDef("drawers", INTER, 132, 438, 23, 22, 12, 0);
    static final PropDef DRAWERS_B = new PropDef("drawers", INTER, 165, 438, 23, 22, 12, 0);
    static final PropDef GURNEY_CURTAIN = new PropDef("ward bed", INTER, 192, 432, 45, 46, 18, 0);
    static final PropDef WATER_COOLER = new PropDef("water cooler", INTER, 264, 425, 16, 30, 10, 0);
    static final PropDef BED_V = new PropDef("bed", INTER, 4, 480, 24, 39, 34, 0);
    static final PropDef BED_H = new PropDef("bed", INTER, 57, 483, 39, 29, 20, 0);
    static final PropDef BED_FRAME_V = new PropDef("bed frame", INTER, 100, 480, 24, 39, 34, 0);
    static final PropDef BED_FRAME_H = new PropDef("bed frame", INTER, 153, 483, 39, 29, 20, 0);
    static final PropDef OP_TABLE = new PropDef("operating table", INTER, 217, 480, 39, 27, 18, 0);
    static final PropDef OP_TABLE_B = new PropDef("operating table", INTER, 192, 496, 39, 43, 18, 0);
    static final PropDef BED_H2 = new PropDef("bed", INTER, 32, 515, 39, 29, 20, 0);
    static final PropDef BED_FRAME_H2 = new PropDef("bed frame", INTER, 128, 515, 39, 29, 20, 0);

    // Animated camp props (strips)
    static final PropDef FLAME_BARREL = new PropDef("flame barrel", "tiles/flame_barrel.png", 0, 0, 64, 64, 12, 22, 7, 5, 6);
    static final PropDef FLAG = new PropDef("camp flag", "tiles/flag.png", 0, 0, 64, 64, 10, 21, 7, 5, 7);
    static final PropDef NPC_TRADER = new PropDef("trader", "characters/npc_idle.png", 0, 0, 32, 32, 6, 9, 6, 0, 9);
    static final PropDef NPC_GUARD = new PropDef("guard", "characters/npc2_idle.png", 0, 0, 32, 32, 6, 9, 6, 0, 10);

}
