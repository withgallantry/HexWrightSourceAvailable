package com.bluup.hexwright.server.reliquary;

import at.petrak.hexcasting.api.casting.eval.MishapEnvironment;
import com.bluup.hexwright.server.pocketcaster.PocketCasterCastEnv;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class ChestCastEnv extends PocketCasterCastEnv {

    private final Supplier<ItemStack> heldSlot;

    public ChestCastEnv(ServerPlayer caster, InteractionHand castingHand, Supplier<ItemStack> heldSlot) {
        super(caster, castingHand);
        this.heldSlot = heldSlot;
    }

    public ItemStack heldSlotStack() {
        return heldSlot.get();
    }

    @Override
    public List<HeldItemInfo> getPrimaryStacks() {
        List<HeldItemInfo> base = super.getPrimaryStacks();
        List<HeldItemInfo> copies = new ArrayList<>(base.size());
        for (HeldItemInfo info : base) {
            copies.add(new HeldItemInfo(info.stack().copy(), null));
        }
        ItemStack held = heldSlot.get();
        if (held.isEmpty()) {
            return copies;
        }
        List<HeldItemInfo> withSlot = new ArrayList<>(copies.size() + 1);
        withSlot.add(new HeldItemInfo(held.copy(), null));
        withSlot.addAll(copies);
        return withSlot;
    }

    @Override
    public MishapEnvironment getMishapEnvironment() {
        return new ChestMishapEnv(this.world);
    }

    @Override
    protected boolean canOvercast() {
        return false;
    }
}
