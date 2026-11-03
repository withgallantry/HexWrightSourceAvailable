package com.bluup.hexwright.server.malediction;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.OperatorUtils;
import at.petrak.hexcasting.api.casting.ParticleSpray;
import at.petrak.hexcasting.api.casting.RenderedSpell;
import at.petrak.hexcasting.api.casting.castables.Action;
import at.petrak.hexcasting.api.casting.castables.ConstMediaAction;
import at.petrak.hexcasting.api.casting.castables.SpellAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.env.PlayerBasedCastEnv;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.iota.DoubleIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadCaster;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadLocation;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadOffhandItem;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.common.malediction.MaledictionFinish;
import com.bluup.hexwright.common.malediction.MaledictionForm;
import com.bluup.hexwright.compat.ephemera.EphemeraStatus;
import com.bluup.hexwright.server.flourish.CastFlourishes;
import com.bluup.hexwright.server.hexpatterns.HexwrightConstMediaAction;
import com.bluup.hexwright.server.hexpatterns.HexwrightSpellAction;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

public final class MaledictionActions {

    private MaledictionActions() {
    }

    public static void register() {
        Registry.register(IXplatAbstractions.INSTANCE.getActionRegistry(), Hexwright.id("internalize_malediction"),
            new ActionRegistryEntry(HexPattern.fromAngles("awddwqawqwawdqqq", HexDir.EAST), INTERNALIZE));
        Registry.register(IXplatAbstractions.INSTANCE.getActionRegistry(), Hexwright.id("utter_malediction"),
            new ActionRegistryEntry(HexPattern.fromAngles("qqqqqwdeddwqa", HexDir.SOUTH_EAST), UTTER));
        Registry.register(IXplatAbstractions.INSTANCE.getActionRegistry(), Hexwright.id("malediction_readiness"),
            new ActionRegistryEntry(HexPattern.fromAngles("qqqqqwdeddwqaw", HexDir.SOUTH_EAST), READINESS));
    }

    private static ServerPlayer caster(CastingEnvironment env) {
        ServerPlayer caster = env instanceof PlayerBasedCastEnv ? env.getCaster() : null;
        if (caster == null) {
            throw new MishapBadCaster();
        }
        return caster;
    }

    private static int slot(List<? extends Iota> args, int idx, int argc) {
        return OperatorUtils.getPositiveIntUnderInclusive(args, idx, MaledictionState.SLOTS - 1, argc);
    }

