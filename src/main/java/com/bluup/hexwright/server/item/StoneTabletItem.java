package com.bluup.hexwright.server.item;

import com.bluup.hexwright.client.progression.ClientRecipeUnlocks;
import com.bluup.hexwright.server.progression.RecipeTablets;
import com.bluup.hexwright.server.progression.RecipeUnlocks;
import com.bluup.hexwright.server.sound.HexwrightSoundEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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

public class StoneTabletItem extends Item {

    private static final String ROOT_TAG = "hexwright_tablet";
    private static final String TAG_RECIPE = "Recipe";

    public StoneTabletItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(String recipeNameKey) {
        ItemStack stack = new ItemStack(HexwrightItems.STONE_TABLET);
        stack.getOrCreateTagElement(ROOT_TAG).putString(TAG_RECIPE, recipeNameKey);
        return stack;
    }

    public static CompoundTag lootTag(String recipeNameKey) {
        CompoundTag root = new CompoundTag();
        CompoundTag tablet = new CompoundTag();
        tablet.putString(TAG_RECIPE, recipeNameKey);
        root.put(ROOT_TAG, tablet);
        return root;
    }

    public static @Nullable String recipeOf(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(ROOT_TAG);
        if (tag == null) {
            return null;
        }
        String recipe = tag.getString(TAG_RECIPE);
        return recipe.isBlank() ? null : recipe;
    }

    @Override
    public Component getName(ItemStack stack) {
        String recipe = recipeOf(stack);
        if (recipe == null) {
            return super.getName(stack);
        }
        return Component.translatable("item.hexwright.stone_tablet.named", Component.translatable(recipe));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        String recipe = recipeOf(stack);
        if (recipe == null) {
            tooltip.add(Component.translatable("tooltip.hexwright.stone_tablet.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable(
            "tooltip.hexwright.stone_tablet.recipe",
            Component.translatable(recipe).withStyle(ChatFormatting.AQUA)
        ).withStyle(ChatFormatting.GRAY));

        if (ClientRecipeUnlocks.isDiscovered(recipe)) {
            tooltip.add(Component.translatable("tooltip.hexwright.stone_tablet.known").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.hexwright.stone_tablet.study", Component.keybind("key.use")).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        String recipe = recipeOf(stack);

        if (level.isClientSide) {
            return recipe == null
                ? InteractionResultHolder.pass(stack)
                : InteractionResultHolder.success(stack);
        }

        ServerPlayer serverPlayer = (ServerPlayer) player;

        if (recipe == null) {
            serverPlayer.displayClientMessage(
                Component.translatable("message.hexwright.stone_tablet.blank").withStyle(ChatFormatting.GRAY), true
            );
            return InteractionResultHolder.pass(stack);
        }

        if (!RecipeTablets.requiresTablet(recipe)) {
            serverPlayer.displayClientMessage(
                Component.translatable("message.hexwright.stone_tablet.ungated").withStyle(ChatFormatting.GRAY), true
            );
            return InteractionResultHolder.pass(stack);
        }

        if (!RecipeUnlocks.unlock(serverPlayer, recipe)) {
            serverPlayer.displayClientMessage(
                Component.translatable(
                    "message.hexwright.stone_tablet.already_known",
                    Component.translatable(recipe)
                ).withStyle(ChatFormatting.GRAY),
                true
            );
            return InteractionResultHolder.pass(stack);
        }

        if (!serverPlayer.getAbilities().instabuild) {
            stack.shrink(1);
        }

        level.playSound(null, serverPlayer.blockPosition(), HexwrightSoundEvents.recipeUnlock(), SoundSource.PLAYERS, 0.8f, 1.0f);
        serverPlayer.displayClientMessage(
            Component.translatable(
                "message.hexwright.stone_tablet.learned",
                Component.translatable(recipe).withStyle(ChatFormatting.AQUA)
            ).withStyle(ChatFormatting.GOLD),
            false
        );

        return InteractionResultHolder.consume(stack);
    }
}
