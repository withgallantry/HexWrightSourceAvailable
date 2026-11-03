package com.bluup.hexwright.server.vault;

import com.bluup.hexwright.server.portal.PortalWindow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class VaultPortalSession implements StreamedPortal {

    public enum State {
        OPENING,
        OPEN,
        CLOSE_PENDING_OCCUPANTS,
        CLOSED
    }

    private final UUID sessionId;
    private final int vaultId;
    private final int frameId;
    private final UUID opener;
    private final UUID pairId;
    private final ResourceKey<Level> outsideDimension;
    private final PortalWindow outsideWindow;
    private final PortalWindow vaultWindow;
    private final List<ChunkPos> outsideChunks;

    private final List<ChunkPos> outsideTicketChunks;

    private final ChunkPos vaultChunk;

    private final int vaultTicketRadius;

    private final List<ChunkPos> vaultChunks;

    private final Set<Long> vaultChunkKeys = new HashSet<>();

    private final AABB roomBounds;

    private State state = State.OPENING;
    private boolean closeRequested;
    private int graceTicks;

    private final Set<UUID> occupants = new HashSet<>();

    private final Set<UUID> insideViewers = new HashSet<>();
    private final Set<UUID> outsideViewers = new HashSet<>();

    public VaultPortalSession(UUID sessionId, int vaultId, int frameId, UUID opener, UUID pairId,
                              ResourceKey<Level> outsideDimension,
                              PortalWindow outsideWindow, PortalWindow vaultWindow,
                              List<ChunkPos> outsideChunks, List<ChunkPos> outsideTicketChunks,
                              ChunkPos vaultChunk, int vaultTicketRadius, List<ChunkPos> vaultChunks,
                              AABB roomBounds) {
        this.sessionId = sessionId;
        this.vaultId = vaultId;
        this.frameId = frameId;
        this.opener = opener;
        this.pairId = pairId;
        this.outsideDimension = outsideDimension;
        this.outsideWindow = outsideWindow;
        this.vaultWindow = vaultWindow;
        this.outsideChunks = outsideChunks;
        this.outsideTicketChunks = outsideTicketChunks;
        this.vaultChunk = vaultChunk;
        this.vaultTicketRadius = vaultTicketRadius;
        this.vaultChunks = vaultChunks;
        this.roomBounds = roomBounds;
        for (ChunkPos chunk : vaultChunks) {
            vaultChunkKeys.add(chunk.toLong());
        }
    }

    public UUID sessionId() {
        return sessionId;
    }

    public int vaultId() {
        return vaultId;
    }

    public int frameId() {
        return frameId;
    }

    public UUID opener() {
        return opener;
    }

    public UUID pairId() {
        return pairId;
    }

    @Override
    public ResourceKey<Level> insideDimension() {
        return VaultDimension.KEY;
    }

    @Override
    public ResourceKey<Level> outsideDimension() {
        return outsideDimension;
    }

    @Override
    public PortalWindow outsideWindow() {
        return outsideWindow;
    }

    public PortalWindow vaultWindow() {
        return vaultWindow;
    }

    @Override
    public List<ChunkPos> outsideChunks() {
        return outsideChunks;
    }

    public List<ChunkPos> outsideTicketChunks() {
        return outsideTicketChunks;
    }

    public ChunkPos vaultChunk() {
        return vaultChunk;
    }

    public int vaultTicketRadius() {
        return vaultTicketRadius;
    }

    public List<ChunkPos> vaultChunks() {
        return vaultChunks;
    }

    @Override
    public List<ChunkPos> insideChunks() {
        return vaultChunks;
    }

    @Override
    public boolean watchesInsideChunk(int chunkX, int chunkZ) {
        return vaultChunkKeys.contains(ChunkPos.asLong(chunkX, chunkZ));
    }

    public AABB roomBounds() {
        return roomBounds;
    }

    @Override
    public AABB insideBounds() {
        return roomBounds;
    }

    public State state() {
        return state;
    }

    void setState(State state) {
        this.state = state;
    }

    public boolean closeRequested() {
        return closeRequested;
    }

    void setCloseRequested(boolean closeRequested) {
        this.closeRequested = closeRequested;
    }

    int graceTicks() {
        return graceTicks;
    }

    void setGraceTicks(int graceTicks) {
        this.graceTicks = graceTicks;
    }

    @Override
    public Set<UUID> occupants() {
        return occupants;
    }

    @Override
    public Set<UUID> insideViewers() {
        return insideViewers;
    }

    @Override
    public Set<UUID> outsideViewers() {
        return outsideViewers;
    }
}
