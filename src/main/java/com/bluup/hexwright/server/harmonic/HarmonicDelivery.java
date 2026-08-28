package com.bluup.hexwright.server.harmonic;

import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.server.block.HarmonicEmitterBlockEntity;
import com.bluup.hexwright.server.network.ResonantRingCastEnv;
import com.bluup.hexwright.server.network.ResonantRingItem;
import com.bluup.hexwright.server.network.ResonantRingNetwork;
import com.bluup.hexwright.server.network.RingSubscriptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class HarmonicDelivery {

    private static final int MAX_ACTIVATIONS_PER_TICK = 64;

    private static final int MAX_DEPTH = 4;

    private static final int MAX_QUEUED = 4096;

    public sealed interface Target {
    }

    public record Ring(HarmonicSubscriptions.Subscriber subscriber) implements Target {
    }

    public record Emitter(HarmonicEmitterRegistry.Address address) implements Target {
    }

    private record Envelope(Target target, CompoundTag payload, int depth) {
    }

    private static final Deque<Envelope> QUEUE = new ArrayDeque<>();

    private static int currentDepth = 0;

    private HarmonicDelivery() {
    }

    public static boolean enqueue(Target target, Iota payload) {
        return enqueueSerialised(target, IotaType.serialize(payload));
    }

    public static boolean enqueueSerialised(Target target, CompoundTag payload) {
        int depth = currentDepth + 1;
        if (depth > MAX_DEPTH || QUEUE.size() >= MAX_QUEUED) {
            return false;
        }
        QUEUE.add(new Envelope(target, payload.copy(), depth));
        return true;
    }

    public static void drain(MinecraftServer server) {
        if (QUEUE.isEmpty()) {
            return;
        }
        int fired = 0;
        while (fired < MAX_ACTIVATIONS_PER_TICK) {
            Envelope envelope = QUEUE.poll();
            if (envelope == null) {
                break;
            }
            fired++;
            currentDepth = envelope.depth();
            try {
                fire(server, envelope);
            } catch (RuntimeException ignored) {
            } finally {
                currentDepth = 0;
            }
        }
    }

    private static void fire(MinecraftServer server, Envelope envelope) {
        if (envelope.target() instanceof Ring ring) {
            fireRing(server, ring.subscriber(), envelope.payload());
        } else if (envelope.target() instanceof Emitter emitter) {
            fireEmitter(server, emitter.address(), envelope.payload());
        }
    }

    private static void fireRing(MinecraftServer server, HarmonicSubscriptions.Subscriber subscriber,
                                 CompoundTag payloadTag) {
        ServerPlayer bearer = server.getPlayerList().getPlayer(subscriber.player());
        if (bearer == null) {
            return;
        }

        List<ItemStack> rings = ResonantRingNetwork.worn(bearer);
        if (subscriber.ringIndex() >= rings.size()) {
            return;
        }
        ItemStack ring = rings.get(subscriber.ringIndex());
        if (!subscriber.networkKey().equals(ResonantRingItem.networkKey(ring))) {
            return;
        }
        if (!HarmonicNetwork.isActive(server, subscriber.networkKey())) {
            return;
        }

        ServerLevel level = bearer.serverLevel();
        List<Iota> hex = RingSubscriptions.hexFor(ring, subscriber.harmonic(), level);
        if (hex == null || hex.isEmpty()) {
            return;
        }

        Iota payload = IotaType.deserialize(payloadTag.copy(), level);
        CastingVM templateVm = IXplatAbstractions.INSTANCE.getStaffcastVM(bearer, InteractionHand.MAIN_HAND);
        CastingImage seededImage = templateVm.getImage().copy(
            List.of(payload),
            0,
            List.of(),
            false,
            0L,
            new CompoundTag()
        );
        CastingVM vm = new CastingVM(seededImage, new ResonantRingCastEnv(bearer, InteractionHand.MAIN_HAND));
        vm.queueExecuteAndWrapIotas(new ArrayList<>(hex), level);

        level.playSound(null, bearer.blockPosition(),
            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.4f, 1.8f);
    }

    private static void fireEmitter(MinecraftServer server, HarmonicEmitterRegistry.Address address,
                                    CompoundTag payloadTag) {
        ServerLevel level = server.getLevel(address.dimension());
        if (level == null || !level.isLoaded(address.pos())) {
            return;
        }
        if (!(level.getBlockEntity(address.pos()) instanceof HarmonicEmitterBlockEntity emitter)) {
            return;
        }
        if (!emitter.listensTo(address.networkKey(), address.harmonic())) {
            return;
        }
        if (!HarmonicNetwork.isActive(server, address.networkKey())) {
            return;
        }

        Iota payload = IotaType.deserialize(payloadTag.copy(), level);
        HarmonicEmitterCasting.cast(level, emitter, payload);
    }
}
