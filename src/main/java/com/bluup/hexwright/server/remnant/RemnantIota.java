package com.bluup.hexwright.server.remnant;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.common.remnant.Remnant;
import com.bluup.hexwright.common.remnant.RemnantDecay;
import com.bluup.hexwright.common.remnant.RemnantType;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class RemnantIota extends Iota {

    private final @Nullable UUID draught;

    public RemnantIota(Remnant remnant, @Nullable UUID draught) {
        super(TYPE, remnant.isEmpty() ? Remnant.EMPTY : remnant);
        this.draught = remnant.isEmpty() ? null : draught;
    }

    public static RemnantIota empty() {
        return new RemnantIota(Remnant.EMPTY, null);
    }

    public static RemnantIota mint(Remnant remnant, MinecraftServer server) {
        if (remnant.isEmpty()) {
            return empty();
        }
        return new RemnantIota(remnant,
            RemnantDrawState.get(server).issue(remnant, server.overworld().getGameTime()));
    }

    public Remnant getRemnant() {
        return (Remnant) this.payload;
    }

    public @Nullable UUID getDraught() {
        return draught;
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
        if (this.draught == null || other.draught == null) {
            return this.getRemnant().isEmpty() && other.getRemnant().isEmpty();
        }
        return this.draught.equals(other.draught);
    }

    @Override
    public @NotNull Tag serialize() {
        CompoundTag tag = new CompoundTag();
        Remnant remnant = getRemnant();
        if (remnant.isEmpty() || draught == null) {
            return tag;
        }
        tag.putString("Type", remnant.type().name());
        tag.putDouble("Drams", remnant.drams());
        tag.putUUID("Draught", draught);
        return tag;
    }

    public static final IotaType<RemnantIota> TYPE = new IotaType<>() {
        @Override
        public @Nullable RemnantIota deserialize(Tag tag, ServerLevel world) throws IllegalArgumentException {
            if (!(tag instanceof CompoundTag compound)) {
                throw new IllegalArgumentException("Expected a compound tag for a remnant iota");
            }
            if (!compound.hasUUID("Draught")) {
                return empty();
            }
            UUID draught = compound.getUUID("Draught");
            Remnant worth = RemnantDrawState.get(world.getServer())
                .peek(draught, world.getServer().overworld().getGameTime());
            return worth == null ? empty() : new RemnantIota(worth, draught);
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
            return Component.translatable("hexwright.iota.remnant.empty");
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
