package com.bluup.hexwright.server.hexpatterns;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.ParticleSpray;
import at.petrak.hexcasting.api.casting.RenderedSpell;
import at.petrak.hexcasting.api.casting.castables.ConstMediaAction;
import at.petrak.hexcasting.api.casting.castables.SpellAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.NullIota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.casting.mishaps.MishapOthersName;
import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.harmonic.HarmonicNetwork;
import com.bluup.hexwright.server.harmonic.HarmonicResolution;
import com.bluup.hexwright.server.network.ResonanceIota;
import net.minecraft.core.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class HarmonicActions {

    private HarmonicActions() {
    }

    public static void register() {
        Registry<ActionRegistryEntry> registry = IXplatAbstractions.INSTANCE.getActionRegistry();

        Registry.register(registry, Hexwright.id("harmonic_publish"),
            new ActionRegistryEntry(HexPattern.fromAngles("qqaeawqqq", HexDir.NORTH_EAST), HARMONIC_PUBLISH));
        Registry.register(registry, Hexwright.id("harmonic_set_state"),
            new ActionRegistryEntry(HexPattern.fromAngles("qqaeawaeedqd", HexDir.NORTH_EAST), HARMONIC_SET_STATE));
        Registry.register(registry, Hexwright.id("carried_resonance"),
            new ActionRegistryEntry(HexPattern.fromAngles("qqaeawqqaq", HexDir.NORTH_EAST), CARRIED_RESONANCE));
    }

    private static final SpellAction HARMONIC_PUBLISH = new Broadcast(false);

    private static final SpellAction HARMONIC_SET_STATE = new Broadcast(true);

    private static final ConstMediaAction CARRIED_RESONANCE = new HexwrightConstMediaAction() {
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
            ServerPlayer caster = env.getCaster();
            if (caster == null) {
                return List.of(new NullIota());
            }
            String networkKey = HarmonicResolution.carriedNetworkOrNull(caster);
            return List.of(networkKey == null ? new NullIota() : new ResonanceIota(networkKey));
        }
    };

    private static final class Broadcast extends HexwrightSpellAction {

        private final boolean retain;

        Broadcast(boolean retain) {
            this.retain = retain;
        }

        @Override
        public int getArgc() {
            return 3;
        }

        @Override
        public Result execute(List<? extends Iota> args, CastingEnvironment env) {
            MinecraftServer server = env.getWorld().getServer();

            int harmonic = HarmonicResolution.requireHarmonic(args, 1);
            HarmonicResolution.requireSendablePayload(args, 2);
            Iota payload = args.get(2);

            Player trueName = MishapOthersName.getTrueNameFromDatum(payload, null);
            if (trueName != null) {
                throw new MishapOthersName(trueName);
            }

            Vec3 where = env.mishapSprayPos();
            String networkKey = HarmonicResolution.requireNetwork(env, args, 0);
            HarmonicResolution.requireActiveExchange(server, where, networkKey);

            return new Result(new RenderedSpell() {
                @Override
                public void cast(CastingEnvironment castEnv) {
                    if (retain) {
                        HarmonicNetwork.setState(server, networkKey, harmonic, payload);
                    } else {
                        HarmonicNetwork.publish(server, networkKey, harmonic, payload);
                    }
                }

                @Override
                public CastingImage cast(CastingEnvironment castEnv, CastingImage image) {
                    cast(castEnv);
                    return image;
                }
            }, MediaConstants.DUST_UNIT, List.of(ParticleSpray.burst(where, 0.5, 10)), 1L);
        }
    }
}
