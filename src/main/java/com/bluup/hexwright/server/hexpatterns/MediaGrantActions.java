package com.bluup.hexwright.server.hexpatterns;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.OperatorUtils;
import at.petrak.hexcasting.api.casting.ParticleSpray;
import at.petrak.hexcasting.api.casting.RenderedSpell;
import at.petrak.hexcasting.api.casting.castables.SpellAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.iota.EntityIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.Vec3Iota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadLocation;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import at.petrak.hexcasting.api.casting.mishaps.MishapNotEnoughMedia;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.mixin.CastingEnvironmentMediaAccessor;
import com.bluup.hexwright.server.media.MediaGrants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class MediaGrantActions {

    private static final double MAX_DUST = 640.0;

    private MediaGrantActions() {
    }

    public static void register() {
        Registry.register(IXplatAbstractions.INSTANCE.getActionRegistry(), Hexwright.id("patrons_endowment"),
            new ActionRegistryEntry(
                HexPattern.fromAngles("qqqqqwaeaeaeaeaeaqq", HexDir.NORTH_WEST), PATRONS_ENDOWMENT));
    }

    private static final SpellAction PATRONS_ENDOWMENT = new HexwrightSpellAction() {
        @Override
        public int getArgc() {
            return 2;
        }

        @Override
        public Result execute(List<? extends Iota> args, CastingEnvironment env) {
            double dust = OperatorUtils.getPositiveDoubleUnderInclusive(args, 1, MAX_DUST, getArgc());
            long media = MediaGrants.dust(dust);
            ServerLevel level = env.getWorld();

            Iota target = args.get(0);
            Runnable endow;
            Vec3 at;

            if (target instanceof EntityIota entityIota) {
                Entity entity = entityIota.getEntity();
                env.assertEntityInRange(entity);
                at = entity.position();
                endow = () -> MediaGrants.grant(entity, media);
            } else if (target instanceof Vec3Iota vecIota) {
                Vec3 vec = vecIota.getVec3();
                env.assertVecInRange(vec);
                BlockPos pos = BlockPos.containing(vec);
                if (level.getBlockEntity(pos) == null) {
                    throw new MishapBadLocation(vec, "hexwright_endowment_target");
                }
                at = Vec3.atCenterOf(pos);
                endow = () -> MediaGrants.grant(level, pos, media);
            } else {
                throw MishapInvalidIota.ofType(target, 0, "hexwright.endowment_target");
            }

            CastingEnvironmentMediaAccessor source = (CastingEnvironmentMediaAccessor) env;
            if (source.hexwright$extractMediaEnvironment(media, true) > 0) {
                throw new MishapNotEnoughMedia(media);
            }

            return new Result(new RenderedSpell() {
                @Override
                public void cast(CastingEnvironment castEnv) {
                    source.hexwright$extractMediaEnvironment(media, false);
                    endow.run();
                }

                @Override
                public CastingImage cast(CastingEnvironment castEnv, CastingImage image) {
                    cast(castEnv);
                    return image;
                }
            }, 0L, List.of(ParticleSpray.burst(at, 1.0, 20)), 1L);
        }
    };
}
