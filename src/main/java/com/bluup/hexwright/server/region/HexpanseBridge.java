package com.bluup.hexwright.server.region;

import at.petrak.hexcasting.api.casting.eval.vm.ContinuationFrame;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.common.lib.hex.HexContinuationTypes;
import at.petrak.hexcasting.common.lib.hex.HexIotaTypes;
import com.bluup.hexwright.Hexwright;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class HexpanseBridge {

    public static final String MOD_ID = "hexpanse";

    private static final ResourceLocation REGION_IOTA = new ResourceLocation(MOD_ID, "region");
    private static final ResourceLocation REGION_FRAME = new ResourceLocation(MOD_ID, "region_foreach");

    private HexpanseBridge() {
    }

    public static boolean isLoaded() {
        return FabricLoader.getInstance().isModLoaded(MOD_ID);
    }

    public static void registerLegacyTypes() {
        Registry.register(HexIotaTypes.REGISTRY, Hexwright.id("region"), LEGACY_REGION_IOTA);
        Registry.register(HexContinuationTypes.REGISTRY, Hexwright.id("region_foreach"), LEGACY_REGION_FRAME);
    }

    @Nullable
    public static Region regionOf(Iota iota) {
        if (iota instanceof RegionIota region) {
            return region.getRegion();
        }
        if (REGION_IOTA.equals(HexIotaTypes.REGISTRY.getKey(iota.getType()))
            && iota.serialize() instanceof CompoundTag tag) {
            try {
                return Region.load(tag);
            } catch (IllegalArgumentException malformed) {
                Hexwright.LOGGER.warn("A Hexpanse region did not load as a Hexwright one", malformed);
            }
        }
        return null;
    }

    private static final IotaType<Iota> LEGACY_REGION_IOTA = new IotaType<>() {
        @Override
        public @Nullable Iota deserialize(Tag tag, ServerLevel world) throws IllegalArgumentException {
            return hexpanseRegionType().deserialize(tag, world);
        }

        @Override
        public Component display(Tag tag) {
            return hexpanseRegionType().display(tag);
        }

        @Override
        public int color() {
            return hexpanseRegionType().color();
        }
    };

    private static final ContinuationFrame.Type<ContinuationFrame> LEGACY_REGION_FRAME = new ContinuationFrame.Type<>() {
        @Override
        public @Nullable ContinuationFrame deserializeFromNBT(@NotNull CompoundTag tag, @NotNull ServerLevel world) {
            return hexpanseFrameType().deserializeFromNBT(tag, world);
        }
    };

    private static IotaType<?> hexpanseRegionType() {
        return Objects.requireNonNull(HexIotaTypes.REGISTRY.get(REGION_IOTA), "Hexpanse registered no region iota");
    }

    private static ContinuationFrame.Type<?> hexpanseFrameType() {
        return Objects.requireNonNull(HexContinuationTypes.REGISTRY.get(REGION_FRAME),
            "Hexpanse registered no region frame");
    }
}
