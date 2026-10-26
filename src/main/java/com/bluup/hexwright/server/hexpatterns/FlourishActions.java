package com.bluup.hexwright.server.hexpatterns;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.ParticleSpray;
import at.petrak.hexcasting.api.casting.RenderedSpell;
import at.petrak.hexcasting.api.casting.castables.SpellAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.env.PlayerBasedCastEnv;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadCaster;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadOffhandItem;
import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.flourish.FlourishItem;
import com.bluup.hexwright.server.flourish.FlourishState;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

public final class FlourishActions {

    private FlourishActions() {
    }

    public static void register() {
        Registry.register(IXplatAbstractions.INSTANCE.getActionRegistry(),
            Hexwright.id("internalize_flourish"),
            new ActionRegistryEntry(HexPattern.fromAngles("awddwqawqwawqeee", HexDir.EAST),
                INTERNALIZE_FLOURISH));
    }

    private static final SpellAction INTERNALIZE_FLOURISH = new HexwrightSpellAction() {
        @Override
        public int getArgc() {
            return 0;
        }

        @Override
        public Result execute(List<? extends Iota> args, CastingEnvironment env) {
            ServerPlayer caster = env instanceof PlayerBasedCastEnv ? env.getCaster() : null;
            if (caster == null) {
                throw new MishapBadCaster();
            }

            InteractionHand hand = handHolding(caster, env, stack -> stack.getItem() instanceof FlourishItem);
            if (hand == null) {
                boolean wearing = FlourishState.get(caster.server).flourish(caster.getUUID()) != null;
                hand = wearing ? handHolding(caster, env, ItemStack::isEmpty) : null;
                if (hand == null) {
                    throw MishapBadOffhandItem.of(caster.getItemInHand(env.getOtherHand()), "hexwright.flourish");
                }
            }
            InteractionHand from = hand;

            return new Result(new RenderedSpell() {
                @Override
                public void cast(CastingEnvironment castEnv) {
                    swap(caster, from);
                }

                @Override
                public CastingImage cast(CastingEnvironment castEnv, CastingImage image) {
                    cast(castEnv);
                    return image;
                }
            }, MediaConstants.DUST_UNIT,
                List.of(ParticleSpray.burst(caster.position().add(0.0, caster.getBbHeight() * 0.6, 0.0), 1.0, 20)),
                1L);
        }
    };

    private static @Nullable InteractionHand handHolding(ServerPlayer caster, CastingEnvironment env,
                                                         Predicate<ItemStack> test) {
        for (InteractionHand hand : new InteractionHand[]{env.getOtherHand(), env.getCastingHand()}) {
            if (test.test(caster.getItemInHand(hand))) {
                return hand;
            }
        }
        return null;
    }

    private static void swap(ServerPlayer caster, InteractionHand hand) {
        FlourishState state = FlourishState.get(caster.server);
        ItemStack held = caster.getItemInHand(hand);
        ItemStack incoming = held.isEmpty() ? ItemStack.EMPTY : held.copyWithCount(1);
        ItemStack outgoing = state.wornItem(caster.getUUID());
        held.shrink(1);
        state.wear(caster.getUUID(), incoming);
        if (outgoing.isEmpty()) {
            return;
        }
        if (caster.getItemInHand(hand).isEmpty()) {
            caster.setItemInHand(hand, outgoing);
        } else {
            caster.getInventory().placeItemBackInInventory(outgoing);
        }
    }
}
