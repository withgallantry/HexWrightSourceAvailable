package com.bluup.hexwright.server.hexweave;

import at.petrak.hexcasting.api.casting.iota.EntityIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.server.hexweave.HexweaveCasting.Activation;
import net.minecraft.ChatFormatting;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

public final class WovenOrbCasting {

    public static final ResourceLocation FIRED_S2C = Hexwright.id("woven_orb_fired");

    public enum Outcome {
        BLANK,
        BROKEN,
        ACTIVATED
    }

    public record Result(Outcome outcome, @Nullable Activation activation, @Nullable WeaveExecutionBudget budget,
                         boolean unravelled) {
    }

    private WovenOrbCasting() {
    }

    public static Result activate(ServerPlayer holder, InteractionHand hand, ItemStack orb) {
        return activate(holder, hand, orb, WeaveTrigger.Kind.ON_USE, "", new EntityIota(holder));
    }

    public static Result activate(ServerPlayer holder, InteractionHand hand, ItemStack orb, WeaveTrigger.Kind event,
                                  String key, @Nullable Iota seed) {
        return activate(holder, hand, orb, event.id() + (key.isEmpty() ? "" : " " + key), event.deliberate(),
            weave -> weave.starts(event, key), seed);
    }

    public static Result activate(ServerPlayer holder, InteractionHand hand, ItemStack orb, WeaveTrigger.Kind event,
                                  int reach, @Nullable Iota seed) {
        return activate(holder, hand, orb, event.id() + " within " + reach, event.deliberate(),
            weave -> weave.starts(event, reach), seed);
    }

    private static Result activate(ServerPlayer holder, InteractionHand hand, ItemStack orb, String event,
                                   boolean deliberate, Function<Hexweave, List<Integer>> starts,
                                   @Nullable Iota seed) {
        if (!WovenOrbItem.hasWeave(orb)) {
            return new Result(Outcome.BLANK, null, null, false);
        }
        Hexweave weave;
        try {
            weave = WovenOrbItem.weave(orb);
        } catch (IllegalArgumentException e) {
            holder.displayClientMessage(Component.translatable("message.hexwright.woven_orb.broken")
                .withStyle(ChatFormatting.GRAY), true);
            return new Result(Outcome.BROKEN, null, null, false);
        }

        WeaveExecutionBudget budget = new WeaveExecutionBudget();
        Activation activation = HexweaveCasting.activate(weave, starts.apply(weave), holder.serverLevel(),
            () -> new WovenOrbCastEnv(holder, hand, deliberate), seed, budget);

        boolean unravelled = activation.stop() == HexweaveCasting.Stop.INVOCATION_CAP;
        if (unravelled) {
            holder.displayClientMessage(Component.translatable("message.hexwright.woven_orb.unravelled")
                .withStyle(ChatFormatting.DARK_PURPLE), true);
        }
        HexwrightDebug.log(HexwrightDebug.WEAVE, "[weave] Woven Orb fired {} for {}: {} runs, stopped {}",
            event, holder.getScoreboardName(), activation.runs().size(), activation.stop());
        holder.level().playSound(null, holder.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
            SoundSource.PLAYERS, 0.8f, 1.2f);
        sendFired(holder, hand);
        return new Result(Outcome.ACTIVATED, activation, budget, unravelled);
    }

    private static void sendFired(ServerPlayer holder, InteractionHand hand) {
        ServerPlayNetworking.send(holder, FIRED_S2C, firedBuf(holder, hand));
        for (ServerPlayer tracking : PlayerLookup.tracking(holder)) {
            if (tracking != holder) {
                ServerPlayNetworking.send(tracking, FIRED_S2C, firedBuf(holder, hand));
            }
        }
    }

    private static FriendlyByteBuf firedBuf(ServerPlayer holder, InteractionHand hand) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(holder.getId());
        buf.writeEnum(hand);
        return buf;
    }
}
