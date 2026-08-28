package com.bluup.hexwright.server.worldgen.dungeon;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.block.ResonantAnchorBlockEntity;
import com.bluup.hexwright.server.block.ResonantAnchorRegistry;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jetbrains.annotations.Nullable;

import ram.talia.moreiotas.api.casting.iota.StringIota;

public final class DungeonGroups {

    public static final String LETTERS = "bcdefghijklmnopqstuxz";

    private static final int SEARCH_CHUNKS = 120;

    private static final ResourceKey<Structure> DEEP_DUNGEON =
        ResourceKey.create(Registries.STRUCTURE, Hexwright.id("deep_dungeon"));

    private DungeonGroups() {
    }

    public static String runeForSite(long seed, ChunkPos site) {
        long hash = seed ^ (site.x * 341873128712L + site.z * 132897987541L);
        hash ^= hash >>> 33;
        hash *= 0xff51afd7ed558ccdL;
        hash ^= hash >>> 33;
        return String.valueOf(LETTERS.charAt((int) Math.floorMod(hash, LETTERS.length())));
    }

    public static String wordFor(long seed, String rune) {
        long hash = seed ^ (rune.charAt(0) * 0x9E3779B97F4A7C15L);
        hash += 0x9E3779B97F4A7C15L;
        hash = (hash ^ (hash >>> 30)) * 0xBF58476D1CE4E5B9L;
        hash = (hash ^ (hash >>> 27)) * 0x94D049BB133111EBL;
        hash ^= hash >>> 31;
        return rune + "-" + String.format("%08x", (int) (hash >>> 32));
    }

    public static int runeIndexOf(String rune) {
        return rune == null || rune.length() != 1 ? -1 : LETTERS.indexOf(rune.charAt(0));
    }

    public static String randomRune(RandomSource random) {
        return String.valueOf(LETTERS.charAt(random.nextInt(LETTERS.length())));
    }

    public static String registryKey(String word) {
        return ResonantAnchorBlockEntity.keyOf(StringIota.makeUnchecked(word));
    }

    public static @Nullable String nearestRune(ServerLevel level, BlockPos pos) {
        Holder<Structure> structure = level.registryAccess()
            .registryOrThrow(Registries.STRUCTURE)
            .getHolder(DEEP_DUNGEON)
            .orElse(null);
        if (structure == null) {
            return null;
        }
        Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
            .findNearestMapStructure(level, HolderSet.direct(structure), pos, SEARCH_CHUNKS, false);
        if (found == null) {
            return null;
        }

        ChunkPos site = new ChunkPos(found.getFirst());
        ChunkAccess chunk = level.getChunk(site.x, site.z, ChunkStatus.STRUCTURE_STARTS);
        StructureStart start = level.structureManager()
            .getStartForStructure(SectionPos.bottomOf(chunk), structure.value(), chunk);
        if (start == null || !start.isValid()) {
            return null;
        }

        for (StructurePiece piece : start.getPieces()) {
            if (!(piece instanceof DungeonPiece room)) {
                continue;
            }
            String rune = room.anchorRune();
            BlockPos anchor = rune == null ? null : room.anchorSpot();
            if (anchor == null) {
                continue;
            }
            ResonantAnchorRegistry.get(level.getServer())
                .register(registryKey(wordFor(level.getSeed(), rune)), level.dimension(), anchor);
            return rune;
        }
        return null;
    }
}
