package com.bluup.hexwright.server.region;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironmentComponent;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.mixin.CastingEnvironmentMediaAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RegionBatch {

    public static final int MEDIA_PAYMENT_INTERVAL = 32;

    public static final int MAX_DEFERRED_POSITIONS = 1 << 16;

    public static final int MAX_DROP_KINDS = 256;

    private static final int SETTLE_RECURSION = 512;

    private static final CastingEnvironmentComponent.Key<Hook> HOOK_KEY =
        new CastingEnvironmentComponent.Key<>() {
        };

    private static final Deque<RegionBatch> OPEN = new ArrayDeque<>();

    private final CastingEnvironment env;
    private final ServerLevel level;
    private final Thread owner;

    private int depth;
    private boolean flushing;

    private final Map<BlockPos, Block> deferred = new LinkedHashMap<>();

    private final List<PendingDrop> drops = new ArrayList<>();
    private long dropSumX;
    private long dropSumY;
    private long dropSumZ;
    private int dropCount;

    private long pendingMedia;
    private int opsSincePayment;

    private RegionBatch(CastingEnvironment env) {
        this.env = env;
        this.level = env.getWorld();
        this.owner = Thread.currentThread();
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> discardAll("a tick ended"));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> discardAll("the server stopped"));
    }

    public static void open(CastingEnvironment env) {
        RegionBatch batch = OPEN.peek();
        if (batch != null && batch.env == env) {
            batch.depth++;
            return;
        }
        batch = new RegionBatch(env);
        batch.depth = 1;
        OPEN.push(batch);
        if (env.getExtension(HOOK_KEY) == null) {
            env.addExtension(new Hook(env));
        }
    }

    public static void close(CastingEnvironment env) {
        RegionBatch batch = OPEN.peek();
        if (batch == null || batch.env != env || batch.owner != Thread.currentThread()) {
            return;
        }
        if (--batch.depth > 0) {
            return;
        }
        OPEN.pop();
        batch.flush();
    }

    private static void closeAllFor(CastingEnvironment env) {
        while (!OPEN.isEmpty() && OPEN.peek().env == env) {
            RegionBatch batch = OPEN.pop();
            batch.depth = 0;
            batch.flush();
        }
    }

    private static void discardAll(String why) {
        if (OPEN.isEmpty()) {
            return;
        }
        Hexwright.LOGGER.warn("Region batch still open when {}; settling {} of them now.", why, OPEN.size());
        while (!OPEN.isEmpty()) {
            RegionBatch batch = OPEN.pop();
            batch.depth = 0;
            batch.flush();
        }
    }


    @Nullable
    private static RegionBatch batchFor(LevelAccessor level) {
        RegionBatch batch = OPEN.peek();
        if (batch == null || batch.flushing || batch.level != level) {
            return null;
        }
        return batch.owner == Thread.currentThread() ? batch : null;
    }

    public static boolean deferBlockUpdate(Level level, BlockPos pos, Block was) {
        RegionBatch batch = batchFor(level);
        if (batch == null) {
            return false;
        }
        batch.remember(pos, was);
        return true;
    }

    public static boolean deferShapeUpdate(LevelAccessor level, BlockPos pos) {
        RegionBatch batch = batchFor(level);
        if (batch == null) {
            return false;
        }
        batch.remember(pos, null);
        return true;
    }

    public static boolean deferDrop(Level level, BlockPos pos, ItemStack stack) {
        RegionBatch batch = batchFor(level);
        if (batch == null || stack.isEmpty()) {
            return false;
        }
        return batch.mergeDrop(pos, stack);
    }

    private void remember(BlockPos pos, @Nullable Block was) {
        if (was == null) {
            if (!deferred.containsKey(pos)) {
                deferred.put(pos.immutable(), null);
            }
        } else if (deferred.get(pos) == null) {
            deferred.put(pos.immutable(), was);
        }
    }

    public static void checkpoint(CastingEnvironment env) {
        RegionBatch batch = OPEN.peek();
        if (batch == null || batch.env != env || batch.owner != Thread.currentThread()) {
            return;
        }
        if (batch.deferred.size() >= MAX_DEFERRED_POSITIONS) {
            batch.settle();
        }
    }

    private boolean mergeDrop(BlockPos pos, ItemStack stack) {
        for (PendingDrop drop : drops) {
            if (ItemStack.isSameItemSameTags(drop.prototype, stack)) {
                drop.count += stack.getCount();
                accumulateDropPosition(pos);
                return true;
            }
        }
        if (drops.size() >= MAX_DROP_KINDS) {
            return false;
        }
        PendingDrop drop = new PendingDrop(stack.copy());
        drop.prototype.setCount(1);
        drop.count = stack.getCount();
        drops.add(drop);
        accumulateDropPosition(pos);
        return true;
    }

    private void accumulateDropPosition(BlockPos pos) {
        dropSumX += pos.getX();
        dropSumY += pos.getY();
        dropSumZ += pos.getZ();
        dropCount++;
    }

    private long swallowMedia(long cost, boolean simulate) {
        if (flushing) {
            return cost;
        }
        if (simulate) {
            payPendingMedia();
            return cost;
        }
        pendingMedia += cost;
        if (++opsSincePayment >= MEDIA_PAYMENT_INTERVAL) {
            payPendingMedia();
        }
        return 0L;
    }

    private void payPendingMedia() {
        long owed = pendingMedia;
        pendingMedia = 0L;
        opsSincePayment = 0;
        if (owed <= 0L) {
            return;
        }
        ((CastingEnvironmentMediaAccessor) env).hexwright$extractMediaEnvironment(owed, false);
    }


    private void flush() {
        payPendingMedia();
        settle();
        spawnDrops();
    }

    private void settle() {
        if (deferred.isEmpty()) {
            return;
        }
        Map<BlockPos, Block> settling = new LinkedHashMap<>(deferred);
        deferred.clear();
        flushing = true;
        try {
            for (Map.Entry<BlockPos, Block> entry : settling.entrySet()) {
                BlockPos pos = entry.getKey();
                BlockState now = level.getBlockState(pos);
                now.updateIndirectNeighbourShapes(level, pos, Block.UPDATE_CLIENTS, SETTLE_RECURSION);
                now.updateNeighbourShapes(level, pos, Block.UPDATE_CLIENTS, SETTLE_RECURSION);
            }
            for (Map.Entry<BlockPos, Block> entry : settling.entrySet()) {
                BlockPos pos = entry.getKey();
                Block was = entry.getValue();
                level.blockUpdated(pos, was != null ? was : level.getBlockState(pos).getBlock());
            }
        } finally {
            flushing = false;
        }
    }

    private void spawnDrops() {
        if (drops.isEmpty() || dropCount == 0) {
            return;
        }
        BlockPos where = new BlockPos(
            (int) (dropSumX / dropCount),
            (int) (dropSumY / dropCount),
            (int) (dropSumZ / dropCount));
        List<PendingDrop> spawning = new ArrayList<>(drops);
        drops.clear();
        dropSumX = 0L;
        dropSumY = 0L;
        dropSumZ = 0L;
        dropCount = 0;

        flushing = true;
        try {
            for (PendingDrop drop : spawning) {
                int max = Math.max(1, drop.prototype.getMaxStackSize());
                long left = drop.count;
                while (left > 0L) {
                    int take = (int) Math.min(left, max);
                    ItemStack stack = drop.prototype.copy();
                    stack.setCount(take);
                    Block.popResource(level, where, stack);
                    left -= take;
                }
            }
        } finally {
            flushing = false;
        }
    }

    private static final class PendingDrop {
        private final ItemStack prototype;
        private long count;

        private PendingDrop(ItemStack prototype) {
            this.prototype = prototype;
        }
    }

    private static final class Hook implements CastingEnvironmentComponent.PostCast,
        CastingEnvironmentComponent.ExtractMedia.Pre {

        private final CastingEnvironment env;

        private Hook(CastingEnvironment env) {
            this.env = env;
        }

        @Override
        public CastingEnvironmentComponent.Key<?> getKey() {
            return HOOK_KEY;
        }

        @Override
        public long onExtractMedia(long cost, boolean simulate) {
            RegionBatch batch = OPEN.peek();
            if (batch == null || batch.env != env || batch.owner != Thread.currentThread()) {
                return cost;
            }
            return batch.swallowMedia(cost, simulate);
        }

        @Override
        public void onPostCast(CastingImage image) {
            closeAllFor(env);
        }
    }
}
