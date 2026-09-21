package com.bluup.hexwright.server.armour;

import com.bluup.hexwright.server.item.ArtifactItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class HatOfPassageItem extends ArmorItem implements ArtifactItem {

    public HatOfPassageItem(Properties properties) {
        super(MageAttireMaterial.INSTANCE, Type.HELMET, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return super.getName(stack).copy().withStyle(ArtifactItem.COLOUR);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(ArtifactItem.tooltipLine());
        CapeOfPassageItem.appendSetTooltip(level, tooltip);
    }
}
