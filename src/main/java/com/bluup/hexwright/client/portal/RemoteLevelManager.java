package com.bluup.hexwright.client.portal;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.mixin.LevelRendererAccessor;
import com.bluup.hexwright.mixin.MinecraftPortalAccessor;
import com.bluup.hexwright.server.portal.PortalPair;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RemoteLevelManager {

    private static final int REMOTE_VIEW_DISTANCE = 12;

    public record EntitySnapshot(int id, boolean player, UUID uuid, int typeId,
                                 double x, double y, double z, float yaw, float pitch, float headYaw,
                                 List<ItemStack> equipment,
                                 List<SynchedEntityData.DataValue<?>> data) {
    }

    private static final class RemoteLevel {
        final ClientLevel level;
        final LevelRenderer renderer;
        final Set<Long> chunks = new HashSet<>();
        final Map<Integer, Entity> entities = new HashMap<>();
        long lastCameraChunk = Long.MIN_VALUE;

        final boolean retained;

        long retireDeadlineGameTime = Long.MAX_VALUE;

        long lastDrawnGameTime;

        long emptySinceGameTime;

        RemoteLevel(ClientLevel level, LevelRenderer renderer, boolean retained) {
            this.level = level;
            this.renderer = renderer;
            this.retained = retained;
            this.lastDrawnGameTime = gameTime();
            this.emptySinceGameTime = gameTime();
        }
    }

    private static final Map<ResourceLocation, List<RemoteLevel>> LEVELS = new HashMap<>();

    private static final Map<ResourceLocation, List<RemoteLevel>> RETIRING = new HashMap<>();

    private static final int MAX_LIVE_REGIONS = 3;

    private static List<RemoteLevel> live(ResourceLocation dimension) {
        return LEVELS.getOrDefault(dimension, List.of());
    }

    private static void addLive(ResourceLocation dimension, RemoteLevel remote) {
        LEVELS.computeIfAbsent(dimension, key -> new ArrayList<>()).add(remote);
    }

    private static void detach(Map<ResourceLocation, List<RemoteLevel>> from,
                               ResourceLocation dimension, RemoteLevel remote) {
        List<RemoteLevel> levels = from.get(dimension);
        if (levels == null) {
            return;
        }
        levels.remove(remote);
        if (levels.isEmpty()) {
            from.remove(dimension);
        }
    }

    private static boolean tracked(ResourceLocation dimension) {
        return LEVELS.containsKey(dimension) || RETIRING.containsKey(dimension);
    }

    private static final Set<LevelRenderer> STREAMED_RENDERERS =
        java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    private static final int STREAMED_VIEW_DISTANCE = 7;

    public static int streamedViewDistance(LevelRenderer renderer) {
        return STREAMED_RENDERERS.contains(renderer) ? STREAMED_VIEW_DISTANCE : -1;
    }

    private static @Nullable ClientLevel savedMainLevel;
    private static @Nullable LevelRenderer savedMainRenderer;
    private static boolean remotePassActive;
    private static @Nullable RemoteLevel activeRemote;

    private static final double MIN_FOG_END = 16.0;

    private static final double MAX_FOG_END = 192.0;

    private RemoteLevelManager() {
    }

    public static void register() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(RemoteLevelManager::destroyAll));
        ClientTickEvents.END_CLIENT_TICK.register(RemoteLevelManager::tick);
    }


    private static final boolean RETENTION_ENABLED =
        !"false".equals(System.getProperty("hexwright.vault.retain"));

    private static final boolean SKIP_LOADING_SCREEN =
        !"false".equals(System.getProperty("hexwright.vault.skipLoadingScreen"));

    public static void retainOutgoingLevel(ResourceKey<Level> incoming) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel outgoing = mc.level;
        skipNextLoadingScreen = false;
        if (!RETENTION_ENABLED || outgoing == null || outgoing.dimension().equals(incoming)) {
            return;
        }
        if (!ClientPortalManager.pairJoins(outgoing.dimension(), incoming)) {
            return;
        }
        List<RemoteLevel> destination = live(incoming.location());
        skipNextLoadingScreen = SKIP_LOADING_SCREEN
            && destination.stream().anyMatch(remote -> !remote.retained);
        ResourceLocation key = outgoing.dimension().location();
        destroyEvery(key);
        List<Integer> stale = new ArrayList<>();
        for (Entity entity : outgoing.entitiesForRendering()) {
            stale.add(entity.getId());
        }
        for (int id : stale) {
            outgoing.removeEntity(id, Entity.RemovalReason.DISCARDED);
        }

        LevelRenderer outgoingRenderer = mc.levelRenderer;
        LevelRenderer replacement = new LevelRenderer(mc, mc.getEntityRenderDispatcher(),
            mc.getBlockEntityRenderDispatcher(), mc.renderBuffers());
        ((MinecraftPortalAccessor) (Object) mc).hexwright$setLevelRenderer(replacement);
        addLive(key, new RemoteLevel(outgoing, outgoingRenderer, true));
        HexwrightNetworking.sendVaultRetained(key, true);
        Hexwright.LOGGER.info(
            "[vault] retained {} ({} entities cleared) for the view out of {}; loading screen skipped: {}",
            key, stale.size(), incoming.location(), skipNextLoadingScreen);
    }

    public static void handleLevelInit(ResourceLocation dimension, ResourceLocation dimensionType) {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener connection = mc.getConnection();
        if (mc.level == null || connection == null) {
            return;
        }
        if (mc.level.dimension().location().equals(dimension)) {
            return;
        }
        if (LEVELS.containsKey(dimension)) {
            return;
        }
        Holder<DimensionType> typeHolder = connection.registryAccess()
            .registryOrThrow(Registries.DIMENSION_TYPE)
            .getHolder(ResourceKey.create(Registries.DIMENSION_TYPE, dimensionType))
            .orElse(null);
        if (typeHolder == null) {
            Hexwright.LOGGER.warn("Remote level init named unknown dimension type {}", dimensionType);
            return;
        }
        addLive(dimension, createStreamedLevel(mc, connection, dimension, typeHolder));
    }

    private static RemoteLevel createStreamedLevel(Minecraft mc, ClientPacketListener connection,
                                                   ResourceLocation dimension,
                                                   Holder<DimensionType> typeHolder) {
        LevelRenderer renderer = new LevelRenderer(mc, mc.getEntityRenderDispatcher(),
            mc.getBlockEntityRenderDispatcher(), mc.renderBuffers());
        STREAMED_RENDERERS.add(renderer);
        ClientLevel.ClientLevelData data = new ClientLevel.ClientLevelData(
            mc.level.getDifficulty(), mc.level.getLevelData().isHardcore(), false);
        ClientLevel level = new ClientLevel(connection, data,
            ResourceKey.create(Registries.DIMENSION, dimension), typeHolder,
            REMOTE_VIEW_DISTANCE, 2, mc::getProfiler, renderer, false, 0L);
        level.setGameTime(mc.level.getGameTime());
        level.setDayTime(mc.level.getDayTime());
        renderer.setLevel(level);
        return new RemoteLevel(level, renderer, false);
    }

    public static void handleChunk(ResourceLocation dimension, ClientboundLevelChunkWithLightPacket packet) {
        List<RemoteLevel> levels = live(dimension);
        if (levels.isEmpty()) {
            return;
        }
        for (RemoteLevel remote : levels) {
            if (applyChunk(remote, packet)) {
                return;
            }
        }
        RemoteLevel opened = openRegion(dimension, levels);
        if (opened != null) {
            applyChunk(opened, packet);
        }
    }

    private static @Nullable RemoteLevel openRegion(ResourceLocation dimension, List<RemoteLevel> levels) {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener connection = mc.getConnection();
        if (mc.level == null || connection == null || levels.isEmpty()) {
            return null;
        }
        Holder<DimensionType> typeHolder = levels.get(0).level.dimensionTypeRegistration();
        if (levels.size() >= MAX_LIVE_REGIONS) {
            RemoteLevel evicted = levels.get(0);
            for (RemoteLevel candidate : levels) {
                if (candidate.lastDrawnGameTime < evicted.lastDrawnGameTime) {
                    evicted = candidate;
                }
            }
            retire(dimension, evicted);
        }
        RemoteLevel opened = createStreamedLevel(mc, connection, dimension, typeHolder);
        addLive(dimension, opened);
        Hexwright.LOGGER.info("[vault] {} now streams {} region(s) at once", dimension,
            live(dimension).size());
        return opened;
    }

    private static boolean applyChunk(RemoteLevel remote, ClientboundLevelChunkWithLightPacket packet) {
        int x = packet.getX();
        int z = packet.getZ();
        if (!remote.retained && remote.chunks.isEmpty()) {
            remote.level.getChunkSource().updateViewCenter(x, z);
        }
        var chunkData = packet.getChunkData();
        LevelChunk chunk = remote.level.getChunkSource().replaceWithPacketData(
            x, z, chunkData.getReadBuffer(), chunkData.getHeightmaps(),
            chunkData.getBlockEntitiesTagsConsumer(x, z));
        if (chunk == null) {
            return false;
        }
        applyLightData(remote.level, x, z, packet.getLightData());
        LevelLightEngine engine = remote.level.getChunkSource().getLightEngine();
        LevelChunkSection[] sections = chunk.getSections();
        ChunkPos pos = chunk.getPos();
        for (int i = 0; i < sections.length; i++) {
            int sectionY = remote.level.getSectionYFromSectionIndex(i);
            engine.updateSectionStatus(SectionPos.of(pos, sectionY), sections[i].hasOnlyAir());
            remote.level.setSectionDirtyWithNeighbors(x, sectionY, z);
        }
        remote.chunks.add(ChunkPos.asLong(x, z));
        remote.emptySinceGameTime = Long.MAX_VALUE;
        SodiumPortalCompat.onRemoteChunkLoaded(remote.level, x, z);
        return true;
    }

    private static void retire(ResourceLocation dimension, RemoteLevel outgoing) {
        List<RemoteLevel> fading = RETIRING.get(dimension);
        if (fading != null) {
            for (RemoteLevel previous : List.copyOf(fading)) {
                detach(RETIRING, dimension, previous);
                destroyLevel(dimension, previous);
            }
        }
        detach(LEVELS, dimension, outgoing);
        outgoing.retireDeadlineGameTime = gameTime() + PortalPair.OPEN_TICKS + FORGET_GRACE_TICKS;
        RETIRING.computeIfAbsent(dimension, key -> new ArrayList<>()).add(outgoing);
        Hexwright.LOGGER.info(
            "[vault] {} is at its region limit; the least recently drawn one retires with its fading pane",
            dimension);
    }

    private static void applyLightData(ClientLevel level, int x, int z, ClientboundLightUpdatePacketData data) {
        LevelLightEngine engine = level.getChunkSource().getLightEngine();
        readSectionList(x, z, engine, LightLayer.SKY,
            data.getSkyYMask(), data.getEmptySkyYMask(), data.getSkyUpdates().iterator());
        readSectionList(x, z, engine, LightLayer.BLOCK,
            data.getBlockYMask(), data.getEmptyBlockYMask(), data.getBlockUpdates().iterator());
        engine.setLightEnabled(new ChunkPos(x, z), true);
    }

    private static void readSectionList(int x, int z, LevelLightEngine engine, LightLayer layer,
                                        BitSet mask, BitSet emptyMask, Iterator<byte[]> updates) {
        for (int i = 0; i < engine.getLightSectionCount(); i++) {
            int sectionY = engine.getMinLightSection() + i;
            boolean hasData = mask.get(i);
            boolean isEmpty = emptyMask.get(i);
            if (hasData || isEmpty) {
                engine.queueSectionData(layer, SectionPos.of(x, sectionY, z),
                    hasData ? new DataLayer(updates.next().clone()) : new DataLayer());
            }
        }
    }

    private static final class DeferredForget {
        final Set<Long> chunks = new HashSet<>();
        long deadlineGameTime;
    }

    private static final Map<ResourceLocation, DeferredForget> DEFERRED_FORGETS = new HashMap<>();

    private static final int FORGET_GRACE_TICKS = 4;

    private static final int EMPTY_REGION_GRACE_TICKS = 100;

    public static void handleForget(ResourceLocation dimension, long[] chunks) {
        if (!tracked(dimension)) {
            return;
        }
        if (ClientPortalManager.closingPaneNeeds(dimension)) {
            DeferredForget pending = DEFERRED_FORGETS.computeIfAbsent(dimension, key -> new DeferredForget());
            for (long packed : chunks) {
                pending.chunks.add(packed);
            }
            pending.deadlineGameTime = gameTime() + PortalPair.OPEN_TICKS + FORGET_GRACE_TICKS;
            return;
        }
        applyForget(dimension, chunks);
    }

    public static void releaseDeferredForgets() {
        if (DEFERRED_FORGETS.isEmpty()) {
            return;
        }
        long now = gameTime();
        Iterator<Map.Entry<ResourceLocation, DeferredForget>> pending = DEFERRED_FORGETS.entrySet().iterator();
        List<Runnable> due = new ArrayList<>();
        while (pending.hasNext()) {
            Map.Entry<ResourceLocation, DeferredForget> entry = pending.next();
            if (now < entry.getValue().deadlineGameTime
                && ClientPortalManager.closingPaneNeeds(entry.getKey())) {
                continue;
            }
            ResourceLocation dimension = entry.getKey();
            long[] chunks = entry.getValue().chunks.stream().mapToLong(Long::longValue).toArray();
            pending.remove();
            due.add(() -> applyForget(dimension, chunks));
        }
        due.forEach(Runnable::run);
    }

    private static long gameTime() {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? 0L : level.getGameTime();
    }

    private static void applyForget(ResourceLocation dimension, long[] chunks) {
        boolean claimed = false;
        for (RemoteLevel retiring : List.copyOf(RETIRING.getOrDefault(dimension, List.of()))) {
            claimed |= dropChunks(retiring, chunks);
            if (retiring.retained || retiring.chunks.isEmpty()) {
                detach(RETIRING, dimension, retiring);
                destroyLevel(dimension, retiring);
                claimed = true;
            }
        }
        List<RemoteLevel> levels = List.copyOf(live(dimension));
        boolean lastRegion = levels.size() <= 1;
        for (RemoteLevel level : levels) {
            if (level.retained && !claimed && lastRegion) {
                destroyLive(dimension, level);
                continue;
            }
            boolean owned = dropChunks(level, chunks);
            if (level.chunks.isEmpty() && (owned || (!claimed && lastRegion))) {
                destroyLive(dimension, level);
            }
        }
    }

    private static boolean dropChunks(RemoteLevel remote, long[] chunks) {
        boolean owned = false;
        for (long packed : chunks) {
            if (remote.chunks.remove(packed)) {
                owned = true;
                SodiumPortalCompat.onRemoteChunkUnloaded(
                    remote.level, ChunkPos.getX(packed), ChunkPos.getZ(packed));
                remote.level.getChunkSource().drop(ChunkPos.getX(packed), ChunkPos.getZ(packed));
            }
        }
        if (owned && remote.chunks.isEmpty()) {
            remote.emptySinceGameTime = gameTime();
        }
        return owned;
    }

    public static void handleBlockUpdate(ResourceLocation dimension, BlockPos pos, int stateId,
                                         @Nullable CompoundTag blockEntityTag) {
        RemoteLevel remote = liveHolding(dimension, pos.getX() >> 4, pos.getZ() >> 4);
        if (remote == null) {
            return;
        }
        remote.level.setBlock(pos, Block.stateById(stateId), 19);
        if (blockEntityTag != null) {
            BlockEntity blockEntity = remote.level.getBlockEntity(pos);
            if (blockEntity != null) {
                blockEntity.load(blockEntityTag);
            }
        }
    }

    public static void handleDestroyProgress(ResourceLocation dimension, int breakerId, BlockPos pos, int stage) {
        RemoteLevel remote = liveHolding(dimension, pos.getX() >> 4, pos.getZ() >> 4);
        if (remote != null) {
            remote.renderer.destroyBlockProgress(breakerId, pos, stage);
        }
    }

    public static void handleEntities(ResourceLocation dimension, ChunkPos regionChunk,
                                      List<EntitySnapshot> snapshots) {
        Minecraft mc = Minecraft.getInstance();
        RemoteLevel remote = liveHolding(dimension, regionChunk.x, regionChunk.z);
        if (remote == null) {
            return;
        }
        Set<Integer> seen = new HashSet<>();
        for (EntitySnapshot snapshot : snapshots) {
            seen.add(snapshot.id());
            Entity entity = remote.entities.get(snapshot.id());
            if (entity == null || entity.isRemoved()) {
                entity = spawnMirror(mc, remote, snapshot);
                if (entity == null) {
                    continue;
                }
            }
            entity.lerpTo(snapshot.x(), snapshot.y(), snapshot.z(), snapshot.yaw(), snapshot.pitch(), 3, true);
            if (entity instanceof LivingEntity living) {
                living.lerpHeadTo(snapshot.headYaw(), 3);
            }
            if (!snapshot.data().isEmpty()) {
                entity.getEntityData().assignValues(snapshot.data());
            }
            if (entity instanceof LivingEntity living) {
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    ItemStack worn = snapshot.equipment().get(slot.ordinal());
                    if (!ItemStack.matches(living.getItemBySlot(slot), worn)) {
                        living.setItemSlot(slot, worn.copy());
                    }
                }
            }
        }
        var iterator = remote.entities.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!seen.contains(entry.getKey())) {
                remote.level.removeEntity(entry.getKey(), Entity.RemovalReason.DISCARDED);
                iterator.remove();
            }
        }
    }

    private static @Nullable Entity spawnMirror(Minecraft mc, RemoteLevel remote, EntitySnapshot snapshot) {
        Entity entity;
        if (snapshot.player()) {
            ClientPacketListener connection = mc.getConnection();
            PlayerInfo info = connection == null ? null : connection.getPlayerInfo(snapshot.uuid());
            GameProfile profile = info != null ? info.getProfile() : new GameProfile(snapshot.uuid(), "vault_visitor");
            entity = new RemotePlayer(remote.level, profile);
        } else {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.byId(snapshot.typeId());
            entity = type.create(remote.level);
            if (entity == null) {
                return null;
            }
        }
        entity.setId(snapshot.id());
        entity.setUUID(snapshot.uuid());
        entity.moveTo(snapshot.x(), snapshot.y(), snapshot.z(), snapshot.yaw(), snapshot.pitch());
        entity.setYHeadRot(snapshot.headYaw());
        if (entity instanceof AbstractClientPlayer player) {
            remote.level.addPlayer(snapshot.id(), player);
        } else {
            remote.level.putNonPlayerEntity(snapshot.id(), entity);
        }
        remote.entities.put(snapshot.id(), entity);
        return entity;
    }


    private static void tick(Minecraft mc) {
        if (LEVELS.isEmpty() && RETIRING.isEmpty()) {
            return;
        }
        if (mc.level == null) {
            destroyAll();
            return;
        }
        ResourceLocation currentDimension = mc.level.dimension().location();
        for (ResourceLocation dimension : List.copyOf(RETIRING.keySet())) {
            for (RemoteLevel retiring : List.copyOf(RETIRING.getOrDefault(dimension, List.of()))) {
                if (dimension.equals(currentDimension) || gameTime() >= retiring.retireDeadlineGameTime) {
                    detach(RETIRING, dimension, retiring);
                    destroyLevel(dimension, retiring);
                    continue;
                }
                retiring.level.setGameTime(mc.level.getGameTime());
                retiring.level.setDayTime(mc.level.getDayTime());
            }
        }
        for (ResourceLocation dimension : List.copyOf(LEVELS.keySet())) {
            for (RemoteLevel remote : List.copyOf(live(dimension))) {
                if (dimension.equals(currentDimension)) {
                    destroyLive(dimension, remote);
                    continue;
                }
                if (!remote.retained && remote.chunks.isEmpty()
                    && gameTime() - remote.emptySinceGameTime > EMPTY_REGION_GRACE_TICKS) {
                    destroyLive(dimension, remote);
                    continue;
                }
                remote.level.setGameTime(mc.level.getGameTime());
                remote.level.setDayTime(mc.level.getDayTime());
                for (Entity entity : List.copyOf(remote.entities.values())) {
                    try {
                        entity.setOldPosAndRot();
                        entity.tickCount++;
                        entity.tick();
                    } catch (RuntimeException e) {
                        remote.level.removeEntity(entity.getId(), Entity.RemovalReason.DISCARDED);
                        remote.entities.remove(entity.getId());
                    }
                }
            }
        }
    }


    public static boolean isReady(ResourceKey<Level> dimension, Vec3 anchor) {
        return levelHolding(dimension.location(), anchor) != null;
    }

    private static @Nullable RemoteLevel levelHolding(ResourceLocation dimension, Vec3 anchor) {
        int chunkX = net.minecraft.util.Mth.floor(anchor.x) >> 4;
        int chunkZ = net.minecraft.util.Mth.floor(anchor.z) >> 4;
        RemoteLevel found = liveHolding(dimension, chunkX, chunkZ);
        if (found != null) {
            return found;
        }
        for (RemoteLevel retiring : RETIRING.getOrDefault(dimension, List.of())) {
            if (holds(retiring, chunkX, chunkZ)) {
                return retiring;
            }
        }
        return null;
    }

    private static @Nullable RemoteLevel liveHolding(ResourceLocation dimension, int chunkX, int chunkZ) {
        for (RemoteLevel remote : live(dimension)) {
            if (holds(remote, chunkX, chunkZ)) {
                return remote;
            }
        }
        return null;
    }

    private static boolean holds(@Nullable RemoteLevel remote, int chunkX, int chunkZ) {
        if (remote == null) {
            return false;
        }
        return remote.retained
            ? remote.level.hasChunk(chunkX, chunkZ)
            : remote.chunks.contains(ChunkPos.asLong(chunkX, chunkZ));
    }

    public static boolean isRemotePassActive() {
        return remotePassActive;
    }

    public static @Nullable ClientLevel remoteLevel(ResourceLocation dimension, Vec3 anchor) {
        RemoteLevel remote = liveHolding(dimension,
            net.minecraft.util.Mth.floor(anchor.x) >> 4, net.minecraft.util.Mth.floor(anchor.z) >> 4);
        return remote == null ? null : remote.level;
    }

    public static @Nullable ResourceLocation activeRemoteDimension() {
        return remotePassActive && activeRemote != null
            ? activeRemote.level.dimension().location() : null;
    }

    public static @Nullable ClientLevel activeRemoteClientLevel() {
        return remotePassActive && activeRemote != null ? activeRemote.level : null;
    }

    public static boolean shouldSkipLoadingScreen() {
        boolean skip = skipNextLoadingScreen;
        skipNextLoadingScreen = false;
        return skip;
    }

    private static boolean skipNextLoadingScreen;

    public static double remoteFogEnd(Vec3 cameraPos) {
        RemoteLevel remote = activeRemote;
        if (remote == null || remote.retained || remote.chunks.size() <= 1) {
            return -1.0;
        }
        if (remote.level.dimension().equals(com.bluup.hexwright.server.vault.VaultDimension.KEY)) {
            return -1.0;
        }
        int minChunkX = Integer.MAX_VALUE;
        int maxChunkX = Integer.MIN_VALUE;
        int minChunkZ = Integer.MAX_VALUE;
        int maxChunkZ = Integer.MIN_VALUE;
        for (long packed : remote.chunks) {
            int chunkX = ChunkPos.getX(packed);
            int chunkZ = ChunkPos.getZ(packed);
            minChunkX = Math.min(minChunkX, chunkX);
            maxChunkX = Math.max(maxChunkX, chunkX);
            minChunkZ = Math.min(minChunkZ, chunkZ);
            maxChunkZ = Math.max(maxChunkZ, chunkZ);
        }
        double westward = cameraPos.x - (minChunkX << 4);
        double eastward = ((maxChunkX + 1) << 4) - cameraPos.x;
        double northward = cameraPos.z - (minChunkZ << 4);
        double southward = ((maxChunkZ + 1) << 4) - cameraPos.z;
        double distance = Math.min(Math.min(westward, eastward), Math.min(northward, southward));
        if (distance <= 0.0) {
            distance = Math.max(Math.max(westward, eastward), Math.max(northward, southward));
        }
        return net.minecraft.util.Mth.clamp(distance, MIN_FOG_END, MAX_FOG_END);
    }

    public static boolean beginPass(ResourceKey<Level> dimension, Vec3 foldedCamera, Vec3 anchor) {
        if (remotePassActive) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        RemoteLevel remote = levelHolding(dimension.location(), anchor);
        if (remote == null || mc.level == null) {
            return false;
        }
        long cameraChunk = ChunkPos.asLong(
            net.minecraft.util.Mth.floor(foldedCamera.x) >> 4,
            net.minecraft.util.Mth.floor(foldedCamera.z) >> 4);
        if (cameraChunk != remote.lastCameraChunk) {
            remote.lastCameraChunk = cameraChunk;
            ((LevelRendererAccessor) remote.renderer).hexwright$viewArea()
                .repositionCamera(foldedCamera.x, foldedCamera.z);
        }
        remote.lastDrawnGameTime = gameTime();
        savedMainLevel = mc.level;
        savedMainRenderer = mc.levelRenderer;
        mc.level = remote.level;
        ((MinecraftPortalAccessor) (Object) mc).hexwright$setLevelRenderer(remote.renderer);
        activeRemote = remote;
        remotePassActive = true;
        return true;
    }

    public static void endPass() {
        if (!remotePassActive) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.level = savedMainLevel;
        if (savedMainRenderer != null) {
            ((MinecraftPortalAccessor) (Object) mc).hexwright$setLevelRenderer(savedMainRenderer);
        }
        savedMainLevel = null;
        savedMainRenderer = null;
        activeRemote = null;
        remotePassActive = false;
    }


    private static void destroyLive(ResourceLocation dimension, RemoteLevel remote) {
        detach(LEVELS, dimension, remote);
        destroyLevel(dimension, remote);
    }

    private static void destroyLevel(ResourceLocation dimension, RemoteLevel remote) {
        if (!tracked(dimension)) {
            DEFERRED_FORGETS.remove(dimension);
        }
        for (Integer id : remote.entities.keySet()) {
            remote.level.removeEntity(id, Entity.RemovalReason.DISCARDED);
        }
        remote.entities.clear();
        STREAMED_RENDERERS.remove(remote.renderer);
        remote.renderer.setLevel(null);
        if (remote.retained && Minecraft.getInstance().getConnection() != null) {
            HexwrightNetworking.sendVaultRetained(dimension, false);
        }
    }

    private static void destroyEvery(ResourceLocation dimension) {
        for (RemoteLevel retiring : List.copyOf(RETIRING.getOrDefault(dimension, List.of()))) {
            detach(RETIRING, dimension, retiring);
            destroyLevel(dimension, retiring);
        }
        for (RemoteLevel remote : List.copyOf(live(dimension))) {
            destroyLive(dimension, remote);
        }
    }

    public static void destroyAll() {
        DEFERRED_FORGETS.clear();
        for (ResourceLocation dimension : List.copyOf(RETIRING.keySet())) {
            destroyEvery(dimension);
        }
        for (ResourceLocation dimension : List.copyOf(LEVELS.keySet())) {
            destroyEvery(dimension);
        }
    }
}
