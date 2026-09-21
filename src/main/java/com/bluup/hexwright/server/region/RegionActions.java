package com.bluup.hexwright.server.region;

import com.bluup.hexwright.server.hexpatterns.HexwrightConstMediaAction;
import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.OperatorUtils;
import at.petrak.hexcasting.api.casting.RenderedSpell;
import at.petrak.hexcasting.api.casting.SpellList;
import at.petrak.hexcasting.api.casting.castables.Action;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.OperationResult;
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.ContinuationFrame;
import at.petrak.hexcasting.api.casting.iota.BooleanIota;
import at.petrak.hexcasting.api.casting.iota.EntityIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import at.petrak.hexcasting.api.casting.mishaps.MishapNotEnoughArgs;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class RegionActions {

    public static final int MAX_NODES = 512;

    private RegionActions() {
    }

    public static void register() {
        Registry<ActionRegistryEntry> registry = IXplatAbstractions.INSTANCE.getActionRegistry();

        Registry.register(registry, Hexwright.id("region_sphere"),
            new ActionRegistryEntry(HexPattern.fromAngles("wqwqwqwqwqwaqeeeee", HexDir.WEST), SPHERE));
        Registry.register(registry, Hexwright.id("region_box"),
            new ActionRegistryEntry(HexPattern.fromAngles("wqwqwqwqwqwaeqqqqqae", HexDir.WEST), BOX));
        Registry.register(registry, Hexwright.id("region_cylinder"),
            new ActionRegistryEntry(HexPattern.fromAngles("wqwqwqwqwqwadaeaeaeaeaea", HexDir.WEST), CYLINDER));

        Registry.register(registry, Hexwright.id("region_union"),
            new ActionRegistryEntry(HexPattern.fromAngles("eewqqqqqweedqeeq", HexDir.NORTH_EAST), UNION));
        Registry.register(registry, Hexwright.id("region_intersection"),
            new ActionRegistryEntry(HexPattern.fromAngles("eewqqqqqweedqwdwq", HexDir.NORTH_EAST), INTERSECTION));
        Registry.register(registry, Hexwright.id("region_difference"),
            new ActionRegistryEntry(HexPattern.fromAngles("eewqqqqqweedqqdwdqq", HexDir.NORTH_EAST), DIFFERENCE));

        Registry.register(registry, Hexwright.id("region_cut"),
            new ActionRegistryEntry(HexPattern.fromAngles("eeeeedqwawqwa", HexDir.NORTH_EAST), CUT));
        Registry.register(registry, Hexwright.id("region_query"),
            new ActionRegistryEntry(HexPattern.fromAngles("eeea", HexDir.EAST), QUERY));

        Registry.register(registry, Hexwright.id("region_reach"),
            new ActionRegistryEntry(HexPattern.fromAngles("wqwqwqwqwqwaeqqqqqaww", HexDir.SOUTH_WEST), REACH));

        Registry.register(registry, Hexwright.id("region_entities"),
            new ActionRegistryEntry(HexPattern.fromAngles("eeeawede", HexDir.EAST), ENTITIES_WITHIN));
        Registry.register(registry, Hexwright.id("region_thoth"),
            new ActionRegistryEntry(HexPattern.fromAngles("eeeadeaqq", HexDir.EAST), REGION_THOTH));
        Registry.register(registry, Hexwright.id("region_fill"),
            new ActionRegistryEntry(HexPattern.fromAngles("eeeeedeqqqwqwqq", HexDir.SOUTH_WEST), REGION_FILL));
        Registry.register(registry, Hexwright.id("region_break"),
            new ActionRegistryEntry(HexPattern.fromAngles("qaqqqqqdeewewee", HexDir.EAST), REGION_BREAK));

        Registry<ContinuationFrame.Type<?>> frames = IXplatAbstractions.INSTANCE.getContinuationTypeRegistry();
        Registry.register(frames, Hexwright.id("region_foreach"), FrameRegionForEach.TYPE);

        RegionBatch.register();
    }


    private static final PureRegionAction SPHERE = new PureRegionAction(2) {
        @Override
        public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
            Vec3 center = finiteVec(args, 0, getArgc());
            double radius = finiteRadius(args, 1, getArgc());
            return List.of(new RegionIota(Region.sphere(center, radius)));
        }
    };

    private static final PureRegionAction BOX = new PureRegionAction(2) {
        @Override
        public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
            Vec3 cornerA = finiteVec(args, 0, getArgc());
            Vec3 cornerB = finiteVec(args, 1, getArgc());
            return List.of(new RegionIota(Region.box(cornerA, cornerB)));
        }
    };

    private static final PureRegionAction CYLINDER = new PureRegionAction(3) {
        @Override
        public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
            Vec3 endpointA = finiteVec(args, 0, getArgc());
            Vec3 endpointB = finiteVec(args, 1, getArgc());
            double radius = finiteRadius(args, 2, getArgc());
            if (endpointB.subtract(endpointA).lengthSqr() < Region.Cylinder.MIN_AXIS_LENGTH_SQR) {
                throw MishapInvalidIota.of(args.get(1), getArgc() - 2, "hexwright.region_axis");
            }
            return List.of(new RegionIota(Region.cylinder(endpointA, endpointB, radius)));
        }
    };


    private static final PureRegionAction UNION = new PureRegionAction(2) {
        @Override
        public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
            Region a = region(args, 0, getArgc());
            Region b = region(args, 1, getArgc());
            assertCombinable(args, a, b, getArgc());
            return List.of(new RegionIota(Region.union(a, b)));
        }
    };

    private static final PureRegionAction INTERSECTION = new PureRegionAction(2) {
        @Override
        public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
            Region a = region(args, 0, getArgc());
            Region b = region(args, 1, getArgc());
            assertCombinable(args, a, b, getArgc());
            return List.of(new RegionIota(Region.intersection(a, b)));
        }
    };

    private static final PureRegionAction DIFFERENCE = new PureRegionAction(2) {
        @Override
        public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
            Region a = region(args, 0, getArgc());
            Region b = region(args, 1, getArgc());
            assertCombinable(args, a, b, getArgc());
            return List.of(new RegionIota(Region.difference(a, b)));
        }
    };


    private static final PureRegionAction CUT = new PureRegionAction(3) {
        @Override
        public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
            Region source = region(args, 0, getArgc());
            Vec3 planePoint = finiteVec(args, 1, getArgc());
            Vec3 normal = finiteVec(args, 2, getArgc());
            if (normal.lengthSqr() < Region.Cut.MIN_NORMAL_LENGTH_SQR) {
                throw MishapInvalidIota.of(args.get(2), getArgc() - 3, "hexwright.region_normal");
            }
            if (source.nodeCount() + 1 > MAX_NODES) {
                throw MishapInvalidIota.of(args.get(0), getArgc() - 1, "hexwright.region_too_complex", MAX_NODES);
            }
            return List.of(
                new RegionIota(Region.cut(source, planePoint, normal, false)),
                new RegionIota(Region.cut(source, planePoint, normal, true)));
        }
    };

    private static final PureRegionAction QUERY = new PureRegionAction(2) {
        @Override
        public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
            Region region = region(args, 0, getArgc());
            Vec3 point = OperatorUtils.getVec3(args, 1, getArgc());
            return List.of(new BooleanIota(region.contains(point)));
        }
    };


    private static final PureRegionAction REACH = new PureRegionAction(1) {
        @Override
        public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
            Region region = region(args, 0, getArgc());
            return List.of(new BooleanIota(RegionAmbit.isWithinAmbit(env, region)));
        }
    };

    private static final PureRegionAction ENTITIES_WITHIN = new PureRegionAction(1) {
        @Override
        public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
            Region region = region(args, 0, getArgc());
            RegionAmbit.assertVolumeReachable(env, region, args.get(0), getArgc() - 1);
            if (region.isEmpty()) {
                return List.of(new ListIota(List.<Iota>of()));
            }

            AABB bounds = region.bounds();
            List<Entity> found = env.getWorld().getEntities((Entity) null, bounds,
                entity -> entity.isAlive() && !entity.isSpectator() && region.contains(entity.position()));
            Vec3 middle = bounds.getCenter();
            found.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(middle)));

            List<Iota> entities = new ArrayList<>(found.size());
            for (Entity entity : found) {
                entities.add(new EntityIota(entity));
            }
            return List.of(new ListIota(entities));
        }
    };

    private static final Action REGION_THOTH = (env, image, continuation) -> {
        List<Iota> stack = new ArrayList<>(image.getStack());
        if (stack.size() < 2) {
            throw new MishapNotEnoughArgs(2, stack.size());
        }
        Iota regionIota = stack.get(stack.size() - 2);
        SpellList code = OperatorUtils.getList(stack, stack.size() - 1, stack.size());
        if (!(regionIota instanceof RegionIota iota)) {
            throw MishapInvalidIota.ofType(regionIota, 1, "hexwright.region");
        }
        Region region = iota.getRegion();
        RegionAmbit.assertIterable(region, regionIota, 1);

        stack.remove(stack.size() - 1);
        stack.remove(stack.size() - 1);
        ContinuationFrame frame = new FrameRegionForEach(region, 0L, code, null, new ArrayList<>());
        CastingImage image2 = FrameRegionForEach.withStack(image.withUsedOp(), stack);
        return new OperationResult(image2, List.of(), continuation.pushFrame(frame), HexEvalSounds.THOTH);
    };


    private static final Action REGION_FILL = (env, image, continuation) -> {
        List<Iota> stack = new ArrayList<>(image.getStack());
        if (stack.isEmpty()) {
            throw new MishapNotEnoughArgs(1, 0);
        }
        Iota regionIota = stack.get(stack.size() - 1);
        if (!(regionIota instanceof RegionIota iota)) {
            throw MishapInvalidIota.ofType(regionIota, 0, "hexwright.region");
        }
        Region region = iota.getRegion();
        RegionAmbit.assertScannable(region, regionIota, 0);

        ItemStack template = RegionBulk.findFillStack(env);
        if (template == null) {
            throw MishapInvalidIota.of(regionIota, 0, "hexwright.region_no_blocks");
        }
        Item blockItem = template.getItem();
        Predicate<ItemStack> matches = candidate -> !candidate.isEmpty() && candidate.getItem() == blockItem;

        RegionBulk.Plan plan = RegionBulk.planFill(env, region, regionIota, 0);
        int affordable = RegionBulk.affordableCount(env, matches, plan.positions().size());
        List<BlockPos> targets = affordable >= plan.positions().size()
            ? plan.positions()
            : plan.positions().subList(0, affordable);

        stack.remove(stack.size() - 1);
        CastingImage image2 = FrameRegionForEach.withStack(image.withUsedOp(), stack);
        if (targets.isEmpty()) {
            return new OperationResult(image2, List.of(), continuation, HexEvalSounds.SPELL);
        }

        ItemStack forPlacement = template.copy();
        BlockState state = RegionBulk.fillState(env.getWorld(), targets.get(0), forPlacement);
        if (state == null) {
            throw MishapInvalidIota.of(regionIota, 0, "hexwright.region_no_blocks");
        }

        List<OperatorSideEffect> effects = List.of(
            new OperatorSideEffect.ConsumeMedia(targets.size() * RegionBulk.FILL_COST_PER_BLOCK),
            new OperatorSideEffect.AttemptSpell(
                new BulkSpell(spellEnv -> {
                    if (!spellEnv.withdrawItem(matches, targets.size(), true)) {
                        return;
                    }
                    RegionBatch.open(spellEnv);
                    try {
                        RegionBulk.fill(spellEnv.getWorld(), targets, state);
                    } finally {
                        RegionBatch.close(spellEnv);
                    }
                }), true, true));
        return new OperationResult(image2, effects, continuation, HexEvalSounds.SPELL);
    };

    private static final Action REGION_BREAK = (env, image, continuation) -> {
        List<Iota> stack = new ArrayList<>(image.getStack());
        if (stack.isEmpty()) {
            throw new MishapNotEnoughArgs(1, 0);
        }
        Iota regionIota = stack.get(stack.size() - 1);
        if (!(regionIota instanceof RegionIota iota)) {
            throw MishapInvalidIota.ofType(regionIota, 0, "hexwright.region");
        }
        Region region = iota.getRegion();
        RegionAmbit.assertScannable(region, regionIota, 0);

        RegionBulk.Plan plan = RegionBulk.planBreak(env, region, regionIota, 0);

        stack.remove(stack.size() - 1);
        CastingImage image2 = FrameRegionForEach.withStack(image.withUsedOp(), stack);
        if (plan.isEmpty()) {
            return new OperationResult(image2, List.of(), continuation, HexEvalSounds.SPELL);
        }

        List<BlockPos> targets = plan.positions();
        List<OperatorSideEffect> effects = List.of(
            new OperatorSideEffect.ConsumeMedia(plan.mediaCost()),
            new OperatorSideEffect.AttemptSpell(
                new BulkSpell(spellEnv -> {
                    ServerLevel level = spellEnv.getWorld();
                    List<BlockState> before = RegionBulk.captureStates(level, targets);
                    RegionBatch.open(spellEnv);
                    try {
                        RegionBulk.breakBlocks(level, targets, spellEnv.getCastingEntity());
                    } finally {
                        RegionBatch.close(spellEnv);
                    }
                    RegionBulk.playBreakEffects(level, targets, before);
                }), true, true));
        return new OperationResult(image2, effects, continuation, HexEvalSounds.SPELL);
    };


    private record BulkSpell(Consumer<CastingEnvironment> action) implements RenderedSpell {

        @Override
        public void cast(CastingEnvironment env) {
            action.accept(env);
        }

        @Override
        public CastingImage cast(CastingEnvironment env, CastingImage image) {
            cast(env);
            return image;
        }
    }

    private abstract static class PureRegionAction extends HexwrightConstMediaAction {

        private final int argc;

        PureRegionAction(int argc) {
            this.argc = argc;
        }

        @Override
        public int getArgc() {
            return argc;
        }

        @Override
        public long getMediaCost() {
            return 0;
        }
    }

    private static Region region(List<? extends Iota> args, int idx, int argc) {
        Iota iota = args.get(idx);
        if (iota instanceof RegionIota region) {
            return region.getRegion();
        }
        throw MishapInvalidIota.ofType(iota, argc - idx - 1, "hexwright.region");
    }

    private static Vec3 finiteVec(List<? extends Iota> args, int idx, int argc) {
        Vec3 vec = OperatorUtils.getVec3(args, idx, argc);
        if (!Double.isFinite(vec.x) || !Double.isFinite(vec.y) || !Double.isFinite(vec.z)) {
            throw MishapInvalidIota.of(args.get(idx), argc - idx - 1, "hexwright.region_coordinate");
        }
        return vec;
    }

    private static double finiteRadius(List<? extends Iota> args, int idx, int argc) {
        double radius = OperatorUtils.getPositiveDouble(args, idx, argc);
        if (!Double.isFinite(radius)) {
            throw MishapInvalidIota.of(args.get(idx), argc - idx - 1, "hexwright.region_radius");
        }
        return radius;
    }

    private static void assertCombinable(List<? extends Iota> args, Region a, Region b, int argc) {
        if (a.nodeCount() + b.nodeCount() + 1 > MAX_NODES) {
            throw MishapInvalidIota.of(args.get(0), argc - 1, "hexwright.region_too_complex", MAX_NODES);
        }
    }
}
