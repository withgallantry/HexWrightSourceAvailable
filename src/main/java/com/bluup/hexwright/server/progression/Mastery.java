package com.bluup.hexwright.server.progression;

import com.bluup.hexwright.server.crucible.EssencePouchData;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

public final class Mastery {

    private Mastery() {
    }

    public static void recordCraft(ServerPlayer player, PocketCasterData.Quality quality, ItemStack result) {
        MasteryState state = MasteryState.get(player.server);
        boolean newBest = state.mastery(player.getUUID()).record(quality);
        state.setDirty();

        HexwrightCriteria.QUALITY_CRAFTED.trigger(player, quality, result);

        if (newBest) {
            player.displayClientMessage(
                Component.translatable(
                    "message.hexwright.mastery.new_best",
                    Component.translatable(quality.translationKey())
                ).withStyle(ChatFormatting.GOLD),
                false
            );
            player.level().playSound(
                null, player.blockPosition(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.0f
            );
        }
    }

    public static void checkEssenceMilestones(ServerPlayer player, ItemStack pouch) {
        if (pouch.isEmpty()) {
            return;
        }
        double total = EssencePouchData.total(pouch);
        if (total > 0) {
            HexwrightCriteria.ESSENCE_COLLECTED.trigger(player, total);
        }
    }
}
