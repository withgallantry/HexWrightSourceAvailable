package com.bluup.hexwright.server.flourish;

import com.bluup.hexwright.common.flourish.Flourish;
import com.bluup.hexwright.common.flourish.FlourishDesign;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public record FlourishCast(@Nullable Flourish flourish, @Nullable FlourishDesign design, Vec3 pos, float extent,
                           Vec3 axis, int casterId, int seed, @Nullable CompoundTag pigment) {

    public FlourishCast {
        if ((flourish == null) == (design == null)) {
            throw new IllegalArgumentException("A flourish cast is either a found flourish or a design");
        }
    }

    public @Nullable Flourish chime() {
        return design != null ? design.chime() : flourish;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(design != null);
        if (design != null) {
            design.write(buf);
        } else {
            buf.writeEnum(flourish);
        }
        buf.writeDouble(pos.x);
        buf.writeDouble(pos.y);
        buf.writeDouble(pos.z);
        buf.writeFloat(extent);
        buf.writeFloat((float) axis.x);
        buf.writeFloat((float) axis.y);
        buf.writeFloat((float) axis.z);
        buf.writeVarInt(casterId);
        buf.writeInt(seed);
        buf.writeNbt(pigment);
    }

    public static FlourishCast read(FriendlyByteBuf buf) {
        FlourishDesign design = null;
        Flourish flourish = null;
        if (buf.readBoolean()) {
            design = FlourishDesign.read(buf);
        } else {
            flourish = buf.readEnum(Flourish.class);
        }
        Vec3 pos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        float extent = buf.readFloat();
        Vec3 axis = new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
        int casterId = buf.readVarInt();
        int seed = buf.readInt();
        CompoundTag pigment = buf.readNbt();
        return new FlourishCast(flourish, design, pos, extent, axis, casterId, seed, pigment);
    }
}
