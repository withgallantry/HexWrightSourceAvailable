package com.bluup.hexwright.server.worldgen.ruinedportal;

import com.bluup.hexwright.server.block.Outcome;
import com.bluup.hexwright.server.sound.HexwrightSoundEvents;
import com.bluup.hexwright.server.block.RuinedPortalFrameBlockEntity;
import com.bluup.hexwright.server.vault.VaultDimension;
import com.bluup.hexwright.server.worldgen.arena.ArenaPiece;
import com.bluup.hexwright.server.worldgen.arena.GolemClears;
import com.bluup.hexwright.server.worldgen.decadentvault.DecadentVaultRegistry;
import com.bluup.hexwright.server.worldgen.dungeon.DungeonClears;
import com.bluup.hexwright.server.worldgen.dungeon.DungeonPiece;
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
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

public final class RuinedPortalOutcomeResolver {

    private static final int WEIGHT_PERIL = 77;
    private static final int WEIGHT_DUNGEON = 12;
    private static final int WEIGHT_GOLEM = 6;
    private static final int WEIGHT_VAULT = 5;

    private static final int SEARCH_CHUNKS = 120;

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
        }
        teleport(player, binding);
    }

    private static RuinedPortalFrameBlockEntity.Binding resolve(ServerLevel level, BlockPos portalPos,
                                                                 RandomSource random) {
        int roll = random.nextInt(WEIGHT_PERIL + WEIGHT_DUNGEON + WEIGHT_GOLEM + WEIGHT_VAULT);
        boolean tryDungeon = roll >= WEIGHT_PERIL;
        boolean tryGolem = roll >= WEIGHT_PERIL + WEIGHT_DUNGEON;
        boolean tryVault = roll >= WEIGHT_PERIL + WEIGHT_DUNGEON + WEIGHT_GOLEM;

        if (tryVault) {
            return vaultBinding(level, portalPos);
        }
        if (tryGolem) {
            RuinedPortalFrameBlockEntity.Binding golem = golemBinding(level, portalPos);
            return golem != null ? golem : vaultBinding(level, portalPos);
        }
        if (tryDungeon) {
            RuinedPortalFrameBlockEntity.Binding dungeon = dungeonBinding(level, portalPos);
            if (dungeon != null) {
                return dungeon;
            }
            RuinedPortalFrameBlockEntity.Binding golem = golemBinding(level, portalPos);
            return golem != null ? golem : vaultBinding(level, portalPos);
        }
        return RuinedPortalPerils.conjure(level, portalPos, random);
    }

    @org.jetbrains.annotations.Nullable
    private static RuinedPortalFrameBlockEntity.Binding dungeonBinding(ServerLevel level, BlockPos portalPos) {
        Holder<Structure> structure = holderOf(level, com.bluup.hexwright.server.worldgen.HexwrightWorldgen.DEEP_DUNGEON_KEY);
        if (structure == null) {
            return null;
        }
        StructureStart start = nearestStart(level, structure, portalPos);
        if (start == null || DungeonClears.get(level.getServer())
            .isCleared(level.dimension(), start.getChunkPos().toLong())) {
            return null;
        }
        for (StructurePiece piece : start.getPieces()) {
            if (piece instanceof DungeonPiece room && room.anchorRune() != null) {
                BlockPos spot = room.anchorSpot();
                if (spot != null) {
                    return new RuinedPortalFrameBlockEntity.Binding(
                        Outcome.DUNGEON, level.dimension(), spot, start.getChunkPos().toLong());
                }
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
        BlockPos landing = binding.getOutcome() == Outcome.DUNGEON
            ? RuinedPortalLanding.onAnchor(destination, binding.getPos())
            : binding.getPos();
        player.teleportTo(destination,
            landing.getX() + 0.5, landing.getY(), landing.getZ() + 0.5,
            player.getYRot(), player.getXRot());
        player.level().playSound(null, player.blockPosition(),
            HexwrightSoundEvents.ruinedPortalTeleport(), SoundSource.PLAYERS, 1.0f, 1.0f);
        player.sendSystemMessage(outcomeMessage(binding));
    }

    private static MutableComponent outcomeMessage(RuinedPortalFrameBlockEntity.Binding binding) {
        String key = switch (binding.getOutcome()) {
            case DUNGEON -> "message.hexwright.ruined_portal.dungeon";
            case GOLEM -> "message.hexwright.ruined_portal.golem";
            case VAULT -> "message.hexwright.ruined_portal.vault";
            default -> "message.hexwright.ruined_portal.peril";
        };
        return Component.translatable(key).withStyle(ChatFormatting.DARK_PURPLE);
    }
}
