package com.bluup.hexwright.server.block;

import com.bluup.hexwright.Hexwright;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.Arrays;

public enum DungeonProp {


    ALTAR("altar", Stance.WALL, 5, 13, 8, 21, 24, 0),
    SHRINE("shrine", Stance.WALL, 7, 42, 8, 17, 53, 3),
    STATUE("statue", Stance.WALL, 0, 12, 8, 10, 51, 0),
    SARCOPHAGUS("sarcophagus", Stance.WALL, 0, 22, 8, 26, 32, 0),
    GIBBET("gibbet", Stance.FLOOR, 0, 30, 30, 12, 80, 1),
    HANGING_CAGE("hanging_cage", Stance.CEILING, 0, 12, 12, 12, 0, 72),
    HANGED_SKELETON("hanged_skeleton", Stance.CEILING, 0, 7, 7, 10, 1, 54),
    BOOKSHELF("bookshelf", Stance.WALL, 0, 10, 8, 19, 44, 0),
    WEAPON_STAND("weapon_stand", Stance.WALL, 0, 4, 8, 20, 35, 0),
    TABLE("table", Stance.FLOOR, 0, 13, 13, 16, 17, 0),
    DRAPED_TABLE("draped_table", Stance.FLOOR, 0, 13, 13, 17, 18, 0),
    CHAIR("chair", Stance.FLOOR, 0, 7, 7, 7, 24, 1),
    CRATE("crate", Stance.FLOOR, 0, 8, 8, 10, 18, 0),
    WAGON("wagon", Stance.FLOOR, 0, 28, 28, 19, 31, 0),
    CANDELABRUM("candelabrum", Stance.WALL, 10, 20, 8, 4, 41, 0),
    CANDLE_SKULL("candle_skull", Stance.WALL, 4, 0, 8, 4, 15, 0),
    SKELETON_REMAINS("skeleton_remains", Stance.WALL, 0, 0, 8, 6, 33, 0),
    CHARRED_REMAINS("charred_remains", Stance.WALL, 0, 0, 8, 6, 33, 0),
    MOSSY_REMAINS("mossy_remains", Stance.WALL, 0, 0, 8, 6, 33, 0),
    TRAINING_DUMMY_1("training_dummy_1", Stance.FLOOR, 0, 9, 9, 17, 45, 0),
    TRAINING_DUMMY_2("training_dummy_2", Stance.FLOOR, 0, 9, 9, 20, 52, 0),
    TRAINING_DUMMY_3("training_dummy_3", Stance.FLOOR, 0, 12, 12, 19, 45, 0),
    TRAINING_DUMMY_4("training_dummy_4", Stance.FLOOR, 0, 12, 12, 30, 47, 0),
    TRAINING_DUMMY_5("training_dummy_5", Stance.FLOOR, 0, 9, 9, 18, 45, 0),

