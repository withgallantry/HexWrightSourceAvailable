package com.bluup.hexwright.server.item;

import com.bluup.hexwright.server.soulforge.Soulforge;
import com.bluup.hexwright.server.soulforge.SoulforgedItem;
import at.petrak.hexcasting.common.lib.HexAttributes;
import com.bluup.hexwright.server.weapon.WeaponTooltips;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class TealsStaffItem extends ItemHexwrightStaff implements SoulforgedItem {

    private static final int LIME = 0x80FF30;

    private static final AttributeModifier AMBIT = new AttributeModifier(
        UUID.fromString("3c1f6a8e-5b2d-4e7a-9f04-8d6b2e1c7a53"),
        "Teals Staff Ambit", 0.5, AttributeModifier.Operation.MULTIPLY_BASE);

    private static final AttributeModifier GRID_ZOOM = new AttributeModifier(
        UUID.fromString("a7e2d940-1c6b-4f38-b5a2-6e9d0f4c8b17"),
        "Teals Staff Grid Zoom", 0.5, AttributeModifier.Operation.MULTIPLY_BASE);

    private static final AttributeModifier MEDIA_DISCOUNT = new AttributeModifier(
        UUID.fromString("5d8b3f12-9e4a-4c67-a1d0-2b7f6e8c9a45"),
        "Teals Staff Discount", -0.5, AttributeModifier.Operation.MULTIPLY_TOTAL);

    public TealsStaffItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!Soulforge.mayWield(player, stack)) {
            return InteractionResultHolder.fail(stack);
        }
        return super.use(level, player, hand);
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        Multimap<Attribute, AttributeModifier> out = HashMultimap.create(super.getDefaultAttributeModifiers(slot));
        if (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) {
            out.put(HexAttributes.AMBIT_RADIUS, AMBIT);
            out.put(HexAttributes.GRID_ZOOM, GRID_ZOOM);
            out.put(HexAttributes.MEDIA_CONSUMPTION_MODIFIER, MEDIA_DISCOUNT);
        }
        return out;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity holder, int slot, boolean selected) {
        super.inventoryTick(stack, level, holder, slot, selected);
        Soulforge.inventoryTick(stack, level, holder);
    }

    @Override
    public void onDestroyed(ItemEntity entity) {
        super.onDestroyed(entity);
        Soulforge.onDestroyed(entity);
    }

    @Override
    public int tithePercent() {
        return 0;
    }

    @Override
    public boolean drops() {
        return false;
    }

    @Override
    public int materialiseColour() {
        return LIME;
    }

    @Override
    public Component getName(ItemStack stack) {
        return WeaponTooltips.graded(stack, super.getName(stack), null);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        SoulforgedItem.appendBond(stack, tooltip);
        tooltip.add(Component.empty());
        WeaponTooltips.add(tooltip, Component.translatable("tooltip.hexwright.teals_staff.recall",
            Component.keybind("key.hexwright.soulforge")), ChatFormatting.DARK_GRAY);
        WeaponTooltips.add(tooltip, Component.translatable("tooltip.hexwright.teals_staff.severance"),
            ChatFormatting.DARK_GRAY);
    }
}
