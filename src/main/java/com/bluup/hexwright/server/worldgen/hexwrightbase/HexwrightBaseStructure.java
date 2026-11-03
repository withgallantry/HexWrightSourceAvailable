package com.bluup.hexwright.server.worldgen.hexwrightbase;

import com.bluup.hexwright.server.worldgen.HexwrightWorldgen;
import com.bluup.hexwright.server.worldgen.SiteSurvey;
import com.bluup.hexwright.server.worldgen.quota.BorderQuota;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

public class HexwrightBaseStructure extends Structure implements BorderQuota {

    private static final int FLAT_TOLERANCE = 10;

    private static final int SAMPLES = 7;

    public static final Codec<HexwrightBaseStructure> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            settingsCodec(instance),
            Codec.intRange(-64, 320).optionalFieldOf("min_y", 60).forGetter(s -> s.minY),
            Codec.intRange(-64, 320).optionalFieldOf("max_y", 160).forGetter(s -> s.maxY),
            Codec.intRange(0, 64).optionalFieldOf("flatness", FLAT_TOLERANCE).forGetter(s -> s.flatness),
            Codec.intRange(0, 64).optionalFieldOf("minimum_within_border", 0).forGetter(s -> s.minimumWithinBorder)
        ).apply(instance, HexwrightBaseStructure::new));

    private final int minY;
    private final int maxY;
    private final int flatness;
    private final int minimumWithinBorder;

    public HexwrightBaseStructure(StructureSettings settings, int minY, int maxY, int flatness, int minimumWithinBorder) {
        super(settings);
        this.minY = minY;
        this.maxY = Math.max(minY, maxY);
        this.flatness = flatness;
        this.minimumWithinBorder = minimumWithinBorder;
    }

    @Override
    public int minimumWithinBorder() {
        return this.minimumWithinBorder;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        Rotation rotation = Rotation.getRandom(context.random());
        Vec3i footprint = HexwrightBasePiece.footprint(context.structureTemplateManager());
        ChunkPos chunk = context.chunkPos();
        int originX = chunk.getMinBlockX();
        int originZ = chunk.getMinBlockZ();

        int reach = Mth.ceil(HexwrightBasePiece.SHIELD_WIDTH / 2.0f);
        int centreX = originX + footprint.getX() / 2;
        int centreZ = originZ + footprint.getZ() / 2;
        SiteSurvey site = SiteSurvey.of(context, centreX - reach, centreZ - reach,
            centreX + reach, centreZ + reach, SAMPLES);
        if (site == null || site.spread() > this.flatness
            || site.mean() < this.minY || site.mean() > this.maxY) {
            return Optional.empty();
        }

        BlockPos origin = new BlockPos(originX, site.mean(), originZ);
        return Optional.of(new GenerationStub(origin, builder -> builder.addPiece(
            new HexwrightBasePiece(context.structureTemplateManager(), origin, rotation))));
    }

    @Override
    public StructureType<?> type() {
        return HexwrightWorldgen.HEXWRIGHT_BASE;
    }
}
