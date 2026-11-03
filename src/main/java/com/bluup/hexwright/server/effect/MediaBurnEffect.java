package com.bluup.hexwright.server.effect;

import at.petrak.hexcasting.api.addldata.ADMediaHolder;
import at.petrak.hexcasting.api.utils.MediaHelper;
import com.bluup.hexwright.HexwrightDebug;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;

public final class MediaBurnEffect extends MobEffect {

    public static final int BURN_PERCENT = 4;

    private static final DustParticleOptions MOTE = new DustParticleOptions(new Vector3f(0.69F, 0.42F, 1.0F), 1.0F);

    MediaBurnEffect() {
        super(MobEffectCategory.HARMFUL, 0xB46CFF);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (!(entity instanceof ServerPlayer player) || !player.isOnFire()) {
            return;
        }
        long burnt = 0L;
        for (ADMediaHolder holder : MediaHelper.scanPlayerForMediaStuff(player)) {
            long share = holder.canRecharge() ? holder.getMedia() * BURN_PERCENT / 100L : 0L;
            if (share > 0L) {
                burnt += holder.withdrawMedia(share, false);
            }
        }
        if (burnt <= 0L) {
            return;
        }
        player.serverLevel().sendParticles(MOTE, player.getX(), player.getY() + player.getBbHeight() * 0.5D,
            player.getZ(), 8, 0.35D, 0.5D, 0.35D, 0.0D);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.4F, 1.6F);
        HexwrightDebug.log(HexwrightDebug.WEAPON, "[weapon] media burn on {}: {} media",
            player.getScoreboardName(), burnt);
    }
}
