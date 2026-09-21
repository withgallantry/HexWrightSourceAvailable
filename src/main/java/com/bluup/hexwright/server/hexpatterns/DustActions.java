package com.bluup.hexwright.server.hexpatterns;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.OperatorUtils;
import at.petrak.hexcasting.api.casting.RenderedSpell;
import at.petrak.hexcasting.api.casting.castables.SpellAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadCaster;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadLocation;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidPattern;
import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.dust.DustManifestations;
import com.bluup.hexwright.server.dust.DustTuning;
import com.bluup.hexwright.server.region.Region;
import com.bluup.hexwright.server.region.RegionAmbit;
import com.bluup.hexwright.server.region.RegionIota;
import net.minecraft.core.Registry;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Consumer;

public final class DustActions {

    private static final long MANIFEST_COST = MediaConstants.DUST_UNIT / 10;
    private static final long DIRECT_COST = MediaConstants.DUST_UNIT / 20;
    private static final long FORM_COST = MediaConstants.DUST_UNIT / 20;
    private static final long CONSTRAIN_COST = MediaConstants.DUST_UNIT / 20;
    private static final long RECALL_COST = MediaConstants.DUST_UNIT / 100;

    private DustActions() {
    }

    public static void register() {
        Registry<ActionRegistryEntry> registry = IXplatAbstractions.INSTANCE.getActionRegistry();
        Registry.register(registry, Hexwright.id("dust_manifest"),
            new ActionRegistryEntry(HexPattern.fromAngles("waa", HexDir.EAST), MANIFEST_DUST));
        Registry.register(registry, Hexwright.id("dust_direct"),
            new ActionRegistryEntry(HexPattern.fromAngles("wdd", HexDir.EAST), DIRECT_DUST));
        Registry.register(registry, Hexwright.id("dust_form"),
            new ActionRegistryEntry(HexPattern.fromAngles("waaqae", HexDir.EAST), FORM_DUST));
        Registry.register(registry, Hexwright.id("dust_constrain"),
            new ActionRegistryEntry(HexPattern.fromAngles("eqqqqqawwe", HexDir.EAST), CONSTRAIN_DUST));
        Registry.register(registry, Hexwright.id("dust_recall"),
            new ActionRegistryEntry(HexPattern.fromAngles("waaqaee", HexDir.EAST), RECALL_DUST));
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

    private static LivingEntity requireCaster(CastingEnvironment env) {
        LivingEntity caster = env.getCastingEntity();
        if (caster == null) {
            throw new MishapBadCaster();
        }
        if (!(caster instanceof Player player) || !player.isCreative()) {
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
        return new SpellAction.Result(new RenderedSpell() {
            @Override
            public void cast(CastingEnvironment castEnv) {
                effect.accept(caster);
            }

            @Override
            public CastingImage cast(CastingEnvironment castEnv, CastingImage image) {
                cast(castEnv);
                return image;
            }
        }, cost, List.of(), 1L);
    }
}
