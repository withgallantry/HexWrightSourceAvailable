package com.bluup.hexwright.server.talisman;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.item.IotaHolderItem;
import at.petrak.hexcasting.api.utils.NBTHelper;
import com.bluup.hexwright.client.talisman.TalismanClient;
import com.bluup.hexwright.server.progression.MakersMark;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
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
import java.util.Locale;
import java.util.Optional;

public class TalismanItem extends Item implements IotaHolderItem {

    public TalismanItem(Properties properties) {
        super(properties);
    }

    public @Nullable TalismanData.Trigger fixedTrigger() {
        return null;
    }

    public @Nullable TalismanData.Context fixedContext() {
        return null;
    }


    @Override
    public @Nullable CompoundTag readIotaTag(ItemStack stack) {
        return NBTHelper.getCompound(stack, TalismanData.TAG_HEX_DATA);
    }

    @Override
    public boolean writeable(ItemStack stack) {
        return true;
    }

    @Override
    public boolean canWrite(ItemStack stack, @Nullable Iota iota) {
        return true;
    }

    @Override
    public void writeDatum(ItemStack stack, @Nullable Iota iota) {
        if (iota == null) {
            stack.removeTagKey(TalismanData.TAG_HEX_DATA);
        } else {
            NBTHelper.put(stack, TalismanData.TAG_HEX_DATA, at.petrak.hexcasting.api.casting.iota.IotaType.serialize(iota));
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.isShiftKeyDown()) {
            if (TalismanDesign.isBlank(stack)) {
                if (level.isClientSide) {
                    TalismanClient.openDrawScreen(hand);
                }
            } else if (!level.isClientSide) {
                player.displayClientMessage(
                    Component.translatable("message.hexwright.talisman.already_painted")
                        .withStyle(ChatFormatting.GRAY),
                    true
                );
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            boolean useTriggered = TalismanData.getTrigger(stack).orElse(null) == TalismanData.Trigger.USE;
            if (useTriggered && TalismanData.isArmed(stack)) {
                TalismanCasting.onTrigger(serverPlayer, TalismanData.Trigger.USE, null, null);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public Component getName(ItemStack stack) {
        Component quality = Component.translatable(TalismanData.getQuality(stack).translationKey());
        return Component.translatable("item.hexwright.talisman.named", quality);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        MakersMark.appendTooltip(stack, tooltip);

        Optional<TalismanData.Trigger> trigger = TalismanData.getTrigger(stack);
        Optional<TalismanData.Context> context = TalismanData.getContext(stack);
        if (trigger.isPresent() && context.isPresent()) {
            tooltip.add(fixedTrigger() != null
                ? Component.translatable(
                    "tooltip.hexwright.talisman.binding_fixed",
                    Component.translatable(trigger.get().translationKey()),
                    Component.translatable(context.get().translationKey())
                ).withStyle(ChatFormatting.LIGHT_PURPLE)
                : Component.translatable(
                    "tooltip.hexwright.talisman.binding",
                    Component.translatable(trigger.get().translationKey()), trigger.get().ordinal(),
                    Component.translatable(context.get().translationKey()), context.get().ordinal()
                ).withStyle(ChatFormatting.LIGHT_PURPLE));
            long cooldown = TalismanData.cooldownTicks(stack, trigger.get());
            tooltip.add(Component.translatable(
                "tooltip.hexwright.talisman.cooldown",
                String.format(Locale.ROOT, "%.1f", cooldown / 20.0)
            ).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.hexwright.talisman.blank")
                .withStyle(ChatFormatting.GRAY));
        }

        if (trigger.isPresent() && context.isPresent() && !TalismanData.isArmed(stack)) {
            tooltip.add(Component.translatable("tooltip.hexwright.talisman.uninscribed")
                .withStyle(ChatFormatting.DARK_GRAY));
        }

        IotaHolderItem.appendHoverText(this, stack, tooltip, flag);
    }
}
