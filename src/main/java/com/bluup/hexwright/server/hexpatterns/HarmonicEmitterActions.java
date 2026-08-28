package com.bluup.hexwright.server.hexpatterns;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.OperatorUtils;
import at.petrak.hexcasting.api.casting.ParticleSpray;
import at.petrak.hexcasting.api.casting.RenderedSpell;
import at.petrak.hexcasting.api.casting.castables.ConstMediaAction;
import at.petrak.hexcasting.api.casting.castables.SpellAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.NullIota;
import at.petrak.hexcasting.api.casting.iota.Vec3Iota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadLocation;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import at.petrak.hexcasting.api.casting.mishaps.MishapOthersName;
import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.block.HarmonicEmitterBlockEntity;
import com.bluup.hexwright.server.harmonic.HarmonicEmitterCastEnv;
import com.bluup.hexwright.server.harmonic.HarmonicResolution;
import com.bluup.hexwright.server.network.ResonanceNames;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class HarmonicEmitterActions {

    private HarmonicEmitterActions() {
    }

    public static void register() {
        Registry<ActionRegistryEntry> registry = IXplatAbstractions.INSTANCE.getActionRegistry();

        Registry.register(registry, Hexwright.id("emitters_gambit"),
            new ActionRegistryEntry(HexPattern.fromAngles("qqqqqwddadadd", HexDir.SOUTH_WEST),
                EMITTERS_GAMBIT));
        Registry.register(registry, Hexwright.id("emitters_reflection"),
            new ActionRegistryEntry(HexPattern.fromAngles("qqqqqwddadaddqd", HexDir.SOUTH_WEST),
                EMITTERS_REFLECTION));
    }

    private static final ConstMediaAction EMITTERS_REFLECTION = new HexwrightConstMediaAction() {
        @Override
        public int getArgc() {
            return 0;
        }

        @Override
        public long getMediaCost() {
            return 0;
        }

        @Override
        public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
            if (!(env instanceof HarmonicEmitterCastEnv emitterEnv)) {
                throw new MishapBadLocation(env.mishapSprayPos(), "hexwright_no_emitter_cast");
            }
            return List.of(new Vec3Iota(Vec3.atCenterOf(emitterEnv.getEmitter().getBlockPos())));
        }
    };

    private static final SpellAction EMITTERS_GAMBIT = new HexwrightSpellAction() {
        @Override
        public int getArgc() {
            return 3;
        }

        @Override
        public Result execute(List<? extends Iota> args, CastingEnvironment env) {
            Vec3 vec = OperatorUtils.getVec3(args, 0, getArgc());
            env.assertVecInRange(vec);
            BlockPos pos = BlockPos.containing(vec);
            if (!(env.getWorld().getBlockEntity(pos) instanceof HarmonicEmitterBlockEntity emitter)) {
                throw new MishapBadLocation(vec, "hexwright_not_emitter");
            }

            int harmonic = HarmonicResolution.requireHarmonic(args, 1);

            String networkKey = emitter.networkKey();
            if (networkKey == null) {
                throw new MishapBadLocation(Vec3.atCenterOf(pos),
                    emitter.dockedKey().isEmpty() ? "hexwright_emitter_unkeyed" : "hexwright_emitter_unattuned");
            }

            ServerLevel level = env.getWorld();
            if (!emitter.inTowerRange()) {
                throw new MishapBadLocation(Vec3.atCenterOf(pos), "hexwright_emitter_out_of_range");
            }

            Iota raw = args.get(2);
            Runnable write;
            if (raw instanceof NullIota) {
                write = emitter::untune;
            } else {
                if (!StoredHex.isHex(raw)) {
                    throw MishapInvalidIota.ofType(raw, 0, "hexwright.subscription");
                }
                Player trueName = MishapOthersName.getTrueNameFromDatum(raw, null);
                if (trueName != null) {
                    throw new MishapOthersName(trueName);
                }
                write = () -> {
                    ResonanceNames.nameOrAssign(level.getServer(), networkKey);
                    emitter.tune(harmonic, raw);
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
