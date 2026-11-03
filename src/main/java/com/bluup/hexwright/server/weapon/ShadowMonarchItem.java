package com.bluup.hexwright.server.weapon;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.common.lib.HexAttributes;
import com.bluup.hexwright.server.item.RechargingItem;
import com.bluup.hexwright.server.soulforge.Soulforge;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class ShadowMonarchItem extends EternalWakizashiItem implements SoulforgedWeapon, RechargingItem {

    static final int CLEAVE_COOLDOWN_TICKS = 600;

    static final int THINNED_PERCENT = 25;

    private static final AttributeModifier THINNED = new AttributeModifier(
        UUID.fromString("3f2b8d61-9c4e-4a07-b5e1-6d0c2a9f7e54"), "hexwright_shadow_monarch_thinned",
        -THINNED_PERCENT / 100.0D, AttributeModifier.Operation.MULTIPLY_TOTAL);

    public ShadowMonarchItem(int attackDamageModifier, float attackSpeedModifier, Properties properties) {
        super(attackDamageModifier, attackSpeedModifier, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }
        if (!Soulforge.mayWield(player, stack) || player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }

        player.getCooldowns().addCooldown(this, CLEAVE_COOLDOWN_TICKS);
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer wielder) {
            SoulCleave.start(serverLevel, wielder, this);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public boolean canWrite(ItemStack stack, @Nullable Iota iota) {
        return false;
    }

    @Override
    public @Nullable CompoundTag readIotaTag(ItemStack stack) {
        return null;
    }

    @Override
    public boolean isMidSwing(Player player) {
        ItemCooldowns cooldowns = player.getCooldowns();
        if (!cooldowns.isOnCooldown(this)) {
            return false;
        }
        float elapsed = CLEAVE_COOLDOWN_TICKS * (1.0F - cooldowns.getCooldownPercent(this, 0.0F));
        return elapsed < SoulCleave.DURATION_TICKS;
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
    public int tithePercent() {
        return 0;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> bondCosts() {
        return ImmutableMultimap.of(HexAttributes.AMBIT_RADIUS, THINNED);
    }

    @Override
    public int materialiseColour() {
        return 0x281680;
    }

    @Override
    public List<Component> costLines() {
        return List.of(
            Component.translatable("tooltip.hexwright.shadow_monarch.thinned", THINNED_PERCENT),
            Component.translatable("tooltip.hexwright.shadow_monarch.backlash", SoulCleave.SELF_CLEAVE_TICKS / 20));
    }

    @Override
    public Component normalStrike() {
        return Component.translatable("tooltip.hexwright.shadow_monarch.blow");
    }

    @Override
    public Component heavyStrike() {
        return Component.translatable("tooltip.hexwright.shadow_monarch.cleave", SoulCleave.CLEAVE_TICKS / 20);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        SoulforgedBladeItem.appendSoulforgedTooltip(stack, this, tooltip);
    }
}
