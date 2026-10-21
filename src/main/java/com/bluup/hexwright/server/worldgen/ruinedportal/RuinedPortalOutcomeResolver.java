package com.bluup.hexwright.server.worldgen.ruinedportal;

import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.server.block.HexwrightBlocks;
import com.bluup.hexwright.server.block.Outcome;
import com.bluup.hexwright.server.sound.HexwrightSoundEvents;
import com.bluup.hexwright.server.block.RuinedPortalFrameBlockEntity;
import com.bluup.hexwright.server.vault.VaultDimension;
import com.bluup.hexwright.server.worldgen.arena.ArenaPiece;
import com.bluup.hexwright.server.worldgen.arena.GolemClears;
import com.bluup.hexwright.server.worldgen.decadentvault.DecadentVaultRegistry;
import com.mojang.datafixers.util.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;

public final class RuinedPortalOutcomeResolver {

    private static final int WEIGHT_RIFT = 70;
    private static final int WEIGHT_PERIL = 20;
    private static final int WEIGHT_GOLEM = 6;
    private static final int WEIGHT_VAULT = 4;

    private static final int SEARCH_CHUNKS = 120;

    private static final int RIFT_MIN_DISTANCE = 256;
    private static final int RIFT_MAX_DISTANCE = 1536;
    private static final int RIFT_SEARCH_CHUNKS = 32;
    private static final int RIFT_ATTEMPTS = 4;
    private static final int FRAME_SCAN_DEPTH = 32;

    private RuinedPortalOutcomeResolver() {
    }

    public static void trigger(ServerPlayer player, RuinedPortalFrameBlockEntity frame) {
        ServerLevel level = (ServerLevel) player.level();
        RuinedPortalFrameBlockEntity.Binding binding = frame.getBinding();
        if (binding == null) {
            binding = resolve(level, frame.getBlockPos(), player.getRandom());
            frame.bind(binding);
        } else if (isStrandedVault(binding)) {
            binding = vaultBinding(level, frame.getBlockPos());
            frame.bind(binding);
        } else if (isRetired(binding)) {
            binding = resolve(level, frame.getBlockPos(), player.getRandom());
            frame.bind(binding);
        }
        teleport(player, binding);
    }

    private static RuinedPortalFrameBlockEntity.Binding resolve(ServerLevel level, BlockPos portalPos,
                                                                 RandomSource random) {
        int roll = random.nextInt(WEIGHT_RIFT + WEIGHT_PERIL + WEIGHT_GOLEM + WEIGHT_VAULT);
        if (roll < WEIGHT_RIFT) {
            RuinedPortalFrameBlockEntity.Binding rift = riftBinding(level, portalPos, random);
            return rift != null ? rift : RuinedPortalPerils.conjure(level, portalPos, random);
        }
        roll -= WEIGHT_RIFT;
        if (roll < WEIGHT_PERIL) {
            return RuinedPortalPerils.conjure(level, portalPos, random);
        }
        roll -= WEIGHT_PERIL;
        if (roll < WEIGHT_GOLEM) {
            RuinedPortalFrameBlockEntity.Binding golem = golemBinding(level, portalPos);
            if (golem != null) {
                return golem;
            }
        }
        return vaultBinding(level, portalPos);
    }

    @org.jetbrains.annotations.Nullable
    private static RuinedPortalFrameBlockEntity.Binding riftBinding(ServerLevel level, BlockPos portalPos,
                                                                     RandomSource random) {
        Holder<Structure> structure = holderOf(level, com.bluup.hexwright.server.worldgen.HexwrightWorldgen.RUINED_PORTAL_KEY);
        if (structure == null) {
            return null;
        }
        ChunkPos home = new ChunkPos(portalPos);
        for (int attempt = 0; attempt < RIFT_ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            int distance = RIFT_MIN_DISTANCE + random.nextInt(RIFT_MAX_DISTANCE - RIFT_MIN_DISTANCE);
            BlockPos aim = portalPos.offset(
                (int) Math.round(Math.cos(angle) * distance), 0, (int) Math.round(Math.sin(angle) * distance));
            Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
                .findNearestMapStructure(level, HolderSet.direct(structure), aim, RIFT_SEARCH_CHUNKS, false);
            if (found == null) {
                continue;
            }
            ChunkPos site = new ChunkPos(found.getFirst());
            if (site.equals(home)) {
                continue;
            }
            BlockPos target = frameIn(level, site);
            if (target != null) {
                HexwrightDebug.log(HexwrightDebug.PORTAL, "[ruined portal] {} linked to the ruin at {}", portalPos, target);
                return new RuinedPortalFrameBlockEntity.Binding(Outcome.RIFT, level.dimension(), target, null);
            }
        }
        HexwrightDebug.log(HexwrightDebug.PORTAL, "[ruined portal] {} found no ruin to link to", portalPos);
        return null;
    }

