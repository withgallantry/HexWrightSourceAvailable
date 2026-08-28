package com.bluup.hexwright.client.staff_assembly;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.client.animation.PlayerAnimationLayers;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class AnchoriteFlightAnimationClient {
    private static final String CLIP = "anchorite_flight_loop";
    private static final int PRIORITY = 850;
    private static final int FADE_TICKS = 6;

    private static final int HOLD_FRAME = 20;

    private static final double HOLD_ENTER_SPEED = 0.03;
    private static final double HOLD_EXIT_SPEED = 0.012;

    private static final double COAST_SPEED_RATIO = 0.97;
    private static final int COAST_TICKS = 3;

    private static final ResourceLocation LAYER_ID = Hexwright.id("anchorite_flight");

    private AnchoriteFlightAnimationClient() {
    }

    public static void onClientTick(Minecraft client) {
        if (client.level == null) {
            return;
        }

        for (AbstractClientPlayer player : client.level.players()) {
            PlayerAnimationLayers.of(player, LAYER_ID, PRIORITY, State.class, State::new).update();
        }
    }

    private static boolean shouldAnimate(AbstractClientPlayer player) {
        var abilities = player.getAbilities();
        return abilities.mayfly
            && abilities.flying
            && !abilities.instabuild
            && !player.isSpectator()
            && !player.isPassenger()
            && !player.isFallFlying()
            && !player.isSwimming();
    }

    private enum Pose {
        NONE,
        HOVER,
        TRAVEL
    }

    private static final class State extends ModifierLayer<KeyframeAnimationPlayer> {
        private final AbstractClientPlayer player;
        private final FlightLeanModifier lean = new FlightLeanModifier();
        private Pose pose = Pose.NONE;
        private double lastSpeed;
        private int coastTicks;

        private State(AbstractClientPlayer player) {
            this.player = player;
            addModifierBefore(lean);
        }

        private void update() {
            double dx = player.getX() - player.xo;
            double dy = player.getY() - player.yo;
            double dz = player.getZ() - player.zo;

            boolean flying = shouldAnimate(player);
            updateLean(flying, dx, dy, dz);

            Pose wanted = !flying
                ? Pose.NONE
                : isTravelling(Math.sqrt(dx * dx + dy * dy + dz * dz)) ? Pose.TRAVEL : Pose.HOVER;
            if (wanted != pose) {
                apply(wanted);
            }
        }

        private boolean isTravelling(double speed) {
            if (speed > lastSpeed) {
                coastTicks = 0;
            } else if (speed < lastSpeed * COAST_SPEED_RATIO) {
                coastTicks++;
            }
            lastSpeed = speed;

            if (pose == Pose.TRAVEL) {
                return coastTicks < COAST_TICKS && speed > HOLD_EXIT_SPEED;
            }
            return coastTicks == 0 && speed > HOLD_ENTER_SPEED;
        }

        private void updateLean(boolean flying, double dx, double dy, double dz) {
            if (!flying) {
                lean.relax();
                return;
            }

            float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
            float sin = Mth.sin(yaw);
            float cos = Mth.cos(yaw);

            lean.update(-sin * dx + cos * dz, -cos * dx - sin * dz, dy);
        }

        private void apply(Pose wanted) {
            if (wanted == Pose.NONE) {
                pose = wanted;
                lastSpeed = 0.0;
                coastTicks = 0;
                replaceAnimationWithFade(
                    AbstractFadeModifier.standardFadeIn(FADE_TICKS, Ease.INOUTSINE), null);
                return;
            }

            KeyframeAnimation clip = com.bluup.hexwright.client.animation.PlayerAnimationRegistry.get(CLIP);
            if (clip == null) {
                return;
            }

            KeyframeAnimationPlayer anim = wanted == Pose.TRAVEL
                ? new HeldFrame(clip, HOLD_FRAME)
                : new KeyframeAnimationPlayer(clip, HOLD_FRAME);

            pose = wanted;
            try {
                replaceAnimationWithFade(
                    AbstractFadeModifier.standardFadeIn(FADE_TICKS, Ease.INOUTSINE),
                    anim.setFirstPersonMode(FirstPersonMode.NONE));
            } catch (Exception e) {
                Hexwright.LOGGER.error("Failed to play Anchorite flight animation", e);
            }
        }
    }

    private static final class HeldFrame extends KeyframeAnimationPlayer {
        private HeldFrame(KeyframeAnimation animation, int frame) {
            super(animation, frame);
        }

        @Override
        public void tick() {
        }

        @Override
        public void setupAnim(float tickDelta) {
            super.setupAnim(0f);
        }
    }
}
