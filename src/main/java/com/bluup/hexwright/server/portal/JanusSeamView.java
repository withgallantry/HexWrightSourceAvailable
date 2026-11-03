package com.bluup.hexwright.server.portal;

import com.bluup.hexwright.server.vault.StreamedPortal;
import com.bluup.hexwright.server.vault.VaultChunkStreamer;
import com.bluup.hexwright.server.vault.VaultManager;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class JanusSeamView implements StreamedPortal {

    private final UUID pairId;
    private final ResourceKey<Level> outsideDimension;
    private final PortalWindow outsideWindow;
    private final List<ChunkPos> outsideChunks;
    private final List<ChunkPos> outsideTicketChunks;
    private final ResourceKey<Level> insideDimension;
    private final PortalWindow insideWindow;
    private final List<ChunkPos> insideChunks;
    private final List<ChunkPos> insideTicketChunks;
    private final Set<Long> insideChunkKeys = new HashSet<>();
    private final AABB insideBounds;

    private final Set<UUID> occupants = new HashSet<>();
    private final Set<UUID> insideViewers = new HashSet<>();
    private final Set<UUID> outsideViewers = new HashSet<>();

    private boolean insideHeld;
    private boolean outsideHeld;

    JanusSeamView(PortalPair pair) {
        this.pairId = pair.id();
        this.outsideDimension = pair.dimension(0);
        this.outsideWindow = pair.window(0);
        this.insideDimension = pair.dimension(1);
        this.insideWindow = pair.window(1);
        int watch = VaultManager.OUTSIDE_WATCH_RADIUS_CHUNKS;
        int ticket = VaultManager.OUTSIDE_TICKET_RADIUS_CHUNKS;
        this.outsideChunks = VaultManager.outsideChunksFor(this.outsideWindow, watch);
        this.outsideTicketChunks = VaultManager.outsideChunksFor(this.outsideWindow, ticket);
        this.insideChunks = VaultManager.outsideChunksFor(this.insideWindow, watch);
        this.insideTicketChunks = VaultManager.outsideChunksFor(this.insideWindow, ticket);
        for (ChunkPos chunk : this.insideChunks) {
            this.insideChunkKeys.add(chunk.toLong());
        }
        this.insideBounds = AABB.ofSize(this.insideWindow.center(), VaultChunkStreamer.OUTSIDE_ENTITY_RANGE * 2.0,
            VaultChunkStreamer.OUTSIDE_ENTITY_RANGE * 2.0, VaultChunkStreamer.OUTSIDE_ENTITY_RANGE * 2.0);
    }

    UUID pairId() {
        return this.pairId;
    }

    void updateOccupants(MinecraftServer server) {
        this.occupants.clear();
        ServerLevel inside = server.getLevel(this.insideDimension);
        if (inside == null) {
            return;
        }
        Vec3 center = this.insideWindow.center();
        double rangeSq = VaultChunkStreamer.SUBSCRIBE_RANGE * VaultChunkStreamer.SUBSCRIBE_RANGE;
        for (ServerPlayer player : inside.players()) {
            if (player.position().distanceToSqr(center) <= rangeSq) {
                this.occupants.add(player.getUUID());
            }
        }
    }

    void updateTickets(MinecraftServer server) {
        this.insideHeld = hold(server.getLevel(this.insideDimension), this.insideTicketChunks, this.insideHeld,
            !this.insideViewers.isEmpty());
        this.outsideHeld = hold(server.getLevel(this.outsideDimension), this.outsideTicketChunks, this.outsideHeld,
            !this.outsideViewers.isEmpty());
    }

    void releaseTickets(MinecraftServer server) {
        this.insideHeld = hold(server.getLevel(this.insideDimension), this.insideTicketChunks, this.insideHeld, false);
        this.outsideHeld = hold(server.getLevel(this.outsideDimension), this.outsideTicketChunks, this.outsideHeld,
            false);
    }

    private boolean hold(@Nullable ServerLevel level, List<ChunkPos> chunks, boolean held, boolean wanted) {
        if (level == null || held == wanted) {
            return held;
        }
        for (ChunkPos chunk : chunks) {
            if (wanted) {
                level.getChunkSource().addRegionTicket(JanusSeams.TICKET, chunk, VaultManager.VIEW_TICKET_RADIUS,
                    this.pairId);
            } else {
                level.getChunkSource().removeRegionTicket(JanusSeams.TICKET, chunk, VaultManager.VIEW_TICKET_RADIUS,
                    this.pairId);
            }
        }
        return wanted;
    }

    @Override
    public ResourceKey<Level> insideDimension() {
        return this.insideDimension;
    }

    @Override
    public List<ChunkPos> insideChunks() {
        return this.insideChunks;
    }

    @Override
    public boolean watchesInsideChunk(int chunkX, int chunkZ) {
        return this.insideChunkKeys.contains(ChunkPos.asLong(chunkX, chunkZ));
    }

    @Override
    public AABB insideBounds() {
        return this.insideBounds;
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
