package com.bluup.hexwright.server.inversion;

import com.bluup.hexwright.Hexwright;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public final class InvertedBlocks {

    public static final TagKey<Block> ARTIFICIAL = TagKey.create(Registries.BLOCK, Hexwright.id("inversion_artificial"));

    public enum Skin implements StringRepresentable {
        GRASS, DIRT, STONE, SAND, GRAVEL, SNOW, ICE, TERRACOTTA, MUD, MOSS, CLAY;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Wood implements StringRepresentable {
        OAK, SPRUCE, BIRCH, JUNGLE, ACACIA, DARK_OAK, MANGROVE, CHERRY, AZALEA, FLOWERING_AZALEA;

        private static final List<Wood> BY_NAME_LENGTH = List.of(FLOWERING_AZALEA, DARK_OAK, MANGROVE, AZALEA,
            SPRUCE, JUNGLE, ACACIA, CHERRY, BIRCH, OAK);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        boolean hasLog() {
            return this != AZALEA && this != FLOWERING_AZALEA;
        }

        static Wood of(Block block) {
            String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
            for (Wood wood : BY_NAME_LENGTH) {
                if (path.contains(wood.getSerializedName())) {
                    return wood;
                }
            }
            return OAK;
        }
    }

    public enum Form implements StringRepresentable {
        GRASS, FERN, FLOWER, BUSH, TALL_LOWER, TALL_UPPER, CANE, MUSHROOM;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Skin> SKIN = EnumProperty.create("skin", Skin.class);
    public static final EnumProperty<Wood> LEAF_WOOD = EnumProperty.create("wood", Wood.class);
    public static final EnumProperty<Wood> LOG_WOOD = EnumProperty.create("wood", Wood.class, Wood::hasLog);
    public static final EnumProperty<Form> FORM = EnumProperty.create("form", Form.class);

    public static final Block GROUND = new Ground(bedrock(SoundType.DEEPSLATE));
    public static final Block LOG = new Log(bedrock(SoundType.DEEPSLATE));
    public static final Block LEAVES = new Leaves(bedrock(SoundType.AZALEA_LEAVES)
        .noOcclusion()
        .isViewBlocking(InvertedBlocks::never)
        .isSuffocating(InvertedBlocks::never)
        .isRedstoneConductor(InvertedBlocks::never));
    public static final Block GROWTH = new Growth(bedrock(SoundType.SCULK_VEIN)
        .noCollission()
        .noOcclusion()
        .offsetType(BlockBehaviour.OffsetType.XZ));
    public static final Block LAYER = new Layer(bedrock(SoundType.DEEPSLATE));
    public static final Block VINE = new Vine(bedrock(SoundType.SCULK_VEIN).noCollission().noOcclusion());

    private static final List<Block> ALL = List.of(GROUND, LOG, LEAVES, GROWTH, LAYER, VINE);

    private InvertedBlocks() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("inverted_ground"), GROUND);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("inverted_log"), LOG);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("inverted_leaves"), LEAVES);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("inverted_growth"), GROWTH);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("inverted_layer"), LAYER);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("inverted_vine"), VINE);
    }

    public static List<Block> all() {
        return ALL;
    }

    public static boolean is(BlockState state) {
        return ALL.contains(state.getBlock());
    }

    private static BlockBehaviour.Properties bedrock(SoundType sound) {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLACK)
            .sound(sound)
            .strength(-1.0f, 3600000.0f)
            .noLootTable()
            .isValidSpawn((state, level, pos, type) -> false)
            .pushReaction(PushReaction.BLOCK);
    }

    private static boolean never(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }

    public static @Nullable BlockState mimic(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.isAir() || !state.getFluidState().isEmpty() || state.getRenderShape() == RenderShape.INVISIBLE
            || state.is(BlockTags.FIRE) || is(state)) {
            return null;
        }
        Block block = state.getBlock();
        if (state.is(BlockTags.LOGS) || block == Blocks.CACTUS || block == Blocks.BEE_NEST) {
            Direction.Axis axis = state.hasProperty(BlockStateProperties.AXIS)
                ? state.getValue(BlockStateProperties.AXIS) : Direction.Axis.Y;
            Wood wood = Wood.of(block);
            return LOG.defaultBlockState().setValue(LOG_WOOD, wood.hasLog() ? wood : Wood.OAK)
                .setValue(BlockStateProperties.AXIS, axis);
        }
        if (state.is(BlockTags.LEAVES) || block == Blocks.AZALEA || block == Blocks.FLOWERING_AZALEA) {
            return LEAVES.defaultBlockState().setValue(LEAF_WOOD, Wood.of(block));
        }
        if (block instanceof VineBlock) {
            BlockState vine = VINE.defaultBlockState();
            for (var face : VineBlock.PROPERTY_BY_DIRECTION.values()) {
                vine = vine.setValue(face, state.getValue(face));
            }
            return vine;
        }
        if (block instanceof SnowLayerBlock) {
            return LAYER.defaultBlockState().setValue(SnowLayerBlock.LAYERS, state.getValue(SnowLayerBlock.LAYERS));
        }
        if (block == Blocks.MOSS_CARPET) {
            return LAYER.defaultBlockState();
        }
        if (block == Blocks.POWDER_SNOW) {
            return GROUND.defaultBlockState().setValue(SKIN, Skin.SNOW);
        }
        if (block == Blocks.SUGAR_CANE || block == Blocks.BAMBOO) {
            return GROWTH.defaultBlockState().setValue(FORM, Form.CANE);
        }
        if (isTerrain(state, level, pos)) {
            return GROUND.defaultBlockState().setValue(SKIN, skinOf(state));
        }
        if (state.getCollisionShape(level, pos).isEmpty()) {
            return GROWTH.defaultBlockState().setValue(FORM, formOf(state));
        }
        return null;
    }

    public static boolean isTerrain(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.is(GROUND)) {
            return true;
        }
        if (is(state) || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.getBlock() == Blocks.BEE_NEST
            || !state.getFluidState().isEmpty()) {
            return false;
        }
        return state.isCollisionShapeFullBlock(level, pos);
    }

    static BlockState ground(Skin skin) {
        return GROUND.defaultBlockState().setValue(SKIN, skin);
    }

    static Skin skinOf(BlockState state) {
        Block block = state.getBlock();
        if (block == Blocks.GRASS_BLOCK || block == Blocks.MYCELIUM || block == Blocks.PODZOL) {
            return Skin.GRASS;
        }
        if (block == Blocks.MUD || block == Blocks.PACKED_MUD || block == Blocks.MUDDY_MANGROVE_ROOTS) {
            return Skin.MUD;
        }
        if (block == Blocks.MOSS_BLOCK) {
            return Skin.MOSS;
        }
        if (state.is(BlockTags.DIRT)) {
            return Skin.DIRT;
        }
        if (state.is(BlockTags.SAND) || block == Blocks.SANDSTONE || block == Blocks.RED_SANDSTONE) {
            return Skin.SAND;
        }
        if (block == Blocks.GRAVEL || block == Blocks.SUSPICIOUS_GRAVEL) {
            return Skin.GRAVEL;
        }
        if (block == Blocks.SNOW_BLOCK) {
            return Skin.SNOW;
        }
        if (state.is(BlockTags.ICE)) {
            return Skin.ICE;
        }
        if (state.is(BlockTags.TERRACOTTA)) {
            return Skin.TERRACOTTA;
        }
        if (block == Blocks.CLAY) {
            return Skin.CLAY;
        }
        return Skin.STONE;
    }

    private static Form formOf(BlockState state) {
        Block block = state.getBlock();
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            return state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER
                ? Form.TALL_LOWER : Form.TALL_UPPER;
        }
        if (state.is(BlockTags.SMALL_FLOWERS) || block == Blocks.PINK_PETALS) {
            return Form.FLOWER;
        }
        if (block == Blocks.FERN) {
            return Form.FERN;
        }
        if (block == Blocks.DEAD_BUSH || block == Blocks.SWEET_BERRY_BUSH || state.is(BlockTags.SAPLINGS)) {
            return Form.BUSH;
        }
        if (block == Blocks.BROWN_MUSHROOM || block == Blocks.RED_MUSHROOM) {
            return Form.MUSHROOM;
        }
        return Form.GRASS;
    }

    public static BlockState fallback(BlockState state) {
        if (state.is(GROUND)) {
            return switch (state.getValue(SKIN)) {
                case GRASS -> Blocks.GRASS_BLOCK.defaultBlockState();
                case DIRT -> Blocks.DIRT.defaultBlockState();
                case STONE -> Blocks.STONE.defaultBlockState();
                case SAND -> Blocks.SAND.defaultBlockState();
                case GRAVEL -> Blocks.GRAVEL.defaultBlockState();
                case SNOW -> Blocks.SNOW_BLOCK.defaultBlockState();
                case ICE -> Blocks.PACKED_ICE.defaultBlockState();
                case TERRACOTTA -> Blocks.TERRACOTTA.defaultBlockState();
                case MUD -> Blocks.MUD.defaultBlockState();
                case MOSS -> Blocks.MOSS_BLOCK.defaultBlockState();
                case CLAY -> Blocks.CLAY.defaultBlockState();
            };
        }
        if (state.is(LOG)) {
            Block log = BuiltInRegistries.BLOCK.get(new ResourceLocation(
                state.getValue(LOG_WOOD).getSerializedName() + "_log"));
            return log.defaultBlockState().hasProperty(BlockStateProperties.AXIS)
                ? log.defaultBlockState().setValue(BlockStateProperties.AXIS, state.getValue(BlockStateProperties.AXIS))
                : Blocks.OAK_LOG.defaultBlockState();
        }
        if (state.is(LEAVES)) {
            Block leaves = BuiltInRegistries.BLOCK.get(new ResourceLocation(
                state.getValue(LEAF_WOOD).getSerializedName() + "_leaves"));
            BlockState natural = leaves.defaultBlockState();
            return natural.hasProperty(BlockStateProperties.PERSISTENT)
                ? natural.setValue(BlockStateProperties.PERSISTENT, true) : Blocks.OAK_LEAVES.defaultBlockState();
        }
        if (state.is(LAYER)) {
            return Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, state.getValue(SnowLayerBlock.LAYERS));
        }
        if (state.is(VINE)) {
            BlockState vine = Blocks.VINE.defaultBlockState();
            for (var face : VineBlock.PROPERTY_BY_DIRECTION.values()) {
                vine = vine.setValue(face, state.getValue(face));
            }
            return vine;
        }
        return Blocks.AIR.defaultBlockState();
    }

    static final class Ground extends Block {
        Ground(Properties properties) {
            super(properties);
            registerDefaultState(this.stateDefinition.any().setValue(SKIN, Skin.STONE));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(SKIN);
        }
    }

    static final class Log extends RotatedPillarBlock {
        Log(Properties properties) {
            super(properties);
            registerDefaultState(defaultBlockState().setValue(LOG_WOOD, Wood.OAK));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(LOG_WOOD);
        }
    }

    static final class Leaves extends Block {
        Leaves(Properties properties) {
            super(properties);
            registerDefaultState(this.stateDefinition.any().setValue(LEAF_WOOD, Wood.OAK));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(LEAF_WOOD);
        }
    }

    static final class Growth extends Block {
        private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 13.0, 14.0);

        Growth(Properties properties) {
            super(properties);
            registerDefaultState(this.stateDefinition.any().setValue(FORM, Form.GRASS));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FORM);
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            var offset = state.getOffset(level, pos);
            return SHAPE.move(offset.x, offset.y, offset.z);
        }
    }

    static final class Layer extends SnowLayerBlock {
        Layer(Properties properties) {
            super(properties);
        }

        @Override
        public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
            return true;
        }

        @Override
        public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level,
                                      BlockPos pos, BlockPos neighbourPos) {
            return state;
        }

        @Override
        public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
            return false;
        }
    }

    static final class Vine extends VineBlock {
        Vine(Properties properties) {
            super(properties);
        }

        @Override
        public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
            return true;
        }

        @Override
        public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level,
                                      BlockPos pos, BlockPos neighbourPos) {
            return state;
        }

        @Override
        public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
            return false;
        }
    }
}
