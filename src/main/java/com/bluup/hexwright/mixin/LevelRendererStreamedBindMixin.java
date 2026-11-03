package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.portal.StreamedLevelBinding;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererStreamedBindMixin implements StreamedLevelBinding {

    @Shadow
    private @Nullable ClientLevel level;

    @Shadow
    private @Nullable ViewArea viewArea;

    @Shadow
    private @Nullable ChunkRenderDispatcher chunkRenderDispatcher;

    @Shadow
    @Final
    private Set<BlockEntity> globalBlockEntities;

    @Shadow
    @Final
    private AtomicReference<?> renderChunkStorage;

    @Shadow
    @Final
    private ObjectArrayList<?> renderChunksInFrustum;

    @Shadow
    public abstract void allChanged();

    @Override
    public void hexwright$bindStreamed(@Nullable ClientLevel incoming) {
        level = incoming;
        if (incoming != null) {
            allChanged();
            return;
        }
        if (viewArea != null) {
            viewArea.releaseAllBuffers();
            viewArea = null;
        }
        if (chunkRenderDispatcher != null) {
            chunkRenderDispatcher.dispose();
        }
        chunkRenderDispatcher = null;
        globalBlockEntities.clear();
        renderChunkStorage.set(null);
        renderChunksInFrustum.clear();
    }
}
