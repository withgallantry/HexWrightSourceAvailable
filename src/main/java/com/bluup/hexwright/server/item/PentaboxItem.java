package com.bluup.hexwright.server.item;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.pentabox.PentaboxData;
import com.bluup.hexwright.server.pentabox.PentaboxInteractions;
import com.bluup.hexwright.server.pentabox.PentaboxUIFactory;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PentaboxItem extends Item {
    public PentaboxItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            openMenuInHand(serverPlayer, stack, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public Component getName(ItemStack stack) {
        PocketCasterData.Quality quality = PentaboxData.getQuality(stack);
        return Component.translatable(
            "item.hexwright.harmonized_pentabox.named",
            Component.translatable(quality.translationKey())
        );
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return PentaboxInteractions.forwardUseOn(context);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        return PentaboxInteractions.forwardInteractLivingEntity(stack, player, target, hand);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return PentaboxInteractions.forwardHurtEnemy(stack, target, attacker);
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity entity) {
        return PentaboxInteractions.forwardMineBlock(stack, level, state, pos, entity);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return PentaboxInteractions.forwardDestroySpeed(stack, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        PocketCasterData.Quality quality = PentaboxData.getQuality(stack);
        tooltip.add(Component.translatable(
            "tooltip.hexwright.pentabox.quality",
            Component.translatable(quality.translationKey())
        ).withStyle(ChatFormatting.GRAY));

        int usableSlots = PentaboxData.usableSlotCount(stack);
        int slotBonus = usableSlots - PocketCasterData.Quality.CRUDE.itemSlots() * PentaboxData.GRID_COLUMNS;
        NonNullList<ItemStack> items = PentaboxData.loadItems(stack);
        int stored = 0;
        for (int i = 0; i < usableSlots; i++) {
            if (!items.get(i).isEmpty()) {
                stored++;
            }
        }
        tooltip.add(Component.translatable(
            "tooltip.hexwright.pentabox.contents",
            stored,
            PocketCasterData.formatWithBonus(usableSlots, slotBonus)
        ).withStyle(ChatFormatting.GRAY));

        ItemStack selected = PentaboxData.getSelectedStack(stack);
        if (selected.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.hexwright.pentabox.none_selected").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.hexwright.pentabox.selected", selected.getHoverName()).withStyle(ChatFormatting.AQUA));
        }
    }

    public static void openMenuInHand(ServerPlayer player, ItemStack stack, InteractionHand hand) {
        if (!PentaboxUIFactory.INSTANCE.openForHand(player, hand)) {
            Hexwright.LOGGER.error("Failed to open Pentabox UI for hand {}", hand);
        }
    }

    public static void openMenuFromLinkedSlot(ServerPlayer player, InteractionHand hand) {
        if (!PentaboxUIFactory.INSTANCE.openForHand(player, hand)) {
            Hexwright.LOGGER.error("Failed to open Pentabox UI from linked stack");
        }
    }
}
