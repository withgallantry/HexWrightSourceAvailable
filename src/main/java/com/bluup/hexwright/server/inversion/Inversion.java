package com.bluup.hexwright.server.inversion;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record Inversion(UUID id, int x, int z, int groundY, Phase phase, long since, @Nullable UUID source,
                        long heldAt) {

    public static final int SIZE = 400;
    public static final int HALF = SIZE / 2;
    public static final double RENT_ABOVE_GROUND = 180.0;
    public static final double REACH = HALF * Math.sqrt(2.0) + 1.0;
    public static final int FRACTURE_TICKS = 20 * 90;
    public static final int RECEDE_TICKS = 20 * 60;

    public enum Phase {
        FRACTURING,
        HELD,
        RECEDING
    }

    public int minX() {
        return x - HALF;
    }

    public int minZ() {
        return z - HALF;
    }

    public boolean covers(int blockX, int blockZ) {
        return blockX >= x - HALF && blockX < x + HALF && blockZ >= z - HALF && blockZ < z + HALF;
    }

    public boolean covers(double worldX, double worldZ) {
        return covers(Mth.floor(worldX), Mth.floor(worldZ));
    }

    public boolean overlaps(int otherX, int otherZ) {
        return Math.abs(otherX - x) < SIZE && Math.abs(otherZ - z) < SIZE;
    }

    public double rentY(int maxBuildHeight) {
        return Math.min(groundY + RENT_ABOVE_GROUND, maxBuildHeight + 8.0);
    }

    public float rentAngle() {
        return (id.getLeastSignificantBits() >>> 40) / (float) (1L << 24) * Mth.TWO_PI;
    }

    public double distanceTo(int blockX, int blockZ) {
        return Math.hypot(blockX + 0.5 - x, blockZ + 0.5 - z);
    }

    public double front(double gameTime) {
        double t = Mth.clamp((gameTime - since) / (phase == Phase.RECEDING ? RECEDE_TICKS : FRACTURE_TICKS), 0.0, 1.0);
        return switch (phase) {
            case FRACTURING -> REACH * Math.sqrt(t);
            case HELD -> REACH;
            case RECEDING -> REACH * Math.sqrt(1.0 - t);
        };
    }

    public Inversion inPhase(Phase next, long at) {
        return new Inversion(id, x, z, groundY, next, at, source, heldAt);
    }

    public Inversion held(long at, long millis) {
        return new Inversion(id, x, z, groundY, Phase.HELD, at, source, millis);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeInt(x);
        buf.writeInt(z);
        buf.writeInt(groundY);
        buf.writeEnum(phase);
        buf.writeLong(since);
    }

    public static Inversion read(FriendlyByteBuf buf) {
        return new Inversion(buf.readUUID(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readEnum(Phase.class),
            buf.readLong(), null, -1L);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putInt("X", x);
        tag.putInt("Z", z);
        tag.putInt("Ground", groundY);
        tag.putString("Phase", phase.name());
        tag.putLong("Since", since);
        if (source != null) {
            tag.putUUID("Source", source);
        }
        tag.putLong("HeldAt", heldAt);
        return tag;
    }

    public static Inversion load(CompoundTag tag) {
        Phase phase;
        try {
            phase = Phase.valueOf(tag.getString("Phase"));
        } catch (IllegalArgumentException ex) {
            phase = Phase.RECEDING;
        }
        long heldAt = tag.contains("HeldAt") ? tag.getLong("HeldAt") : phase == Phase.HELD ? System.currentTimeMillis() : -1L;
        return new Inversion(tag.getUUID("Id"), tag.getInt("X"), tag.getInt("Z"), tag.getInt("Ground"), phase,
            tag.getLong("Since"), tag.hasUUID("Source") ? tag.getUUID("Source") : null, heldAt);
    }
}
