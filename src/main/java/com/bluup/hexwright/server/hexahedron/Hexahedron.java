package com.bluup.hexwright.server.hexahedron;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public record Hexahedron(UUID id, double x, double z, double riftY, int radius, long startTick,
                         List<RiftAnchor> anchors, boolean opensInversion) {

    public static final float SIZE = 128.0f;
    public static final float HALF_SIZE = SIZE / 2.0f;
    public static final float HALF_DIAGONAL = HALF_SIZE * (float) Math.sqrt(3.0);
    public static final int EMERGE_TICKS = 1200;
    public static final int SPIN_TICKS = 12000;
    public static final int MIN_RADIUS = 64;
    public static final int MAX_RADIUS = 1024;
    public static final int DEFAULT_RADIUS = 256;
    public static final int RIFT_ABOVE_BUILD_LIMIT = 24;
    public static final int BURST_DELAY = 60;
    public static final int BURST_TICKS = 50;
    public static final int CLEAR_TICKS = 240;

    private static final Matrix3f CORNER_DOWN = cornerDown();

    public Hexahedron {
        anchors = List.copyOf(anchors);
    }

    public static Hexahedron opening(UUID id, double x, double z, double riftY, int radius, long startTick,
                                     boolean opensInversion) {
        return new Hexahedron(id, x, z, riftY, radius, startTick, Collections.nCopies(RiftAnchor.COUNT, RiftAnchor.fresh()),
            opensInversion);
    }

    private static Matrix3f cornerDown() {
        float a = (float) (1.0 / Math.sqrt(2.0));
        float b = (float) (1.0 / Math.sqrt(3.0));
        float c = (float) (1.0 / Math.sqrt(6.0));
        return new Matrix3f(a, b, -c, -a, b, -c, 0.0f, b, 2.0f * c);
    }

    public Vec3 anchor() {
        return new Vec3(x, riftY, z);
    }

    public boolean covers(Vec3 point) {
        double dx = point.x - x;
        double dz = point.z - z;
        return dx * dx + dz * dz <= (double) radius * radius;
    }

    public float emergence(double gameTime) {
        float t = (float) Mth.clamp((gameTime - startTick) / EMERGE_TICKS, 0.0, 1.0);
        return t * t * (3.0f - 2.0f * t);
    }

    public float through(double gameTime) {
        float let = 0.0f;
        for (RiftAnchor anchor : anchors) {
            let += anchor.lurch(gameTime);
        }
        return emergence(gameTime) + Math.min(let, RiftAnchor.COUNT - 1) / RiftAnchor.COUNT;
    }

    public long burstAt() {
        long last = Long.MIN_VALUE;
        for (RiftAnchor anchor : anchors) {
            if (!anchor.fallen()) {
                return RiftAnchor.NEVER;
            }
            last = Math.max(last, anchor.impactAt());
        }
        return last + BURST_DELAY;
    }

    public float burst(double gameTime) {
        long at = burstAt();
        return at == RiftAnchor.NEVER ? 0.0f : (float) Mth.clamp((gameTime - at) / BURST_TICKS, 0.0, 1.0);
    }

    public float cleared(double gameTime) {
        long at = burstAt();
        return at == RiftAnchor.NEVER ? 0.0f : (float) Mth.clamp((gameTime - at) / CLEAR_TICKS, 0.0, 1.0);
    }

    public boolean hasBurst(double gameTime) {
        long at = burstAt();
        return at != RiftAnchor.NEVER && gameTime >= at;
    }

    public Vec3 anchorAt(int index) {
        double angle = (id.getMostSignificantBits() >>> 40) / (double) (1L << 24) * Mth.TWO_PI + index * Mth.HALF_PI;
        double distance = Math.min(Mth.clamp(radius * 0.6, 112.0, 180.0), radius - 8.0);
        return new Vec3(x + Math.cos(angle) * distance, riftY - RiftAnchor.HANG, z + Math.sin(angle) * distance);
    }

    public Hexahedron withAnchor(int index, RiftAnchor anchor) {
        List<RiftAnchor> changed = new ArrayList<>(anchors);
        changed.set(index, anchor);
        return new Hexahedron(id, x, z, riftY, radius, startTick, changed, opensInversion);
    }

    public boolean anchorsDown() {
        return anchors.stream().allMatch(RiftAnchor::landed);
    }

    public Matrix3f basis(double gameTime) {
        double turns = (gameTime - startTick) / SPIN_TICKS + (id.getLeastSignificantBits() >>> 40) / (double) (1L << 24);
        return new Matrix3f().rotationY((float) (turns % 1.0 * Mth.TWO_PI)).mul(CORNER_DOWN);
    }

    public static Vector3f centre(float through) {
        return new Vector3f(0.0f, (1.0f - through) * HALF_DIAGONAL, 0.0f);
    }

    public float stormRadius() {
        return Mth.clamp(radius * 1.15f, 200.0f, 640.0f);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeDouble(x);
        buf.writeDouble(z);
        buf.writeDouble(riftY);
        buf.writeVarInt(radius);
        buf.writeLong(startTick);
        buf.writeCollection(anchors, (out, anchor) -> anchor.write(out));
        buf.writeBoolean(opensInversion);
    }

    public static Hexahedron read(FriendlyByteBuf buf) {
        return new Hexahedron(buf.readUUID(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
            buf.readVarInt(), buf.readLong(), buf.readList(RiftAnchor::read), buf.readBoolean());
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putDouble("X", x);
        tag.putDouble("Z", z);
        tag.putDouble("RiftY", riftY);
        tag.putInt("Radius", radius);
        tag.putLong("Start", startTick);
        ListTag saved = new ListTag();
        anchors.forEach(anchor -> saved.add(anchor.save()));
        tag.put("Anchors", saved);
        tag.putBoolean("OpensInversion", opensInversion);
        return tag;
    }

    public static Hexahedron load(CompoundTag tag) {
        boolean opensInversion = !tag.contains("OpensInversion") || tag.getBoolean("OpensInversion");
        Hexahedron hexahedron = opening(tag.getUUID("Id"), tag.getDouble("X"), tag.getDouble("Z"),
            tag.getDouble("RiftY"), tag.getInt("Radius"), tag.getLong("Start"), opensInversion);
        ListTag saved = tag.getList("Anchors", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(saved.size(), RiftAnchor.COUNT); i++) {
            hexahedron = hexahedron.withAnchor(i, RiftAnchor.load(saved.getCompound(i)));
        }
        return hexahedron;
    }
}
