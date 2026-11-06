package com.bluup.hexwright.server.hexahedron;

import at.petrak.hexcasting.api.misc.MediaConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;

public record RiftAnchor(long media, long fellAt, int groundY, boolean landed) {

    public static final int COUNT = 4;
    public static final long MEDIA_NEEDED = 100L * 64L * MediaConstants.CRYSTAL_UNIT;
    public static final double HANG = 168.0;
    public static final float HALF_LENGTH = 15.0f;
    public static final float RADIUS = 4.2f;
    public static final int TEAR_TICKS = 30;
    public static final int FALL_TICKS = 56;
    public static final int DISSOLVE_TICKS = 70;
    public static final int LURCH_TICKS = 160;
    public static final long NEVER = Long.MIN_VALUE;
    public static final int UNKNOWN = Integer.MIN_VALUE;

    public static RiftAnchor fresh() {
        return new RiftAnchor(0L, NEVER, UNKNOWN, false);
    }

    public boolean fallen() {
        return fellAt != NEVER;
    }

    public float fill() {
        return (float) Math.min(1.0, media / (double) MEDIA_NEEDED);
    }

    public long impactAt() {
        return fellAt + TEAR_TICKS + FALL_TICKS;
    }

    public float lurch(double gameTime) {
        if (!fallen()) {
            return 0.0f;
        }
        float t = (float) Mth.clamp((gameTime - impactAt()) / LURCH_TICKS, 0.0, 1.0);
        return t * t * (3.0f - 2.0f * t);
    }

    public RiftAnchor fed(long added) {
        return new RiftAnchor(media + added, fellAt, groundY, landed);
    }

    public RiftAnchor onGround(int y) {
        return new RiftAnchor(media, fellAt, y, landed);
    }

    public RiftAnchor tearing(long now, int y) {
        return new RiftAnchor(media, now, y, false);
    }

    public RiftAnchor landing() {
        return new RiftAnchor(media, fellAt, groundY, true);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarLong(media);
        buf.writeLong(fellAt);
        buf.writeInt(groundY);
        buf.writeBoolean(landed);
    }

    public static RiftAnchor read(FriendlyByteBuf buf) {
        return new RiftAnchor(buf.readVarLong(), buf.readLong(), buf.readInt(), buf.readBoolean());
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Media", media);
        if (fallen()) {
            tag.putLong("FellAt", fellAt);
        }
        if (groundY != UNKNOWN) {
            tag.putInt("GroundY", groundY);
        }
        tag.putBoolean("Landed", landed);
        return tag;
    }

    public static RiftAnchor load(CompoundTag tag) {
        return new RiftAnchor(tag.getLong("Media"), tag.contains("FellAt") ? tag.getLong("FellAt") : NEVER,
            tag.contains("GroundY") ? tag.getInt("GroundY") : UNKNOWN, tag.getBoolean("Landed"));
    }
}
