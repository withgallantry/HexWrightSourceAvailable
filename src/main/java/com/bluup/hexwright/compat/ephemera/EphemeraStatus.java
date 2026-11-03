package com.bluup.hexwright.compat.ephemera;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.common.lib.hex.HexIotaTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import org.jetbrains.annotations.Nullable;

public final class EphemeraStatus {

    private static final ResourceLocation STATUS_IOTA = new ResourceLocation("ephemera", "potion");
    private static final String EFFECT_KEY = "potion_key";

    private EphemeraStatus() {
    }

    @Nullable
    public static MobEffect statusOf(Iota iota) {
        if (STATUS_IOTA.equals(HexIotaTypes.REGISTRY.getKey(iota.getType()))
            && iota.serialize() instanceof CompoundTag tag) {
            return BuiltInRegistries.MOB_EFFECT.get(ResourceLocation.tryParse(tag.getString(EFFECT_KEY)));
        }
        return null;
    }
}
