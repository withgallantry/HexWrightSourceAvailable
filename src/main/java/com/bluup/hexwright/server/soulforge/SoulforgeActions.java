package com.bluup.hexwright.server.soulforge;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.OperatorUtils;
import at.petrak.hexcasting.api.casting.ParticleSpray;
import at.petrak.hexcasting.api.casting.RenderedSpell;
import at.petrak.hexcasting.api.casting.castables.SpellAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadCaster;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadEntity;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadLocation;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadOffhandItem;
import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.hexpatterns.HexwrightSpellAction;
import com.bluup.hexwright.server.weapon.PestiferousItem;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Consumer;

public final class SoulforgeActions {

    private static final long CALLING_COST = MediaConstants.DUST_UNIT;
    private static final long REST_COST = MediaConstants.DUST_UNIT / 10;
    private static final long SEVERANCE_COST = MediaConstants.DUST_UNIT;
    private static final long CONTAGION_COST = MediaConstants.DUST_UNIT;

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

    private static final SpellAction CALLING = onBond(CALLING_COST, Soulforge::recall);
    private static final SpellAction REST = onBond(REST_COST, Soulforge::dismiss);
    private static final SpellAction SEVERANCE = onBond(SEVERANCE_COST,
        caster -> Soulforge.unbind(caster.server, caster.getUUID()));

    private static final SpellAction CONTAGION = new HexwrightSpellAction() {
        @Override
        public int getArgc() {
            return 1;
        }

        @Override
        public Result execute(List<? extends Iota> args, CastingEnvironment env) {
            ServerPlayer caster = env.getCaster();
            if (caster == null) {
                throw new MishapBadCaster();
            }
            LivingEntity source = OperatorUtils.getLivingEntityButNotArmorStand(args, 0, getArgc());
            env.assertEntityInRange(source);

            CastingEnvironment.HeldItemInfo held =
                env.getHeldItemToOperateOn(stack -> stack.getItem() instanceof PestiferousItem);
            if (held == null) {
                throw MishapBadOffhandItem.of(null, "hexwright.pestiferous");
            }
            ItemStack blade = held.stack();
            if (!SoulBond.isOwnedBy(blade, caster)) {
                throw MishapBadOffhandItem.of(blade, "hexwright.pestiferous_owned");
            }
            MobEffectInstance sickness = PestiferousItem.strongestCapturable(source);
            if (sickness == null) {
                throw MishapBadEntity.of(source, "hexwright.afflicted");
            }

            return new Result(spell(() -> PestiferousItem.drawOut(blade, source, sickness.getEffect(),
                sickness.getAmplifier())), CONTAGION_COST,
                List.of(ParticleSpray.cloud(source.position().add(0.0, source.getEyeHeight() / 2.0, 0.0), 1.0, 20)),
                1L);
        }
    };

    private static SpellAction onBond(long cost, Consumer<ServerPlayer> effect) {
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
                if (!Soulforge.ownsOne(caster)) {
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