    ALCHEMY_TABLE("alchemy_table", Stance.WALL, 0, 31, 8, 25, 29, 4),
    SCRIBES_TABLE("scribes_table", Stance.WALL, 0, 13, 8, 18, 20, 4),
    NOTICE_BOARD("notice_board", Stance.WALL, 0, -1, 8, 26, 44, 0),
    WALL_SHELF("wall_shelf", Stance.WALL, 0, 25, 8, 30, 19, -1),
    STOCKED_SHELF("stocked_shelf", Stance.WALL, 0, 2, 8, 19, 52, -24),
    THRONE("throne", Stance.WALL, 0, 60, 8, 24, 48, 1),
    ARCHERY_TARGET("archery_target", Stance.WALL, 0, 6, 8, 11, 30, 0),
    SWORD_RACK("sword_rack", Stance.WALL, 0, 13, 8, 26, 41, 3),
    EMPTY_RACK("empty_rack", Stance.WALL, 0, 13, 8, 26, 41, 3),
    ARMOURY_RACK("armoury_rack", Stance.FLOOR, 0, 26, 26, 24, 62, 3),
    SHORT_LADDER("short_ladder", Stance.WALL, 0, 14, 8, 15, 48, 0),
    TALL_LADDER("tall_ladder", Stance.WALL, 0, 25, 8, 16, 77, 0),
    HUNG_BANNER("hung_banner", Stance.WALL, 0, -5, 8, 8, 32, 0),
    SIGIL_BANNER("sigil_banner", Stance.WALL, 0, -5, 8, 8, 33, 0),
    STANDING_BANNER("standing_banner", Stance.WALL, 0, -5, 8, 8, 32, 0),
    CANDLE_STAND("candle_stand", Stance.WALL, 8, -2, 8, 3, 41, 0),
    GILT_CANDLE_STAND("gilt_candle_stand", Stance.WALL, 8, -2, 8, 3, 41, 0),
    ROUND_TABLE("round_table", Stance.FLOOR, 0, 10, 10, 10, 20, 0),
    LONG_TABLE("long_table", Stance.FLOOR, 0, 16, 16, 16, 17, 3),
    COVERED_TABLE("covered_table", Stance.FLOOR, 0, 10, 10, 10, 19, 0),
    SIDE_TABLE("side_table", Stance.FLOOR, 0, 5, 5, 8, 7, 0),
    PADDED_CHAIR("padded_chair", Stance.FLOOR, 0, 8, 8, 12, 24, 1),
    WOODEN_CHAIR("wooden_chair", Stance.FLOOR, 0, 7, 7, 8, 23, 1),
    STOOL("stool", Stance.FLOOR, 0, 6, 6, 6, 9, 0),
    LOW_STOOL("low_stool", Stance.FLOOR, 0, 6, 6, 6, 10, 2),
    TALL_STOOL("tall_stool", Stance.FLOOR, 0, 6, 6, 6, 16, 1),
    STONE_BENCH("stone_bench", Stance.FLOOR, 0, 17, 17, 9, 11, 0),
    PLANK_BENCH("plank_bench", Stance.FLOOR, 0, 19, 19, 9, 22, 0),
    TALL_CRATE("tall_crate", Stance.FLOOR, 0, 16, 16, 16, 28, 5),
    LONG_CRATE("long_crate", Stance.FLOOR, 0, 13, 13, 9, 9, 1),
    STACKED_CRATE("stacked_crate", Stance.FLOOR, 0, 9, 9, 9, 18, 0),
    BARREL("barrel", Stance.FLOOR, 0, 8, 8, 8, 18, 0),
    OPEN_CRATE("open_crate", Stance.FLOOR, 0, 7, 7, 5, 7, 0),
    BASKET("basket", Stance.FLOOR, 0, 9, 9, 7, 14, 0),
    APPLE_CRATE("apple_crate", Stance.FLOOR, 0, 7, 7, 5, 7, 0),
    APPLE_BASKET("apple_basket", Stance.FLOOR, 0, 9, 9, 7, 14, 0),
    CARROT_CRATE("carrot_crate", Stance.FLOOR, 0, 7, 7, 5, 7, 0),
    CARROT_BASKET("carrot_basket", Stance.FLOOR, 0, 9, 9, 7, 14, 0),
    POTATO_CRATE("potato_crate", Stance.FLOOR, 0, 7, 7, 5, 7, 0),
    VASE("vase", Stance.FLOOR, 0, 5, 5, 7, 17, 0),
    SMALL_VASE("small_vase", Stance.FLOOR, 0, 7, 7, 9, 14, 0),
    POTTED_PLANT("potted_plant", Stance.FLOOR, 0, 4, 4, 4, 26, 0),
    COFFIN("coffin", Stance.FLOOR, 0, 14, 14, 31, 13, 0),
    CRYPT("crypt", Stance.FLOOR, 0, 17, 17, 32, 30, 0),
    SPRAWLED_SKELETON("sprawled_skeleton", Stance.FLOOR, 0, 11, 11, 21, 9, 1),
    SCATTERED_BONES("scattered_bones", Stance.FLOOR, 0, 12, 12, 10, 9, 1),
    SKULL_PILE("skull_pile", Stance.FLOOR, 0, 11, 11, 10, 16, 1),
    FALLEN_SWORD("fallen_sword", Stance.FLOOR, 0, 18, 18, 25, 36, 5),
    FIRE_CAULDRON("fire_cauldron", Stance.FLOOR, 12, 17, 17, 18, 22, 0),
    MINE_CART("mine_cart", Stance.FLOOR, 0, 10, 10, 11, 13, 0),
    LADEN_MINE_CART("laden_mine_cart", Stance.FLOOR, 0, 10, 10, 12, 20, 0);


    public enum Stance {
        FLOOR,
        WALL,
        CEILING
    }

    public record Shape(int front, int back, int side, int height, int drop) {

        public static int beyond(int sixteenths) {
            return Math.max(0, (sixteenths - 8 + 15) / 16);
        }

        public int aheadBlocks() {
            return beyond(this.front);
        }

        public int sideBlocks() {
            return beyond(this.side);
        }

        public int behindBlocks() {
            return beyond(this.back);
        }

        public int headroom() {
            return Math.max(1, (this.height + 15) / 16);
        }

        public int hang() {
            return Math.max(1, (this.drop + 15) / 16);
        }
    }

    private final String propName;
    private final Stance stance;
    private final Shape shape;
    private final DungeonPropBlock block;

    DungeonProp(String propName, Stance stance, int light,
                int front, int back, int side, int height, int drop) {
        this.propName = propName;
        this.stance = stance;
        this.shape = new Shape(front, back, side, height, drop);
        BlockBehaviour.Properties settings = FabricBlockSettings.copyOf(Blocks.STONE)
            .noOcclusion()
            .isViewBlocking((state, level, pos) -> false)
            .isSuffocating((state, level, pos) -> false)
            .strength(0.6F, 1.0F)
            .noLootTable();
        if (light > 0) {
            settings = settings.lightLevel(state -> light);
        }
        this.block = new DungeonPropBlock(settings, this);
    }

    public String propName() {
        return this.propName;
    }

    public Stance stance() {
        return this.stance;
    }

    public Shape shape() {
        return this.shape;
    }

    public DungeonPropBlock block() {
        return this.block;
    }

    public static BlockEntityType<DungeonPropBlockEntity> BLOCK_ENTITY;

    public static void register() {
        for (DungeonProp prop : values()) {
            Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("prop_" + prop.propName), prop.block);
        }
        BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Hexwright.id("dungeon_prop"),
            FabricBlockEntityTypeBuilder.create(DungeonPropBlockEntity::new,
                Arrays.stream(values()).map(DungeonProp::block).toArray(Block[]::new)).build()
        );
    }
}
