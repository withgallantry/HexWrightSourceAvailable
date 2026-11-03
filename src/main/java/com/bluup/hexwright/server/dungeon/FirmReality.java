package com.bluup.hexwright.server.dungeon;

import com.bluup.hexwright.server.effect.HexwrightEffects;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FirmReality {

    private static final int SWEEP_INTERVAL_TICKS = 10;

    private record Zone(ResourceKey<Level> dimension, AABB box) {
    }

    private static final Map<UUID, Zone> ZONES = new ConcurrentHashMap<>();

    private FirmReality() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(FirmReality::sweep);
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            if (DungeonDimension.isDungeonLevel(destination)) {
                HexwrightEffects.sync(player, HexwrightEffects.FIRM_REALITY, true, MobEffectInstance.INFINITE_DURATION);
                player.displayClientMessage(Component.translatable("message.hexwright.dungeon.firm_reality")
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ZONES.clear());
    }

    public static void hold(ResourceKey<Level> dimension, UUID owner, AABB box) {
        ZONES.put(owner, new Zone(dimension, box));
    }

    public static void release(UUID owner) {
        ZONES.remove(owner);
    }

    public static boolean holds(Entity entity) {
        return DungeonDimension.isDungeonLevel(entity.level()) || inZone(entity);
    }

    private static boolean inZone(Entity entity) {
        if (ZONES.isEmpty()) {
            return false;
        }
        for (Zone zone : ZONES.values()) {
            if (zone.dimension().equals(entity.level().dimension()) && zone.box().contains(entity.position())) {
                return true;
            }
        }
        return false;
    }

    public static int damageProtection(Iterable<ItemStack> armour, DamageSource source) {
        int protection = 0;
        for (ItemStack stack : armour) {
            for (Map.Entry<Enchantment, Integer> entry : EnchantmentHelper.getEnchantments(stack).entrySet()) {
                Enchantment enchantment = entry.getKey();
                protection += enchantment.getDamageProtection(Math.min(entry.getValue(), enchantment.getMaxLevel()), source);
            }
        }
        return protection;
    }

    private static void sweep(MinecraftServer server) {
        if (server.getTickCount() % SWEEP_INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean held = holds(player);
            if (held && !player.hasEffect(HexwrightEffects.FIRM_REALITY) && !DungeonDimension.isDungeonLevel(player.level())) {
                player.displayClientMessage(Component.translatable("message.hexwright.boss.firm_reality")
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
            }
            HexwrightEffects.sync(player, HexwrightEffects.FIRM_REALITY, held, MobEffectInstance.INFINITE_DURATION);
        }
    }
}
