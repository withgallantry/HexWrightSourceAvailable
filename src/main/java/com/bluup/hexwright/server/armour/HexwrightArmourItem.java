package com.bluup.hexwright.server.armour;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.api.item.IotaHolderItem;
import com.bluup.hexwright.server.hexpatterns.CantorActions;
import com.bluup.hexwright.server.talisman.TalismanSlots;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.client.model.HumanoidModel;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.RenderProvider;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class HexwrightArmourItem extends ArmorItem implements GeoItem, IotaHolderItem {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<Object> renderProvider = GeoItem.makeRenderer(this);

    private final ArmourSet set;
    private final ArmourTier tier;

    public HexwrightArmourItem(ArmourSet set, ArmourTier tier, ArmourPiece piece, Properties properties) {
        super(tier, piece.type(), properties);
        this.set = set;
        this.tier = tier;
    }

    public ArmourSet set() {
        return set;
    }

    public ArmourTier tier() {
        return tier;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(
            "item.hexwright.armour_piece.named",
            Component.translatable(tier.displayQuality().translationKey()),
            Component.translatable("armour_set.hexwright." + set.id()),
            Component.translatable("armour_piece.hexwright." + getType().getName())
        ).withStyle(tier.displayQuality().color());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.hexwright.armour.quality",
            Component.translatable(tier.displayQuality().translationKey()).withStyle(tier.displayQuality().color()))
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.hexwright.armour.set",
            Component.translatable("armour_set.hexwright." + set.id())).withStyle(ChatFormatting.GRAY));
        if (set == ArmourSet.AUGUR && getType() == Type.CHESTPLATE) {
            tooltip.add(Component.translatable("tooltip.hexwright.armour.augur_chestplate.power")
                .withStyle(ChatFormatting.DARK_PURPLE));
        }
        if (set == ArmourSet.HEXWARDEN && getType() == Type.CHESTPLATE) {
            tooltip.add(Component.translatable("tooltip.hexwright.armour.hexwarden_chestplate.power")
                .withStyle(ChatFormatting.DARK_PURPLE));
            tooltip.add(Component.translatable("tooltip.hexwright.armour.hexwarden_chestplate.protection",
                Math.round(HexwardenMishapEnv.protection(tier) * 100)).withStyle(ChatFormatting.GRAY));
        }
        if (set == ArmourSet.VEILWALKER && getType() == Type.CHESTPLATE) {
            tooltip.add(Component.translatable("tooltip.hexwright.armour.veilwalker_chestplate.power")
                .withStyle(ChatFormatting.DARK_PURPLE));
            tooltip.add(Component.translatable("tooltip.hexwright.armour.veilwalker_chestplate.slots",
                TalismanSlots.bonusFor(tier), TalismanSlots.BASE_SLOTS + TalismanSlots.bonusFor(tier))
                .withStyle(ChatFormatting.GRAY));
        }
        if (set == ArmourSet.CANTOR && getType() == Type.CHESTPLATE) {
            tooltip.add(Component.translatable("tooltip.hexwright.armour.cantor_chestplate.power")
                .withStyle(ChatFormatting.DARK_PURPLE));
            tooltip.add(Component.translatable("tooltip.hexwright.armour.cantor_chestplate.minds",
                CantorActions.mindCount(tier)).withStyle(ChatFormatting.GRAY));
        }
        if (set == ArmourSet.VENATOR && getType() == Type.CHESTPLATE) {
            tooltip.add(Component.translatable("tooltip.hexwright.armour.venator_chestplate.power")
                .withStyle(ChatFormatting.DARK_PURPLE));
            tooltip.add(Component.translatable("tooltip.hexwright.armour.venator_chestplate.recharge",
                Math.round(VenatorPowers.cooldownReduction(tier) * 100)).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.hexwright.armour.venator_chestplate.arrows",
                Math.round(VenatorPowers.arrowRefundChance(tier) * 100)).withStyle(ChatFormatting.GRAY));
        }
        if (set == ArmourSet.DOMITOR && getType() == Type.CHESTPLATE) {
            tooltip.add(Component.translatable("tooltip.hexwright.armour.domitor_chestplate.power")
                .withStyle(ChatFormatting.DARK_PURPLE));
            tooltip.add(Component.translatable("tooltip.hexwright.armour.domitor_chestplate.ward",
                Math.round(DomitorPowers.wardFraction(tier) * 100)).withStyle(ChatFormatting.GRAY));
        }
        if (getType() == Type.CHESTPLATE && ArmourPowerToggle.hasPower(set)) {
            tooltip.add(Component.translatable("tooltip.hexwright.armour.set_requirement",
                ArmourPowerToggle.REQUIRED_PIECES,
                Component.translatable(tier.displayQuality().translationKey()).withStyle(tier.displayQuality().color()))
                .withStyle(ChatFormatting.GRAY));
            appendSigilTooltip(stack, tooltip, flag);
        }
    }

    private void appendSigilTooltip(ItemStack stack, List<Component> tooltip, TooltipFlag flag) {
        if (ArmourPowerToggle.getPattern(stack) == null) {
            tooltip.add(Component.translatable("tooltip.hexwright.armour.sigil_unbound")
                .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        IotaHolderItem.appendHoverText(this, stack, tooltip, flag);
        boolean enabled = ArmourPowerToggle.isEnabled(stack);
        tooltip.add(Component.translatable(enabled
                ? "tooltip.hexwright.armour.sigil_on"
                : "tooltip.hexwright.armour.sigil_off")
            .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED));
    }


    @Override
    public @Nullable CompoundTag readIotaTag(ItemStack stack) {
        return ArmourPowerToggle.getPatternTag(stack);
    }

    @Override
    public boolean writeable(ItemStack stack) {
        return getType() == Type.CHESTPLATE && ArmourPowerToggle.hasPower(set);
    }

    @Override
    public boolean canWrite(ItemStack stack, @Nullable Iota iota) {
        return writeable(stack) && (iota == null || iota instanceof PatternIota);
    }

    @Override
    public void writeDatum(ItemStack stack, @Nullable Iota iota) {
        if (iota instanceof PatternIota patternIota) {
            ArmourPowerToggle.setPattern(stack, patternIota.getPattern());
        } else {
            ArmourPowerToggle.clearPattern(stack);
        }
    }


    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public Supplier<Object> getRenderProvider() {
        return renderProvider;
    }

    @Override
    public void createRenderer(Consumer<Object> consumer) {
        consumer.accept(new RenderProvider() {
            private GeoArmorRenderer<?> renderer;

            @Override
            public HumanoidModel<LivingEntity> getHumanoidArmorModel(LivingEntity entity, ItemStack stack,
                                                                     EquipmentSlot slot,
                                                                     HumanoidModel<LivingEntity> original) {
                if (renderer == null) {
                    renderer = new com.bluup.hexwright.client.armour.HexwrightArmourRenderer();
                }
                renderer.prepForRender(entity, stack, slot, original);
                return renderer;
            }
        });
    }
}
