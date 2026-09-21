package com.bluup.hexwright.server.reliquary;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import ram.talia.hexal.api.casting.iota.MoteIota;
import ram.talia.hexal.api.mediafieditems.MediafiedItemManager;

import java.util.UUID;

public final class HexalMoteStorage {

    private static final boolean PRESENT = FabricLoader.getInstance().isModLoaded("hexal");

    private HexalMoteStorage() {
    }

    public static void lendBoundStorage(ServerPlayer opener, CompoundTag userData) {
        if (!PRESENT) {
            return;
        }
        Bridge.lend(opener, userData);
    }

    private static final class Bridge {
        private static void lend(ServerPlayer opener, CompoundTag userData) {
            UUID storage = MediafiedItemManager.INSTANCE.getBoundStorage(opener);
            if (storage != null) {
                userData.putUUID(MoteIota.TAG_TEMP_STORAGE, storage);
            }
        }
    }
}
