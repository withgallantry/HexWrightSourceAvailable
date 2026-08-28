package com.bluup.hexwright.server.item;

import com.bluup.hexwright.common.staff_assembly.calc.IngredientCategory;
import com.bluup.hexwright.server.crucible.EssencePouchData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EndlessPouchItem extends Item {
    public EndlessPouchItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return !EssencePouchData.isEmpty(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        Map<IngredientCategory, Double> stored = EssencePouchData.getAll(stack);
        if (stored.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.hexwright.pouch.empty").withStyle(ChatFormatting.GRAY));
            return;
        }

        tooltip.add(Component.translatable("tooltip.hexwright.pouch.contents").withStyle(ChatFormatting.GRAY));
        for (Map.Entry<IngredientCategory, Double> entry : stored.entrySet()) {
            tooltip.add(Component.literal("  ")
                .append(aspectLine(entry.getKey(), entry.getValue()))
                .withStyle(ChatFormatting.DARK_PURPLE));
        }
    }

    public static Component aspectName(IngredientCategory aspect) {
        return Component.translatable("aspect.hexwright." + aspect.name().toLowerCase(Locale.ROOT));
    }

    public static MutableComponent aspectLine(IngredientCategory aspect, double amount) {
        return Component.translatable("label.hexwright.essence.entry", aspectName(aspect), formatAmount(amount));
    }

    public static String formatAmount(double amount) {
        if (amount == Math.floor(amount)) {
            return String.valueOf((long) amount);
        }
        return String.format(Locale.ROOT, "%.1f", amount);
    }
}
