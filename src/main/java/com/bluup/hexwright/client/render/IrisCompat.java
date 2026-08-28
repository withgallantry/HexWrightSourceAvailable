package com.bluup.hexwright.client.render;

import com.bluup.hexwright.Hexwright;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

public final class IrisCompat {
    private static final String IRIS_API_CLASS = "net.irisshaders.iris.api.v0.IrisApi";
    private static final boolean IRIS_INSTALLED = FabricLoader.getInstance().isModLoaded("iris");

    private static volatile boolean resolutionAttempted;
    private static volatile Object irisApiInstance;
    private static volatile Method isShaderPackInUseMethod;

    private IrisCompat() {
    }

    public static boolean isShaderPackActive() {
        if (!IRIS_INSTALLED) {
            return false;
        }

        if (!resolutionAttempted) {
            resolve();
        }

        Method method = isShaderPackInUseMethod;
        Object instance = irisApiInstance;
        if (method == null || instance == null) {
            return false;
        }

        try {
            return Boolean.TRUE.equals(method.invoke(instance));
        } catch (ReflectiveOperationException | RuntimeException e) {
            Hexwright.LOGGER.warn("Iris shader-pack-active check failed, assuming no shader pack is active from now on", e);
            isShaderPackInUseMethod = null;
            return false;
        }
    }

    private static synchronized void resolve() {
        if (resolutionAttempted) {
            return;
        }
        resolutionAttempted = true;

        try {
            Class<?> apiClass = Class.forName(IRIS_API_CLASS);
            Method getInstance = apiClass.getMethod("getInstance");
            Object instance = getInstance.invoke(null);
            Method isInUse = apiClass.getMethod("isShaderPackInUse");
            irisApiInstance = instance;
            isShaderPackInUseMethod = isInUse;
        } catch (ReflectiveOperationException | RuntimeException e) {
            Hexwright.LOGGER.info("Iris is installed but its shader-pack-active API could not be resolved; " +
                "bloom will not auto-disable for shader packs this session ({})", e.toString());
        }
    }
}
