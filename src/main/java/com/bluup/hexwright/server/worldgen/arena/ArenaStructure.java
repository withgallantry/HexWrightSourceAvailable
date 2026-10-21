package com.bluup.hexwright.server.worldgen.arena;

import com.bluup.hexwright.server.worldgen.HexwrightWorldgen;
import com.bluup.hexwright.server.worldgen.SiteSurvey;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

public class ArenaStructure extends Structure {

    private static final int WIDTH = 31;
    static final int LENGTH = 54;

    private static final int ARENA_MIN = 1;
    private static final int ARENA_MAX = 27;

    private static final int FLAT_TOLERANCE = 4;

    private static final int SITE_TOLERANCE = 9;

    private static final int SAMPLES = 5;

    public static final Codec<ArenaStructure> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            settingsCodec(instance),
            Codec.intRange(-64, 320).optionalFieldOf("min_y", 55).forGetter(s -> s.minY),
            Codec.intRange(-64, 320).optionalFieldOf("max_y", 140).forGetter(s -> s.maxY),
            Codec.intRange(0, 32).optionalFieldOf("flatness", FLAT_TOLERANCE).forGetter(s -> s.flatness),
            Codec.intRange(0, 64).optionalFieldOf("site_flatness", SITE_TOLERANCE)
                .forGetter(s -> s.siteFlatness),
            Codec.intRange(0, 24 * 365).optionalFieldOf("regenerate_min_hours", 24)
                .forGetter(s -> s.regenerateMinHours),
            Codec.intRange(0, 24 * 365).optionalFieldOf("regenerate_max_hours", 48)
                .forGetter(s -> s.regenerateMaxHours)
        ).apply(instance, ArenaStructure::new));

    private final int minY;
    private final int maxY;
    private final int flatness;
    private final int siteFlatness;

    private final int regenerateMinHours;
    private final int regenerateMaxHours;

    public ArenaStructure(StructureSettings settings, int minY, int maxY, int flatness,
                          int siteFlatness, int regenerateMinHours, int regenerateMaxHours) {
        super(settings);
        this.minY = minY;
        this.maxY = Math.max(minY, maxY);
        this.flatness = flatness;
        this.siteFlatness = Math.max(flatness, siteFlatness);
        this.regenerateMinHours = Math.min(regenerateMinHours, regenerateMaxHours);
        this.regenerateMaxHours = Math.max(regenerateMinHours, regenerateMaxHours);
    }

    boolean regenerates() {
        return this.regenerateMaxHours > 0;
    }

    long regenerationDelayMillis(RandomSource random) {
        int spreadMinutes = (this.regenerateMaxHours - this.regenerateMinHours) * 60;
        long minutes = this.regenerateMinHours * 60L + random.nextInt(spreadMinutes + 1);
        return minutes * 60_000L;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        WorldgenRandom random = context.random();
        Rotation rotation = Rotation.getRandom(random);
        boolean turned = rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
        int width = turned ? LENGTH : WIDTH;
        int length = turned ? WIDTH : LENGTH;

        ChunkPos chunk = context.chunkPos();
        int originX = chunk.getMinBlockX();
        int originZ = chunk.getMinBlockZ();

        SiteSurvey site = SiteSurvey.of(context, originX, originZ,
            originX + width - 1, originZ + length - 1, SAMPLES);
        if (site == null || site.spread() > this.siteFlatness) {
            return Optional.empty();
        }

        BoundingBox arena = arenaFootprint(rotation);
        SiteSurvey pit = SiteSurvey.of(context, originX + arena.minX(), originZ + arena.minZ(),
            originX + arena.maxX(), originZ + arena.maxZ(), SAMPLES);
        if (pit == null || pit.spread() > this.flatness) {
            return Optional.empty();
        }
        if (pit.mean() < this.minY || pit.mean() > this.maxY) {
            return Optional.empty();
        }

        BlockPos origin = new BlockPos(
            originX, pit.mean() - ArenaPiece.GROUND_Y + ArenaPiece.LIFT, originZ);
        return Optional.of(new GenerationStub(origin, builder ->
            builder.addPiece(new ArenaPiece(context.structureTemplateManager(), origin, rotation))));
    }

    private static BoundingBox arenaFootprint(Rotation rotation) {
        int lowX = ARENA_MIN;
        int lowZ = ARENA_MIN;
        int highX = ARENA_MAX;
        int highZ = ARENA_MAX;
        return switch (rotation) {
            case NONE -> new BoundingBox(lowX, 0, lowZ, highX, 0, highZ);
            case CLOCKWISE_90 -> new BoundingBox(
                LENGTH - 1 - highZ, 0, lowX, LENGTH - 1 - lowZ, 0, highX);
            case CLOCKWISE_180 -> new BoundingBox(
                WIDTH - 1 - highX, 0, LENGTH - 1 - highZ, WIDTH - 1 - lowX, 0, LENGTH - 1 - lowZ);
            case COUNTERCLOCKWISE_90 -> new BoundingBox(
                lowZ, 0, WIDTH - 1 - highX, highZ, 0, WIDTH - 1 - lowX);
        };
    }

    @Override
    public StructureType<?> type() {
        return HexwrightWorldgen.ANCIENT_ARENA;
    }
}
