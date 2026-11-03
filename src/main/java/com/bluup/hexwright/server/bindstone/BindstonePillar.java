package com.bluup.hexwright.server.bindstone;

import com.bluup.hexwright.server.block.BindstoneBlock;
import com.bluup.hexwright.server.block.HexwrightBlocks;
import com.bluup.hexwright.server.sound.HexwrightSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.phys.Vec3;

import java.util.function.BiConsumer;

public final class BindstonePillar {

    public static final int RINGS = 6;

    public static final int RUNE_COUNT = 26;

    public static final double INFLUENCE_RADIUS = 32.0;

    public static final float NOTE_VOLUME = 0.7f;

    public static final int NOTE_TICKS = 50;

    public static int noteTicks(float pitch) {
        return Math.round(NOTE_TICKS / pitch);
    }

    public static final float GAP_MIN_FRACTION = 0.55f;
    public static final float GAP_MAX_FRACTION = 0.85f;

    public static int nextGap(RandomSource random, int noteTicks) {
        float fraction = GAP_MIN_FRACTION + random.nextFloat() * (GAP_MAX_FRACTION - GAP_MIN_FRACTION);
        return Math.max(1, Math.round(noteTicks * fraction));
    }

    public static final int REST_MAX_TICKS = 20;

    public static int nextRest(RandomSource random) {
        return random.nextInt(REST_MAX_TICKS + 1);
    }

    public static final float NOTE_PITCH_MIN = 0.70f;
    public static final float NOTE_PITCH_MAX = 1.00f;

    public static float nextPitch(RandomSource random) {
        return NOTE_PITCH_MIN + random.nextFloat() * (NOTE_PITCH_MAX - NOTE_PITCH_MIN);
    }

    public static void sendNote(Level level, BlockPos origin, float pitch) {
        Holder<SoundEvent> note = BuiltInRegistries.SOUND_EVENT
            .wrapAsHolder(HexwrightSoundEvents.bindstoneNote());
        Vec3 source = Vec3.atCenterOf(origin);
        ClientboundSoundPacket packet = new ClientboundSoundPacket(
            note,
            SoundSource.BLOCKS,
            source.x,
            source.y,
            source.z,
            NOTE_VOLUME,
            pitch,
            level.getRandom().nextLong()
        );
        double reachSqr = INFLUENCE_RADIUS * INFLUENCE_RADIUS;
        for (Player player : level.players()) {
            if (player instanceof ServerPlayer listener && listener.distanceToSqr(source) <= reachSqr) {
                listener.connection.send(packet);
            }
        }
    }


    private BindstonePillar() {
    }

    public static Vec3 wardCentre(BlockPos origin) {
        return new Vec3(origin.getX() + 0.5, origin.getY() + RINGS / 2.0, origin.getZ() + 0.5);
    }

    public static boolean occupies(BlockPos origin, BlockPos pos) {
        int dy = pos.getY() - origin.getY();
        return Math.abs(pos.getX() - origin.getX()) <= 2 && Math.abs(pos.getZ() - origin.getZ()) <= 2
            && dy >= 0 && dy <= RINGS;
    }

    public static boolean isPillarBlock(BlockState state) {
        return state.is(HexwrightBlocks.BINDSTONE_BLOCK) || state.is(HexwrightBlocks.BINDSTONE_COLUMN_BLOCK)
            || state.is(HexwrightBlocks.BINDSTONE_STAIRS_BLOCK) || state.is(HexwrightBlocks.BINDSTONE_SLAB_BLOCK);
    }

    public static BlockPos runeFace(BlockPos origin, int ring, Direction outward) {
        return origin.offset(outward.getStepX(), ring, outward.getStepZ());
    }

    public static BlockPos originOf(BlockPos runeBlock, Direction outward, int ring) {
        return runeBlock.offset(-outward.getStepX(), -ring, -outward.getStepZ());
    }

