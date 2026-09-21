package com.bluup.hexwright.server.dust;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public record DustPacket(int entityId, byte op, float mass, float targetMass,
                         @Nullable CompoundTag region, boolean constrained,
                         Vec3 vector, int durationTicks) {

    public static final byte OP_RELEASE = 0;
    public static final byte OP_STATE = 1;
    public static final byte OP_MASS = 2;
    public static final byte OP_DIRECT = 3;

    public static DustPacket release(int entityId) {
        return new DustPacket(entityId, OP_RELEASE, 0.0f, 0.0f, null, false, Vec3.ZERO, 0);
    }

    public static DustPacket state(int entityId, float mass, float targetMass,
                                   @Nullable CompoundTag region, boolean constrained) {
        return new DustPacket(entityId, OP_STATE, mass, targetMass, region, constrained, Vec3.ZERO, 0);
    }

    public static DustPacket mass(int entityId, float mass, float targetMass) {
        return new DustPacket(entityId, OP_MASS, mass, targetMass, null, false, Vec3.ZERO, 0);
    }

    public static DustPacket direct(int entityId, Vec3 vector, int durationTicks) {
        return new DustPacket(entityId, OP_DIRECT, 0.0f, 0.0f, null, false, vector, durationTicks);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeByte(op);
        switch (op) {
            case OP_STATE -> {
                buf.writeFloat(mass);
                buf.writeFloat(targetMass);
                buf.writeBoolean(constrained);
                buf.writeBoolean(region != null);
                if (region != null) {
                    buf.writeNbt(region);
                }
            }
            case OP_MASS -> {
                buf.writeFloat(mass);
                buf.writeFloat(targetMass);
            }
            case OP_DIRECT -> {
                buf.writeDouble(vector.x);
                buf.writeDouble(vector.y);
                buf.writeDouble(vector.z);
                buf.writeVarInt(durationTicks);
            }
            default -> {
            }
        }
    }

    public static DustPacket read(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        byte op = buf.readByte();
        return switch (op) {
            case OP_STATE -> {
                float mass = buf.readFloat();
                float target = buf.readFloat();
                boolean constrained = buf.readBoolean();
                CompoundTag region = buf.readBoolean() ? buf.readNbt() : null;
                yield state(entityId, mass, target, region, constrained);
            }
            case OP_MASS -> mass(entityId, buf.readFloat(), buf.readFloat());
            case OP_DIRECT -> direct(entityId, new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readVarInt());
            default -> release(entityId);
        };
    }
}
