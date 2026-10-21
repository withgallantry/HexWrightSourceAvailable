package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.worldgen.quota.PlannedStarts;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ChunkGeneratorStructureState.class)
public abstract class ChunkGeneratorStructureStatePlannedMixin implements PlannedStarts {

    @Unique
    private volatile Long2ObjectMap<Structure> hexwright$plannedStarts = Long2ObjectMaps.emptyMap();

    @Override
    public Long2ObjectMap<Structure> hexwright$plannedStarts() {
        return this.hexwright$plannedStarts;
    }

    @Override
    public void hexwright$setPlannedStarts(Long2ObjectMap<Structure> starts) {
        this.hexwright$plannedStarts = starts;
    }
}
