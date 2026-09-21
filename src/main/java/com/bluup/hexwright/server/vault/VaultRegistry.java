package com.bluup.hexwright.server.vault;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class VaultRegistry extends SavedData {

    private static final String STORAGE_ID = "hexwright_vaults";

    private final Map<Integer, VaultRecord> records = new LinkedHashMap<>();
    private int nextId;

    public static VaultRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(VaultRegistry::load, VaultRegistry::new, STORAGE_ID);
    }

    public @Nullable VaultRecord byId(int vaultId) {
        return records.get(vaultId);
    }

    public List<VaultRecord> byCreator(UUID creator) {
        List<VaultRecord> created = new ArrayList<>();
        for (VaultRecord record : records.values()) {
            if (record.owner().equals(creator)) {
                created.add(record);
            }
        }
        return created;
    }

    public int count() {
        return records.size();
    }

    public @Nullable VaultRecord byPosition(Vec3 position) {
        VaultRecord room = byRoom(position);
        return room != null ? room : byCell(position);
    }

    public @Nullable VaultRecord byRoom(Vec3 position) {
        return nearest(position, true);
    }

    private @Nullable VaultRecord byCell(Vec3 position) {
        return nearest(position, false);
    }

    private @Nullable VaultRecord nearest(Vec3 position, boolean room) {
        VaultRecord best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (VaultRecord record : records.values()) {
            AABB bounds = room
                ? VaultRooms.habitableBounds(record)
                : VaultRooms.cellBounds(record);
            if (!bounds.contains(position)) {
                continue;
            }
            double anchorX = room ? bounds.getCenter().x : record.origin().getX();
            double anchorZ = room ? bounds.getCenter().z : record.origin().getZ();
            double dx = position.x - anchorX;
            double dz = position.z - anchorZ;
            double distSq = dx * dx + dz * dz;
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = record;
            }
        }
        return best;
    }

    public VaultRecord create(MinecraftServer server, ServerPlayer owner,
                              PocketCasterData.Quality grade, boolean artifact,
                              @Nullable String build) {
        ServerLevel vaultLevel = VaultDimension.level(server);
        if (vaultLevel == null) {
            throw new IllegalStateException("Vault dimension hexwright:vaults is not loaded");
        }
        int id = nextId++;
        boolean estate = VaultRooms.layoutFor(grade, artifact).isEstate();
        String chosen = !estate ? ""
            : build != null && VaultBuilds.byId(build) != null ? build
            : VaultBuilds.roll(id).id();
        VaultRecord record = new VaultRecord(id, owner.getUUID(),
            VaultRooms.cellOrigin(id), grade, artifact, chosen,
            VaultRooms.TEMPLATE_VERSION, vaultLevel.getGameTime());
        records.put(id, record);
        setDirty();
        generateRoom(vaultLevel, record);
        HexwrightDebug.log(HexwrightDebug.VAULT, "Created {} vault {}{} for {} at {}",
            artifact ? "ARTIFACT" : grade, id, chosen.isEmpty() ? "" : " (" + chosen + ")",
            owner.getGameProfile().getName(), record.origin());
        return record;
    }

    private static void generateRoom(ServerLevel vaultLevel, VaultRecord record) {
        for (ChunkPos chunk : VaultRooms.roomChunks(record)) {
            vaultLevel.getChunk(chunk.x, chunk.z);
        }
        VaultRooms.generate(vaultLevel, record);
    }

    private VaultRegistry() {
    }

    private static VaultRegistry load(CompoundTag tag) {
        VaultRegistry registry = new VaultRegistry();
        registry.nextId = tag.getInt("NextId");
        ListTag entries = tag.getList("Vaults", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            try {
                VaultRecord record = VaultRecord.load(entries.getCompound(i));
                registry.records.put(record.id(), record);
                registry.nextId = Math.max(registry.nextId, record.id() + 1);
            } catch (RuntimeException e) {
                Hexwright.LOGGER.warn("Dropping malformed vault record from save", e);
            }
        }
        return registry;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("NextId", nextId);
        ListTag entries = new ListTag();
        for (VaultRecord record : records.values()) {
            entries.add(record.save());
        }
        tag.put("Vaults", entries);
        return tag;
    }
}
