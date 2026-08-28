package com.bluup.hexwright.server.signet;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.client.signet.ArtisanSignetClient;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ArtisanSignetItem extends Item {

    public ArtisanSignetItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!SignatureMark.hasMark(stack)) {
            if (level.isClientSide) {
                ArtisanSignetClient.openDrawScreen(hand);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        if (!level.isClientSide) {
            InteractionHand otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            ItemStack target = player.getItemInHand(otherHand);
            if (isEligibleTarget(target)) {
                SignatureMark.copyMark(stack, target);
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.4f, 1.6f);
                player.displayClientMessage(
                    Component.translatable("message.hexwright.artisan_signet.signed", target.getHoverName())
                        .withStyle(ChatFormatting.GOLD),
                    true
                );
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static boolean isEligibleTarget(ItemStack target) {
        if (target.isEmpty() || target.getItem() instanceof ArtisanSignetItem) {
            return false;
        }
        return BuiltInRegistries.ITEM.getKey(target.getItem()).getNamespace().equals(Hexwright.MOD_ID);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        if (!SignatureMark.hasMark(stack)) {
            tooltip.add(Component.translatable("tooltip.hexwright.artisan_signet.blank")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        } else {
            tooltip.add(Component.translatable("tooltip.hexwright.artisan_signet.hint")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return SignatureMark.hasMark(stack);
    }
}
