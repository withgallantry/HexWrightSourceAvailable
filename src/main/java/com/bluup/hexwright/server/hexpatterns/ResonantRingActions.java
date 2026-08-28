package com.bluup.hexwright.server.hexpatterns;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.ParticleSpray;
import at.petrak.hexcasting.api.casting.RenderedSpell;
import at.petrak.hexcasting.api.casting.castables.SpellAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.NullIota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadCaster;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadLocation;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadOffhandItem;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import at.petrak.hexcasting.api.casting.mishaps.MishapOthersName;
import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.harmonic.HarmonicResolution;
import com.bluup.hexwright.server.network.ResonantRingItem;
import com.bluup.hexwright.server.network.RingSubscriptions;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class ResonantRingActions {

    private ResonantRingActions() {
    }

    public static void register() {
        Registry.register(IXplatAbstractions.INSTANCE.getActionRegistry(),
            Hexwright.id("ring_bearers_gambit"),
            new ActionRegistryEntry(HexPattern.fromAngles("qqqqqwdewed", HexDir.SOUTH_WEST),
                RING_BEARERS_GAMBIT));
    }

    private static final SpellAction RING_BEARERS_GAMBIT = new HexwrightSpellAction() {
        @Override
        public int getArgc() {
            return 2;
        }

        @Override
        public Result execute(List<? extends Iota> args, CastingEnvironment env) {
            ServerPlayer caster = env.getCaster();
            if (caster == null) {
                throw new MishapBadCaster();
            }

            ItemStack ring = caster.getItemInHand(env.getOtherHand());
            if (!(ring.getItem() instanceof ResonantRingItem)) {
                throw MishapBadOffhandItem.of(ring, "hexwright.resonant_ring");
            }
            if (ResonantRingItem.networkKey(ring) == null) {
                throw MishapBadOffhandItem.of(ring, "hexwright.resonant_ring_attuned");
            }

            int harmonic = HarmonicResolution.requireHarmonic(args, 0);

            Iota raw = args.get(1);
            Runnable write;
            if (raw instanceof NullIota) {
                write = () -> RingSubscriptions.clear(ring, harmonic);
            } else {
                if (!StoredHex.isHex(raw)) {
                    throw MishapInvalidIota.ofType(raw, 0, "hexwright.subscription");
                }
                Player trueName = MishapOthersName.getTrueNameFromDatum(raw, null);
                if (trueName != null) {
                    throw new MishapOthersName(trueName);
                }
                if (RingSubscriptions.count(ring) >= RingSubscriptions.MAX_SUBSCRIPTIONS
                    && !alreadyListens(ring, harmonic)) {
                    throw new MishapBadLocation(caster.position(), "hexwright_ring_full");
                }
                write = () -> RingSubscriptions.set(ring, harmonic, raw);
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
            }, MediaConstants.DUST_UNIT, List.of(ParticleSpray.burst(caster.position(), 0.5, 10)), 1L);
        }

        private boolean alreadyListens(ItemStack ring, int harmonic) {
            for (RingSubscriptions.Subscription subscription : RingSubscriptions.list(ring)) {
                if (subscription.harmonic() == harmonic) {
                    return true;
                }
            }
            return false;
        }
    };
}