    @org.jetbrains.annotations.Nullable
    private static BlockPos frameIn(ServerLevel level, ChunkPos site) {
        level.getChunk(site.x, site.z);
        int x = site.getMinBlockX();
        int z = site.getMinBlockZ();
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 4;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = top; y > top - FRAME_SCAN_DEPTH; y--) {
            if (level.getBlockState(cursor.set(x, y, z)).is(HexwrightBlocks.RUINED_PORTAL_FRAME_BLOCK)) {
                return cursor.immutable();
            }
        }
        return null;
    }

    @org.jetbrains.annotations.Nullable
    private static RuinedPortalFrameBlockEntity.Binding golemBinding(ServerLevel level, BlockPos portalPos) {
        Holder<Structure> structure = holderOf(level, com.bluup.hexwright.server.worldgen.HexwrightWorldgen.ANCIENT_ARENA_KEY);
        if (structure == null) {
            return null;
        }
        StructureStart start = nearestStart(level, structure, portalPos);
        if (start == null || GolemClears.get(level.getServer())
            .isCleared(level.dimension(), start.getChunkPos().toLong())) {
            return null;
        }
        for (StructurePiece piece : start.getPieces()) {
            if (piece instanceof ArenaPiece arena) {
                BlockPos spot = arena.entryPoint();
                if (spot != null) {
                    return new RuinedPortalFrameBlockEntity.Binding(
                        Outcome.GOLEM, level.dimension(), spot, start.getChunkPos().toLong());
                }
            }
        }
        return null;
    }

    private static RuinedPortalFrameBlockEntity.Binding vaultBinding(ServerLevel level, BlockPos portalPos) {
        BlockPos entrance = DecadentVaultRegistry.get(level.getServer()).createFor(level, portalPos);
        return new RuinedPortalFrameBlockEntity.Binding(
            Outcome.VAULT, level.dimension(), entrance, null);
    }

    private static boolean isStrandedVault(RuinedPortalFrameBlockEntity.Binding binding) {
        return binding.getOutcome() == Outcome.VAULT
            && binding.getDimension().equals(VaultDimension.KEY);
    }

    private static boolean isRetired(RuinedPortalFrameBlockEntity.Binding binding) {
        return switch (binding.getOutcome()) {
            case PERIL_LAVA, PERIL_NETHER_LAVA, DUNGEON -> true;
            default -> false;
        };
    }

    @org.jetbrains.annotations.Nullable
    private static Holder<Structure> holderOf(ServerLevel level, ResourceKey<Structure> key) {
        return level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolder(key).orElse(null);
    }

    @org.jetbrains.annotations.Nullable
    private static StructureStart nearestStart(ServerLevel level, Holder<Structure> structure, BlockPos pos) {
        Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
            .findNearestMapStructure(level, HolderSet.direct(structure), pos, SEARCH_CHUNKS, false);
        if (found == null) {
            return null;
        }
        ChunkPos site = new ChunkPos(found.getFirst());
        ChunkAccess chunk = level.getChunk(site.x, site.z, ChunkStatus.STRUCTURE_STARTS);
        StructureStart start = level.structureManager()
            .getStartForStructure(SectionPos.bottomOf(chunk), structure.value(), chunk);
        return start != null && start.isValid() ? start : null;
    }

    private static void teleport(ServerPlayer player, RuinedPortalFrameBlockEntity.Binding binding) {
        ServerLevel destination = player.getServer().getLevel(binding.getDimension());
        if (destination == null) {
            return;
        }
        destination.getChunk(binding.getPos().getX() >> 4, binding.getPos().getZ() >> 4);
        Vec3 landing = binding.getOutcome() == Outcome.RIFT
            ? RuinedPortalManager.landingBeside(destination, binding.getPos())
            : Vec3.atBottomCenterOf(binding.getPos());
        player.teleportTo(destination, landing.x, landing.y, landing.z, player.getYRot(), player.getXRot());
        player.level().playSound(null, player.blockPosition(),
            HexwrightSoundEvents.ruinedPortalTeleport(), SoundSource.PLAYERS, 1.0f, 1.0f);
        player.sendSystemMessage(outcomeMessage(binding));
    }

    private static MutableComponent outcomeMessage(RuinedPortalFrameBlockEntity.Binding binding) {
        String key = switch (binding.getOutcome()) {
            case RIFT -> "message.hexwright.ruined_portal.rift";
            case GOLEM -> "message.hexwright.ruined_portal.golem";
            case VAULT -> "message.hexwright.ruined_portal.vault";
            default -> "message.hexwright.ruined_portal.peril";
        };
        return Component.translatable(key).withStyle(ChatFormatting.DARK_PURPLE);
    }
}
