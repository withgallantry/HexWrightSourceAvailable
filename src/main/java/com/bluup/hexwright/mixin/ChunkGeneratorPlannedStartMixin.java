package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.worldgen.quota.PlantedSites;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorPlannedStartMixin {

    @Inject(method = "createStructures", at = @At("TAIL"))
    private void hexwright$createPlannedStart(RegistryAccess registryAccess, ChunkGeneratorStructureState state,
                                              StructureManager structures, ChunkAccess chunk,
                                              StructureTemplateManager templates, CallbackInfo ci) {
        PlantedSites.createPlannedStart((ChunkGenerator) (Object) this, registryAccess, state, structures, chunk, templates);
    }
}
