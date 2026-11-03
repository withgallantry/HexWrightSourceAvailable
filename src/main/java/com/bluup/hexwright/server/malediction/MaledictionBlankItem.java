package com.bluup.hexwright.server.malediction;

import com.bluup.hexwright.common.malediction.MaledictionFinish;
import com.bluup.hexwright.common.malediction.MaledictionForm;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MaledictionBlankItem extends Item {

    private static final String ROOT_TAG = "hexwright_malediction";
    private static final String TAG_FORM = "Form";
    private static final String TAG_FINISH = "Finish";
    private static final String TAG_OWNER = "Owner";
    private static final String TAG_OWNER_NAME = "OwnerName";
    private static final String TAG_FLOURISH = "Flourish";

    public MaledictionBlankItem(Properties properties) {
        super(properties);
    }

    public static void fuse(ItemStack stack, MaledictionForm form, MaledictionFinish finish, Player fuser) {
        CompoundTag tag = stack.getOrCreateTagElement(ROOT_TAG);
        tag.putString(TAG_FORM, form.id());
        tag.putString(TAG_FINISH, finish.id());
        bind(stack, fuser);
    }

    public static ItemStack fused(Item blank, MaledictionForm form, MaledictionFinish finish) {
        ItemStack stack = new ItemStack(blank);
        CompoundTag tag = stack.getOrCreateTagElement(ROOT_TAG);
        tag.putString(TAG_FORM, form.id());
        tag.putString(TAG_FINISH, finish.id());
        return stack;
    }

    public static boolean isFused(ItemStack stack) {
        return stack.getItem() instanceof MaledictionBlankItem && form(stack) != null && finish(stack) != null;
    }

    public static @Nullable MaledictionForm form(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(ROOT_TAG);
        return tag == null ? null : MaledictionForm.byId(tag.getString(TAG_FORM));
    }

    public static @Nullable MaledictionFinish finish(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(ROOT_TAG);
        return tag == null ? null : MaledictionFinish.byId(tag.getString(TAG_FINISH));
    }

    public static ItemStack flourish(ItemStack stack) {
        CompoundTag tag = stack.isEmpty() ? null : stack.getTagElement(ROOT_TAG);
        return tag == null || !tag.contains(TAG_FLOURISH, Tag.TAG_COMPOUND) ? ItemStack.EMPTY
            : ItemStack.of(tag.getCompound(TAG_FLOURISH));
    }

    public static void setFlourish(ItemStack stack, ItemStack flourish) {
        if (flourish.isEmpty()) {
            CompoundTag tag = stack.getTagElement(ROOT_TAG);
            if (tag != null) {
                tag.remove(TAG_FLOURISH);
            }
        } else {
            stack.getOrCreateTagElement(ROOT_TAG).put(TAG_FLOURISH, flourish.copyWithCount(1).save(new CompoundTag()));
        }
    }

    public static boolean mayUse(ItemStack stack, Player player) {
        CompoundTag tag = stack.getTagElement(ROOT_TAG);
        return tag == null || !tag.hasUUID(TAG_OWNER) || tag.getUUID(TAG_OWNER).equals(player.getUUID());
    }

    public static void bind(ItemStack stack, Player player) {
        CompoundTag tag = stack.getOrCreateTagElement(ROOT_TAG);
        tag.putUUID(TAG_OWNER, player.getUUID());
        tag.putString(TAG_OWNER_NAME, player.getGameProfile().getName());
    }

    public static Component name(MaledictionForm form, MaledictionFinish finish) {
        return Component.translatable("item.hexwright.malediction_blank.fused",
            Component.translatable(finish.nameKey()), Component.translatable(form.nameKey()));
    }

    public static Rarity rarity(MaledictionForm form, MaledictionFinish finish) {
        return form.rarity().ordinal() >= finish.rarity().ordinal() ? form.rarity() : finish.rarity();
    }

    @Override
    public Component getName(ItemStack stack) {
        MaledictionForm form = form(stack);
        MaledictionFinish finish = finish(stack);
        return form == null || finish == null ? super.getName(stack) : name(form, finish);
    }

    @Override
    public Rarity getRarity(ItemStack stack) {
        MaledictionForm form = form(stack);
        MaledictionFinish finish = finish(stack);
        return form == null || finish == null ? super.getRarity(stack) : rarity(form, finish);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        MaledictionForm form = form(stack);
        MaledictionFinish finish = finish(stack);
        if (form == null || finish == null) {
            tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("item.hexwright." + form.itemId() + ".desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.hexwright." + finish.itemId() + ".desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.hexwright.malediction.charges", form.charges(),
            String.format("%.1f", form.rechargeTicks() / 20.0)).withStyle(ChatFormatting.DARK_GRAY));
        ItemStack flourish = flourish(stack);
        if (!flourish.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.hexwright.malediction.flourish", flourish.getHoverName())
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        CompoundTag tag = stack.getTagElement(ROOT_TAG);
        if (tag != null && tag.hasUUID(TAG_OWNER)) {
            tooltip.add(Component.translatable("tooltip.hexwright.malediction.owner", tag.getString(TAG_OWNER_NAME))
                .withStyle(ChatFormatting.DARK_PURPLE));
        }
        tooltip.add(Component.translatable("tooltip.hexwright.malediction.how")
            .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
    }
}
