package com.bluup.hexwright.client.portal;

import com.bluup.hexwright.Hexwright;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class SodiumWorldBinding {

    private static Method getWorldRenderer;
    private static Method setWorld;
    private static Method enterManagedCode;
    private static Method exitManagedCode;

    private static final boolean INSTALLED = FabricLoader.getInstance().isModLoaded("sodium");
    private static final boolean RESOLVED = INSTALLED && resolve();

    private SodiumWorldBinding() {
    }

    private static boolean resolve() {
        try {
            Class<?> extended = Class.forName("me.jellysquid.mods.sodium.client.world.WorldRendererExtended");
            Class<?> worldRenderer = Class.forName("me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer");
            Class<?> renderDevice = Class.forName("me.jellysquid.mods.sodium.client.gl.device.RenderDevice");
            getWorldRenderer = extended.getMethod("sodium$getWorldRenderer");
            setWorld = worldRenderer.getMethod("setWorld", ClientLevel.class);
            enterManagedCode = renderDevice.getMethod("enterManagedCode");
            exitManagedCode = renderDevice.getMethod("exitManagedCode");
            return true;
        } catch (ReflectiveOperationException | RuntimeException mismatch) {
            Hexwright.LOGGER.warn("[portal] Sodium is present but its world binding does not look as expected"
                + " ({}); remote levels will be bound through LevelRenderer.setLevel instead.", mismatch.toString());
            return false;
        }
    }

    public static boolean isUsable() {
        return !INSTALLED || RESOLVED;
    }

    public static void bind(LevelRenderer renderer, @Nullable ClientLevel level) {
        if (!RESOLVED) {
            return;
        }
        try {
            Object sodiumRenderer = getWorldRenderer.invoke(renderer);
            enterManagedCode.invoke(null);
            try {
                setWorld.invoke(sodiumRenderer, level);
            } finally {
                exitManagedCode.invoke(null);
            }
        } catch (InvocationTargetException thrown) {
            if (thrown.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(thrown.getCause());
        } catch (IllegalAccessException unreachable) {
            throw new IllegalStateException(unreachable);
        }
    }
}
