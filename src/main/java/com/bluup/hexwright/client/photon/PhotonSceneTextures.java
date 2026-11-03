package com.bluup.hexwright.client.photon;

import com.bluup.hexwright.client.render.SceneSnapshot;
import com.lowdragmc.photon.client.gameobject.emitter.PhotonParticleRenderType;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.CustomShaderMaterial;
import com.lowdragmc.photon.core.mixins.accessor.ShaderInstanceAccessor;
import net.minecraft.client.renderer.ShaderInstance;

import java.util.List;

public final class PhotonSceneTextures {

    public static final String SCENE_COLOR_SAMPLER = "SamplerSceneColor";
    public static final String SCENE_DEPTH_SAMPLER = "SamplerSceneDepth";

    private PhotonSceneTextures() {
    }

    public static void register() {
        SceneSnapshot.register();
    }

    public static void onCustomShaderSetup(CustomShaderMaterial material) {
        if (material.isCompiledError()) {
            return;
        }
        ShaderInstance shader = material.getShader();
        if (!(shader instanceof ShaderInstanceAccessor accessor)) {
            return;
        }
        List<String> samplerNames = accessor.getSamplerNames();
        boolean wantsColor = samplerNames.contains(SCENE_COLOR_SAMPLER);
        boolean wantsDepth = samplerNames.contains(SCENE_DEPTH_SAMPLER);
        if (!wantsColor && !wantsDepth) {
            return;
        }
        if (!SceneSnapshot.capture(SceneSnapshot.SLOT_PHOTON + PhotonParticleRenderType.getLAYER().ordinal())) {
            return;
        }
        if (wantsColor) {
            shader.setSampler(SCENE_COLOR_SAMPLER, SceneSnapshot.colorTextureId());
        }
        if (wantsDepth) {
            shader.setSampler(SCENE_DEPTH_SAMPLER, SceneSnapshot.depthTextureId());
        }
    }
}
