package com.bluup.hexwright.server.dungeon;

import com.bluup.hexwright.Hexwright;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DungeonInstance {

    public enum Kind {
        DUNGEON(null),
        TALUS_ARENA("binary_suns"),
        TOWER_SKELETON_ARENA("teal_magenta_nebula"),
        MAGNUS_ARENA("crimson_nebula"),
        COG_SENTINEL_ARENA("brass_nebula");

        private final @Nullable String sky;

        Kind(@Nullable String sky) {
            this.sky = sky;
        }

        public @Nullable ResourceLocation sky() {
            return this.sky == null ? null : Hexwright.id(this.sky);
        }
    }

    public enum State {
        BUILDING,
        OPEN,
        CLEARING
    }

    public record Entrance(ResourceKey<Level> dimension, Vec3 center, Direction facing, @Nullable BlockPos stone) {

        public Entrance(ResourceKey<Level> dimension, Vec3 center, Direction facing) {
            this(dimension, center, facing, null);
        }

        void save(CompoundTag tag) {
            tag.putString("Dimension", this.dimension.location().toString());
            tag.putDouble("X", this.center.x);
            tag.putDouble("Y", this.center.y);
            tag.putDouble("Z", this.center.z);
            tag.putString("Facing", this.facing.getName());
            if (this.stone != null) {
                tag.putLong("Stone", this.stone.asLong());
            }
        }

        static @Nullable Entrance load(CompoundTag tag) {
            ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
            Direction facing = Direction.byName(tag.getString("Facing"));
            if (dimension == null || facing == null) {
                return null;
            }
            return new Entrance(ResourceKey.create(Registries.DIMENSION, dimension),
                new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z")), facing,
                tag.contains("Stone") ? BlockPos.of(tag.getLong("Stone")) : null);
        }
    }

    private final int id;
    private final Kind kind;
    private final int cell;
    private final long seed;
    private final UUID opener;
    private final Entrance entrance;
    private final BoundingBox extent;
    private final long startChunk;
    private final BlockPos shaftFoot;
    private final BlockPos safeRoomAt;

    private State state;
    private long riftOpensAt;
    private long riftClosesAt;
    private final Set<UUID> members = new HashSet<>();
    private final Set<UUID> departed = new HashSet<>();
    private final Map<UUID, Long> offlineSince = new HashMap<>();

    DungeonInstance(int id, Kind kind, int cell, long seed, UUID opener, Entrance entrance, BoundingBox extent,
                    long startChunk, BlockPos shaftFoot, BlockPos safeRoomAt, State state) {
        this.id = id;
        this.kind = kind;
        this.cell = cell;
        this.seed = seed;
        this.opener = opener;
        this.entrance = entrance;
        this.extent = extent;
        this.startChunk = startChunk;
        this.shaftFoot = shaftFoot;
        this.safeRoomAt = safeRoomAt;
        this.state = state;
    }

    public int id() {
        return this.id;
    }

    public Kind kind() {
        return this.kind;
    }

    public int cell() {
        return this.cell;
    }

    public long seed() {
        return this.seed;
    }

    public UUID opener() {
        return this.opener;
    }

    public Entrance entrance() {
        return this.entrance;
    }

    public BoundingBox extent() {
        return this.extent;
    }

    public long startChunk() {
        return this.startChunk;
    }

    public BlockPos shaftFoot() {
        return this.shaftFoot;
    }

    public DungeonSafeRoom safeRoom() {
        return new DungeonSafeRoom(this.safeRoomAt, this.kind != Kind.DUNGEON);
    }

    public State state() {
        return this.state;
    }

    void setState(State state) {
        this.state = state;
    }

    public long riftOpensAt() {
        return this.riftOpensAt;
    }

    public long riftClosesAt() {
        return this.riftClosesAt;
    }

    void openRift(long opensAt, long closesAt) {
        this.riftOpensAt = opensAt;
        this.riftClosesAt = closesAt;
    }

    void holdWindowUntil(long notBefore) {
        this.riftOpensAt = notBefore;
    }

    void closeRift(long now) {
        this.riftClosesAt = Math.min(this.riftClosesAt, now);
    }

    public boolean riftHolds(long now) {
        return this.state == State.OPEN && now < this.riftClosesAt;
    }

    public boolean windowHolds(long now) {
        return this.entrance.stone() != null && riftHolds(now) && now >= this.riftOpensAt;
    }

    public boolean windowForming(long now) {
        return this.entrance.stone() != null
            && (this.state == State.BUILDING || riftHolds(now) && now < this.riftOpensAt);
    }

    public @Nullable DungeonRift entranceRift(long now) {
        if (this.entrance.stone() != null) {
            return null;
        }
        if (this.state == State.BUILDING) {
            return new DungeonRift(this.id, DungeonRift.Kind.FORMING, this.entrance.center(), this.entrance.facing(),
                this.seed, 0L, 0L);
        }
        return riftHolds(now)
            ? new DungeonRift(this.id, DungeonRift.Kind.ENTRANCE, this.entrance.center(), this.entrance.facing(),
                this.seed, this.riftOpensAt, this.riftClosesAt)
            : null;
    }

    public Set<UUID> members() {
        return this.members;
    }

    public Set<UUID> departed() {
        return this.departed;
    }

    public Map<UUID, Long> offlineSince() {
        return this.offlineSince;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Id", this.id);
        tag.putString("Kind", this.kind.name());
        tag.putInt("Cell", this.cell);
        tag.putLong("Seed", this.seed);
        tag.putUUID("Opener", this.opener);
        CompoundTag entrance = new CompoundTag();
        this.entrance.save(entrance);
        tag.put("Entrance", entrance);
        tag.putIntArray("Extent", new int[]{this.extent.minX(), this.extent.minY(), this.extent.minZ(),
            this.extent.maxX(), this.extent.maxY(), this.extent.maxZ()});
        tag.putLong("StartChunk", this.startChunk);
        tag.putLong("ShaftFoot", this.shaftFoot.asLong());
        tag.putLong("SafeRoom", this.safeRoomAt.asLong());
        tag.putString("State", this.state.name());
        tag.putLong("RiftOpensAt", this.riftOpensAt);
        tag.putLong("RiftClosesAt", this.riftClosesAt);
        tag.put("Members", uuids(this.members));
        tag.put("Departed", uuids(this.departed));
        ListTag offline = new ListTag();
        this.offlineSince.forEach((player, since) -> {
            CompoundTag row = new CompoundTag();
            row.putUUID("Player", player);
            row.putLong("Since", since);
            offline.add(row);
        });
        tag.put("Offline", offline);
        return tag;
    }

    static @Nullable DungeonInstance load(CompoundTag tag) {
        Entrance entrance = Entrance.load(tag.getCompound("Entrance"));
        int[] extent = tag.getIntArray("Extent");
        if (entrance == null || extent.length != 6 || !tag.hasUUID("Opener")) {
            return null;
        }
        State state;
        Kind kind;
        try {
            state = State.valueOf(tag.getString("State"));
            kind = tag.contains("Kind") ? Kind.valueOf(tag.getString("Kind")) : Kind.DUNGEON;
        } catch (IllegalArgumentException e) {
            return null;
        }
        BlockPos shaftFoot = BlockPos.of(tag.getLong("ShaftFoot"));
        BlockPos safeRoomAt = tag.contains("SafeRoom") ? BlockPos.of(tag.getLong("SafeRoom"))
            : new BlockPos(shaftFoot.getX(), tag.getInt("Lid"), shaftFoot.getZ());
        DungeonInstance instance = new DungeonInstance(tag.getInt("Id"), kind, tag.getInt("Cell"), tag.getLong("Seed"),
            tag.getUUID("Opener"), entrance,
            new BoundingBox(extent[0], extent[1], extent[2], extent[3], extent[4], extent[5]),
            tag.getLong("StartChunk"), shaftFoot, safeRoomAt, state);
        instance.openRift(tag.getLong("RiftOpensAt"), tag.getLong("RiftClosesAt"));
        readUuids(tag.getList("Members", Tag.TAG_INT_ARRAY), instance.members);
        readUuids(tag.getList("Departed", Tag.TAG_INT_ARRAY), instance.departed);
        ListTag offline = tag.getList("Offline", Tag.TAG_COMPOUND);
        for (int index = 0; index < offline.size(); index++) {
            CompoundTag row = offline.getCompound(index);
            if (row.hasUUID("Player")) {
                instance.offlineSince.put(row.getUUID("Player"), row.getLong("Since"));
            }
        }
        return instance;
    }

    private static ListTag uuids(Set<UUID> players) {
        ListTag list = new ListTag();
        for (UUID player : players) {
            list.add(NbtUtils.createUUID(player));
        }
        return list;
    }

    private static void readUuids(ListTag list, Set<UUID> into) {
        for (Tag entry : list) {
            if (entry instanceof IntArrayTag ints && ints.size() == 4) {
                into.add(NbtUtils.loadUUID(entry));
            }
        }
    }
}
