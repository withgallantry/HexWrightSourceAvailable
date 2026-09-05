package com.bluup.hexwright.client.animation;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.client.weapon.WeaponSlashVisualClient;
import com.bluup.hexwright.client.weapon.WeaponTrailVisualClient;
import com.bluup.hexwright.common.animation.PlayerAnimationLayer;
import com.bluup.hexwright.server.weapon.AnimatedWeapon;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractModifier;
import dev.kosmx.playerAnim.api.layered.modifier.AdjustmentModifier;
import dev.kosmx.playerAnim.api.layered.modifier.MirrorModifier;
import dev.kosmx.playerAnim.api.layered.modifier.SpeedModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Set;

public final class PlayerAnimationClient {
    private static final int ONE_SHOT_PRIORITY = 950;
    private static final int LOOP_PRIORITY = 900;
    private static final int FADE_OUT_TICKS = 5;

    private static final ResourceLocation ONE_SHOT_ID = Hexwright.id("animation_one_shot");
    private static final ResourceLocation LOOP_ID = Hexwright.id("animation_loop");

    private static final int MIN_FADE_IN_TICKS = 3;

    private static final String FLAG_FOLLOW_CAMERA_PITCH = "followCameraPitch";
    private static final String FLAG_TWO_HANDED = "twoHanded";
    private static final String FLAG_WHOLE_BODY_FIRST_PERSON = "wholeBodyFirstPerson";

    private static final Set<String> MAIN_HAND_PARTS = Set.of("rightArm", "rightItem");

    private static final Set<String> BOTH_HAND_PARTS = Set.of("rightArm", "rightItem", "leftArm", "leftItem");

    private static final FirstPersonConfiguration ONE_HANDED_FIRST_PERSON = new FirstPersonConfiguration()
        .setShowRightArm(true)
        .setShowLeftArm(false)
        .setShowRightItem(true)
        .setShowLeftItem(false);

    private static final FirstPersonConfiguration TWO_HANDED_FIRST_PERSON = new FirstPersonConfiguration()
        .setShowRightArm(true)
        .setShowLeftArm(true)
        .setShowRightItem(true)
        .setShowLeftItem(false);

    private PlayerAnimationClient() {
    }

    public static void handle(int entityId, PlayerAnimationLayer layer, @Nullable String clipName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        Entity entity = mc.level.getEntity(entityId);
        if (!(entity instanceof AbstractClientPlayer player)) {
            return;
        }

        boolean isLoop = layer == PlayerAnimationLayer.LOOP;
        Sub sub = PlayerAnimationLayers.of(player,
            isLoop ? LOOP_ID : ONE_SHOT_ID,
            isLoop ? LOOP_PRIORITY : ONE_SHOT_PRIORITY,
            Sub.class, Sub::new);
        play(sub, layer, clipName);
    }

