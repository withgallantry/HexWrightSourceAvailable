package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.common.animation.PlayerAnimationLayer;
import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.item.RechargingItem;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FrostbiteItem extends EternalWakizashiItem implements RechargingItem {

    static final int PURSUIT_COOLDOWN_TICKS = 60;

    private static final String PURSUIT_CLIP = "sword_diagonal_slash_1_running";

    public FrostbiteItem(int attackDamageModifier, float attackSpeedModifier, Properties properties) {
        super(attackDamageModifier, attackSpeedModifier, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }

        player.getCooldowns().addCooldown(this, PURSUIT_COOLDOWN_TICKS);
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer wielder) {
            HexwrightNetworking.sendPlayerAnimation(wielder, PlayerAnimationLayer.ONE_SHOT, PURSUIT_CLIP);
            CompoundTag hex = readIotaTag(stack);
            FrozenPursuit.start(serverLevel, wielder, hex == null || hex.isEmpty() ? null : hex);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public @Nullable Recharge recharge(ItemStack stack, LivingEntity holder, float partialTick) {
        if (!(holder instanceof Player player)) {
            return null;
        }
        float left = player.getCooldowns().getCooldownPercent(this, partialTick);
        return left <= 0.0f ? null : new Recharge(left * PURSUIT_COOLDOWN_TICKS, PURSUIT_COOLDOWN_TICKS);
    }

    @Override
    protected void appendExtraTooltip(ItemStack stack, List<Component> tooltip) {
        WeaponTooltips.add(tooltip, Component.translatable("tooltip.hexwright.frostbite.pursuit",
            FrozenPursuit.CUTS), ChatFormatting.AQUA);
        WeaponTooltips.add(tooltip, Component.translatable("tooltip.hexwright.frostbite.recharge",
            String.format("%.1f", PURSUIT_COOLDOWN_TICKS / 20.0F)), ChatFormatting.GRAY);
    }
}
