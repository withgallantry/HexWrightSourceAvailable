package com.bluup.hexwright.server.worldgen.dungeon;

import com.bluup.hexwright.Hexwright;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Clearable;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

final class DungeonBackfill {

    static final TagKey<Block> GRAVES = TagKey.create(Registries.BLOCK, Hexwright.id("graves"));

    private static final int POCKET_RADIUS = 1;
    private static final int POCKET_HEIGHT = 3;

    private static final int SHAFT_REACH = 32;

    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    record Pockets(LongSet kept, LongSet air) {

        static Pockets none() {
            return new Pockets(new LongOpenHashSet(), new LongOpenHashSet());
        }

        boolean protects(BlockPos pos) {
            long packed = pos.asLong();
            return this.kept.contains(packed) || this.air.contains(packed);
        }
    }

    private DungeonBackfill() {
    }

    static Pockets pocketsFor(ServerLevel level, LongSet chunks, Map<DungeonPiece, Long2ObjectMap<Block>> templates) {
        Pockets pockets = Pockets.none();
        for (long packed : chunks) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(packed), ChunkPos.getZ(packed));
            if (chunk == null) {
                continue;
            }
            for (BlockEntity entity : chunk.getBlockEntities().values()) {
                if (kept(entity, templates)) {
                    pockets.kept().add(entity.getBlockPos().asLong());
                }
            }
        }
        for (long kept : pockets.kept()) {
            BlockPos at = BlockPos.of(kept);
            for (BlockPos cell : BlockPos.betweenClosed(
                at.offset(-POCKET_RADIUS, 0, -POCKET_RADIUS),
                at.offset(POCKET_RADIUS, POCKET_HEIGHT - 1, POCKET_RADIUS))) {
                if (!pockets.kept().contains(cell.asLong())) {
                    pockets.air().add(cell.asLong());
                }
            }
        }
        return pockets;
    }

    private static boolean kept(BlockEntity entity, Map<DungeonPiece, Long2ObjectMap<Block>> templates) {
        BlockPos pos = entity.getBlockPos();
        BlockState state = entity.getBlockState();
        for (Map.Entry<DungeonPiece, Long2ObjectMap<Block>> room : templates.entrySet()) {
            DungeonPiece piece = room.getKey();
            if (!piece.getBoundingBox().isInside(pos)) {
                continue;
            }
            if (state.is(GRAVES)) {
                return true;
            }
            Block placed = room.getValue().get(pos.asLong());
            if (placed == null || state.is(placed) || !(entity instanceof Container container) || container.isEmpty()
                || PlayerBuilds.isWorldgen(entity)) {
                return false;
            }
            Block fitting = piece.fittingBlocks().get(pos);
            return fitting == null || !state.is(fitting);
        }
        return false;
    }

    static void fill(ServerLevel level, Map<DungeonPiece, Long2ObjectMap<Block>> templates, ChunkPos chunk,
                     Pockets pockets) {
        for (Map.Entry<DungeonPiece, Long2ObjectMap<Block>> room : templates.entrySet()) {
            BoundingBox region = slice(level, room.getKey(), chunk);
            if (region == null) {
                continue;
            }
            LongSet claimed = room.getValue().keySet();
            fillRegion(level, region, claimed, pockets);
            fillRegion(level, region, claimed, pockets);
            sweep(level, region, claimed, pockets);
        }
    }

    private static @Nullable BoundingBox slice(ServerLevel level, DungeonPiece piece, ChunkPos chunk) {
        BoundingBox column = column(level, chunk);
        BoundingBox box = piece.getBoundingBox();
        if (!box.intersects(column)) {
            return null;
        }
        return new BoundingBox(
            Math.max(box.minX(), column.minX()), box.minY(), Math.max(box.minZ(), column.minZ()),
            Math.min(box.maxX(), column.maxX()), box.maxY(), Math.min(box.maxZ(), column.maxZ()));
    }

    private static void fillRegion(ServerLevel level, BoundingBox region, LongSet claimed, Pockets pockets) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                for (int y = region.minY(); y <= region.maxY(); y++) {
                    pos.set(x, y, z);
                    long packed = pos.asLong();
                    if (!claimed.contains(packed) || pockets.kept().contains(packed)) {
                        continue;
                    }
                    replace(level, pos, pockets.air().contains(packed) ? Blocks.AIR.defaultBlockState() : rock(y));
                }
            }
        }
    }

    private static void replace(ServerLevel level, BlockPos pos, BlockState with) {
        BlockState was = level.getBlockState(pos);
        if (was == with) {
            return;
        }
        if (was.hasBlockEntity()) {
            Clearable.tryClear(level.getBlockEntity(pos));
        }
        level.setBlock(pos, with, FLAGS);
    }

    static void closeShaft(ServerLevel level, BlockPos column, int moduleTop, Pockets pockets) {
        for (int y = moduleTop + 1; y <= moduleTop + SHAFT_REACH; y++) {
            BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
            if (!level.getBlockState(pos).is(Blocks.LADDER)) {
                return;
            }
            if (!pockets.protects(pos)) {
                level.setBlock(pos, rock(y), FLAGS);
            }
        }
    }

    private static void sweep(ServerLevel level, BoundingBox region, LongSet claimed, Pockets pockets) {
        for (Entity entity : level.getEntities((Entity) null, AABB.of(region),
            entity -> !(entity instanceof Player) && claimed.contains(entity.blockPosition().asLong()))) {
            if (entity instanceof Enemy) {
                entity.discard();
                continue;
            }
            if (pockets.protects(entity.blockPosition())) {
                continue;
            }
            if (PlayerBuilds.passing(entity) && !(entity instanceof ItemEntity) && !(entity instanceof ExperienceOrb)) {
                entity.discard();
                continue;
            }
            int x = entity.getBlockX();
            int z = entity.getBlockZ();
            entity.teleportTo(x + 0.5D, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z + 0.5D);
        }
    }

    static BlockState rock(int y) {
        return y < 0 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState();
    }

    static BoundingBox column(ServerLevel level, ChunkPos chunk) {
        return new BoundingBox(
            chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(),
            chunk.getMaxBlockX(), level.getMaxBuildHeight(), chunk.getMaxBlockZ());
    }
}
