package com.bluup.hexwright.server.research;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class RuinedTabletItem extends Item {

    private static final String ROOT_TAG = "hexwright_ruined_tablet";
    private static final String TAG_SEED = "Seed";
    private static final String TAG_DAMAGE = "Damage";

    public RuinedTabletItem(Properties properties) {
        super(properties);
    }

    public static boolean isRolled(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(ROOT_TAG);
        return tag != null && tag.contains(TAG_SEED);
    }

    public static long seedOf(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(ROOT_TAG);
        return tag == null ? 0L : tag.getLong(TAG_SEED);
    }

    public static TabletDamage damageOf(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(ROOT_TAG);
        return tag == null
            ? TabletDamage.WEATHERED
            : TabletDamage.byName(tag.getString(TAG_DAMAGE), TabletDamage.WEATHERED);
    }

    public boolean isArtifact() {
        return false;
    }

    public void restored(ItemStack stack, ServerPlayer player) {
    }

    public static void roll(ItemStack stack, RandomSource random) {
        if (isRolled(stack) || !(stack.getItem() instanceof RuinedTabletItem tablet)) {
            return;
        }
        write(stack, random.nextLong(), TabletDamage.roll(random, tablet.isArtifact()));
    }

    private static void write(ItemStack stack, long seed, TabletDamage damage) {
        CompoundTag tag = stack.getOrCreateTagElement(ROOT_TAG);
        tag.putLong(TAG_SEED, seed);
        tag.putString(TAG_DAMAGE, damage.getSerializedName());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (isRolled(stack)) {
            TabletPuzzle puzzle = TabletPuzzle.generate(seedOf(stack), damageOf(stack));
            tooltip.add(Component.translatable("tooltip.hexwright.ruined_tablet.condition",
                damageOf(stack).label(), puzzle.damagePercent()).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.hexwright.ruined_tablet.breaks",
                puzzle.breaks().size()).withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.hexwright.ruined_tablet.unread")
                .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.hexwright.ruined_tablet.use")
            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
