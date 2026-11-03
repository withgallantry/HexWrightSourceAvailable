package com.bluup.hexwright.server.vault;

import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import com.bluup.hexwright.server.portal.PortalWindow;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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

    public static final int MAIN_DOOR = 0;

    public record Frame(int id, BlockPos anchor, Direction facing) {

        public PortalWindow window() {
            return VaultPortalPlacement.paneAt(anchor, facing);
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Id", id);
            tag.put("Anchor", NbtUtils.writeBlockPos(anchor));
            tag.putString("Facing", facing.getName());
            return tag;
        }

        static @Nullable Frame load(CompoundTag tag) {
            Direction facing = Direction.byName(tag.getString("Facing"));
            if (facing == null || facing.getAxis().isVertical()) {
                return null;
            }
            return new Frame(tag.getInt("Id"), NbtUtils.readBlockPos(tag.getCompound("Anchor")), facing);
        }
    }

    private final int id;
    private final UUID owner;
    private final BlockPos origin;
    private final PocketCasterData.Quality grade;
    private final boolean artifact;
    private final String build;
    private int templateVersion;
    private final long createdGameTime;

    private Access access = Access.EVERYONE;

    private @Nullable Door lastDoor;

    private String sky = "";

    private final Map<UUID, String> allowed = new LinkedHashMap<>();

    private final List<Frame> frames = new ArrayList<>();

    private int nextFrameId = MAIN_DOOR + 1;

    private @Nullable Frame mainDoorFrame;

    public VaultRecord(int id, UUID owner, BlockPos origin, PocketCasterData.Quality grade,
                       boolean artifact, String build, int templateVersion, long createdGameTime) {
        this.id = id;
        this.owner = owner;
        this.origin = origin;
        this.grade = grade;
        this.artifact = artifact;
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

    public boolean artifact() {
        return artifact;
    }

    public String build() {
        return build;
    }

    public int templateVersion() {
        return templateVersion;
    }

    void setTemplateVersion(int templateVersion) {
        this.templateVersion = templateVersion;
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

    public String sky() {
        return sky;
    }

    void setSky(String sky) {
        this.sky = sky;
    }


    public List<Frame> frames() {
        return Collections.unmodifiableList(frames);
    }

    public @Nullable Frame frame(int frameId) {
        for (Frame frame : frames) {
            if (frame.id() == frameId) {
                return frame;
            }
        }
        return null;
    }

    Frame addFrame(BlockPos anchor, Direction facing) {
        Frame frame = new Frame(nextFrameId++, anchor, facing);
        frames.add(frame);
        return frame;
    }

    boolean removeFrame(int frameId) {
        return frames.removeIf(frame -> frame.id() == frameId);
    }

    public @Nullable Frame mainDoorFrame() {
        return mainDoorFrame;
    }

    void setMainDoorFrame(@Nullable Frame frame) {
        this.mainDoorFrame = frame;
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

    void revokeAll() {
        allowed.clear();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Id", id);
        tag.putUUID("Owner", owner);
        tag.put("Origin", NbtUtils.writeBlockPos(origin));
        tag.putString("Grade", grade.name());
        if (artifact) {
            tag.putBoolean("Artifact", true);
        }
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
        if (!sky.isEmpty()) {
            tag.putString("Sky", sky);
        }
        if (!frames.isEmpty()) {
            ListTag frameTags = new ListTag();
            for (Frame frame : frames) {
                frameTags.add(frame.save());
            }
            tag.put("Frames", frameTags);
        }
        if (nextFrameId > MAIN_DOOR + 1) {
            tag.putInt("NextFrame", nextFrameId);
        }
        if (mainDoorFrame != null) {
            tag.put("MainDoorFrame", mainDoorFrame.save());
        }
        return tag;
    }

    public static VaultRecord load(CompoundTag tag) {
        PocketCasterData.Quality grade = tag.contains("Grade")
            ? PocketCasterData.Quality.byName(tag.getString("Grade"))
            : PocketCasterData.Quality.FINE;
        String build = tag.getString("Build");
        boolean artifact = tag.contains("Artifact")
            ? tag.getBoolean("Artifact")
            : grade == PocketCasterData.Quality.MASTERWORK && !build.isEmpty();
        VaultRecord record = new VaultRecord(
            tag.getInt("Id"),
            tag.getUUID("Owner"),
            NbtUtils.readBlockPos(tag.getCompound("Origin")),
            grade,
            artifact,
            build,
            tag.getInt("Template"),
            tag.getLong("Created"));
        record.access = Access.byName(tag.getString("Access"));
        if (tag.contains("Door")) {
            record.lastDoor = Door.load(tag.getCompound("Door"));
        }
        record.sky = tag.getString("Sky");
        ListTag guests = tag.getList("Allowed", Tag.TAG_COMPOUND);
        for (int i = 0; i < guests.size(); i++) {
            CompoundTag guest = guests.getCompound(i);
            record.allowed.put(guest.getUUID("Id"), guest.getString("Name"));
        }
        ListTag frameTags = tag.getList("Frames", Tag.TAG_COMPOUND);
        for (int i = 0; i < frameTags.size(); i++) {
            Frame frame = Frame.load(frameTags.getCompound(i));
            if (frame != null && frame.id() > MAIN_DOOR) {
                record.frames.add(frame);
                record.nextFrameId = Math.max(record.nextFrameId, frame.id() + 1);
            }
        }
        record.nextFrameId = Math.max(record.nextFrameId, tag.getInt("NextFrame"));
        if (tag.contains("MainDoorFrame")) {
            Frame frame = Frame.load(tag.getCompound("MainDoorFrame"));
            record.mainDoorFrame = frame != null && frame.id() == MAIN_DOOR ? frame : null;
        }
        return record;
    }
}
