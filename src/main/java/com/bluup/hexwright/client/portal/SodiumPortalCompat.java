package com.bluup.hexwright.client.portal;

import com.bluup.hexwright.Hexwright;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class SodiumPortalCompat {

    public static final class Context {
        private Object renderLists;
        private int renderDistance;

        private Context(Object renderLists, int renderDistance) {
            this.renderLists = renderLists;
            this.renderDistance = renderDistance;
        }
    }

    private static final boolean WANTED =
        !"false".equals(System.getProperty("hexwright.portal.sodium"));

    private static final boolean VERBOSE =
        "true".equals(System.getProperty("hexwright.portal.sodium.debug"));

    private static final int QUIET_AFTER = 8;

    private static int swapsReported;

    private static Field renderListsField;
    private static Field renderDistanceField;
    private static Field sectionManagerField;
    private static Field listsField;
    private static Method worldRendererInstance;
    private static Method scheduleTerrainUpdate;
    private static Method emptyRenderLists;
    private static Method visibleChunkCount;
    private static Method chunkTrackerGet;
    private static Method chunkStatusAdded;
    private static Method chunkStatusRemoved;
    private static int blockDataFlag;

    private static final boolean AVAILABLE = resolve();

    private SodiumPortalCompat() {
    }

    private static boolean resolve() {
        if (!WANTED) {
            Hexwright.LOGGER.info("[sodium-portal] disabled by -Dhexwright.portal.sodium=false; portal views "
                + "are drawn by vanilla's renderer, with its section grid forced back to full size");
            return false;
        }
        if (!FabricLoader.getInstance().isModLoaded("sodium")) {
            Hexwright.LOGGER.info("[sodium-portal] Sodium not installed; portal views use vanilla's renderer");
            return false;
        }
        try {
            Class<?> sectionManager = Class.forName(
                "me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager");
            Class<?> worldRenderer = Class.forName(
                "me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer");
            Class<?> sortedLists = Class.forName(
                "me.jellysquid.mods.sodium.client.render.chunk.lists.SortedRenderLists");

            renderListsField = sectionManager.getDeclaredField("renderLists");
            renderListsField.setAccessible(true);
            renderDistanceField = sectionManager.getDeclaredField("renderDistance");
            renderDistanceField.setAccessible(true);
            sectionManagerField = worldRenderer.getDeclaredField("renderSectionManager");
            sectionManagerField.setAccessible(true);
            worldRendererInstance = worldRenderer.getMethod("instanceNullable");
            scheduleTerrainUpdate = worldRenderer.getMethod("scheduleTerrainUpdate");
            visibleChunkCount = worldRenderer.getMethod("getVisibleChunkCount");
            emptyRenderLists = sortedLists.getMethod("empty");
            listsField = sortedLists.getDeclaredField("lists");
            listsField.setAccessible(true);

            Class<?> trackerHolder = Class.forName(
                "me.jellysquid.mods.sodium.client.render.chunk.map.ChunkTrackerHolder");
            Class<?> tracker = Class.forName(
                "me.jellysquid.mods.sodium.client.render.chunk.map.ChunkTracker");
            Class<?> chunkStatus = Class.forName(
                "me.jellysquid.mods.sodium.client.render.chunk.map.ChunkStatus");
            chunkTrackerGet = trackerHolder.getMethod("get", net.minecraft.client.multiplayer.ClientLevel.class);
            chunkStatusAdded = tracker.getMethod("onChunkStatusAdded", int.class, int.class, int.class);
            chunkStatusRemoved = tracker.getMethod("onChunkStatusRemoved", int.class, int.class, int.class);
            blockDataFlag = chunkStatus.getField("FLAG_ALL").getInt(null);
            Hexwright.LOGGER.info(
                "[sodium-portal] active - portal views will be drawn by Sodium from their own render lists");
            return true;
        } catch (ReflectiveOperationException | RuntimeException mismatch) {
            Hexwright.LOGGER.warn(
                "[sodium-portal] Sodium is present but its renderer does not look the way portal views "
                    + "expect ({}); falling back to forcing vanilla's section grid back to full size.",
                mismatch.toString());
            return false;
        }
    }

    public static boolean isActive() {
        return AVAILABLE;
    }

    public static Context newContext(int renderDistance) {
        if (!AVAILABLE) {
            return null;
        }
        try {
            return new Context(emptyRenderLists.invoke(null), Math.max(1, renderDistance));
        } catch (ReflectiveOperationException failed) {
            return null;
        }
    }

    public static void onRemoteChunkLoaded(net.minecraft.client.multiplayer.ClientLevel level, int x, int z) {
        notifyTracker(chunkStatusAdded, level, x, z);
    }

    public static void onRemoteChunkUnloaded(net.minecraft.client.multiplayer.ClientLevel level, int x, int z) {
        notifyTracker(chunkStatusRemoved, level, x, z);
    }

    private static void notifyTracker(Method call, net.minecraft.client.multiplayer.ClientLevel level, int x, int z) {
        if (!AVAILABLE || level == null) {
            return;
        }
        try {
            Object tracker = chunkTrackerGet.invoke(null, level);
            if (tracker != null) {
                call.invoke(tracker, x, z, blockDataFlag);
            }
        } catch (ReflectiveOperationException | RuntimeException failed) {
            Hexwright.LOGGER.warn("[sodium-portal] could not tell Sodium about a remote chunk at {},{}", x, z);
        }
    }

    public static void swap(Context context) {
        if (!AVAILABLE || context == null) {
            return;
        }
        if (RemoteLevelManager.isRemotePassActive()) {
            reportRemotePass();
            return;
        }
        try {
            Object worldRenderer = worldRendererInstance.invoke(null);
            if (worldRenderer == null) {
                return;
            }
            Object sectionManager = sectionManagerField.get(worldRenderer);
            if (sectionManager == null) {
                return;
            }

            scheduleTerrainUpdate.invoke(worldRenderer);

            Object heldLists = renderListsField.get(sectionManager);
            renderListsField.set(sectionManager, context.renderLists);
            context.renderLists = heldLists;

            int heldDistance = renderDistanceField.getInt(sectionManager);
            renderDistanceField.setInt(sectionManager, context.renderDistance);
            context.renderDistance = heldDistance;

            scheduleTerrainUpdate.invoke(worldRenderer);

            report(worldRenderer, context, heldLists);
        } catch (ReflectiveOperationException failed) {
            Hexwright.LOGGER.warn("[sodium-portal] could not swap Sodium's render lists for a portal pass", failed);
        }
    }

    private static void report(Object worldRenderer, Context context, Object handedBack) {
        if (!VERBOSE && swapsReported >= QUIET_AFTER) {
            return;
        }
        swapsReported++;
        try {
            Hexwright.LOGGER.info(
                "[sodium-portal] swap #{}: pass draws from {} list(s) at distance {}, "
                    + "handed back {} list(s); Sodium reports {} visible chunk(s){}",
                swapsReported,
                listCount(renderListsField.get(sectionManagerField.get(worldRenderer))),
                renderDistanceField.getInt(sectionManagerField.get(worldRenderer)),
                listCount(handedBack),
                visibleChunkCount.invoke(worldRenderer),
                (!VERBOSE && swapsReported == QUIET_AFTER)
                    ? " (further swaps quiet; -Dhexwright.portal.sodium.debug=true for all)" : "");
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static void reportRemotePass() {
        if (!VERBOSE && swapsReported >= QUIET_AFTER) {
            return;
        }
        swapsReported++;
        try {
            Object worldRenderer = worldRendererInstance.invoke(null);
            if (worldRenderer == null) {
                Hexwright.LOGGER.info("[sodium-portal] remote pass #{}: no Sodium renderer on the remote LevelRenderer",
                    swapsReported);
                return;
            }
            Object sectionManager = sectionManagerField.get(worldRenderer);
            Hexwright.LOGGER.info(
                "[sodium-portal] remote pass #{}: remote renderer holds {} list(s) at distance {}, "
                    + "{} visible chunk(s){}",
                swapsReported,
                sectionManager == null ? -1 : listCount(renderListsField.get(sectionManager)),
                sectionManager == null ? -1 : renderDistanceField.getInt(sectionManager),
                visibleChunkCount.invoke(worldRenderer),
                (!VERBOSE && swapsReported == QUIET_AFTER)
                    ? " (further reports quiet; -Dhexwright.portal.sodium.debug=true for all)" : "");
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static int listCount(Object sortedRenderLists) {
        if (sortedRenderLists == null) {
            return -1;
        }
        try {
            return ((java.util.List<?>) listsField.get(sortedRenderLists)).size();
        } catch (ReflectiveOperationException | RuntimeException unknown) {
            return -1;
        }
    }
}
