package com.bluup.hexwright.server.bindstone;

import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.inspiration.Inspiration;
import com.bluup.hexwright.server.sound.HexwrightSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class BindstoneDissolve {

    public static final int STAGES = BindstonePillar.RINGS + 3;

    public static final int STAGE_TICKS = 7;

    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private BindstoneDissolve() {
    }

    public static void advance(ServerLevel level, BlockPos origin, int stage) {
        if (stage == 0) {
            HexwrightNetworking.sendBindstoneDissolve(level, origin, (STAGES - 1) * STAGE_TICKS);
            strip(level, origin);
            level.playSound(null, origin, HexwrightSoundEvents.bindstoneClaim(), SoundSource.BLOCKS, 1.0F, 1.0F);
            BindstonePillar.sendNote(level, origin, BindstonePillar.NOTE_PITCH_MAX);
            return;
        }
        if (stage == 1) {
            return;
        }
        if (stage > BindstonePillar.RINGS + 1) {
            leaveChest(level, origin);
            return;
        }

        int band = BindstonePillar.RINGS + 1 - stage;

        if (band % 2 == 1) {
            float pitch = Mth.lerp(band / (float) (BindstonePillar.RINGS - 1),
                BindstonePillar.NOTE_PITCH_MIN, BindstonePillar.NOTE_PITCH_MAX);
            BindstonePillar.sendNote(level, origin, pitch);
        }

        if (band >= BindstonePillar.RINGS - Inspiration.CLAIM_ORBS) {
            Vec3 at = Vec3.atCenterOf(origin.above(band));
            Inspiration.dropClaimOrb(level, at);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS,
                0.5F, 1.0F + band * 0.06F);
        }
    }

    private static void strip(ServerLevel level, BlockPos origin) {
        BlockState air = Blocks.AIR.defaultBlockState();
        BindstonePillar.forEachBlock(origin, (pos, built) -> {
            if (pos.equals(origin) || !BindstonePillar.isPillarBlock(level.getBlockState(pos))) {
                return;
            }
            level.setBlock(pos, air, FLAGS);
        });
    }

    private static void leaveChest(ServerLevel level, BlockPos origin) {
        level.setBlock(origin,
            Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, towardsClaimant(level, origin)),
            Block.UPDATE_ALL);
        BindstoneReward.stockSpoils(level, origin, level.getRandom());

        level.sendParticles(ParticleTypes.FLASH, origin.getX() + 0.5, origin.getY() + 1.0, origin.getZ() + 0.5,
            1, 0.0, 0.0, 0.0, 0.0);
        level.playSound(null, origin, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, 0.7F);
        level.playSound(null, origin, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.8F, 0.8F);
    }

    private static Direction towardsClaimant(ServerLevel level, BlockPos origin) {
        double x = origin.getX() + 0.5;
        double z = origin.getZ() + 0.5;
        Player nearby = level.getNearestPlayer(x, origin.getY() + 0.5, z, BindstonePillar.INFLUENCE_RADIUS, false);
        if (nearby == null) {
            return Direction.NORTH;
        }
        return Direction.getNearest(nearby.getX() - x, 0.0, nearby.getZ() - z);
    }
}
