package com.bluup.hexwright.server.armour;

import com.bluup.hexwright.server.hexpatterns.HexwrightSpellAction;
import at.petrak.hexcasting.api.casting.ParticleSpray;
import at.petrak.hexcasting.api.casting.RenderedSpell;
import at.petrak.hexcasting.api.casting.castables.Action;
import at.petrak.hexcasting.api.casting.castables.SpecialHandler;
import at.petrak.hexcasting.api.casting.castables.SpellAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.iota.BooleanIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadCaster;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadLocation;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class ArmourPowerActions {

    private ArmourPowerActions() {
    }

    public static void register() {
        Registry.register(IXplatAbstractions.INSTANCE.getSpecialHandlerRegistry(),
            Hexwright.id("armour_sigil_toggle"), SIGIL_TOGGLE_FACTORY);
    }

    private static ItemStack requirePowerChest(CastingEnvironment env) {
        LivingEntity wearer = env.getCastingEntity();
        if (wearer == null) {
            throw new MishapBadCaster();
        }
        ItemStack chest = wearer.getItemBySlot(EquipmentSlot.CHEST);
        if (!(chest.getItem() instanceof HexwrightArmourItem piece)
            || piece.getType() != ArmorItem.Type.CHESTPLATE
            || !ArmourPowerToggle.hasPower(piece.set())) {
            throw new MishapBadLocation(env.mishapSprayPos(), "hexwright_no_power_chest");
        }
        return chest;
    }

    private static final SpecialHandler.Factory<ArmourSigilToggle> SIGIL_TOGGLE_FACTORY = (pattern, env) -> {
        LivingEntity wearer = env.getCastingEntity();
        if (wearer == null) {
            return null;
        }
        ItemStack chest = wearer.getItemBySlot(EquipmentSlot.CHEST);
        if (!(chest.getItem() instanceof HexwrightArmourItem piece)
            || piece.getType() != ArmorItem.Type.CHESTPLATE
            || !ArmourPowerToggle.hasPower(piece.set())
            || !ArmourPowerToggle.matches(chest, pattern)) {
            return null;
        }
        return new ArmourSigilToggle(piece.set());
    };

    private record ArmourSigilToggle(ArmourSet set) implements SpecialHandler {
        @Override
        public Action act() {
            return TOGGLE_ACTION;
        }

        @Override
        public Component getName() {
            return Component.translatable("hexcasting.spell.hexwright.armour_sigil_toggle",
                Component.translatable("armour_set.hexwright." + set.id()));
        }
    }

    private static final SpellAction TOGGLE_ACTION = new HexwrightSpellAction() {
        @Override
        public int getArgc() {
            return 1;
        }

        @Override
        public Result execute(List<? extends Iota> args, CastingEnvironment env) {
            Iota raw = args.get(0);
            if (!(raw instanceof BooleanIota boolIota)) {
                throw MishapInvalidIota.ofType(raw, 0, "hexwright.armour_power_toggle");
            }
            boolean enabled = boolIota.getBool();
            ItemStack chest = requirePowerChest(env);
            ServerPlayer caster = env.getCaster();

            return new Result(new RenderedSpell() {
                @Override
                public void cast(CastingEnvironment castEnv) {
                    ArmourPowerToggle.setEnabled(chest, enabled);
                    if (caster != null) {
                        caster.displayClientMessage(
                            Component.translatable(enabled
                                    ? "message.hexwright.armour.power_on"
                                    : "message.hexwright.armour.power_off")
                                .withStyle(ChatFormatting.AQUA),
                            true
                        );
                    }
                }

                @Override
                public CastingImage cast(CastingEnvironment castEnv, CastingImage image) {
                    cast(castEnv);
                    return image;
                }
            }, MediaConstants.DUST_UNIT / 10, List.of(ParticleSpray.burst(env.mishapSprayPos(), 0.5, 10)), 1L);
        }
    };
}
