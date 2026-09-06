package com.bluup.hexwright.server.staff_assembly;

import com.bluup.hexwright.common.animation.PlayerAnimationLayer;
import com.bluup.hexwright.common.staff_assembly.calc.CoreData;
import com.bluup.hexwright.common.staff_assembly.calc.CoreRegistry;
import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.sound.HexwrightSoundEvents;
import at.petrak.hexcasting.api.addldata.ADMediaHolder;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.api.pigment.FrozenPigment;
import at.petrak.hexcasting.api.utils.MediaHelper;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class StaffCoreBeamHandler {
    private static final String BEAM_POWER_ID = StaffPowers.BEAM_CAST_POWER_ID;
    private static final double BOLT_SPEED = 2.2;
    private static final int SPHERE_TINKLE_INTERVAL_TICKS = 12;

    private static final float AREA_ACTIVATE_PITCH = 1.0f;
    private static final float AREA_DEACTIVATE_PITCH = 0.7f;

    private static final String AREA_CLIP = "staff_area_channel";
    private static final String PROJECTILE_CLIP = "staff_projectile_release";

    private static final long BOLT_FIRE_COST = MediaConstants.DUST_UNIT / 30;
    private static final int BOLT_COOLDOWN_TICKS = 10;
    private static final long AREA_HOLD_COST_PER_SECOND = MediaConstants.DUST_UNIT / 30;

    private static final Map<UUID, Boolean> BEAM_WAS_ACTIVE = new HashMap<>();
    private static final Map<UUID, Integer> BOLT_LAST_FIRE_TICK = new HashMap<>();
    private static final Map<UUID, Boolean> AREA_WAS_ACTIVE = new HashMap<>();
    private static final Map<UUID, Double> AREA_DRAIN_PROGRESS = new HashMap<>();
    private static final Map<UUID, Integer> AREA_LAST_CAST_TICK = new HashMap<>();

    private StaffCoreBeamHandler() {
    }

    public static void handle(ServerPlayer player, boolean active, boolean crosshairFree, boolean leftClickBusy) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty() || !held.is(HexwrightItems.CONFIGURABLE_STAFF)) {
            clearHeldState(player);
            HexwrightNetworking.sendStaffCoreSphereVisual(player, false, null, 0.0);
            return;
        }

        ItemStack coreItem = StaffAssemblyData.getCoreItem(held);
        Optional<CoreData> coreData = CoreRegistry.lookup(coreItem.getItem());
        if (coreData.isEmpty()) {
            clearHeldState(player);
            HexwrightNetworking.sendStaffCoreSphereVisual(player, false, null, 0.0);
            return;
        }

        String powerId = coreData.get().powerId();
        if (BEAM_POWER_ID.equals(powerId)) {
            stopAreaChannel(player);
            HexwrightNetworking.sendStaffCoreSphereVisual(player, false, null, 0.0);
            boolean fireHeld = active && !leftClickBusy;
            boolean wasActive = BEAM_WAS_ACTIVE.getOrDefault(player.getUUID(), false);
            BEAM_WAS_ACTIVE.put(player.getUUID(), fireHeld);
            if (fireHeld && !wasActive) {
                fireBolt(player, held);
            }
            return;
        }

        BEAM_WAS_ACTIVE.remove(player.getUUID());

        if (StaffPowers.AREA_CAST_POWER_ID.equals(powerId)) {
            boolean channelHeld = active && crosshairFree;
            boolean wasAreaActive = AREA_WAS_ACTIVE.getOrDefault(player.getUUID(), false);
            AREA_WAS_ACTIVE.put(player.getUUID(), channelHeld);

            if (!channelHeld || !drainAreaHoldCost(player, held)) {
                if (channelHeld && !wasAreaActive) {
                    playNoMediaMishap(player, "message.hexwright.staff.area_no_media");
                }
                AREA_DRAIN_PROGRESS.remove(player.getUUID());
                HexwrightNetworking.sendStaffCoreSphereVisual(player, false, null, 0.0);
                if (wasAreaActive) {
                    HexwrightNetworking.sendPlayerAnimation(player, PlayerAnimationLayer.LOOP, null);
                    playAreaChannelSting(player, AREA_DEACTIVATE_PITCH);
                }
                return;
            }
            double halfExtent = StaffPowers.areaScanHalfExtent(player, held);
            FrozenPigment pigment = IXplatAbstractions.INSTANCE.getPigment(player);
            HexwrightNetworking.sendStaffCoreSphereVisual(player, true, pigment, halfExtent);
            if (!wasAreaActive) {
                HexwrightNetworking.sendPlayerAnimation(player, PlayerAnimationLayer.LOOP, AREA_CLIP);
                playAreaChannelSting(player, AREA_ACTIVATE_PITCH);
            }
            if (areaCastReady(player, coreItem)) {
                StaffPowers.executeTick(player, held);
            }
            playSphereAmbientTinkle(player);
            return;
        }

        stopAreaChannel(player);
        HexwrightNetworking.sendStaffCoreSphereVisual(player, false, null, 0.0);
    }

    private static boolean areaCastReady(ServerPlayer player, ItemStack coreItem) {
        int now = player.server.getTickCount();
        int interval = StaffCoreData.areaCastIntervalTicks(StaffCoreData.getQuality(coreItem));
        Integer last = AREA_LAST_CAST_TICK.get(player.getUUID());
        if (last != null && now >= last && now - last < interval) {
            return false;
        }
        AREA_LAST_CAST_TICK.put(player.getUUID(), now);
        return true;
    }

    public static void clearPlayer(ServerPlayer player) {
        BOLT_LAST_FIRE_TICK.remove(player.getUUID());
        AREA_LAST_CAST_TICK.remove(player.getUUID());
        clearHeldState(player);
    }

    private static void clearHeldState(ServerPlayer player) {
        BEAM_WAS_ACTIVE.remove(player.getUUID());
        stopAreaChannel(player, false);
    }

    private static void stopAreaChannel(ServerPlayer player) {
        stopAreaChannel(player, true);
    }

    private static void stopAreaChannel(ServerPlayer player, boolean playSting) {
        AREA_DRAIN_PROGRESS.remove(player.getUUID());
        if (Boolean.TRUE.equals(AREA_WAS_ACTIVE.remove(player.getUUID()))) {
            HexwrightNetworking.sendPlayerAnimation(player, PlayerAnimationLayer.LOOP, null);
            if (playSting) {
                playAreaChannelSting(player, AREA_DEACTIVATE_PITCH);
            }
        }
    }

    private static void playAreaChannelSting(ServerPlayer player, float pitch) {
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
            HexwrightSoundEvents.areaCastActivate(), SoundSource.PLAYERS, 0.7f, pitch);
    }

    private static boolean drainAreaHoldCost(ServerPlayer player, ItemStack staff) {
        UUID id = player.getUUID();
        double progress = AREA_DRAIN_PROGRESS.getOrDefault(id, 0.0) + AREA_HOLD_COST_PER_SECOND / 20.0;
        long whole = (long) progress;
        if (whole <= 0) {
            AREA_DRAIN_PROGRESS.put(id, progress);
            return true;
        }
        if (!payMediaCost(player, staff, whole)) {
            return false;
        }
        AREA_DRAIN_PROGRESS.put(id, progress - whole);
        return true;
    }

    private static boolean payMediaCost(ServerPlayer player, ItemStack staff, long cost) {
        if (cost <= 0) {
            return true;
        }
        long discounted = Math.round(cost * (1.0 - StaffAssemblyData.getMediaDiscount(staff)));
        if (discounted <= 0) {
            return true;
        }
        if (drawMedia(player, discounted, true) > 0) {
            return false;
        }
        drawMedia(player, discounted, false);
        return true;
    }

    private static long drawMedia(ServerPlayer player, long cost, boolean simulate) {
        long remaining = cost;
        for (ADMediaHolder source : MediaHelper.scanPlayerForMediaStuff(player)) {
            long got = MediaHelper.extractMedia(source, remaining, false, simulate);
            remaining -= got;
            if (remaining <= 0) {
                break;
            }
        }
        return remaining;
    }

    private static void fireBolt(ServerPlayer player, ItemStack staff) {
        int now = player.server.getTickCount();
        Integer lastFire = BOLT_LAST_FIRE_TICK.get(player.getUUID());
        if (lastFire != null && now >= lastFire && now - lastFire < BOLT_COOLDOWN_TICKS) {
            return;
        }

        if (!payMediaCost(player, staff, BOLT_FIRE_COST)) {
            playNoMediaMishap(player);
            return;
        }

        List<HexPattern> patterns = StaffAssemblyData.getAreaCastPatterns(staff);

        Vec3 direction = player.getLookAngle().normalize();
        Vec3 start = computeStaffTip(player, direction);

        double impactAmbit = StaffCoreData.echoImpactAmbit(StaffCoreData.getQuality(StaffAssemblyData.getCoreItem(staff)));
        StaffCoreBoltEntity bolt = new StaffCoreBoltEntity(player.level(), player, patterns, impactAmbit);
        bolt.setPos(start.x, start.y, start.z);
        bolt.shoot(direction.x, direction.y, direction.z, (float) BOLT_SPEED, 0.0f);
        player.level().addFreshEntity(bolt);
        BOLT_LAST_FIRE_TICK.put(player.getUUID(), now);
        HexwrightNetworking.sendPlayerAnimation(player, PlayerAnimationLayer.ONE_SHOT, PROJECTILE_CLIP);

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), HexwrightSoundEvents.staffCoreProjectileFire(), SoundSource.PLAYERS, 0.75f, 1.0f);
    }

    private static void playNoMediaMishap(ServerPlayer player) {
        playNoMediaMishap(player, "message.hexwright.staff.echo_no_media");
    }

    private static void playNoMediaMishap(ServerPlayer player, String messageKey) {
        var level = player.serverLevel();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), HexEvalSounds.MISHAP.sound(), SoundSource.PLAYERS, 1.0f, 1.0f);
        level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getEyeY(), player.getZ(), 10, 0.2, 0.2, 0.2, 0.02);
        player.displayClientMessage(Component.translatable(messageKey).withStyle(ChatFormatting.RED), true);
    }

    private static Vec3 computeStaffTip(ServerPlayer player, Vec3 direction) {
        Vec3 up = player.getUpVector(1.0f).normalize();
        double bodyYaw = Math.toRadians(player.yBodyRot);
        Vec3 bodyForward = new Vec3(-Math.sin(bodyYaw), 0.0, Math.cos(bodyYaw));
        Vec3 right = bodyForward.cross(up);
        if (right.lengthSqr() < 1.0E-6) {
            right = direction.cross(up);
        }
        if (right.lengthSqr() < 1.0E-6) {
            return player.position().add(0.0, player.getBbHeight() * 0.62, 0.0);
        }

        right = right.normalize();
        bodyForward = bodyForward.lengthSqr() < 1.0E-6 ? direction.normalize() : bodyForward.normalize();

        double handSide = player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? 1.0 : -1.0;

        Vec3 handRoot = player.position().add(0.0, player.getBbHeight() * 0.62, 0.0);
        return handRoot
            .add(right.scale(0.26 * handSide))
            .add(bodyForward.scale(0.56))
            .add(up.scale(-0.08))
            .add(direction.scale(0.14));
    }

    private static void playSphereAmbientTinkle(ServerPlayer player) {
        if (player.tickCount % SPHERE_TINKLE_INTERVAL_TICKS != 0) {
            return;
        }

        var level = player.serverLevel();
        float pitchJitter = (level.random.nextFloat() - 0.5f) * 0.2f;
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.22f, 1.25f + pitchJitter);
    }
}
