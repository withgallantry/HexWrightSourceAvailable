package com.bluup.hexwright.server.worldgen.quota;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.world.level.levelgen.structure.Structure;

public interface PlannedStarts {

    Long2ObjectMap<Structure> hexwright$plannedStarts();

    void hexwright$setPlannedStarts(Long2ObjectMap<Structure> starts);
}
