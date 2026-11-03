package com.bluup.hexwright.compat.hexic;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import com.bluup.hexwright.server.region.Region;
import com.bluup.hexwright.server.region.RegionAmbit;
import com.bluup.hexwright.server.region.RegionBatch;
import com.bluup.hexwright.server.region.RegionBulk;
import com.bluup.hexwright.server.region.RegionIota;
import com.bluup.hexwright.server.region.RegionSpills;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.eu.net.pool.hexic.InventoryView;
import org.eu.net.pool.hexic.TypedRegistry;
import org.eu.net.pool.hexic.VariantIota;
import org.eu.net.pool.hexic.views$package;
import org.jetbrains.annotations.Nullable;
import scala.Function1;
import scala.Option;
import scala.Some;
import scala.collection.immutable.Map;
import scala.collection.immutable.Map$;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

final class RegionAbsorb {

    private RegionAbsorb() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void register() {
        TypedRegistry<IotaType<?>, Object, Object> absorbRegistry = (TypedRegistry) views$package.absorbRegistry();
        Function1<RegionIota, Option<Function1<InventoryView.WriteContext, Map<VariantIota<?>, Object>>>> target =
            RegionAbsorb::target;
        absorbRegistry.update(RegionIota.TYPE, target);
    }

    private static Option<Function1<InventoryView.WriteContext, Map<VariantIota<?>, Object>>> target(RegionIota iota) {
        Function1<InventoryView.WriteContext, Map<VariantIota<?>, Object>> absorb = context -> absorb(iota, context);
        return new Some<>(absorb);
    }

    private static Map<VariantIota<?>, Object> absorb(RegionIota iota, InventoryView.WriteContext context) {
        CastingEnvironment env = context.env();
        Region region = iota.getRegion();
        RegionAmbit.assertScannable(region, iota, 0);
        RegionBulk.Plan plan = RegionBulk.planBreak(env, region, iota, 0);
        Map<VariantIota<?>, Object> absorbed = Map$.MODULE$.empty();
        if (plan.isEmpty()) {
            return absorbed;
        }

        context.ct().$plus$eq(plan.mediaCost(), context.tx());
        context.ct().extractMediaOrThrow(true, env);

        TakenBlocks taken = new TakenBlocks(env, plan.positions());
        taken.updateSnapshots(context.tx());
        LinkedHashMap<ItemVariant, Long> moved = new LinkedHashMap<>();
        for (Entity spilled : RegionSpills.catchWhile(env.getWorld(), () -> taken.take(moved))) {
            if (spilled instanceof ItemEntity item) {
                count(moved, item.getItem());
            } else {
                taken.spawnOnCommit(spilled);
            }
        }
        for (var entry : moved.entrySet()) {
            if (VariantIota.ofItem(entry.getKey()) instanceof VariantIota<?> concept) {
                absorbed = absorbed.updated(concept, entry.getValue());
            }
        }
        return absorbed;
    }

    private static void count(LinkedHashMap<ItemVariant, Long> moved, ItemStack stack) {
        if (!stack.isEmpty()) {
            moved.merge(ItemVariant.of(stack), (long) stack.getCount(), Long::sum);
        }
    }

    private static final class TakenBlocks extends SnapshotParticipant<List<TakenBlocks.Before>> {

        private record Before(BlockPos pos, BlockState state, @Nullable CompoundTag blockEntity) {
        }

        private final CastingEnvironment env;
        private final ServerLevel level;
        private final List<BlockPos> positions;
        private final List<BlockState> broken = new ArrayList<>();
        private final List<Entity> spawnOnCommit = new ArrayList<>();

        private TakenBlocks(CastingEnvironment env, List<BlockPos> positions) {
            this.env = env;
            this.level = env.getWorld();
            this.positions = positions;
        }

        void take(LinkedHashMap<ItemVariant, Long> moved) {
            for (BlockPos pos : positions) {
                BlockState state = level.getBlockState(pos);
                BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
                for (ItemStack drop : Block.getDrops(state, level, pos, blockEntity, env.getCastingEntity(),
                    ItemStack.EMPTY)) {
                    count(moved, drop);
                }
                broken.add(state);
                level.setBlock(pos, level.getFluidState(pos).createLegacyBlock(), Block.UPDATE_KNOWN_SHAPE);
            }
        }

        void spawnOnCommit(Entity entity) {
            spawnOnCommit.add(entity);
        }

        @Override
        protected List<Before> createSnapshot() {
            List<Before> before = new ArrayList<>(positions.size());
            for (BlockPos pos : positions) {
                BlockState state = level.getBlockState(pos);
                BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
                before.add(new Before(pos, state, blockEntity == null ? null : blockEntity.saveWithoutMetadata()));
            }
            return before;
        }

        @Override
        protected void readSnapshot(List<Before> snapshot) {
            for (Before before : snapshot) {
                level.setBlock(before.pos(), before.state(), Block.UPDATE_KNOWN_SHAPE);
                BlockEntity restored = before.blockEntity() == null ? null : level.getBlockEntity(before.pos());
                if (restored != null) {
                    restored.load(before.blockEntity());
                    restored.setChanged();
                }
            }
            broken.clear();
            spawnOnCommit.clear();
        }

        @Override
        protected void onFinalCommit() {
            RegionBatch.open(env);
            try {
                for (int i = 0; i < broken.size(); i++) {
                    BlockPos pos = positions.get(i);
                    BlockState was = broken.get(i);
                    BlockState now = level.getBlockState(pos);
                    level.sendBlockUpdated(pos, was, now, Block.UPDATE_ALL);
                    level.blockUpdated(pos, was.getBlock());
                    was.updateIndirectNeighbourShapes(level, pos, Block.UPDATE_CLIENTS, 511);
                    now.updateNeighbourShapes(level, pos, Block.UPDATE_CLIENTS, 511);
                    now.updateIndirectNeighbourShapes(level, pos, Block.UPDATE_CLIENTS, 511);
                    was.spawnAfterBreak(level, pos, ItemStack.EMPTY, true);
                }
                spawnOnCommit.forEach(level::addFreshEntity);
            } finally {
                RegionBatch.close(env);
            }
            RegionBulk.playBreakEffects(level, positions.subList(0, broken.size()), broken);
        }
    }
}
