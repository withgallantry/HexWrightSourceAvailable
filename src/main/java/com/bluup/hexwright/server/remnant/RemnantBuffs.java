package com.bluup.hexwright.server.remnant;

import com.bluup.hexwright.common.remnant.RemnantType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RemnantBuffs {

    private static final long SWEEP_INTERVAL_TICKS = 10L;

    private static final double RIFT_RADIUS = 12.0;

    private static final double RIFT_PULL = 0.35;

    private static final float SHARDSKIN_RETALIATION = 4.0f;

    private static final double TREMOR_RADIUS = 24.0;

    private static final double INTERDICT_RADIUS = 12.0;

    private static final int SWEEP_EFFECT_TICKS = 30;

    private static final int ON_HIT_DURATION_TICKS = 100;

    private static boolean retaliating = false;

    private RemnantBuffs() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.overworld().getGameTime() % SWEEP_INTERVAL_TICKS != 0L) {
                return;
            }
            sweep(server);
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (retaliating) {
                return true;
            }
            long now = gameTime(entity);

            if (source.getEntity() instanceof ServerPlayer dealer && dealer != entity) {
                if (has(dealer, RemnantType.VENOM, now)) {
                    entity.addEffect(new MobEffectInstance(MobEffects.POISON, ON_HIT_DURATION_TICKS, 0));
                }
                if (has(dealer, RemnantType.BLIGHT, now)) {
                    entity.addEffect(new MobEffectInstance(MobEffects.WITHER, ON_HIT_DURATION_TICKS, 0));
                }
            }

            if (entity instanceof ServerPlayer victim
                && has(victim, RemnantType.SHARDSKIN, now)
                && source.getEntity() instanceof LivingEntity attacker) {
                retaliate(victim, attacker);
            }

            return true;
        });
    }

    public static void grant(ServerPlayer player, RemnantType type, int durationTicks, double drams) {
        MinecraftServer server = player.server;
        long expiresAt = server.overworld().getGameTime() + durationTicks;

        RemnantBuffState state = RemnantBuffState.get(server);
        state.forPlayer(player.getUUID()).set(type, expiresAt);
        state.setDirty();

        switch (type) {
            case BALLAST -> applyModifier(player, Attributes.KNOCKBACK_RESISTANCE, type,
                Math.min(1.0, drams / 400.0), AttributeModifier.Operation.ADDITION);
            case IMPACT -> applyModifier(player, Attributes.ATTACK_KNOCKBACK, type,
                Math.min(2.0, drams / 260.0), AttributeModifier.Operation.ADDITION);
            case ADAMANT -> {
                applyModifier(player, Attributes.KNOCKBACK_RESISTANCE, type, 1.0,
                    AttributeModifier.Operation.ADDITION);
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, durationTicks, 2));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, durationTicks, 1));
            }
            case ASCENDANT -> setFlight(player, true);
            default -> {
            }
        }
    }

    public static boolean has(ServerPlayer player, RemnantType type, long now) {
        return RemnantBuffState.get(player.server).forPlayer(player.getUUID()).has(type, now);
    }

    private static void sweep(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        RemnantBuffState state = RemnantBuffState.get(server);
        boolean dirty = false;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            RemnantBuffState.Active active = state.forPlayer(player.getUUID());
            List<RemnantType> lapsed = new ArrayList<>();
            for (Map.Entry<RemnantType, Long> entry : active.all().entrySet()) {
                if (entry.getValue() <= now) {
                    lapsed.add(entry.getKey());
                }
            }
            for (RemnantType type : lapsed) {
                revoke(player, type);
                active.clear(type);
                dirty = true;
            }

            if (active.has(RemnantType.RIFT, now)) {
                pullItems(player);
            }
            if (active.has(RemnantType.ASCENDANT, now)) {
                setFlight(player, true);
            }
            if (active.has(RemnantType.RIME, now)) {
                player.setTicksFrozen(0);
            }
            if (active.has(RemnantType.TREMOR, now)) {
                senseLife(player);
            }
            if (active.has(RemnantType.INTERDICT, now)) {
                interdict(player);
            }
        }

        if (dirty) {
            state.setDirty();
        }
    }

    private static void revoke(ServerPlayer player, RemnantType type) {
        switch (type) {
            case BALLAST -> removeModifier(player, Attributes.KNOCKBACK_RESISTANCE, type);
            case IMPACT -> removeModifier(player, Attributes.ATTACK_KNOCKBACK, type);
            case ADAMANT -> removeModifier(player, Attributes.KNOCKBACK_RESISTANCE, type);
            case ASCENDANT -> setFlight(player, false);
            default -> {
            }
        }
    }


    private static UUID modifierId(RemnantType type) {
        return UUID.nameUUIDFromBytes(("hexwright:remnant:" + type.name()).getBytes(StandardCharsets.UTF_8));
    }

    private static void applyModifier(ServerPlayer player, Attribute attribute, RemnantType type,
                                      double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        UUID id = modifierId(type);
        instance.removeModifier(id);
        instance.addPermanentModifier(
            new AttributeModifier(id, "hexwright_remnant_" + type.lowerName(), amount, operation));
    }

    private static void removeModifier(ServerPlayer player, Attribute attribute, RemnantType type) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(modifierId(type));
        }
    }


    private static void setFlight(ServerPlayer player, boolean allowed) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        if (player.getAbilities().mayfly == allowed) {
            return;
        }
        player.getAbilities().mayfly = allowed;
        if (!allowed) {
            player.getAbilities().flying = false;
        }
        player.onUpdateAbilities();
    }


    private static void pullItems(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 centre = player.position();
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class,
            player.getBoundingBox().inflate(RIFT_RADIUS));
        for (ItemEntity item : items) {
            if (!item.isAlive()) {
                continue;
            }
            Vec3 toward = centre.subtract(item.position());
            double distance = toward.length();
            if (distance < 0.5) {
                continue;
            }
            item.setDeltaMovement(item.getDeltaMovement().add(toward.normalize().scale(RIFT_PULL)));
            item.setPickUpDelay(0);
        }
        if (!items.isEmpty()) {
            level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY(1.0), player.getZ(),
                4, 0.4, 0.6, 0.4, 0.1);
        }
    }


    private static void senseLife(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class,
            player.getBoundingBox().inflate(TREMOR_RADIUS))) {
            if (nearby == player) {
                continue;
            }
            nearby.addEffect(new MobEffectInstance(MobEffects.GLOWING, SWEEP_EFFECT_TICKS, 0,
                true, false, false));
        }
    }


    private static void interdict(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (Mob mob : level.getEntitiesOfClass(Mob.class,
            player.getBoundingBox().inflate(INTERDICT_RADIUS))) {
            if (!(mob instanceof Enemy)) {
                continue;
            }
            mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SWEEP_EFFECT_TICKS, 1,
                true, false, false));
            mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, SWEEP_EFFECT_TICKS, 0,
                true, false, false));
        }
    }


    private static void retaliate(ServerPlayer victim, LivingEntity attacker) {
        ServerLevel level = victim.serverLevel();
        retaliating = true;
        try {
            attacker.hurt(level.damageSources().thorns(victim), SHARDSKIN_RETALIATION);
        } finally {
            retaliating = false;
        }
        level.sendParticles(ParticleTypes.CRIT, attacker.getX(), attacker.getY(0.6), attacker.getZ(),
            8, 0.25, 0.3, 0.25, 0.05);
    }

    private static long gameTime(@Nullable Entity entity) {
        if (entity == null || entity.level().getServer() == null) {
            return 0L;
        }
        return entity.level().getServer().overworld().getGameTime();
    }
}