    private static final SpellAction INTERNALIZE = new HexwrightSpellAction() {
        @Override
        public int getArgc() {
            return 1;
        }

        @Override
        public Result execute(List<? extends Iota> args, CastingEnvironment env) {
            ServerPlayer caster = caster(env);
            int slot = slot(args, 0, getArgc());
            InteractionHand hand = handHolding(caster, env, MaledictionBlankItem::isFused);
            if (hand != null && !MaledictionBlankItem.mayUse(caster.getItemInHand(hand), caster)) {
                throw MishapBadOffhandItem.of(caster.getItemInHand(hand), "hexwright.own_malediction");
            }
            if (hand == null) {
                boolean held = !MaledictionState.get(caster.server).slot(caster.getUUID(), slot).isEmpty();
                hand = held ? handHolding(caster, env, ItemStack::isEmpty) : null;
                if (hand == null) {
                    throw MishapBadOffhandItem.of(caster.getItemInHand(env.getOtherHand()), "hexwright.malediction");
                }
            }
            InteractionHand from = hand;
            return new Result(new RenderedSpell() {
                @Override
                public void cast(CastingEnvironment castEnv) {
                    swap(caster, from, slot);
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

    private static final SpellAction UTTER_STRIKING = uttering(false);
    private static final SpellAction UTTER_AFFLICTING = uttering(true);

    private static final Action UTTER = (env, image, continuation) -> {
        List<Iota> stack = image.getStack();
        boolean afflicting = stack.size() >= 2 && EphemeraStatus.statusOf(stack.get(stack.size() - 2)) != null;
        return (afflicting ? UTTER_AFFLICTING : UTTER_STRIKING).operate(env, image, continuation);
    };

    private static SpellAction uttering(boolean afflicting) {
        return new HexwrightSpellAction() {
            @Override
            public int getArgc() {
                return afflicting ? 3 : 2;
            }

            @Override
            public Result execute(List<? extends Iota> args, CastingEnvironment env) {
                ServerPlayer caster = caster(env);
                int slot = slot(args, getArgc() - 1, getArgc());
                ItemStack held = MaledictionState.get(caster.server).slot(caster.getUUID(), slot);
                MaledictionForm form = MaledictionBlankItem.form(held);
                MaledictionFinish finish = MaledictionBlankItem.finish(held);
                if (form == null || finish == null) {
                    throw new MishapBadLocation(env.mishapSprayPos(), "hexwright_malediction_empty");
                }
                MobEffect status = afflicting ? afflictingStatus(form, args.get(1)) : null;
                long now = caster.server.overworld().getGameTime();
                if (MaledictionState.get(caster.server).charges(caster.getUUID(), form, now) < 1) {
                    throw new MishapBadLocation(env.mishapSprayPos(), "hexwright_malediction_spent");
                }
                MaledictionCasting.Delivery delivery = MaledictionCasting.prepare(form, finish, args.get(0),
                    getArgc() - 1, status, env, caster);
                ItemStack flourish = MaledictionBlankItem.flourish(held);
                ParticleSpray spray = ParticleSpray.cloud(caster.getEyePosition(), 0.5, 12);
                return new Result(new RenderedSpell() {
                    @Override
                    public void cast(CastingEnvironment castEnv) {
                        if (!MaledictionState.get(caster.server).spend(caster.getUUID(), form,
                            caster.server.overworld().getGameTime())) {
                            return;
                        }
                        HexwrightDebug.log(HexwrightDebug.CASTING, "[malediction] {} uttered {} {}",
                            caster.getGameProfile().getName(), finish.id(), form.id());
                        if (!flourish.isEmpty()) {
                            CastFlourishes.play(castEnv, spray, flourish);
                        }
                        delivery.deliver(caster.serverLevel(), caster);
                    }

                    @Override
                    public CastingImage cast(CastingEnvironment castEnv, CastingImage image) {
                        cast(castEnv);
                        return image;
                    }
                }, (long) form.dustCost() * MediaConstants.DUST_UNIT,
                    flourish.isEmpty() ? List.of(spray) : List.of(), 1L);
            }
        };
    }

    private static MobEffect afflictingStatus(MaledictionForm form, Iota named) {
        if (form != MaledictionForm.DOOM) {
            throw MishapInvalidIota.of(named, 1, "hexwright.malediction_status_needs_doom");
        }
        MobEffect status = EphemeraStatus.statusOf(named);
        if (status == null || !MaledictionDooms.mayLandAs(status)) {
            throw MishapInvalidIota.of(named, 1, "hexwright.doom_status");
        }
        return status;
    }

    private static final ConstMediaAction READINESS = new HexwrightConstMediaAction() {
        @Override
        public int getArgc() {
            return 1;
        }

        @Override
        public long getMediaCost() {
            return 0;
        }

        @Override
        public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
            ServerPlayer caster = caster(env);
            int slot = slot(args, 0, getArgc());
            MaledictionState state = MaledictionState.get(caster.server);
            MaledictionForm form = MaledictionBlankItem.form(state.slot(caster.getUUID(), slot));
            int ready = form == null ? 0
                : state.charges(caster.getUUID(), form, caster.server.overworld().getGameTime());
            return List.of(new DoubleIota(ready));
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

    private static void swap(ServerPlayer caster, InteractionHand hand, int slot) {
        MaledictionState state = MaledictionState.get(caster.server);
        ItemStack held = caster.getItemInHand(hand);
        ItemStack incoming = held.isEmpty() ? ItemStack.EMPTY : held.copyWithCount(1);
        if (!incoming.isEmpty()) {
            MaledictionBlankItem.bind(incoming, caster);
        }
        held.shrink(1);
        ItemStack outgoing = state.swap(caster.getUUID(), slot, incoming);
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
