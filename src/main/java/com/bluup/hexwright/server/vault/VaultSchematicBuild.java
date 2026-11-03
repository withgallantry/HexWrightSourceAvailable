package com.bluup.hexwright.server.vault;

import com.bluup.hexwright.Hexwright;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.Optional;

public abstract class VaultSchematicBuild implements VaultBuild {

    private final ResourceLocation template;
    private final Rotation rotation;
    private final int width;
    private final int depth;
    private final int entranceX;
    private final int entranceZ;
    private final int templateHeight;
    private final int groundsSize;
    private final int originX;
    private final int originZ;

    protected VaultSchematicBuild(ResourceLocation template, Rotation rotation,
                                  int width, int depth, int entranceX, int entranceZ,
                                  int templateHeight, int groundsSize, int originZ) {
        this.template = template;
        this.rotation = rotation;
        this.width = width;
        this.depth = depth;
        this.entranceX = entranceX;
        this.entranceZ = entranceZ;
        this.templateHeight = templateHeight;
        this.groundsSize = groundsSize;
        this.originX = VaultGrounds.alignedOriginX(groundsSize, width, entranceX);
        this.originZ = originZ;
    }

    @Override
    public int groundsSize() {
        return groundsSize;
    }

    @Override
    public int topY() {
        return VaultGrounds.GROUND_Y + templateHeight;
    }

    @Override
    public Rect reserved() {
        return new Rect(originX, originZ, originX + width - 1, originZ + depth - 1);
    }

    @Override
    public BlockPos entrance() {
        return new BlockPos(originX + entranceX, VaultGrounds.GROUND_Y, originZ + entranceZ);
    }

    @Override
    public void place(ServerLevel level, BlockPos groundsMin, VaultRecord record, RandomSource random) {
        Optional<StructureTemplate> loaded = level.getStructureManager().get(template);
        if (loaded.isEmpty()) {
            Hexwright.LOGGER.error("Vault build '{}' is missing its structure {}", id(), template);
            return;
        }
        StructureTemplate structure = loaded.get();
        BlockPos target = groundsMin.offset(originX, VaultGrounds.GROUND_Y, originZ);
        BlockPos zero = structure.getZeroPositionWithTransform(target, Mirror.NONE, rotation);
        StructurePlaceSettings settings = new StructurePlaceSettings()
            .setRotation(rotation)
            .setMirror(Mirror.NONE)
            .setIgnoreEntities(true);
        structure.placeInWorld(level, zero, zero, settings, random, Block.UPDATE_CLIENTS);
    }
}
