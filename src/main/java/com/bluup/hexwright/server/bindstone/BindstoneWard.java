package com.bluup.hexwright.server.bindstone;

import com.bluup.hexwright.mixin.EntitySharedFlagAccessor;
import com.bluup.hexwright.server.worldgen.TeleportWards;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BindstoneWard {

    private static final int NOTICE_COOLDOWN_TICKS = 80;

    private static final double WARDED_FALL_SPEED = 0.35;

    private static final int FLAG_FALL_FLYING = 7;

    private static final Map<UUID, Integer> NOTICE_COOLDOWNS = new HashMap<>();

    private BindstoneWard() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(BindstoneWard::tick);
        registerBlockProtection();
        TeleportWards.register(new TeleportWards.Check() {
            @Override
            public boolean isEmpty(ServerLevel level) {
                return BindstoneRegistry.isEmpty();
            }

            @Override
            public boolean refuses(ServerLevel level, Vec3 from, Vec3 to) {
                return BindstoneWard.refusesTeleport(level, from, to);
            }

            @Override
            public void notifyRefused(ServerPlayer player) {
                BindstoneWard.notifyRefused(player);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            NOTICE_COOLDOWNS.remove(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            BindstoneRegistry.clear();
            NOTICE_COOLDOWNS.clear();
        });
    }

    private static void registerBlockProtection() {
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (!refusesEdit(player, pos)) {
                return InteractionResult.PASS;
            }
            notifyImmovable((ServerPlayer) player);
            return InteractionResult.FAIL;
        });
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, entity) -> {
            if (!refusesEdit(player, pos)) {
                return true;
            }
            notifyImmovable((ServerPlayer) player);
            return false;
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!placesBlocks(player.getItemInHand(hand)) || !refusesEdit(player, hit.getBlockPos())) {
                return InteractionResult.PASS;
            }
            notifyImmovable((ServerPlayer) player);
            return InteractionResult.FAIL;
        });
        UseItemCallback.EVENT.register((player, level, hand) -> {
            ItemStack held = player.getItemInHand(hand);
            if (!placesBlocks(held) || !refusesEdit(player, player.blockPosition())) {
                return InteractionResultHolder.pass(held);
            }
            notifyImmovable((ServerPlayer) player);
            return InteractionResultHolder.fail(held);
        });
    }

    private static boolean placesBlocks(ItemStack stack) {
        return stack.getItem() instanceof BlockItem || stack.getItem() instanceof BucketItem;
    }

    private static boolean refusesEdit(Player player, BlockPos pos) {
        if (!BindstoneRegistry.sealsAnything() || player.level().isClientSide) {
            return false;
        }
        return player instanceof ServerPlayer server
            && !server.isSpectator()
            && BindstoneRegistry.sealsEdits(server.level(), pos);
    }

    private static void notifyImmovable(ServerPlayer player) {
        if (!offCooldown(player)) {
            return;
        }
        player.sendSystemMessage(
            Component.translatable("message.hexwright.bindstone.immovable").withStyle(ChatFormatting.DARK_PURPLE)
        );
        player.level().playSound(
            null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 0.6f, 0.4f
        );
    }

    public static boolean refusesTeleport(Level level, Vec3 from, Vec3 to) {
        return BindstoneRegistry.isWarded(level, to) || BindstoneRegistry.isWarded(level, from);
    }

    public static void notifyRefused(ServerPlayer player) {
        if (!offCooldown(player)) {
            return;
        }
        player.sendSystemMessage(
            Component.translatable("message.hexwright.bindstone.refused").withStyle(ChatFormatting.DARK_PURPLE)
        );
        player.level().playSound(
            null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 0.6f, 0.4f
        );
    }

    private static void tick(MinecraftServer server) {
        if (BindstoneRegistry.isEmpty()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || !BindstoneRegistry.isWarded(player.level(), player.position())) {
                continue;
            }

            boolean grounded = false;
            if (player.isFallFlying()) {
                ((EntitySharedFlagAccessor) player).hexwright$setSharedFlag(FLAG_FALL_FLYING, false);
                grounded = true;
            }
            if (player.getAbilities().flying) {
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
                grounded = true;
            }

            Vec3 motion = player.getDeltaMovement();
            if (motion.y < -WARDED_FALL_SPEED) {
                player.setDeltaMovement(motion.x, -WARDED_FALL_SPEED, motion.z);
                player.hurtMarked = true;
            }
            player.resetFallDistance();

            if (grounded) {
                notifyGrounded(player);
            }
        }
    }

    private static void notifyGrounded(ServerPlayer player) {
        if (!offCooldown(player)) {
            return;
        }
        player.sendSystemMessage(
            Component.translatable("message.hexwright.bindstone.grounded").withStyle(ChatFormatting.DARK_PURPLE)
        );
        player.level().playSound(
            null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 0.6f, 0.4f
        );
    }

    private static boolean offCooldown(ServerPlayer player) {
        int now = player.tickCount;
        Integer last = NOTICE_COOLDOWNS.get(player.getUUID());
        if (last != null && now - last < NOTICE_COOLDOWN_TICKS && now >= last) {
            return false;
        }
        NOTICE_COOLDOWNS.put(player.getUUID(), now);
        return true;
    }
}
