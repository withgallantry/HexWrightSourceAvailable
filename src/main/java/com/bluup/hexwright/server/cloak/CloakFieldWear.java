package com.bluup.hexwright.server.cloak;

import com.bluup.hexwright.HexwrightDebug;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CloakFieldWear {

    private static final long MEND_DELAY_TICKS = 200;
    private static final float MEND_PER_TICK = 0.05f;

    private static final float BLAST_WEAR_PER_POWER = 12.0f;
    private static final float BLAST_REACH_PER_POWER = 2.0f;

    private static final float THROWN_WEAR = 1.0f;

    private record Wear(float amount, long lastHit) {
    }

    private static final Map<UUID, Wear> WEAR = new HashMap<>();
    private static final Map<UUID, Long> LAST_STRIKE = new HashMap<>();

    private CloakFieldWear() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            WEAR.clear();
            LAST_STRIKE.clear();
        });
    }

    public static void strike(ServerLevel level, CloakField field, Vec3 point, Player player) {
        long now = level.getGameTime();
        Long previous = LAST_STRIKE.put(player.getUUID(), now);
        float charge = previous == null
            ? 1.0f
            : Mth.clamp((now - previous) / player.getCurrentItemAttackStrengthDelay(), 0.0f, 1.0f);
        float amount = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * charge;
        wear(level, field, point, amount, player);
    }

    public static void projectile(ServerLevel level, CloakField field, Vec3 point, Projectile projectile) {
        float amount = projectile instanceof AbstractArrow arrow
            ? Mth.ceil(arrow.getDeltaMovement().length() * arrow.getBaseDamage())
            : THROWN_WEAR;
        wear(level, field, point, amount, projectile.getOwner());
    }

    public static void blast(ServerLevel level, Vec3 centre, float power, @Nullable Entity igniter) {
        double reach = power * BLAST_REACH_PER_POWER;
        for (CloakField field : CloakFieldStore.fieldsIn(level)) {
            if (field.toughness() <= 0.0f) {
                continue;
            }
            Vec3 point = field.surfacePoint(centre);
            double distance = point.distanceTo(centre);
            if (distance < reach) {
                CloakFieldImpacts.ripple(level, field, point);
                wear(level, field, point, (float) (power * BLAST_WEAR_PER_POWER * (1.0 - distance / reach)), igniter);
            }
        }
    }

    private static void wear(ServerLevel level, CloakField field, Vec3 point, float amount, @Nullable Entity attacker) {
        if (field.toughness() <= 0.0f || amount <= 0.0f) {
            return;
        }
        long now = level.getGameTime();
        float worn = current(field.id(), now) + amount;
        HexwrightDebug.log(HexwrightDebug.CLOAK, "[cloak] field {} worn {} by {}: {}/{}",
            field.id(), amount, attacker, worn, field.toughness());
        if (worn < field.toughness()) {
            WEAR.put(field.id(), new Wear(worn, now));
            if (attacker instanceof ServerPlayer player) {
                int left = Mth.ceil(100.0f * (1.0f - worn / field.toughness()));
                player.displayClientMessage(Component.translatable("message.hexwright.cloak.strained", left), true);
            }
            return;
        }
        WEAR.remove(field.id());
        CloakFieldStore.remove(level, field.id());
        level.playSound(null, point.x, point.y, point.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 4.0f, 0.6f);
        level.playSound(null, point.x, point.y, point.z, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 4.0f, 0.5f);
        if (attacker instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("message.hexwright.cloak.breached"), true);
        }
    }

    private static float current(UUID id, long now) {
        Wear wear = WEAR.get(id);
        if (wear == null) {
            return 0.0f;
        }
        long mending = now - wear.lastHit() - MEND_DELAY_TICKS;
        return mending <= 0 ? wear.amount() : Math.max(0.0f, wear.amount() - mending * MEND_PER_TICK);
    }
}
