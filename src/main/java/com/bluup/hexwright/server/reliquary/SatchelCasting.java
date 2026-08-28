package com.bluup.hexwright.server.reliquary;

import com.bluup.hexwright.server.hexpatterns.StoredHex;
import at.petrak.hexcasting.api.addldata.ADIotaHolder;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class SatchelCasting {

    private static int castDepth = 0;

    private SatchelCasting() {
    }

    public static List<Iota> fire(ServerPlayer player, InteractionHand hand, ItemStack focus, List<Iota> seeds,
                                   Supplier<ItemStack> heldSlot) {
        if (castDepth > 0 || focus.isEmpty()) {
            return List.of();
        }
        ServerLevel level = player.serverLevel();
        ADIotaHolder holder = IXplatAbstractions.INSTANCE.findDataHolder(focus);
        Iota iota = holder == null ? null : holder.readIota(level);
        List<Iota> hex = StoredHex.decode(iota);
        if (hex == null || hex.isEmpty()) {
            return List.of();
        }

        castDepth++;
        try {
            CastingVM templateVm = IXplatAbstractions.INSTANCE.getStaffcastVM(player, hand);
            CastingImage seededImage = templateVm.getImage().copy(
                seeds,
                0,
                List.of(),
                false,
                0L,
                new CompoundTag()
            );
            CastingVM vm = new CastingVM(seededImage, new ChestCastEnv(player, hand, heldSlot));
            vm.queueExecuteAndWrapIotas(new ArrayList<>(hex), level);
            return new ArrayList<>(vm.getImage().getStack());
        } catch (RuntimeException e) {
            Hexwright.LOGGER.warn("Satchel/chest hook hex threw during {}'s cast (hand={}, focus={})",
                player.getGameProfile().getName(), hand, focus, e);
            return List.of();
        } finally {
            castDepth--;
        }
    }

}
