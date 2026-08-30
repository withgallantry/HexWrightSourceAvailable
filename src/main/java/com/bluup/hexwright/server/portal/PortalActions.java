package com.bluup.hexwright.server.portal;

import com.bluup.hexwright.server.hexpatterns.HexwrightSpellAction;
import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.OperatorUtils;
import at.petrak.hexcasting.api.casting.ParticleSpray;
import at.petrak.hexcasting.api.casting.RenderedSpell;
import at.petrak.hexcasting.api.casting.castables.SpellAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.Vec3Iota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadCaster;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class PortalActions {

    private static final double MATCH_TOLERANCE = 0.5;

    public static final long MEDIA_PER_SECOND = MediaConstants.DUST_UNIT / 2;

    private static final double MIN_SECONDS = 1.0;

    private static final double MAX_SECONDS = 600.0;

    private static final int VIEW_DISTANCE_SLACK_CHUNKS = 1;

    private static final double MIN_SEPARATION = 16.0;

    private static final long SHUTTER_COST = MediaConstants.DUST_UNIT / 10;

    private PortalActions() {
    }

    public static void register() {
        Registry<ActionRegistryEntry> registry = IXplatAbstractions.INSTANCE.getActionRegistry();
        Registry.register(registry, Hexwright.id("noneuclidean_window"),
            new ActionRegistryEntry(HexPattern.fromAngles("qqwqqqwaqq", HexDir.SOUTH_WEST), JANUS_THRESHOLD));
        Registry.register(registry, Hexwright.id("dismiss_window"),
            new ActionRegistryEntry(HexPattern.fromAngles("qqwqqqwaqqedad", HexDir.SOUTH_WEST), JANUS_SHUTTER));
    }

    private static final SpellAction JANUS_THRESHOLD = new HexwrightSpellAction() {
        @Override
        public int getArgc() {
            return 3;
        }

        @Override
        public Result execute(List<? extends Iota> args, CastingEnvironment env) {
            requireCrystal(args, 0, getArgc(), env);

            PortalWindow first = readWindow(args, 0, getArgc(), env);
            PortalWindow second = readWindow(args, 1, getArgc(), env);
            double seconds = OperatorUtils.getDoubleBetween(args, 2, MIN_SECONDS, MAX_SECONDS, getArgc());

            if (Math.abs(first.width() - second.width()) > MATCH_TOLERANCE
                || Math.abs(first.height() - second.height()) > MATCH_TOLERANCE) {
                throw MishapInvalidIota.of(args.get(1), getArgc() - 2, "hexwright.portal_mismatch",
                    String.format("%.1f", first.width()), String.format("%.1f", first.height()));
            }

            ServerLevel level = env.getWorld();
            PortalManager manager = PortalManager.get(level);
            PortalPair coincidentFirst = manager.findCoincident(first);
            PortalPair coincidentSecond = manager.findCoincident(second);

            double limit = maxSeparation(level);
            if (first.center().distanceTo(second.center()) > limit) {
                throw MishapInvalidIota.of(args.get(1), getArgc() - 2, "hexwright.portal_range",
                    String.format("%.0f", limit));
            }

            long openTicks = Math.round(seconds * 20.0);
            long cost = Math.round(seconds * MEDIA_PER_SECOND);

            return new Result(new RenderedSpell() {
                @Override
                public void cast(CastingEnvironment castEnv) {
                    if (coincidentFirst != null) {
                        manager.removePair(level, coincidentFirst.id());
                    }
                    if (coincidentSecond != null && (coincidentFirst == null
                        || !coincidentSecond.id().equals(coincidentFirst.id()))) {
                        manager.removePair(level, coincidentSecond.id());
                    }
                    UUID caster = castEnv.getCastingEntity() != null
                        ? castEnv.getCastingEntity().getUUID()
                        : Util.NIL_UUID;
                    long created = level.getGameTime();
                    manager.addPair(level, new PortalPair(
                        UUID.randomUUID(), caster, first, second, created,
                        created + PortalPair.OPEN_TICKS + openTicks));
                }

                @Override
                public CastingImage cast(CastingEnvironment castEnv, CastingImage image) {
                    cast(castEnv);
                    return image;
                }
            }, cost, List.of(
                ParticleSpray.cloud(first.center(), Math.max(first.width(), first.height()) * 0.5, 40),
                ParticleSpray.cloud(second.center(), Math.max(second.width(), second.height()) * 0.5, 40)), 1L);
        }
    };

    private static final SpellAction JANUS_SHUTTER = new HexwrightSpellAction() {
        @Override
        public int getArgc() {
            return 1;
        }

        @Override
        public Result execute(List<? extends Iota> args, CastingEnvironment env) {
            requireCrystal(args, 0, getArgc(), env);

            Vec3 at = OperatorUtils.getVec3(args, 0, getArgc());
            env.assertVecInRange(at);

            ServerLevel level = env.getWorld();
            PortalManager manager = PortalManager.get(level);
            PortalPair target = manager.findContaining(at, level.dimension());
            if (target == null) {
                throw MishapInvalidIota.of(args.get(0), 0, "hexwright.portal_absent");
            }

            return new Result(new RenderedSpell() {
                @Override
                public void cast(CastingEnvironment castEnv) {
                    manager.removePair(level, target.id());
                }

                @Override
                public CastingImage cast(CastingEnvironment castEnv, CastingImage image) {
                    cast(castEnv);
                    return image;
                }
            }, SHUTTER_COST, List.of(ParticleSpray.burst(at, 0.8, 20)), 1L);
        }
    };

    private static void requireCrystal(List<? extends Iota> args, int index, int argc, CastingEnvironment env) {
        LivingEntity caster = env.getCastingEntity();
        if (caster == null) {
            throw new MishapBadCaster();
        }
        if (!WorldCrystalSlot.isWorn(caster)) {
            throw MishapInvalidIota.of(args.get(index), argc - 1 - index, "hexwright.portal_key");
        }
    }

    private static double maxSeparation(ServerLevel level) {
        int viewChunks = level.getServer().getPlayerList().getViewDistance();
        return Math.max(MIN_SEPARATION, (viewChunks - VIEW_DISTANCE_SLACK_CHUNKS) * 16.0);
    }

    private static PortalWindow readWindow(List<? extends Iota> args, int index, int argc, CastingEnvironment env) {
        Iota raw = args.get(index);
        int blame = argc - 1 - index;
        var list = OperatorUtils.getList(args, index, argc);
        if (list.size() != 2
            || !(list.getAt(0) instanceof Vec3Iota firstIota)
            || !(list.getAt(1) instanceof Vec3Iota secondIota)) {
            throw MishapInvalidIota.ofType(raw, blame, "hexwright.portal_window");
        }
        Vec3 first = firstIota.getVec3();
        Vec3 second = secondIota.getVec3();
        env.assertVecInRange(first);
        env.assertVecInRange(second);

        PortalWindow window = PortalWindow.fromCorners(first, second);
        if (window == null) {
            throw MishapInvalidIota.of(raw, blame, "hexwright.portal_flat");
        }
        if (window.width() > PortalWindow.MAX_SPAN || window.height() > PortalWindow.MAX_SPAN) {
            throw MishapInvalidIota.of(raw, blame, "hexwright.portal_size",
                String.format("%.0f", PortalWindow.MAX_SPAN));
        }
        if (intersectsPhysicalWorld(env.getWorld(), window)) {
            throw MishapInvalidIota.of(raw, blame, "hexwright.portal_blocked");
        }
        return window;
    }

    private static boolean intersectsPhysicalWorld(ServerLevel level, PortalWindow window) {
        int minY = Mth.floor(window.origin().y);
        int maxY = Mth.floor(window.origin().y + window.height() - 1.0e-6);
        for (BlockPos column : horizontalColumns(window)) {
            for (int y = minY; y <= maxY; y++) {
                BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
                BlockState state = level.getBlockState(pos);
                if (!state.getCollisionShape(level, pos).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<BlockPos> horizontalColumns(PortalWindow window) {
        Vec3 start = window.origin();
        Vec3 end = start.add(window.u());
        int steps = Math.max(1, (int) Math.ceil(window.width() / 0.2));
        Set<Long> seen = new HashSet<>();
        List<BlockPos> columns = new ArrayList<>();
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            int bx = Mth.floor(Mth.lerp(t, start.x, end.x));
            int bz = Mth.floor(Mth.lerp(t, start.z, end.z));
            if (seen.add((((long) bx) << 32) ^ (bz & 0xFFFFFFFFL))) {
                columns.add(new BlockPos(bx, 0, bz));
            }
        }
        return columns;
    }
}
