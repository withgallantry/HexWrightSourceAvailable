package com.bluup.hexwright.server.armour;

import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import com.bluup.hexwright.server.progression.MakersMark;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class ArmourGemItem extends Item {

    private final ArmourSet set;

    public ArmourGemItem(ArmourSet set, Properties properties) {
        super(properties);
        this.set = set;
    }

    public ArmourSet set() {
        return set;
    }

    @Override
    public Component getName(ItemStack stack) {
        PocketCasterData.Quality grade = ArmourGemData.getQuality(stack);
        Component quality = Component.translatable(grade.translationKey());
        return Component.translatable(set.gemNameKey(), quality).withStyle(grade.color());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        MakersMark.appendTooltip(stack, tooltip);

        tooltip.add(Component.translatable("tooltip.hexwright.armour_gem.set",
            Component.translatable("armour_set.hexwright." + set.id())).withStyle(ChatFormatting.GRAY));

        PocketCasterData.Quality grade = ArmourGemData.getQuality(stack);
        ArmourTier tier = ArmourTier.forGrade(grade);
        tooltip.add(Component.translatable("tooltip.hexwright.armour_gem.forges",
            Component.translatable(tier.displayQuality().translationKey()).withStyle(grade.color()))
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.hexwright.armour_gem.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
