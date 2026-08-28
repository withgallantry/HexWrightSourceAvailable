package com.bluup.hexwright.server.weapon;

import at.petrak.hexcasting.api.item.MediaHolderItem;
import at.petrak.hexcasting.api.misc.MediaConstants;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class SoulHarvest {

    private static final long MEDIA_PER_HEALTH = MediaConstants.DUST_UNIT / 40;

    private SoulHarvest() {
    }

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
            if (!(source.getEntity() instanceof ServerPlayer killer)) {
                return;
            }
            if (!(killer.getMainHandItem().getItem() instanceof EternalScytheItem)) {
                return;
            }
            harvest(killer, victim);
        });
    }

    private static void harvest(ServerPlayer killer, LivingEntity victim) {
        long worth = (long) (victim.getMaxHealth() * MEDIA_PER_HEALTH);
        if (worth <= 0L) {
            return;
        }

        long banked = 0L;
        for (int slot = 0; slot < killer.getInventory().getContainerSize() && banked < worth; slot++) {
            ItemStack stack = killer.getInventory().getItem(slot);
            if (!(stack.getItem() instanceof MediaHolderItem holder) || !holder.canRecharge(stack)) {
                continue;
            }
            banked += holder.insertMedia(stack, worth - banked, false);
        }

        if (banked > 0L) {
            announce(killer, victim);
        }
    }

    private static void announce(ServerPlayer killer, LivingEntity victim) {
        ServerLevel level = killer.serverLevel();
        level.sendParticles(ParticleTypes.SOUL, victim.getX(), victim.getY(0.5), victim.getZ(),
            10, 0.25, 0.35, 0.25, 0.02);
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
            SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 0.7F, 1.3F);
    }
}
