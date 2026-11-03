package com.bluup.hexwright.server.cloak;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public record CloakField(UUID id, Vec3 center, float width, float height, @Nullable UUID owner, float toughness,
                         Set<UUID> guests) {

    public static final int MIN_SIZE = 2;
    public static final int MAX_WIDTH = 128;
    public static final int MAX_HEIGHT = 128;

    public CloakField {
        guests = Set.copyOf(guests);
    }

    public CloakField(UUID id, Vec3 center, float width, float height, @Nullable UUID owner) {
        this(id, center, width, height, owner, 0.0f, Set.of());
    }

    public CloakField(UUID id, Vec3 center, float width, float height, @Nullable UUID owner, float toughness) {
        this(id, center, width, height, owner, toughness, Set.of());
    }

    public static boolean validSize(double width, double height) {
        return width >= MIN_SIZE && width <= MAX_WIDTH && height >= MIN_SIZE && height <= MAX_HEIGHT;
    }

    public float halfWidth() {
        return width * 0.5f;
    }

    public boolean admits(@Nullable Entity entity) {
        return owner != null && entity != null && (owner.equals(entity.getUUID()) || guests.contains(entity.getUUID()));
    }

    public CloakField toggleGuest(UUID guest) {
        Set<UUID> changed = new HashSet<>(guests);
        if (!changed.remove(guest)) {
            changed.add(guest);
        }
        return new CloakField(id, center, width, height, owner, toughness, changed);
    }

    public boolean encloses(double x, double y, double z) {
        return encloses(center, width, height, x, y, z);
    }

    public boolean encloses(Vec3 point) {
        return encloses(point.x, point.y, point.z);
    }

    public static boolean encloses(Vec3 center, double width, double height, Vec3 point) {
        return encloses(center, width, height, point.x, point.y, point.z);
    }

    private static boolean encloses(Vec3 center, double width, double height, double x, double y, double z) {
        double half = width * 0.5;
        return Math.abs(x - center.x) <= half && Math.abs(z - center.z) <= half && Math.abs(y - center.y) <= height;
    }

    public boolean contains(Vec3 point) {
        return point.y >= center.y && encloses(point);
    }

    public Vec3 surfacePoint(Vec3 point) {
        double half = halfWidth();
        double x = point.x - center.x;
        double y = Math.max(point.y - center.y, 0.0);
        double z = point.z - center.z;
        if (Math.abs(x) > half || Math.abs(z) > half || y > height) {
            return center.add(Mth.clamp(x, -half, half), Math.min(y, height), Mth.clamp(z, -half, half));
        }
        double toWallX = half - Math.abs(x);
        double toWallZ = half - Math.abs(z);
        double toRoof = height - y;
        if (toRoof <= toWallX && toRoof <= toWallZ) {
            y = height;
        } else if (toWallX <= toWallZ) {
            x = Math.copySign(half, x);
        } else {
            z = Math.copySign(half, z);
        }
        return center.add(x, y, z);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeDouble(center.x);
        buf.writeDouble(center.y);
        buf.writeDouble(center.z);
        buf.writeFloat(width);
        buf.writeFloat(height);
        buf.writeNullable(owner, FriendlyByteBuf::writeUUID);
        buf.writeFloat(toughness);
        buf.writeCollection(guests, FriendlyByteBuf::writeUUID);
    }

    public static CloakField read(FriendlyByteBuf buf) {
        return new CloakField(buf.readUUID(),
            new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readFloat(), buf.readFloat(),
            buf.readNullable(FriendlyByteBuf::readUUID), buf.readFloat(),
            buf.readCollection(HashSet::new, FriendlyByteBuf::readUUID));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putDouble("X", center.x);
        tag.putDouble("Y", center.y);
        tag.putDouble("Z", center.z);
        tag.putFloat("Width", width);
        tag.putFloat("Height", height);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        if (toughness > 0.0f) {
            tag.putFloat("Toughness", toughness);
        }
        if (!guests.isEmpty()) {
            ListTag list = new ListTag();
            guests.forEach(guest -> list.add(NbtUtils.createUUID(guest)));
            tag.put("Guests", list);
        }
        return tag;
    }

    public static CloakField load(CompoundTag tag) {
        float height = tag.getFloat("Height");
        float width;
        if (isDome(tag)) {
            float radius = tag.getFloat("Radius");
            height = tag.contains("Height") ? height : radius;
            width = radius * 2.0f;
        } else {
            width = tag.getFloat("Width");
        }
        Set<UUID> guests = new HashSet<>();
        for (Tag guest : tag.getList("Guests", Tag.TAG_INT_ARRAY)) {
            guests.add(NbtUtils.loadUUID(guest));
        }
        return new CloakField(tag.getUUID("Id"),
            new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z")), width, height,
            tag.hasUUID("Owner") ? tag.getUUID("Owner") : null, tag.getFloat("Toughness"), guests);
    }

    static boolean isDome(CompoundTag tag) {
        return !tag.contains("Width");
    }
}
