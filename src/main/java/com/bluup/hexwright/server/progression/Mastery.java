package com.bluup.hexwright.server.progression;

import com.bluup.hexwright.common.staff_assembly.calc.CoreRegistry;
import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.crucible.EssencePouchData;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

public final class Mastery {

    private static final Map<String, PocketCasterData.Quality> CORE_REQUIREMENTS = Map.of(
        "quartz_core", PocketCasterData.Quality.SOUND,
        "scribe_core", PocketCasterData.Quality.FINE,
        "traveller_core", PocketCasterData.Quality.EXQUISITE,
        "echo_core", PocketCasterData.Quality.MASTERWORK
    );

    private Mastery() {
    }

    public static PlayerMastery of(ServerPlayer player) {
        return MasteryState.get(player.server).mastery(player.getUUID());
    }

    public static boolean hasMastery(ServerPlayer player, PocketCasterData.Quality required) {
        return of(player).hasMastery(required);
    }

    public static void recordCraft(ServerPlayer player, PocketCasterData.Quality quality, ItemStack result) {
        MasteryState state = MasteryState.get(player.server);
        boolean newBest = state.mastery(player.getUUID()).record(quality);
        state.setDirty();

        HexwrightCriteria.QUALITY_CRAFTED.trigger(player, quality, result);

        if (newBest) {
            HexwrightNetworking.sendMasterySync(player);
            player.displayClientMessage(
                Component.translatable(
                    "message.hexwright.mastery.new_best",
                    Component.translatable(quality.translationKey())
                ).withStyle(ChatFormatting.GOLD),
                false
            );
            String unlockKey = unlockNoteKey(quality);
            if (unlockKey != null) {
                player.displayClientMessage(
                    Component.translatable(unlockKey).withStyle(ChatFormatting.AQUA),
                    false
                );
            }
            player.level().playSound(
                null, player.blockPosition(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.0f
            );
        }
    }

    private static @Nullable String unlockNoteKey(PocketCasterData.Quality quality) {
        return switch (quality) {
            case SOUND -> "message.hexwright.mastery.unlock.sound";
            case FINE -> "message.hexwright.mastery.unlock.fine";
            case EXQUISITE -> "message.hexwright.mastery.unlock.exquisite";
            case MASTERWORK -> "message.hexwright.mastery.unlock.masterwork";
            default -> null;
        };
    }

    public static Optional<PocketCasterData.Quality> coreRequirement(Item coreItem) {
        return CoreRegistry.lookup(coreItem)
            .map(data -> CORE_REQUIREMENTS.get(data.id()));
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
