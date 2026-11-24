// Tapatsawin Chaieua          6813105
// Thanapon Thanadunpremdet    6813106
// Tanawat Wongwad             6813107
// Natthanon Achachan          6813241
package Project3_6813107;

import java.awt.image.BufferedImage;

final class MapSprites {
    private MapSprites() {}

    record PropDef(String sheet, int sx, int sy, int w, int h, int footH, int inset,
                   int frames, int padBottom, int ticksPerFrame) {
        PropDef(String sheet, int sx, int sy, int w, int h, int footH, int inset) {
            this(sheet, sx, sy, w, h, footH, inset, 1, 0, 1);
        }

        BufferedImage[] images() {
            return frames > 1 ? AssetLoader.loadStrip(sheet, frames)
                    : new BufferedImage[]{AssetLoader.subImage(sheet, sx, sy, w, h)};
        }
    }

    private static PropDef p(String sheet, int sx, int sy, int w, int h, int footH, int inset) {
        return new PropDef(sheet, sx, sy, w, h, footH, inset);
    }

    private static PropDef anim(String sheet, int w, int h, int footH, int inset, int frames, int padBottom, int ticks) {
        return new PropDef(sheet, 0, 0, w, h, footH, inset, frames, padBottom, ticks);
    }

    static final String WASTE = "tiles/wasteland.png";
    static final String CAMP = "tiles/camp.png";
    static final String INTER = "tiles/interior.png";

    static final PropDef CAR_RED_V = p(WASTE, 0, 164, 32, 59, 46, 2);
    static final PropDef CAR_RED_H = p(WASTE, 32, 185, 64, 38, 24, 2);
    static final PropDef CAR_BLUE_V = p(WASTE, 0, 224, 32, 64, 50, 2);
    static final PropDef VAN_BLUE_H = p(WASTE, 32, 248, 77, 40, 26, 2);
    static final PropDef CAR_BURNT_V = p(WASTE, 0, 292, 32, 59, 46, 2);
    static final PropDef CAR_BURNT_H = p(WASTE, 32, 313, 63, 37, 24, 2);
    static final PropDef VAN_BURNT_V = p(WASTE, 1, 354, 30, 62, 48, 2);
    static final PropDef VAN_BURNT_H = p(WASTE, 32, 376, 76, 39, 26, 2);
    static final PropDef CRATE_METAL = p(WASTE, 131, 162, 26, 27, 16, 1);
    static final PropDef CRATE_METAL_G = p(WASTE, 160, 162, 32, 31, 16, 4);
    static final PropDef CRATE_WOOD = p(WASTE, 131, 194, 26, 27, 16, 1);
    static final PropDef CRATE_WOOD_G = p(WASTE, 160, 194, 32, 31, 16, 4);
    static final PropDef BARREL_RED = p(WASTE, 132, 225, 24, 29, 12, 2);
    static final PropDef BARREL_RED_G = p(WASTE, 162, 225, 27, 32, 12, 4);
    static final PropDef BARREL_ORANGE = p(WASTE, 132, 257, 24, 29, 12, 2);
    static final PropDef BARREL_ORANGE_G = p(WASTE, 162, 257, 27, 32, 12, 4);
    static final PropDef GARAGE_GATE = p(WASTE, 201, 178, 46, 78, 22, 0);
    static final PropDef PLANKS = p(WASTE, 196, 265, 24, 18, 0, 0);
    static final PropDef STOP_SIGN = p(WASTE, 229, 260, 22, 26, 5, 7);
    static final PropDef TIRES = p(WASTE, 130, 289, 28, 30, 14, 1);
    static final PropDef TIRES_LOW = p(WASTE, 162, 293, 28, 24, 12, 1);
    static final PropDef TIRES_G = p(WASTE, 192, 289, 32, 31, 14, 3);
    static final PropDef TIRES_LOW_G = p(WASTE, 225, 293, 31, 27, 12, 3);
    static final PropDef FENCE_BROKEN_A = p(WASTE, 128, 324, 63, 28, 8, 2);
    static final PropDef FENCE_BROKEN_B = p(WASTE, 198, 324, 53, 29, 8, 2);
    static final PropDef PUMP = p(WASTE, 141, 369, 35, 46, 16, 3);
    static final PropDef PUMP_G = p(WASTE, 205, 369, 38, 47, 16, 5);
    static final PropDef PUMP_ROW = p(WASTE, 0, 426, 128, 54, 20, 2);
    static final PropDef PUMP_ROW_BURNT = p(WASTE, 0, 490, 128, 54, 20, 2);
    static final PropDef PUMP_BURNT = p(WASTE, 13, 561, 38, 47, 16, 4);
    static final PropDef CRATE_STACK = p(WASTE, 134, 437, 51, 40, 22, 1);
    static final PropDef CRATE_STACK_G = p(WASTE, 195, 437, 57, 44, 22, 4);
    static final PropDef METAL_STACK = p(WASTE, 134, 499, 51, 41, 22, 1);
    static final PropDef METAL_STACK_G = p(WASTE, 196, 499, 55, 43, 22, 4);
    static final PropDef METAL_STACK_OPEN = p(WASTE, 134, 564, 51, 41, 22, 1);
    static final PropDef SIGN_EXIT = p(WASTE, 69, 546, 54, 30, 5, 6);
    static final PropDef SIGN_DANGER = p(WASTE, 69, 578, 54, 30, 5, 6);
    static final PropDef TABLE_WORK = p(WASTE, 257, 64, 31, 32, 14, 0);
    static final PropDef TABLE_MED = p(WASTE, 257, 32, 31, 32, 14, 0);
    static final PropDef DESK_RADIO = p(WASTE, 257, 96, 31, 32, 14, 0);

