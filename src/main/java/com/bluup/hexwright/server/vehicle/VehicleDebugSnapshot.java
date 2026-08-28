package com.bluup.hexwright.server.vehicle;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

public record VehicleDebugSnapshot(
    Vec3 riderInput,
    Vec3 position,
    Vec3 velocity,
    Vec3 riderForward,
    Vec3 riderRight,
    Vec3 previousCommand,
    String memory,
    int memorySize,
    double maxHorizontalSpeed,
    double maxVerticalSpeed,
    double maxAcceleration,
    long internalMedia,
    long mediaCapacity,
    long lastMediaCost,
    boolean stalled,
    boolean hasHex,
    int ticksUntilNextEvaluation
) {

    public static final int MAX_MEMORY_LENGTH = 192;

    public static VehicleDebugSnapshot of(VehicleEntity vehicle) {
        FlightExecutionContext context = vehicle.debugContext();
        String memory = context.getPersistentMemory().display().getString();
        if (memory.length() > MAX_MEMORY_LENGTH) {
            memory = memory.substring(0, MAX_MEMORY_LENGTH) + "...";
        }
        return new VehicleDebugSnapshot(
            context.getRiderInput(),
            context.getVehiclePosition(),
            context.getVehicleVelocity(),
            context.getRiderForward(),
            context.getRiderRight(),
            context.getPreviousCommand(),
            memory,
            vehicle.getPersistentMemory().size(),
            context.getMaxHorizontalSpeed(),
            context.getMaxVerticalSpeed(),
            context.getMaxAcceleration(),
            context.getInternalMedia(),
            vehicle.getMediaCapacity(),
            vehicle.getLastMediaCost(),
            vehicle.isStalled(),
            vehicle.getStoredHex() != null,
            vehicle.getTicksUntilNextEvaluation()
        );
    }

    public void write(FriendlyByteBuf buf) {
        writeVec(buf, riderInput);
        writeVec(buf, position);
        writeVec(buf, velocity);
        writeVec(buf, riderForward);
        writeVec(buf, riderRight);
        writeVec(buf, previousCommand);
        buf.writeUtf(memory, MAX_MEMORY_LENGTH + 8);
        buf.writeVarInt(memorySize);
        buf.writeDouble(maxHorizontalSpeed);
        buf.writeDouble(maxVerticalSpeed);
        buf.writeDouble(maxAcceleration);
        buf.writeVarLong(internalMedia);
        buf.writeVarLong(mediaCapacity);
        buf.writeVarLong(lastMediaCost);
        buf.writeBoolean(stalled);
        buf.writeBoolean(hasHex);
        buf.writeVarInt(ticksUntilNextEvaluation);
    }

    public static VehicleDebugSnapshot read(FriendlyByteBuf buf) {
        return new VehicleDebugSnapshot(
            readVec(buf), readVec(buf), readVec(buf), readVec(buf), readVec(buf), readVec(buf),
            buf.readUtf(MAX_MEMORY_LENGTH + 8),
            buf.readVarInt(),
            buf.readDouble(), buf.readDouble(), buf.readDouble(),
            buf.readVarLong(), buf.readVarLong(), buf.readVarLong(),
            buf.readBoolean(), buf.readBoolean(), buf.readVarInt()
        );
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
