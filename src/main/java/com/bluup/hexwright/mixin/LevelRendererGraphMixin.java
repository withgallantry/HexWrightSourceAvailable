package com.bluup.hexwright.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererGraphMixin {

    private static final boolean hexwright$GRAPH_PATCH_ENABLED =
        !"false".equals(System.getProperty("hexwright.portal.graphpatch"));

    @Redirect(
        method = "updateRenderChunks",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/ChunkRenderDispatcher$CompiledChunk;facesCanSeeEachother(Lnet/minecraft/core/Direction;Lnet/minecraft/core/Direction;)Z"))
    private boolean hexwright$traverseUncompiledSections(ChunkRenderDispatcher.CompiledChunk compiled,
                                                         Direction entered, Direction leaving) {
        if (hexwright$GRAPH_PATCH_ENABLED
            && compiled == ChunkRenderDispatcher.CompiledChunk.UNCOMPILED) {
            return true;
        }
        return compiled.facesCanSeeEachother(entered, leaving);
    }
}
