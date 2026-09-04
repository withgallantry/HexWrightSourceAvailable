package com.bluup.hexwright.client.render;

import com.bluup.hexwright.Hexwright;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

public final class IrisCompat {
    private static final String IRIS_API_CLASS = "net.irisshaders.iris.api.v0.IrisApi";
    private static final String WORLD_RENDERING_PHASE_CLASS =
        "net.irisshaders.iris.pipeline.WorldRenderingPhase";
    private static final boolean IRIS_INSTALLED = FabricLoader.getInstance().isModLoaded("iris");

    private static volatile boolean resolutionAttempted;
    private static volatile Object irisApiInstance;
    private static volatile Method isShaderPackInUseMethod;
    private static volatile Method isRenderingShadowPassMethod;

    private static int privatePassDepth;

    private IrisCompat() {
    }

    public static boolean isInstalled() {
        return IRIS_INSTALLED;
    }

    public static boolean isShaderPackActive() {
        return query(false);
    }

    public static boolean isRenderingShadowPass() {
        return query(true);
    }

    public static void beginPrivatePass() {
        privatePassDepth++;
    }

    public static void endPrivatePass() {
        if (privatePassDepth > 0) {
            privatePassDepth--;
        }
    }

    public static boolean isPrivatePassActive() {
        return privatePassDepth > 0;
    }

    public static void beginNestedWorldPass() {
        bumpFrameCounter();
    }

    public static void endNestedWorldPass() {
        bumpFrameCounter();
        Object pipeline = pipeline();
        if (pipeline instanceof HexwrightIrisPipeline hexwright) {
            hexwright.hexwright$setBeforeTranslucent(true);
            hexwright.hexwright$setRenderingWorld(false);
        }
    }

    @Nullable
    private static Object pipeline() {
        if (!IRIS_INSTALLED) {
            return null;
        }
        if (!resolutionAttempted) {
            resolve();
        }
        Method get = getPipelineNullableMethod;
        Object manager = pipelineManager();
        if (get == null || manager == null) {
            return null;
        }
        try {
            return get.invoke(manager);
        } catch (ReflectiveOperationException | RuntimeException e) {
            getPipelineNullableMethod = null;
            return null;
        }
    }

    @Nullable
    private static Object pipelineManager() {
        Method get = getPipelineManagerMethod;
        if (get == null) {
            return null;
        }
        try {
            return get.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            getPipelineManagerMethod = null;
            return null;
        }
    }

    private static void bumpFrameCounter() {
        Object counter = frameCounter;
        Method begin = beginFrameMethod;
        if (counter == null || begin == null) {
            return;
        }
        try {
            begin.invoke(counter);
        } catch (ReflectiveOperationException | RuntimeException e) {
            beginFrameMethod = null;
        }
    }

    private static volatile Method getPipelineManagerMethod;
    private static volatile Method getPipelineNullableMethod;
    private static volatile Object frameCounter;
    private static volatile Method beginFrameMethod;

    @Nullable
    public static Object lastDimension() {
        if (!IRIS_INSTALLED) {
            return null;
        }
        if (!resolutionAttempted) {
            resolve();
        }
        java.lang.reflect.Field field = lastDimensionField;
        if (field == null) {
            return null;
        }
        try {
            return field.get(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            lastDimensionField = null;
            return null;
        }
    }

    private static volatile java.lang.reflect.Field lastDimensionField;

    private static boolean finalPassHookSeen;

    public static void noteFinalPassHook() {
        if (!finalPassHookSeen) {
            finalPassHookSeen = true;
            Hexwright.LOGGER.info("[iris] hexwright's hook on Iris's final pass is live; "
                + "the halo and the block masks composite there");
        }
    }

    public static boolean finalPassHookSeen() {
        return finalPassHookSeen;
    }

    @Nullable
    public static Object worldRenderingPhaseNone() {
        if (!resolutionAttempted) {
            resolve();
        }
        return phaseNone;
    }

    private static volatile Object phaseNone;

    private static boolean query(boolean shadowPass) {
        if (!IRIS_INSTALLED) {
            return false;
        }
        if (!resolutionAttempted) {
            resolve();
        }
        Method method = shadowPass ? isRenderingShadowPassMethod : isShaderPackInUseMethod;
        Object instance = irisApiInstance;
        if (method == null || instance == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(method.invoke(instance));
        } catch (ReflectiveOperationException | RuntimeException e) {
            Hexwright.LOGGER.warn("Iris query failed, assuming Iris is inactive from now on", e);
            isShaderPackInUseMethod = null;
            isRenderingShadowPassMethod = null;
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
            Object instance = apiClass.getMethod("getInstance").invoke(null);
            irisApiInstance = instance;
            isShaderPackInUseMethod = apiClass.getMethod("isShaderPackInUse");
            isRenderingShadowPassMethod = apiClass.getMethod("isRenderingShadowPass");
        } catch (ReflectiveOperationException | RuntimeException e) {
            Hexwright.LOGGER.info("Iris is installed but its v0 API could not be resolved; "
                + "hexwright will behave as though no shader pack is ever active ({})", e.toString());
        }

        try {
            Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
            getPipelineManagerMethod = irisClass.getMethod("getPipelineManager");
            lastDimensionField = irisClass.getField("lastDimension");
            getPipelineNullableMethod = Class.forName("net.irisshaders.iris.pipeline.PipelineManager")
                .getMethod("getPipelineNullable");
            Object counter = Class.forName("net.irisshaders.iris.uniforms.SystemTimeUniforms")
                .getField("COUNTER").get(null);
            frameCounter = counter;
            beginFrameMethod = counter.getClass().getMethod("beginFrame");
        } catch (ReflectiveOperationException | RuntimeException e) {
            Hexwright.LOGGER.info("Iris's pipeline and frame counter could not be resolved; a portal "
                + "pass will not be able to put the shader pack's per-frame state back ({})", e.toString());
        }

        try {
            for (Object constant : Class.forName(WORLD_RENDERING_PHASE_CLASS).getEnumConstants()) {
                if ("NONE".equals(((Enum<?>) constant).name())) {
                    phaseNone = constant;
                    break;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            Hexwright.LOGGER.info("Iris's WorldRenderingPhase could not be resolved; hexwright's "
                + "private draws will not be able to neutralise the rendering phase ({})", e.toString());
        }
    }
}
