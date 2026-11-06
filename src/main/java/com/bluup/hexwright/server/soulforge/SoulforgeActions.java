package com.bluup.hexwright.server.soulforge;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.OperatorUtils;
import at.petrak.hexcasting.api.casting.ParticleSpray;
import at.petrak.hexcasting.api.casting.RenderedSpell;
import at.petrak.hexcasting.api.casting.castables.Action;
import at.petrak.hexcasting.api.casting.castables.SpellAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.iota.DoubleIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadCaster;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadEntity;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadLocation;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadOffhandItem;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.compat.ephemera.EphemeraStatus;
import com.bluup.hexwright.server.hexpatterns.HexwrightSpellAction;
import com.bluup.hexwright.server.weapon.PestiferousItem;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class SoulforgeActions {

    private static final long CALLING_COST = MediaConstants.DUST_UNIT;
    private static final long REST_COST = MediaConstants.DUST_UNIT / 10;
    private static final long SEVERANCE_COST = MediaConstants.DUST_UNIT;
    private static final long CONTAGION_COST = MediaConstants.DUST_UNIT;
    private static final long PURGE_COST = MediaConstants.DUST_UNIT / 10;

    private SoulforgeActions() {
    }

    public static void register() {
        Registry<ActionRegistryEntry> registry = IXplatAbstractions.INSTANCE.getActionRegistry();
        Registry.register(registry, Hexwright.id("soulbound_calling"),
            new ActionRegistryEntry(HexPattern.fromAngles("ewdeddee", HexDir.SOUTH_EAST), CALLING));
        Registry.register(registry, Hexwright.id("soulbound_rest"),
            new ActionRegistryEntry(HexPattern.fromAngles("dwqqqqqwd", HexDir.SOUTH_EAST), REST));
        Registry.register(registry, Hexwright.id("soulbound_severance"),
            new ActionRegistryEntry(HexPattern.fromAngles("eewddedq", HexDir.SOUTH_EAST), SEVERANCE));
        Registry.register(registry, Hexwright.id("soulbound_contagion"),
            new ActionRegistryEntry(HexPattern.fromAngles("eeddedadedd", HexDir.EAST), CONTAGION));
    }

    private static final SpellAction CALLING = onBond(CALLING_COST,
        caster -> Soulforge.ownsOne(caster) || Soulforge.mayReclaim(caster), Soulforge::recall);
    private static final SpellAction REST = onBond(REST_COST, Soulforge::ownsOne, Soulforge::dismiss);
    private static final SpellAction SEVERANCE = onBond(SEVERANCE_COST, Soulforge::ownsOne,
        caster -> Soulforge.unbind(caster.server, caster.getUUID(), true));

    private static final SpellAction CONTAGION_STRONGEST = taking(1, (args, env) -> {
        ServerPlayer caster = requireCaster(env);
        LivingEntity source = OperatorUtils.getLivingEntityButNotArmorStand(args, 0, 1);
        env.assertEntityInRange(source);
        ItemStack blade = heldPestiferous(env, caster);
        MobEffectInstance status = PestiferousItem.strongest(source);
        if (status == null) {
            throw MishapBadEntity.of(source, "hexwright.afflicted");
        }
        return drawOut(blade, source, status);
    });

    private static final SpellAction CONTAGION_NAMED = taking(2, (args, env) -> {
        ServerPlayer caster = requireCaster(env);
        LivingEntity source = OperatorUtils.getLivingEntityButNotArmorStand(args, 0, 2);
        env.assertEntityInRange(source);
        MobEffect effect = EphemeraStatus.statusOf(args.get(1));
        if (effect == null) {
            throw MishapInvalidIota.of(args.get(1), 0, "hexwright.known_status");
        }
        ItemStack blade = heldPestiferous(env, caster);
        MobEffectInstance status = source.getEffect(effect);
        if (status == null) {
            throw new MishapBadEntity(source,
                Component.translatable("hexcasting.mishap.bad_item.hexwright.afflicted_with", effect.getDisplayName()));
        }
        return drawOut(blade, source, status);
    });

    private static final SpellAction CONTAGION_PURGE = taking(1, (args, env) -> {
        ServerPlayer caster = requireCaster(env);
        ItemStack blade = heldPestiferous(env, caster);
        int carried = PestiferousItem.carried(blade, 0).size();
        if (carried == 0) {
            throw MishapBadOffhandItem.of(blade, "hexwright.pestiferous_carrying");
        }
        int index = OperatorUtils.getPositiveIntUnderInclusive(args, 0, carried - 1, 1);
        return new SpellAction.Result(spell(() -> PestiferousItem.purge(blade, index)), PURGE_COST,
            List.of(ParticleSpray.burst(caster.position(), 0.5, 10)), 1L);
    });

    private static final Action CONTAGION = (env, image, continuation) -> {
        List<Iota> stack = image.getStack();
        Iota top = stack.isEmpty() ? null : stack.get(stack.size() - 1);
        SpellAction shape = top instanceof DoubleIota ? CONTAGION_PURGE
            : top != null && EphemeraStatus.statusOf(top) != null ? CONTAGION_NAMED
            : CONTAGION_STRONGEST;
        return shape.operate(env, image, continuation);
    };

    private static ServerPlayer requireCaster(CastingEnvironment env) {
        ServerPlayer caster = env.getCaster();
        if (caster == null) {
            throw new MishapBadCaster();
        }
        return caster;
    }

    private static ItemStack heldPestiferous(CastingEnvironment env, ServerPlayer caster) {
        CastingEnvironment.HeldItemInfo held =
            env.getHeldItemToOperateOn(stack -> stack.getItem() instanceof PestiferousItem);
        if (held == null) {
            throw MishapBadOffhandItem.of(null, "hexwright.pestiferous");
        }
        ItemStack blade = held.stack();
        if (!SoulBond.isOwnedBy(blade, caster)) {
            throw MishapBadOffhandItem.of(blade, "hexwright.pestiferous_owned");
        }
        return blade;
    }

    private static SpellAction.Result drawOut(ItemStack blade, LivingEntity source, MobEffectInstance status) {
        if (!PestiferousItem.hasRoomFor(blade, status.getEffect())) {
            throw MishapBadOffhandItem.of(blade, "hexwright.pestiferous_full", PestiferousItem.MAX_CARRIED);
        }
        return new SpellAction.Result(spell(() -> PestiferousItem.drawOut(blade, source, status.getEffect(),
            status.getAmplifier())), CONTAGION_COST,
            List.of(ParticleSpray.cloud(source.position().add(0.0, source.getEyeHeight() / 2.0, 0.0), 1.0, 20)),
            1L);
    }

    private static SpellAction taking(int argc,
                                      BiFunction<List<? extends Iota>, CastingEnvironment, SpellAction.Result> execute) {
        return new HexwrightSpellAction() {
            @Override
            public int getArgc() {
                return argc;
            }

            @Override
            public Result execute(List<? extends Iota> args, CastingEnvironment env) {
                return execute.apply(args, env);
            }
        };
    }

    private static SpellAction onBond(long cost, Predicate<ServerPlayer> allowed, Consumer<ServerPlayer> effect) {
        return new HexwrightSpellAction() {
            @Override
            public int getArgc() {
                return 0;
            }

            @Override
            public Result execute(List<? extends Iota> args, CastingEnvironment env) {
                ServerPlayer caster = env.getCaster();
                if (caster == null) {
                    throw new MishapBadCaster();
                }
                if (!allowed.test(caster)) {
                    throw new MishapBadLocation(env.mishapSprayPos(), "hexwright_no_soulbond");
                }
                return new Result(spell(() -> effect.accept(caster)), cost,
                    List.of(ParticleSpray.burst(caster.position(), 0.5, 10)), 1L);
            }
        };
    }

    private static RenderedSpell spell(Runnable effect) {
        return new RenderedSpell() {
            @Override
            public void cast(CastingEnvironment castEnv) {
                effect.run();
            }

            @Override
            public CastingImage cast(CastingEnvironment castEnv, CastingImage image) {
                cast(castEnv);
                return image;
            }
        };
    }
}
