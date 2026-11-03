package com.bluup.hexwright.server.worldgen.dungeon;

import com.bluup.hexwright.Hexwright;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

public final class DungeonRooms {

    private static final ResourceKey<Structure> DEEP_DUNGEON =
        ResourceKey.create(Registries.STRUCTURE, Hexwright.id("deep_dungeon"));

    private DungeonRooms() {
    }

    public static boolean isInside(ServerLevel level, BlockPos pos, ResourceLocation template) {
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(DEEP_DUNGEON);
        if (structure == null) {
            return false;
        }
        StructureStart start = level.structureManager().getStructureWithPieceAt(pos, structure);
        if (!start.isValid()) {
            return false;
        }
        for (StructurePiece piece : start.getPieces()) {
            if (piece instanceof DungeonPiece room
                && room.isTemplate(template)
                && piece.getBoundingBox().isInside(pos)) {
                return true;
            }
        }
        return false;
    }
}
