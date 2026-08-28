package com.bluup.hexwright.server.bindstone;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.item.StoneTabletItem;
import com.bluup.hexwright.server.progression.RecipeTablets;
import com.bluup.hexwright.server.progression.RecipeUnlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class BindstoneReward {

    private BindstoneReward() {
    }

    public static void grant(ServerPlayer player) {
        Set<String> gated = RecipeTablets.get().gatedRecipes();
        if (gated.isEmpty()) {
            Hexwright.LOGGER.warn("A bindstone pillar was claimed but no recipes are tablet-gated; nothing to give.");
            return;
        }

        List<String> candidates = new ArrayList<>();
        for (String recipe : gated) {
            if (!RecipeUnlocks.isUnlocked(player, recipe)) {
                candidates.add(recipe);
            }
        }
        if (candidates.isEmpty()) {
            candidates.addAll(gated);
        }
        candidates.sort(String::compareTo);

        String recipe = candidates.get(player.getRandom().nextInt(candidates.size()));
        ItemStack tablet = StoneTabletItem.of(recipe);

        player.sendSystemMessage(
            Component.translatable("message.hexwright.bindstone.claimed", tablet.getHoverName())
                .withStyle(ChatFormatting.LIGHT_PURPLE)
        );

        if (player.getInventory().add(tablet)) {
            return;
        }
        ItemEntity dropped = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), tablet);
        dropped.setNoPickUpDelay();
        dropped.setDeltaMovement(0.0, 0.0, 0.0);
        player.level().addFreshEntity(dropped);
    }
}
