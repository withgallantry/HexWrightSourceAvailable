package com.bluup.hexwright.server.powerorb;

import at.petrak.hexcasting.api.misc.MediaConstants;
import com.bluup.hexwright.server.effect.HexwrightEffects;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ConstructOrbPower {

    public static final long SUMMON_COST = 15 * MediaConstants.DUST_UNIT;

    public static final ConstructOrbPower GOLEM = new ConstructOrbPower(PowerOrbPower.GOLEM,
        PowerOrbEntities.SPIRIT_GOLEM, SpiritGolemEntity.MAX_HEALTH, SpiritGolemEntity::summon,
        HexwrightEffects.SPIRIT_GOLEM, 0);
    public static final ConstructOrbPower CAT = new ConstructOrbPower(PowerOrbPower.CAT,
        PowerOrbEntities.CAT_CONSTRUCT, CatConstructEntity.MAX_HEALTH, CatConstructEntity::summon,
        HexwrightEffects.CAT_CONSTRUCT, 0);
    public static final ConstructOrbPower GUARDIAN = new ConstructOrbPower(PowerOrbPower.GUARDIAN,
        PowerOrbEntities.GUARDIAN_CONSTRUCT, GuardianConstructEntity.MAX_HEALTH, GuardianConstructEntity::summon,
        HexwrightEffects.GUARDIAN_CONSTRUCT, 60 * 20);

    private static final List<ConstructOrbPower> ALL = List.of(GOLEM, CAT, GUARDIAN);

    private static final int PERIOD = 5;
    private static final int HEALTH_SYNC_TICKS = 20;
    private static final double RECALL_DISTANCE = 48.0D;

    @FunctionalInterface
    private interface Summoner {
        @Nullable
        OrbConstructEntity summon(ServerLevel level, Player owner, Vec3 at, float health);
    }

    private final PowerOrbPower power;
    private final EntityType<? extends OrbConstructEntity> type;
    private final float maxHealth;
    private final Summoner summoner;
    private final MobEffect status;
    private final int slainCooldownTicks;
    private final Map<UUID, OrbConstructEntity> constructs = new HashMap<>();

    private ConstructOrbPower(PowerOrbPower power, EntityType<? extends OrbConstructEntity> type, float maxHealth,
                              Summoner summoner, MobEffect status, int slainCooldownTicks) {
        this.power = power;
        this.type = type;
        this.maxHealth = maxHealth;
        this.summoner = summoner;
        this.status = status;
        this.slainCooldownTicks = slainCooldownTicks;
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ConstructOrbPower::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            for (ConstructOrbPower power : ALL) {
                OrbConstructEntity construct = power.constructs.remove(handler.player.getUUID());
                if (construct != null) {
                    putAway(construct, PowerOrbSlot.worn(handler.player, power.power));
                }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ALL.forEach(power -> power.constructs.clear()));
    }

    public float maxHealth() {
        return this.maxHealth;
    }

    public int slainCooldownTicks() {
        return this.slainCooldownTicks;
    }

    @Nullable
    public OrbConstructEntity construct(Player player) {
        return this.constructs.get(player.getUUID());
    }

    public boolean claims(OrbConstructEntity construct) {
        UUID owner = construct.getOwnerUUID();
        return owner != null && this.constructs.get(owner) == construct;
    }

    public void summon(ServerPlayer player, ItemStack orb) {
        if (!PowerOrbData.holdsConstruct(orb)) {
            PowerOrbData.setConstructHealth(orb, this.maxHealth);
        }
        PowerOrbData.setActive(orb, true);
        if (!this.constructs.containsKey(player.getUUID())) {
            callOut(player, orb);
        }
        syncStatus(player);
    }

    public void dismiss(ServerPlayer player, ItemStack orb) {
        PowerOrbData.setActive(orb, false);
        OrbConstructEntity construct = this.constructs.remove(player.getUUID());
        if (construct != null && construct.isDeadOrDying()) {
            PowerOrbData.clearConstruct(orb);
        } else if (construct != null) {
            putAway(construct, orb);
        }
        syncStatus(player);
    }

    public MutableComponent message(String event, Object... args) {
        return Component.translatable("message.hexwright.power_orb." + this.power.id() + "_" + event, args);
    }

    private static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        if (now % PERIOD != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (ConstructOrbPower power : ALL) {
                power.tickPlayer(player, now);
                power.syncStatus(player);
            }
        }
    }

    private void syncStatus(ServerPlayer player) {
        HexwrightEffects.sync(player, this.status, this.constructs.containsKey(player.getUUID()),
            MobEffectInstance.INFINITE_DURATION);
    }

    private void tickPlayer(ServerPlayer player, int now) {
        UUID id = player.getUUID();
        ItemStack orb = PowerOrbSlot.worn(player, this.power);
        OrbConstructEntity construct = this.constructs.get(id);

        if (construct != null && construct.isDeadOrDying()) {
            this.constructs.remove(id);
            if (!orb.isEmpty()) {
                PowerOrbData.clearConstruct(orb);
                PowerOrbData.setActive(orb, false);
                if (this.slainCooldownTicks > 0) {
                    PowerOrbData.setReadyAt(orb, player.level().getGameTime() + this.slainCooldownTicks);
                }
            }
            player.displayClientMessage(message("slain").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (construct != null && construct.isRemoved()) {
            this.constructs.remove(id);
            construct = null;
        }

        boolean wanted = !orb.isEmpty() && PowerOrbData.isActive(orb) && PowerOrbData.holdsConstruct(orb)
            && player.isAlive() && !player.isSpectator();
        if (construct != null) {
            boolean strayed = construct.level() != player.level()
                || construct.distanceToSqr(player) > RECALL_DISTANCE * RECALL_DISTANCE;
            if (!wanted || strayed) {
                this.constructs.remove(id);
                putAway(construct, orb);
                return;
            }
            if (now % HEALTH_SYNC_TICKS == 0 && PowerOrbData.constructHealth(orb) != construct.getHealth()) {
                PowerOrbData.setConstructHealth(orb, construct.getHealth());
            }
            return;
        }
        if (wanted) {
            callOut(player, orb);
        }
    }

    private void callOut(ServerPlayer player, ItemStack orb) {
        ServerLevel level = player.serverLevel();
        Vec3 at = OrbConstructEntity.spotNear(level, player, this.type);
        if (at == null) {
            at = player.position();
        }
        OrbConstructEntity construct = this.summoner.summon(level, player, at, PowerOrbData.constructHealth(orb));
        if (construct != null) {
            this.constructs.put(player.getUUID(), construct);
        }
    }

    private static void putAway(OrbConstructEntity construct, ItemStack orb) {
        if (construct.isRemoved() || construct.isDeadOrDying()) {
            return;
        }
        if (!orb.isEmpty()) {
            PowerOrbData.setConstructHealth(orb, construct.getHealth());
        }
        construct.dismiss();
    }
}
