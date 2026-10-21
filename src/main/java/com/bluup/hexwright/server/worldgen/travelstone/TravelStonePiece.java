package com.bluup.hexwright.server.worldgen.travelstone;

import com.bluup.hexwright.server.dungeon.InstancedDungeons;
import com.bluup.hexwright.server.worldgen.HexwrightWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

public class TravelStonePiece extends StructurePiece {

    private static final int PAD = TravelStoneStructure.PAD_RADIUS;
    private static final int BLEND = TravelStoneStructure.BLEND_RADIUS;

    private static final int UNDERHANG = 16;
    private static final int HEADROOM = 24;

    private static final int SOIL_DEPTH = 3;

    private static final int ALTAR_FRONT = 6;
    private static final int ALTAR_SIDE = 3;

    private static final int STONE_CLEARANCE = 5;
    private static final int PATH_HALF_WIDTH = 3;
    private static final int RING_HALF_DEPTH = 3;

    private static final int PICKS = 72;

    private static final BlockState[] ROCK = {
        Blocks.STONE.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.STONE.defaultBlockState(),
        Blocks.ANDESITE.defaultBlockState(), Blocks.ANDESITE.defaultBlockState(), Blocks.ANDESITE.defaultBlockState(),
        Blocks.COBBLESTONE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(),
        Blocks.GRAVEL.defaultBlockState(), Blocks.GRAVEL.defaultBlockState(),
        Blocks.TUFF.defaultBlockState(), Blocks.MOSSY_COBBLESTONE.defaultBlockState()
    };

    private static final BlockState[] SANDSTONE = {
        Blocks.SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(),
        Blocks.SMOOTH_SANDSTONE.defaultBlockState(), Blocks.SMOOTH_SANDSTONE.defaultBlockState(),
        Blocks.SAND.defaultBlockState(), Blocks.SAND.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState()
    };

    private static final BlockState[] BADLANDS = {
        Blocks.RED_SANDSTONE.defaultBlockState(), Blocks.RED_SANDSTONE.defaultBlockState(),
        Blocks.RED_SANDSTONE.defaultBlockState(), Blocks.TERRACOTTA.defaultBlockState(),
        Blocks.TERRACOTTA.defaultBlockState(), Blocks.RED_SAND.defaultBlockState(), Blocks.RED_SAND.defaultBlockState(),
        Blocks.ORANGE_TERRACOTTA.defaultBlockState(), Blocks.BROWN_TERRACOTTA.defaultBlockState()
    };

    private final int originX;
    private final int originY;
    private final int originZ;
    private final Direction front;

    public TravelStonePiece(BlockPos origin, Direction front) {
        super(HexwrightWorldgen.TRAVEL_STONE_PIECE, 0, boundsFor(origin));
        this.originX = origin.getX();
        this.originY = origin.getY();
        this.originZ = origin.getZ();
        this.front = front;
    }

    public TravelStonePiece(CompoundTag tag) {
        super(HexwrightWorldgen.TRAVEL_STONE_PIECE, tag);
        this.originX = tag.getInt("OX");
        this.originY = tag.getInt("OY");
        this.originZ = tag.getInt("OZ");
        Direction front = Direction.byName(tag.getString("Front"));
        this.front = front != null && front.getAxis().isHorizontal() ? front : Direction.NORTH;
    }

