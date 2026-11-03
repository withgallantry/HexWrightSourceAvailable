package com.bluup.hexwright.server.powerorb;

import at.petrak.hexcasting.api.casting.OperatorUtils;
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
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.hexpatterns.HexwrightSpellAction;
import com.bluup.hexwright.server.portal.VoidTearManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

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
        if (!(orb.getItem() instanceof PowerOrbItem item)
            || !item.power().bindsSigil()
            || !PowerOrbData.matches(orb, pattern)) {
            return null;
        }
        return new OrbSigil(item.power());
    };

    private record OrbSigil(PowerOrbPower power) implements SpecialHandler {
        @Override
        public Action act() {
            return power == PowerOrbPower.TEETH ? TEETH_ACTION : SIGIL_ACTION;
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
                case GOLEM -> construct(env, wearer, orb, ConstructOrbPower.GOLEM);
                case CAT -> construct(env, wearer, orb, ConstructOrbPower.CAT);
                case GUARDIAN -> construct(env, wearer, orb, ConstructOrbPower.GUARDIAN);
                case SANCTUARY -> sanctuary(env, wearer, orb);
                case TEETH, DUST -> throw new MishapBadLocation(env.mishapSprayPos(), "hexwright_no_power_orb");
            };
        }
    };

    private static final SpellAction TEETH_ACTION = new HexwrightSpellAction() {
        @Override
        public int getArgc() {
            return 1;
        }

        @Override
        public Result execute(List<? extends Iota> args, CastingEnvironment env) {
            Vec3 at = OperatorUtils.getVec3(args, 0, getArgc());
            env.assertVecInRange(at);

            if (!(env.getCastingEntity() instanceof ServerPlayer wearer)) {
                throw new MishapBadCaster();
            }
            ServerLevel level = env.getWorld();
            if (!TeethOrbPower.hasLineOfSight(level, wearer, at)) {
                throw MishapInvalidIota.of(args.get(0), 0, "hexwright.tear_blocked");
            }
            if (TeethOrbPower.atLimit(level, wearer)) {
                throw MishapInvalidIota.of(args.get(0), 0, "hexwright.tear_limit",
                    String.valueOf(VoidTearManager.MAX_TEARS_PER_CASTER));
            }

            return new Result(new RenderedSpell() {
                @Override
                public void cast(CastingEnvironment castEnv) {
                    TeethOrbPower.cast(castEnv.getWorld(), wearer, at);
                }

                @Override
                public CastingImage cast(CastingEnvironment castEnv, CastingImage image) {
                    cast(castEnv);
                    return image;
                }
            }, TeethOrbPower.TEAR_COST,
                List.of(ParticleSpray.burst(at, VoidTearManager.tearLength() * 0.6, 18)), 1L);
        }
    };

    private static SpellAction.Result construct(CastingEnvironment env, ServerPlayer wearer, ItemStack orb,
                                                ConstructOrbPower power) {
        boolean summon = !PowerOrbData.isActive(orb);
        long left = PowerOrbData.cooldownLeft(orb, wearer.level().getGameTime());
        if (summon && left > 0) {
            return result(env, 0L, () -> wearer.displayClientMessage(
                power.message("recovering", (left + 19) / 20).withStyle(ChatFormatting.GRAY), true));
        }
        long cost = summon && !PowerOrbData.holdsConstruct(orb) ? ConstructOrbPower.SUMMON_COST : TOGGLE_COST;
        return result(env, cost, () -> {
            if (summon) {
                power.summon(wearer, orb);
            } else {
                power.dismiss(wearer, orb);
            }
            wearer.displayClientMessage(
                power.message(summon ? "summoned" : "dismissed").withStyle(ChatFormatting.AQUA), true);
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
