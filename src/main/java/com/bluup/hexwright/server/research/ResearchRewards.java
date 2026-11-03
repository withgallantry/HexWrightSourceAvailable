package com.bluup.hexwright.server.research;

import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.server.item.ArtifactItem;
import com.bluup.hexwright.server.progression.RecipeTablets;
import com.bluup.hexwright.server.progression.RecipeUnlocks;
import com.bluup.hexwright.server.sound.HexwrightSoundEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public final class ResearchRewards {

    public static final float PRISTINE = 0.50f;

    public static final float SOUND = 0.20f;

    private ResearchRewards() {
    }

    public static boolean anythingLeft(ServerPlayer player, TabletDamage damage) {
        return !unlearned(player, damage).isEmpty();
    }

    public static Set<String> pool(TabletDamage damage) {
        RecipeTablets.Config config = RecipeTablets.get();
        return damage.isArtifact() ? config.artifactRecipes() : config.gatedRecipes();
    }

    private static List<String> unlearned(ServerPlayer player, TabletDamage damage) {
        return pool(damage).stream()
            .filter(recipe -> !RecipeUnlocks.isUnlocked(player, recipe))
            .sorted()
            .toList();
    }

    public static @Nullable String grant(ServerPlayer player, TabletDamage damage, RandomSource random) {
        List<String> unlearned = unlearned(player, damage);
        if (unlearned.isEmpty()) {
            player.displayClientMessage(
                Component.translatable("message.hexwright.research.nothing_new").withStyle(ChatFormatting.GRAY), false);
            return null;
        }

        String recipe = unlearned.get(random.nextInt(unlearned.size()));
        RecipeUnlocks.unlock(player, recipe);

        player.level().playSound(null, player.blockPosition(), HexwrightSoundEvents.recipeUnlock(),
            SoundSource.BLOCKS, 0.9f, 1.0f);
        player.displayClientMessage(
            Component.translatable("message.hexwright.research.restored", recipeName(recipe, damage))
                .withStyle(ChatFormatting.GOLD),
            false);

        HexwrightDebug.log(HexwrightDebug.TABLET, "[tablet] {} restored a tablet, learned {}",
            player.getGameProfile().getName(), recipe);
        return recipe;
    }

    public static Component recipeName(String recipe, TabletDamage damage) {
        return Component.translatable(recipe).withStyle(damage.isArtifact() ? ArtifactItem.COLOUR : ChatFormatting.AQUA);
    }

    public static String fidelity(float remaining) {
        if (remaining >= PRISTINE) {
            return "pristine";
        }
        return remaining >= SOUND ? "sound" : "patched";
    }
}
