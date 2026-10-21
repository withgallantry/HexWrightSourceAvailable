package com.bluup.hexwright.server.worldgen.dungeon;

import com.bluup.hexwright.server.worldgen.AreaWard;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DungeonInstanceLayout {

    public record Plan(StructureStart start, BlockPos shaftFoot, int lid) {
    }

    private DungeonInstanceLayout() {
    }

    public static @Nullable Plan plan(ServerLevel level, int x, int z, long seed) {
        DungeonStructure dungeon = DungeonStructure.in(level);
        if (dungeon == null) {
            return null;
        }
        int y = Mth.clamp(dungeon.lowestFloorY(), level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
        BlockPos origin = new BlockPos(x, y, z);
        StructureTemplateManager templates = level.getStructureManager();
        List<DungeonPlanner.Placement> placements =
            dungeon.plan(templates, RandomSource.create(seed), DungeonStructure.extrasFor(seed));
        List<DungeonPiece> pieces = dungeon.pieces(templates, origin, withoutWayIn(placements), "");
        for (int index : entranceCandidates(placements)) {
            DungeonPiece room = pieces.get(index);
            BlockPos column = room.tapColumn();
            if (column == null) {
                continue;
            }
            StructureStart start = new StructureStart(dungeon, new ChunkPos(origin), 0,
                new PiecesContainer(new ArrayList<StructurePiece>(pieces)));
            return new Plan(start, room.shaftFoot(column), room.getBoundingBox().maxY());
        }
        return null;
    }

    private static List<Integer> entranceCandidates(List<DungeonPlanner.Placement> placements) {
        int top = 0;
        for (DungeonPlanner.Placement placement : placements) {
            top = Math.max(top, placement.level());
        }
        List<Integer> candidates = new ArrayList<>();
        for (int index = 0; index < placements.size(); index++) {
            if (placements.get(index).fittings().anchor()) {
                candidates.add(index);
            }
        }
        for (int index = 0; index < placements.size(); index++) {
            DungeonPlanner.Fittings fittings = placements.get(index).fittings();
            if (placements.get(index).level() == top && !fittings.anchor() && !fittings.titan()
                && !fittings.miniboss()) {
                candidates.add(index);
            }
        }
        return candidates;
    }

    private static List<DungeonPlanner.Placement> withoutWayIn(List<DungeonPlanner.Placement> placements) {
        List<DungeonPlanner.Placement> out = new ArrayList<>(placements.size());
        for (DungeonPlanner.Placement placement : placements) {
            DungeonPlanner.Fittings fittings = placement.fittings();
            out.add(new DungeonPlanner.Placement(placement.module(), placement.rotation(), placement.cellX(),
                placement.level(), placement.cellZ(),
                new DungeonPlanner.Fittings(fittings.kyaniteChest(), false, fittings.fixture(), fittings.miniboss(),
                    fittings.trap(), false, fittings.servitor(), fittings.titan())));
        }
        return out;
    }

    public static LongSet chunks(Plan plan) {
        return DungeonStructure.chunksOf(plan.start().getPieces());
    }

    public static void place(ServerLevel level, Plan plan, ChunkPos chunk, long seed) {
        plan.start().placeInChunk(level, level.structureManager(), level.getChunkSource().getGenerator(),
            RandomSource.create(seed ^ chunk.toLong()), DungeonBackfill.column(level, chunk), chunk);
    }

    public static void ladder(ServerLevel level, BlockPos foot, int top, long seed) {
        DungeonFittings.ladderShaft(level, DungeonBackfill.column(level, new ChunkPos(foot)),
            RandomSource.create(seed ^ foot.asLong()), foot, foot.getY(), top);
    }

    public static void register(ServerLevel level, StructureStart start) {
        long startChunk = start.getChunkPos().toLong();
        for (long packed : DungeonStructure.chunksOf(start.getPieces())) {
            level.getChunk(ChunkPos.getX(packed), ChunkPos.getZ(packed))
                .addReferenceForStructure(start.getStructure(), startChunk);
        }
        LevelChunk owner = level.getChunk(start.getChunkPos().x, start.getChunkPos().z);
        owner.setStartForStructure(start.getStructure(), start);
        level.onStructureStartsAvailable(owner);
        reseal(level, startChunk);
    }

    public static void unregister(ServerLevel level, long startChunk, BoundingBox extent) {
        DungeonStructure dungeon = DungeonStructure.in(level);
        if (dungeon == null) {
            return;
        }
        for (int x = extent.minX() >> 4; x <= extent.maxX() >> 4; x++) {
            for (int z = extent.minZ() >> 4; z <= extent.maxZ() >> 4; z++) {
                LevelChunk chunk = level.getChunk(x, z);
                LongSet references = chunk.getReferencesForStructure(dungeon);
                if (!references.contains(startChunk)) {
                    continue;
                }
                Map<Structure, LongSet> all = new HashMap<>(chunk.getAllReferences());
                LongSet remaining = new LongOpenHashSet(references);
                remaining.remove(startChunk);
                if (remaining.isEmpty()) {
                    all.remove(dungeon);
                } else {
                    all.put(dungeon, remaining);
                }
                chunk.setAllReferences(all);
            }
        }
        LevelChunk owner = level.getChunk(ChunkPos.getX(startChunk), ChunkPos.getZ(startChunk));
        if (owner.getStartForStructure(dungeon) != null) {
            Map<Structure, StructureStart> starts = new HashMap<>(owner.getAllStarts());
            starts.remove(dungeon);
            owner.setAllStarts(starts);
            level.onStructureStartsAvailable(owner);
        }
        reseal(level, startChunk);
    }

    private static void reseal(ServerLevel level, long startChunk) {
        DungeonClears.get(level.getServer()).reseal(level.dimension(), startChunk);
        AreaWard.forgetCells();
    }
}
