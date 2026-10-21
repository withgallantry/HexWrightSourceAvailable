package com.bluup.hexwright.server.hexpatterns;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.OperatorUtils;
import at.petrak.hexcasting.api.casting.RenderedSpell;
import at.petrak.hexcasting.api.casting.castables.Action;
import at.petrak.hexcasting.api.casting.castables.SpellAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.OperationResult;
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadCaster;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadLocation;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidPattern;
import at.petrak.hexcasting.api.casting.mishaps.MishapNotEnoughArgs;
import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.common.casting.actions.eval.OpEval;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.dust.DustLicence;
import com.bluup.hexwright.server.dust.DustManifestations;
import com.bluup.hexwright.server.dust.DustTuning;
import com.bluup.hexwright.server.powerorb.PowerOrbPower;
import com.bluup.hexwright.server.region.Region;
import com.bluup.hexwright.server.region.RegionAmbit;
import com.bluup.hexwright.server.region.RegionIota;
import net.minecraft.core.Registry;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class DustActions {

    private static final long MANIFEST_COST = MediaConstants.DUST_UNIT / 10;
    private static final long DIRECT_COST = MediaConstants.DUST_UNIT / 20;
    private static final long FORM_COST = MediaConstants.DUST_UNIT / 20;
    private static final long IMPEL_COST = MediaConstants.DUST_UNIT / 20;
    private static final long MOVE_COST = MediaConstants.DUST_UNIT / 20;
    private static final long CONSTRAIN_COST = MediaConstants.DUST_UNIT / 20;
    private static final long RECALL_COST = MediaConstants.DUST_UNIT / 100;
    private static final long AWAIT_COST = MediaConstants.DUST_UNIT / 100;

    private DustActions() {
    }

    public static void register() {
        Registry<ActionRegistryEntry> registry = IXplatAbstractions.INSTANCE.getActionRegistry();
        Registry.register(registry, Hexwright.id("dust_manifest"),
            new ActionRegistryEntry(HexPattern.fromAngles("waadd", HexDir.EAST), MANIFEST_DUST));
        Registry.register(registry, Hexwright.id("dust_direct"),
            new ActionRegistryEntry(HexPattern.fromAngles("waaqaedd", HexDir.EAST), DIRECT_DUST));
        Registry.register(registry, Hexwright.id("dust_form"),
            new ActionRegistryEntry(HexPattern.fromAngles("waaqae", HexDir.EAST), FORM_DUST));
        Registry.register(registry, Hexwright.id("dust_impel"),
            new ActionRegistryEntry(HexPattern.fromAngles("waaqaea", HexDir.EAST), IMPEL_DUST));
        Registry.register(registry, Hexwright.id("dust_move"),
            new ActionRegistryEntry(HexPattern.fromAngles("waaqaeq", HexDir.EAST), MOVE_DUST));
        Registry.register(registry, Hexwright.id("dust_constrain"),
            new ActionRegistryEntry(HexPattern.fromAngles("waaqaed", HexDir.EAST), CONSTRAIN_DUST));
        Registry.register(registry, Hexwright.id("dust_recall"),
            new ActionRegistryEntry(HexPattern.fromAngles("wddaa", HexDir.EAST), RECALL_DUST));
        Registry.register(registry, Hexwright.id("dust_await"),
            new ActionRegistryEntry(HexPattern.fromAngles("waaqaewaaqa", HexDir.EAST), AWAIT_DUST));
    }

    private static final SpellAction MANIFEST_DUST = new HexwrightSpellAction() {
        @Override
        public int getArgc() {
            return 1;
        }

        @Override
        public Result execute(List<? extends Iota> args, CastingEnvironment env) {
            double target = OperatorUtils.getDoubleBetween(args, 0, 0.0, DustTuning.MAX_TARGET_MASS, getArgc());
            LivingEntity caster = requireCaster(env);
            long cost = MANIFEST_COST;
            if (target > 0.0 && !DustManifestations.isActive(caster)) {
                cost += (long) Math.ceil(target * DustTuning.mediaPerMass);
            }
            return result(caster, cost, c -> DustManifestations.manifest(c, target));
        }
    };

    private static final SpellAction DIRECT_DUST = new HexwrightSpellAction() {
        @Override
        public int getArgc() {
            return 1;
        }

        @Override
        public Result execute(List<? extends Iota> args, CastingEnvironment env) {
            Vec3 vector = OperatorUtils.getVec3(args, 0, getArgc());
            LivingEntity caster = requireCaster(env);
            if (!Double.isFinite(vector.x) || !Double.isFinite(vector.y) || !Double.isFinite(vector.z)
                || vector.lengthSqr() < 1.0e-6) {
                throw MishapInvalidIota.of(args.get(0), 0, "hexwright.dust_vector");
            }
            requireDust(env, caster);
            return result(caster, DIRECT_COST, c -> DustManifestations.direct(c, vector));
        }
    };

    private static final SpellAction FORM_DUST = new HexwrightSpellAction() {
        @Override
        public int getArgc() {
            return 1;
        }

        @Override
        public Result execute(List<? extends Iota> args, CastingEnvironment env) {
            Iota raw = args.get(0);
            if (!(raw instanceof RegionIota regionIota)) {
                throw MishapInvalidIota.ofType(raw, 0, "hexwright.region");
            }
            Region region = regionIota.getRegion();
            if (region.isEmpty()) {
                throw MishapInvalidIota.of(raw, 0, "hexwright.dust_region");
            }
            LivingEntity caster = requireCaster(env);
            requireDust(env, caster);
            RegionAmbit.assertVolumeReachable(env, region, raw, 0);
            return result(caster, FORM_COST, c -> DustManifestations.form(c, region));
        }
    };

    private static final SpellAction IMPEL_DUST = new HexwrightSpellAction() {
        @Override
        public int getArgc() {
            return 1;
        }

        @Override
        public Result execute(List<? extends Iota> args, CastingEnvironment env) {
            Vec3 vector = OperatorUtils.getVec3(args, 0, getArgc());
            LivingEntity caster = requireCaster(env);
            if (!Double.isFinite(vector.x) || !Double.isFinite(vector.y) || !Double.isFinite(vector.z)) {
                throw MishapInvalidIota.of(args.get(0), 0, "hexwright.dust_vector");
            }
            requireDust(env, caster);
            if (!DustManifestations.isFormed(caster)) {
                throw new MishapBadLocation(env.mishapSprayPos(), "hexwright_dust_unformed");
            }
            double speed = vector.length();
            Vec3 velocity = speed > DustTuning.maxDriftSpeed ? vector.scale(DustTuning.maxDriftSpeed / speed) : vector;
            return result(caster, IMPEL_COST, c -> DustManifestations.impel(c, velocity));
        }
    };

    private static final SpellAction MOVE_DUST = new HexwrightSpellAction() {
        @Override
        public int getArgc() {
            return 1;
        }

        @Override
        public Result execute(List<? extends Iota> args, CastingEnvironment env) {
            Vec3 center = OperatorUtils.getVec3(args, 0, getArgc());
            LivingEntity caster = requireCaster(env);
            if (!Double.isFinite(center.x) || !Double.isFinite(center.y) || !Double.isFinite(center.z)) {
                throw MishapInvalidIota.of(args.get(0), 0, "hexwright.dust_vector");
            }
            env.assertVecInRange(center);
            requireDust(env, caster);
            if (!DustManifestations.isFormed(caster)) {
                throw new MishapBadLocation(env.mishapSprayPos(), "hexwright_dust_unformed");
            }
            return result(caster, MOVE_COST, c -> DustManifestations.move(c, center));
        }
    };

    private static final SpellAction CONSTRAIN_DUST = new HexwrightSpellAction() {
        @Override
        public int getArgc() {
            return 1;
        }

        @Override
        public Result execute(List<? extends Iota> args, CastingEnvironment env) {
            boolean constrained = OperatorUtils.getBool(args, 0, getArgc());
            LivingEntity caster = requireCaster(env);
            requireDust(env, caster);
            if (constrained && !DustManifestations.isFormed(caster)) {
                throw new MishapBadLocation(env.mishapSprayPos(), "hexwright_dust_unformed");
            }
            return result(caster, CONSTRAIN_COST, c -> DustManifestations.constrain(c, constrained));
        }
    };

    private static final SpellAction RECALL_DUST = new HexwrightSpellAction() {
        @Override
        public int getArgc() {
            return 0;
        }

        @Override
        public Result execute(List<? extends Iota> args, CastingEnvironment env) {
            LivingEntity caster = requireCaster(env);
            requireDust(env, caster);
            return result(caster, RECALL_COST, DustManifestations::recall);
        }
    };

    private static final Action AWAIT_DUST = (env, image, continuation) -> {
        LivingEntity caster = requireCaster(env);
        List<Iota> stack = new ArrayList<>(image.getStack());
        if (stack.isEmpty()) {
            throw new MishapNotEnoughArgs(1, 0);
        }
        Iota code = stack.get(stack.size() - 1);
        List<Iota> hex = StoredHex.decode(code);
        if (hex == null) {
            throw MishapInvalidIota.of(code, 0, "evaluatable");
        }
        requireDust(env, caster);
        if (DustManifestations.pending(caster) == null) {
            return OpEval.INSTANCE.operate(env, image, continuation);
        }
        if (!DustManifestations.canAwait(caster)) {
            throw new MishapBadLocation(env.mishapSprayPos(), "hexwright_dust_await_full");
        }

        stack.remove(stack.size() - 1);
        CastingImage later = new CastingImage(new ArrayList<>(stack), 0, List.of(), false, 0L,
            image.getUserData().copy());
        List<OperatorSideEffect> effects = List.of(
            new OperatorSideEffect.ConsumeMedia(AWAIT_COST),
            new OperatorSideEffect.AttemptSpell(spell(caster, c -> DustManifestations.await(c, hex, later)), true, true));
        CastingImage used = image.withUsedOp();
        CastingImage next = used.copy(stack, used.getParenCount(), used.getParenthesized(), used.getEscapeNext(),
            used.getOpsConsumed(), used.getUserData());
        return new OperationResult(next, effects, continuation, HexEvalSounds.SPELL);
    };

    private static LivingEntity requireCaster(CastingEnvironment env) {
        LivingEntity caster = env.getCastingEntity();
        if (caster == null) {
            throw new MishapBadCaster();
        }
        if (!(caster instanceof Player) || !DustLicence.licensed(caster)) {
            throw new MishapInvalidPattern();
        }
        return caster;
    }

    private static void requireDust(CastingEnvironment env, LivingEntity caster) {
        if (!DustManifestations.isActive(caster)) {
            throw new MishapBadLocation(env.mishapSprayPos(), "hexwright_no_dust");
        }
    }

    private static SpellAction.Result result(LivingEntity caster, long cost, Consumer<LivingEntity> effect) {
        return new SpellAction.Result(spell(caster, effect), cost, List.of(), 1L);
    }

    private static RenderedSpell spell(LivingEntity caster, Consumer<LivingEntity> effect) {
        return new RenderedSpell() {
            @Override
            public void cast(CastingEnvironment castEnv) {
                effect.accept(caster);
            }

            @Override
            public CastingImage cast(CastingEnvironment castEnv, CastingImage image) {
                cast(castEnv);
                return image;
            }
        };
    }
}
