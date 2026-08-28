package com.bluup.hexwright.server.vault;

import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class VaultRecord {

    public enum Access {
        EVERYONE,
        ALLOWED_ONLY;

        public static Access byName(String name) {
            for (Access access : values()) {
                if (access.name().equalsIgnoreCase(name)) {
                    return access;
                }
            }
            return EVERYONE;
        }
    }

    public record Door(ResourceKey<Level> dimension, BlockPos anchor, Direction facing) {

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Dim", dimension.location().toString());
            tag.put("Anchor", NbtUtils.writeBlockPos(anchor));
            tag.putString("Facing", facing.getName());
            return tag;
        }

        static @Nullable Door load(CompoundTag tag) {
            ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dim"));
            Direction facing = Direction.byName(tag.getString("Facing"));
            if (dimension == null || facing == null || facing.getAxis().isVertical()) {
                return null;
            }
            return new Door(ResourceKey.create(Registries.DIMENSION, dimension),
                NbtUtils.readBlockPos(tag.getCompound("Anchor")), facing);
        }
    }

    private final int id;
    private final UUID owner;
    private final BlockPos origin;
    private final PocketCasterData.Quality grade;
    private final String build;
    private final int templateVersion;
    private final long createdGameTime;

    private Access access = Access.EVERYONE;

    private @Nullable Door lastDoor;

    private final Map<UUID, String> allowed = new LinkedHashMap<>();

    public VaultRecord(int id, UUID owner, BlockPos origin, PocketCasterData.Quality grade,
                       String build, int templateVersion, long createdGameTime) {
        this.id = id;
        this.owner = owner;
        this.origin = origin;
        this.grade = grade;
        this.build = build;
        this.templateVersion = templateVersion;
        this.createdGameTime = createdGameTime;
    }

    public int id() {
        return id;
    }

    public UUID owner() {
        return owner;
    }

    public BlockPos origin() {
        return origin;
    }

    public PocketCasterData.Quality grade() {
        return grade;
    }

    public String build() {
        return build;
    }

    public int templateVersion() {
        return templateVersion;
    }

    public long createdGameTime() {
        return createdGameTime;
    }

    public Access access() {
        return access;
    }

    void setAccess(Access access) {
        this.access = access;
    }


    public @Nullable Door lastDoor() {
        return lastDoor;
    }

    void setLastDoor(Door door) {
        this.lastDoor = door;
    }

    public Map<UUID, String> allowed() {
        return Collections.unmodifiableMap(allowed);
    }

    String invite(UUID uuid, String name) {
        return allowed.put(uuid, name);
    }

    String revoke(UUID uuid) {
        return allowed.remove(uuid);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Id", id);
        tag.putUUID("Owner", owner);
        tag.put("Origin", NbtUtils.writeBlockPos(origin));
        tag.putString("Grade", grade.name());
        if (!build.isEmpty()) {
            tag.putString("Build", build);
        }
        tag.putInt("Template", templateVersion);
        tag.putLong("Created", createdGameTime);
        tag.putString("Access", access.name());
        if (!allowed.isEmpty()) {
            ListTag guests = new ListTag();
            for (Map.Entry<UUID, String> entry : allowed.entrySet()) {
                CompoundTag guest = new CompoundTag();
                guest.putUUID("Id", entry.getKey());
                guest.putString("Name", entry.getValue());
                guests.add(guest);
            }
            tag.put("Allowed", guests);
        }
        if (lastDoor != null) {
            tag.put("Door", lastDoor.save());
        }
        return tag;
    }

    public static VaultRecord load(CompoundTag tag) {
        PocketCasterData.Quality grade = tag.contains("Grade")
            ? PocketCasterData.Quality.byName(tag.getString("Grade"))
            : PocketCasterData.Quality.FINE;
        VaultRecord record = new VaultRecord(
            tag.getInt("Id"),
            tag.getUUID("Owner"),
            NbtUtils.readBlockPos(tag.getCompound("Origin")),
            grade,
            tag.getString("Build"),
            tag.getInt("Template"),
            tag.getLong("Created"));
        record.access = Access.byName(tag.getString("Access"));
        if (tag.contains("Door")) {
            record.lastDoor = Door.load(tag.getCompound("Door"));
        }
        ListTag guests = tag.getList("Allowed", Tag.TAG_COMPOUND);
        for (int i = 0; i < guests.size(); i++) {
            CompoundTag guest = guests.getCompound(i);
            record.allowed.put(guest.getUUID("Id"), guest.getString("Name"));
        }
        return record;
    }
}
