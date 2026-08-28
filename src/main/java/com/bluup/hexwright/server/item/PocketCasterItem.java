package com.bluup.hexwright.server.item;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import com.bluup.hexwright.server.pocketcaster.PocketCasterUIFactory;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PocketCasterItem extends Item {
    public PocketCasterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!PocketCasterUIFactory.INSTANCE.openForHand(serverPlayer, hand)) {
                Hexwright.LOGGER.error("Failed to open Pocket Caster UI for hand {}", hand);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public Component getName(ItemStack stack) {
        PocketCasterData.Quality quality = PocketCasterData.getQuality(stack);
        return Component.translatable(
            "item.hexwright.pocket_caster.named",
            Component.translatable(quality.translationKey())
        );
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        com.bluup.hexwright.server.progression.MakersMark.appendTooltip(stack, tooltip);
        super.appendHoverText(stack, level, tooltip, flag);

        PocketCasterData.Quality quality = PocketCasterData.getQuality(stack);
        tooltip.add(Component.translatable(
            "tooltip.hexwright.pocket_caster.quality",
            Component.translatable(quality.translationKey())
        ).withStyle(ChatFormatting.GRAY));
        int slots = PocketCasterData.itemSlotCount(stack);
        int slotBonus = slots - PocketCasterData.Quality.CRUDE.itemSlots();
        tooltip.add(Component.translatable(
            "tooltip.hexwright.pocket_caster.slots",
            PocketCasterData.formatWithBonus(slots, slotBonus)
        ).withStyle(ChatFormatting.GRAY));

    }
}
