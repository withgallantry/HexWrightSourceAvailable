package com.bluup.hexwright.client.portal;

import com.bluup.hexwright.server.portal.PortalPair;
import com.bluup.hexwright.server.portal.PortalWindow;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ClientPortalManager {

    public record Entry(PortalPair pair, long openStartGameTime, long closeStartGameTime) {

        public boolean closing() {
            return closeStartGameTime >= 0L;
        }
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static @Nullable ClientLevel boundLevel;

    private ClientPortalManager() {
    }

    public static void register() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            ENTRIES.clear();
            boundLevel = null;
            PortalAttackHandler.reset();
            PortalViewRenderer.destroyAllTargets();
        }));
    }

    public static void handleFullSync(List<PortalPair> pairs) {
        bindCurrentLevel();
        ENTRIES.clear();
        for (PortalPair pair : pairs) {
            ENTRIES.add(new Entry(pair, -1L, -1L));
        }
        PortalViewRenderer.pruneTargets();
    }

    public static void handleAdd(PortalPair pair) {
        bindCurrentLevel();
        ENTRIES.removeIf(entry -> entry.pair().id().equals(pair.id()));
        ENTRIES.add(new Entry(pair, now(), -1L));
    }

    public static void handleRemove(UUID pairId) {
        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry entry = ENTRIES.get(i);
            if (entry.pair().id().equals(pairId) && !entry.closing()) {
                ENTRIES.set(i, new Entry(entry.pair(), entry.openStartGameTime(), now()));
                return;
            }
        }
    }

    private static long now() {
        return Minecraft.getInstance().level != null
            ? Minecraft.getInstance().level.getGameTime()
            : 0L;
    }

    private static void bindCurrentLevel() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != boundLevel) {
            ENTRIES.clear();
            boundLevel = level;
        }
    }

    public static List<Entry> entries() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || level != boundLevel) {
            return List.of();
        }
        if (ENTRIES.removeIf(entry -> entry.closing() && openProgress(entry, 1.0f) <= 0.0f)) {
            PortalViewRenderer.pruneTargets();
        }
        RemoteLevelManager.releaseDeferredForgets();
        return ENTRIES;
    }

    public static boolean closingPaneNeeds(ResourceLocation dimension) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || level != boundLevel) {
            return false;
        }
        for (Entry entry : ENTRIES) {
            PortalPair pair = entry.pair();
            if (!entry.closing() || !pair.isCrossDimensional()) {
                continue;
            }
            for (int side = 0; side < 2; side++) {
                ResourceKey<Level> sideDimension = pair.dimension(side);
                if (sideDimension != null
                    && !sideDimension.equals(level.dimension())
                    && sideDimension.location().equals(dimension)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static @Nullable Entry byId(UUID pairId) {
        for (Entry entry : entries()) {
            if (entry.pair().id().equals(pairId)) {
                return entry;
            }
        }
        return null;
    }

    public static boolean sideIsLocal(PortalPair pair, int side) {
        ClientLevel level = Minecraft.getInstance().level;
        return level != null && pair.sideIn(side, level.dimension());
    }

    public static boolean pairJoins(ResourceKey<Level> from, ResourceKey<Level> to) {
        for (Entry entry : entries()) {
            PortalPair pair = entry.pair();
            if (!pair.isCrossDimensional()) {
                continue;
            }
            ResourceKey<Level> a = pair.dimension(0);
            ResourceKey<Level> b = pair.dimension(1);
            if ((from.equals(a) && to.equals(b)) || (from.equals(b) && to.equals(a))) {
                return true;
            }
        }
        return false;
    }

    public static float openProgress(Entry entry, float partialTick) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return entry.closing() ? 0.0f : 1.0f;
        }
        float open = 1.0f;
        if (entry.openStartGameTime() >= 0L) {
            float elapsed = (level.getGameTime() - entry.openStartGameTime()) + partialTick;
            open = Mth.clamp(elapsed / PortalPair.OPEN_TICKS, 0.0f, 1.0f);
        }
        if (!entry.closing()) {
            return open;
        }
        float closeElapsed = (level.getGameTime() - entry.closeStartGameTime()) + partialTick;
        float closeProgress = 1.0f - Mth.clamp(closeElapsed / PortalPair.OPEN_TICKS, 0.0f, 1.0f);
        return Math.min(open, closeProgress);
    }


    public record PortalPick(UUID pairId, int side, double distance) {
    }

    public static @Nullable PortalPick pickPortal(Player player, double reach, @Nullable HitResult vanillaHit) {
        List<Entry> entries = entries();
        if (entries.isEmpty()) {
            return null;
        }
        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 look = player.getViewVector(1.0f);

        double limit = reach;
        if (vanillaHit != null && vanillaHit.getType() != HitResult.Type.MISS) {
            limit = Math.min(limit, Math.sqrt(vanillaHit.getLocation().distanceToSqr(eye)));
        }

        PortalPick best = null;
        for (Entry entry : entries) {
            if (entry.closing() || openProgress(entry, 1.0f) < 1.0f) {
                continue;
            }
            for (int side = 0; side < 2; side++) {
                if (!sideIsLocal(entry.pair(), side)) {
                    continue;
                }
                PortalWindow window = entry.pair().window(side);
                double t = window.rayHit(eye, look, limit);
                if (t > 0.0 && (best == null || t < best.distance())) {
                    best = new PortalPick(entry.pair().id(), side, t);
                }
            }
        }
        return best;
    }
}
