package com.bluup.hexwright.server.hexpatterns;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.OperatorUtils;
import at.petrak.hexcasting.api.casting.ParticleSpray;
import at.petrak.hexcasting.api.casting.RenderedSpell;
import at.petrak.hexcasting.api.casting.castables.SpellAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadCaster;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.portal.VoidTearManager;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class VoidTearActions {

    private static final long TEAR_COST = MediaConstants.DUST_UNIT / 4;

    private static final double SIGHT_TOLERANCE_SQ = 1.0;

    private VoidTearActions() {
    }

    public static void register() {
        Registry<ActionRegistryEntry> registry = IXplatAbstractions.INSTANCE.getActionRegistry();
        Registry.register(registry, Hexwright.id("void_tear"),
            new ActionRegistryEntry(HexPattern.fromAngles("qqwqqqwaqqqada", HexDir.SOUTH_WEST), JANUS_TEETH));
    }

    private static final SpellAction JANUS_TEETH = new HexwrightSpellAction() {
        @Override
        public int getArgc() {
            return 1;
        }

        @Override
        public Result execute(List<? extends Iota> args, CastingEnvironment env) {
            Vec3 at = OperatorUtils.getVec3(args, 0, getArgc());
            env.assertVecInRange(at);

            LivingEntity caster = env.getCastingEntity();
            if (caster == null) {
                throw new MishapBadCaster();
            }

            ServerLevel level = env.getWorld();
            if (!hasLineOfSight(level, caster, at)) {
                throw MishapInvalidIota.of(args.get(0), 0, "hexwright.tear_blocked");
            }
            if (VoidTearManager.get(level).countFor(caster.getUUID()) >= VoidTearManager.MAX_TEARS_PER_CASTER) {
                throw MishapInvalidIota.of(args.get(0), 0, "hexwright.tear_limit",
                    String.valueOf(VoidTearManager.MAX_TEARS_PER_CASTER));
            }

            return new Result(new RenderedSpell() {
                @Override
                public void cast(CastingEnvironment castEnv) {
                    VoidTearManager.get(castEnv.getWorld()).open(castEnv.getWorld(), caster, at);
                }

                @Override
                public CastingImage cast(CastingEnvironment castEnv, CastingImage image) {
                    cast(castEnv);
                    return image;
                }
            }, TEAR_COST, List.of(ParticleSpray.burst(at, VoidTearManager.tearLength() * 0.6, 18)), 1L);
        }
    };

    private static boolean hasLineOfSight(ServerLevel level, LivingEntity caster, Vec3 at) {
        Vec3 eye = caster.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(
            eye, at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        return hit.getType() == HitResult.Type.MISS
            || hit.getLocation().distanceToSqr(at) <= SIGHT_TOLERANCE_SQ;
    }
}
