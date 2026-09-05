package com.bluup.hexwright.client.render;

import com.bluup.hexwright.Hexwright;
import net.fabricmc.loader.api.FabricLoader;
import net.irisshaders.iris.api.v0.IrisApi;
import org.joml.Matrix4f;

public final class IrisCompat {
    private static final boolean INSTALLED = FabricLoader.getInstance().isModLoaded("iris");
    private static int privatePassDepth;
    private static boolean finalPassHookSeen;

    private IrisCompat() {
    }

    public static boolean isInstalled() {
        return INSTALLED;
    }

    public static boolean isShaderPackActive() {
        return INSTALLED && IrisApi.getInstance().isShaderPackInUse();
    }

    public static boolean isRenderingShadowPass() {
        return INSTALLED && IrisApi.getInstance().isRenderingShadowPass();
    }

    public static Runnable capturePortalState() {
        return INSTALLED ? new IrisPortalState() : () -> { };
    }

    public static void preparePortalWorld() {
        if (INSTALLED) {
            IrisPortalState.prepareWorld();
        }
    }

    public static void beginPrivatePass() {
        privatePassDepth++;
    }

    public static void endPrivatePass() {
        if (privatePassDepth == 0) {
            throw new IllegalStateException("Unbalanced Iris private pass");
        }
        privatePassDepth--;
    }

    public static boolean isPrivatePassActive() {
        return privatePassDepth > 0;
    }

    public static void captureGbufferProjection(Matrix4f projection) {
        if (INSTALLED) {
            net.irisshaders.iris.uniforms.CapturedRenderingState.INSTANCE.setGbufferProjection(projection);
        }
    }

    public static void noteFinalPassHook() {
        if (!finalPassHookSeen) {
            finalPassHookSeen = true;
            Hexwright.LOGGER.info("[iris] final-pass bloom integration active");
        }
    }

    public static boolean finalPassHookSeen() {
        return finalPassHookSeen;
    }
}
