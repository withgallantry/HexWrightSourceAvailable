package com.bluup.hexwright.server.worldgen.dungeon;

import com.bluup.hexwright.server.worldgen.HexwrightWorldgen;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DungeonStructure extends Structure {

    private static final int MODULE_WIDTH = 32;
    private static final int MODULE_HEIGHT = 16;

    private static final int CEILING_PAD = 1;

    private static final long EXTRAS_SALT = 0x6A09E667F3BCC909L;

    public static final Codec<DungeonStructure> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        settingsCodec(instance),
        Codec.intRange(-64, 320).fieldOf("min_y").forGetter(structure -> structure.minY),
        Codec.intRange(-64, 320).fieldOf("max_y").forGetter(structure -> structure.maxY),
        Codec.intRange(3, 24).optionalFieldOf("lower_rooms", 7).forGetter(structure -> structure.lowerRooms),
        Codec.intRange(3, 24).optionalFieldOf("upper_rooms", 5).forGetter(structure -> structure.upperRooms),
        Codec.intRange(2, 6).optionalFieldOf("min_floors", 3).forGetter(structure -> structure.minFloors),
        Codec.intRange(2, 6).optionalFieldOf("max_floors", 4).forGetter(structure -> structure.maxFloors),
        Codec.floatRange(0.0F, 1.0F).optionalFieldOf("mob_chance", 0.55F).forGetter(structure -> structure.mobChance),
        Codec.intRange(0, 12).optionalFieldOf("traps", 3).forGetter(structure -> structure.traps),
        Codec.floatRange(0.0F, 1.0F).optionalFieldOf("corrupt_chance", 1.0F / 3.0F)
            .forGetter(structure -> structure.corruptChance)
    ).apply(instance, DungeonStructure::new));

    private final int minY;
    private final int maxY;
    private final int lowerRooms;
    private final int upperRooms;

    private final int minFloors;
    private final int maxFloors;

    private final float mobChance;

    private final int traps;

    private final float corruptChance;

    public DungeonStructure(StructureSettings settings, int minY, int maxY,
                            int lowerRooms, int upperRooms, int minFloors, int maxFloors,
                            float mobChance, int traps, float corruptChance) {
        super(settings);
        this.minY = minY;
        this.maxY = Math.max(minY, maxY);
        this.lowerRooms = lowerRooms;
        this.upperRooms = upperRooms;
        this.minFloors = Math.min(minFloors, maxFloors);
        this.maxFloors = Math.max(minFloors, maxFloors);
        this.mobChance = mobChance;
        this.traps = traps;
        this.corruptChance = corruptChance;
    }

    static @Nullable DungeonStructure in(ServerLevel level) {
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
            .get(HexwrightWorldgen.DEEP_DUNGEON_KEY);
        return structure instanceof DungeonStructure dungeon ? dungeon : null;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        WorldgenRandom random = context.random();
        int floorY = this.minY + random.nextInt(this.maxY - this.minY + 1);
        ChunkPos chunk = context.chunkPos();
        BlockPos origin = new BlockPos(chunk.getMinBlockX(), floorY, chunk.getMinBlockZ());

        String rune = DungeonGroups.runeForSite(context.seed(), chunk);
        RandomSource extras = new XoroshiroRandomSource(context.seed() ^ chunk.toLong() * EXTRAS_SALT);

        return Optional.of(new GenerationStub(origin, builder ->
            layOut(context.structureTemplateManager(), origin, random, extras, rune).forEach(builder::addPiece)));
    }

    List<DungeonPiece> layOut(StructureTemplateManager templates, BlockPos origin, RandomSource random,
                              RandomSource extras, String rune) {
        return pieces(templates, origin, plan(templates, random, extras), rune);
    }

    List<DungeonPlanner.Placement> plan(StructureTemplateManager templates, RandomSource random, RandomSource extras) {
        int floors = this.minFloors + random.nextInt(this.maxFloors - this.minFloors + 1);
        int[] roomsPer = DungeonPlanner.roomsPerFloor(floors, this.lowerRooms, this.upperRooms);
        return DungeonPlanner.plan(random, floors, roomsPer, this.traps,
            module -> DungeonFittings.titanRoom(templates, module), extras, this.corruptChance);
    }

    List<DungeonPiece> pieces(StructureTemplateManager templates, BlockPos origin,
                              List<DungeonPlanner.Placement> placements, String rune) {
        List<DungeonPiece> pieces = new ArrayList<>();
        for (DungeonPlanner.Placement placement : placements) {
            BlockPos cell = origin.offset(
                placement.cellX() * MODULE_WIDTH,
                placement.level() * MODULE_HEIGHT,
                placement.cellZ() * MODULE_WIDTH);
            pieces.add(new DungeonPiece(
                templates, placement.module().template(), cell, placement.rotation(), this.mobChance,
                placement.fittings(), rune));
        }
        return pieces;
    }

    static RandomSource extrasFor(long seed) {
        return new XoroshiroRandomSource(seed ^ EXTRAS_SALT);
    }

    static LongSet chunksOf(List<? extends StructurePiece> pieces) {
        LongSet chunks = new LongOpenHashSet();
        for (StructurePiece piece : pieces) {
            BoundingBox box = piece.getBoundingBox();
            for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
                for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
                    chunks.add(ChunkPos.asLong(x, z));
                }
            }
        }
        return chunks;
    }

    public int lowestFloorY() {
        return this.minY;
    }

    public int highestCeilingY() {
        return this.maxY + (this.maxFloors + CEILING_PAD) * MODULE_HEIGHT;
    }

    @Override
    public StructureType<?> type() {
        return HexwrightWorldgen.DEEP_DUNGEON;
    }
}
