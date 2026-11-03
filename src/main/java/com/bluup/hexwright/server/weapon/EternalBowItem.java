package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.server.item.ArtifactItem;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.List;

public class EternalBowItem extends GreatBowItem implements ArtifactItem {

    private static final int HEX_COOLDOWN_TICKS = 30;

    public EternalBowItem(Properties properties) {
        super(PocketCasterData.Quality.MASTERWORK, properties);
    }

    @Override
    protected int hexCooldownTicks() {
        return HEX_COOLDOWN_TICKS;
    }

    @Override
    protected boolean hasInfiniteAmmo() {
        return true;
    }

    @Override
    public Component getName(ItemStack stack) {
        return WeaponTooltips.graded(stack, Component.translatable("item.hexwright.eternal_bow"), null);
    }

    @Override
    protected void appendExtraTooltip(ItemStack stack, List<Component> tooltip) {
        WeaponTooltips.add(tooltip, Component.translatable("tooltip.hexwright.eternal_bow.arrows"),
            ChatFormatting.GRAY);
    }
}
