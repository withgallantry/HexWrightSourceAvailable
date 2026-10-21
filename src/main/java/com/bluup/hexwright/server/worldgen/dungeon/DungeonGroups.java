package com.bluup.hexwright.server.worldgen.dungeon;

import com.bluup.hexwright.server.block.ResonantAnchorBlockEntity;
import com.bluup.hexwright.server.block.ResonantAnchorRegistry;
import net.minecraft.world.level.ChunkPos;

import ram.talia.moreiotas.api.casting.iota.StringIota;

public final class DungeonGroups {

    public static final String LETTERS = "bcdefghijklmnopqstuxz";

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

    public static String registryKey(String word) {
        return ResonantAnchorBlockEntity.keyOf(StringIota.makeUnchecked(word));
    }
}
