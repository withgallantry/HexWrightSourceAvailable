package com.bluup.hexwright.server.worldgen.bindstone;

import com.bluup.hexwright.server.bindstone.BindstonePillar;
import com.bluup.hexwright.server.worldgen.HexwrightWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

public class BindstonePillarPiece extends StructurePiece {

    private static final int LAKE_RADIUS = BindstonePillarStructure.LAKE_RADIUS;
    private static final int WALL_RADIUS = BindstonePillarStructure.WALL_RADIUS;
    private static final int LAKE_DEPTH = BindstonePillarStructure.LAKE_DEPTH;
    private static final int REQUIRED_CLEARANCE = BindstonePillarStructure.REQUIRED_CLEARANCE;

    private static final int MOAT_CLEARING = 8;

    private final int centreX;
    private final int centreY;
    private final int centreZ;

    public BindstonePillarPiece(BlockPos centre) {
        super(HexwrightWorldgen.BINDSTONE_PILLAR_PIECE, 0, boundsFor(centre));
        this.centreX = centre.getX();
        this.centreY = centre.getY();
        this.centreZ = centre.getZ();
    }

    public BindstonePillarPiece(CompoundTag tag) {
        super(HexwrightWorldgen.BINDSTONE_PILLAR_PIECE, tag);
        this.centreX = tag.getInt("CX");
        this.centreY = tag.getInt("CY");
        this.centreZ = tag.getInt("CZ");
    }

    private static BoundingBox boundsFor(BlockPos centre) {
        return new BoundingBox(
            centre.getX() - WALL_RADIUS, centre.getY() - LAKE_DEPTH, centre.getZ() - WALL_RADIUS,
            centre.getX() + WALL_RADIUS, centre.getY() + REQUIRED_CLEARANCE, centre.getZ() + WALL_RADIUS);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("CX", this.centreX);
        tag.putInt("CY", this.centreY);
        tag.putInt("CZ", this.centreZ);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager,
                            ChunkGenerator generator, RandomSource random, BoundingBox chunkBox,
                            ChunkPos chunkPos, BlockPos pivot) {
        BlockPos centre = new BlockPos(this.centreX, this.centreY, this.centreZ);
        if (!chunkBox.isInside(centre)) {
            return;
        }
        digLake(level, centre);
        BindstonePillar.build(level, centre.above(2));
    }

    private static void digLake(WorldGenLevel level, BlockPos centre) {
        BlockState rock = centre.getY() < 0
            ? Blocks.DEEPSLATE.defaultBlockState()
            : Blocks.STONE.defaultBlockState();
        BlockState lava = Blocks.LAVA.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        int groundTop = centre.getY();
        int pan = groundTop - LAKE_DEPTH;

        for (int dx = -WALL_RADIUS; dx <= WALL_RADIUS; dx++) {
            for (int dz = -WALL_RADIUS; dz <= WALL_RADIUS; dz++) {
                int distanceSqr = dx * dx + dz * dz;
                if (distanceSqr > WALL_RADIUS * WALL_RADIUS) {
                    continue;
                }
                int x = centre.getX() + dx;
                int z = centre.getZ() + dz;

                if (distanceSqr > LAKE_RADIUS * LAKE_RADIUS) {
                    for (int y = pan; y <= groundTop; y++) {
                        cursor.set(x, y, z);
                        if (isClear(level.getBlockState(cursor))) {
                            level.setBlock(cursor, rock, Block.UPDATE_CLIENTS);
                        }
                    }
                    continue;
                }

                cursor.set(x, pan, z);
                level.setBlock(cursor, rock, Block.UPDATE_CLIENTS);

                boolean island = Math.abs(dx) <= 2 && Math.abs(dz) <= 2;
                for (int y = pan + 1; y <= groundTop; y++) {
                    cursor.set(x, y, z);
                    level.setBlock(cursor, island ? rock : lava, Block.UPDATE_CLIENTS);
                }

                if (island) {
                    cursor.set(x, groundTop + 1, z);
                    level.setBlock(cursor, rock, Block.UPDATE_CLIENTS);
                    clearColumn(level, cursor, x, z, groundTop + 2, groundTop + REQUIRED_CLEARANCE, air);
                    continue;
                }

                clearColumn(level, cursor, x, z, groundTop + 1, groundTop + MOAT_CLEARING, air);
            }
        }
    }

    private static void clearColumn(WorldGenLevel level, BlockPos.MutableBlockPos cursor,
                                    int x, int z, int from, int to, BlockState air) {
        for (int y = from; y <= to; y++) {
            cursor.set(x, y, z);
            if (!level.getBlockState(cursor).isAir()) {
                level.setBlock(cursor, air, Block.UPDATE_CLIENTS);
            }
        }
    }

    private static boolean isClear(BlockState state) {
        return state.isAir() || state.canBeReplaced();
    }
}
