package com.bluup.hexwright.server.worldgen.travelstone;

import com.bluup.hexwright.server.worldgen.HexwrightWorldgen;
import com.bluup.hexwright.server.worldgen.SiteSurvey;
import com.bluup.hexwright.server.worldgen.quota.BorderQuota;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class TravelStoneStructure extends Structure implements BorderQuota {

    static final int PAD_RADIUS = 11;

    static final int BLEND_RADIUS = 15;

    private static final int MAX_SEARCH_RADIUS = 6;

    private static final int CANDIDATE_STRIDE = 2;

    private static final int[][] QUICK_SAMPLES = {
        {0, 0}, {PAD_RADIUS, 0}, {-PAD_RADIUS, 0}, {0, PAD_RADIUS}, {0, -PAD_RADIUS},
        {8, 8}, {-8, 8}, {8, -8}, {-8, -8}
    };

    private static final int MAX_SURVEYS = 4;

    private static final int VILLAGE_CLEARANCE = 8;

    public static final Codec<TravelStoneStructure> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            settingsCodec(instance),
            Codec.intRange(-64, 320).optionalFieldOf("min_y", 55).forGetter(s -> s.minY),
            Codec.intRange(-64, 320).optionalFieldOf("max_y", 180).forGetter(s -> s.maxY),
            Codec.intRange(0, 16).optionalFieldOf("flatness", 5).forGetter(s -> s.flatness),
            Codec.intRange(0, MAX_SEARCH_RADIUS).optionalFieldOf("search_radius", MAX_SEARCH_RADIUS)
                .forGetter(s -> s.searchRadius),
            Codec.intRange(0, 64).optionalFieldOf("minimum_within_border", 0).forGetter(s -> s.minimumWithinBorder)
        ).apply(instance, TravelStoneStructure::new));

    private final int minY;
    private final int maxY;
    private final int flatness;
    private final int searchRadius;
    private final int minimumWithinBorder;

    public TravelStoneStructure(StructureSettings settings, int minY, int maxY, int flatness, int searchRadius,
                                int minimumWithinBorder) {
        super(settings);
        this.minY = minY;
        this.maxY = Math.max(minY, maxY);
        this.flatness = flatness;
        this.searchRadius = searchRadius;
        this.minimumWithinBorder = minimumWithinBorder;
    }

    @Override
    public int minimumWithinBorder() {
        return this.minimumWithinBorder;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos start = context.chunkPos();
        RandomSpreadStructurePlacement villages = villagePlacement(context);
        List<Candidate> candidates = new ArrayList<>();
        for (int dx = -this.searchRadius; dx <= this.searchRadius; dx += CANDIDATE_STRIDE) {
            for (int dz = -this.searchRadius; dz <= this.searchRadius; dz += CANDIDATE_STRIDE) {
                if (nearVillage(context, villages, start.x + dx, start.z + dz)) {
                    continue;
                }
                Candidate candidate = quickLook(context, start.getMiddleBlockX() + dx * 16,
                    start.getMiddleBlockZ() + dz * 16, Math.max(Math.abs(dx), Math.abs(dz)));
                if (candidate != null) {
                    candidates.add(candidate);
                }
            }
        }
        candidates.sort(Comparator.comparingInt(Candidate::spread).thenComparingInt(Candidate::distance));
        for (int index = 0; index < Math.min(MAX_SURVEYS, candidates.size()); index++) {
            Candidate candidate = candidates.get(index);
            SiteSurvey site = SiteSurvey.of(context, candidate.x() - PAD_RADIUS, candidate.z() - PAD_RADIUS,
                candidate.x() + PAD_RADIUS, candidate.z() + PAD_RADIUS, 5);
            if (site == null || site.spread() > this.flatness) {
                continue;
            }
            BlockPos origin = new BlockPos(candidate.x(), site.mean() + 1, candidate.z());
            Direction front = Direction.Plane.HORIZONTAL.getRandomDirection(context.random());
            return Optional.of(new GenerationStub(origin, builder -> builder.addPiece(new TravelStonePiece(origin, front))));
        }
        return Optional.empty();
    }

    private record Candidate(int x, int z, int spread, int distance) {
    }

    private @Nullable Candidate quickLook(GenerationContext context, int x, int z, int distance) {
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (int[] offset : QUICK_SAMPLES) {
            int surface = context.chunkGenerator().getFirstOccupiedHeight(x + offset[0], z + offset[1],
                Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
            lowest = Math.min(lowest, surface);
            highest = Math.max(highest, surface);
            if (highest - lowest > this.flatness || lowest < this.minY || highest > this.maxY) {
                return null;
            }
            if (offset == QUICK_SAMPLES[0] && !context.validBiome().test(context.chunkGenerator().getBiomeSource()
                .getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(surface + 1), QuartPos.fromBlock(z),
                    context.randomState().sampler()))) {
                return null;
            }
        }
        return new Candidate(x, z, highest - lowest, distance);
    }

    private static @Nullable RandomSpreadStructurePlacement villagePlacement(GenerationContext context) {
        return context.registryAccess().registry(Registries.STRUCTURE_SET)
            .flatMap(sets -> sets.getOptional(BuiltinStructureSets.VILLAGES))
            .map(StructureSet::placement)
            .filter(RandomSpreadStructurePlacement.class::isInstance)
            .map(RandomSpreadStructurePlacement.class::cast)
            .orElse(null);
    }

    private static boolean nearVillage(GenerationContext context, @Nullable RandomSpreadStructurePlacement villages,
                                       int chunkX, int chunkZ) {
        if (villages == null) {
            return false;
        }
        int spacing = villages.spacing();
        for (int regionX = Math.floorDiv(chunkX - VILLAGE_CLEARANCE, spacing);
             regionX <= Math.floorDiv(chunkX + VILLAGE_CLEARANCE, spacing); regionX++) {
            for (int regionZ = Math.floorDiv(chunkZ - VILLAGE_CLEARANCE, spacing);
                 regionZ <= Math.floorDiv(chunkZ + VILLAGE_CLEARANCE, spacing); regionZ++) {
                ChunkPos village = villages.getPotentialStructureChunk(context.seed(), regionX * spacing, regionZ * spacing);
                if (Math.abs(village.x - chunkX) <= VILLAGE_CLEARANCE && Math.abs(village.z - chunkZ) <= VILLAGE_CLEARANCE) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public StructureType<?> type() {
        return HexwrightWorldgen.TRAVEL_STONE;
    }
}
