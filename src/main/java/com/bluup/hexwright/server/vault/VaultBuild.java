package com.bluup.hexwright.server.vault;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

public interface VaultBuild {

    String id();

    int groundsSize();

    int topY();

    Rect reserved();

    BlockPos entrance();

    void place(ServerLevel level, BlockPos groundsMin, VaultRecord record, RandomSource random);

    record Rect(int minX, int minZ, int maxX, int maxZ) {

        public boolean contains(int x, int z, int margin) {
            return x >= minX - margin && x <= maxX + margin
                && z >= minZ - margin && z <= maxZ + margin;
        }
    }
}
