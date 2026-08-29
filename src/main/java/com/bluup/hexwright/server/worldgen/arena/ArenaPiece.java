package com.bluup.hexwright.server.worldgen.arena;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.boss.HexwrightBossEntities;
import com.bluup.hexwright.server.boss.ancient.AncientBallistaEntity;
import com.bluup.hexwright.server.boss.ancient.AncientGolemEntity;
import com.bluup.hexwright.server.boss.ancient.AncientMercenaryEntity;
import com.bluup.hexwright.server.worldgen.HexwrightWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jetbrains.annotations.Nullable;

public class ArenaPiece extends TemplateStructurePiece {

    public static final ResourceLocation TEMPLATE = Hexwright.id("ancient_arena");

    public static final int GROUND_Y = 10;

    public static final int LIFT = 4;

    static final String GOLEM_MARKER = "boss/ancient_golem";
    static final String BALLISTA_MARKER = "boss/ancient_ballista";

    private static final double CREW_OFFSET = 2.0D;

    private static final double BALLISTA_CENTRE = 1.12D;

    @Nullable
    private final ChunkPos startChunk;

    public ArenaPiece(StructureTemplateManager templates, BlockPos origin, Rotation rotation,
                       ChunkPos startChunk) {
        super(HexwrightWorldgen.ARENA_PIECE, 0, templates, TEMPLATE, TEMPLATE.toString(),
            settings(rotation), align(templates, rotation, origin));
        this.startChunk = startChunk;
    }

    public ArenaPiece(StructureTemplateManager templates, CompoundTag tag) {
        super(HexwrightWorldgen.ARENA_PIECE, tag, templates,
            template -> settings(savedRotation(tag)));
        this.startChunk = null;
    }

    private static StructurePlaceSettings settings(Rotation rotation) {
        return new StructurePlaceSettings()
            .setRotation(rotation)
            .setMirror(Mirror.NONE)
            .setIgnoreEntities(true);
    }

    private static BlockPos align(StructureTemplateManager templates, Rotation rotation,
                                  BlockPos origin) {
        BoundingBox turned = templates.getOrCreate(TEMPLATE)
            .getBoundingBox(settings(rotation), BlockPos.ZERO);
        return new BlockPos(
            origin.getX() - turned.minX(),
            origin.getY() - turned.minY(),
            origin.getZ() - turned.minZ());
    }

    private static Rotation savedRotation(CompoundTag tag) {
        String saved = tag.getString("Rot");
        for (Rotation rotation : Rotation.values()) {
            if (rotation.name().equals(saved)) {
                return rotation;
            }
        }
        Hexwright.LOGGER.warn("Arena piece has no readable Rot tag; placing it unrotated");
        return Rotation.NONE;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putString("Rot", this.placeSettings.getRotation().name());
    }

    @Nullable
    private BlockPos markerPos(String metadata) {
        StructurePlaceSettings unclipped = settings(this.placeSettings.getRotation());
        for (StructureTemplate.StructureBlockInfo info : this.template.filterBlocks(
            this.templatePosition, unclipped, Blocks.STRUCTURE_BLOCK)) {
            CompoundTag nbt = info.nbt();
            if (nbt != null && metadata.equals(nbt.getString("metadata"))) {
                return info.pos();
            }
        }
        return null;
    }

    @Nullable
    public BlockPos entryPoint() {
        BlockPos golem = markerPos(GOLEM_MARKER);
        return golem == null ? null : golem.above(2);
    }

    @Override
    protected void handleDataMarker(String marker, BlockPos pos, ServerLevelAccessor level,
                                    RandomSource random, BoundingBox box) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        switch (marker) {
            case GOLEM_MARKER -> standGolem(level, pos);
            case BALLISTA_MARKER -> standBallista(level, pos);
            default -> Hexwright.LOGGER.warn("Ancient arena carries an unknown marker '{}'", marker);
        }
    }

    private void standGolem(ServerLevelAccessor level, BlockPos pos) {
        AncientGolemEntity golem = HexwrightBossEntities.ANCIENT_GOLEM.create(level.getLevel());
        if (golem == null) {
            return;
        }
        BlockPos ballista = markerPos(BALLISTA_MARKER);
        float facing = ballista == null ? 0.0F : yawToward(pos, ballista);
        golem.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, facing, 0.0F);
        golem.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE,
            null, null);
        golem.setPersistenceRequired();
        golem.setArena(this.boundingBox);
        if (this.startChunk != null) {
            golem.setArenaStartChunk(this.startChunk);
        }
        level.addFreshEntity(golem);
    }

    private void standBallista(ServerLevelAccessor level, BlockPos pos) {
        BlockPos golem = markerPos(GOLEM_MARKER);
        float facing = golem == null ? 0.0F : yawToward(pos, golem);

        double heading = facing * Mth.DEG_TO_RAD;
        double forwardX = -Math.sin(heading);
        double forwardZ = Math.cos(heading);
        double x = pos.getX() + 0.5D - forwardX * BALLISTA_CENTRE;
        double z = pos.getZ() + 0.5D - forwardZ * BALLISTA_CENTRE;

        AncientBallistaEntity ballista =
            HexwrightBossEntities.ANCIENT_BALLISTA.create(level.getLevel());
        if (ballista != null) {
            ballista.moveTo(x, pos.getY(), z, facing, 0.0F);
            level.addFreshEntity(ballista);
        }

        AncientMercenaryEntity crew =
            HexwrightBossEntities.ANCIENT_MERCENARY.create(level.getLevel());
        if (crew != null) {
            double sideways = (facing + 90.0F) * Mth.DEG_TO_RAD;
            crew.moveTo(pos.getX() + 0.5D - Math.sin(sideways) * CREW_OFFSET,
                pos.getY(),
                pos.getZ() + 0.5D + Math.cos(sideways) * CREW_OFFSET,
                facing, 0.0F);
            level.addFreshEntity(crew);
        }
    }

    private static float yawToward(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
    }
}
