package com.bluup.hexwright.server.harmonic;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.iota.DoubleIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.NullIota;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadLocation;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import com.bluup.hexwright.server.network.ResonanceIota;
import com.bluup.hexwright.server.network.ResonantKeyItem;
import com.bluup.hexwright.server.network.ResonantRingItem;
import com.bluup.hexwright.server.network.ResonantRingNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class HarmonicResolution {

    private static final double EPSILON = 0.0001;

    private HarmonicResolution() {
    }

    public static String requireNetwork(CastingEnvironment env, List<? extends Iota> args, int index) {
        Iota raw = args.get(index);
        if (raw instanceof ResonanceIota resonance) {
            return resonance.getNetworkKey();
        }
        if (!(raw instanceof NullIota)) {
            throw MishapInvalidIota.ofType(raw, args.size() - 1 - index, "hexwright.resonance");
        }
        ServerPlayer caster = env.getCaster();
        if (caster == null) {
            throw new MishapBadLocation(env.mishapSprayPos(), "hexwright_no_carrier");
        }
        return carriedNetwork(caster);
    }

    public static String carriedNetwork(ServerPlayer caster) {
        String networkKey = carriedNetworkOrNull(caster);
        if (networkKey != null) {
            return networkKey;
        }

        boolean sawResonantItem = !ResonantRingNetwork.worn(caster).isEmpty();
        for (InteractionHand hand : InteractionHand.values()) {
            sawResonantItem |= caster.getItemInHand(hand).getItem() instanceof ResonantKeyItem;
        }
        throw new MishapBadLocation(caster.position(),
            sawResonantItem ? "hexwright_unattuned" : "hexwright_no_resonance");
    }

    public static @Nullable String carriedNetworkOrNull(ServerPlayer caster) {
        for (ItemStack ring : ResonantRingNetwork.worn(caster)) {
            String networkKey = ResonantRingItem.networkKey(ring);
            if (networkKey != null) {
                return networkKey;
            }
        }

        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = caster.getItemInHand(hand);
            if (!(held.getItem() instanceof ResonantKeyItem)) {
                continue;
            }
            String networkKey = ResonantKeyItem.networkKey(held);
            if (networkKey != null) {
                return networkKey;
            }
        }

        return null;
    }

    public static void requireActiveExchange(MinecraftServer server, Vec3 where, String networkKey) {
        HarmonicExchangeState state = HarmonicExchangeState.get(server);
        if (state.exchangeFor(networkKey) == null) {
            throw new MishapBadLocation(where, "hexwright_no_exchange");
        }
        if (!HarmonicNetwork.isActive(server, networkKey)) {
            throw new MishapBadLocation(where, "hexwright_exchange_inactive");
        }
    }

    public static int requireHarmonic(List<? extends Iota> args, int index) {
        Iota raw = args.get(index);
        if (!(raw instanceof DoubleIota number)) {
            throw MishapInvalidIota.ofType(raw, args.size() - 1 - index, "hexwright.harmonic");
        }
        double value = number.getDouble();
        long rounded = Math.round(value);
        if (Math.abs(value - rounded) > EPSILON) {
            throw MishapInvalidIota.ofType(raw, args.size() - 1 - index, "hexwright.harmonic");
        }
        if (rounded < 0 || rounded >= HarmonicExchangeState.HARMONIC_COUNT) {
            throw MishapInvalidIota.ofType(raw, args.size() - 1 - index, "hexwright.harmonic_range");
        }
        return (int) rounded;
    }

    public static void requireSendablePayload(List<? extends Iota> args, int index) {
        Iota payload = args.get(index);
        if (HarmonicNetwork.isPayloadTooLarge(payload)) {
            throw MishapInvalidIota.ofType(payload, args.size() - 1 - index, "hexwright.harmonic_payload");
        }
    }
}
