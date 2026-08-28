package com.bluup.hexwright.client.portal;

import com.bluup.hexwright.client.block.TrackedBlockEffect;
import com.bluup.hexwright.client.block.CrucibleFlameVisualClient;
import com.bluup.hexwright.client.block.ManifoldVaultVisualClient;
import com.bluup.hexwright.client.block.ResonanceTowerVisualClient;
import com.bluup.hexwright.server.block.HexwrightBlockStates;
import com.bluup.hexwright.server.block.HexwrightBlocks;
import com.bluup.hexwright.server.portal.PortalPair;
import com.lowdragmc.lowdraglib.client.scene.ParticleManager;
import com.lowdragmc.lowdraglib.utils.DummyWorld;
import com.lowdragmc.photon.client.PhotonParticleManager;
import com.lowdragmc.photon.client.fx.BlockEffect;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

public final class RemotePhotonVisuals {

    private static final int SCAN_CHUNK_RADIUS = 2;

    private static final class RemoteFxLevel extends DummyWorld {
        private final ClientLevel remote;

        RemoteFxLevel(ClientLevel remote) {
            super(remote);
            this.remote = remote;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return remote.getBlockState(pos);
        }
    }

    private static final class State {
        final RemoteFxLevel fxLevel;

        final ParticleManager particles = new PhotonParticleManager();
        final Map<BlockPos, BlockEffect> effects = new HashMap<>();

        State(ClientLevel remote) {
            fxLevel = new RemoteFxLevel(remote);
            fxLevel.setParticleManager(particles);
            particles.setLevel(fxLevel);
        }

        void destroyEffects() {
            for (BlockEffect effect : effects.values()) {
                TrackedBlockEffect.destroy(effect);
            }
            effects.clear();
        }
    }

    private static final Map<ResourceLocation, State> STATES = new HashMap<>();

    private RemotePhotonVisuals() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(RemotePhotonVisuals::clear));
    }

    public static void clear() {
        for (State state : STATES.values()) {
            state.destroyEffects();
        }
        STATES.clear();
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) {
            if (mc.level == null && !STATES.isEmpty()) {
                clear();
            }
            return;
        }

        Map<ResourceLocation, Set<Vec3>> anchors = new HashMap<>();
        for (ClientPortalManager.Entry entry : ClientPortalManager.entries()) {
            PortalPair pair = entry.pair();
            if (!pair.isCrossDimensional()) {
                continue;
            }
            for (int side = 0; side < 2; side++) {
                ResourceKey<Level> dim = pair.dimension(side);
                if (dim == null || ClientPortalManager.sideIsLocal(pair, side)) {
                    continue;
                }
                anchors.computeIfAbsent(dim.location(), key -> new HashSet<>())
                    .add(pair.window(side).center());
            }
        }

        Iterator<Map.Entry<ResourceLocation, State>> states = STATES.entrySet().iterator();
        while (states.hasNext()) {
            Map.Entry<ResourceLocation, State> entry = states.next();
            if (!anchors.containsKey(entry.getKey())
                || RemoteLevelManager.remoteLevel(entry.getKey()) != entry.getValue().fxLevel.remote) {
                entry.getValue().destroyEffects();
                states.remove();
            }
        }

        for (Map.Entry<ResourceLocation, Set<Vec3>> viewed : anchors.entrySet()) {
            ClientLevel remote = RemoteLevelManager.remoteLevel(viewed.getKey());
            if (remote == null) {
                continue;
            }
            State state = STATES.computeIfAbsent(viewed.getKey(), key -> new State(remote));
            reconcile(state, remote, viewed.getValue());
            state.particles.tick();
        }
    }

    private static void reconcile(State state, ClientLevel remote, Set<Vec3> anchors) {
        Map<BlockPos, BiFunction<Level, BlockPos, BlockEffect>> wanted = new HashMap<>();
        Set<Long> visitedChunks = new HashSet<>();
        for (Vec3 anchor : anchors) {
            ChunkPos center = new ChunkPos(BlockPos.containing(anchor));
            for (int dx = -SCAN_CHUNK_RADIUS; dx <= SCAN_CHUNK_RADIUS; dx++) {
                for (int dz = -SCAN_CHUNK_RADIUS; dz <= SCAN_CHUNK_RADIUS; dz++) {
                    if (!visitedChunks.add(ChunkPos.asLong(center.x + dx, center.z + dz))) {
                        continue;
                    }
                    if (!(remote.getChunk(center.x + dx, center.z + dz, ChunkStatus.FULL, false)
                        instanceof LevelChunk chunk)) {
                        continue;
                    }
                    for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                        BiFunction<Level, BlockPos, BlockEffect> factory =
                            effectFor(remote, entry.getKey(), entry.getValue());
                        if (factory != null) {
                            wanted.put(entry.getKey().immutable(), factory);
                        }
                    }
                }
            }
        }

        Iterator<Map.Entry<BlockPos, BlockEffect>> live = state.effects.entrySet().iterator();
        while (live.hasNext()) {
            Map.Entry<BlockPos, BlockEffect> entry = live.next();
            FXRuntime runtime = entry.getValue().getRuntime();
            if (!wanted.containsKey(entry.getKey())) {
                TrackedBlockEffect.destroy(entry.getValue());
                live.remove();
            } else if (runtime == null || !runtime.isAlive()) {
                live.remove();
            }
        }
        for (Map.Entry<BlockPos, BiFunction<Level, BlockPos, BlockEffect>> entry : wanted.entrySet()) {
            if (!state.effects.containsKey(entry.getKey())) {
                BlockEffect effect = entry.getValue().apply(state.fxLevel, entry.getKey());
                if (effect != null) {
                    state.effects.put(entry.getKey(), effect);
                }
            }
        }
    }

    private static @Nullable BiFunction<Level, BlockPos, BlockEffect> effectFor(
        ClientLevel remote, BlockPos pos, BlockEntity blockEntity) {
        BlockState blockState = remote.getBlockState(pos);
        boolean active = blockState.hasProperty(HexwrightBlockStates.ACTIVE)
            && blockState.getValue(HexwrightBlockStates.ACTIVE);
        if (blockState.getBlock() == HexwrightBlocks.CRUCIBLE_BLOCK && active) {
            return CrucibleFlameVisualClient::startEffect;
        }
        if (blockState.getBlock() == HexwrightBlocks.RESONANCE_TOWER_BLOCK && active) {
            return ResonanceTowerVisualClient::startEffect;
        }
        if (blockState.getBlock() == HexwrightBlocks.MANIFOLD_VAULT_BLOCK
            && blockEntity instanceof net.minecraft.world.Container container && !container.isEmpty()) {
            return ManifoldVaultVisualClient::startEffect;
        }
        return null;
    }

    public static void render(PoseStack poseStack, Camera camera, float partialTick) {
        ResourceLocation dimension = RemoteLevelManager.activeRemoteDimension();
        if (dimension == null) {
            return;
        }
        State state = STATES.get(dimension);
        if (state != null) {
            state.particles.render(poseStack, camera, partialTick);
        }
    }
}
