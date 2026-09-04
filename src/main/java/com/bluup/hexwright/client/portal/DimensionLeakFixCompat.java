package com.bluup.hexwright.client.portal;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.vault.VaultDimension;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class DimensionLeakFixCompat {

    private static final String MOD_ID = "dimension-leak-fix";

    private static final int WATCH_TICKS = 120;

    private static boolean lookupDone;
    private static @Nullable Field pendingWorldsField;
    private static @Nullable Field countdownField;

    private static final List<WeakReference<ClientLevel>> CLAIMED = new ArrayList<>();

    private static int watchTicksLeft;

    private DimensionLeakFixCompat() {
    }

    public static void register() {
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(
            DimensionLeakFixCompat::reset));
        Hexwright.LOGGER.info("[vault] {} present; vault crossings will keep their levels out of "
            + "its delayed cleanup", MOD_ID);
    }

    public static boolean isVault(@Nullable ResourceKey<Level> key) {
        return VaultDimension.KEY.equals(key);
    }

    public static void noteTransition(@Nullable ClientLevel outgoing,
                                      @Nullable ResourceKey<Level> incoming) {
        if (outgoing == null || !lookup()) {
            return;
        }
        ResourceKey<Level> from = outgoing.dimension();
        if (!isVault(from) && !isVault(incoming)) {
            return;
        }
        Hexwright.LOGGER.debug("[vault] DimensionLeakFix compatibility: vault transition detected "
            + "({} -> {})", from.location(), incoming == null ? "none" : incoming.location());
        CLAIMED.add(new WeakReference<>(outgoing));
        watchTicksLeft = WATCH_TICKS;
    }

    private static void tick() {
        if (watchTicksLeft <= 0) {
            return;
        }
        watchTicksLeft--;
        withdraw();
        if (CLAIMED.isEmpty()) {
            watchTicksLeft = 0;
        }
    }

    private static void withdraw() {
        Field pendingField = pendingWorldsField;
        if (pendingField == null) {
            return;
        }
        try {
            Object raw = pendingField.get(null);
            if (!(raw instanceof List<?> pending) || pending.isEmpty()) {
                CLAIMED.removeIf(ref -> ref.get() == null);
                return;
            }
            int withdrawn = 0;
            for (Iterator<WeakReference<ClientLevel>> it = CLAIMED.iterator(); it.hasNext(); ) {
                ClientLevel claimed = it.next().get();
                if (claimed == null) {
                    it.remove();
                    continue;
                }
                if (pending.removeIf(queued -> queued == claimed)) {
                    withdrawn++;
                    it.remove();
                }
            }
            if (withdrawn == 0) {
                return;
            }
            boolean standDown = pending.isEmpty() && standDown();
            Hexwright.LOGGER.info("[vault] DimensionLeakFix compatibility: suppressing Phase B for "
                + "{} retained level(s){}", withdrawn,
                standDown ? "" : "; other levels still queued, its countdown left running");
        } catch (Throwable t) {
            Hexwright.LOGGER.warn("[vault] DimensionLeakFix compatibility disabled: {}",
                t.toString());
            pendingWorldsField = null;
            countdownField = null;
            CLAIMED.clear();
            watchTicksLeft = 0;
        }
    }

    private static boolean standDown() {
        Field field = countdownField;
        if (field == null) {
            return false;
        }
        try {
            field.setInt(null, -1);
            return true;
        } catch (Throwable t) {
            countdownField = null;
            return false;
        }
    }

    private static void reset() {
        CLAIMED.clear();
        watchTicksLeft = 0;
    }

    private static boolean lookup() {
        if (lookupDone) {
            return pendingWorldsField != null;
        }
        lookupDone = true;
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            return false;
        }
        for (Field field : Minecraft.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            String name = field.getName();
            if (name.contains("dimLeakFix_pendingWorlds") && List.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                pendingWorldsField = field;
            } else if (name.contains("dimLeakFix_countdown") && field.getType() == int.class) {
                field.setAccessible(true);
                countdownField = field;
            }
        }
        if (pendingWorldsField == null) {
            Hexwright.LOGGER.warn("[vault] {} is installed but its pending-world list was not found "
                + "on Minecraft; vault crossings will pay for its Phase B cleanup as normal", MOD_ID);
            return false;
        }
        return true;
    }
}
