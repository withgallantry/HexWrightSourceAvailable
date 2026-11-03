package com.bluup.hexwright.server.dust;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public record DustPacket(int entityId, byte op, float mass, float targetMass,
                         @Nullable CompoundTag region, int formationSerial, boolean constrained,
                         Vec3 drift, Vec3 vector, Vec3 driftTarget, @Nullable Vec3 destination,
                         int durationTicks) {

    public static final byte OP_RELEASE = 0;
    public static final byte OP_STATE = 1;
    public static final byte OP_MASS = 2;
    public static final byte OP_DIRECT = 3;

    public static DustPacket release(int entityId) {
        return new DustPacket(entityId, OP_RELEASE, 0.0f, 0.0f, null, 0, false,
            Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, null, 0);
    }

    public static DustPacket state(int entityId, float mass, float targetMass,
                                   @Nullable CompoundTag region, int formationSerial, boolean constrained,
                                   Vec3 drift, Vec3 driftVelocity, Vec3 driftTarget,
                                   @Nullable Vec3 destination) {
        return new DustPacket(entityId, OP_STATE, mass, targetMass, region, formationSerial, constrained,
            drift, driftVelocity, driftTarget, destination, 0);
    }

    public static DustPacket mass(int entityId, float mass, float targetMass) {
        return new DustPacket(entityId, OP_MASS, mass, targetMass, null, 0, false,
            Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, null, 0);
    }

    public static DustPacket direct(int entityId, Vec3 vector, int durationTicks) {
        return new DustPacket(entityId, OP_DIRECT, 0.0f, 0.0f, null, 0, false, Vec3.ZERO, vector, Vec3.ZERO, null,
            durationTicks);
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
                    buf.writeVarInt(formationSerial);
                    writeVec(buf, drift);
                    writeVec(buf, vector);
                    writeVec(buf, driftTarget);
                    buf.writeBoolean(destination != null);
                    if (destination != null) {
                        writeVec(buf, destination);
                    }
                }
            }
            case OP_MASS -> {
                buf.writeFloat(mass);
                buf.writeFloat(targetMass);
            }
            case OP_DIRECT -> {
                writeVec(buf, vector);
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
                int serial = region != null ? buf.readVarInt() : 0;
                Vec3 drift = region != null ? readVec(buf) : Vec3.ZERO;
                Vec3 driftVelocity = region != null ? readVec(buf) : Vec3.ZERO;
                Vec3 driftTarget = region != null ? readVec(buf) : Vec3.ZERO;
                Vec3 destination = region != null && buf.readBoolean() ? readVec(buf) : null;
                yield state(entityId, mass, target, region, serial, constrained, drift, driftVelocity,
                    driftTarget, destination);
            }
            case OP_MASS -> mass(entityId, buf.readFloat(), buf.readFloat());
            case OP_DIRECT -> direct(entityId, readVec(buf), buf.readVarInt());
            default -> release(entityId);
        };
    }

    private static void writeVec(FriendlyByteBuf buf, Vec3 vec) {
        buf.writeDouble(vec.x);
        buf.writeDouble(vec.y);
        buf.writeDouble(vec.z);
    }

    private static Vec3 readVec(FriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }
}
