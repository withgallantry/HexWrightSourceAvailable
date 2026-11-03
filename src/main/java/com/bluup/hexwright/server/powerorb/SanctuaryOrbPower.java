package com.bluup.hexwright.server.powerorb;

import at.petrak.hexcasting.api.misc.MediaConstants;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class SanctuaryOrbPower {

    public static final long CAST_COST = 15 * MediaConstants.DUST_UNIT;
    public static final int COOLDOWN_TICKS = 30 * 20;

    private static final double DROP = 16.0D;

    private SanctuaryOrbPower() {
    }

    public static void cast(ServerPlayer player, ItemStack orb) {
        ServerLevel level = player.serverLevel();
        Vec3 at = spot(level, player);
        SanctuaryHandsEntity hands = new SanctuaryHandsEntity(level, at, player);
        level.addFreshEntity(hands);
        level.playSound(null, at.x, at.y, at.z, PowerOrbSounds.SANCTUARY_SUMMON.get(), SoundSource.PLAYERS,
            1.0F, 0.9F + level.random.nextFloat() * 0.2F);
        PowerOrbData.setReadyAt(orb, level.getGameTime() + COOLDOWN_TICKS);
    }

    private static Vec3 spot(ServerLevel level, ServerPlayer player) {
        if (player.onGround()) {
            return player.position();
        }
        Vec3 from = player.position();
        BlockHitResult ground = level.clip(new ClipContext(from, from.subtract(0.0D, DROP, 0.0D),
            ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
        return ground.getType() == HitResult.Type.MISS ? from : ground.getLocation();
    }
}
