package com.bluup.hexwright.server.cloak;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record CloakField(UUID id, Vec3 center, float radius, float height, @Nullable UUID owner, float toughness) {

    public static final int MIN_SIZE = 2;
    public static final int MAX_RADIUS = 64;
    public static final int MAX_HEIGHT = 128;

    public CloakField(UUID id, Vec3 center, float radius, float height, @Nullable UUID owner) {
        this(id, center, radius, height, owner, 0.0f);
    }

    public static boolean validSize(double radius, double height) {
        return radius >= MIN_SIZE && radius <= MAX_RADIUS && height >= MIN_SIZE && height <= MAX_HEIGHT;
    }

    public boolean admits(@Nullable Entity entity) {
        return owner != null && entity != null && owner.equals(entity.getUUID());
    }

    public boolean encloses(double x, double y, double z) {
        return encloses(center, radius, height, x, y, z);
    }

    public boolean encloses(Vec3 point) {
        return encloses(point.x, point.y, point.z);
    }

    public static boolean encloses(Vec3 center, double radius, double height, Vec3 point) {
        return encloses(center, radius, height, point.x, point.y, point.z);
    }

    private static boolean encloses(Vec3 center, double radius, double height, double x, double y, double z) {
        double dx = x - center.x;
        double dy = y - center.y;
        double dz = z - center.z;
        return (dx * dx + dz * dz) / (radius * radius) + dy * dy / (height * height) <= 1.0;
    }

    public boolean contains(Vec3 point) {
        double dy = point.y - center.y;
        if (dy < 0.0) {
            return false;
        }
        double dx = point.x - center.x;
        double dz = point.z - center.z;
        return (dx * dx + dz * dz) / ((double) radius * radius) + dy * dy / ((double) height * height) <= 1.0;
    }

    public Vec3 surfacePoint(Vec3 point) {
        double squash = radius / height;
        Vec3 rel = point.subtract(center);
        Vec3 onSphere = new Vec3(rel.x, Math.max(rel.y, 0.0) * squash, rel.z);
        if (onSphere.lengthSqr() < 1.0e-6) {
            onSphere = new Vec3(0.0, 1.0, 0.0);
        }
        onSphere = onSphere.normalize().scale(radius);
        return center.add(onSphere.x, onSphere.y / squash, onSphere.z);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeDouble(center.x);
        buf.writeDouble(center.y);
        buf.writeDouble(center.z);
        buf.writeFloat(radius);
        buf.writeFloat(height);
        buf.writeNullable(owner, FriendlyByteBuf::writeUUID);
        buf.writeFloat(toughness);
    }

    public static CloakField read(FriendlyByteBuf buf) {
        return new CloakField(buf.readUUID(),
            new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readFloat(), buf.readFloat(),
            buf.readNullable(FriendlyByteBuf::readUUID), buf.readFloat());
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putDouble("X", center.x);
        tag.putDouble("Y", center.y);
        tag.putDouble("Z", center.z);
        tag.putFloat("Radius", radius);
        tag.putFloat("Height", height);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        if (toughness > 0.0f) {
            tag.putFloat("Toughness", toughness);
        }
        return tag;
    }

    public static CloakField load(CompoundTag tag) {
        float radius = tag.getFloat("Radius");
        float height = tag.contains("Height") ? tag.getFloat("Height") : radius;
        return new CloakField(tag.getUUID("Id"),
            new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z")), radius, height,
            tag.hasUUID("Owner") ? tag.getUUID("Owner") : null, tag.getFloat("Toughness"));
    }
}
