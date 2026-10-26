package com.bluup.hexwright.server.inspiration;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.inits.HexwrightNetworking;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Inspiration {

    public static final TagKey<EntityType<?>> INSPIRING =
        TagKey.create(Registries.ENTITY_TYPE, Hexwright.id("inspiring"));

    private static final int MAX_ORBS = 16;

    public static final int CLAIM_ORBS = 5;

    private static final Map<UUID, Integer> pending = new HashMap<>();

    private Inspiration() {
    }

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
            if (victim.level().isClientSide || !victim.getType().is(INSPIRING)) {
                return;
            }
            if (!(victim.getKillCredit() instanceof Player) || !victim.shouldDropExperience()) {
                return;
            }
            drop((ServerLevel) victim.level(), victim.position().add(0.0, victim.getBbHeight() * 0.5, 0.0),
                orbsFor(victim));
        });
        ServerTickEvents.END_SERVER_TICK.register(Inspiration::announce);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            HexwrightNetworking.sendInspirationSync(handler.player));
    }

    public static void dropClaimOrb(ServerLevel level, Vec3 pos) {
        level.addFreshEntity(new InspirationOrbEntity(level, pos));
    }

    public static void drop(ServerLevel level, Vec3 pos, int orbs) {
        for (int i = 0; i < orbs; i++) {
            level.addFreshEntity(new InspirationOrbEntity(level, pos));
        }
        if (orbs > 0) {
            level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.NEUTRAL, 0.6f, 1.3f);
        }
    }

    private static int orbsFor(LivingEntity victim) {
        return Mth.clamp(Math.round(Mth.sqrt(victim.getMaxHealth()) / 2.0f), 1, MAX_ORBS);
    }

    static void collect(ServerPlayer collector, InspirationOrbEntity orb) {
        int total = InspirationState.get(collector.server).add(collector.getUUID(), 1);
        pending.merge(collector.getUUID(), 1, Integer::sum);
        collector.serverLevel().playSound(null, orb.getX(), orb.getY(), orb.getZ(),
            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.3f,
            1.0f + collector.getRandom().nextFloat() * 0.4f);
        HexwrightDebug.log(HexwrightDebug.INSPIRATION, "[inspiration] {} collected an orb: {} in all",
            collector.getGameProfile().getName(), total);
    }

    private static void announce(MinecraftServer server) {
        if (pending.isEmpty()) {
            return;
        }
        InspirationState state = InspirationState.get(server);
        pending.forEach((id, gained) -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                return;
            }
            int total = state.total(id);
            int level = InspirationLevels.levels(total);
            if (InspirationLevels.levels(total - gained) < level) {
                player.sendSystemMessage(
                    Component.translatable("message.hexwright.inspiration.level_up", level)
                        .withStyle(ChatFormatting.AQUA));
                player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.7f, 1.6f);
            }
            HexwrightNetworking.sendInspirationSync(player);
        });
        pending.clear();
    }
}