    public static void forEachBlock(BlockPos origin, BiConsumer<BlockPos, BlockState> out) {
        for (int ring = 0; ring < RINGS; ring++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = origin.offset(dx, ring, dz);
                    if (dx != 0 && dz != 0) {
                        out.accept(pos, HexwrightBlocks.BINDSTONE_COLUMN_BLOCK.defaultBlockState());
                        continue;
                    }
                    Direction outward = dx == 0 && dz == 0
                        ? Direction.NORTH
                        : Direction.getNearest(dx, 0, dz);
                    boolean core = dx == 0 && dz == 0 && ring == 0;
                    out.accept(pos, HexwrightBlocks.BINDSTONE_BLOCK.defaultBlockState()
                        .setValue(BindstoneBlock.FACING, outward)
                        .setValue(BindstoneBlock.CORE, core));
                }
            }
        }

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                out.accept(origin.offset(dx, RINGS, dz), HexwrightBlocks.BINDSTONE_SLAB_BLOCK.defaultBlockState());
            }
        }

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) != 2 && Math.abs(dz) != 2) {
                    continue;
                }
                if (isSkirtGap(dx, dz)) {
                    continue;
                }
                out.accept(origin.offset(dx, 0, dz),
                    HexwrightBlocks.BINDSTONE_STAIRS_BLOCK.defaultBlockState()
                        .setValue(StairBlock.FACING, skirtFacing(dx, dz))
                        .setValue(StairBlock.HALF, Half.BOTTOM)
                        .setValue(StairBlock.SHAPE, StairsShape.STRAIGHT));
            }
        }
    }

    public static void build(LevelAccessor level, BlockPos origin) {
        forEachBlock(origin, (pos, state) -> level.setBlock(pos, state, Block.UPDATE_CLIENTS));

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) != 2 && Math.abs(dz) != 2) {
                    continue;
                }
                BlockPos pos = origin.offset(dx, 0, dz);
                BlockState state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof StairBlock)) {
                    continue;
                }
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    BlockPos neighbour = pos.relative(side);
                    state = state.updateShape(side, level.getBlockState(neighbour), level, pos, neighbour);
                }
                level.setBlock(pos, state, Block.UPDATE_CLIENTS);
            }
        }
    }

    private static boolean isSkirtGap(int dx, int dz) {
        return (dx == 0 && Math.abs(dz) == 2) || (dz == 0 && Math.abs(dx) == 2);
    }

    private static Direction skirtFacing(int dx, int dz) {
        if (Math.abs(dx) == 2) {
            return dx > 0 ? Direction.WEST : Direction.EAST;
        }
        return dz > 0 ? Direction.NORTH : Direction.SOUTH;
    }

    public static int[] rollRunes(RandomSource random) {
        int[] runes = new int[RINGS];
        for (int ring = 0; ring < RINGS; ring++) {
            runes[ring] = 1 + random.nextInt(RUNE_COUNT);
        }
        return runes;
    }

    public static void setRing(LevelAccessor level, BlockPos origin, int ring, int rune) {
        for (Direction outward : Direction.Plane.HORIZONTAL) {
            BlockPos pos = runeFace(origin, ring, outward);
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof BindstoneBlock)) {
                continue;
            }
            if (state.getValue(BindstoneBlock.RUNE) == rune) {
                continue;
            }
            level.setBlock(
                pos,
                state.setValue(BindstoneBlock.RUNE, rune).setValue(BindstoneBlock.FACING, outward),
                Block.UPDATE_ALL
            );
        }
    }

    public static void clearAllRings(LevelAccessor level, BlockPos origin) {
        for (int ring = 0; ring < RINGS; ring++) {
            setRing(level, origin, ring, 0);
        }
    }

    public static void setAllRings(LevelAccessor level, BlockPos origin, int[] runes) {
        for (int ring = 0; ring < RINGS && ring < runes.length; ring++) {
            setRing(level, origin, ring, runes[ring]);
        }
    }

}
