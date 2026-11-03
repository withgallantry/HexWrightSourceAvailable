package com.bluup.hexwright.server.dungeon;

import com.bluup.hexwright.server.portal.PortalWindow;
import com.bluup.hexwright.server.vault.StreamedPortal;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class TravelStoneWindow implements StreamedPortal {

    private final int instanceId;
    private final UUID pairId;
    private final ResourceKey<Level> outsideDimension;
    private final PortalWindow outsideWindow;
    private final List<ChunkPos> outsideChunks;
    private final ChunkPos outsideTicketChunk;
    private final ChunkPos roomTicketChunk;
    private final List<ChunkPos> roomChunks;
    private final Set<Long> roomChunkKeys = new HashSet<>();
    private final AABB roomBounds;

    private final Set<UUID> occupants = new HashSet<>();
    private final Set<UUID> insideViewers = new HashSet<>();
    private final Set<UUID> outsideViewers = new HashSet<>();

    TravelStoneWindow(int instanceId, UUID pairId, ResourceKey<Level> outsideDimension, PortalWindow outsideWindow,
                      List<ChunkPos> outsideChunks, ChunkPos outsideTicketChunk, ChunkPos roomTicketChunk,
                      List<ChunkPos> roomChunks, AABB roomBounds) {
        this.instanceId = instanceId;
        this.pairId = pairId;
        this.outsideDimension = outsideDimension;
        this.outsideWindow = outsideWindow;
        this.outsideChunks = outsideChunks;
        this.outsideTicketChunk = outsideTicketChunk;
        this.roomTicketChunk = roomTicketChunk;
        this.roomChunks = roomChunks;
        this.roomBounds = roomBounds;
        for (ChunkPos chunk : roomChunks) {
            this.roomChunkKeys.add(chunk.toLong());
        }
    }

    public int instanceId() {
        return this.instanceId;
    }

    public UUID pairId() {
        return this.pairId;
    }

    ChunkPos outsideTicketChunk() {
        return this.outsideTicketChunk;
    }

    ChunkPos roomTicketChunk() {
        return this.roomTicketChunk;
    }

    @Override
    public ResourceKey<Level> insideDimension() {
        return DungeonDimension.KEY;
    }

    @Override
    public List<ChunkPos> insideChunks() {
        return this.roomChunks;
    }

    @Override
    public boolean watchesInsideChunk(int chunkX, int chunkZ) {
        return this.roomChunkKeys.contains(ChunkPos.asLong(chunkX, chunkZ));
    }

    @Override
    public AABB insideBounds() {
        return this.roomBounds;
    }

    @Override
    public ResourceKey<Level> outsideDimension() {
        return this.outsideDimension;
    }

    @Override
    public PortalWindow outsideWindow() {
        return this.outsideWindow;
    }

    @Override
    public List<ChunkPos> outsideChunks() {
        return this.outsideChunks;
    }

    @Override
    public Set<UUID> occupants() {
        return this.occupants;
    }

    @Override
    public Set<UUID> insideViewers() {
        return this.insideViewers;
    }

    @Override
    public Set<UUID> outsideViewers() {
        return this.outsideViewers;
    }
}
