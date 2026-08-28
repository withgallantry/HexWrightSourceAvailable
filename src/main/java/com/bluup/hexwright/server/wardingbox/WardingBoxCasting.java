package com.bluup.hexwright.server.wardingbox;

import com.bluup.hexwright.server.hexpatterns.StoredHex;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.iota.EntityIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.misc.MediaConstants;
import com.bluup.hexwright.server.block.WardingBoxBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

public final class WardingBoxCasting {

    private static final long TRIGGER_COST = MediaConstants.DUST_UNIT / 30;

    private WardingBoxCasting() {
    }

    public static boolean cast(ServerLevel level, WardingBoxBlockEntity box, Entity visitor) {
        CompoundTag spellTag = box.getSpellTag();
        if (spellTag == null) {
            return false;
        }

        Iota iota;
        try {
            iota = IotaType.deserialize(spellTag, level);
        } catch (RuntimeException e) {
            return false;
        }
        List<Iota> hex = StoredHex.decode(iota);
        if (hex == null || hex.isEmpty()) {
            return false;
        }

        if (box.getMedia() < TRIGGER_COST) {
            return false;
        }
        box.payMedia(TRIGGER_COST, false);

        try {
            box.clearDisplay();

            List<Iota> seededStack = List.of((Iota) new EntityIota(visitor));
            CastingImage seededImage = new CastingImage().copy(
                seededStack,
                0,
                List.of(),
                false,
                0L,
                new CompoundTag()
            );

            CastingVM vm = new CastingVM(seededImage, new WardingBoxCastEnv(level, box));
            vm.queueExecuteAndWrapIotas(new ArrayList<>(hex), level);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

}
