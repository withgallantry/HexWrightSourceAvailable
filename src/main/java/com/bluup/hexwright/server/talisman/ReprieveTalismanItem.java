package com.bluup.hexwright.server.talisman;

import com.bluup.hexwright.server.item.ArtifactItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ReprieveTalismanItem extends TalismanItem implements ArtifactItem {

    public ReprieveTalismanItem(Properties properties) {
        super(properties);
    }

    @Override
    public TalismanData.Trigger fixedTrigger() {
        return TalismanData.Trigger.BRINK;
    }

    @Override
    public TalismanData.Context fixedContext() {
        return TalismanData.Context.SELF;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(this.getDescriptionId(stack)).withStyle(ArtifactItem.COLOUR);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(ArtifactItem.tooltipLine());
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.hexwright.talisman.reprieve")
            .withStyle(ChatFormatting.GRAY));
    }
}
