package com.bluup.hexwright.server.powerorb;

import at.petrak.hexcasting.api.casting.ParticleSpray;
import at.petrak.hexcasting.api.casting.RenderedSpell;
import at.petrak.hexcasting.api.casting.castables.Action;
import at.petrak.hexcasting.api.casting.castables.SpecialHandler;
import at.petrak.hexcasting.api.casting.castables.SpellAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadCaster;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadLocation;
import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.hexpatterns.HexwrightSpellAction;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class PowerOrbActions {

    private static final long TOGGLE_COST = MediaConstants.DUST_UNIT / 10;

    private PowerOrbActions() {
    }

    public static void register() {
        Registry.register(IXplatAbstractions.INSTANCE.getSpecialHandlerRegistry(),
            Hexwright.id("power_orb_sigil"), SIGIL_FACTORY);
    }

    private static final SpecialHandler.Factory<OrbSigil> SIGIL_FACTORY = (pattern, env) -> {
        if (!(env.getCastingEntity() instanceof ServerPlayer wearer)) {
            return null;
        }
        ItemStack orb = PowerOrbSlot.worn(wearer);
        if (!(orb.getItem() instanceof PowerOrbItem item) || !PowerOrbData.matches(orb, pattern)) {
            return null;
        }
        return new OrbSigil(item.power());
    };

    private record OrbSigil(PowerOrbPower power) implements SpecialHandler {
        @Override
        public Action act() {
            return SIGIL_ACTION;
        }

        @Override
        public Component getName() {
            return Component.translatable("hexcasting.spell.hexwright.power_orb_sigil",
                Component.translatable(power.subtitleKey()));
        }
    }

    private static final SpellAction SIGIL_ACTION = new HexwrightSpellAction() {
        @Override
        public int getArgc() {
            return 0;
        }

        @Override
        public Result execute(List<? extends Iota> args, CastingEnvironment env) {
            if (!(env.getCastingEntity() instanceof ServerPlayer wearer)) {
                throw new MishapBadCaster();
            }
            ItemStack orb = PowerOrbSlot.worn(wearer);
            if (!(orb.getItem() instanceof PowerOrbItem item)) {
                throw new MishapBadLocation(env.mishapSprayPos(), "hexwright_no_power_orb");
            }
            return switch (item.power()) {
                case GOLEM -> golem(env, wearer, orb);
                case SANCTUARY -> sanctuary(env, wearer, orb);
            };
        }
    };

    private static SpellAction.Result golem(CastingEnvironment env, ServerPlayer wearer, ItemStack orb) {
        boolean summon = !PowerOrbData.isActive(orb);
        long cost = summon && !PowerOrbData.holdsGolem(orb) ? GolemOrbPower.SUMMON_COST : TOGGLE_COST;
        return result(env, cost, () -> {
            if (summon) {
                GolemOrbPower.summon(wearer, orb);
            } else {
                GolemOrbPower.dismiss(wearer, orb);
            }
            wearer.displayClientMessage(
                Component.translatable(summon
                        ? "message.hexwright.power_orb.golem_summoned"
                        : "message.hexwright.power_orb.golem_dismissed")
                    .withStyle(ChatFormatting.AQUA),
                true
            );
        });
    }

    private static SpellAction.Result sanctuary(CastingEnvironment env, ServerPlayer wearer, ItemStack orb) {
        long left = PowerOrbData.cooldownLeft(orb, wearer.level().getGameTime());
        if (left > 0) {
            return result(env, 0L, () -> wearer.displayClientMessage(
                Component.translatable("message.hexwright.power_orb.sanctuary_recovering",
                        (left + 19) / 20)
                    .withStyle(ChatFormatting.GRAY),
                true));
        }
        return result(env, SanctuaryOrbPower.CAST_COST, () -> SanctuaryOrbPower.cast(wearer, orb));
    }

    private static SpellAction.Result result(CastingEnvironment env, long cost, Runnable effect) {
        return new SpellAction.Result(new RenderedSpell() {
            @Override
            public void cast(CastingEnvironment castEnv) {
                effect.run();
            }

            @Override
            public CastingImage cast(CastingEnvironment castEnv, CastingImage image) {
                cast(castEnv);
                return image;
            }
        }, cost, List.of(ParticleSpray.burst(env.mishapSprayPos(), 0.5, 10)), 1L);
    }
}
