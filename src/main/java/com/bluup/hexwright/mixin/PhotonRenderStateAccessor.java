package com.bluup.hexwright.mixin;

import com.lowdragmc.photon.client.gameobject.emitter.PhotonParticleRenderType;
import com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = PhotonParticleRenderType.class, remap = false)
public interface PhotonRenderStateAccessor {

    @Accessor("LAYER")
    static RendererSetting.Layer hexwright$getLayer() {
        throw new AssertionError("mixin accessor not applied");
    }

    @Accessor("LAYER")
    static void hexwright$setLayer(RendererSetting.Layer layer) {
        throw new AssertionError("mixin accessor not applied");
    }

    @Accessor("FRUSTUM")
    static Frustum hexwright$getFrustum() {
        throw new AssertionError("mixin accessor not applied");
    }

    @Accessor("FRUSTUM")
    static void hexwright$setFrustum(Frustum frustum) {
        throw new AssertionError("mixin accessor not applied");
    }
}