    static final PropDef TENT_A = p(CAMP, 28, 229, 68, 75, 44, 6);
    static final PropDef TENT_FLAT = p(CAMP, 112, 250, 64, 54, 36, 2);
    static final PropDef TENT_GREEN = p(CAMP, 30, 336, 36, 64, 34, 2);
    static final PropDef TENT_GREEN_FLAT = p(CAMP, 80, 357, 63, 40, 28, 2);
    static final PropDef STALL_A = p(CAMP, 144, 325, 57, 75, 16, 6);
    static final PropDef STALL_B = p(CAMP, 209, 325, 57, 75, 16, 6);
    static final PropDef SHED = p(CAMP, 276, 322, 50, 78, 16, 4);
    static final PropDef BOX = p(CAMP, 22, 438, 20, 26, 10, 1);
    static final PropDef PALLET = p(CAMP, 49, 432, 30, 31, 12, 1);
    static final PropDef BUCKET_TOOLS = p(CAMP, 81, 426, 29, 38, 10, 5);
    static final PropDef BUCKET_PIPES = p(CAMP, 113, 424, 29, 40, 10, 5);
    static final PropDef BUCKET_WOOD = p(CAMP, 149, 426, 22, 38, 10, 3);
    static final PropDef SIGN_ELECTRIC = p(CAMP, 213, 434, 24, 30, 4, 9);
    static final PropDef SIGN_SHELTER = p(CAMP, 243, 432, 27, 32, 4, 10);
    static final PropDef SIGN_KEEP_OUT = p(CAMP, 287, 433, 72, 31, 5, 8);
    static final PropDef SIGN_WARNING = p(CAMP, 213, 466, 24, 30, 4, 9);
    static final PropDef SIGN_TOXIC = p(CAMP, 245, 466, 24, 30, 4, 9);
    static final PropDef SIGN_QUARANTINE = p(CAMP, 287, 466, 72, 30, 5, 8);
    static final PropDef BOX_OPEN = p(CAMP, 18, 470, 28, 26, 12, 2);
    static final PropDef BOX_PILE = p(CAMP, 55, 480, 37, 36, 16, 1);
    static final PropDef BOX_SUPPLY = p(CAMP, 99, 482, 38, 35, 14, 2);
    static final PropDef WATER_TANK_H = p(CAMP, 144, 474, 32, 21, 12, 1);
    static final PropDef WATER_TANK_V = p(CAMP, 181, 464, 22, 32, 16, 1);
    static final PropDef BOX_WATER = p(CAMP, 18, 502, 28, 26, 12, 2);
    static final PropDef COT_V = p(CAMP, 151, 496, 18, 32, 0, 0);
    static final PropDef COT_H = p(CAMP, 176, 509, 32, 18, 0, 0);
    static final PropDef TOOLBOXES = p(CAMP, 240, 496, 46, 32, 12, 2);
    static final PropDef BOX_TOOLS = p(CAMP, 18, 536, 29, 24, 12, 2);
    static final PropDef BOX_EXTINGUISHER = p(CAMP, 49, 531, 30, 29, 12, 2);
    static final PropDef BOX_AMMO = p(CAMP, 83, 529, 28, 31, 12, 2);
    static final PropDef BOX_MEDS = p(CAMP, 114, 534, 29, 26, 12, 2);
    static final PropDef GAS_TANK = p(CAMP, 21, 578, 23, 34, 14, 1);
    static final PropDef CRATE_CLUSTER = p(CAMP, 48, 574, 63, 45, 22, 2);
    static final PropDef AMMO_CRATE_V = p(CAMP, 112, 583, 13, 38, 14, 0);
    static final PropDef AMMO_CRATE_H = p(CAMP, 145, 603, 30, 18, 12, 0);
    static final PropDef BARREL_STACK = p(CAMP, 190, 580, 37, 40, 18, 2);
    static final PropDef SIDE_TABLE = p(CAMP, 21, 628, 23, 28, 10, 1);
    static final PropDef CRATE_EMPTY = p(CAMP, 52, 625, 24, 30, 14, 1);
    static final PropDef CRATE_CLOSED = p(CAMP, 84, 625, 24, 30, 14, 1);
    static final PropDef BARREL_WOOD = p(CAMP, 117, 626, 22, 29, 12, 2);
    static final PropDef BARREL_GREY = p(CAMP, 149, 626, 22, 29, 12, 2);
    static final PropDef BARREL_BLUE = p(CAMP, 180, 624, 22, 31, 12, 2);
    static final PropDef BARREL_OIL = p(CAMP, 212, 624, 22, 31, 12, 2);
    static final PropDef STALL_FOOD = p(CAMP, 18, 669, 60, 40, 14, 2);
    static final PropDef STALL_TRADE = p(CAMP, 80, 667, 64, 42, 14, 2);
    static final PropDef TABLE_FOOD = p(CAMP, 146, 677, 60, 32, 14, 2);
    static final PropDef TABLE_TRADE = p(CAMP, 208, 677, 64, 32, 14, 2);
    static final PropDef PIPE_STACK = p(CAMP, 278, 666, 21, 54, 10, 2);
    static final PropDef WATER_TOWER = p(CAMP, 18, 722, 28, 62, 14, 1);
    static final PropDef PLANTER = p(CAMP, 84, 726, 24, 26, 12, 1);
    static final PropDef PLANTER_LONG = p(CAMP, 48, 760, 64, 24, 12, 1);
    static final PropDef PLANTER_TALL = p(CAMP, 116, 720, 24, 64, 50, 1);
    static final PropDef CROP_BEET = p(CAMP, 148, 725, 24, 27, 12, 1);
    static final PropDef CROP_ONION = p(CAMP, 180, 723, 24, 29, 12, 1);
    static final PropDef CROP_WHEAT = p(CAMP, 148, 756, 24, 28, 12, 1);
    static final PropDef CROP_CORN = p(CAMP, 180, 756, 24, 28, 12, 1);
    static final PropDef LOGS_PAIR = p(CAMP, 278, 721, 21, 63, 14, 1);
    static final PropDef LOGS_TIED = p(CAMP, 304, 721, 32, 63, 14, 1);
    static final PropDef LOG_SINGLE = p(CAMP, 347, 726, 11, 58, 12, 0);
    static final PropDef TRASH_A = p(CAMP, 244, 552, 25, 39, 0, 0);
    static final PropDef TRASH_B = p(CAMP, 272, 556, 34, 35, 0, 0);
    static final PropDef TRASH_C = p(CAMP, 317, 552, 38, 39, 0, 0);
    static final PropDef PAPERS = p(CAMP, 289, 497, 15, 15, 0, 0);
    static final PropDef PAPERS_B = p(CAMP, 306, 497, 12, 15, 0, 0);
    static final PropDef SACK = p(CAMP, 304, 513, 16, 15, 0, 0);

