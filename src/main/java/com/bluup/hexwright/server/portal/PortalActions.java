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
import at.petrak.hexcasting.api.casting.mishaps.MishapBadLocation;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.api.player.Sentinel;
import at.petrak.hexcasting.common.lib.HexAttributes;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.dungeon.DungeonDimension;
import com.bluup.hexwright.server.vault.VaultDimension;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
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

    public static final long GREATER_MEDIA_PER_SECOND = MEDIA_PER_SECOND * 2;

    private static final double MIN_SECONDS = 1.0;

    private static final double MAX_SECONDS = 600.0;

    private static final int VIEW_DISTANCE_SLACK_CHUNKS = 1;

    private static final double MIN_SEPARATION = 16.0;

    private static final long SHUTTER_COST = MediaConstants.DUST_UNIT / 10;

    public static final GameRules.Key<GameRules.IntegerValue> MAX_SPAN = GameRuleRegistry.register(
        "hexwrightJanusMaxSpan", GameRules.Category.MISC, GameRuleFactory.createIntRule(14, 1));

    private PortalActions() {
    }

    public static void register() {
        Registry<ActionRegistryEntry> registry = IXplatAbstractions.INSTANCE.getActionRegistry();
        Registry.register(registry, Hexwright.id("noneuclidean_window"),
            new ActionRegistryEntry(HexPattern.fromAngles("qqwqqqwaqq", HexDir.SOUTH_WEST), JANUS_THRESHOLD));
        Registry.register(registry, Hexwright.id("greater_noneuclidean_window"),
            new ActionRegistryEntry(HexPattern.fromAngles("qqwqqqwaqqww", HexDir.SOUTH_WEST), JANUS_GREATER_THRESHOLD));
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
            return openThreshold(args, getArgc(), env, false);
        }
    };

    private static final SpellAction JANUS_GREATER_THRESHOLD = new HexwrightSpellAction() {
        @Override
        public int getArgc() {
            return 3;
        }

        @Override
        public Result execute(List<? extends Iota> args, CastingEnvironment env) {
            return openThreshold(args, getArgc(), env, true);
        }
    };

    private static SpellAction.Result openThreshold(List<? extends Iota> args, int argc, CastingEnvironment env,
                                                    boolean greater) {
        requireCrystal(args, 0, argc, env);

        ServerLevel level = env.getWorld();
        if (greater && isSealed(level)) {
            throw MishapInvalidIota.of(args.get(0), argc - 1, "hexwright.portal_sealed");
        }
        PortalWindow first = readWindow(args, 0, argc, env);
        FarSide far = greater ? readFarSide(args, 1, argc, env) : null;
        ServerLevel farLevel = far == null ? level : far.level();
        PortalWindow second = far == null ? readWindow(args, 1, argc, env) : readFarWindow(args, 1, argc, far);
        double seconds = OperatorUtils.getDoubleBetween(args, 2, MIN_SECONDS, MAX_SECONDS, argc);

        if (Math.abs(first.width() - second.width()) > MATCH_TOLERANCE
            || Math.abs(first.height() - second.height()) > MATCH_TOLERANCE) {
            throw MishapInvalidIota.of(args.get(1), argc - 2, "hexwright.portal_mismatch",
                String.format("%.1f", first.width()), String.format("%.1f", first.height()));
        }

        PortalManager manager = PortalManager.get(level);
        PortalManager farManager = PortalManager.get(farLevel);
        UUID caster = env.getCastingEntity() != null
            ? env.getCastingEntity().getUUID()
            : Util.NIL_UUID;
        boolean fixture = isCreativeCast(env);

        PortalPair coincidentFirst = manager.findCoincident(first);
        PortalPair coincidentSecond = farManager.findCoincident(second);
        if (!fixture && coincidentFirst != null && !coincidentFirst.caster().equals(caster)) {
            throw MishapInvalidIota.of(args.get(0), argc - 1, "hexwright.portal_occupied");
        }
        if (!fixture && coincidentSecond != null && !coincidentSecond.caster().equals(caster)) {
            throw MishapInvalidIota.of(args.get(1), argc - 2, "hexwright.portal_occupied");
        }

        if (far == null) {
            double limit = maxSeparation(level);
            if (first.center().distanceTo(second.center()) > limit) {
                throw MishapInvalidIota.of(args.get(1), argc - 2, "hexwright.portal_range",
                    String.format("%.0f", limit));
            }
        }

        long openTicks = Math.round(seconds * 20.0);
        long cost = Math.round(seconds * (far == null ? MEDIA_PER_SECOND : GREATER_MEDIA_PER_SECOND));
        ResourceKey<Level> firstDimension = far == null ? null : level.dimension();
        ResourceKey<Level> secondDimension = far == null ? null : farLevel.dimension();

        List<ParticleSpray> sprays = new ArrayList<>(2);
        sprays.add(ParticleSpray.cloud(first.center(), Math.max(first.width(), first.height()) * 0.5, 40));
        if (far == null) {
            sprays.add(ParticleSpray.cloud(second.center(), Math.max(second.width(), second.height()) * 0.5, 40));
        }

        return new SpellAction.Result(new RenderedSpell() {
            @Override
            public void cast(CastingEnvironment castEnv) {
                if (coincidentFirst != null) {
                    manager.removePair(level, coincidentFirst.id());
                }
                if (coincidentSecond != null && (coincidentFirst == null
                    || !coincidentSecond.id().equals(coincidentFirst.id()))) {
                    farManager.removePair(farLevel, coincidentSecond.id());
                }
                long created = level.getGameTime();
                manager.addPair(level, fixture
                    ? PortalPair.fixture(UUID.randomUUID(), first, second, created, firstDimension, secondDimension)
                    : new PortalPair(UUID.randomUUID(), caster, first, second, created,
                        created + PortalPair.OPEN_TICKS + openTicks, firstDimension, secondDimension, false, false));
            }

            @Override
            public CastingImage cast(CastingEnvironment castEnv, CastingImage image) {
                cast(castEnv);
                return image;
            }
        }, cost, sprays, 1L);
    }

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
            if (target.isFixed() && !isCreativeCast(env)) {
                throw MishapInvalidIota.of(args.get(0), 0, "hexwright.portal_fixed");
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
        if (!WorldCrystalSlot.isCarried(caster)) {
            throw MishapInvalidIota.of(args.get(index), argc - 1 - index, "hexwright.portal_key");
        }
    }

    private static boolean isCreativeCast(CastingEnvironment env) {
        return env.getCastingEntity() instanceof Player player && player.isCreative();
    }

    private static double maxSeparation(ServerLevel level) {
        int viewChunks = level.getServer().getPlayerList().getViewDistance();
        return Math.max(MIN_SEPARATION, (viewChunks - VIEW_DISTANCE_SLACK_CHUNKS) * 16.0);
    }

    private static boolean isSealed(ServerLevel level) {
        return VaultDimension.isVaultLevel(level) || DungeonDimension.isDungeonLevel(level);
    }

    private record FarSide(ServerLevel level, Vec3 sentinel, double radius) {
    }

    private static FarSide readFarSide(List<? extends Iota> args, int index, int argc, CastingEnvironment env) {
        if (!(env.getCastingEntity() instanceof ServerPlayer player)) {
            throw new MishapBadCaster();
        }
        Sentinel sentinel = IXplatAbstractions.INSTANCE.getSentinel(player);
        ServerLevel level = sentinel == null || !sentinel.extendsRange()
            || sentinel.dimension().equals(env.getWorld().dimension())
            ? null
            : env.getWorld().getServer().getLevel(sentinel.dimension());
        if (level == null) {
            throw MishapInvalidIota.of(args.get(index), argc - 1 - index, "hexwright.portal_sentinel");
        }
        if (isSealed(level)) {
            throw MishapInvalidIota.of(args.get(index), argc - 1 - index, "hexwright.portal_sealed");
        }
        return new FarSide(level, sentinel.position(), player.getAttributeValue(HexAttributes.SENTINEL_RADIUS));
    }

    private static PortalWindow readWindow(List<? extends Iota> args, int index, int argc, CastingEnvironment env) {
        Vec3[] corners = readCorners(args, index, argc);
        env.assertVecInRange(corners[0]);
        env.assertVecInRange(corners[1]);
        return validWindow(args, index, argc, env.getWorld(), corners);
    }

    private static PortalWindow readFarWindow(List<? extends Iota> args, int index, int argc, FarSide far) {
        Vec3[] corners = readCorners(args, index, argc);
        for (Vec3 corner : corners) {
            if (!far.level().isInWorldBounds(BlockPos.containing(corner))
                || !far.level().getWorldBorder().isWithinBounds(corner.x, corner.z, 0.5)) {
                throw new MishapBadLocation(corner, "out_of_world");
            }
            if (corner.distanceToSqr(far.sentinel()) > far.radius() * far.radius() + 1.0e-11) {
                throw new MishapBadLocation(corner, "too_far");
            }
        }
        return validWindow(args, index, argc, far.level(), corners);
    }

    private static Vec3[] readCorners(List<? extends Iota> args, int index, int argc) {
        var list = OperatorUtils.getList(args, index, argc);
        if (list.size() != 2
            || !(list.getAt(0) instanceof Vec3Iota firstIota)
            || !(list.getAt(1) instanceof Vec3Iota secondIota)) {
            throw MishapInvalidIota.ofType(args.get(index), argc - 1 - index, "hexwright.portal_window");
        }
        return new Vec3[]{firstIota.getVec3(), secondIota.getVec3()};
    }

    private static PortalWindow validWindow(List<? extends Iota> args, int index, int argc, ServerLevel level,
                                            Vec3[] corners) {
        Iota raw = args.get(index);
        int blame = argc - 1 - index;
        PortalWindow window = PortalWindow.fromCorners(corners[0], corners[1]);
        if (window == null) {
            throw MishapInvalidIota.of(raw, blame, "hexwright.portal_flat");
        }
        int maxSpan = level.getGameRules().getInt(MAX_SPAN);
        if (window.width() > maxSpan || window.height() > maxSpan) {
            throw MishapInvalidIota.of(raw, blame, "hexwright.portal_size", String.valueOf(maxSpan));
        }
        if (intersectsPhysicalWorld(level, window)) {
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
        Vec3 inset = window.uHat().scale(1.0e-6);
        Vec3 start = window.origin().add(inset);
        Vec3 end = window.origin().add(window.u()).subtract(inset);
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
