package com.bluup.hexwright.server.soulforge;

import com.bluup.hexwright.server.sound.HexwrightSoundEvents;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public interface SoulforgedItem {

    TextColor COLOUR = TextColor.fromRgb(0x5CE8E4);

    TextColor UNIQUE_COLOUR = TextColor.fromRgb(0xF5C76A);

    static boolean is(ItemStack stack) {
        return stack.getItem() instanceof SoulforgedItem;
    }

    static Component label() {
        return Component.translatable("quality.hexwright.soulforged");
    }

    static Component tooltipLine() {
        return Component.translatable("tooltip.hexwright.soulforged.quality",
                label().copy().withStyle(style -> style.withColor(COLOUR)),
                Component.translatable("tooltip.hexwright.soulforged.unique")
                    .withStyle(style -> style.withColor(UNIQUE_COLOUR)))
            .withStyle(ChatFormatting.GRAY);
    }

    static void appendBond(ItemStack stack, List<Component> tooltip) {
        tooltip.add(tooltipLine());
        if (SoulBond.isBound(stack)) {
            tooltip.add(Component.translatable("tooltip.hexwright.soulforged.bound",
                    Component.literal(SoulBond.ownerName(stack)).withStyle(style -> style.withColor(COLOUR)))
                .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.hexwright.soulforged.unbound").withStyle(ChatFormatting.GRAY));
        }
    }

    default Multimap<Attribute, AttributeModifier> bondCosts() {
        return ImmutableMultimap.of();
    }

    default void bondTick(ServerPlayer owner) {
    }

    default int tithePercent() {
        return Soulforge.TITHE_PERCENT;
    }

    default boolean drops() {
        return true;
    }

    int materialiseColour();

    default void playManifest(ServerLevel level, ServerPlayer owner, boolean arriving) {
        Soulforge.play(level, owner, HexwrightSoundEvents.soulforgedManifest(), arriving ? 1.0F : 0.85F);
    }
}
