package com.bluup.hexwright.server.region;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;

public class RegionIota extends Iota {

    public RegionIota(Region region) {
        super(TYPE, region);
    }

    public Region getRegion() {
        return (Region) this.payload;
    }

    @Override
    public boolean isTruthy() {
        return !getRegion().isEmpty();
    }

    @Override
    public boolean toleratesOther(Iota that) {
        return typesMatch(this, that)
            && that instanceof RegionIota other
            && this.getRegion().equals(other.getRegion());
    }

    @Override
    public @NotNull Tag serialize() {
        return getRegion().save();
    }

    public static final IotaType<RegionIota> TYPE = new IotaType<>() {
        @Override
        public RegionIota deserialize(Tag tag, ServerLevel world) throws IllegalArgumentException {
            if (!(tag instanceof CompoundTag compound)) {
                throw new IllegalArgumentException("Expected a compound tag for a region iota");
            }
            return new RegionIota(Region.load(compound));
        }

        @Override
        public Component display(Tag tag) {
            if (tag instanceof CompoundTag compound) {
                try {
                    return Component.translatable("hexwright.iota.region.display",
                        summarise(Region.load(compound).describe()));
                } catch (IllegalArgumentException malformed) {
                }
            }
            return Component.translatable("hexwright.iota.region");
        }

        @Override
        public int color() {
            return 0xFF_E8B14C;
        }
    };

    private static String summarise(String description) {
        return description.length() <= 40 ? description : description.substring(0, 39) + "…";
    }

    public static void register() {
        Registry.register(IXplatAbstractions.INSTANCE.getIotaTypeRegistry(), Hexwright.id("region"), TYPE);
    }
}
