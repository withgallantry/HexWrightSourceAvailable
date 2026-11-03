package com.bluup.hexwright.server.vault;

import com.bluup.hexwright.server.portal.PortalWindow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface StreamedPortal {

    ResourceKey<Level> insideDimension();

    List<ChunkPos> insideChunks();

    boolean watchesInsideChunk(int chunkX, int chunkZ);

    AABB insideBounds();

    ResourceKey<Level> outsideDimension();

    PortalWindow outsideWindow();

    List<ChunkPos> outsideChunks();

    Set<UUID> occupants();

    Set<UUID> insideViewers();

    Set<UUID> outsideViewers();
}
