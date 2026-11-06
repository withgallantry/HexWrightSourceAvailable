package com.bluup.hexwright.common.malediction;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

public record MaledictionSigil(Kind kind, int colour, Vec3 from, Vec3 to, float size, int entityId) {

    public enum Kind {
        IMPACT,
        BURST,
        LANCE,
        LIGHTNING,
        BIND,
        DOOM,
        TOLL,
        LIFTED
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeEnum(kind);
        buf.writeInt(colour);
        buf.writeDouble(from.x);
        buf.writeDouble(from.y);
        buf.writeDouble(from.z);
        buf.writeDouble(to.x);
        buf.writeDouble(to.y);
        buf.writeDouble(to.z);
        buf.writeFloat(size);
        buf.writeVarInt(entityId);
    }

    public static MaledictionSigil read(FriendlyByteBuf buf) {
        Kind kind = buf.readEnum(Kind.class);
        int colour = buf.readInt();
        Vec3 from = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        Vec3 to = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        float size = buf.readFloat();
        int entityId = buf.readVarInt();
        return new MaledictionSigil(kind, colour, from, to, size, entityId);
    }
}
