package com.bluup.hexwright.server.cantillation;

import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.worn.WornItemState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class CantillationState extends WornItemState {

    private static final String STORAGE_ID = "hexwright_cantillations";

    public static CantillationState get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(CantillationState::load, CantillationState::new, STORAGE_ID);
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return CantillationItem.of(stack) != null;
    }

    @Override
    public void wear(ServerPlayer player, ItemStack stack) {
        super.wear(player, stack);
        sync(player);
    }

    public void sync(ServerPlayer player) {
        HexwrightNetworking.sendWornCantillation(player, CantillationItem.of(worn(player.getUUID())));
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        saveWorn(tag);
        return tag;
    }

    public static CantillationState load(CompoundTag tag) {
        CantillationState state = new CantillationState();
        state.loadWorn(tag);
        return state;
    }
}
