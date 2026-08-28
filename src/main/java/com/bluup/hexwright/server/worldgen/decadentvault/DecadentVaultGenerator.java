package com.bluup.hexwright.server.worldgen.decadentvault;

import com.bluup.hexwright.server.block.DecadentVaultExitBlockEntity;
import com.bluup.hexwright.server.block.HexwrightBlocks;
import com.bluup.hexwright.server.block.VaultPlinthBlockEntity;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import com.bluup.hexwright.server.vault.VaultKeyItem;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

final class DecadentVaultGenerator {

    static final int SIZE = 42;
    static final int HEIGHT = 14;

    private static final int CENTRE = SIZE / 2;
    private static final int PILLAR_INSET = 6;

    private static final float SECONDARY_CHANCE = 0.35f;

    private DecadentVaultGenerator() {
    }

    static BlockPos generate(ServerLevel level, BlockPos origin, ResourceKey<Level> portalDimension,
                             BlockPos portalPos, RandomSource random) {
        int minChunk = origin.getX() >> 4;
        int maxChunk = (origin.getX() + SIZE) >> 4;
        int minChunkZ = origin.getZ() >> 4;
        int maxChunkZ = (origin.getZ() + SIZE) >> 4;
        for (int cx = minChunk; cx <= maxChunk; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                level.getChunk(cx, cz);
            }
        }

        shell(level, origin);
        hollow(level, origin);
        floor(level, origin);
        pillars(level, origin);
        ceilingAccents(level, origin, random);
        ceilingLights(level, origin);

        stockPlinth(level, origin, CENTRE, CENTRE,
            VaultKeyItem.blank(PocketCasterData.Quality.MASTERWORK));
        if (random.nextFloat() < SECONDARY_CHANCE) {
            int sx = PILLAR_INSET + random.nextInt(SIZE - 2 * PILLAR_INSET);
            int sz = PILLAR_INSET + random.nextInt(SIZE - 2 * PILLAR_INSET);
            if (Math.abs(sx - CENTRE) > 5 || Math.abs(sz - CENTRE) > 5) {
                stockPlinth(level, origin, sx, sz, MasterworkRewards.roll(random));
            }
        }

        BlockPos exitPos = origin.offset(CENTRE, 1, 3);
        set(level, exitPos, HexwrightBlocks.DECADENT_VAULT_EXIT_BLOCK.defaultBlockState());
        BlockEntity exitEntity = level.getBlockEntity(exitPos);
        if (exitEntity instanceof DecadentVaultExitBlockEntity exit) {
            exit.bindPortal(portalDimension, portalPos);
        }

        return origin.offset(CENTRE, 1, SIZE - 4);
    }

    private static void shell(ServerLevel level, BlockPos origin) {
        var shell = HexwrightBlocks.REFINED_BINDSTONE_BLOCK.defaultBlockState();
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                set(level, origin.offset(x, 0, z), shell);
                set(level, origin.offset(x, HEIGHT - 1, z), shell);
            }
        }
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < SIZE; x++) {
                set(level, origin.offset(x, y, 0), shell);
                set(level, origin.offset(x, y, SIZE - 1), shell);
            }
            for (int z = 0; z < SIZE; z++) {
                set(level, origin.offset(0, y, z), shell);
                set(level, origin.offset(SIZE - 1, y, z), shell);
            }
        }
    }

    private static void hollow(ServerLevel level, BlockPos origin) {
        var air = Blocks.AIR.defaultBlockState();
        for (int y = 2; y < HEIGHT - 1; y++) {
            for (int x = 1; x < SIZE - 1; x++) {
                for (int z = 1; z < SIZE - 1; z++) {
                    set(level, origin.offset(x, y, z), air);
                }
            }
        }
    }

    private static void ceilingLights(ServerLevel level, BlockPos origin) {
        var lamp = Blocks.GLOWSTONE.defaultBlockState();
        for (int x = 4; x < SIZE - 3; x += 6) {
            for (int z = 4; z < SIZE - 3; z += 6) {
                set(level, origin.offset(x, HEIGHT - 2, z), lamp);
            }
        }
    }

    private static void floor(ServerLevel level, BlockPos origin) {
        var field = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        var trim = Blocks.GOLD_BLOCK.defaultBlockState();
        var inlay = Blocks.QUARTZ_BLOCK.defaultBlockState();
        for (int x = 1; x < SIZE - 1; x++) {
            for (int z = 1; z < SIZE - 1; z++) {
                int dx = Math.abs(x - CENTRE);
                int dz = Math.abs(z - CENTRE);
                int ring = Math.max(dx, dz);
                var state = field;
                if (ring <= 1) {
                    state = trim;
                } else if (dx + dz <= 9 && (dx + dz) % 3 == 0) {
                    state = inlay;
                } else if (ring % 7 == 0) {
                    state = trim;
                }
                set(level, origin.offset(x, 1, z), state);
            }
        }
    }

    private static void pillars(ServerLevel level, BlockPos origin) {
        var pillar = Blocks.QUARTZ_PILLAR.defaultBlockState();
        int[] offsets = {PILLAR_INSET, SIZE - 1 - PILLAR_INSET};
        int[] mids = {CENTRE};
        for (int x : concat(offsets, mids)) {
            for (int z : offsets) {
                for (int y = 1; y < HEIGHT - 1; y++) {
                    set(level, origin.offset(x, y, z), pillar);
                }
            }
        }
        for (int z : concat(offsets, mids)) {
            for (int x : offsets) {
                for (int y = 1; y < HEIGHT - 1; y++) {
                    set(level, origin.offset(x, y, z), pillar);
                }
            }
        }
    }

    private static int[] concat(int[] a, int[] b) {
        int[] result = new int[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private static void ceilingAccents(ServerLevel level, BlockPos origin, RandomSource random) {
        var bud = Blocks.AMETHYST_CLUSTER.defaultBlockState();
        int count = 10 + random.nextInt(8);
        for (int i = 0; i < count; i++) {
            int x = 2 + random.nextInt(SIZE - 4);
            int z = 2 + random.nextInt(SIZE - 4);
            set(level, origin.offset(x, HEIGHT - 2, z), bud);
        }
    }

    private static void stockPlinth(ServerLevel level, BlockPos origin, int localX, int localZ,
                                    ItemStack item) {
        var gold = Blocks.GOLD_BLOCK.defaultBlockState();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                set(level, origin.offset(localX + dx, 1, localZ + dz), gold);
            }
        }
        BlockPos plinthPos = origin.offset(localX, 2, localZ);
        set(level, plinthPos, HexwrightBlocks.VAULT_PLINTH_BLOCK.defaultBlockState());
        BlockEntity entity = level.getBlockEntity(plinthPos);
        if (entity instanceof VaultPlinthBlockEntity plinth) {
            plinth.stock(item);
        }
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }
}
