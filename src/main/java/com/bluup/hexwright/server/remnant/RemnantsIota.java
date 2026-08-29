package com.bluup.hexwright.server.remnant;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.common.remnant.Remnant;
import com.bluup.hexwright.common.remnant.RemnantSnapshot;
import com.bluup.hexwright.common.remnant.RemnantType;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RemnantsIota extends Iota {

    private static final int DISPLAY_LIMIT = 5;

    public RemnantsIota(RemnantSnapshot snapshot) {
        super(TYPE, snapshot);
    }

    public RemnantSnapshot getSnapshot() {
        return (RemnantSnapshot) this.payload;
    }

    @Override
    public boolean isTruthy() {
        return !getSnapshot().isEmpty();
    }

    @Override
    public boolean toleratesOther(Iota that) {
        if (!typesMatch(this, that) || !(that instanceof RemnantsIota other)) {
            return false;
        }
        return this.getSnapshot().id().equals(other.getSnapshot().id());
    }

    @Override
    public @NotNull Tag serialize() {
        CompoundTag tag = new CompoundTag();
        ListTag entries = new ListTag();
        for (Remnant remnant : getSnapshot().remnants()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Type", remnant.type().name());
            entry.putDouble("Drams", remnant.drams());
            entries.add(entry);
        }
        tag.put("Remnants", entries);
        tag.putLong("CapturedAt", getSnapshot().capturedAt());
        tag.putUUID("Id", getSnapshot().id());
        return tag;
    }

    public static final IotaType<RemnantsIota> TYPE = new IotaType<>() {
        @Override
        public @Nullable RemnantsIota deserialize(Tag tag, ServerLevel world) throws IllegalArgumentException {
            if (!(tag instanceof CompoundTag compound)) {
                throw new IllegalArgumentException("Expected a compound tag for a remnants iota");
            }
            List<Remnant> remnants = new ArrayList<>();
            ListTag entries = compound.getList("Remnants", Tag.TAG_COMPOUND);
            for (int i = 0; i < entries.size(); i++) {
                CompoundTag entry = entries.getCompound(i);
                RemnantType type = RemnantType.byName(entry.getString("Type"));
                if (type != null) {
                    remnants.add(new Remnant(type, entry.getDouble("Drams")));
                }
            }
            long capturedAt = compound.getLong("CapturedAt");
            UUID id = compound.hasUUID("Id")
                ? compound.getUUID("Id")
                : RemnantSnapshot.legacyId(remnants, capturedAt);
            return new RemnantsIota(new RemnantSnapshot(remnants, capturedAt, id));
        }

        @Override
        public Component display(Tag tag) {
            if (!(tag instanceof CompoundTag compound)) {
                return Component.translatable("hexwright.iota.remnants");
            }
            ListTag entries = compound.getList("Remnants", Tag.TAG_COMPOUND);
            if (entries.isEmpty()) {
                return Component.translatable("hexwright.iota.remnants.empty");
            }

            MutableComponent listed = Component.empty();
            int shown = 0;
            for (int i = 0; i < entries.size() && shown < DISPLAY_LIMIT; i++) {
                CompoundTag entry = entries.getCompound(i);
                RemnantType type = RemnantType.byName(entry.getString("Type"));
                if (type == null) {
                    continue;
                }
                if (shown > 0) {
                    listed.append(Component.literal(", "));
                }
                listed.append(Component.translatable("hexwright.iota.remnants.entry",
                    type.label(), (int) Math.floor(entry.getDouble("Drams"))));
                shown++;
            }

            int remaining = entries.size() - shown;
            if (remaining > 0) {
                listed.append(Component.literal(", "))
                    .append(Component.translatable("hexwright.iota.remnants.more", remaining));
            }
            return Component.translatable("hexwright.iota.remnants.display", listed);
        }

        @Override
        public int color() {
            return 0xFF_8E44AD;
        }
    };

    public static void register() {
        Registry.register(IXplatAbstractions.INSTANCE.getIotaTypeRegistry(), Hexwright.id("remnants"), TYPE);
    }
}
