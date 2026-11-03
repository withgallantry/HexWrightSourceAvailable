package com.bluup.hexwright.client.animation;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.weapon.AnimatedWeapon;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.api.layered.modifier.MirrorModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class WeaponIdleAnimationClient {
    private static final int PRIORITY = 800;

    private static final int FADE_TICKS = 6;

    private static final ResourceLocation LAYER_ID = Hexwright.id("weapon_idle");

    private WeaponIdleAnimationClient() {
    }

    public static void onClientTick(Minecraft client) {
        if (client.level == null) {
            return;
        }

        for (AbstractClientPlayer player : client.level.players()) {
            PlayerAnimationLayers.of(player, LAYER_ID, PRIORITY, CarryLayer.class, CarryLayer::new).update();
        }
    }

    @Nullable
    private static String wantedClip(AbstractClientPlayer player) {
        if (player.isSpectator()
            || player.isSleeping()
            || player.isVisuallySwimming()
            || player.isFallFlying()
            || player.isPassenger()) {
            return null;
        }

        ItemStack held = player.getMainHandItem();
        return held.getItem() instanceof AnimatedWeapon weapon ? weapon.idleClip() : null;
    }

    private static final class CarryLayer extends ModifierLayer<KeyframeAnimationPlayer> {
        private final AbstractClientPlayer player;
        private final MirrorModifier mirror = new MirrorModifier();

        @Nullable
        private String playing;

        private CarryLayer(AbstractClientPlayer player) {
            this.player = player;
            addModifierBefore(mirror);
        }

        private void update() {
            String wanted = wantedClip(player);
            if (Objects.equals(wanted, playing)) {
                return;
            }

            if (wanted == null) {
                playing = null;
                replaceAnimationWithFade(
                    AbstractFadeModifier.standardFadeIn(FADE_TICKS, Ease.INOUTSINE), null);
                return;
            }

            KeyframeAnimation clip = PlayerAnimationRegistry.get(wanted);
            if (clip == null) {
                return;
            }

            playing = wanted;
            mirror.setEnabled(player.getMainArm() == HumanoidArm.LEFT);
            try {
                replaceAnimationWithFade(
                    AbstractFadeModifier.standardFadeIn(FADE_TICKS, Ease.INOUTSINE),
                    new KeyframeAnimationPlayer(clip).setFirstPersonMode(FirstPersonMode.NONE));
            } catch (Exception e) {
                Hexwright.LOGGER.error("Failed to play weapon idle animation '{}'", wanted, e);
                playing = null;
            }
        }
    }
}
