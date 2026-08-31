package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ShortSwordItem extends SwordItem implements AnimatedWeapon {

    static final String[] COMBO_CLIPS = {
        "sword_diagonal_slash_1",
        "sword_diagonal_slash_2",
    };

    static final String[] SPRINT_COMBO_CLIPS = {
        "sword_diagonal_slash_1_running",
        "sword_diagonal_slash_2_running",
    };

    static final int COMBO_RESET_TICKS = 30;

    static final Set<String> ALL_COMBO_CLIPS = Stream
        .concat(Arrays.stream(COMBO_CLIPS), Arrays.stream(SPRINT_COMBO_CLIPS))
        .collect(Collectors.toUnmodifiableSet());

    static final float LONGEST_COMBO_CLIP_TICKS = 13.0F;

    private final PocketCasterData.Quality quality;

    public ShortSwordItem(PocketCasterData.Quality quality, Properties properties) {
        super(ShortSwordGrade.tier(quality),
            ShortSwordGrade.attackDamageModifier(quality),
            ShortSwordGrade.attackSpeedModifier(quality),
            properties);
        this.quality = quality;
    }

    public PocketCasterData.Quality quality() {
        return this.quality;
    }

    @Override
    public int comboLength() {
        return COMBO_CLIPS.length;
    }

    @Override
    public int comboResetTicks() {
        return COMBO_RESET_TICKS;
    }

    @Override
    public float clipSpeed(Player player, String clip) {
        if (!ALL_COMBO_CLIPS.contains(clip)) {
            return 1.0F;
        }
        return Math.max(1.0F,
            LONGEST_COMBO_CLIP_TICKS / ShortSwordGrade.attackIntervalTicks(this.quality));
    }

    @Override
    @Nullable
    public String strikeClip(Player player, int comboStep) {
        String[] clips = player.isSprinting() ? SPRINT_COMBO_CLIPS : COMBO_CLIPS;
        return clips[Math.floorMod(comboStep, clips.length)];
    }

    @Override
    @Nullable
    public SoundEvent strikeSound() {
        return SoundEvents.PLAYER_ATTACK_SWEEP;
    }

    @Override
    public float strikeSoundVolume() {
        return 0.5F;
    }

    @Override
    public float strikeSoundPitch() {
        return 1.45F;
    }

    @Override
    public Component getName(ItemStack stack) {
        return WeaponTooltips.graded(stack,
            Component.translatable("item.hexwright.duelist_short_sword"), this.quality);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.hexwright.duelist_short_sword.quality",
                Component.translatable(this.quality.translationKey()).withStyle(this.quality.color()))
            .withStyle(ChatFormatting.GRAY));
        WeaponTooltips.add(tooltip, Component.translatable("tooltip.hexwright.duelist_short_sword.combo",
            COMBO_CLIPS.length), ChatFormatting.GRAY);
    }
}
