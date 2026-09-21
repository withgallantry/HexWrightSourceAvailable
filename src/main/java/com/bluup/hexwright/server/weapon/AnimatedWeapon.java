package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.common.animation.PlayerAnimationLayer;
import com.bluup.hexwright.common.weapon.MiningClickState;
import com.bluup.hexwright.inits.HexwrightNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public interface AnimatedWeapon {

    Vector3f NO_TRAIL_OFFSET = new Vector3f();

    @Nullable
    default String idleClip() {
        return null;
    }

    @Nullable
    default String strikeClip(Player player, int comboStep) {
        return null;
    }

    default int comboLength() {
        return 1;
    }

    default int comboResetTicks() {
        return 0;
    }

    default float clipSpeed(Player player, String clip) {
        return 1.0F;
    }

    @Nullable
    default SoundEvent strikeSound() {
        return null;
    }

    default float strikeSoundVolume() {
        return 1.0F;
    }

    default float strikeSoundPitch() {
        return 1.0F;
    }

    default int strikeSoundDelayTicks() {
        return 0;
    }

    default int strikeHitDelayTicks() {
        return 0;
    }

    default float swingArcReach() {
        return SwingArc.DEFAULT_REACH;
    }

    default float swingArcDegrees() {
        return SwingArc.DEFAULT_ARC_DEGREES;
    }

    @Nullable
    default ResourceLocation trailFx() {
        return null;
    }

    @Nullable
    default String trailAnchorPart() {
        return null;
    }

    default Vector3f trailAnchorOffset() {
        return NO_TRAIL_OFFSET;
    }

    @Nullable
    default WeaponSlash slashFor(String playerClip) {
        return null;
    }

    default boolean isMidSwing(Player player) {
        return false;
    }

    static void onSwing(ServerPlayer player, InteractionHand hand, MeleeSwingState state) {
        if (hand != InteractionHand.MAIN_HAND) {
            return;
        }
        Item item = player.getMainHandItem().getItem();
        if (!(item instanceof AnimatedWeapon weapon)) {
            return;
        }
        if (((MiningClickState) player.gameMode).hexwright$isMiningClick()) {
            return;
        }
        if (weapon.isMidSwing(player)) {
            return;
        }

        int now = player.server.getTickCount();
        if (state.interactedThisTick(now)) {
            return;
        }

        if (!state.aimedAtSomething(now)) {
            SwingArc.strike(player, weapon);
        }

        int attackInterval = attackInterval(player);
        if (state.isFreshAttack(now, attackInterval)) {
            state.dropHeldSwing();
            animate(player, item, weapon, state, now);
        } else {
            state.holdSwing(now, attackInterval);
        }
    }

    static boolean ownsAttackSwing(Player player) {
        return player.getMainHandItem().getItem() instanceof AnimatedWeapon weapon
            && weapon.strikeClip(player, 0) != null;
    }

    static boolean ownsSwingPacket(ServerPlayer player, InteractionHand hand, MeleeSwingState state) {
        return hand == InteractionHand.MAIN_HAND
            && ownsAttackSwing(player)
            && !((MiningClickState) player.gameMode).hexwright$isMiningClick()
            && !state.interactedThisTick(player.server.getTickCount());
    }

    static void onConnectionTick(ServerPlayer player, MeleeSwingState state) {
        if (!state.hasHeldSwing()) {
            return;
        }
        if (player.isRemoved() || !player.isAlive()) {
            state.dropHeldSwing();
            return;
        }
        Item item = player.getMainHandItem().getItem();
        if (!(item instanceof AnimatedWeapon weapon) || weapon.isMidSwing(player)) {
            state.dropHeldSwing();
            return;
        }

        int now = player.server.getTickCount();
        if (!state.isFreshAttack(now, attackInterval(player))) {
            return;
        }
        state.dropHeldSwing();
        animate(player, item, weapon, state, now);
    }

    private static void animate(ServerPlayer player, Item item, AnimatedWeapon weapon,
                                MeleeSwingState state, int now) {
        int step = state.peekStep(now, item, weapon.comboLength(), weapon.comboResetTicks());
        String clip = weapon.strikeClip(player, step);
        SoundEvent sound = weapon.strikeSound();
        if (clip == null && sound == null) {
            return;
        }
        state.commit(now, item, step);

        if (clip != null) {
            HexwrightNetworking.sendPlayerAnimation(player, PlayerAnimationLayer.ONE_SHOT, clip);
        }
        if (sound != null) {
            int delay = frameToTicks(player, weapon, clip, weapon.strikeSoundDelayTicks());
            if (delay <= 0) {
                playStrikeSound(player, weapon, sound);
            } else {
                SlamWindUp.schedule(player.server, delay, () -> playStrikeSound(player, weapon, sound));
            }
        }
    }

    private static int attackInterval(Player player) {
        return Mth.ceil(player.getCurrentItemAttackStrengthDelay());
    }

    static int frameToTicks(Player player, AnimatedWeapon weapon, @Nullable String clip, int frame) {
        if (frame <= 0) {
            return 0;
        }
        float speed = clip == null ? 1.0F : weapon.clipSpeed(player, clip);
        return speed <= 0.0F ? frame : Math.round(frame / speed);
    }

    private static void playStrikeSound(ServerPlayer player, AnimatedWeapon weapon, SoundEvent sound) {
        if (player.isRemoved() || !player.isAlive()) {
            return;
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            sound, SoundSource.PLAYERS, weapon.strikeSoundVolume(), weapon.strikeSoundPitch());
    }
}