    static final PropDef CABINET = p(INTER, 0, 224, 32, 45, 18, 0);
    static final PropDef LOCKER_PAIR = p(INTER, 47, 224, 34, 52, 18, 0);
    static final PropDef CUPBOARD = p(INTER, 111, 224, 34, 45, 18, 0);
    static final PropDef LOCKER_TALL = p(INTER, 175, 224, 34, 59, 18, 0);
    static final PropDef SHELF_EMPTY = p(INTER, 227, 229, 26, 49, 14, 0);
    static final PropDef SHELF_WIDE = p(INTER, 268, 228, 40, 49, 14, 0);
    static final PropDef LOCKER = p(INTER, 8, 288, 17, 45, 14, 0);
    static final PropDef LOCKER_BROKEN = p(INTER, 40, 288, 22, 46, 14, 0);
    static final PropDef LOCKER_DOUBLE = p(INTER, 79, 288, 34, 45, 14, 0);
    static final PropDef FRIDGE = p(INTER, 130, 288, 28, 44, 16, 0);
    static final PropDef FRIDGE_OPEN = p(INTER, 161, 288, 29, 45, 16, 0);
    static final PropDef FRIDGE_DOUBLE = p(INTER, 207, 288, 34, 48, 16, 0);
    static final PropDef SHELF_STOCKED = p(INTER, 268, 295, 40, 49, 14, 0);
    static final PropDef DESK_V = p(INTER, 0, 353, 26, 48, 30, 0);
    static final PropDef DESK_V2 = p(INTER, 38, 353, 26, 48, 30, 0);
    static final PropDef DESK_H = p(INTER, 71, 354, 50, 30, 16, 0);
    static final PropDef VENDING_RED = p(INTER, 128, 352, 32, 42, 16, 0);
    static final PropDef VENDING_BLACK = p(INTER, 160, 352, 32, 42, 16, 0);
    static final PropDef VENDING_COFFEE = p(INTER, 193, 352, 30, 45, 16, 0);
    static final PropDef RACK = p(INTER, 225, 364, 30, 32, 12, 0);
    static final PropDef RACK_STOCKED = p(INTER, 257, 364, 30, 32, 12, 0);
    static final PropDef DESK_PC_V = p(INTER, 0, 417, 26, 50, 30, 0);
    static final PropDef DESK_LAB_V = p(INTER, 38, 416, 26, 51, 30, 0);
    static final PropDef DESK_PC_H = p(INTER, 71, 416, 50, 32, 16, 0);
    static final PropDef DRAWERS = p(INTER, 132, 438, 23, 22, 12, 0);
    static final PropDef DRAWERS_B = p(INTER, 165, 438, 23, 22, 12, 0);
    static final PropDef GURNEY_CURTAIN = p(INTER, 192, 432, 45, 46, 18, 0);
    static final PropDef WATER_COOLER = p(INTER, 264, 425, 16, 30, 10, 0);
    static final PropDef BED_V = p(INTER, 4, 480, 24, 39, 34, 0);
    static final PropDef BED_H = p(INTER, 57, 483, 39, 29, 20, 0);
    static final PropDef BED_FRAME_V = p(INTER, 100, 480, 24, 39, 34, 0);
    static final PropDef BED_FRAME_H = p(INTER, 153, 483, 39, 29, 20, 0);
    static final PropDef OP_TABLE = p(INTER, 217, 480, 39, 27, 18, 0);
    static final PropDef OP_TABLE_B = p(INTER, 192, 496, 39, 43, 18, 0);
    static final PropDef BED_H2 = p(INTER, 32, 515, 39, 29, 20, 0);
    static final PropDef BED_FRAME_H2 = p(INTER, 128, 515, 39, 29, 20, 0);

    static final PropDef FLAME_BARREL = anim("tiles/flame_barrel.png", 64, 64, 12, 22, 7, 5, 6);
    static final PropDef FLAG = anim("tiles/flag.png", 64, 64, 10, 21, 7, 5, 7);
    static final PropDef NPC_TRADER = anim("characters/npc_idle.png", 32, 32, 6, 9, 6, 0, 9);
    static final PropDef NPC_GUARD = anim("characters/npc2_idle.png", 32, 32, 6, 9, 6, 0, 10);
}
