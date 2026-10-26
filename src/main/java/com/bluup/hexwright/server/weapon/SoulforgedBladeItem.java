package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.server.soulforge.Soulforge;
import com.bluup.hexwright.server.soulforge.SoulforgedItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public abstract class SoulforgedBladeItem extends BattleAxeItem implements SoulforgedWeapon {

    private final int abilityCooldownTicks;

    private final Map<UUID, Integer> unleashedAt = new HashMap<>();

    protected SoulforgedBladeItem(int attackDamageModifier, float attackSpeedModifier, int abilityCooldownTicks,
                                  Properties properties) {
        super(EternalTier.ETERNAL, attackDamageModifier, attackSpeedModifier, Cleave.NETHERITE_REACH,
            abilityCooldownTicks, properties);
        this.abilityCooldownTicks = abilityCooldownTicks;
    }

    protected abstract boolean unleash(ServerLevel level, ServerPlayer wielder, ItemStack stack);

    protected abstract int channelTicks();

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }
        if (!Soulforge.mayWield(player, stack) || player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }

        player.getCooldowns().addCooldown(this, this.abilityCooldownTicks);
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer wielder) {
            if (unleash(serverLevel, wielder, stack)) {
                this.unleashedAt.put(wielder.getUUID(), serverLevel.getServer().getTickCount());
            } else {
                player.getCooldowns().removeCooldown(this);
            }
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public boolean isMidSwing(Player player) {
        MinecraftServer server = player.getServer();
        Integer at = this.unleashedAt.get(player.getUUID());
        return server != null && at != null && server.getTickCount() - at < channelTicks();
    }

    @Override
    public @Nullable Recharge recharge(ItemStack stack, LivingEntity holder, float partialTick) {
        return holder instanceof Player player ? Recharge.ofCooldown(player, this, partialTick) : null;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity holder, int slot, boolean selected) {
        super.inventoryTick(stack, level, holder, slot, selected);
        Soulforge.inventoryTick(stack, level, holder);
    }

    @Override
    @Nullable
    public WeaponSlash slashFor(String playerClip) {
        return null;
    }

    @Override
    protected boolean land(ServerLevel level, ServerPlayer player, InteractionHand hand) {
        return false;
    }

    @Override
    protected boolean castsOnCaught() {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        appendSoulforgedTooltip(stack, this, tooltip);
    }

    static void appendSoulforgedTooltip(ItemStack stack, SoulforgedWeapon weapon, List<Component> tooltip) {
        SoulforgedItem.appendBond(stack, tooltip);

        appendStrike(tooltip, "tooltip.hexwright.soulforged.normal_strike", weapon.normalStrike());
        appendStrike(tooltip, "tooltip.hexwright.soulforged.heavy_strike", weapon.heavyStrike());
        weapon.appendHeld(stack, tooltip);

        tooltip.add(Component.empty());
        if (weapon.tithePercent() > 0) {
            WeaponTooltips.add(tooltip, Component.translatable("tooltip.hexwright.soulforged.tithe",
                weapon.tithePercent()), ChatFormatting.RED);
        }
        for (Component cost : weapon.costLines()) {
            WeaponTooltips.add(tooltip, cost, ChatFormatting.RED);
        }

        tooltip.add(Component.empty());
        WeaponTooltips.add(tooltip, Component.translatable("tooltip.hexwright.soulforged.recall",
            Component.keybind("key.hexwright.soulforge")), ChatFormatting.DARK_GRAY);
        WeaponTooltips.add(tooltip, Component.translatable("tooltip.hexwright.soulforged.severance"),
            ChatFormatting.DARK_GRAY);
    }

    private static void appendStrike(List<Component> tooltip, String headingKey, Component description) {
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable(headingKey).withStyle(style -> style.withColor(SoulforgedItem.COLOUR)));
        WeaponTooltips.add(tooltip, description, ChatFormatting.GRAY);
    }
}
