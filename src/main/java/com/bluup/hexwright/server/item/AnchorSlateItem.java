package com.bluup.hexwright.server.item;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.item.IotaHolderItem;
import com.bluup.hexwright.server.worldgen.dungeon.DungeonGroups;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import ram.talia.moreiotas.api.casting.iota.StringIota;

import java.util.List;

public class AnchorSlateItem extends Item implements IotaHolderItem {

    private static final String ROOT_TAG = "hexwright_anchor_slate";
    private static final String TAG_RUNE = "Rune";

    private static final String TAG_WORD = "Word";

    public AnchorSlateItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(String rune) {
        ItemStack stack = new ItemStack(HexwrightItems.ANCHOR_SLATE);
        stack.getOrCreateTagElement(ROOT_TAG).putString(TAG_RUNE, rune);
        return stack;
    }

    public static ItemStack of(ServerLevel level, String rune) {
        ItemStack stack = of(rune);
        stamp(stack, level.getSeed(), rune);
        return stack;
    }

    public static @Nullable String runeOf(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(ROOT_TAG);
        if (tag == null) {
            return null;
        }
        String rune = tag.getString(TAG_RUNE);
        return rune.isEmpty() ? null : rune;
    }

    private static void stamp(ItemStack stack, long seed, String rune) {
        stack.getOrCreateTagElement(ROOT_TAG).putString(TAG_WORD, DungeonGroups.wordFor(seed, rune));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        String rune = runeOf(stack);
        if (rune == null || DungeonGroups.runeIndexOf(rune) < 0) {
            return;
        }
        CompoundTag tag = stack.getTagElement(ROOT_TAG);
        if (tag != null && DungeonGroups.wordFor(serverLevel.getSeed(), rune).equals(tag.getString(TAG_WORD))) {
            return;
        }
        stamp(stack, serverLevel.getSeed(), rune);
    }


    @Override
    public @Nullable CompoundTag readIotaTag(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(ROOT_TAG);
        if (tag == null) {
            return null;
        }
        String word = tag.getString(TAG_WORD);
        return word.isEmpty() ? null : IotaType.serialize(StringIota.makeUnchecked(word));
    }

    @Override
    public boolean writeable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean canWrite(ItemStack stack, @Nullable Iota iota) {
        return false;
    }

    @Override
    public void writeDatum(ItemStack stack, @Nullable Iota iota) {
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        String rune = runeOf(stack);
        if (rune == null || DungeonGroups.runeIndexOf(rune) < 0) {
            tooltip.add(Component.translatable("tooltip.hexwright.anchor_slate.blank")
                .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.add(Component.translatable("tooltip.hexwright.anchor_slate.hint")
            .withStyle(ChatFormatting.GRAY));
    }
}
