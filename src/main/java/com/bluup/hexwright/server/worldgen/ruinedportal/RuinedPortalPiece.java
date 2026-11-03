package com.bluup.hexwright.server.worldgen.ruinedportal;

import com.bluup.hexwright.server.block.HexwrightBlocks;
import com.bluup.hexwright.server.worldgen.HexwrightWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

public class RuinedPortalPiece extends StructurePiece {

    private static final int RADIUS = RuinedPortalStructure.FOOTPRINT_RADIUS;

    private static final int PLAZA_RADIUS = 5;

    private static final int UNDERHANG = 8;
    private static final int HEADROOM = 16;

    private static final BlockState[] PALETTE = {
        Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Blocks.MOSSY_COBBLESTONE.defaultBlockState(),
        Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Blocks.MOSSY_COBBLESTONE.defaultBlockState(),
        Blocks.COBBLESTONE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(),
        Blocks.COBBLESTONE.defaultBlockState(),
        Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), Blocks.CRACKED_STONE_BRICKS.defaultBlockState(),
        Blocks.STONE_BRICKS.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState(),
        Blocks.ANDESITE.defaultBlockState()
    };

    private static final int ARCHWAY_RADIUS = 1;

    private static final int ARCHWAY_CLEAR_HEIGHT = 4;

    private static final int OFF_CHUNK = Integer.MIN_VALUE;

    private final int originX;
    private final int originZ;

    public RuinedPortalPiece(BlockPos origin) {
        super(HexwrightWorldgen.RUINED_PORTAL_PIECE, 0, boundsFor(origin));
        this.originX = origin.getX();
        this.originZ = origin.getZ();
    }

    public RuinedPortalPiece(CompoundTag tag) {
        super(HexwrightWorldgen.RUINED_PORTAL_PIECE, tag);
        this.originX = tag.getInt("OX");
        this.originZ = tag.getInt("OZ");
    }

    private static BoundingBox boundsFor(BlockPos origin) {
        return new BoundingBox(
            origin.getX() - RADIUS, origin.getY() - UNDERHANG, origin.getZ() - RADIUS,
            origin.getX() + RADIUS, origin.getY() + HEADROOM, origin.getZ() + RADIUS);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("OX", this.originX);
        tag.putInt("OZ", this.originZ);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager,
                            ChunkGenerator generator, RandomSource random, BoundingBox chunkBox,
                            ChunkPos chunkPos, BlockPos pivot) {
        RandomSource layout = RandomSource.create(
            (long) this.originX * 341873128712L + (long) this.originZ * 132897987541L);
        clearArchway(level, chunkBox);
        placeFloor(level, chunkBox, layout);
        placeRing(level, chunkBox, layout);
        placeMound(level, chunkBox, layout);
        placeFrame(level, chunkBox);
    }

    private void placeFloor(WorldGenLevel level, BoundingBox chunkBox, RandomSource random) {
        for (int dx = -PLAZA_RADIUS; dx <= PLAZA_RADIUS; dx++) {
            for (int dz = -PLAZA_RADIUS; dz <= PLAZA_RADIUS; dz++) {
                float roll = random.nextFloat();
                BlockState state = palette(random);
                double distance = Math.sqrt(dx * dx + dz * dz);
                double coverage = 1.4 - (distance / (PLAZA_RADIUS + 1.0));
                if (roll > coverage) {
                    continue;
                }
                int ground = groundAt(level, chunkBox, dx, dz);
                if (ground != OFF_CHUNK) {
                    place(level, chunkBox,
                        new BlockPos(this.originX + dx, ground - 1, this.originZ + dz), state);
                }
            }
        }
    }

    private void clearArchway(WorldGenLevel level, BoundingBox chunkBox) {
        for (int dx = -ARCHWAY_RADIUS; dx <= ARCHWAY_RADIUS; dx++) {
            for (int dz = -ARCHWAY_RADIUS; dz <= ARCHWAY_RADIUS; dz++) {
                int ground = groundAt(level, chunkBox, dx, dz);
                if (ground == OFF_CHUNK) {
                    continue;
                }
                for (int y = 0; y < ARCHWAY_CLEAR_HEIGHT; y++) {
                    BlockPos pos = new BlockPos(this.originX + dx, ground + y, this.originZ + dz);
                    if (!chunkBox.isInside(pos)) {
                        continue;
                    }
                    BlockState existing = level.getBlockState(pos);
                    if (existing.isAir() || !existing.getFluidState().isEmpty()) {
                        continue;
                    }
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private static boolean insideArchway(int dx, int dz) {
        return Math.abs(dx) <= ARCHWAY_RADIUS && Math.abs(dz) <= ARCHWAY_RADIUS;
    }

    private void placeFrame(WorldGenLevel level, BoundingBox chunkBox) {
        int ground = groundAt(level, chunkBox, 0, 0);
        if (ground != OFF_CHUNK) {
            place(level, chunkBox, new BlockPos(this.originX, ground, this.originZ),
                HexwrightBlocks.RUINED_PORTAL_FRAME_BLOCK.defaultBlockState());
        }
    }

    private void placeRing(WorldGenLevel level, BoundingBox chunkBox, RandomSource random) {
        int stumpCount = 6 + random.nextInt(5);
        for (int i = 0; i < stumpCount; i++) {
            boolean collapsed = random.nextInt(5) == 0;
            double angle = (2 * Math.PI * i / stumpCount) + (random.nextDouble() - 0.5) * 0.5;
            int radius = 4 + random.nextInt(3);
            int dx = (int) Math.round(Math.cos(angle) * radius);
            int dz = (int) Math.round(Math.sin(angle) * radius);
            int height = 2 + random.nextInt(3);
            BlockState[] courses = new BlockState[height];
            for (int y = 0; y < height; y++) {
                courses[y] = palette(random);
            }
            boolean vined = random.nextInt(3) == 0;
            if (collapsed) {
                continue;
            }
            int ground = groundAt(level, chunkBox, dx, dz);
            if (ground == OFF_CHUNK) {
                continue;
            }
            for (int y = 0; y < height; y++) {
                place(level, chunkBox,
                    new BlockPos(this.originX + dx, ground + y, this.originZ + dz), courses[y]);
            }
            if (vined) {
                placeVine(level, chunkBox, dx, dz, ground + height - 1, angle);
            }
        }

        int rubbleCount = 10 + random.nextInt(6);
        for (int i = 0; i < rubbleCount; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            int radius = 2 + random.nextInt(RADIUS - 1);
            int dx = (int) Math.round(Math.cos(angle) * radius);
            int dz = (int) Math.round(Math.sin(angle) * radius);
            BlockState state = palette(random);
            if (insideArchway(dx, dz)) {
                continue;
            }
            int ground = groundAt(level, chunkBox, dx, dz);
            if (ground != OFF_CHUNK) {
                place(level, chunkBox,
                    new BlockPos(this.originX + dx, ground, this.originZ + dz), state);
            }
        }
    }

    private void placeMound(WorldGenLevel level, BoundingBox chunkBox, RandomSource random) {
        int bumps = 5 + random.nextInt(4);
        for (int i = 0; i < bumps; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            int radius = random.nextInt(3);
            int dx = (int) Math.round(Math.cos(angle) * radius);
            int dz = (int) Math.round(Math.sin(angle) * radius);
            BlockState state = random.nextBoolean()
                ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                : Blocks.COARSE_DIRT.defaultBlockState();
            if (insideArchway(dx, dz)) {
                continue;
            }
            int ground = groundAt(level, chunkBox, dx, dz);
            if (ground != OFF_CHUNK) {
                place(level, chunkBox,
                    new BlockPos(this.originX + dx, ground, this.originZ + dz), state);
            }
        }
    }

    private void placeVine(WorldGenLevel level, BoundingBox chunkBox, int stumpDx, int stumpDz,
                           int topY, double outwardAngle) {
        Direction outward = cardinal(outwardAngle);
        BlockState vine = Blocks.VINE.defaultBlockState()
            .setValue(VineBlock.getPropertyForFace(outward.getOpposite()), true);
        place(level, chunkBox, new BlockPos(
            this.originX + stumpDx + outward.getStepX(), topY,
            this.originZ + stumpDz + outward.getStepZ()), vine);
    }

    private int groundAt(WorldGenLevel level, BoundingBox chunkBox, int dx, int dz) {
        int x = this.originX + dx;
        int z = this.originZ + dz;
        if (x < chunkBox.minX() || x > chunkBox.maxX() || z < chunkBox.minZ() || z > chunkBox.maxZ()) {
            return OFF_CHUNK;
        }
        return level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
    }

    private static void place(WorldGenLevel level, BoundingBox chunkBox, BlockPos pos, BlockState state) {
        if (chunkBox.isInside(pos)) {
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
    }

    private static Direction cardinal(double angle) {
        double deg = Math.toDegrees(angle);
        if (deg < 0) {
            deg += 360;
        }
        if (deg >= 315 || deg < 45) {
            return Direction.EAST;
        } else if (deg < 135) {
            return Direction.SOUTH;
        } else if (deg < 225) {
            return Direction.WEST;
        } else {
            return Direction.NORTH;
        }
    }

    private static BlockState palette(RandomSource random) {
        return PALETTE[random.nextInt(PALETTE.length)];
    }
}
