package com.bluup.hexwright.server.harmonic;

import com.bluup.hexwright.server.hexpatterns.StoredHex;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import com.bluup.hexwright.server.block.HarmonicEmitterBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.List;

public final class HarmonicEmitterCasting {

    private HarmonicEmitterCasting() {
    }

    public static boolean cast(ServerLevel level, HarmonicEmitterBlockEntity emitter, Iota payload) {
        CompoundTag hexTag = emitter.hexTag();
        if (hexTag == null) {
            return false;
        }

        List<Iota> hex;
        try {
            hex = StoredHex.decode(IotaType.deserialize(hexTag.copy(), level));
        } catch (RuntimeException malformed) {
            return false;
        }
        if (hex == null || hex.isEmpty()) {
            return false;
        }

        try {
            emitter.clearDisplay();
            emitter.recordSignal();

            CastingImage seededImage = new CastingImage().copy(
                List.of(payload),
                0,
                List.of(),
                false,
                0L,
                new CompoundTag()
            );

            CastingVM vm = new CastingVM(seededImage, new HarmonicEmitterCastEnv(level, emitter));
            vm.queueExecuteAndWrapIotas(new ArrayList<>(hex), level);

            level.playSound(null, emitter.getBlockPos(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.4f, 1.8f);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
