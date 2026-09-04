package com.bluup.hexwright.server.staff_assembly;

import at.petrak.hexcasting.api.addldata.ADIotaHolder;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.item.IotaHolderItem;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.server.network.ResonantRingItem;
import net.minecraft.world.item.ItemStack;

public final class HeldIotaPrecedence {

    private HeldIotaPrecedence() {
    }

    public static boolean isCarrier(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof ResonantRingItem) {
            return false;
        }
        if (stack.getItem() instanceof IotaHolderItem) {
            return true;
        }
        return IXplatAbstractions.INSTANCE.findDataHolder(stack) != null;
    }

    public static boolean yieldsRead(CastingEnvironment env) {
        return env.getHeldItemToOperateOn(HeldIotaPrecedence::isCarrier) != null;
    }

    public static boolean yieldsWrite(CastingEnvironment env, Iota datum) {
        return env.getHeldItemToOperateOn(stack -> {
            if (!isCarrier(stack)) {
                return false;
            }
            ADIotaHolder holder = IXplatAbstractions.INSTANCE.findDataHolder(stack);
            return holder != null && holder.writeIota(datum, true);
        }) != null;
    }
}
