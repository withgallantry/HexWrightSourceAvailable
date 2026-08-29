package com.bluup.hexwright.server.remnant;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.common.remnant.Remnant;
import com.bluup.hexwright.common.remnant.RemnantType;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RemnantIota extends Iota {

    public RemnantIota(Remnant remnant) {
        super(TYPE, remnant);
    }

    public Remnant getRemnant() {
        return (Remnant) this.payload;
    }

    @Override
    public boolean isTruthy() {
        return !getRemnant().isEmpty();
    }

    @Override
    public boolean toleratesOther(Iota that) {
        if (!typesMatch(this, that) || !(that instanceof RemnantIota other)) {
            return false;
        }
        return this.getRemnant().type() == other.getRemnant().type()
            && Math.abs(this.getRemnant().drams() - other.getRemnant().drams()) < 0.001;
    }

    @Override
    public @NotNull Tag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", getRemnant().type().name());
        tag.putDouble("Drams", getRemnant().drams());
        return tag;
    }

    public static final IotaType<RemnantIota> TYPE = new IotaType<>() {
        @Override
        public @Nullable RemnantIota deserialize(Tag tag, ServerLevel world) throws IllegalArgumentException {
            if (!(tag instanceof CompoundTag compound)) {
                throw new IllegalArgumentException("Expected a compound tag for a remnant iota");
            }
            RemnantType type = RemnantType.byName(compound.getString("Type"));
            if (type == null) {
                return null;
            }
            return new RemnantIota(new Remnant(type, compound.getDouble("Drams")));
        }

        @Override
        public Component display(Tag tag) {
            if (tag instanceof CompoundTag compound) {
                RemnantType type = RemnantType.byName(compound.getString("Type"));
                if (type != null) {
                    return Component.translatable("hexwright.iota.remnant.display",
                        type.label(), (int) Math.floor(compound.getDouble("Drams")));
                }
            }
            return Component.translatable("hexwright.iota.remnant");
        }

        @Override
        public int color() {
            return 0xFF_C0392B;
        }
    };

    public static void register() {
        Registry.register(IXplatAbstractions.INSTANCE.getIotaTypeRegistry(), Hexwright.id("remnant"), TYPE);
    }
}
