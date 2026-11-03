package com.bluup.hexwright.server.weapon;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public interface SpearStaff extends AnimatedWeapon {

    String JAB_CLIP = "spear_staff_jab";

    String SPRINT_JAB_CLIP = "spear_staff_jab_running";

    int CONTACT_TICK = 5;

    float LONGEST_CLIP_TICKS = 13.0F;

    @Override
    @Nullable
    default String strikeClip(Player player, int comboStep) {
        return player.isSprinting() ? SPRINT_JAB_CLIP : JAB_CLIP;
    }

    @Override
    default float clipSpeed(Player player, String clip) {
        if (!JAB_CLIP.equals(clip) && !SPRINT_JAB_CLIP.equals(clip)) {
            return 1.0F;
        }
        return Math.max(1.0F, LONGEST_CLIP_TICKS / Math.max(1.0F, player.getCurrentItemAttackStrengthDelay()));
    }

    @Override
    @Nullable
    default SoundEvent strikeSound() {
        return SoundEvents.TRIDENT_THROW;
    }

    @Override
    default float strikeSoundVolume() {
        return 0.6F;
    }

    @Override
    default float strikeSoundPitch() {
        return 1.3F;
    }

    @Override
    default int strikeSoundDelayTicks() {
        return CONTACT_TICK;
    }

    @Override
    default float swingArcReach() {
        return 4.5F;
    }

    @Override
    default float swingArcDegrees() {
        return 40.0F;
    }
}
