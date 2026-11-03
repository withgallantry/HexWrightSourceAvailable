package com.bluup.hexwright.server.flourish;

import com.bluup.hexwright.common.flourish.FlourishDesign;
import com.bluup.hexwright.common.flourish.FlourishFigure;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FlourishBlankItem extends Item {

    private static final String TAG_DESIGN = "Flourish";

    public FlourishBlankItem(Properties properties) {
        super(properties);
    }

    public static @Nullable FlourishDesign design(ItemStack stack) {
        if (!(stack.getItem() instanceof FlourishBlankItem) || stack.getTag() == null
            || !stack.getTag().contains(TAG_DESIGN)) {
            return null;
        }
        return FlourishDesign.load(stack.getTag().getCompound(TAG_DESIGN));
    }

    public static boolean isInscribed(ItemStack stack) {
        return design(stack) != null;
    }

    public static void inscribe(ItemStack stack, FlourishDesign design) {
        stack.getOrCreateTag().put(TAG_DESIGN, design.save());
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        return isInscribed(stack) ? getDescriptionId() + ".inscribed" : getDescriptionId();
    }

    @Override
    public Rarity getRarity(ItemStack stack) {
        FlourishDesign design = design(stack);
        return design != null ? design.rarity() : super.getRarity(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        FlourishDesign design = design(stack);
        if (design == null) {
            tooltip.add(Component.translatable("tooltip.hexwright.flourish_blank.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        for (FlourishDesign.Layer layer : design.layers()) {
            Component placement = Component.translatable(layer.placement().translationKey());
            if (layer.figure() == null) {
                tooltip.add(Component.translatable("tooltip.hexwright.flourish_blank.layer",
                    Component.translatable(layer.motif().translationKey()), placement).withStyle(ChatFormatting.GRAY));
                continue;
            }
            MutableComponent shapes = Component.empty();
            for (FlourishFigure.Ring ring : layer.figure().rings()) {
                if (!shapes.getSiblings().isEmpty()) {
                    shapes.append(" + ");
                }
                shapes.append(Component.translatable(ring.shape().translationKey()));
            }
            tooltip.add(Component.translatable("tooltip.hexwright.flourish_blank.figure", shapes, placement)
                .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.hexwright.flourish.how")
            .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
    }
}
