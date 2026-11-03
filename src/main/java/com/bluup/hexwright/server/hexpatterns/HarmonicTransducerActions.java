package com.bluup.hexwright.server.hexpatterns;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.OperatorUtils;
import at.petrak.hexcasting.api.casting.ParticleSpray;
import at.petrak.hexcasting.api.casting.RenderedSpell;
import at.petrak.hexcasting.api.casting.castables.SpellAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.NullIota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadLocation;
import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.harmonic.HarmonicChannelTarget;
import com.bluup.hexwright.server.harmonic.HarmonicResolution;
import com.bluup.hexwright.server.network.ResonanceNames;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class HarmonicTransducerActions {

    private HarmonicTransducerActions() {
    }

    public static void register() {
        Registry<ActionRegistryEntry> registry = IXplatAbstractions.INSTANCE.getActionRegistry();

        Registry.register(registry, Hexwright.id("transducers_gambit"),
            new ActionRegistryEntry(HexPattern.fromAngles("qqqqqwddadada", HexDir.SOUTH_WEST),
                TRANSDUCERS_GAMBIT));
    }

    private static final SpellAction TRANSDUCERS_GAMBIT = new HexwrightSpellAction() {
        @Override
        public int getArgc() {
            return 2;
        }

        @Override
        public Result execute(List<? extends Iota> args, CastingEnvironment env) {
            Vec3 vec = OperatorUtils.getVec3(args, 0, getArgc());
            env.assertVecInRange(vec);
            BlockPos pos = BlockPos.containing(vec);
            if (!(env.getWorld().getBlockEntity(pos) instanceof HarmonicChannelTarget target)) {
                throw new MishapBadLocation(vec, "hexwright_not_tunable");
            }

            String refusal = target.tuningRefusal();
            if (refusal != null) {
                throw new MishapBadLocation(Vec3.atCenterOf(pos), refusal);
            }

            ServerLevel level = env.getWorld();
            Runnable write;
            if (args.get(1) instanceof NullIota) {
                write = target::untune;
            } else {
                int harmonic = HarmonicResolution.requireHarmonic(args, 1);
                write = () -> {
                    for (String networkKey : target.networkKeys()) {
                        ResonanceNames.nameOrAssign(level.getServer(), networkKey);
                    }
                    target.tune(harmonic);
                };
            }

            return new Result(new RenderedSpell() {
                @Override
                public void cast(CastingEnvironment castEnv) {
                    write.run();
                }

                @Override
                public CastingImage cast(CastingEnvironment castEnv, CastingImage image) {
                    cast(castEnv);
                    return image;
                }
            }, MediaConstants.DUST_UNIT, List.of(ParticleSpray.burst(Vec3.atCenterOf(pos), 0.5, 10)), 1L);
        }
    };
}