    private static BoundingBox boundsFor(BlockPos origin) {
        return new BoundingBox(origin.getX() - BLEND, origin.getY() - UNDERHANG, origin.getZ() - BLEND,
            origin.getX() + BLEND, origin.getY() + HEADROOM, origin.getZ() + BLEND);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("OX", this.originX);
        tag.putInt("OY", this.originY);
        tag.putInt("OZ", this.originZ);
        tag.putString("Front", this.front.getName());
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
                            RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        long site = (long) this.originX * 341873128712L + (long) this.originZ * 132897987541L + this.originY;
        for (int x = Math.max(chunkBox.minX(), this.originX - BLEND); x <= Math.min(chunkBox.maxX(), this.originX + BLEND); x++) {
            for (int z = Math.max(chunkBox.minZ(), this.originZ - BLEND); z <= Math.min(chunkBox.maxZ(), this.originZ + BLEND); z++) {
                shapeColumn(level, chunkBox, x, z, RandomSource.create(Mth.getSeed(x, 0, z) ^ site));
            }
        }
        placeBoulders(level, chunkBox, RandomSource.create(site));
        Direction side = this.front.getClockWise();
        place(level, chunkBox, new BlockPos(this.originX, this.originY, this.originZ),
            InstancedDungeons.TRAVEL_STONE.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, this.front));
        place(level, chunkBox, new BlockPos(this.originX, this.originY, this.originZ)
            .relative(this.front, ALTAR_FRONT).relative(side, ALTAR_SIDE), InstancedDungeons.CRYSTAL_ALTAR.defaultBlockState());
    }

    private void shapeColumn(WorldGenLevel level, BoundingBox chunkBox, int x, int z, RandomSource random) {
        double distance = Math.sqrt(Mth.square(x - this.originX) + Mth.square(z - this.originZ));
        double rim = BLEND - random.nextDouble() * 2.0;
        float paveRoll = random.nextFloat();
        int pick = random.nextInt(PICKS);
        if (distance > rim) {
            return;
        }
        int natural = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
        BlockState top = level.getBlockState(new BlockPos(x, natural - 1, z));
        if (!top.getFluidState().isEmpty()) {
            return;
        }
        BlockState below = level.getBlockState(new BlockPos(x, natural - 2, z));
        BlockState soil = below.isAir() || !below.getFluidState().isEmpty() ? Blocks.STONE.defaultBlockState() : below;
        double blend = distance <= PAD ? 0.0 : Mth.clamp((distance - PAD) / (rim - PAD), 0.0, 1.0);
        double ease = blend * blend * (3.0 - 2.0 * blend);
        int target = (int) Math.round(Mth.lerp(ease, this.originY, natural));
        if (natural < target) {
            for (int y = natural - 1; y < target - 1; y++) {
                place(level, chunkBox, new BlockPos(x, y, z),
                    target - 1 - y <= SOIL_DEPTH ? soil : Blocks.STONE.defaultBlockState());
            }
        } else {
            for (int y = target; y < natural; y++) {
                place(level, chunkBox, new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
            }
        }
        double paved = distance <= PAD ? 1.0 : 0.9 * (1.0 - ease) * (1.0 - ease);
        place(level, chunkBox, new BlockPos(x, target - 1, z), paveRoll < paved ? paletteFor(top, pick) : top);
    }

    private void placeBoulders(WorldGenLevel level, BoundingBox chunkBox, RandomSource random) {
        int count = 4 + random.nextInt(4);
        Direction side = this.front.getClockWise();
        for (int index = 0; index < count; index++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double radius = PAD - 2 + random.nextDouble() * (BLEND - PAD);
            int height = 1 + random.nextInt(2);
            boolean wide = random.nextBoolean();
            int pick = random.nextInt(PICKS);
            int capPick = random.nextInt(PICKS);
            int dx = (int) Math.round(Math.cos(angle) * radius);
            int dz = (int) Math.round(Math.sin(angle) * radius);
            int across = dx * side.getStepX() + dz * side.getStepZ();
            int along = dx * this.front.getStepX() + dz * this.front.getStepZ();
            int altarX = this.front.getStepX() * ALTAR_FRONT + side.getStepX() * ALTAR_SIDE;
            int altarZ = this.front.getStepZ() * ALTAR_FRONT + side.getStepZ() * ALTAR_SIDE;
            if (dx * dx + dz * dz < STONE_CLEARANCE * STONE_CLEARANCE || Math.abs(across) <= PATH_HALF_WIDTH
                || Math.abs(along) <= RING_HALF_DEPTH || Math.abs(dx - altarX) <= 2 && Math.abs(dz - altarZ) <= 2) {
                continue;
            }
            placeBoulderColumn(level, chunkBox, dx, dz, height, pick, capPick);
            if (wide) {
                placeBoulderColumn(level, chunkBox, dx + side.getStepX(), dz + side.getStepZ(), 1, pick, capPick);
            }
        }
    }

    private void placeBoulderColumn(WorldGenLevel level, BoundingBox chunkBox, int dx, int dz, int height,
                                    int pick, int capPick) {
        int x = this.originX + dx;
        int z = this.originZ + dz;
        if (!chunkBox.isInside(new BlockPos(x, chunkBox.minY(), z))) {
            return;
        }
        int ground = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
        BlockState under = level.getBlockState(new BlockPos(x, ground - 1, z));
        for (int y = 0; y < height; y++) {
            BlockState block = paletteFor(under, y == height - 1 && height > 1 ? capPick : pick);
            place(level, chunkBox, new BlockPos(x, ground + y, z), soilless(block));
        }
    }

    private static BlockState soilless(BlockState state) {
        return state.is(Blocks.GRAVEL) || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)
            ? Blocks.COBBLESTONE.defaultBlockState()
            : state;
    }

    private static BlockState paletteFor(BlockState ground, int pick) {
        BlockState[] palette = ROCK;
        if (ground.is(Blocks.SAND) || ground.is(Blocks.SANDSTONE)) {
            palette = SANDSTONE;
        } else if (ground.is(Blocks.RED_SAND) || ground.is(Blocks.RED_SANDSTONE) || ground.is(BlockTags.TERRACOTTA)) {
            palette = BADLANDS;
        }
        return palette[pick % palette.length];
    }

    private static void place(WorldGenLevel level, BoundingBox chunkBox, BlockPos pos, BlockState state) {
        if (chunkBox.isInside(pos)) {
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
    }
}