    private static void play(Sub sub, PlayerAnimationLayer layer, @Nullable String clipName) {
        try {
            if (clipName != null && !clipName.isEmpty()) {
                KeyframeAnimation animation = PlayerAnimationRegistry.get(clipName);
                if (animation == null) {
                    return;
                }

                boolean twoHanded = flag(animation, FLAG_TWO_HANDED, false);
                sub.followCameraPitch = flag(animation, FLAG_FOLLOW_CAMERA_PITCH, true);
                sub.handFilter.visibleParts = twoHanded ? BOTH_HAND_PARTS : MAIN_HAND_PARTS;
                sub.handFilter.wholeBody =
                    flag(animation, FLAG_WHOLE_BODY_FIRST_PERSON, !sub.followCameraPitch);

                var copy = animation.mutableCopy();
                copy.torso.fullyEnablePart(true);
                copy.head.pitch.setEnabled(false);
                copy.head.yaw.setEnabled(true);
                if (layer != PlayerAnimationLayer.LOOP) {
                    copy.isLooped = false;
                }

                sub.mirror.setEnabled(sub.player.getMainArm() == HumanoidArm.LEFT);
                sub.speed.speed = clipSpeed(sub.player, clipName);
                sub.replaceAnimationWithFade(
                    AbstractFadeModifier.standardFadeIn(fadeInTicks(copy.beginTick, sub.speed.speed), Ease.INOUTSINE),
                    new KeyframeAnimationPlayer(copy.build(), 0)
                        .setFirstPersonConfiguration(twoHanded ? TWO_HANDED_FIRST_PERSON : ONE_HANDED_FIRST_PERSON)
                        .setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL));

                if (layer != PlayerAnimationLayer.LOOP) {
                    WeaponTrailVisualClient.onSwingStarted(sub.player,
                        Math.round(copy.stopTick / sub.speed.speed));
                    WeaponSlashVisualClient.onSwingStarted(sub.player, clipName, sub.speed.speed);
                }
            } else {
                sub.speed.speed = 1.0F;
                sub.replaceAnimationWithFade(
                    AbstractFadeModifier.standardFadeIn(FADE_OUT_TICKS, Ease.INOUTSINE), null);
                sub.pitchAdjustment.fadeOut(FADE_OUT_TICKS);
                if (layer != PlayerAnimationLayer.LOOP) {
                    WeaponTrailVisualClient.onSwingStopped(sub.player);
                    WeaponSlashVisualClient.onSwingStopped(sub.player);
                }
            }
        } catch (Exception e) {
            Hexwright.LOGGER.error("Failed to play player animation '{}'", clipName, e);
        }
    }

    private static int fadeInTicks(float beginTick, float speed) {
        return Math.max(Math.round(beginTick / speed), MIN_FADE_IN_TICKS);
    }

    private static float clipSpeed(AbstractClientPlayer player, String clipName) {
        if (!(player.getMainHandItem().getItem() instanceof AnimatedWeapon weapon)) {
            return 1.0F;
        }
        float speed = weapon.clipSpeed(player, clipName);
        return Float.isFinite(speed) ? Mth.clamp(speed, 0.1F, 10.0F) : 1.0F;
    }

    private static boolean flag(KeyframeAnimation animation, String name, boolean fallback) {
        Object value = animation.extraData.get(name);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        return fallback;
    }

    private static final class FirstPersonHandFilter extends AbstractModifier {
        private Set<String> visibleParts = MAIN_HAND_PARTS;

        private boolean wholeBody;

        @Override
        public @NotNull Vec3f get3DTransform(@NotNull String modelName, @NotNull TransformType type,
                                             float tickDelta, @NotNull Vec3f value0) {
            if (!wholeBody && FirstPersonMode.isFirstPersonPass() && !visibleParts.contains(modelName)) {
                return value0;
            }
            return super.get3DTransform(modelName, type, tickDelta, value0);
        }
    }

    private static final class Sub extends ModifierLayer<KeyframeAnimationPlayer> {
        private final AbstractClientPlayer player;
        private final SpeedModifier speed = new SpeedModifier();
        private final MirrorModifier mirror = new MirrorModifier();
        private final AdjustmentModifier pitchAdjustment;
        private final FirstPersonHandFilter handFilter = new FirstPersonHandFilter();

        private boolean followCameraPitch = true;

        private Sub(AbstractClientPlayer player) {
            this.player = player;
            this.pitchAdjustment = createPitchAdjustment();
            speed.speed = 1F;
            mirror.setEnabled(false);
            addModifier(handFilter, 0);
            addModifier(pitchAdjustment, 0);
            addModifier(speed, 0);
            addModifier(mirror, 0);
        }

        private AdjustmentModifier createPitchAdjustment() {
            return new AdjustmentModifier(partName -> {
                float rotationX = 0f;

                if (!followCameraPitch) {
                    return Optional.empty();
                }

                if (FirstPersonMode.isFirstPersonPass() && !handFilter.wholeBody) {
                    float pitch = (float) Math.toRadians(player.getXRot());
                    switch (partName) {
                        case "rightArm" -> rotationX = pitch;
                        default -> {
                            return Optional.empty();
                        }
                    }
                } else {
                    float pitch = (float) Math.toRadians(player.getXRot() / 2F);
                    switch (partName) {
                        case "body" -> rotationX = -pitch;
                        case "rightArm", "leftArm" -> rotationX = pitch;
                        case "rightLeg", "leftLeg" -> rotationX = -pitch;
                        default -> {
                            return Optional.empty();
                        }
                    }
                }

                return Optional.of(new AdjustmentModifier.PartModifier(
                    new Vec3f(rotationX, 0f, 0f), new Vec3f(0f, 0f, 0f)));
            });
        }
    }
}
