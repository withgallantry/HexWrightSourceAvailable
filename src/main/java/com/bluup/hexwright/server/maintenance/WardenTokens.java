package com.bluup.hexwright.server.maintenance;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.HexwrightDebug;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class WardenTokens {

    public static final Item TOKEN_OF_MAINTENANCE = new WardenTokenItem(
        new Item.Properties().rarity(Rarity.RARE), "tooltip.hexwright.token_of_maintenance");
    public static final Item TOKEN_OF_THE_WARDEN = new WardenTokenItem(
        new Item.Properties().rarity(Rarity.EPIC), "tooltip.hexwright.token_of_the_warden");

    private WardenTokens() {
    }

    static void register() {
        Registry.register(BuiltInRegistries.ITEM, Hexwright.id("token_of_maintenance"), TOKEN_OF_MAINTENANCE);
        Registry.register(BuiltInRegistries.ITEM, Hexwright.id("token_of_the_warden"), TOKEN_OF_THE_WARDEN);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            for (ItemStack stack : Owed.get(server).takeFor(handler.player.getUUID())) {
                give(handler.player, stack);
            }
        });
    }

    public static void award(ServerLevel level, @Nullable UUID keystone, Collection<UUID> fighters,
                             @Nullable ServerPlayer finisher) {
        MinecraftServer server = level.getServer();
        Set<UUID> participants = new LinkedHashSet<>(fighters);
        if (keystone != null) {
            participants.addAll(PlanarMaintenance.feedersOf(server, keystone));
        }
        if (finisher != null) {
            participants.add(finisher.getUUID());
        }
        for (UUID participant : participants) {
            hand(server, participant, new ItemStack(TOKEN_OF_MAINTENANCE));
        }
        if (finisher != null) {
            hand(server, finisher.getUUID(), new ItemStack(TOKEN_OF_THE_WARDEN));
        }
        HexwrightDebug.log(HexwrightDebug.MAINTENANCE, "[maintenance] the Warden fell: {} tokens of maintenance, finished by {}",
            participants.size(), finisher == null ? "nobody" : finisher.getGameProfile().getName());
    }

    private static void hand(MinecraftServer server, UUID player, ItemStack stack) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            give(online, stack);
        } else {
            Owed.get(server).add(player, stack);
        }
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        player.sendSystemMessage(Component.translatable("message.hexwright.warden_token.received", stack.getHoverName())
            .withStyle(ChatFormatting.LIGHT_PURPLE));
        if (player.getInventory().add(stack)) {
            return;
        }
        ItemEntity dropped = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack);
        dropped.setNoPickUpDelay();
        dropped.setDeltaMovement(0.0, 0.0, 0.0);
        player.level().addFreshEntity(dropped);
    }

    private static final class Owed extends SavedData {

        private static final String STORAGE_ID = "hexwright_warden_tokens";

        private final Map<UUID, List<ItemStack>> owed = new HashMap<>();

        static Owed get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(Owed::load, Owed::new, STORAGE_ID);
        }

        void add(UUID player, ItemStack stack) {
            this.owed.computeIfAbsent(player, key -> new ArrayList<>()).add(stack);
            setDirty();
        }

        List<ItemStack> takeFor(UUID player) {
            List<ItemStack> taken = this.owed.remove(player);
            if (taken == null) {
                return List.of();
            }
            setDirty();
            return taken;
        }

        private static Owed load(CompoundTag tag) {
            Owed owed = new Owed();
            for (String key : tag.getAllKeys()) {
                List<ItemStack> stacks = new ArrayList<>();
                for (Tag saved : tag.getList(key, Tag.TAG_COMPOUND)) {
                    ItemStack stack = ItemStack.of((CompoundTag) saved);
                    if (!stack.isEmpty()) {
                        stacks.add(stack);
                    }
                }
                owed.owed.put(UUID.fromString(key), stacks);
            }
            return owed;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            this.owed.forEach((player, stacks) -> {
                ListTag saved = new ListTag();
                stacks.forEach(stack -> saved.add(stack.save(new CompoundTag())));
                tag.put(player.toString(), saved);
            });
            return tag;
        }
    }
}
